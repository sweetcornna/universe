package io.github.sweetcornna.universe.terraform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.terraform.TerraformState;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * {@code /universe terraform ...}：调试星球改造（ADR-0036 §六）。星球省略时取执行者所在的星球，写的话和
 * {@code /universe planet} 一样用维度 ID，例如 {@code universe:s3/p2}。
 *
 * <ul>
 *   <li>{@code info}：现在的气压、成分、温度、门槛、到了的阶段；</li>
 *   <li>{@code gas <气体> <单位>}：放进（正）或抽走（负）一种气体；</li>
 *   <li>{@code biomass <量>}：把生物量设成多少；</li>
 *   <li>{@code mirrors <面数>}、{@code shades <面数>}：直接设反射镜、遮阳板的面数（之后放下、拆掉真的反射镜还会在这个数上加减）；</li>
 *   <li>{@code advance <刻>}：快进，生物量和大气化学一起推进；</li>
 *   <li>{@code reset}：清空这颗星球的改造状态。</li>
 * </ul>
 */
public final class TerraformCommand {
    private static final String C = "commands.universe.terraform.";
    private static final DynamicCommandExceptionType NOT_TERRAFORMABLE =
            new DynamicCommandExceptionType(id -> Component.translatable(C + "not_terraformable", String.valueOf(id)));
    private static final DynamicCommandExceptionType UNKNOWN_GAS =
            new DynamicCommandExceptionType(name -> Component.translatable(C + "unknown_gas", String.valueOf(name)));

    private static final SuggestionProvider<CommandSourceStack> PLANETS = (context, builder) -> {
        Galaxy galaxy = Planets.galaxy(context.getSource().getServer());
        return SharedSuggestionProvider.suggestResource(galaxy.systems().stream()
                .flatMap(system -> system.bodies().stream())
                .filter(Terraform::terraformable)
                .map(planet -> Planets.dimension(planet.id()).identifier()), builder);
    };
    private static final SuggestionProvider<CommandSourceStack> GASES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Gas.values()).map(gas -> gas.name().toLowerCase(Locale.ROOT)), builder);

    /** 一个子命令要执行的事，星球已经找好。 */
    @FunctionalInterface
    private interface Action {
        int run(CommandContext<CommandSourceStack> context, Planet base) throws CommandSyntaxException;
    }

    private TerraformCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> terraform = Commands.literal("terraform")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        terraform.then(withPlanet(Commands.literal("info"), (context, base) -> info(context.getSource(), base)));
        terraform.then(Commands.literal("gas").then(Commands.argument("gas", StringArgumentType.word()).suggests(GASES)
                .then(withPlanet(Commands.argument("units", DoubleArgumentType.doubleArg()), TerraformCommand::gas))));
        terraform.then(Commands.literal("biomass").then(withPlanet(Commands.argument("amount", DoubleArgumentType.doubleArg(0)),
                (context, base) -> set(context.getSource(), base, "biomass", DoubleArgumentType.getDouble(context, "amount"),
                        state -> state.withBiomass(DoubleArgumentType.getDouble(context, "amount"))))));
        terraform.then(Commands.literal("mirrors").then(withPlanet(Commands.argument("count", IntegerArgumentType.integer(0, 999)),
                (context, base) -> set(context.getSource(), base, "mirrors", IntegerArgumentType.getInteger(context, "count"),
                        state -> state.withMirrors(IntegerArgumentType.getInteger(context, "count"))))));
        terraform.then(Commands.literal("shades").then(withPlanet(Commands.argument("count", IntegerArgumentType.integer(0, 999)),
                (context, base) -> set(context.getSource(), base, "shades", IntegerArgumentType.getInteger(context, "count"),
                        state -> state.withShades(IntegerArgumentType.getInteger(context, "count"))))));
        terraform.then(Commands.literal("advance").then(withPlanet(Commands.argument("ticks", LongArgumentType.longArg(1, 100_000_000L)),
                TerraformCommand::advance)));
        terraform.then(withPlanet(Commands.literal("reset"), (context, base) -> {
            Terraforming.update(context.getSource().getServer(), base.id(), state -> TerraformState.EMPTY);
            context.getSource().sendSuccess(() -> Component.translatable(C + "reset", name(context.getSource(), base)), true);
            return 1;
        }));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(terraform));
    }

    /** 子命令后面可以跟一个星球；不跟就取执行者所在的星球。 */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withPlanet(T builder, Action action) {
        return builder.executes(context -> action.run(context, here(context.getSource())))
                .then(Commands.argument("planet", IdentifierArgument.id()).suggests(PLANETS)
                        .executes(context -> action.run(context, named(context))));
    }

    private static Planet here(CommandSourceStack source) throws CommandSyntaxException {
        return terraformable(source, source.getLevel().dimension());
    }

    private static Planet named(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(context, "planet");
        return terraformable(context.getSource(), ResourceKey.create(Registries.DIMENSION, id));
    }

    private static Planet terraformable(CommandSourceStack source, ResourceKey<Level> dimension) throws CommandSyntaxException {
        Galaxy galaxy = Planets.galaxy(source.getServer());
        return Planets.bodyId(dimension).flatMap(galaxy::body).filter(Terraform::terraformable)
                .orElseThrow(() -> NOT_TERRAFORMABLE.create(dimension.identifier()));
    }

    private static Component name(CommandSourceStack source, Planet planet) {
        return Discoveries.name(source.getServer(), DiscoveryKey.body(planet.id()));
    }

    // ---- 子命令

    private static int info(CommandSourceStack source, Planet base) {
        TerraformState state = Terraforming.state(source.getServer(), base.id());
        EffectiveEnvironment env = Terraforming.environment(source.getServer(), base);
        MutableComponent text = Component.translatable(C + "info", name(source, base), number(env.pressure(), 4), number(env.shieldingColumn(), 2),
                number(env.temperature(), 1), number(state.biomass(), 4), state.mirrors(), state.shades(), state.pioneers().size());
        List<String> mix = new ArrayList<>();
        env.mix().sorted().stream().limit(6).forEach(entry -> mix.add(entry.getKey().formula() + " " + percent(entry.getValue())));
        text.append("\n  ").append(Component.translatable(C + "info.mix", mix.isEmpty() ? "-" : String.join(", ", mix)));
        List<String> deltas = new ArrayList<>();
        for (Map.Entry<Gas, Double> entry : state.deltas().entrySet()) {
            deltas.add(entry.getKey().formula() + " " + (entry.getValue() >= 0 ? "+" : "") + number(entry.getValue(), 0));
        }
        text.append("\n  ").append(Component.translatable(C + "info.deltas", deltas.isEmpty() ? "-" : String.join(", ", deltas)));
        for (Terraform.Threshold threshold : Terraform.thresholds(env)) {
            text.append("\n  ").append(Component.translatable(C + "info.threshold", Component.translatable(ThresholdText.labelKey(threshold.kind())),
                    ThresholdText.value(threshold), ThresholdText.range(threshold),
                    Component.translatable(threshold.met() ? C + "info.met" : C + "info.unmet")));
        }
        EnumSet<Terraform.Stage> stages = Terraform.reached(env);
        MutableComponent names = Component.empty();
        for (Terraform.Stage stage : stages) {
            if (!names.getSiblings().isEmpty()) {
                names.append(", ");
            }
            names.append(Component.translatable(C + "stage." + stage.name().toLowerCase(Locale.ROOT)));
        }
        text.append("\n  ").append(Component.translatable(C + "info.stages", stages.isEmpty() ? Component.literal("-") : names));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    private static int gas(CommandContext<CommandSourceStack> context, Planet base) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "gas");
        Gas gas = Optional.ofNullable(Gas.byName(name.toUpperCase(Locale.ROOT))).orElseThrow(() -> UNKNOWN_GAS.create(name));
        double units = DoubleArgumentType.getDouble(context, "units");
        CommandSourceStack source = context.getSource();
        Terraforming.addGas(source.getServer(), base.id(), gas, units);
        EffectiveEnvironment env = Terraforming.environment(source.getServer(), base);
        source.sendSuccess(() -> Component.translatable(C + "gas", name(source, base), (units >= 0 ? "+" : "") + number(units, 0), gas.formula(),
                number(env.pressure(), 4), number(env.temperature(), 1)), true);
        return 1;
    }

    private static int set(CommandSourceStack source, Planet base, String what, double value, UnaryOperator<TerraformState> change) {
        Terraforming.update(source.getServer(), base.id(), change);
        source.sendSuccess(() -> Component.translatable(C + what, name(source, base), number(value, what.equals("biomass") ? 4 : 0)), true);
        return 1;
    }

    private static int advance(CommandContext<CommandSourceStack> context, Planet base) {
        CommandSourceStack source = context.getSource();
        long ticks = LongArgumentType.getLong(context, "ticks");
        boolean compact = Terraforming.compactStar(source.getServer(), base);
        long now = source.getServer().overworld().getGameTime();
        Terraforming.update(source.getServer(), base.id(), state -> Terraform.advance(state, base, compact, ticks).withLastUpdate(now));
        EffectiveEnvironment env = Terraforming.environment(source.getServer(), base);
        source.sendSuccess(() -> Component.translatable(C + "advance", name(source, base), ticks, number(env.pressure(), 4),
                number(env.temperature(), 1), number(env.biomass(), 4)), true);
        return 1;
    }

    private static String number(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String percent(double fraction) {
        double value = fraction * 100;
        return (value >= 1 ? String.format(Locale.ROOT, "%.1f", value) : value >= 0.01 ? String.format(Locale.ROOT, "%.3f", value)
                : String.format(Locale.ROOT, "%.2e", value)) + "%";
    }
}
