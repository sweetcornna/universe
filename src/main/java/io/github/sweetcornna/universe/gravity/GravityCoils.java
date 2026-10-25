package io.github.sweetcornna.universe.gravity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.gravity.GravityRules;
import io.github.sweetcornna.universe.core.gravity.GravityRules.Weight;
import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.darkfog.entity.DarkFogRaider;
import io.github.sweetcornna.universe.deepspace.DebrisChunk;
import io.github.sweetcornna.universe.entity.civ.AlienNpc;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.mecha.fleet.FleetUnit;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.survival.Multitool;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 引力抓取器（ADR-0034 §七）：多功能工具的附件。按住右键抓准星对着的东西，悬在面前跟着视线走；松开放下；
 * 抓着时左键扔出去，撞到别的生物按速度和重量伤它；潜行左键把前方锥形里没固定的东西推开。都在服务端判定。
 */
public final class GravityCoils {
    /** 放下以后多少刻里不让人马上捡（不然悬在脚边的掉落物一松手就进背包了，也算放下了）。 */
    private static final int DROP_PICKUP_DELAY = 10;
    /** 扔出去的东西最多追踪多少刻。 */
    private static final int THROWN_LIFE = 60;
    /** 推一次以后多少刻才能再推。 */
    private static final int PUSH_COOLDOWN = 12;
    /** 抓着的东西离该在的点超过这么远（卡在墙后面了）就松手。 */
    private static final double SNAP = GravityRules.REACH + 4;
    /** 失重时扔、推的反冲（格/刻，按扔出去的东西的重量再乘）。 */
    private static final double RECOIL = 0.12;

    private static final class Hold {
        /** 抓着的人：直接记着，不去玩家列表里查（假玩家不在列表里）；重生、换维度以后对不上了就松手。 */
        final ServerPlayer player;
        final Entity target;
        final Weight weight;
        final boolean noGravity;
        boolean refreshed = true;
        int ticks;

        Hold(ServerPlayer player, Entity target, Weight weight) {
            this.player = player;
            this.target = target;
            this.weight = weight;
            this.noGravity = target.isNoGravity();
        }
    }

    private record Thrown(Entity entity, @Nullable ServerPlayer thrower, Weight weight, int[] life, Set<Integer> hit) {
    }

    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    /** 扔完以后要先松开右键才能再抓（不然扔出去的东西下一刻又被抓回来）。 */
    private static final Set<UUID> NEED_RELEASE = new HashSet<>();
    private static final Map<UUID, Long> PUSHED = new HashMap<>();
    private static final List<Thrown> THROWN = new ArrayList<>();

    private GravityCoils() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                release(player, false);
                NEED_RELEASE.remove(player.getUUID());
                PUSHED.remove(player.getUUID());
            }
        });
        gameBus.addListener(PlayerEvent.StartTracking.class, event -> {
            if (event.getEntity() instanceof ServerPlayer tracker) {
                startTracking(tracker, event.getTarget());
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            HOLDS.clear();
            NEED_RELEASE.clear();
            PUSHED.clear();
            THROWN.clear();
        });
    }

    /** 拿着多功能工具、切在引力抓取器上。 */
    public static boolean active(Player player) {
        return player.getMainHandItem().getItem() instanceof MultitoolItem && Multitool.activeMode(player.getMainHandItem()) == ToolMode.GRAVITY;
    }

    /** 这个实体正被谁的引力抓取器抓着（牵引光束就不去抢它）。 */
    public static boolean held(Entity entity) {
        for (Hold hold : HOLDS.values()) {
            if (hold.target == entity) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable Entity holding(ServerPlayer player) {
        Hold hold = HOLDS.get(player.getUUID());
        return hold == null ? null : hold.target;
    }

    // ---- 抓

    /** 多功能工具每刻调（切在引力抓取器上时）：{@code trigger} 是这一刻按没按着右键。 */
    public static void hold(ServerPlayer player, boolean trigger) {
        UUID id = player.getUUID();
        if (!trigger) {
            NEED_RELEASE.remove(id);
            release(player, true);
            return;
        }
        if (NEED_RELEASE.contains(id)) {
            return;
        }
        Hold hold = HOLDS.get(id);
        if (hold == null) {
            Entity target = aim(player);
            if (target == null) {
                return;
            }
            hold = grab(player, target);
        }
        hold.refreshed = true;
    }

    /** 准星对着的、抓得动的东西（被方块挡住的不算）。 */
    static @Nullable Entity aim(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(GravityRules.REACH));
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limit = block.getType() == HitResult.Type.MISS ? GravityRules.REACH : block.getLocation().distanceTo(eye);
        end = eye.add(player.getLookAngle().scale(limit));
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(player, new AABB(eye, end).inflate(1.0), e -> grabbable(player, e) && !held(e))) {
            // 掉落物、小碎片太小，判定框放宽一点，好瞄
            double grace = entity instanceof ItemEntity || entity instanceof ExperienceOrb ? 0.45 : 0.15;
            var hit = entity.getBoundingBox().inflate(grace).clip(eye, end);
            if (hit.isPresent()) {
                double distance = hit.get().distanceTo(eye);
                if (distance < bestDistance) {
                    best = entity;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** {@code player} 抓不抓得动 {@code entity}：别人的舰队单位不让抓（ADR-0033：别的玩家拿不走你的单位）。 */
    public static boolean grabbable(ServerPlayer player, Entity entity) {
        if (entity instanceof FleetUnit unit && !player.getUUID().equals(unit.ownerId())) {
            return false;
        }
        return weight(entity) != null;
    }

    /** 抓不抓得动、按哪档重量算；抓不动为空。 */
    static @Nullable Weight weight(Entity entity) {
        if (!entity.isAlive()) {
            return null;
        }
        if (entity instanceof ItemEntity) {
            return Weight.ITEM;
        }
        if (entity instanceof DebrisChunk chunk) {
            return chunk.small() ? Weight.DEBRIS : null;
        }
        if (entity instanceof Sentinel sentinel) {
            SentinelKind kind = sentinel.kind();
            return kind == SentinelKind.DRONE || kind == SentinelKind.REPAIR || kind == SentinelKind.SUMMONER ? Weight.DRONE : null;
        }
        if (entity instanceof DarkFogRaider) {
            return Weight.DRONE;
        }
        if (entity instanceof LivingEntity living && !(living instanceof Player) && !(living instanceof ArmorStand) && !(living instanceof AlienNpc)
                && !living.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES) && !living.isPassenger() && !living.isVehicle()
                && GravityRules.liftable(living.getBbWidth(), living.getBbHeight())) {
            return Weight.CREATURE;
        }
        return null;
    }

    private static Hold grab(ServerPlayer player, Entity target) {
        Hold hold = new Hold(player, target, weight(target));
        HOLDS.put(player.getUUID(), hold);
        target.setNoGravity(true);
        if (target instanceof Sentinel sentinel) {
            sentinel.grabbed(player);
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        ServerLevel level = player.level();
        level.playSound(null, target.getX(), target.getY(), target.getZ(), DeepSpaceSounds.GRAVITY_GRAB.get(), SoundSource.PLAYERS, 0.8F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        broadcast(player, new GravityPayloads.Beam(player.getId(), target.getId()));
        return hold;
    }

    /**
     * 松手。
     *
     * @param gently 轻轻放下（右键松开）；否则是被迫松开的（下线、东西没了）
     */
    public static void release(ServerPlayer player, boolean gently) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) {
            return;
        }
        Entity target = hold.target;
        target.setNoGravity(hold.noGravity);
        if (target.isAlive()) {
            target.setDeltaMovement(target.getDeltaMovement().scale(0.2));
            target.needsSync = true;
            target.resetFallDistance();
            if (target instanceof ItemEntity item) {
                item.setPickUpDelay(DROP_PICKUP_DELAY);
            }
            if (gently) {
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(), DeepSpaceSounds.GRAVITY_DROP.get(), SoundSource.PLAYERS,
                        0.6F, 1.0F);
            }
        }
        broadcast(player, new GravityPayloads.Beam(player.getId(), -1));
    }

    // ---- 扔、推

    public static void handleAction(GravityPayloads.Action payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive() || !active(player)) {
            return;
        }
        if (payload.push()) {
            push(player);
        } else {
            throwHeld(player);
        }
    }

    /** 抓着的东西朝视线方向扔出去。 */
    public static boolean throwHeld(ServerPlayer player) {
        Hold hold = HOLDS.remove(player.getUUID());
        if (hold == null) {
            return false;
        }
        NEED_RELEASE.add(player.getUUID());
        Entity target = hold.target;
        target.setNoGravity(hold.noGravity);
        Vec3 look = player.getLookAngle();
        target.setDeltaMovement(look.scale(GravityRules.THROW_SPEED));
        target.needsSync = true;
        target.syncVelocity = true;
        if (target instanceof ItemEntity item) {
            item.setPickUpDelay(40);
        }
        if (target instanceof Sentinel sentinel) {
            sentinel.stun(GravityRules.SENTINEL_STUN);
        }
        THROWN.add(new Thrown(target, player, hold.weight, new int[] {THROWN_LIFE}, new HashSet<>()));
        ServerLevel level = player.level();
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), DeepSpaceSounds.GRAVITY_THROW.get(), SoundSource.PLAYERS, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        recoil(player, look, hold.weight.damage() / Weight.DRONE.damage());
        broadcast(player, new GravityPayloads.Beam(player.getId(), -1));
        return true;
    }

    /** 往前方锥形里推一下：没固定的东西都推开。抓着东西时不推。 */
    public static int push(ServerPlayer player) {
        if (HOLDS.containsKey(player.getUUID())) {
            return 0;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = PUSHED.get(player.getUUID());
        if (last != null && now - last < PUSH_COOLDOWN) {
            return 0;
        }
        PUSHED.put(player.getUUID(), now);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double[] origin = {eye.x, eye.y, eye.z};
        double[] dir = {look.x, look.y, look.z};
        int pushed = 0;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(GravityRules.PUSH_RANGE + 1), GravityCoils::pushable)) {
            Vec3 at = entity.getBoundingBox().getCenter();
            if (!GravityRules.inCone(origin, dir, new double[] {at.x, at.y, at.z})) {
                continue;
            }
            double distance = at.distanceTo(eye);
            Vec3 away = distance < 1e-3 ? look : at.subtract(eye).normalize();
            double speed = GravityRules.pushSpeed(distance) * resistance(entity);
            entity.push(away.scale(speed).add(0, entity.onGround() ? 0.2 : 0, 0));
            entity.syncVelocity = true;
            if (entity instanceof Sentinel sentinel) {
                sentinel.stun(10);
            }
            pushed++;
        }
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), DeepSpaceSounds.GRAVITY_PUSH.get(), SoundSource.PLAYERS, 1.0F,
                0.95F + level.getRandom().nextFloat() * 0.1F);
        recoil(player, look, 0.6);
        broadcast(player, new GravityPayloads.Pushed(player.getId()));
        return pushed;
    }

    /** 推得动的：没固定的东西（掉落物、经验球、碎片、生物、矿车和船），不推别的玩家、头目、飞船。 */
    private static boolean pushable(Entity entity) {
        if (!entity.isAlive() || entity instanceof Player || entity instanceof ShipEntity || entity.isPassenger()) {
            return false;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof DebrisChunk || entity instanceof VehicleEntity) {
            return true;
        }
        return entity instanceof LivingEntity living && !(living instanceof ArmorStand) && !living.getType().builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
    }

    /** 越重越推不动：大碎片、抗击退的生物。 */
    private static double resistance(Entity entity) {
        if (entity instanceof DebrisChunk chunk) {
            return switch (chunk.size()) {
                case SMALL -> 0.8;
                case MEDIUM -> 0.35;
                case LARGE -> 0.1;
            };
        }
        if (entity instanceof LivingEntity living) {
            double size = Math.max(living.getBbWidth(), living.getBbHeight());
            double bulk = size <= GravityRules.MAX_SIZE ? 0.7 : 0.7 * GravityRules.MAX_SIZE / size;
            return bulk * (1 - Math.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1));
        }
        return 1.0;
    }

    /** 失重时扔东西、推东西，人往后退（动量守恒）。 */
    private static void recoil(ServerPlayer player, Vec3 look, double mass) {
        if (player.getGravity() > 1e-4 || player.isPassenger()) {
            return;
        }
        player.push(look.scale(-RECOIL * mass));
        player.syncVelocity = true;
    }

    /**
     * 有人新看见了抓着东西的人或者被抓着的东西：把引力束补发给他（抓的那一下只发给了当时看得见的人）。两头都补，
     * 后看见的那一头补发时两个实体客户端都有了。
     */
    private static void startTracking(ServerPlayer tracker, Entity entity) {
        if (tracker.connection == null) {
            return;
        }
        for (Hold hold : HOLDS.values()) {
            if (hold.player == entity || hold.target == entity) {
                PacketDistributor.sendToPlayer(tracker, new GravityPayloads.Beam(hold.player.getId(), hold.target.getId()));
            }
        }
    }

    /** 发给看得见这名玩家的人和他自己（游戏内测试的假玩家没有连接，不发）。 */
    private static void broadcast(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        }
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        if (!HOLDS.isEmpty()) {
            Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator();
            List<ServerPlayer> lost = new ArrayList<>();
            while (it.hasNext()) {
                Hold hold = it.next().getValue();
                ServerPlayer player = hold.player;
                if (player.isRemoved() && player.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION) {
                    hold.target.setNoGravity(hold.noGravity);
                    it.remove();
                    continue;
                }
                if (!hold.refreshed || !follow(player, hold)) {
                    lost.add(player);
                }
                hold.refreshed = false;
            }
            for (ServerPlayer player : lost) {
                release(player, false);
            }
        }
        if (!THROWN.isEmpty()) {
            THROWN.removeIf(GravityCoils::flying);
        }
    }

    /** 抓着的东西跟着视线走；返回还抓不抓得住。 */
    private static boolean follow(ServerPlayer player, Hold hold) {
        Entity target = hold.target;
        if (!target.isAlive() || target.level() != player.level() || !player.isAlive() || !active(player)) {
            return false;
        }
        hold.ticks++;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // 大一点的东西放远一点，免得贴着脸
        double reach = GravityRules.HOLD_DISTANCE + target.getBbWidth() * 0.5;
        Vec3 point = eye.add(look.scale(reach));
        Vec3 center = target.getBoundingBox().getCenter();
        if (center.distanceTo(point) > SNAP) {
            return false;
        }
        double[] v = GravityRules.follow(new double[] {center.x, center.y, center.z}, new double[] {point.x, point.y, point.z});
        target.setDeltaMovement(v[0], v[1], v[2]);
        target.setNoGravity(true);
        target.resetFallDistance();
        target.needsSync = true;
        if (target instanceof Sentinel sentinel) {
            sentinel.stun(5);
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        if (hold.ticks % 6 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(0xB58CFF, 0.7F), center.x, center.y, center.z, 2, target.getBbWidth() * 0.4,
                    target.getBbHeight() * 0.4, target.getBbWidth() * 0.4, 0);
        }
        return true;
    }

    /** 扔出去的东西这一刻撞没撞上别的生物；返回 true 表示不用再追了。 */
    private static boolean flying(Thrown thrown) {
        Entity entity = thrown.entity;
        if (!entity.isAlive() || --thrown.life[0] <= 0 || !(entity.level() instanceof ServerLevel level)) {
            return true;
        }
        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length();
        if (speed < GravityRules.IMPACT_SPEED) {
            return true;
        }
        AABB sweep = entity.getBoundingBox().expandTowards(motion).inflate(0.3);
        boolean struck = false;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e != entity && e != thrown.thrower && e.isAlive() && !thrown.hit.contains(e.getId()))) {
            float damage = (float) GravityRules.impact(thrown.weight, speed);
            if (damage <= 0) {
                continue;
            }
            thrown.hit.add(victim.getId());
            victim.hurtServer(level, level.damageSources().thrown(entity, thrown.thrower), damage);
            victim.push(motion.normalize().scale(0.4 + 0.2 * thrown.weight.damage() / Weight.DRONE.damage()));
            victim.syncVelocity = true;
            if (victim instanceof Sentinel sentinel) {
                sentinel.stun(GravityRules.SENTINEL_STUN / 2);
            }
            struck = true;
        }
        // 扔出去的生物、无人机自己撞墙也疼
        if (!struck && entity instanceof LivingEntity living && (living.horizontalCollision || living.verticalCollision)) {
            float damage = (float) GravityRules.impact(thrown.weight, speed) * 0.5F;
            if (damage > 0) {
                living.hurtServer(level, level.damageSources().flyIntoWall(), damage);
            }
            struck = true;
        }
        if (struck) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), DeepSpaceSounds.GRAVITY_IMPACT.get(), SoundSource.PLAYERS, 1.0F,
                    0.9F + level.getRandom().nextFloat() * 0.2F);
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY(0.5), entity.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
            entity.setDeltaMovement(motion.scale(0.3));
            entity.needsSync = true;
            return true;
        }
        return false;
    }
}
