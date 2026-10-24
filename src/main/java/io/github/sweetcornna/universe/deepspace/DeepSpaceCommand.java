package io.github.sweetcornna.universe.deepspace;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Site;
import io.github.sweetcornna.universe.core.deepspace.HulkLayout;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.OutpostLayout;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 开发和调试用的 {@code /universe deepspace}：在面前放残骸、前哨站、碎片带，看附近的兴趣点，飞到某一处，引爆、清掉残骸。
 */
public final class DeepSpaceCommand {
    private static final SimpleCommandExceptionType NOT_IN_SPACE = new SimpleCommandExceptionType(
            Component.translatable("commands.universe.deepspace.not_in_space"));
    private static final SimpleCommandExceptionType NO_WRECK = new SimpleCommandExceptionType(
            Component.translatable("commands.universe.deepspace.no_wreck"));

    private DeepSpaceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("deepspace")
                        .then(Commands.literal("hulk")
                                .executes(context -> hulk(context, 0))
                                .then(Commands.argument("length", IntegerArgumentType.integer(HulkLayout.MIN_LENGTH, HulkLayout.MAX_LENGTH))
                                        .executes(context -> hulk(context, IntegerArgumentType.getInteger(context, "length")))))
                        .then(Commands.literal("outpost").executes(DeepSpaceCommand::outpost))
                        .then(Commands.literal("debris").executes(DeepSpaceCommand::debris))
                        .then(Commands.literal("status").executes(DeepSpaceCommand::status))
                        .then(Commands.literal("goto")
                                .then(Commands.literal("hulk").executes(context -> go(context, Kind.HULK)))
                                .then(Commands.literal("outpost").executes(context -> go(context, Kind.OUTPOST)))
                                .then(Commands.literal("debris").executes(context -> go(context, Kind.DEBRIS))))
                        .then(Commands.literal("detonate").executes(DeepSpaceCommand::detonate))
                        .then(Commands.literal("clear").executes(DeepSpaceCommand::clear))));
    }

    /** 面前 {@code distance} 格处（视线方向放平）。 */
    private static Vec3 ahead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) {
            flat = new Vec3(0, 0, 1);
        }
        return player.getEyePosition().add(flat.normalize().scale(distance));
    }

    private static int hulk(CommandContext<CommandSourceStack> context, int length) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        long seed = level.getRandom().nextLong();
        Layout layout = Hulks.layout(seed, length);
        int[] min = layout.min();
        int[] max = layout.max();
        // 侧面朝着玩家摆：船身横在面前
        float yaw = player.getYRot();
        int rotation = Math.floorMod(Math.round(yaw / 90.0F), 4);
        double half = Math.max(max[2] - min[2], max[1] - min[1]) / 2.0 + 14;
        Vec3 center = ahead(player, half);
        DeepSpaceData data = DeepSpaceData.get(level.getServer());
        DeepSpaceData.State state = data.state(data.newCustomKey(), Kind.HULK);
        Hulks.Wreck wreck = Hulks.place(level, Placement.centered(layout, center, rotation), layout, seed, length, state);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.deepspace.hulk", layout.length(), layout.cells().size(),
                wreck.key()), true);
        return layout.cells().size();
    }

    private static int outpost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        long seed = level.getRandom().nextLong();
        DeepSpaceData data = DeepSpaceData.get(level.getServer());
        String key = data.newCustomKey();
        int facing = Math.floorMod(Math.round(player.getYRot() / 90.0F), 4);
        // 局部 +x（核心舱）离玩家远、走廊和仓库朝着玩家
        DeepSpace.placeOutpost(level, ahead(player, 30), facing + 1, seed, data.state(key, Kind.OUTPOST));
        int cells = OutpostLayout.generate(seed).cells().size();
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.deepspace.outpost", cells, key), true);
        return cells;
    }

    private static int debris(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        int count = DebrisFields.spawnLoose(level, ahead(player, 60), level.getRandom().nextLong());
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.deepspace.debris", count), true);
        return count;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            throw NOT_IN_SPACE.create();
        }
        Vec3 at = source.getPosition();
        DeepSpaceData data = DeepSpaceData.get(source.getServer());
        List<Site> sites = DeepSpace.sites(source.getServer(), system.getAsInt());
        for (Site site : sites) {
            String key = DeepSpaceData.key(system.getAsInt(), site.index());
            DeepSpaceData.State state = data.find(key).orElse(null);
            Component kind = Component.translatable("hud.universe.deep_space." + site.kind().key());
            String detail;
            if (site.kind() == Kind.DEBRIS) {
                int total = DebrisFields.slots(site.seed()).size();
                int cleared = state == null ? 0 : state.cleared.cardinality();
                detail = (total - cleared) + "/" + total;
            } else if (site.kind() == Kind.HULK) {
                detail = state == null || !state.built ? "-" : state.integrity.value() + "% " + state.integrity.phase();
            } else {
                detail = state != null && state.built ? "built" : "-";
            }
            int distance = (int) Math.round(site.distanceTo(at.x, at.y, at.z));
            source.sendSuccess(() -> Component.translatable("commands.universe.deepspace.status_line", key, kind, Math.round(site.x()),
                    Math.round(site.y()), Math.round(site.z()), distance, detail), false);
        }
        for (Hulks.Wreck wreck : Hulks.wrecks(level)) {
            if (wreck.key.startsWith("custom/") || wreck.key.startsWith("test/")) {
                int distance = (int) Math.round(wreck.center.distanceTo(at));
                source.sendSuccess(() -> Component.translatable("commands.universe.deepspace.status_line", wreck.key,
                        Component.translatable("hud.universe.deep_space.hulk"), Math.round(wreck.center.x), Math.round(wreck.center.y),
                        Math.round(wreck.center.z), distance, wreck.integrity.value() + "% " + wreck.integrity.phase()), false);
            }
        }
        return sites.size();
    }

    /** 带着坐的船一起飞到最近的某种兴趣点外面。 */
    private static int go(CommandContext<CommandSourceStack> context, Kind kind) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            throw NOT_IN_SPACE.create();
        }
        Vec3 at = player.position();
        Site site = DeepSpace.sites(level.getServer(), system.getAsInt()).stream()
                .filter(s -> s.kind() == kind)
                .min(Comparator.comparingDouble(s -> s.distanceTo(at.x, at.y, at.z)))
                .orElseThrow(NO_WRECK::create);
        Vec3 target = new Vec3(site.x(), site.y(), site.z() - kind.extent() - 30);
        Entity root = player.getRootVehicle();
        root.teleportTo(target.x, target.y, target.z);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.deepspace.goto",
                Component.translatable("hud.universe.deep_space." + kind.key()), Math.round(site.x()), Math.round(site.y()), Math.round(site.z())),
                true);
        return 1;
    }

    private static Hulks.Wreck nearest(CommandSourceStack source) throws CommandSyntaxException {
        Vec3 at = source.getPosition();
        return Hulks.wrecks(source.getLevel()).stream()
                .min(Comparator.comparingDouble(wreck -> wreck.center.distanceToSqr(at)))
                .orElseThrow(NO_WRECK::create);
    }

    private static int detonate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Hulks.Wreck wreck = nearest(context.getSource());
        Hulks.detonate(context.getSource().getLevel(), wreck);
        context.getSource().sendSuccess(() -> Component.translatable("commands.universe.deepspace.detonated", wreck.key), true);
        return 1;
    }

    /** 清掉最近的一艘开发命令放的残骸，或者附近的散碎片。 */
    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int removed = 0;
        for (DebrisChunk chunk : level.getEntitiesOfClass(DebrisChunk.class, new AABB(source.getPosition(), source.getPosition()).inflate(160),
                chunk -> chunk.field() == null)) {
            chunk.discard();
            removed++;
        }
        Vec3 at = source.getPosition();
        Hulks.Wreck wreck = Hulks.wrecks(level).stream()
                .filter(w -> !w.persistent || w.key.startsWith("custom/"))
                .min(Comparator.comparingDouble(w -> w.center.distanceToSqr(at)))
                .orElse(null);
        if (wreck != null) {
            removed += Structures.clear(level, wreck.placement, wreck.layout);
            Hulks.forget(wreck);
            DeepSpaceData.get(source.getServer()).remove(wreck.key);
        }
        int count = removed;
        source.sendSuccess(() -> Component.translatable("commands.universe.deepspace.cleared", count), true);
        return count;
    }
}
