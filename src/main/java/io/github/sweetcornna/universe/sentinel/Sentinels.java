package io.github.sweetcornna.universe.sentinel;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.sentinel.Offense;
import io.github.sweetcornna.universe.core.sentinel.SentinelKind;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.core.sentinel.SentinelStrength;
import io.github.sweetcornna.universe.core.sentinel.Wanted;
import io.github.sweetcornna.universe.core.sentinel.Wave;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 守卫机的调度（ADR-0014，ADR-0026 改成五级通缉）。
 *
 * <ul>
 *   <li>有守卫的星球上，按守卫强度在每个玩家附近保持几架巡逻的守卫机（玩家在地下深处时不补）。</li>
 *   <li>违规行为（{@link Offense}）提高玩家的警戒值；通缉后附近的守卫机都转为敌对，按级别整波传送来增援（{@link Wave}）。
 *       这一波的成员都打掉了就升一级、隔几秒来下一波；打光五级，这颗星球的守卫机撤走 10 分钟，HUD 标出守卫塔。
 *       成员没被打掉就不见了（卸载、传送走）的，下次补上，不会让玩家一直卡在这一级。</li>
 *   <li>敌对的守卫机都看不见玩家 20 + 10 × 级别 秒、离开星球或者死亡，通缉解除，追捕的守卫机撤退。三星以上飞上太空，
 *       太空守卫机的拦截机接着追（ADR-0024，{@link SpaceEncounters}）。</li>
 *   <li>守卫机停着（{@link #shutdown}：守卫塔的终端、打光五级）的星球上不巡逻、不通缉，在场的守卫机撤走。</li>
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
    /** 打光一波以后，下一波隔多久到（刻）。 */
    static final int NEXT_WAVE_DELAY = 100;
    /** 一种守卫机连着放不下多少次（每秒一次）就不等了：地面单位换成无人机，无人机就算了。 */
    static final int SPAWN_GIVE_UP = 15;
    /** 打光五级以后撤走多久（刻）：10 分钟。 */
    public static final int WITHDRAW_TICKS = 20 * 60 * 10;
    private static final Map<ResourceKey<Level>, SentinelLevel> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /** 不存档的每个玩家的计时和这一波的成员。 */
    private static final class Session {
        int patrolCooldown = 200;
        boolean seen;
        /** 当前这一波是第几级的；0 表示还没开始（或者在两波之间）。 */
        int waveLevel;
        /** 打光一波以后，还要等多少刻来下一波。 */
        int waveDelay;
        /** 这一波还活着的成员。 */
        final Map<UUID, SentinelKind> members = new LinkedHashMap<>();
        /** 这一波还没放下的，按种类。 */
        final int[] pending = new int[SentinelKind.values().length];
        /** 每种连着放不下几次了。 */
        final int[] failures = new int[SentinelKind.values().length];

        int pendingTotal() {
            return Arrays.stream(this.pending).sum();
        }

        /** HUD 上的"还剩几架"；两波之间、这一波还没开始时是 -1。 */
        int remaining() {
            return this.waveDelay > 0 || this.waveLevel == 0 ? -1 : this.members.size() + this.pendingTotal();
        }

        void resetWave() {
            this.waveLevel = 0;
            this.waveDelay = 0;
            this.members.clear();
            Arrays.fill(this.pending, 0);
            Arrays.fill(this.failures, 0);
        }
    }

    private Sentinels() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                tick(player);
                ServerPerf.end("player/sentinels", start);
            }
        });
        gameBus.addListener(LivingDeathEvent.class, Sentinels::onDeath);
        gameBus.addListener(LivingIncomingDamageEvent.class, Sentinels::onIncomingDamage);
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> SESSIONS.remove(event.getEntity().getUUID()));
    }

    /** 建世界时定的守卫机强度（ADR-0028 §七）：血量、伤害和每波的架数乘它；0 时守卫机不出现。 */
    public static double strength(ServerLevel level) {
        return UniverseRules.sentinelStrength(level.getServer());
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

    // ---- 停机

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    private static String key(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** 这颗星球的守卫机停 {@code ticks} 刻：不巡逻、不通缉，在场的都撤走（守卫塔的终端、打光五级以后）。重开服务器也接着算。 */
    public static void shutdown(ServerLevel level, long ticks) {
        long now = now(level);
        SentinelDormancy.get(level.getServer()).extend(key(level), now + ticks, now);
        for (Sentinel sentinel : level.getEntities(EntityTypeTest.forClass(Sentinel.class), Sentinel::isAlive)) {
            if (!sentinel.retreating()) {
                sentinel.retreat();
            }
        }
    }

    /** 这颗星球的守卫机停着没有。 */
    /** 提前结束停机（游戏内测试用：测试世界只有一个维度，别的测试还要用守卫机）。 */
    public static void wake(ServerLevel level) {
        SentinelDormancy.get(level.getServer()).clear(key(level));
    }

    public static boolean dormant(ServerLevel level) {
        return dormantTicks(level) > 0;
    }

    /** 这颗星球的守卫机还要停多少刻；没停时为 0。 */
    public static long dormantTicks(ServerLevel level) {
        return Math.max(0, SentinelDormancy.get(level.getServer()).until(key(level)) - now(level));
    }

    // ---- 违规

    public static void offend(ServerPlayer player, Offense offense) {
        ServerLevel level = player.level();
        SentinelLevel sentinels = level(level);
        if (!sentinels.present() || !accountable(player) || dormant(level) || !SentinelStrength.present(strength(level))) {
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

    /** 通缉级别因为违规升高了（开始通缉、跳级）：附近的守卫机转为敌对，下一次调度按新的级别出一波。 */
    private static void escalate(ServerPlayer player, int before, int after) {
        Session session = session(player);
        session.seen = true;
        session.waveDelay = 0;
        player.sendOverlayMessage(Component.translatable("message.universe.sentinel.wanted", after));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE,
                1.0F, 0.5F + after * 0.1F);
        for (Sentinel sentinel : Sentinel.near(player.level(), player.position(), NEAR)) {
            if (!sentinel.isHostile() && !sentinel.retreating()) {
                sentinel.setHostile(player);
            }
        }
    }

    /** 守卫机挨了玩家的打。 */
    static void onAttacked(ServerPlayer player, Sentinel sentinel) {
        offend(player, Offense.ATTACK_SENTINEL);
        if (accountable(player) && level(player.level()).present() && !dormant(player.level())
                && SentinelStrength.present(strength(player.level()))) {
            sentinel.setHostile(player);
        }
    }

    static void onDestroyed(Sentinel sentinel, DamageSource source) {
        // 不管是谁打掉的、怎么掉的，都算这一波少了一架
        for (Session session : SESSIONS.values()) {
            session.members.remove(sentinel.getUUID());
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            offend(player, Offense.DESTROY_SENTINEL);
            Session session = session(player);
            session.patrolCooldown = Math.max(session.patrolCooldown, PATROL_AFTER_LOSS);
        }
    }

    /** 守卫机打出的伤害（弹丸、扑击、踩踏、光束）乘守卫机强度。 */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Sentinel sentinel && sentinel.level() instanceof ServerLevel level) {
            double strength = strength(level);
            if (strength != 1) {
                event.setAmount((float) SentinelStrength.scale(event.getAmount(), strength));
            }
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof AlienCreature && event.getSource().getEntity() instanceof ServerPlayer player) {
            offend(player, Offense.KILL_CREATURE);
        }
    }

    /** 召唤无人机叫来的守卫机也算进这一波（要打掉才算打光）。 */
    static void reinforce(ServerPlayer player, Sentinel sentinel) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && session.waveLevel > 0) {
            session.members.put(sentinel.getUUID(), sentinel.kind());
        }
    }

    // ---- 每刻

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        SentinelAlert alert = player.getData(UniverseAttachments.SENTINEL_ALERT);
        Wanted wanted = alert.wanted();
        Session session = session(player);
        int wantedBefore = wanted.level();
        if (alert.moveTo(level.dimension().identifier().toString())) {
            session.resetWave();
            if (!SpaceEncounters.pursueIntoSpace(player, wantedBefore)) {
                // 三星以上飞上太空的，太空守卫机接着追（ADR-0024）
                player.sendOverlayMessage(Component.translatable("message.universe.sentinel.left_planet"));
            }
        }
        SentinelLevel sentinels = level(level);
        if (!sentinels.present()) {
            hud(player, SentinelHud.NONE);
            return;
        }
        if (!SentinelStrength.present(strength(level))) {
            // 守卫机强度 0（ADR-0028 §七）：通缉撤销，附近的守卫机撤走，不再出来
            if (wanted.wanted()) {
                wanted.clear();
                calm(player, true);
            }
            session.resetWave();
            session.seen = false;
            if (player.tickCount % 20 == 3) {
                for (Sentinel sentinel : Sentinel.near(level, player.position(), NEAR)) {
                    if (!sentinel.retreating()) {
                        sentinel.retreat();
                    }
                }
            }
            hud(player, SentinelHud.NONE);
            return;
        }
        long dormant = dormantTicks(level);
        if (dormant > 0) {
            if (wanted.wanted()) {
                wanted.clear();
                calm(player, true);
            }
            session.resetWave();
            session.seen = false;
            if (player.tickCount % 20 == 3) {
                for (Sentinel sentinel : Sentinel.near(level, player.position(), NEAR)) {
                    if (!sentinel.retreating()) {
                        sentinel.retreat();
                    }
                }
            }
            hud(player, SentinelHud.of(sentinels.ordinal(), 0, 0, 0, false, 0, (int) Math.ceil(dormant / 20.0)));
            return;
        }
        if (wanted.wanted() && !accountable(player)) {
            wanted.clear();
            session.resetWave();
            calm(player, false);
        }
        if (player.tickCount % 10 == 0) {
            session.seen = wanted.wanted() && seen(level, player);
        }
        if (wanted.tick(session.seen)) {
            player.sendOverlayMessage(Component.translatable("message.universe.sentinel.escaped"));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.8F, 1.2F);
            calm(player, true);
            session.resetWave();
            session.patrolCooldown = Math.max(session.patrolCooldown, PATROL_AFTER_LOSS);
        }
        if (player.tickCount % 20 == 3) {
            maintain(level, player, sentinels, wanted, session);
        }
        if (!wanted.wanted()) {
            session.resetWave();
        }
        hud(player, SentinelHud.of(sentinels.ordinal(), wanted.level(), wanted.alert() / Wanted.THRESHOLD, wanted.escapeProgress(),
                session.seen, wanted.wanted() ? session.remaining() : 0, 0));
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
        List<Sentinel> near = Sentinel.near(level, player.position(), NEAR);
        if (wanted.wanted()) {
            for (Sentinel sentinel : near) {
                if (!sentinel.isHostile() && !sentinel.retreating()) {
                    sentinel.setHostile(player);
                }
            }
            hunt(level, player, sentinels, wanted, session, near);
            return;
        }
        if (player.isSpectator() || near.size() >= sentinels.patrols() || session.patrolCooldown > 0 || !nearSurface(level, player)) {
            return;
        }
        session.patrolCooldown = PATROL_INTERVAL;
        spawn(level, player, SentinelKind.DRONE, false);
    }

    /** 通缉时每秒一次：开新的一波、补放没放下的、清点成员，打光了就升级或者撤走。 */
    private static void hunt(ServerLevel level, ServerPlayer player, SentinelLevel sentinels, Wanted wanted, Session session,
            List<Sentinel> near) {
        if (session.waveDelay > 0) {
            session.waveDelay -= 20;
            if (session.waveDelay > 0) {
                return;
            }
            session.waveDelay = 0;
        }
        if (session.waveLevel != wanted.level()) {
            startWave(player, sentinels, wanted.level(), session, near);
        }
        // 没被打掉就不见了的（卸载、传送走）：放回待放，下次补上
        for (Iterator<Map.Entry<UUID, SentinelKind>> it = session.members.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, SentinelKind> member = it.next();
            Entity entity = level.getEntity(member.getKey());
            if (!(entity instanceof Sentinel sentinel) || sentinel.isRemoved() || sentinel.retreating()) {
                it.remove();
                session.pending[member.getValue().ordinal()]++;
            } else if (sentinel.getTarget() != player && !sentinel.isDeadOrDying()) {
                sentinel.setHostile(player);
            }
        }
        for (SentinelKind kind : SentinelKind.values()) {
            int index = kind.ordinal();
            while (session.pending[index] > 0) {
                Sentinel sentinel = spawn(level, player, kind, true);
                if (sentinel == null) {
                    if (++session.failures[index] >= SPAWN_GIVE_UP) {
                        // 地面上放不下的换成无人机，无人机也放不下就算了
                        if (!kind.flying()) {
                            session.pending[SentinelKind.DRONE.ordinal()] += session.pending[index];
                        }
                        session.pending[index] = 0;
                        session.failures[index] = 0;
                    }
                    break;
                }
                session.failures[index] = 0;
                session.pending[index]--;
                session.members.put(sentinel.getUUID(), kind);
            }
        }
        if (session.members.isEmpty() && session.pendingTotal() == 0) {
            waveCleared(level, player, wanted, session);
        }
    }

    /** 开始这一级的一波：附近已经在追捕的守卫机先算进去，差的从远处传送来。 */
    private static void startWave(ServerPlayer player, SentinelLevel sentinels, int level, Session session, List<Sentinel> near) {
        Wave wave = Wave.of(level, sentinels, strength(player.level()));
        session.waveLevel = level;
        Arrays.fill(session.failures, 0);
        for (Sentinel sentinel : near) {
            if (sentinel.getTarget() == player && sentinel.isAlive() && !sentinel.retreating()) {
                session.members.putIfAbsent(sentinel.getUUID(), sentinel.kind());
            }
        }
        int[] have = new int[SentinelKind.values().length];
        session.members.values().forEach(kind -> have[kind.ordinal()]++);
        for (SentinelKind kind : SentinelKind.values()) {
            session.pending[kind.ordinal()] = Math.max(0, wave.count(kind) - have[kind.ordinal()]);
        }
        if (session.pendingTotal() > 0) {
            player.sendOverlayMessage(Component.translatable("message.universe.sentinel.wave", level, session.pendingTotal()));
        }
    }

    private static void waveCleared(ServerLevel level, ServerPlayer player, Wanted wanted, Session session) {
        switch (wanted.waveCleared()) {
            case ESCALATED -> {
                session.resetWave();
                session.waveDelay = NEXT_WAVE_DELAY;
                player.sendOverlayMessage(Component.translatable("message.universe.sentinel.wave_cleared", wanted.level()));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE,
                        1.0F, 0.5F + wanted.level() * 0.1F);
            }
            case WITHDRAWN -> {
                session.resetWave();
                calm(player, true);
                shutdown(level, WITHDRAW_TICKS);
                SentinelPillars.reveal(player);
                player.sendOverlayMessage(Component.translatable("message.universe.sentinel.withdrawn", WITHDRAW_TICKS / 1200));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE,
                        1.0F, 0.8F);
            }
            case NONE -> session.resetWave();
        }
    }

    /** 玩家在地表附近（不在地下深处）。 */
    private static boolean nearSurface(ServerLevel level, ServerPlayer player) {
        return player.getY() > level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ()) - 8;
    }

    public static EntityType<? extends Sentinel> type(SentinelKind kind) {
        return switch (kind) {
            case DRONE -> UniverseEntities.SENTINEL.get();
            case HEAVY -> UniverseEntities.HEAVY_SENTINEL.get();
            case REPAIR -> UniverseEntities.REPAIR_SENTINEL.get();
            case SUMMONER -> UniverseEntities.SUMMONER_SENTINEL.get();
            case QUADRUPED -> UniverseEntities.QUADRUPED_SENTINEL.get();
            case WALKER -> UniverseEntities.WALKER_SENTINEL.get();
        };
    }

    /**
     * 在指定的位置放一架守卫机（地标门口巡逻的、召唤无人机叫来的）。{@code target} 不为空时直接敌对，有传送过来的光。
     *
     * @return 放下的守卫机；放不下时为空
     */
    public static @Nullable Sentinel spawnAt(ServerLevel level, Vec3 pos, SentinelKind kind, @Nullable ServerPlayer target) {
        EntityType<? extends Sentinel> type = type(kind);
        double strength = strength(level);
        if (!SentinelStrength.present(strength) || !level.noCollision(type.getSpawnAABB(pos.x, pos.y, pos.z))) {
            return null;
        }
        Sentinel sentinel = type.create(level, EntitySpawnReason.EVENT);
        if (sentinel == null) {
            return null;
        }
        applyStrength(sentinel, strength);
        float yaw = target != null ? yawTowards(pos, target.position()) : level.getRandom().nextFloat() * 360;
        sentinel.snapTo(pos.x, pos.y, pos.z, yaw, 0);
        level.addFreshEntity(sentinel);
        if (target != null) {
            sentinel.setHostile(target);
            arrive(level, sentinel);
        }
        return sentinel;
    }

    /** 调试命令用：普通或重型守卫机。 */
    public static @Nullable Sentinel spawn(ServerLevel level, ServerPlayer player, boolean heavy, boolean hostile) {
        return spawn(level, player, heavy ? SentinelKind.HEAVY : SentinelKind.DRONE, hostile);
    }

    /**
     * 在玩家附近放一架守卫机。飞的：巡逻的在 20–36 格外、离地 4–6 格，增援在 18–28 格外、离地 8–12 格；
     * 走路的放在 14–24 格外的实地上。增援传送过来时有一团光。
     *
     * @return 放下的守卫机；找不到地方时为空
     */
    public static @Nullable Sentinel spawn(ServerLevel level, ServerPlayer player, SentinelKind kind, boolean hostile) {
        EntityType<? extends Sentinel> type = type(kind);
        double strength = strength(level);
        if (!SentinelStrength.present(strength)) {
            return null;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance;
            if (!kind.flying()) {
                distance = 14 + level.getRandom().nextDouble() * 10;
            } else {
                distance = hostile ? 18 + level.getRandom().nextDouble() * 10 : 20 + level.getRandom().nextDouble() * 16;
            }
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;
            BlockPos column = BlockPos.containing(x, 0, z);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            double y;
            if (kind.flying()) {
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
                y = ground + (hostile ? 8 + level.getRandom().nextDouble() * 4 : 4 + level.getRandom().nextDouble() * 2);
            } else {
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                BlockPos feet = new BlockPos(column.getX(), ground, column.getZ());
                BlockPos below = feet.below();
                // 实地上，脚下不是水和熔岩，离玩家的高度差不太大
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(feet).isEmpty()
                        || !level.getFluidState(below).isEmpty() || Math.abs(ground - player.getY()) > 12) {
                    continue;
                }
                y = ground;
            }
            if (y > level.getMaxY() - 2 || !level.noCollision(type.getSpawnAABB(x, y, z))) {
                continue;
            }
            Sentinel sentinel = type.create(level, EntitySpawnReason.EVENT);
            if (sentinel == null) {
                return null;
            }
            applyStrength(sentinel, strength);
            sentinel.snapTo(x, y, z, yawTowards(new Vec3(x, y, z), player.position()), 0);
            level.addFreshEntity(sentinel);
            if (hostile) {
                sentinel.setHostile(player);
                arrive(level, sentinel);
            }
            return sentinel;
        }
        return null;
    }

    /** 血量乘守卫机强度，满血放出来。 */
    private static void applyStrength(Sentinel sentinel, double strength) {
        AttributeInstance health = sentinel.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && strength != 1) {
            health.setBaseValue(Math.max(1, SentinelStrength.scale(health.getBaseValue(), strength)));
            sentinel.setHealth(sentinel.getMaxHealth());
        }
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
    }

    /** 传送过来的效果：一团光和一声响。 */
    private static void arrive(ServerLevel level, Sentinel sentinel) {
        double x = sentinel.getX();
        double y = sentinel.getY(0.5);
        double z = sentinel.getZ();
        double spread = sentinel.getBbWidth() * 0.5;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 30, spread, sentinel.getBbHeight() * 0.4, spread, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, spread * 0.7, sentinel.getBbHeight() * 0.3, spread * 0.7, 0.08);
        boolean big = sentinel.getBbWidth() > 1.2F;
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0F, big ? 0.8F : 1.4F);
    }
}
