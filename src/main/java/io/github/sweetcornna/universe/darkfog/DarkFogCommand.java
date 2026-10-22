package io.github.sweetcornna.universe.darkfog;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.core.darkfog.UnitKind;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe darkfog ...}：调试黑雾的地面基地（ADR-0028）。都只管命令执行者所在的星球。
 *
 * <ul>
 *   <li>{@code list}：这颗星球上的基地：编号、状态、位置、等级、威胁、建筑数、营地里的单位。</li>
 *   <li>{@code tp <编号>}：传到基地外边。</li>
 *   <li>{@code create [距离]}：在面前这么远（默认 48 格）马上扎下一处基地。</li>
 *   <li>{@code threat <编号> <百分比>}、{@code level <编号> <等级>}、{@code units <编号> <种类> <数量>}：直接改。</li>
 *   <li>{@code attack <编号>}：威胁设满，30 秒后来袭。</li>
 *   <li>{@code grow <编号> <分钟>}：让它多长这么久。</li>
 *   <li>{@code remove <编号>}、{@code clear}：收拾掉一处、全部基地（不留地核）。</li>
 * </ul>
 */
public final class DarkFogCommand {
    private DarkFogCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("darkfog")
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("tp").then(id().executes(DarkFogCommand::tp)))
                        .then(Commands.literal("create")
                                .executes(context -> create(context.getSource(), 48))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(0, 512))
                                        .executes(context -> create(context.getSource(), IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("threat").then(id().then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(context -> threat(context, IntegerArgumentType.getInteger(context, "percent"))))))
                        .then(Commands.literal("level").then(id().then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                                .executes(context -> level(context, IntegerArgumentType.getInteger(context, "level"))))))
                        .then(Commands.literal("units").then(id()
                                .then(Commands.literal("raider").then(count(UnitKind.RAIDER)))
                                .then(Commands.literal("ranger").then(count(UnitKind.RANGER)))
                                .then(Commands.literal("guardian").then(count(UnitKind.GUARDIAN)))))
                        .then(Commands.literal("attack").then(id().executes(DarkFogCommand::attack)))
                        .then(Commands.literal("grow").then(id().then(Commands.argument("minutes", IntegerArgumentType.integer(1, 720))
                                .executes(context -> grow(context, IntegerArgumentType.getInteger(context, "minutes"))))))
                        .then(Commands.literal("remove").then(id().executes(DarkFogCommand::remove)))
                        .then(Commands.literal("clear").executes(context -> clear(context.getSource())))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> id() {
        return Commands.argument("id", IntegerArgumentType.integer(0));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> count(UnitKind kind) {
        return Commands.argument("count", IntegerArgumentType.integer(0, 64))
                .executes(context -> units(context, kind, IntegerArgumentType.getInteger(context, "count")));
    }

    private static Optional<BaseState> base(CommandContext<CommandSourceStack> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        Optional<BaseState> base = DarkFogBases.find(context.getSource().getLevel(), id);
        if (base.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.universe.darkfog.unknown", id));
        }
        return base;
    }

    private static int list(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var bases = DarkFogBases.bases(level);
        if (bases.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.none"), false);
            return 0;
        }
        for (BaseState base : bases) {
            String status = base.status().name().toLowerCase(Locale.ROOT);
            String y = base.ground() ? String.valueOf(base.y()) : "?";
            int threat = (int) Math.round(base.threat() * 100);
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.line", base.id(), status, base.x(), y, base.z(),
                    base.level(), threat, base.buildings(), base.units(UnitKind.RAIDER), base.units(UnitKind.RANGER),
                    base.units(UnitKind.GUARDIAN), Component.translatable(base.powered()
                            ? "commands.universe.darkfog.powered" : "commands.universe.darkfog.unpowered"),
                    DarkFogBases.attacking(level, base.id()) ? "!" : ""), false);
        }
        return bases.size();
    }

    private static int tp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<BaseState> base = base(context);
        if (base.isEmpty()) {
            return 0;
        }
        BaseState b = base.get();
        ServerLevel level = context.getSource().getLevel();
        // 底板还没铺（区块没加载过）就按那里的地面算；读高度图会就地加载区块，命令里可以
        double y = (b.ground() ? b.y() : level.getHeight(Heightmap.Types.WORLD_SURFACE, b.x(), b.z())) + 12;
        player.teleportTo(level, b.x() + 0.5, y, b.z() + 34.5, Set.of(), 180, 15, true);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.tp", b.id()), false);
        return 1;
    }

    private static int create(CommandSourceStack source, int distance) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        Vec3 at = player.position().add(look.lengthSqr() < 1e-6 ? Vec3.ZERO : look.normalize().scale(distance));
        BaseState base = DarkFogBases.create(source.getLevel(), (int) Math.floor(at.x), (int) Math.floor(at.z), null);
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.created", base.id(), base.x(), base.z()), true);
        return 1;
    }

    private static int threat(CommandContext<CommandSourceStack> context, int percent) {
        return base(context).map(base -> {
            base.setThreat(percent / 100.0);
            DarkFogData.get(context.getSource().getServer()).setDirty();
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.threat", base.id(), percent), true);
            return 1;
        }).orElse(0);
    }

    private static int level(CommandContext<CommandSourceStack> context, int level) {
        return base(context).map(base -> {
            base.setLevel(level);
            DarkFogData.get(context.getSource().getServer()).setDirty();
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.level", base.id(), level), true);
            return 1;
        }).orElse(0);
    }

    private static int units(CommandContext<CommandSourceStack> context, UnitKind kind, int count) {
        return base(context).map(base -> {
            base.setUnits(kind, count);
            DarkFogData.get(context.getSource().getServer()).setDirty();
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.units", base.id(),
                    Component.translatable("entity.universe.dark_fog_" + kind.id()), count), true);
            return 1;
        }).orElse(0);
    }

    private static int attack(CommandContext<CommandSourceStack> context) {
        return base(context).map(base -> {
            base.setThreat(1);
            DarkFogData.get(context.getSource().getServer()).setDirty();
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.attack", base.id()), true);
            return 1;
        }).orElse(0);
    }

    private static int grow(CommandContext<CommandSourceStack> context, int minutes) {
        return base(context).map(base -> {
            base.backdate(minutes * 1200L);
            DarkFogData.get(context.getSource().getServer()).setDirty();
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.grow", base.id(), minutes), true);
            return 1;
        }).orElse(0);
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        return base(context).map(base -> {
            DarkFogBases.remove(context.getSource().getLevel(), base.id());
            context.getSource().sendSuccess(() -> Component.translatable("commands.universe.darkfog.removed", base.id()), true);
            return 1;
        }).orElse(0);
    }

    private static int clear(CommandSourceStack source) {
        int count = DarkFogBases.clear(source.getLevel());
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.cleared", count), true);
        return count;
    }
}
