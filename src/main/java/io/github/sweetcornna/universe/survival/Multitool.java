package io.github.sweetcornna.universe.survival;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.survival.LaserHeat;
import io.github.sweetcornna.universe.core.survival.VeinClusters;
import io.github.sweetcornna.universe.network.ScanResultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 多功能工具（玩法设计 §4）的服务端部分。
 *
 * <ul>
 *   <li><b>采矿激光</b>：按住使用键朝 10 格内的方块开火，挖掘速度和铁镐差不多、什么方块都能挖（钻石级的矿除外），
 *       连续开火 4 秒过热，强制冷却 2 秒。挖下来的方块走原版的破坏流程（事件、掉落、经验）。</li>
 *   <li><b>扫描</b>：潜行时按使用键发一次脉冲，把 32 格内的矿石并成矿脉发给客户端，HUD 上标 30 秒。冷却 5 秒。</li>
 * </ul>
 */
public final class Multitool {
    static final double RANGE = 10;
    /** 激光比同等的镐快一半。 */
    static final float LASER_SPEED = 1.5F;
    static final int SCAN_RADIUS = 32;
    static final int SCAN_COOLDOWN = 100;
    private static final int BEAM_COLOR = 0xFF5A3C;
    private static final TagKey<Block> ORES = Tags.BlockItems.ORES.block();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    static final class State {
        final LaserHeat heat = new LaserHeat();
        boolean trigger;
        @Nullable BlockPos target;
        float progress;
        int scanCooldown;
        boolean wasOverheated;
    }

    private Multitool() {
    }

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    static void forget(UUID player) {
        STATES.remove(player);
    }

    /** 物品的 {@code onUseTick} 每刻调一次：玩家按着开火。 */
    public static void trigger(ServerPlayer player) {
        state(player).trigger = true;
    }

    /** 每刻在玩家推进完之后调（{@link PlayerSurvival}）。 */
    static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        boolean trigger = state.trigger && player.getMainHandItem().getItem() instanceof MultitoolItem;
        state.trigger = false;
        boolean firing = state.heat.tick(trigger);
        if (state.scanCooldown > 0) {
            state.scanCooldown--;
        }
        if (firing) {
            fire(player, state);
        } else {
            clearTarget(player, state);
        }
        if (state.heat.overheated() && !state.wasOverheated) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.6F);
            player.sendOverlayMessage(Component.translatable("message.universe.multitool.overheated"));
        }
        state.wasOverheated = state.heat.overheated();
    }

    static float heat(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null ? 0 : state.heat.heat();
    }

    static boolean overheated(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.heat.overheated();
    }

    // ---- 采矿激光

    private static void fire(ServerPlayer player, State state) {
        ServerLevel level = player.level();
        HitResult hit = player.pick(RANGE, 1.0F, false);
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 from = player.getEyePosition().add(look.scale(0.4)).add(right.scale(0.3)).add(0, -0.25, 0);
        Vec3 to = hit.getLocation();
        beam(level, from, to);
        if (player.tickCount % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5F, 2.0F);
        }
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            clearTarget(player, state);
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!pos.equals(state.target)) {
            clearTarget(player, state);
            state.target = pos.immutable();
        }
        BlockState block = level.getBlockState(pos);
        float hardness = block.getDestroySpeed(level, pos);
        ItemStack tool = player.getMainHandItem();
        boolean harvestable = !block.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(block);
        if (hardness < 0 || !harvestable || !level.mayInteract(player, pos)) {
            // 挖不动：只溅火花
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 2, 0.05, 0.05, 0.05, 0.02);
            return;
        }
        state.progress += hardness == 0 ? 1 : tool.getDestroySpeed(block) / hardness / 30F * LASER_SPEED;
        level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
        if (state.progress >= 1) {
            level.destroyBlockProgress(crackId(player), pos, -1);
            state.target = null;
            state.progress = 0;
            player.gameMode.destroyBlock(pos);
        } else {
            level.destroyBlockProgress(crackId(player), pos, Mth.clamp((int) (state.progress * 10), 0, 9));
        }
    }

    private static void beam(ServerLevel level, Vec3 from, Vec3 to) {
        DustParticleOptions dust = new DustParticleOptions(BEAM_COLOR, 0.6F);
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) (length / 0.35));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private static void clearTarget(ServerPlayer player, State state) {
        if (state.target != null) {
            player.level().destroyBlockProgress(crackId(player), state.target, -1);
            state.target = null;
        }
        state.progress = 0;
    }

    /** 裂纹的编号：和玩家自己左键挖的裂纹分开。 */
    private static int crackId(ServerPlayer player) {
        return ~player.getId();
    }

    // ---- 扫描

    public static void scan(ServerPlayer player) {
        State state = state(player);
        if (state.scanCooldown > 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.multitool.charging"));
            return;
        }
        state.scanCooldown = SCAN_COOLDOWN;
        ServerLevel level = player.level();
        List<ScanResultPayload.Marker> markers = scanOres(level, player.blockPosition(), SCAN_RADIUS);
        PacketDistributor.sendToPlayer(player, new ScanResultPayload(markers));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(angle) * 1.5, player.getY() + 0.2,
                    player.getZ() + Math.sin(angle) * 1.5, 1, 0, 0, 0, 0);
        }
        int total = markers.stream().mapToInt(ScanResultPayload.Marker::count).sum();
        player.sendOverlayMessage(Component.translatable("message.universe.multitool.scanned", markers.size(), total));
    }

    /** 半径内已加载区块里的矿石，按矿脉合并，由近到远。 */
    static List<ScanResultPayload.Marker> scanOres(ServerLevel level, BlockPos center, int radius) {
        VeinClusters clusters = new VeinClusters();
        List<Block> kinds = new ArrayList<>();
        Map<Block, Integer> ids = new HashMap<>();
        int radiusSq = radius * radius;
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
                    if (section.hasOnlyAir() || baseY + 15 < center.getY() - radius || baseY > center.getY() + radius
                            || !section.maybeHas(state -> state.is(ORES))) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!state.is(ORES)) {
                                    continue;
                                }
                                int wx = (cx << 4) + x;
                                int wy = baseY + y;
                                int wz = (cz << 4) + z;
                                if (center.distSqr(new BlockPos(wx, wy, wz)) > radiusSq) {
                                    continue;
                                }
                                int kind = ids.computeIfAbsent(state.getBlock(), block -> {
                                    kinds.add(block);
                                    return kinds.size() - 1;
                                });
                                clusters.add(wx, wy, wz, kind);
                            }
                        }
                    }
                }
            }
        }
        List<ScanResultPayload.Marker> markers = new ArrayList<>();
        for (VeinClusters.Vein vein : clusters.nearest(center.getX(), center.getY(), center.getZ(), ScanResultPayload.MAX_MARKERS)) {
            markers.add(new ScanResultPayload.Marker(new BlockPos(vein.x(), vein.y(), vein.z()),
                    BuiltInRegistries.BLOCK.getKey(kinds.get(vein.kind())), vein.count()));
        }
        return markers;
    }
}
