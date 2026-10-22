package io.github.sweetcornna.universe.encounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fleet.FreighterLayout;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.sentinel.SentinelStrength;
import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.core.ship.PirateRaid;
import io.github.sweetcornna.universe.core.space.Encounters;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.encounter.EncounterSites.Kind;
import io.github.sweetcornna.universe.encounter.EncounterSites.Site;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.fleet.FleetData;
import io.github.sweetcornna.universe.fleet.FreighterBlocks;
import io.github.sweetcornna.universe.fleet.Freighters;
import io.github.sweetcornna.universe.network.EncounterPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.pirate.PirateFighter;
import io.github.sweetcornna.universe.pirate.Pirates;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseStats;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import io.github.sweetcornna.universe.trade.Credits;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.CustomClickActionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 太空遭遇（ADR-0024，规则见 {@link Encounters}）：开船在开阔的太空里每隔 6–10 分钟判定一次，和海盗分开算。
 *
 * <ul>
 *   <li>废弃货船：前方 1.5–2.5k 格出现一艘破了洞的小货船。从左舷的机库口飞进去，穿过一间间舱室搜货箱，最前面那间有船长的保险箱；
 *       天花板下几处漏电，走到底下会挨电。30 分钟后拆掉，有人还在船里就等他出来。应急信号接收器也能叫出一艘。</li>
 *   <li>遇险信号：前方 300 格左右一艘货船被 3–5 架海盗围攻。飞近了海盗才露面，飞得太近或者开火，海盗就转过来打你。
 *       3 分钟内打光，船长把货船送给你（已经有货船的给信用点）；没打完货船就跃迁逃走。朝货船开火开多了，货船跳走，守卫机追来。</li>
 *   <li>太空守卫机：星球上三星通缉还飞上太空，拦截机从身后追来，干扰脉冲引擎；打光、甩开、躲到空间站附近或者跃迁走才算完。</li>
 * </ul>
 *
 * <p>放下的船壳记在 {@link EncounterSites} 里，到点拆；遇险信号的战斗、追捕、船长的报价不存档，重开服务器就散了（船壳照样拆掉）。
 * 建、拆船壳要加载区块，排到服务器刻末尾（{@link Freighters#later}）。
 */
public final class SpaceEncounters {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL = 20;
    /** 守卫机锁定以后几刻追上来。 */
    private static final int PURSUIT_WARNING = 100;
    /** 拦截机在身后多远出现。 */
    private static final double PURSUIT_SPAWN = 110;
    /** 遇险的货船拿到报酬以后过多久跳走（刻）。 */
    private static final int FAREWELL = 100;
    /** 废弃货船到点了但有人在船里：过这么久（刻）再看。 */
    private static final int POSTPONE = 60 * 20;
    /** 遭遇之间至少隔多远（格），也不放在别人的货船边上。 */
    private static final double SPACING = 300;
    public static final ResourceKey<LootTable> DERELICT_CARGO = loot("chests/derelict_cargo");
    public static final ResourceKey<LootTable> DERELICT_SAFE = loot("chests/derelict_safe");
    /** 聊天栏里船长报价的两个按钮。 */
    static final Identifier ACCEPT = Universe.id("rescue_freighter");
    static final Identifier DECLINE = Universe.id("rescue_credits");

    private static final Map<UUID, int[]> TIMERS = new HashMap<>();
    private static final Map<Long, Distress> DISTRESS = new HashMap<>();
    private static final Map<UUID, Pursuit> PURSUITS = new HashMap<>();
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private static final Map<UUID, EncounterPayloads.Signal> SENT = new HashMap<>();
    private static final Map<Long, Shape> SHAPES = new HashMap<>();

    private enum State {
        /** 信号发出来了，还没人赶到。 */
        SIGNAL,
        /** 海盗露面了。 */
        SIEGE,
        /** 打光了，等船长的报价有个结果。 */
        WON
    }

    /** 一处遇险信号。 */
    private static final class Distress {
        final long seed;
        final UUID player;
        final ResourceKey<Level> dimension;
        final Vec3 center;
        final double clearance;
        final int count;
        final long signalEnds;
        final List<PirateFighter> raiders = new ArrayList<>();
        State state = State.SIGNAL;
        long siegeEnds;
        long leaveAt;
        /** 玩家打中货船几下了。 */
        int strays;

        Distress(long seed, UUID player, ResourceKey<Level> dimension, Vec3 center, double clearance, int count, long signalEnds) {
            this.seed = seed;
            this.player = player;
            this.dimension = dimension;
            this.center = center;
            this.clearance = clearance;
            this.count = count;
            this.signalEnds = signalEnds;
        }
    }

    /** 一场太空守卫机的追捕。 */
    private static final class Pursuit {
        final UUID player;
        final ResourceKey<Level> dimension;
        final int count;
        final boolean forced;
        final long spawnAt;
        final List<SentinelInterceptor> interceptors = new ArrayList<>();
        boolean spawned;
        boolean over;
        int destroyed;

        Pursuit(UUID player, ResourceKey<Level> dimension, int count, boolean forced, long spawnAt) {
            this.player = player;
            this.dimension = dimension;
            this.count = count;
            this.forced = forced;
            this.spawnAt = spawnAt;
        }
    }

    /** 船长的报价：把这艘货船送给你。 */
    private record Offer(long seed, Grade grade) {
    }

    /**
     * 一艘遭遇船的形状（相对原点）：机库口外面、包围盒、漏电的格子。
     *
     * @param mouth  机库口外面（废弃货船的自动驾驶目标）
     * @param bounds {minX, minY, minZ, maxX, maxY, maxZ}
     */
    private record Shape(List<FreighterLayout.Cell> cells, double[] mouth, int[] bounds, List<FreighterLayout.Cell> hazards) {
        Vec3 center(BlockPos origin) {
            return new Vec3(origin.getX() + (this.bounds[0] + this.bounds[3] + 1) / 2.0, origin.getY() + (this.bounds[1] + this.bounds[4] + 1) / 2.0,
                    origin.getZ() + (this.bounds[2] + this.bounds[5] + 1) / 2.0);
        }

        AABB box(BlockPos origin) {
            return new AABB(origin.getX() + this.bounds[0], origin.getY() + this.bounds[1], origin.getZ() + this.bounds[2],
                    origin.getX() + this.bounds[3] + 1, origin.getY() + this.bounds[4] + 1, origin.getZ() + this.bounds[5] + 1);
        }

        /** 中心到最远的船头、船尾有多远。 */
        double radius() {
            return Math.max(this.bounds[3] - this.bounds[0], this.bounds[5] - this.bounds[2]) / 2.0 + 1;
        }
    }

    /**
     * 自动驾驶列表里的信号。
     *
     * @param bounds 废弃货船的包围盒（世界坐标），自动驾驶照着飞进机库；遇险的货船为空
     * @param stop   离目标多远停下
     */
    public record Beacon(boolean derelict, Vec3 target, int @Nullable [] bounds, double stop) {
    }

    private SpaceEncounters() {
    }

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Universe.id(path));
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("encounters", () -> tick(event.getServer())));
        gameBus.addListener(CustomClickActionEvent.class, SpaceEncounters::onClick);
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            UUID id = event.getEntity().getUUID();
            TIMERS.remove(id);
            SENT.remove(id);
            Pursuit pursuit = PURSUITS.remove(id);
            if (pursuit != null) {
                pursuit.interceptors.forEach(PirateFighter::leave);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            TIMERS.clear();
            DISTRESS.clear();
            PURSUITS.clear();
            OFFERS.clear();
            SENT.clear();
            SHAPES.clear();
        });
    }

    // ---- 查询

    /** 这架战机还属于一场没结束的遭遇（围攻货船的海盗、追捕的拦截机）。 */
    public static boolean belongs(PirateFighter fighter) {
        UUID target = fighter.targetId();
        if (target == null) {
            return false;
        }
        if (fighter instanceof SentinelInterceptor) {
            Pursuit pursuit = PURSUITS.get(target);
            return pursuit != null && !pursuit.over && pursuit.interceptors.contains(fighter);
        }
        for (Distress distress : DISTRESS.values()) {
            if (distress.state == State.SIEGE && distress.player.equals(target) && distress.raiders.contains(fighter)) {
                return true;
            }
        }
        return false;
    }

    /** 飞船的脉冲引擎被拦截机干扰：驾驶员正被追捕，附近有没撤走的拦截机。 */
    public static boolean jammed(ShipEntity ship) {
        Entity pilot = ship.pilot();
        Pursuit pursuit = pilot == null ? null : PURSUITS.get(pilot.getUUID());
        if (pursuit == null || pursuit.over || !pursuit.spawned) {
            return false;
        }
        double range = PirateRaid.JAM_DISTANCE * PirateRaid.JAM_DISTANCE;
        return pursuit.interceptors.stream().anyMatch(f -> !f.isRemoved() && !f.leaving() && f.distanceToSqr(ship) < range);
    }

    /** 已经有一处遭遇、正被追捕、正被海盗袭击、船长在等回话：不再判定新的遭遇。 */
    public static boolean busy(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return EncounterSites.get(server).of(player.getUUID()).isPresent() || PURSUITS.containsKey(player.getUUID())
                || OFFERS.containsKey(player.getUUID()) || Pirates.raiding(player);
    }

    /** 自动驾驶列表里的信号：这名玩家在这个系里的遭遇。 */
    public static Optional<Beacon> beacon(MinecraftServer server, UUID player, int system) {
        return EncounterSites.get(server).of(player).filter(site -> site.system() == system).map(site -> {
            Shape shape = shape(site);
            BlockPos origin = origin(site);
            if (site.kind() == Kind.DERELICT) {
                Vec3 mouth = new Vec3(origin.getX() + shape.mouth()[0], origin.getY() + shape.mouth()[1] - 1, origin.getZ() + shape.mouth()[2]);
                int[] b = shape.bounds();
                int[] bounds = {origin.getX() + b[0], origin.getY() + b[1], origin.getZ() + b[2], origin.getX() + b[3], origin.getY() + b[4],
                        origin.getZ() + b[5]};
                return new Beacon(true, mouth, bounds, 0);
            }
            return new Beacon(false, shape.center(origin), null, shape.radius() + 60);
        });
    }

    /** 调试命令用：{@code {累计刻数, 要飞满的刻数}}，还没开始计时返回空。 */
    static int @Nullable [] timer(ServerPlayer player) {
        return TIMERS.get(player.getUUID());
    }

    static @Nullable String pursuitStatus(ServerPlayer player) {
        Pursuit pursuit = PURSUITS.get(player.getUUID());
        return pursuit == null ? null : (pursuit.spawned ? pursuit.interceptors.size() + "/" + pursuit.count : "warning");
    }

    static @Nullable String distressStatus(ServerPlayer player) {
        for (Distress distress : DISTRESS.values()) {
            if (distress.player.equals(player.getUUID())) {
                return distress.state.name().toLowerCase(Locale.ROOT) + " raiders=" + distress.raiders.size() + " strays=" + distress.strays;
            }
        }
        return null;
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (!DISTRESS.isEmpty()) {
            for (Distress distress : List.copyOf(DISTRESS.values())) {
                tickDistress(server, distress, now);
            }
        }
        if (!PURSUITS.isEmpty()) {
            for (Pursuit pursuit : List.copyOf(PURSUITS.values())) {
                tickPursuit(server, pursuit, now);
            }
        }
        int tick = server.getTickCount();
        if (tick % Encounters.HAZARD_INTERVAL == 0) {
            shock(server);
        }
        if (tick % CHECK_INTERVAL != 0) {
            return;
        }
        roll(server);
        expire(server, now);
        signals(server, now);
    }

    /** 开船飞着的玩家攒够时间就掷一次。 */
    private static void roll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ShipEntity ship = cruising(player);
            if (ship == null || player.isCreative() || player.isSpectator() || busy(player)) {
                continue;
            }
            int[] timer = TIMERS.computeIfAbsent(player.getUUID(), id -> new int[] {0, Encounters.interval(rng(player))});
            timer[0] += CHECK_INTERVAL;
            if (timer[0] < timer[1]) {
                continue;
            }
            timer[0] = 0;
            timer[1] = Encounters.interval(rng(player));
            switch (Encounters.roll(rng(player))) {
                case DERELICT -> derelict(player, ship);
                // 和平难度没有海盗：遇险信号换成废弃货船
                case DISTRESS -> {
                    if (!distress(player, ship)) {
                        derelict(player, ship);
                    }
                }
                case NOTHING -> {
                }
            }
        }
    }

    /** 玩家正在太空里、空间站附近以外驾驶着飞船（没在跃迁），返回那艘船。 */
    private static @Nullable ShipEntity cruising(ServerPlayer player) {
        if (!(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player || ship.isWarping()
                || !SpaceDimensions.isSpace(player.level().dimension())) {
            return null;
        }
        return nearStation(player.level(), ship.position(), Encounters.STATION_SAFE) ? null : ship;
    }

    private static boolean nearStation(ServerLevel level, Vec3 pos, double distance) {
        Optional<StationLayout.Site> site = Stations.site(level.getServer(), level.dimension());
        return site.isPresent() && site.get().distanceTo(pos.x, pos.y, pos.z) < distance;
    }

    private static Rng rng(ServerPlayer player) {
        return Rng.of(player.getRandom().nextLong());
    }

    /** 船头前方找一块空地方：离星球、恒星、空间站、别的遭遇和货船够远。找不到时为空。 */
    private static double @Nullable [] spot(ServerLevel level, int system, ShipEntity ship, double min, double max, Rng rng) {
        MinecraftServer server = level.getServer();
        Optional<StarSystem> star = Planets.galaxy(server).system(system);
        if (star.isEmpty()) {
            return null;
        }
        double starRadius = SpaceLayout.starRadius(star.get());
        double[][] bodies = ShipTransit.layout(server, system).stream()
                .map(b -> new double[] {b.x(), b.y(), b.z(), b.radius()}).toArray(double[][]::new);
        Optional<StationLayout.Site> station = Stations.site(server, level.dimension());
        List<double[]> taken = new ArrayList<>();
        for (Site other : EncounterSites.get(server).all()) {
            if (other.system() == system) {
                taken.add(new double[] {other.x(), other.y(), other.z()});
            }
        }
        for (Map.Entry<UUID, FleetData.Entry> entry : FleetData.get(server).inSystem(system)) {
            entry.getValue().placed().ifPresent(p -> taken.add(new double[] {p.x(), p.y(), p.z()}));
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            float yaw = ship.getYRot() + (attempt == 0 ? 0 : (float) rng.range(-120.0, 120.0));
            double[] at = Encounters.ahead(ship.getX(), ship.getY(), ship.getZ(), yaw, min, max, rng);
            if (!Encounters.clear(at, starRadius, bodies)
                    || station.isPresent() && station.get().distanceTo(at[0], at[1], at[2]) < Encounters.STATION_CLEARANCE) {
                continue;
            }
            boolean crowded = false;
            for (double[] t : taken) {
                if (Math.hypot(t[0] - at[0], t[2] - at[2]) < SPACING) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                return at;
            }
        }
        return null;
    }

    private static void notify(ServerPlayer player, Component message, SoundEvent sound, float pitch) {
        player.sendSystemMessage(message);
        player.sendOverlayMessage(message);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.NEUTRAL, 1.0F, pitch);
    }

    // ---- 船壳

    private static BlockPos origin(Site site) {
        return new BlockPos(site.x(), site.y(), site.z());
    }

    private static Shape shape(Site site) {
        return SHAPES.computeIfAbsent(site.seed(), seed -> {
            FreighterLayout.Exterior hull;
            List<FreighterLayout.Cell> cells;
            if (site.kind() == Kind.DERELICT) {
                FreighterLayout.Derelict derelict = FreighterLayout.derelict(seed);
                hull = derelict.hull();
                cells = derelict.all();
            } else {
                hull = FreighterLayout.small(seed);
                cells = hull.cells();
            }
            List<FreighterLayout.Cell> hazards = cells.stream().filter(c -> c.role() == FreighterLayout.Role.HAZARD).toList();
            return new Shape(cells, hull.mouth(), hull.bounds(), hazards);
        });
    }

    /** 放船壳，货箱和保险箱填上战利品表（打开时才抽）。只能在服务器刻末尾调。 */
    private static void build(MinecraftServer server, Site site) {
        ServerLevel level = SpaceDimensions.getOrCreate(server, site.system());
        BlockPos origin = origin(site);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (FreighterLayout.Cell cell : shape(site).cells()) {
            cursor.setWithOffset(origin, cell.x(), cell.y(), cell.z());
            level.setBlock(cursor, FreighterBlocks.exterior(cell.role()), Block.UPDATE_CLIENTS);
            if (cell.role() == FreighterLayout.Role.CRATE || cell.role() == FreighterLayout.Role.SAFE) {
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), cursor,
                        cell.role() == FreighterLayout.Role.SAFE ? DERELICT_SAFE : DERELICT_CARGO);
            }
        }
        LOGGER.info("Encounter {} ({}) placed in system {} at {}", site.seed(), site.kind(), site.system(), origin);
    }

    /** 拆掉船壳（只拆还是原样的格子，箱子里剩下的东西不掉出来）；{@code warp} 时放一团跃迁的光。 */
    private static void demolish(MinecraftServer server, Site site, boolean warp) {
        EncounterSites.get(server).remove(site.seed());
        Shape shape = shape(site);
        SHAPES.remove(site.seed());
        Freighters.later(() -> {
            ServerLevel level = SpaceDimensions.getOrCreate(server, site.system());
            BlockPos origin = origin(site);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (FreighterLayout.Cell cell : shape.cells()) {
                cursor.setWithOffset(origin, cell.x(), cell.y(), cell.z());
                if (level.getBlockState(cursor).is(FreighterBlocks.exterior(cell.role()).getBlock())) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
                }
            }
            if (warp) {
                Vec3 c = shape.center(origin);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 400, 14, 5, 14, 0.4);
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 12, 4, 12, 0.3);
                level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE8F0FF), c.x, c.y, c.z, 3, 4, 2, 4, 0);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 4.0F, 0.6F);
            }
            LOGGER.info("Encounter {} ({}) removed from system {}", site.seed(), site.kind(), site.system());
        });
    }

    /** 到点的废弃货船拆掉；重开服务器以后剩下的遇险货船也拆掉。 */
    private static void expire(MinecraftServer server, long now) {
        EncounterSites sites = EncounterSites.get(server);
        for (Site site : sites.all()) {
            if (site.kind() == Kind.DISTRESS) {
                if (!DISTRESS.containsKey(site.seed())) {
                    demolish(server, site, false);
                }
                continue;
            }
            if (now < site.expires()) {
                continue;
            }
            ServerLevel level = server.getLevel(SpaceDimensions.key(site.system()));
            AABB box = shape(site).box(origin(site));
            if (level != null && level.players().stream().anyMatch(p -> box.contains(p.position()))) {
                sites.replace(new Site(site.seed(), site.kind(), site.system(), site.x(), site.y(), site.z(), site.owner(), now + POSTPONE));
                continue;
            }
            demolish(server, site, false);
        }
    }

    /** 废弃货船里：走到漏电的线管底下挨电。 */
    private static void shock(MinecraftServer server) {
        for (Site site : EncounterSites.get(server).all()) {
            if (site.kind() != Kind.DERELICT) {
                continue;
            }
            ServerLevel level = server.getLevel(SpaceDimensions.key(site.system()));
            if (level == null || level.players().isEmpty()) {
                continue;
            }
            BlockPos origin = origin(site);
            Shape shape = shape(site);
            AABB box = shape.box(origin);
            for (ServerPlayer player : level.players()) {
                if (player.isCreative() || player.isSpectator() || !box.contains(player.position())) {
                    continue;
                }
                Vec3 eye = player.getEyePosition();
                for (FreighterLayout.Cell cell : shape.hazards()) {
                    BlockPos at = origin.offset(cell.x(), cell.y(), cell.z());
                    if (!level.getBlockState(at).is(UniverseBlocks.LIVE_CONDUIT.get())
                            || !Encounters.shocks(eye.x, eye.y, eye.z, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5)) {
                        continue;
                    }
                    player.hurtServer(level, level.damageSources().source(SurvivalDamage.SHOCK), Encounters.HAZARD_DAMAGE);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y - 0.4, eye.z, 16, 0.3, 0.5, 0.3, 0.3);
                    level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.9F);
                    break;
                }
            }
        }
    }

    // ---- 废弃货船

    /** 在船头前方放一艘废弃货船（判定到了、用了应急信号接收器、调试命令）。 */
    public static boolean derelict(ServerPlayer player, ShipEntity ship) {
        ServerLevel level = (ServerLevel) ship.level();
        MinecraftServer server = level.getServer();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty()) {
            return false;
        }
        Rng rng = rng(player);
        double[] at = spot(level, system.getAsInt(), ship, Encounters.MIN_AHEAD, Encounters.MAX_AHEAD, rng);
        if (at == null) {
            return false;
        }
        long seed = rng.nextLong();
        // 机库口和船差不多高
        BlockPos origin = BlockPos.containing(at[0], at[1] - FreighterLayout.HANGAR_MIDDLE, at[2]);
        Site site = new Site(seed, Kind.DERELICT, system.getAsInt(), origin.getX(), origin.getY(), origin.getZ(), player.getUUID(),
                server.overworld().getGameTime() + Encounters.DERELICT_LIFETIME);
        EncounterSites.get(server).add(site);
        Freighters.later(() -> build(server, site));
        int distance = (int) Math.round(ship.position().distanceTo(new Vec3(at[0], at[1], at[2])));
        notify(player, Component.translatable("message.universe.encounter.derelict", distance).withStyle(ChatFormatting.AQUA),
                SoundEvents.BEACON_POWER_SELECT, 1.4F);
        return true;
    }

    // ---- 遇险信号

    /** 在船头前方放一艘被海盗围攻的货船。和平难度下没有。 */
    public static boolean distress(ServerPlayer player, ShipEntity ship) {
        ServerLevel level = (ServerLevel) ship.level();
        MinecraftServer server = level.getServer();
        OptionalInt system = SpaceDimensions.system(level.dimension());
        if (system.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        Rng rng = rng(player);
        double[] at = spot(level, system.getAsInt(), ship, Encounters.MIN_DISTRESS_AHEAD, Encounters.MAX_DISTRESS_AHEAD, rng);
        if (at == null) {
            return false;
        }
        long seed = rng.nextLong();
        long now = server.overworld().getGameTime();
        BlockPos origin = BlockPos.containing(at[0], at[1] - FreighterLayout.HANGAR_MIDDLE, at[2]);
        Site site = new Site(seed, Kind.DISTRESS, system.getAsInt(), origin.getX(), origin.getY(), origin.getZ(), player.getUUID(),
                now + Encounters.SIGNAL_TIME + Encounters.DISTRESS_TIME + Encounters.OFFER_TIME);
        EncounterSites.get(server).add(site);
        Freighters.later(() -> build(server, site));
        Shape shape = shape(site);
        int count = rng.range(Encounters.MIN_RAIDERS, Encounters.MAX_RAIDERS);
        DISTRESS.put(seed, new Distress(seed, player.getUUID(), level.dimension(), shape.center(origin), shape.radius() + 4, count,
                now + Encounters.SIGNAL_TIME));
        int distance = (int) Math.round(ship.position().distanceTo(new Vec3(at[0], at[1], at[2])));
        notify(player, Component.translatable("message.universe.encounter.distress", distance).withStyle(ChatFormatting.GOLD),
                SoundEvents.NOTE_BLOCK_BIT.value(), 0.7F);
        return true;
    }

    private static void tickDistress(MinecraftServer server, Distress distress, long now) {
        distress.raiders.removeIf(Entity::isRemoved);
        ServerPlayer player = server.getPlayerList().getPlayer(distress.player);
        boolean present = player != null && player.isAlive() && player.level().dimension() == distress.dimension;
        switch (distress.state) {
            case SIGNAL -> {
                if (player == null || now >= distress.signalEnds) {
                    finish(server, distress, player, Component.translatable("message.universe.encounter.signal_lost"));
                } else if (present && player.getRootVehicle().position().distanceTo(distress.center) < Encounters.RAIDER_RANGE) {
                    raid(player, distress, now);
                }
            }
            case SIEGE -> {
                if (!present) {
                    finish(server, distress, player, Component.translatable("message.universe.encounter.abandoned"));
                } else if (distress.raiders.isEmpty()) {
                    win(server, player, distress, now);
                } else if (now >= distress.siegeEnds) {
                    finish(server, distress, player, Component.translatable("message.universe.encounter.freighter_fled"));
                }
            }
            case WON -> {
                if (now < distress.leaveAt) {
                    return;
                }
                Offer offer = OFFERS.get(distress.player);
                if (offer != null && offer.seed() == distress.seed) {
                    // 一直没回话：船长按信用点算
                    OFFERS.remove(distress.player);
                    if (player != null) {
                        pay(player, "message.universe.encounter.offer_expired");
                    }
                }
                finish(server, distress, null, null);
            }
        }
    }

    /** 飞近了：海盗露面，围着货船打。 */
    private static void raid(ServerPlayer player, Distress distress, long now) {
        ServerLevel level = player.level();
        double start = level.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < distress.count; i++) {
            double angle = start + Math.PI * 2 * i / distress.count;
            double r = distress.clearance + 20 + level.getRandom().nextDouble() * 20;
            Vec3 pos = distress.center.add(Math.cos(angle) * r, (level.getRandom().nextDouble() - 0.5) * 24, Math.sin(angle) * r);
            PirateFighter raider = PirateFighter.raider(level, distress.player, i, distress.count, pos, distress.center, distress.clearance);
            level.addFreshEntity(raider);
            distress.raiders.add(raider);
        }
        distress.state = State.SIEGE;
        distress.siegeEnds = now + Encounters.DISTRESS_TIME;
        Component message = Component.translatable("message.universe.encounter.under_attack", distress.count).withStyle(ChatFormatting.RED);
        player.sendSystemMessage(message);
        player.sendOverlayMessage(message);
        level.playSound(null, distress.center.x, distress.center.y, distress.center.z, SoundEvents.RAID_HORN, SoundSource.HOSTILE, 3.0F, 1.4F);
    }

    /** 海盗打光了：没有货船的收到船长的报价，有货船的拿信用点。 */
    private static void win(MinecraftServer server, ServerPlayer player, Distress distress, long now) {
        distress.state = State.WON;
        player.awardStat(UniverseStats.FREIGHTERS_RESCUED.get());
        notify(player, Component.translatable("message.universe.encounter.rescued").withStyle(ChatFormatting.GREEN), SoundEvents.PLAYER_LEVELUP, 1.2F);
        if (hasFreighter(server, player.getUUID())) {
            pay(player, "message.universe.encounter.reward");
            distress.leaveAt = now + FAREWELL;
            return;
        }
        Grade grade = Encounters.rescueGrade(rng(player));
        OFFERS.put(player.getUUID(), new Offer(distress.seed, grade));
        distress.leaveAt = now + Encounters.OFFER_TIME;
        MutableComponent accept = Component.translatable("message.universe.encounter.accept").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.Custom(ACCEPT, Optional.empty()))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("message.universe.encounter.accept_hint"))));
        MutableComponent decline = Component.translatable("message.universe.encounter.decline", Credits.format(Encounters.RESCUE_REWARD))
                .withStyle(style -> style.withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent.Custom(DECLINE, Optional.empty())));
        player.sendSystemMessage(Component.translatable("message.universe.encounter.offer",
                Component.literal(grade.letter()).withColor(grade.color()), accept, decline));
    }

    private static boolean hasFreighter(MinecraftServer server, UUID player) {
        return FleetData.get(server).find(player).flatMap(entry -> entry.fleet().freighter()).isPresent();
    }

    private static void pay(ServerPlayer player, String key) {
        Credits.add(player, Encounters.RESCUE_REWARD);
        player.sendSystemMessage(Component.translatable(key, Credits.format(Encounters.RESCUE_REWARD)).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.9F);
    }

    /** 遇险信号结束：海盗撤走，货船跳走。 */
    private static void finish(MinecraftServer server, Distress distress, @Nullable ServerPlayer player, @Nullable Component message) {
        DISTRESS.remove(distress.seed);
        distress.raiders.forEach(PirateFighter::leave);
        Offer offer = OFFERS.get(distress.player);
        if (offer != null && offer.seed() == distress.seed) {
            OFFERS.remove(distress.player);
        }
        EncounterSites.get(server).find(distress.seed).ifPresent(site -> demolish(server, site, true));
        if (player != null && message != null) {
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
            player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.YELLOW));
        }
    }

    /** 聊天栏里点了船长报价的按钮。 */
    private static void onClick(CustomClickActionEvent event) {
        boolean accept = event.getIdentifier().equals(ACCEPT);
        if (!accept && !event.getIdentifier().equals(DECLINE)) {
            return;
        }
        event.setCanceled(true);
        if (event.getPlayer() != null) {
            answer(event.getPlayer(), accept);
        }
    }

    /** 回船长的话：收下货船，或者要信用点。 */
    static boolean answer(ServerPlayer player, boolean takeFreighter) {
        MinecraftServer server = player.level().getServer();
        Offer offer = OFFERS.remove(player.getUUID());
        if (offer == null) {
            player.sendSystemMessage(Component.translatable("message.universe.encounter.offer_gone").withStyle(ChatFormatting.GRAY));
            return false;
        }
        long now = server.overworld().getGameTime();
        Distress distress = DISTRESS.get(offer.seed());
        Optional<Site> site = EncounterSites.get(server).find(offer.seed());
        if (takeFreighter && site.isPresent() && !hasFreighter(server, player.getUUID())) {
            // 遇险的那艘换成玩家自己的货船，停在原地
            DISTRESS.remove(offer.seed());
            demolish(server, site.get(), false);
            Site s = site.get();
            Freighters.grant(server, player.getUUID(), offer.seed(), offer.grade(), s.system(), new double[] {s.x(), s.y(), s.z()});
            notify(player, Component.translatable("message.universe.encounter.freighter_yours", offer.grade().letter()).withStyle(ChatFormatting.GREEN),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
            return true;
        }
        pay(player, "message.universe.encounter.reward");
        if (distress != null) {
            distress.leaveAt = now + FAREWELL;
        }
        return true;
    }

    /** 弹体打中方块：玩家打中遇险的货船，打多了货船跳走、守卫机追来。 */
    public static void onBoltHit(ServerLevel level, @Nullable Entity owner, BlockPos pos) {
        if (DISTRESS.isEmpty()) {
            return;
        }
        ServerPlayer shooter = owner instanceof ServerPlayer p ? p
                : owner instanceof ShipEntity ship && ship.pilot() instanceof ServerPlayer pilot ? pilot : null;
        if (shooter == null || !FreighterBlocks.isHull(level.getBlockState(pos))) {
            return;
        }
        MinecraftServer server = level.getServer();
        for (Distress distress : List.copyOf(DISTRESS.values())) {
            if (distress.dimension != level.dimension()) {
                continue;
            }
            Optional<Site> site = EncounterSites.get(server).find(distress.seed);
            if (site.isEmpty() || !shape(site.get()).box(origin(site.get())).contains(Vec3.atCenterOf(pos))) {
                continue;
            }
            distress.strays++;
            if (distress.strays == 1) {
                shooter.sendOverlayMessage(Component.translatable("message.universe.encounter.friendly_fire").withStyle(ChatFormatting.RED));
            }
            if (distress.strays >= Encounters.FRIENDLY_FIRE) {
                finish(server, distress, shooter, null);
                Component message = Component.translatable("message.universe.encounter.betrayed").withStyle(ChatFormatting.RED);
                shooter.sendSystemMessage(message);
                pursue(shooter, Encounters.sentinels(Encounters.SENTINEL_WANTED), false);
            }
            return;
        }
    }

    // ---- 太空守卫机

    /** 星球上被通缉着飞上了太空：通缉星数够了就追上来。返回是不是追来了。 */
    public static boolean pursueIntoSpace(ServerPlayer player, int wanted) {
        return SpaceDimensions.isSpace(player.level().dimension()) && pursue(player, Encounters.sentinels(wanted), false);
    }

    /** 太空守卫机锁定玩家，几秒后 {@code count} 架拦截机从身后追来。{@code forced}（调试命令）时不管创造模式、和平难度、空间站。 */
    public static boolean pursue(ServerPlayer player, int count, boolean forced) {
        ServerLevel level = player.level();
        if (!forced) {
            // 守卫机强度（ADR-0028 §七）：架数跟着变，0 时不追
            count = SentinelStrength.count(count, UniverseRules.sentinelStrength(level.getServer()));
        }
        if (count <= 0 || !SpaceDimensions.isSpace(level.dimension())) {
            return false;
        }
        if (!forced && (player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL)) {
            return false;
        }
        long now = level.getServer().overworld().getGameTime();
        Pursuit old = PURSUITS.put(player.getUUID(), new Pursuit(player.getUUID(), level.dimension(), count, forced, now + PURSUIT_WARNING));
        if (old != null) {
            old.interceptors.forEach(PirateFighter::leave);
        }
        Component warning = Component.translatable("message.universe.encounter.sentinels_locked").withStyle(ChatFormatting.RED);
        player.sendSystemMessage(warning);
        player.sendOverlayMessage(warning);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0F, 0.5F);
        return true;
    }

    private static void tickPursuit(MinecraftServer server, Pursuit pursuit, long now) {
        pursuit.interceptors.removeIf(Entity::isRemoved);
        if (pursuit.over) {
            if (pursuit.interceptors.isEmpty()) {
                PURSUITS.remove(pursuit.player, pursuit);
            }
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(pursuit.player);
        if (player == null || !player.isAlive()) {
            endPursuit(pursuit, null, null);
            return;
        }
        ServerLevel level = player.level();
        if (level.dimension() != pursuit.dimension) {
            endPursuit(pursuit, player, pursuit.spawned ? Component.translatable("message.universe.encounter.sentinels_escaped") : null);
            return;
        }
        Entity target = player.getRootVehicle();
        if (!pursuit.forced && nearStation(level, target.position(), PirateRaid.STATION_SAFE)) {
            endPursuit(pursuit, player, Component.translatable("message.universe.encounter.sentinels_station"));
            return;
        }
        if (!pursuit.spawned) {
            if (now >= pursuit.spawnAt) {
                spawnInterceptors(level, player, target, pursuit);
            }
            return;
        }
        if (pursuit.interceptors.isEmpty()) {
            pursuit.over = true;
            PURSUITS.remove(pursuit.player, pursuit);
            notify(player, (pursuit.destroyed > 0
                    ? Component.translatable("message.universe.encounter.sentinels_destroyed", pursuit.destroyed)
                    : Component.translatable("message.universe.encounter.sentinels_gone")).withStyle(ChatFormatting.GREEN),
                    SoundEvents.PLAYER_LEVELUP, 1.2F);
            return;
        }
        double giveUp = Encounters.INTERCEPTOR_GIVE_UP * Encounters.INTERCEPTOR_GIVE_UP;
        if (pursuit.interceptors.stream().allMatch(f -> f.distanceToSqr(target) > giveUp)) {
            endPursuit(pursuit, player, Component.translatable("message.universe.encounter.sentinels_lost"));
        }
    }

    /** 拦截机从目标身后出现，朝着目标飞过来。 */
    private static void spawnInterceptors(ServerLevel level, ServerPlayer player, Entity target, Pursuit pursuit) {
        pursuit.spawned = true;
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 heading = target.getDeltaMovement().horizontalDistanceSqr() > 0.01
                ? target.getDeltaMovement().multiply(1, 0, 1).normalize()
                : Vec3.directionFromRotation(0, target.getYRot());
        Vec3 right = heading.cross(new Vec3(0, 1, 0)).normalize();
        for (int i = 0; i < pursuit.count; i++) {
            double spread = (i - (pursuit.count - 1) / 2.0) * 18;
            Vec3 pos = center.subtract(heading.scale(PURSUIT_SPAWN + level.getRandom().nextDouble() * 15))
                    .add(right.scale(spread)).add(0, (level.getRandom().nextDouble() - 0.3) * 20, 0);
            pos = new Vec3(pos.x, Math.clamp(pos.y, level.getMinY() + 30, level.getMaxY() + 150), pos.z);
            Vec3 velocity = center.subtract(pos).normalize().scale(PirateFlight.SPEED * Encounters.INTERCEPTOR_SPEED);
            SentinelInterceptor interceptor = SentinelInterceptor.create(level, pursuit.player, i, pursuit.count, pos, velocity);
            level.addFreshEntity(interceptor);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 30, 1, 0.6, 1, 0.2);
            pursuit.interceptors.add(interceptor);
        }
        Component incoming = Component.translatable("message.universe.encounter.sentinels_incoming", pursuit.count).withStyle(ChatFormatting.RED);
        player.sendSystemMessage(incoming);
        player.sendOverlayMessage(incoming);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.4F);
    }

    /** 追捕结束（不是打光的）：剩下的拦截机撤走。 */
    private static void endPursuit(Pursuit pursuit, @Nullable ServerPlayer player, @Nullable Component message) {
        pursuit.over = true;
        pursuit.interceptors.forEach(PirateFighter::leave);
        if (pursuit.interceptors.isEmpty()) {
            PURSUITS.remove(pursuit.player, pursuit);
        }
        if (message != null && player != null) {
            player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
            player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.YELLOW));
        }
    }

    /** 一架拦截机被打下来：残骸装进击落它的人的船里，不给赏金。 */
    static void onInterceptorDestroyed(SentinelInterceptor interceptor, ServerLevel level, DamageSource source) {
        UUID target = interceptor.targetId();
        Pursuit pursuit = target == null ? null : PURSUITS.get(target);
        if (pursuit != null) {
            pursuit.destroyed++;
            pursuit.interceptors.remove(interceptor);
        }
        ServerPlayer killer = Pirates.killer(source);
        boolean stowed = Pirates.salvage(interceptor, level, source, killer);
        if (killer != null) {
            killer.awardStat(UniverseStats.INTERCEPTORS_DESTROYED.get());
            killer.sendSystemMessage(Component.translatable(stowed ? "message.universe.encounter.interceptor_down_stowed"
                    : "message.universe.encounter.interceptor_down").withStyle(ChatFormatting.GOLD));
        }
    }

    // ---- 调试命令

    /** 所有遭遇收场：拆掉船壳，海盗和拦截机撤走。返回拆了几处。 */
    static int clear(MinecraftServer server) {
        for (Distress distress : List.copyOf(DISTRESS.values())) {
            distress.raiders.forEach(PirateFighter::leave);
        }
        DISTRESS.clear();
        OFFERS.clear();
        for (Pursuit pursuit : PURSUITS.values()) {
            pursuit.over = true;
            pursuit.interceptors.forEach(PirateFighter::leave);
        }
        TIMERS.clear();
        List<Site> sites = EncounterSites.get(server).all();
        for (Site site : sites) {
            demolish(server, site, false);
        }
        return sites.size();
    }

    /** 调试命令：把玩家（开着船的连船）送到自己那处遭遇跟前；废弃货船直接送进机库，{@code hazard} 时送到第一处漏电的底下。 */
    static boolean visit(ServerPlayer player, boolean hazard) {
        MinecraftServer server = player.level().getServer();
        Optional<Site> found = EncounterSites.get(server).of(player.getUUID());
        if (found.isEmpty() || SpaceDimensions.system(player.level().dimension()).orElse(-1) != found.get().system()) {
            return false;
        }
        Site site = found.get();
        Shape shape = shape(site);
        BlockPos origin = origin(site);
        Entity root = player.getRootVehicle();
        if (site.kind() == Kind.DERELICT) {
            // 机库里，面朝后墙上的门（+x）
            player.stopRiding();
            int[] b = shape.bounds();
            double x = origin.getX() + b[0] + 4.5;
            double z = origin.getZ() + 0.5;
            if (hazard && !shape.hazards().isEmpty()) {
                FreighterLayout.Cell cell = shape.hazards().getFirst();
                x = origin.getX() + cell.x() + 0.5;
                z = origin.getZ() + cell.z() + 0.5;
            }
            player.teleportTo(player.level(), x, origin.getY() + 2, z, Set.of(), -90, 0, true);
            player.resetFallDistance();
            return true;
        }
        Vec3 center = shape.center(origin);
        Vec3 from = center.add(-(Encounters.RAIDER_RANGE - 20), 0, 0);
        root.teleportTo(from.x, from.y, from.z);
        root.setYRot(-90);
        return true;
    }

    // ---- HUD

    /** 每秒：信号变了就告诉客户端。 */
    private static void signals(MinecraftServer server, long now) {
        EncounterSites sites = EncounterSites.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            EncounterPayloads.Signal signal = sites.of(player.getUUID()).map(site -> signal(site, now)).orElse(EncounterPayloads.Signal.EMPTY);
            if (!signal.equals(SENT.get(player.getUUID()))) {
                SENT.put(player.getUUID(), signal);
                PacketDistributor.sendToPlayer(player, signal);
            }
        }
    }

    private static EncounterPayloads.Signal signal(Site site, long now) {
        Shape shape = shape(site);
        BlockPos origin = origin(site);
        if (site.kind() == Kind.DERELICT) {
            Vec3 at = new Vec3(origin.getX() + shape.mouth()[0], origin.getY() + shape.mouth()[1], origin.getZ() + shape.mouth()[2]);
            return new EncounterPayloads.Signal(EncounterPayloads.Signal.DERELICT, site.system(), at.x, at.y, at.z, -1);
        }
        Distress distress = DISTRESS.get(site.seed());
        int hull = distress != null && distress.state == State.SIEGE
                ? (int) Math.clamp(Math.ceil(100.0 * (distress.siegeEnds - now) / Encounters.DISTRESS_TIME), 0, 100) : -1;
        Vec3 c = shape.center(origin);
        return new EncounterPayloads.Signal(EncounterPayloads.Signal.DISTRESS, site.system(), c.x, c.y, c.z, hull);
    }
}
