package io.github.sweetcornna.universe.darkfog.space;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.darkfog.LancerRaid;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.rules.UniverseRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe darkfog lancers ...}：调试太空单位和枪骑轰炸（ADR-0028 §八）。
 *
 * <ul>
 *   <li>{@code spawn <lancer|humpback> [架数] [等级]}：在面前 24 格放出守巢的，家就是放出来的地方。</li>
 *   <li>{@code raid [架数] [等级]}：派一队来炸脚下这一带，和巢穴派的一样先预警。</li>
 *   <li>{@code strike [架数] [等级]}：不预警，下一刻就放出来；不管这里是不是已经有一队。</li>
 *   <li>{@code status}：这颗星球上的轰炸。{@code clear}：轰炸都撤走，附近的太空单位都收走。</li>
 * </ul>
 */
public final class LancerCommand {
    /** 收走多远内的太空单位。 */
    private static final double CLEAR_RANGE = 512;
    /** 命令放的不算哪座巢穴的：打死了照样掉东西，但不扣巢穴的单位，轰炸完也不还给巢穴。 */
    private static final int NO_HIVE = -1;

    private LancerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> lancers = Commands.literal("lancers")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(spawn("lancer", SpaceUnitKind.LANCER))
                        .then(spawn("humpback", SpaceUnitKind.HUMPBACK)))
                .then(withCount(Commands.literal("raid"), 6, (context, count, level) -> raid(context.getSource(), count, level, false)))
                .then(withCount(Commands.literal("strike"), 6, (context, count, level) -> raid(context.getSource(), count, level, true)))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("clear").executes(context -> clear(context.getSource())));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("darkfog").then(lancers)));
    }

    private interface CountAction {
        int run(CommandContext<CommandSourceStack> context, int count, int level);
    }

    /** {@code [架数] [等级]}：都可以不写。 */
    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T withCount(T node, int defaultCount,
            CountAction action) {
        return node.executes(context -> action.run(context, defaultCount, 0))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                        .executes(context -> action.run(context, IntegerArgumentType.getInteger(context, "count"), 0))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 30))
                                .executes(context -> action.run(context, IntegerArgumentType.getInteger(context, "count"),
                                        IntegerArgumentType.getInteger(context, "level")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spawn(String name, SpaceUnitKind kind) {
        return withCount(Commands.literal(name), 1, (context, count, level) -> spawn(context.getSource(), kind, count, level));
    }

    private static int spawn(CommandSourceStack source, SpaceUnitKind kind, int count, int darkFogLevel) {
        ServerLevel level = source.getLevel();
        Vec3 look = source.getEntity() != null ? source.getEntity().getLookAngle() : new Vec3(0, 0, 1);
        Vec3 home = source.getPosition().add(look.x * 24, look.y * 24 + 2, look.z * 24);
        double spacing = kind == SpaceUnitKind.HUMPBACK ? 8 : 3;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            Vec3 at = count == 1 ? home : home.add(Math.cos(angle) * spacing, (i % 3 - 1) * 1.5, Math.sin(angle) * spacing);
            DarkFogSpaceUnit.spawn(level, kind, NO_HIVE, darkFogLevel, at, home, DarkFogSpaceUnit.Mode.DEFEND);
        }
        Component name = Component.translatable("entity.universe.dark_fog_" + kind.id());
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.lancers.spawned", count, name, darkFogLevel), true);
        return count;
    }

    private static int raid(CommandSourceStack source, int count, int darkFogLevel, boolean now) {
        ServerLevel level = source.getLevel();
        BlockPos target = BlockPos.containing(source.getPosition());
        if (now) {
            LancerRaids.launchNow(level, target, count, darkFogLevel, NO_HIVE, LancerRaids.Options.DEFAULT);
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.lancers.strike", count), true);
            return count;
        }
        if (LancerRaids.active(level)) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.lancers.busy"));
            return 0;
        }
        if (!UniverseRules.darkFog(source.getServer()).enabled()) {
            source.sendFailure(Component.translatable("commands.universe.darkfog.lancers.disabled"));
            return 0;
        }
        int launched = LancerRaids.launch(level, target, count, darkFogLevel, NO_HIVE);
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.lancers.raid", launched, LancerRaid.WARNING_TICKS / 20), true);
        return launched;
    }

    private static int status(CommandSourceStack source) {
        List<Component> lines = LancerRaids.describe(source.getLevel());
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.lancers.none"), false);
            return 0;
        }
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        int raids = LancerRaids.abortAll(level);
        List<Entity> units = level.getEntitiesOfClass(DarkFogSpaceUnit.class, AABB.ofSize(source.getPosition(), CLEAR_RANGE * 2, CLEAR_RANGE * 2,
                CLEAR_RANGE * 2), unit -> unit.mode() == DarkFogSpaceUnit.Mode.DEFEND).stream().map(Entity.class::cast).toList();
        units.forEach(Entity::discard);
        source.sendSuccess(() -> Component.translatable("commands.universe.darkfog.lancers.cleared", units.size(), raids), true);
        return units.size() + raids;
    }
}
