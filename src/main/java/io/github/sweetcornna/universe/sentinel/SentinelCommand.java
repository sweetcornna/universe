package io.github.sweetcornna.universe.sentinel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.core.sentinel.Wanted;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 调试命令 {@code /universe sentinel}（ADR-0014）：
 * {@code status} 看当前星球的守卫强度和自己的警戒值；{@code planets} 列出本恒星系各星球的守卫强度；
 * {@code offend <行为> [次数]} 模拟违规；{@code spawn [heavy]} 在附近放一架巡逻的守卫机；{@code clear} 清掉通缉。
 */
public final class SentinelCommand {
    private static final DynamicCommandExceptionType UNKNOWN_OFFENSE =
            new DynamicCommandExceptionType(name -> Component.literal("未知的违规行为：" + name));
    private static final SuggestionProvider<CommandSourceStack> OFFENSES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Offense.values()).map(o -> o.name().toLowerCase(Locale.ROOT)), builder);

    private SentinelCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sentinel")
                .then(Commands.literal("status").executes(SentinelCommand::status))
                .then(Commands.literal("planets").executes(SentinelCommand::planets))
                .then(Commands.literal("offend")
                        .then(Commands.argument("offense", StringArgumentType.word()).suggests(OFFENSES)
                                .executes(context -> offend(context, 1))
                                .then(Commands.argument("times", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> offend(context, IntegerArgumentType.getInteger(context, "times"))))))
                .then(Commands.literal("spawn")
                        .executes(context -> spawn(context, false))
                        .then(Commands.literal("heavy").executes(context -> spawn(context, true))))
                .then(Commands.literal("clear").executes(SentinelCommand::clear));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        SentinelLevel level = Sentinels.level(player.level());
        Wanted wanted = player.getData(UniverseAttachments.SENTINEL_ALERT).wanted();
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "守卫强度：%s（巡逻 %d 架，警戒倍数 %.1f）；警戒值 %.1f，通缉 %d 级，脱离 %.0f%%",
                level.nameZh(), level.patrols(), level.alertFactor(), wanted.alert(), wanted.level(), wanted.escapeProgress() * 100)), false);
        return wanted.level();
    }

    private static int planets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int system = Planets.body(player.level()).map(planet -> planet.id().system()).orElse(GalaxyGenerator.HOME_SYSTEM);
        StarSystem star = Planets.galaxy(player.level().getServer()).systems().get(system);
        List<String> lines = new ArrayList<>();
        for (Planet planet : star.planets()) {
            lines.add(line(planet));
            for (Planet moon : planet.moons()) {
                lines.add("  " + line(moon));
            }
        }
        context.getSource().sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
        return lines.size();
    }

    private static String line(Planet planet) {
        return planet.id().path() + " " + planet.name().zh() + "（" + planet.type().nameZh() + "）守卫："
                + SentinelLevel.of(planet).nameZh();
    }

    private static int offend(CommandContext<CommandSourceStack> context, int times) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "offense");
        Offense offense;
        try {
            offense = Offense.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_OFFENSE.create(name);
        }
        for (int i = 0; i < times; i++) {
            Sentinels.offend(player, offense);
        }
        return status(context);
    }

    private static int spawn(CommandContext<CommandSourceStack> context, boolean heavy) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Sentinel sentinel = Sentinels.spawn(player.level(), player, heavy, false);
        context.getSource().sendSuccess(() -> Component.literal(sentinel == null ? "附近找不到地方放守卫机"
                : String.format(Locale.ROOT, "在 %.0f %.0f %.0f 放了一架%s守卫机", sentinel.getX(), sentinel.getY(), sentinel.getZ(),
                        heavy ? "重型" : "")), false);
        return sentinel == null ? 0 : 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.getData(UniverseAttachments.SENTINEL_ALERT).wanted().clear();
        return status(context);
    }
}
