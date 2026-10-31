package io.github.sweetcornna.universe.darkfog.hive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.BaseSites;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.core.darkfog.DarkFogLoot;
import io.github.sweetcornna.universe.core.darkfog.DarkFogSettings;
import io.github.sweetcornna.universe.core.darkfog.HiveBuildingKind;
import io.github.sweetcornna.universe.core.darkfog.HiveLayout;
import io.github.sweetcornna.universe.core.darkfog.HiveSites;
import io.github.sweetcornna.universe.core.darkfog.HiveState;
import io.github.sweetcornna.universe.core.darkfog.SpaceUnitKind;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.facility.FacilityRepairs;
import io.github.sweetcornna.universe.darkfog.flight.DarkFogFlights;
import io.github.sweetcornna.universe.darkfog.space.DarkFogSpaceUnit;
import io.github.sweetcornna.universe.darkfog.space.LancerRaids;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseSounds;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.stats.ProductionData;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 黑雾的太空巢穴（ADR-0028 §八）：有地面基地的恒星系在太空维度里放一座，按时间结算；威胁满了派枪骑去轰炸玩家所在的星球，
 * 攒够物质派中继站、放种子。
 *
 * <ul>
 *   <li>开局按种子算出哪些恒星系有地面基地（{@link HiveSites#seeded}，不用等星球加载），每个放一座；以后种子落下（{@link #found}）再添。</li>
 *   <li>每秒对着存档结算所有巢穴（{@link HiveState}）：太空维度没人时会卸载，所以不靠实体 tick。恒星系有戴森球时，
 *       光子接收器偷走一部分发电（{@link DysonSphere#setSiphon}）。</li>
 *   <li>威胁按恒星系里各星球（母星除外）的用电涨，打中继站猛涨；满了挑这个恒星系里有人站着的星球（母星除外），
 *       在离人最近的用电设备那里派一队枪骑（{@link LancerRaids#launch}）。</li>
 *   <li>有人在太空维度里飞近、区块加载了，就按存档放出建筑和一部分守巢的单位；人走远收回，血量写回存档。</li>
 *   <li>核心打掉：恒星系里还有中继站（地面基地上的、飞着的）就过 10 分钟在原地重建，降 3 级；一个都没了就彻底消灭。</li>
 * </ul>
 *
 * <p>接口（第二块的子代理之间约好的，签名别改）：中继站和种子（{@code DarkFogFlights}）调 {@link #found}、{@link #hasHive}、
 * {@link #summaries}；地面基地和飞着的中继站挨打调 {@link #onRelayHurt}；巢穴建筑、太空单位、枪骑轰炸的实体调下面的回调；
 * 护卫舰清剿调 {@link #strike}。
 */
public final class DarkFogHives {
    private static final int INTERVAL = 20;
    /** 有人在这么近（三维距离）、区块已加载，就把巢穴放出来。 */
    private static final double PLACE_RANGE = 640;
    /** 人走得比这还远就收回。 */
    private static final double RECALL_RANGE = 800;
    /** 放出来守巢的单位，每种最多几个（港口里的数照记，死一个补一个）。 */
    private static final int[] DEFENDERS = {8, 2};
    /** 守巢的单位离巢穴太远就收回港口。 */
    private static final double LEASH = 480;
    /** 枪骑轰炸离人最近的用电设备，太远（格）的不算。 */
    private static final double RAID_REACH = 400;
    /** 枪骑轰炸多久没回信就当打完了（刻）：服务器重启、星球卸载时轰炸的实体就没了。 */
    private static final long RAID_TIMEOUT_TICKS = 20 * 60 * 20;

    /** 每座放出来的巢穴在内存里的样子；不存档。 */
    private static final Map<Integer, Live> LIVE = new HashMap<>();
    /** 正在轰炸的：恒星系 → 派出去的那一刻。 */
    private static final Map<Integer, Long> RAIDS = new HashMap<>();
    /** 游戏内测试用的巢穴：不自动结算、不放出来；原来那座先收着，测完放回去。 */
    private static final Set<Integer> INERT = new HashSet<>();
    private static final Map<Integer, HiveState> STASHED = new HashMap<>();

    /** 一座巢穴放出来的实体。 */
    private static final class Live {
        final HiveBuilding[] buildings = new HiveBuilding[HiveLayout.size()];
        final List<DarkFogSpaceUnit> defenders = new ArrayList<>();
        @Nullable ServerLevel level;
        boolean placed;
    }

    private DarkFogHives() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("darkfog/hives", () -> tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            LIVE.clear();
            RAIDS.clear();
            INERT.clear();
            STASHED.clear();
        });
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---- 开局放哪些

    /** 第一次结算：按种子给有地面基地的恒星系各放一座。 */
    private static void plan(MinecraftServer server, DarkFogHiveData data, DarkFogSettings settings, long now) {
        for (StarSystem system : Planets.galaxy(server).systems()) {
            if (data.hive(system.index()).isPresent()) {
                continue;
            }
            List<BaseSites.Body> bodies = new ArrayList<>();
            system.bodies().stream()
                    .filter(Planet::landable)
                    .filter(p -> !p.id().equals(GalaxyGenerator.HOME_PLANET_ID))
                    .forEach(p -> bodies.add(new BaseSites.Body(p.id().path(), p.seed(), p.worldSize())));
            boolean home = system.index() == GalaxyGenerator.HOME_SYSTEM;
            if (!HiveSites.seeded(bodies, home, system.seed(), settings.occupation())) {
                continue;
            }
            HiveState hive = newHive(server, system);
            hive.activate(settings.initialLevel(), now, settings.growth(), light(system));
            data.put(hive);
        }
        data.markPlanned();
    }

    /** 这个恒星系的巢穴（还没长）：位置按种子算。 */
    private static HiveState newHive(MinecraftServer server, StarSystem system) {
        HiveSites.Site site = site(server, system);
        return new HiveState(system.index(), site.x(), site.y(), site.z());
    }

    /** 巢穴的位置：躲开星球、空间站、黑洞。 */
    public static HiveSites.Site site(MinecraftServer server, StarSystem system) {
        List<double[]> avoid = new ArrayList<>();
        StationLayout.site(system).ifPresent(s -> avoid.add(new double[] {s.x(), s.y(), s.z()}));
        BlackHoles.in(system).ifPresent(hole -> avoid.add(new double[] {hole.x(), hole.y(), hole.z()}));
        return HiveSites.site(system.seed(), SpaceLayout.starRadius(system), ShipTransit.layout(server, system.index()), avoid);
    }

    private static double light(StarSystem system) {
        return DysonSphere.lightFactor(system.star().luminosity());
    }

    private static double light(MinecraftServer server, int system) {
        return Planets.galaxy(server).system(system).map(DarkFogHives::light).orElse(1.0);
    }

    // ---- 每秒结算

    private static void tick(MinecraftServer server) {
        if (now(server) % INTERVAL != 0 || !server.tickRateManager().runsNormally()) {
            return;
        }
        DarkFogHiveData data = DarkFogHiveData.get(server);
        DarkFogSettings settings = UniverseRules.darkFog(server);
        long now = now(server);
        if (!data.planned() && settings.enabled()) {
            plan(server, data, settings, now);
        }
        if (data.hives().isEmpty()) {
            return;
        }
        Map<Integer, Double> watts = watts(server, now);
        for (HiveState hive : List.copyOf(data.hives().values())) {
            if (INERT.contains(hive.system())) {
                continue;
            }
            tickHive(server, hive, settings, watts.getOrDefault(hive.system(), 0.0), now);
        }
        // 轰炸的实体没了（服务器重启、星球卸载）也要能再派
        RAIDS.entrySet().removeIf(entry -> {
            if (now - entry.getValue() <= RAID_TIMEOUT_TICKS) {
                return false;
            }
            data.hive(entry.getKey()).ifPresent(hive -> hive.raidOver(0));
            return true;
        });
        data.setDirty();
    }

    /** 每个恒星系最近一分钟的用电（瓦），母星不算。 */
    private static Map<Integer, Double> watts(MinecraftServer server, long now) {
        Map<Integer, Double> watts = new HashMap<>();
        ProductionData.get(server).stats().ledgers().forEach((key, ledger) -> {
            Identifier id = Identifier.tryParse(key);
            if (id == null) {
                return;
            }
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
            if (dimension == Level.OVERWORLD) {
                return;
            }
            Planets.bodyId(dimension).ifPresent(body -> {
                double perTick = (double) ledger.demand().sum(ProductionStats.Range.MINUTE, now) / (ProductionStats.SHORT_TICKS * ProductionStats.SHORT_SLOTS);
                watts.merge(body.system(), perTick * 20, Double::sum);
            });
        });
        return watts;
    }

    private static void tickHive(MinecraftServer server, HiveState hive, DarkFogSettings settings, double watts, long now) {
        int system = hive.system();
        Optional<DysonSphere> sphere = Dyson.existing(server, system);
        if (hive.status() == HiveState.Status.GONE) {
            sphere.ifPresent(s -> s.setSiphon(0));
            recall(server, system, hive);
            return;
        }
        if (!settings.enabled()) {
            // 关了黑雾：停摆，实体收回
            hive.skipTo(now);
            sphere.ifPresent(s -> s.setSiphon(0));
            recall(server, system, hive);
            return;
        }
        double siphon = sphere.map(s -> s.generation() * hive.siphonShare()).orElse(0.0);
        sphere.ifPresent(s -> s.setSiphon(siphon));
        boolean raiding = RAIDS.containsKey(system);
        double threat = raiding || settings.passive() ? 0 : HiveState.powerThreatPerTick(watts, settings);
        int relays = hive.status() == HiveState.Status.REBUILDING ? relays(server, system) : 1;
        HiveState.Status before = hive.status();
        hive.advanceTo(now, new HiveState.Environment(settings.growth(), light(server, system), siphon, threat, relays));
        if (before == HiveState.Status.REBUILDING && hive.status() == HiveState.Status.ACTIVE) {
            notice(server, system, Component.translatable("message.universe.dark_fog.hive_rebuilt").withStyle(ChatFormatting.RED));
        } else if (before == HiveState.Status.REBUILDING && hive.status() == HiveState.Status.GONE) {
            notice(server, system, Component.translatable("message.universe.dark_fog.hive_destroyed").withStyle(ChatFormatting.GOLD));
        }
        if (hive.status() != HiveState.Status.ACTIVE) {
            recall(server, system, hive);
            return;
        }
        if (!raiding && !settings.passive() && hive.raidReady()) {
            raid(server, hive, settings, now);
        }
        Vec3 center = center(hive);
        if (hive.relayReady() && DarkFogFlights.relaysInFlight(server, system) < HiveState.MAX_RELAYS_IN_FLIGHT) {
            if (DarkFogFlights.launchRelay(server, system, hive.level(), center)) {
                hive.relayLaunched();
            } else {
                hive.relayFailed();
            }
        }
        if (hive.seedReady() && DarkFogFlights.seedsInFlight(server, system) == 0) {
            if (DarkFogFlights.launchSeed(server, system, hive.level(), center)) {
                hive.seedLaunched();
            } else {
                hive.seedFailed();
            }
        }
        ServerLevel space = server.getLevel(SpaceDimensions.key(system));
        if (space != null) {
            placeOrRecall(space, hive);
        } else {
            recall(server, system, hive);
        }
    }

    /** 恒星系里还活着的中继站：地面基地上的加上飞着的。 */
    public static int relays(MinecraftServer server, int system) {
        int count = DarkFogFlights.relaysInFlight(server, system);
        for (DarkFogBases.BaseRef ref : DarkFogBases.bases(server, system)) {
            if (ref.base().status() == BaseState.Status.ACTIVE && ref.base().relayAlive()) {
                count++;
            }
        }
        return count;
    }

    // ---- 轰炸

    /** 威胁满了：挑这个恒星系里有人站着的星球（母星除外），在离人最近的用电设备那里派一队枪骑。 */
    private static void raid(MinecraftServer server, HiveState hive, DarkFogSettings settings, long now) {
        int size = hive.raidSize(settings.aggressiveness());
        if (size <= 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD || Planets.bodyId(level.dimension()).filter(body -> body.system() == hive.system()).isEmpty()
                    || LancerRaids.active(level)) {
                continue;
            }
            Optional<BlockPos> target = raidTarget(level);
            if (target.isEmpty()) {
                continue;
            }
            int sent = LancerRaids.launch(level, target.get(), size, hive.level(), hive.system());
            if (sent > 0) {
                hive.raidLaunched(sent);
                RAIDS.put(hive.system(), now);
                return;
            }
        }
    }

    /** 这颗星球上离人最近的用电设备（{@link #RAID_REACH} 格以内）。 */
    static Optional<BlockPos> raidTarget(ServerLevel level) {
        LevelPower power = LevelPower.existing(level);
        if (power == null) {
            return Optional.empty();
        }
        BlockPos best = null;
        double bestDistance = RAID_REACH * RAID_REACH;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (PowerSystem.Grid grid : power.grids()) {
                if (grid.demand() <= 0) {
                    continue;
                }
                long id = grid.nearest(player.getX(), player.getZ());
                if (id == Long.MIN_VALUE) {
                    continue;
                }
                BlockPos pos = BlockPos.of(id);
                double d = pos.distToCenterSqr(player.getX(), pos.getY() + 0.5, player.getZ());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = pos;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // ---- 放出来、收回

    public static Vec3 center(HiveState hive) {
        return new Vec3(hive.x(), hive.y(), hive.z());
    }

    private static Vec3 slotPos(HiveState hive, int slot) {
        HiveLayout.Slot s = HiveLayout.slot(slot);
        return new Vec3(hive.x() + s.dx(), hive.y() + s.dy(), hive.z() + s.dz());
    }

    private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, Vec3 pos, double range) {
        ServerPlayer best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double d = player.position().distanceToSqr(pos);
            if (d <= bestDistance) {
                bestDistance = d;
                best = player;
            }
        }
        return best;
    }

    /** 巢穴占的那几个区块都加载了没有。 */
    private static boolean loaded(ServerLevel level, HiveState hive) {
        int r = HiveLayout.RADIUS;
        for (int dx = -r; dx <= r; dx += r) {
            for (int dz = -r; dz <= r; dz += r) {
                if (!level.isLoaded(BlockPos.containing(hive.x() + dx, 0, hive.z() + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void placeOrRecall(ServerLevel space, HiveState hive) {
        Live live = LIVE.get(hive.system());
        boolean placed = live != null && live.placed;
        boolean wanted = nearestPlayer(space, center(hive), placed ? RECALL_RANGE : PLACE_RANGE) != null && loaded(space, hive);
        if (wanted) {
            place(space, hive, LIVE.computeIfAbsent(hive.system(), key -> new Live()));
        } else if (placed) {
            recall(space.getServer(), hive.system(), hive);
        }
    }

    private static void place(ServerLevel level, HiveState hive, Live live) {
        live.level = level;
        live.placed = true;
        for (int slot = 0; slot < HiveLayout.size(); slot++) {
            HiveBuilding building = live.buildings[slot];
            if (building != null && (building.isRemoved() || !building.isAlive())) {
                live.buildings[slot] = building = null;
            }
            if (!hive.alive(slot)) {
                if (building != null) {
                    building.discard();
                    live.buildings[slot] = null;
                }
                continue;
            }
            if (building == null) {
                building = HiveBuilding.create(level, HiveLayout.slot(slot).kind(), hive.system(), slot, hive.level(), slotPos(hive, slot));
                building.setHealth(Math.max(1, building.getMaxHealth() * hive.health(slot)));
                building.setPowered(true);
                level.addFreshEntity(building);
                live.buildings[slot] = building;
            } else {
                if (building.darkFogLevel() != hive.level()) {
                    building.setDarkFogLevel(hive.level());
                }
                building.setPowered(true);
                if (building.getMaxHealth() > 0) {
                    hive.setHealth(slot, building.getHealth() / building.getMaxHealth());
                }
            }
        }
        placeDefenders(level, hive, live);
    }

    private static void placeDefenders(ServerLevel level, HiveState hive, Live live) {
        Vec3 home = center(hive);
        live.defenders.removeIf(unit -> {
            if (unit.isRemoved() || !unit.isAlive()) {
                return true;
            }
            if (unit.position().distanceToSqr(home) > LEASH * LEASH) {
                // 追得太远了：回港口
                unit.discard();
                return true;
            }
            return false;
        });
        for (SpaceUnitKind kind : SpaceUnitKind.values()) {
            int wanted = Math.min(hive.units(kind), DEFENDERS[kind.ordinal()]);
            List<DarkFogSpaceUnit> ofKind = live.defenders.stream().filter(unit -> unit.kind() == kind).toList();
            for (int i = wanted; i < ofKind.size(); i++) {
                ofKind.get(i).discard();
                live.defenders.remove(ofKind.get(i));
            }
            for (int i = ofKind.size(); i < wanted; i++) {
                live.defenders.add(DarkFogSpaceUnit.spawn(level, kind, hive.system(), hive.level(), portPos(level, hive, kind), home,
                        DarkFogSpaceUnit.Mode.DEFEND));
            }
        }
    }

    /** 这种单位从哪个港口出来：随便挑一个还在的，旁边几格。 */
    private static Vec3 portPos(ServerLevel level, HiveState hive, SpaceUnitKind kind) {
        List<Integer> ports = new ArrayList<>();
        for (int slot = 0; slot < HiveLayout.size(); slot++) {
            if (hive.alive(slot) && HiveLayout.slot(slot).kind() == kind.port()) {
                ports.add(slot);
            }
        }
        Vec3 pos = ports.isEmpty() ? center(hive) : slotPos(hive, ports.get(level.getRandom().nextInt(ports.size())));
        double yaw = level.getRandom().nextDouble() * Math.PI * 2;
        double pitch = (level.getRandom().nextDouble() - 0.5) * Math.PI * 0.6;
        double r = 8 + level.getRandom().nextDouble() * 6;
        return pos.add(Math.cos(yaw) * Math.cos(pitch) * r, Math.sin(pitch) * r, Math.sin(yaw) * Math.cos(pitch) * r);
    }

    /** 人走远了（或者巢穴没了）：建筑的血量写回存档，实体都收回。 */
    private static void recall(MinecraftServer server, int system, HiveState hive) {
        Live live = LIVE.remove(system);
        if (live == null) {
            return;
        }
        for (int slot = 0; slot < HiveLayout.size(); slot++) {
            HiveBuilding building = live.buildings[slot];
            if (building != null) {
                if (building.isAlive() && !building.isRemoved() && building.getMaxHealth() > 0 && hive.status() == HiveState.Status.ACTIVE) {
                    hive.setHealth(slot, building.getHealth() / building.getMaxHealth());
                }
                building.discard();
            }
        }
        live.defenders.forEach(Entity::discard);
    }

    // ---- 给中继站和种子（R）

    /** 种子到了：在这个恒星系建一座新巢，从 {@code darkFogLevel} 级开始（已经有活着的巢穴就不建）。 */
    public static void found(MinecraftServer server, int system, int darkFogLevel) {
        DarkFogHiveData data = DarkFogHiveData.get(server);
        if (hasHive(server, system) || !UniverseRules.darkFog(server).enabled()) {
            return;
        }
        Optional<StarSystem> star = Planets.galaxy(server).system(system);
        if (star.isEmpty()) {
            return;
        }
        HiveState hive = newHive(server, star.get());
        hive.found(darkFogLevel, now(server));
        data.put(hive);
        notice(server, system, Component.translatable("message.universe.dark_fog.hive_founded").withStyle(ChatFormatting.RED));
    }

    /** 这个恒星系有没有活着的（或者正在重建的）巢穴。种子按这个挑去处。 */
    public static boolean hasHive(MinecraftServer server, int system) {
        return DarkFogHiveData.get(server).hive(system).filter(hive -> hive.status() != HiveState.Status.GONE).isPresent();
    }

    /**
     * 一座巢穴的概况，星图上画标记用。
     *
     * @param system 恒星系编号
     * @param alive  核心还在（false 是打掉了、等着重建）
     * @param level  黑雾等级
     * @param threat 威胁，0–1
     * @param x      在太空维度里的位置
     */
    public record Summary(int system, boolean alive, int level, double threat, double x, double y, double z) {
    }

    /** 所有的巢穴（已经彻底消灭的不算）。 */
    public static List<Summary> summaries(MinecraftServer server) {
        List<Summary> list = new ArrayList<>();
        for (HiveState hive : DarkFogHiveData.get(server).hives().values()) {
            if (hive.status() != HiveState.Status.GONE) {
                list.add(new Summary(hive.system(), hive.status() == HiveState.Status.ACTIVE, hive.level(), hive.threat(), hive.x(), hive.y(),
                        hive.z()));
            }
        }
        list.sort((a, b) -> Integer.compare(a.system(), b.system()));
        return list;
    }

    /** 恒星系里的中继站挨了打（地面基地上的、飞着的都算）：巢穴的威胁猛涨。 */
    public static void onRelayHurt(MinecraftServer server, int system, float damage) {
        active(server, system).ifPresent(hive -> {
            hive.addThreat(damage * HiveState.relayThreatPerDamage(UniverseRules.darkFog(server)));
            DarkFogHiveData.get(server).setDirty();
        });
    }

    private static Optional<HiveState> active(MinecraftServer server, int system) {
        return DarkFogHiveData.get(server).hive(system).filter(hive -> hive.status() == HiveState.Status.ACTIVE);
    }

    // ---- 给巢穴建筑（B）

    /** 巢穴建筑挨了打（伤害已经扣过），{@code healthFraction} 是剩下的血量比例。 */
    public static void onBuildingHurt(ServerLevel level, int system, int slot, float damage, float healthFraction) {
        MinecraftServer server = level.getServer();
        active(server, system).ifPresent(hive -> {
            if (slot < 0 || slot >= HiveLayout.size()) {
                return;
            }
            hive.hurt(damage, HiveState.buildingThreatPerDamage(HiveLayout.slot(slot).kind(), UniverseRules.darkFog(server)));
            hive.setHealth(slot, healthFraction);
            DarkFogHiveData.get(server).setDirty();
        });
    }

    /** 巢穴建筑被打掉了，{@code at} 是它的位置。 */
    public static void onBuildingDestroyed(ServerLevel level, int system, int slot, Vec3 at, int darkFogLevel) {
        if (slot < 0 || slot >= HiveLayout.size()) {
            return;
        }
        dropLoot(level, at, darkFogLevel, lootWeight(HiveLayout.slot(slot).kind()));
        MinecraftServer server = level.getServer();
        active(server, system).ifPresent(hive -> {
            Live live = LIVE.get(system);
            if (live != null) {
                live.buildings[slot] = null;
            }
            if (slot == HiveLayout.CORE) {
                collapse(server, hive, at);
            } else {
                hive.destroy(slot);
            }
            DarkFogHiveData.get(server).setDirty();
        });
    }

    /**
     * 掉东西：星球上和地面基地一样（{@link DarkFogBases#dropLoot}）；太空里失重飘着、慢慢散开，存在得久一倍，
     * 开船或者飞过去就捡到。
     */
    public static void dropLoot(ServerLevel level, Vec3 at, int darkFogLevel, double weight) {
        if (!SpaceDimensions.isSpace(level.dimension())) {
            DarkFogBases.dropLoot(level, at, darkFogLevel, weight);
            return;
        }
        Rng rng = Rng.of(level.getRandom().nextLong());
        for (DarkFogLoot.Drop drop : DarkFogLoot.roll(darkFogLevel, weight, rng)) {
            Identifier id = Identifier.tryParse(drop.item());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.get(id).ifPresent(item -> {
                ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(item, drop.count()),
                        (level.getRandom().nextDouble() - 0.5) * 0.06, (level.getRandom().nextDouble() - 0.5) * 0.06,
                        (level.getRandom().nextDouble() - 0.5) * 0.06);
                entity.setNoGravity(true);
                entity.setDefaultPickUpDelay();
                entity.lifespan = DarkFogLoot.LIFESPAN_TICKS * 2;
                entity.addTag(FacilityRepairs.DARK_FOG_LOOT_TAG);
                level.addFreshEntity(entity);
            });
        }
    }

    /** 一座建筑按几份算掉落：核心最多。 */
    public static double lootWeight(HiveBuildingKind kind) {
        return switch (kind) {
            case CORE -> 16;
            case LANCER_PORT, HUMPBACK_PORT -> 6;
            case PLASMA_TOWER, PHASE_LASER -> 5;
            case PHOTON_RECEIVER -> 4;
        };
    }

    /** 一个单位按几份算掉落。 */
    public static double lootWeight(SpaceUnitKind kind) {
        return kind == SpaceUnitKind.HUMPBACK ? 3 : 1;
    }

    /** 核心没了：建筑一起瓦解、掉东西，守巢的单位散了；恒星系里还有中继站就等着重建，没有就消灭。 */
    private static void collapse(MinecraftServer server, HiveState hive, @Nullable Vec3 at) {
        int system = hive.system();
        Live live = LIVE.remove(system);
        if (live != null) {
            for (int slot = 0; slot < HiveLayout.size(); slot++) {
                HiveBuilding other = live.buildings[slot];
                if (other != null && other.isAlive() && !other.isRemoved() && other.level() instanceof ServerLevel level) {
                    dropLoot(level, other.position(), other.darkFogLevel(), lootWeight(other.hiveKind()) / 2);
                    level.sendParticles(ParticleTypes.EXPLOSION, other.getX(), other.getY() + 1, other.getZ(), 3, 2, 2, 2, 0);
                    other.discard();
                }
            }
            live.defenders.forEach(Entity::discard);
            if (at != null && live.level != null) {
                live.level.playSound(null, at.x, at.y, at.z, UniverseSounds.EXPLOSION_LARGE.get(), SoundSource.HOSTILE, 1.0F, 0.7F);
            }
        }
        boolean relays = relays(server, system) > 0;
        hive.collapse(relays);
        Dyson.existing(server, system).ifPresent(sphere -> sphere.setSiphon(0));
        notice(server, system, Component.translatable(relays ? "message.universe.dark_fog.hive_collapsed" : "message.universe.dark_fog.hive_destroyed")
                .withStyle(ChatFormatting.GOLD));
    }

    // ---- 给太空单位（U）

    /** 巢穴的单位挨了打（伤害已经扣过）；{@code defending} 是守巢的，轰炸星球的是 false。 */
    public static void onUnitHurt(ServerLevel level, int system, float damage, boolean defending) {
        MinecraftServer server = level.getServer();
        active(server, system).ifPresent(hive -> {
            hive.hurt(damage, defending ? HiveState.unitThreatPerDamage(UniverseRules.darkFog(server)) : 0);
            DarkFogHiveData.get(server).setDirty();
        });
    }

    /** 巢穴的单位被打死了（守巢的、轰炸星球的都调这个）；掉落由这里管。 */
    public static void onUnitKilled(ServerLevel level, int system, SpaceUnitKind kind, boolean defending, Vec3 at, int darkFogLevel) {
        dropLoot(level, at, darkFogLevel, lootWeight(kind));
        if (!defending) {
            return;
        }
        MinecraftServer server = level.getServer();
        active(server, system).ifPresent(hive -> {
            hive.unitLost(kind);
            Live live = LIVE.get(system);
            if (live != null) {
                live.defenders.removeIf(unit -> unit.isRemoved() || !unit.isAlive());
            }
            DarkFogHiveData.get(server).setDirty();
        });
    }

    /** 一次枪骑轰炸结束了，{@code survivors} 架活着回去。 */
    public static void onRaidOver(MinecraftServer server, int system, int survivors) {
        RAIDS.remove(system);
        active(server, system).ifPresent(hive -> {
            hive.raidOver(survivors);
            DarkFogHiveData.get(server).setDirty();
        });
    }

    // ---- 给护卫舰清剿（T）

    /**
     * 清剿的结果。
     *
     * @param found              这个恒星系有活着的巢穴
     * @param unitsKilled        打掉了几个单位
     * @param buildingsDestroyed 打掉了几座建筑
     * @param level              巢穴的等级（算掉落用）
     */
    public record Strike(boolean found, int unitsKilled, int buildingsDestroyed, int level) {
        public static final Strike NONE = new Strike(false, 0, 0, 0);
    }

    /** 护卫舰清剿回来了：赢了 {@code successes} 段遭遇，按这个打掉巢穴的单位和建筑（不会打掉核心）。 */
    public static Strike strike(MinecraftServer server, int system, int successes) {
        Optional<HiveState> found = active(server, system);
        if (found.isEmpty()) {
            return Strike.NONE;
        }
        HiveState hive = found.get();
        int[] result = hive.strike(successes);
        // 放着的实体下一秒按存档对齐：没了的建筑收回、多出来的单位回港口
        DarkFogHiveData.get(server).setDirty();
        return new Strike(true, result[0], result[1], hive.level());
    }

    // ---- 消息

    /** 发给这个恒星系里的人（太空、各星球；母星系也包括主世界）。 */
    private static void notice(MinecraftServer server, int system, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            OptionalInt at = Dyson.systemOf(player.level().dimension());
            if (at.isPresent() && at.getAsInt() == system) {
                player.sendSystemMessage(message);
            }
        }
    }

    // ---- 调试命令和游戏内测试用

    static DarkFogHiveData data(MinecraftServer server) {
        return DarkFogHiveData.get(server);
    }

    /** 在这个恒星系按种子的位置马上建一座（换掉原来的），从 {@code level} 级开始，带初始成长。 */
    static HiveState create(MinecraftServer server, int system, int level) {
        StarSystem star = Planets.galaxy(server).system(system).orElseThrow();
        HiveState hive = newHive(server, star);
        return replace(server, hive, level, true);
    }

    /** 在指定的位置马上建一座（换掉原来的）。 */
    static HiveState createAt(MinecraftServer server, int system, Vec3 at, int level) {
        return replace(server, new HiveState(system, at.x, at.y, at.z), level, true);
    }

    private static HiveState replace(MinecraftServer server, HiveState hive, int level, boolean grow) {
        recallAll(server, hive.system());
        RAIDS.remove(hive.system());
        if (grow) {
            hive.activate(level, now(server), UniverseRules.darkFog(server).growth(), light(server, hive.system()));
        } else {
            hive.found(level, now(server));
        }
        DarkFogHiveData.get(server).put(hive);
        return hive;
    }

    private static void recallAll(MinecraftServer server, int system) {
        DarkFogHiveData.get(server).hive(system).ifPresentOrElse(hive -> recall(server, system, hive), () -> {
            Live live = LIVE.remove(system);
            if (live != null) {
                for (HiveBuilding building : live.buildings) {
                    if (building != null) {
                        building.discard();
                    }
                }
                live.defenders.forEach(Entity::discard);
            }
        });
    }

    /** 打掉核心（调试命令用）：和真被打掉一样，只是不掉东西。 */
    static boolean destroyCore(MinecraftServer server, int system) {
        Optional<HiveState> hive = active(server, system);
        hive.ifPresent(h -> {
            collapse(server, h, null);
            DarkFogHiveData.get(server).setDirty();
        });
        return hive.isPresent();
    }

    /** 收拾掉一座巢穴：实体收回，存档里删掉。 */
    static boolean remove(MinecraftServer server, int system) {
        recallAll(server, system);
        RAIDS.remove(system);
        Dyson.existing(server, system).ifPresent(sphere -> sphere.setSiphon(0));
        boolean had = DarkFogHiveData.get(server).hive(system).isPresent();
        DarkFogHiveData.get(server).remove(system);
        return had;
    }

    /** 有没有正在轰炸的。 */
    static boolean raiding(int system) {
        return RAIDS.containsKey(system);
    }

    /** 放出来的建筑有几座（调试命令显示用）。 */
    static int placedBuildings(int system) {
        Live live = LIVE.get(system);
        if (live == null) {
            return 0;
        }
        int count = 0;
        for (HiveBuilding building : live.buildings) {
            if (building != null && !building.isRemoved()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 游戏内测试用：一座不自动结算、不放出来的巢穴（种子落下时的样子），原来那座先收着，{@link #removeInert} 时放回去。
     */
    public static HiveState createInert(MinecraftServer server, int system, int level) {
        DarkFogHiveData data = DarkFogHiveData.get(server);
        if (!INERT.contains(system)) {
            data.hive(system).ifPresent(old -> STASHED.put(system, old));
        }
        INERT.add(system);
        StarSystem star = Planets.galaxy(server).system(system).orElseThrow();
        return replace(server, newHive(server, star), level, false);
    }

    /** 游戏内测试用：收掉测试的巢穴，放回原来那座。 */
    public static void removeInert(MinecraftServer server, int system) {
        remove(server, system);
        INERT.remove(system);
        HiveState old = STASHED.remove(system);
        if (old != null) {
            DarkFogHiveData.get(server).put(old);
        }
    }

    /** 游戏内测试用：按现在的环境结算一次（测试的巢穴平时不结算）。 */
    public static void settle(MinecraftServer server, int system) {
        DarkFogHiveData.get(server).hive(system).ifPresent(hive -> {
            DarkFogSettings settings = UniverseRules.darkFog(server);
            int relays = hive.status() == HiveState.Status.REBUILDING ? relays(server, system) : 1;
            hive.advanceTo(now(server), new HiveState.Environment(settings.growth(), light(server, system), 0, 0, relays));
            DarkFogHiveData.get(server).setDirty();
        });
    }

    /** 游戏内测试用：像建筑实体被打掉那样打掉一座（核心就瓦解）。 */
    public static void destroyForTest(ServerLevel level, int system, int slot, Vec3 at) {
        onBuildingDestroyed(level, system, slot, at, DarkFogHiveData.get(level.getServer()).hive(system).map(HiveState::level).orElse(0));
    }
}
