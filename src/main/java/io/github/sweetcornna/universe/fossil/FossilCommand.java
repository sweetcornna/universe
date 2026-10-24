package io.github.sweetcornna.universe.fossil;

import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BoneFamily;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkLayout;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /universe fossil ...}：调试化石（ADR-0034 §三）。"看着的展台"是准星对着的骨架展台或化石底座（6 格以内）。
 *
 * <ul>
 *   <li>{@code give <类群> <部位>}：给一块骨头；{@code exhibit <类群>}：给一件同一类群的完整展品；</li>
 *   <li>{@code put <类群> <部位>}：把骨头直接放到看着的展台上；{@code fill <类群>}：看着的骨架展台放满同一类群；</li>
 *   <li>{@code open}：打开看着的骨架展台；{@code pack}：把看着的骨架展台封装成展品给自己；</li>
 *   <li>{@code dig}：当作在脚下挖了一块化石土，吵附近睡着的石哨兵；{@code sentry}：面前放一个醒着的石哨兵；</li>
 *   <li>{@code collector}：面前放一个化石收藏家；{@code locate}：最近的发掘场；{@code goto}：传送到最近的发掘场边上。</li>
 * </ul>
 */
public final class FossilCommand {
    private static final double REACH = 6;
    private static final double LOCATE_RANGE = 8000;

    private FossilCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("give");
        LiteralArgumentBuilder<CommandSourceStack> put = Commands.literal("put");
        LiteralArgumentBuilder<CommandSourceStack> exhibit = Commands.literal("exhibit");
        LiteralArgumentBuilder<CommandSourceStack> fill = Commands.literal("fill");
        for (BoneFamily family : BoneFamily.values()) {
            LiteralArgumentBuilder<CommandSourceStack> giveFamily = Commands.literal(family.id());
            LiteralArgumentBuilder<CommandSourceStack> putFamily = Commands.literal(family.id());
            for (BonePart part : BonePart.values()) {
                Bone bone = new Bone(family, part);
                giveFamily.then(Commands.literal(part.id()).executes(context -> give(context.getSource(), bone)));
                putFamily.then(Commands.literal(part.id()).executes(context -> put(context.getSource(), bone)));
            }
            give.then(giveFamily);
            put.then(putFamily);
            exhibit.then(Commands.literal(family.id()).executes(context -> exhibit(context.getSource(), family)));
            fill.then(Commands.literal(family.id()).executes(context -> fill(context.getSource(), family)));
        }
        LiteralArgumentBuilder<CommandSourceStack> fossil = Commands.literal("fossil")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(give).then(put).then(exhibit).then(fill)
                .then(Commands.literal("open").executes(context -> open(context.getSource())))
                .then(Commands.literal("pack").executes(context -> pack(context.getSource())))
                .then(Commands.literal("dig").executes(context -> dig(context.getSource())))
                .then(Commands.literal("sentry").executes(context -> sentry(context.getSource())))
                .then(Commands.literal("collector").executes(context -> collector(context.getSource())))
                .then(Commands.literal("locate").executes(context -> locate(context.getSource())))
                .then(Commands.literal("goto").executes(context -> teleport(context.getSource())));
        dispatcher.register(Commands.literal(Universe.MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(fossil));
    }

    private static int give(CommandSourceStack source, Bone bone) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Fossils.give(player, new ItemStack(UniverseItems.BONES.get(bone).get()));
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.given", FossilNames.family(bone.family()), FossilNames.part(bone)),
                false);
        return 1;
    }

    private static int exhibit(CommandSourceStack source, BoneFamily family) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Skeleton skeleton = Skeleton.pure(family);
        Fossils.give(player, FossilExhibitItem.of(skeleton));
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.exhibit", FossilNames.skeleton(skeleton)), false);
        return 1;
    }

    private static int put(CommandSourceStack source, Bone bone) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = looked(player);
        if (pos != null && player.level().getBlockEntity(pos) instanceof SkeletonDisplayBlockEntity display) {
            if (!display.insert(bone)) {
                display.setSkeleton(display.skeleton().with(bone));
            }
        } else if (pos != null && player.level().getBlockEntity(pos) instanceof FossilPedestalBlockEntity pedestal) {
            pedestal.setBone(bone);
        } else {
            source.sendFailure(Component.translatable("commands.universe.fossil.no_display"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.put", FossilNames.family(bone.family()), FossilNames.part(bone)),
                false);
        return 1;
    }

    private static int fill(CommandSourceStack source, BoneFamily family) throws CommandSyntaxException {
        SkeletonDisplayBlockEntity display = display(source);
        if (display == null) {
            return 0;
        }
        display.setSkeleton(Skeleton.pure(family));
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.filled", FossilNames.skeleton(display.skeleton())), false);
        return 1;
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        SkeletonDisplayBlockEntity display = display(source);
        if (display == null) {
            return 0;
        }
        SkeletonDisplayBlock.open(source.getPlayerOrException(), display);
        return 1;
    }

    private static int pack(CommandSourceStack source) throws CommandSyntaxException {
        SkeletonDisplayBlockEntity display = display(source);
        if (display == null) {
            return 0;
        }
        Skeleton skeleton = display.skeleton();
        ItemStack exhibit = display.pack();
        if (exhibit.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.fossil.empty"));
            return 0;
        }
        Fossils.give(source.getPlayerOrException(), exhibit);
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.packed", FossilNames.skeleton(skeleton)), false);
        return 1;
    }

    private static int dig(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int count = DigSites.disturb(player.level(), player.blockPosition(), player);
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.dug", count), false);
        return count;
    }

    private static int sentry(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos at = ground(level, ahead(player, 6));
        StoneSentry sentry = DigSites.wake(level, at, UniverseBlocks.DORMANT_STONE_SENTRY.get().defaultBlockState(), player);
        if (sentry == null) {
            source.sendFailure(Component.translatable("commands.universe.fossil.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.sentry"), false);
        return 1;
    }

    private static int collector(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        BlockPos at = ground(level, ahead(player, 3));
        AlienNpc npc = UniverseEntities.ALIEN_NPC.get().create(level, EntitySpawnReason.COMMAND);
        if (npc == null) {
            return 0;
        }
        Race race = Civ.dominant(level).orElse(Race.values()[0]);
        long seed = player.getRandom().nextLong();
        NameGenerator.Name name = Civ.tongue(level.getServer(), race).name(seed);
        Vec3 pos = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        float yaw = player.getYRot() + 180;
        npc.setup(race, NpcRole.FOSSIL_COLLECTOR, seed, "command:collector:" + seed, name, pos, yaw);
        level.addFreshEntity(npc);
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.collector"), false);
        return 1;
    }

    private static int locate(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<LandmarkLayout> layout = Landmarks.layout(player.level());
        Optional<Landmark> site = layout.flatMap(l -> l.nearest(LandmarkType.DIG_SITE, player.getX(), player.getZ(), LOCATE_RANGE));
        if (site.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.fossil.none"));
            return 0;
        }
        Landmark landmark = site.get();
        int distance = (int) Math.round(Math.hypot(landmark.x() - player.getX(), landmark.z() - player.getZ()));
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.located", landmark.x(), landmark.z(), distance), false);
        return 1;
    }

    /** 传送到最近的发掘场：站在南边，朝北看着那片化石土。 */
    private static int teleport(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();
        Optional<Landmark> site = Landmarks.layout(level).flatMap(l -> l.nearest(LandmarkType.DIG_SITE, player.getX(), player.getZ(), LOCATE_RANGE));
        if (site.isEmpty()) {
            source.sendFailure(Component.translatable("commands.universe.fossil.none"));
            return 0;
        }
        int x = site.get().x();
        int z = site.get().z() + 12;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), 180, 20, true);
        source.sendSuccess(() -> Component.translatable("commands.universe.fossil.located", site.get().x(), site.get().z(), 0), false);
        return 1;
    }

    // ---- 工具

    private static SkeletonDisplayBlockEntity display(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = looked(player);
        if (pos != null && player.level().getBlockEntity(pos) instanceof SkeletonDisplayBlockEntity display) {
            return display;
        }
        source.sendFailure(Component.translatable("commands.universe.fossil.no_display"));
        return null;
    }

    private static BlockPos looked(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
    }

    private static Vec3 ahead(ServerPlayer player, double distance) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        return player.position().add(look.scale(distance));
    }

    private static BlockPos ground(ServerLevel level, Vec3 at) {
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
