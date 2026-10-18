package io.github.sweetcornna.universe.survival;

import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.sentinel.Sentinels;
import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.network.ToolModePayload;
import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.core.survival.PlasmaWeapon;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.poi.Pois;
import io.github.sweetcornna.universe.poi.PoiProgress;
import io.github.sweetcornna.universe.core.survival.LaserHeat;
import io.github.sweetcornna.universe.core.survival.VeinClusters;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.network.ScanResultPayload;
import io.github.sweetcornna.universe.tech.SuitTech;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.ProjectileUtil;
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
 *   <li><b>战斗模式</b>（ADR-0014）：G 键切换，按住使用键连发等离子弹，和采矿激光共用热量；扫描两种模式都能用。</li>
 * </ul>
 */
public final class Multitool {
    static final double RANGE = 10;
    /** 激光比同等的镐快一半。 */
    static final float LASER_SPEED = 1.5F;
    static final int SCAN_RADIUS = 32;
    static final int SCAN_COOLDOWN = 100;
    /** 扫描顺带标出多远以内的兴趣点（ADR-0014）。 */
    static final int POI_RADIUS = 200;
    private static final int BEAM_COLOR = 0xFF5A3C;
    /** 战斗模式瞄多远：弹体从右手边打出，朝准星对着的点飞，近处也不会偏到准星右边。 */
    static final double AIM_RANGE = 60;
    private static final TagKey<Block> ORES = Tags.BlockItems.ORES.block();
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    static final class State {
        final LaserHeat heat = new LaserHeat();
        final PlasmaWeapon weapon = new PlasmaWeapon();
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
        ItemStack tool = player.getMainHandItem();
        boolean holding = tool.getItem() instanceof MultitoolItem;
        boolean trigger = state.trigger && holding;
        state.trigger = false;
        boolean combat = holding && mode(tool) == ToolMode.COMBAT;
        TechBonuses bonuses = trigger ? SuitTech.tool(tool) : TechBonuses.NONE;
        float heatFactor = (float) bonuses.reduction(Stat.COOLING) * (combat ? PlasmaWeapon.HEAT_FACTOR : 1);
        boolean firing = state.heat.tick(trigger, heatFactor);
        if (state.scanCooldown > 0) {
            state.scanCooldown--;
        }
        if (combat) {
            clearTarget(player, state);
            int shots = state.weapon.tick(firing, bonuses.boost(Stat.FIRE_RATE));
            for (int i = 0; i < shots; i++) {
                shoot(player, bonuses);
            }
        } else if (firing) {
            fire(player, state, bonuses);
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

    // ---- 模式

    public static ToolMode mode(ItemStack tool) {
        return tool.getOrDefault(UniverseDataComponents.TOOL_MODE.get(), ToolMode.MINING);
    }

    /** 按了切换键：主手的多功能工具在采矿和战斗模式之间切换。 */
    public static void toggleMode(ServerPlayer player) {
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof MultitoolItem)) {
            return;
        }
        ToolMode mode = mode(tool).next();
        tool.set(UniverseDataComponents.TOOL_MODE.get(), mode);
        clearTarget(player, state(player));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.7F,
                mode == ToolMode.COMBAT ? 1.5F : 1.0F);
        player.sendOverlayMessage(Component.translatable("message.universe.multitool.mode." + mode.name().toLowerCase(Locale.ROOT)));
    }

    public static void handleToggleMode(ToolModePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            toggleMode(player);
        }
    }

    // ---- 等离子弹

    private static void shoot(ServerPlayer player, TechBonuses bonuses) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 aim = aimPoint(player, eye, look);
        Vec3 from;
        Vec3 direction;
        if (aim.distanceToSqr(eye) < 4) {
            // 贴着东西开火：从正前方打出去，免得弹体从侧面绕开目标
            from = eye.add(look.scale(0.3));
            direction = look;
        } else {
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            from = eye.add(look.scale(0.5)).add(right.scale(0.25)).add(0, -0.2, 0);
            direction = aim.subtract(from);
        }
        level.addFreshEntity(PlasmaBolt.shoot(level, player, from, direction, PlasmaWeapon.damage(bonuses.boost(Stat.DAMAGE)), PlasmaBolt.PLAYER_COLOR));
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.3F,
                1.8F + player.getRandom().nextFloat() * 0.2F);
        // 火花放在枪口前一格多：贴着镜头的粒子会大得遮住视野
        Vec3 flash = from.add(direction.normalize().scale(1.2));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, flash.x, flash.y, flash.z, 1, 0.02, 0.02, 0.02, 0.02);
    }

    /** 准星对着的点：先找方块，再找这段视线上最近的实体。 */
    private static Vec3 aimPoint(ServerPlayer player, Vec3 eye, Vec3 look) {
        HitResult block = player.pick(AIM_RANGE, 1.0F, false);
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(AIM_RANGE)) : block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                target -> !target.isSpectator() && target.isPickable(), eye.distanceToSqr(end));
        return entity == null ? end : entity.getLocation();
    }

    // ---- 采矿激光

    private static void fire(ServerPlayer player, State state, TechBonuses bonuses) {
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
        state.progress += hardness == 0 ? 1 : tool.getDestroySpeed(block) / hardness / 30F * LASER_SPEED * (float) bonuses.boost(Stat.MINING);
        level.sendParticles(ParticleTypes.SMOKE, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
        if (state.progress >= 1) {
            level.destroyBlockProgress(crackId(player), pos, -1);
            state.target = null;
            state.progress = 0;
            boolean ore = block.is(ORES);
            if (player.gameMode.destroyBlock(pos)) {
                // 守卫机盯着用激光大量采矿的人（ADR-0014）
                Sentinels.offend(player, ore ? Offense.LASER_MINING_ORE : Offense.LASER_MINING);
            }
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
        TechBonuses bonuses = SuitTech.tool(player.getMainHandItem());
        state.scanCooldown = (int) Math.round(SCAN_COOLDOWN * bonuses.scanCooldown());
        ServerLevel level = player.level();
        int radius = (int) Math.round(SCAN_RADIUS * bonuses.boost(Stat.SCAN_RADIUS));
        List<ScanResultPayload.Marker> markers = scanOres(level, player.blockPosition(), radius);
        List<ScanResultPayload.PoiMarker> pois = scanPois(player, (int) Math.round(POI_RADIUS * bonuses.boost(Stat.SCAN_RADIUS)));
        PacketDistributor.sendToPlayer(player, new ScanResultPayload(markers, pois));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(angle) * 1.5, player.getY() + 0.2,
                    player.getZ() + Math.sin(angle) * 1.5, 1, 0, 0, 0, 0);
        }
        int total = markers.stream().mapToInt(ScanResultPayload.Marker::count).sum();
        player.sendOverlayMessage(pois.isEmpty()
                ? Component.translatable("message.universe.multitool.scanned", markers.size(), total)
                : Component.translatable("message.universe.multitool.scanned_poi", markers.size(), total, pois.size()));
    }

    /** 半径内的兴趣点（ADR-0014）：位置由星球种子算，不用加载区块；已经加载的区块用真实地面的高度。 */
    static List<ScanResultPayload.PoiMarker> scanPois(ServerPlayer player, int radius) {
        ServerLevel level = player.level();
        PoiProgress progress = player.getData(UniverseAttachments.POI_PROGRESS);
        return Pois.layout(level).map(layout -> layout.within(player.getX(), player.getZ(), radius).stream()
                .limit(ScanResultPayload.MAX_POIS)
                .map(poi -> new ScanResultPayload.PoiMarker(new BlockPos(poi.x(), Pois.groundY(level, layout, poi), poi.z()),
                        poi.type(), progress.visited(poi.key())))
                .toList()).orElse(List.of());
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
