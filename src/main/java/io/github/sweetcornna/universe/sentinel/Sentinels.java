package io.github.sweetcornna.universe.sentinel;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.core.sentinel.Wanted;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 守卫机的调度（ADR-0014）。
 *
 * <ul>
 *   <li>有守卫的星球上，按守卫强度在每个玩家附近保持几架巡逻的守卫机（玩家在地下深处时不补）。</li>
 *   <li>违规行为（{@link Offense}）提高玩家的警戒值；通缉后附近的守卫机都转为敌对，并按级别从远处传送来增援，
 *       三级通缉有重型守卫机。</li>
 *   <li>敌对的守卫机都看不见玩家 20 秒、离开星球或者死亡，通缉解除，追捕的守卫机撤退。</li>
 *   <li>创造、旁观模式和和平难度下不会被通缉。</li>
 * </ul>
 */
public final class Sentinels {
    /** 多远以内的守卫机算"附近"：巡逻补到这个数、通缉时都转为敌对。 */
    static final double NEAR = 64;
    /** 敌对的守卫机在多远以内、有视线才算"看见"玩家。 */
    static final double SIGHT = 48;
    /** 两次补巡逻之间至少隔多久（刻）；巡逻的守卫机被打掉后隔得更久。 */
    static final int PATROL_INTERVAL = 400;
    static final int PATROL_AFTER_LOSS = 1200;
    /** 两波增援之间隔多久（刻）。 */
    static final int REINFORCE_INTERVAL = 300;
    private static final Map<ResourceKey<Level>, SentinelLevel> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /** 不存档的每个玩家的计时。 */
    private static final class Session {
        int patrolCooldown = 200;
        int reinforceCooldown;
        boolean seen;
    }

    private Sentinels() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                tick(player);
            }
        });
        gameBus.addListener(LivingDeathEvent.class, Sentinels::onDeath);
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SESSIONS.remove(event.getEntity().getUUID()));
    }

    /** 这个世界的守卫强度；母星、不是星球的维度没有守卫。 */
    public static SentinelLevel level(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), key -> Planets.planet(level).map(SentinelLevel::of).orElse(SentinelLevel.NONE));
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
    }

    /** 会不会被通缉：生存、冒险模式，不是和平难度。 */
    private static boolean accountable(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator() && player.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    // ---- 违规

    public static void offend(ServerPlayer player, Offense offense) {
        ServerLevel level = player.level();
        SentinelLevel sentinels = level(level);
        if (!sentinels.present() || !accountable(player)) {
            return;
        }
        SentinelAlert alert = alert(player);
        Wanted wanted = alert.wanted();
        int before = wanted.level();
        if (wanted.offend(offense, sentinels.alertFactor())) {
            escalate(player, before, wanted.level());
        }
    }

    private static SentinelAlert alert(ServerPlayer player) {
        SentinelAlert alert = player.getData(UniverseAttachments.SENTINEL_ALERT);
        alert.moveTo(player.level().dimension().identifier().toString());
        return alert;
    }

    private static void escalate(ServerPlayer player, int before, int after) {
        Session session = session(player);
        session.reinforceCooldown = 0;
        session.seen = true;
        player.sendOverlayMessage(Component.translatable(after >= Wanted.MAX_LEVEL && before < Wanted.MAX_LEVEL
                ? "message.universe.sentinel.heavy" : "message.universe.sentinel.wanted", after));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE,
                1.0F, 0.5F + after * 0.1F);
        // 附近的守卫机立刻转为敌对
        for (Sentinel sentinel : Sentinel.near(player.level(), player.position(), NEAR)) {
            if (!sentinel.isHostile()) {
                sentinel.setHostile(player);
            }
        }
    }

    /** 守卫机挨了玩家的打。 */
    static void onAttacked(ServerPlayer player, Sentinel sentinel) {
        offend(player, Offense.ATTACK_SENTINEL);
        if (accountable(player) && level(player.level()).present()) {
            sentinel.setHostile(player);
        }
    }

    static void onDestroyed(Sentinel sentinel, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            offend(player, Offense.DESTROY_SENTINEL);
            Session session = session(player);
            session.patrolCooldown = Math.max(session.patrolCooldown, PATROL_AFTER_LOSS);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof AlienCreature && event.getSource().getEntity() instanceof ServerPlayer player) {
            offend(player, Offense.KILL_CREATURE);
        }
    }

    // ---- 每刻

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        SentinelAlert alert = player.getData(UniverseAttachments.SENTINEL_ALERT);
        Wanted wanted = alert.wanted();
        Session session = session(player);
        if (alert.moveTo(level.dimension().identifier().toString())) {
            player.sendOverlayMessage(Component.translatable("message.universe.sentinel.left_planet"));
        }
        SentinelLevel sentinels = level(level);
        if (!sentinels.present()) {
            hud(player, SentinelHud.NONE);
            return;
        }
        if (wanted.wanted() && !accountable(player)) {
            wanted.clear();
            calm(player, false);
        }
        if (player.tickCount % 10 == 0) {
            session.seen = wanted.wanted() && seen(level, player);
        }
        if (wanted.tick(session.seen)) {
            player.sendOverlayMessage(Component.translatable("message.universe.sentinel.escaped"));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.8F, 1.2F);
            calm(player, true);
            session.patrolCooldown = Math.max(session.patrolCooldown, PATROL_AFTER_LOSS);
        }
        if (player.tickCount % 20 == 3) {
            maintain(level, player, sentinels, wanted, session);
        }
        hud(player, SentinelHud.of(sentinels.ordinal(), wanted.level(), wanted.alert() / Wanted.THRESHOLDS[0], wanted.escapeProgress(),
                session.seen));
    }

    private static void hud(ServerPlayer player, SentinelHud hud) {
        if (!player.getData(UniverseAttachments.SENTINEL_HUD).equals(hud)) {
            player.setData(UniverseAttachments.SENTINEL_HUD, hud);
        }
    }

    /** 有没有追捕这个玩家的守卫机看得见他。 */
    private static boolean seen(ServerLevel level, ServerPlayer player) {
        for (Sentinel sentinel : Sentinel.near(level, player.position(), SIGHT)) {
            if (sentinel.isHostile() && sentinel.getTarget() == player && sentinel.hasLineOfSight(player)) {
                return true;
            }
        }
        return false;
    }

    /** 不再追捕这个玩家：增援撤走，原来的巡逻恢复巡逻。 */
    private static void calm(ServerPlayer player, boolean retreat) {
        for (Sentinel sentinel : Sentinel.near(player.level(), player.position(), NEAR * 2)) {
            if (sentinel.getTarget() == player) {
                if (retreat) {
                    sentinel.retreat();
                } else {
                    sentinel.setHostile(null);
                }
            }
        }
    }

    private static void maintain(ServerLevel level, ServerPlayer player, SentinelLevel sentinels, Wanted wanted, Session session) {
        session.patrolCooldown -= 20;
        session.reinforceCooldown -= 20;
        List<Sentinel> near = Sentinel.near(level, player.position(), NEAR);
        if (wanted.wanted()) {
            int drones = 0;
            int heavies = 0;
            for (Sentinel sentinel : near) {
                if (!sentinel.isHostile()) {
                    sentinel.setHostile(player);
                }
                if (sentinel.getTarget() == player) {
                    if (sentinel.isHeavy()) {
                        heavies++;
                    } else {
                        drones++;
                    }
                }
            }
            int missingDrones = Wanted.drones(wanted.level(), sentinels) - drones;
            int missingHeavies = Wanted.heavies(wanted.level()) - heavies;
            if ((missingDrones > 0 || missingHeavies > 0) && session.reinforceCooldown <= 0) {
                session.reinforceCooldown = REINFORCE_INTERVAL;
                for (int i = 0; i < Math.min(2, missingDrones); i++) {
                    spawn(level, player, false, true);
                }
                if (missingHeavies > 0) {
                    spawn(level, player, true, true);
                }
            }
            return;
        }
        if (player.isSpectator() || near.size() >= sentinels.patrols() || session.patrolCooldown > 0 || !nearSurface(level, player)) {
            return;
        }
        session.patrolCooldown = PATROL_INTERVAL;
        spawn(level, player, false, false);
    }

    /** 玩家在地表附近（不在地下深处）。 */
    private static boolean nearSurface(ServerLevel level, ServerPlayer player) {
        return player.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ()) - 8;
    }

    /**
     * 在玩家附近放一架守卫机：巡逻的在 20–36 格外、离地 4–6 格；增援在 18–28 格外、离地 8–12 格，传送过来时有一团光。
     *
     * @return 放下的守卫机；找不到地方时为空
     */
    public static @Nullable Sentinel spawn(ServerLevel level, ServerPlayer player, boolean heavy, boolean hostile) {
        EntityType<Sentinel> type = heavy ? UniverseEntities.HEAVY_SENTINEL.get() : UniverseEntities.SENTINEL.get();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance = hostile ? 18 + level.getRandom().nextDouble() * 10 : 20 + level.getRandom().nextDouble() * 16;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            BlockPos column = BlockPos.containing(x, 0, z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            double y = ground + (hostile ? 8 + level.getRandom().nextDouble() * 4 : 4 + level.getRandom().nextDouble() * 2);
            if (y > level.getMaxY() - 2 || !level.noCollision(type.getSpawnAABB(x, y, z))) {
                continue;
            }
            Sentinel sentinel = type.create(level, EntitySpawnReason.EVENT);
            if (sentinel == null) {
                return null;
            }
            Vec3 toPlayer = player.position().subtract(x, y, z);
            sentinel.snapTo(x, y, z, (float) (Math.atan2(-toPlayer.x, toPlayer.z) * 180 / Math.PI), 0);
            level.addFreshEntity(sentinel);
            if (hostile) {
                sentinel.setHostile(player);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.4, z, 30, 0.4, 0.4, 0.4, 0.1);
                level.sendParticles(ParticleTypes.END_ROD, x, y + 0.4, z, 12, 0.3, 0.3, 0.3, 0.08);
                level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0F, heavy ? 0.8F : 1.4F);
            }
            return sentinel;
        }
        return null;
    }
}
