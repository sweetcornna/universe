package io.github.sweetcornna.universe.darkfog.flight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.darkfog.BaseLayout;
import io.github.sweetcornna.universe.core.darkfog.BaseState;
import io.github.sweetcornna.universe.core.darkfog.BuildingKind;
import io.github.sweetcornna.universe.core.darkfog.DarkFogLoot;
import io.github.sweetcornna.universe.core.darkfog.Flight;
import io.github.sweetcornna.universe.core.darkfog.FlightPlan;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.darkfog.DarkFogBases;
import io.github.sweetcornna.universe.darkfog.DarkFogData;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.darkfog.shield.Shields;
import io.github.sweetcornna.universe.entity.ship.ShipCargo;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.factory.power.LevelPower;
import io.github.sweetcornna.universe.network.DarkFogMapPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.pirate.Pirates;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.rules.UniverseRules;
import io.github.sweetcornna.universe.stats.ProductionData;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 巢穴派出去的中继站和种子（ADR-0028 §八）。按时间飞，存档；起飞和到达的时候有实体，打得掉。
 *
 * <ul>
 *   <li>中继站：飞到这个恒星系里一颗有玩家设施的星球（护盾盖住的地方不能落），落地建新基地（{@code DarkFogBases.landRelay}），
 *       或者给中继站被打掉的基地补上（{@code DarkFogBases.restoreRelay}）。</li>
 *   <li>种子：慢慢飞到最近的还没有巢穴的恒星系，到了建新巢（{@code DarkFogHives.found}）。星图上看得见。</li>
 * </ul>
 *
 * <p>接口（签名别改）：巢穴调 {@link #launchRelay}、{@link #launchSeed}、{@link #relaysInFlight}、{@link #seedsInFlight}。
 *
 * <p>每秒结算一次所有飞行。起飞那一段，巢穴所在的太空维度加载着、出发点附近有人，就放一个实体沿着路线飞；降落那一段，
 * 星球加载着、落点附近有人，就放一个从高空落下来的中继站，落地那一刻才建基地；没人看着就直接落。实体被打掉，这次飞行就没了。
 */
public final class DarkFogFlights {
    private static final int INTERVAL = 20;
    /** 隔多久记一次各星球上玩家设施的位置。 */
    private static final int FACILITY_INTERVAL = 1200;
    /** 隔多久看一次星图的数据变没变。 */
    private static final int SYNC_INTERVAL = 200;
    /** 太空里离路线多近有人才放实体。 */
    private static final double SPACE_WATCH = 512;
    /** 星球上离落点多近有人才放降落的中继站（只看水平距离）。 */
    private static final double DESCENT_WATCH = 256;
    /** 起飞时每刻飞多远。 */
    private static final double RELAY_DEPART_SPEED = 0.6;
    private static final double SEED_DEPART_SPEED = 0.4;
    /** 种子进入恒星系时每刻飞多远。 */
    private static final double SEED_ARRIVE_SPEED = 0.5;
    /** 种子从离恒星多远的地方进来。 */
    private static final double SEED_ENTRY_DISTANCE = 26000;
    private static final double SEED_ENTRY_HEIGHT = 120;
    /** 往同一颗星球最多同时飞几个中继站。 */
    private static final int MAX_RELAYS_PER_PLANET = 2;
    /** 一颗星球上活着的基地到了这么多，中继站就不往那里落了。 */
    private static final int MAX_BASES_PER_PLANET = 12;
    /** 种子按几份掉东西（中继站按地上那座的份数）。 */
    private static final double SEED_LOOT = 12;
    /** 打掉、降落的提示发给多远内的人。 */
    private static final double NOTICE_RANGE = 384;

    /** 放出来的实体：哪一次飞行、哪一段。不存档。 */
    private record Live(DarkFogCraft craft, Flight.Phase phase) {
    }

    private static final Map<Integer, Live> LIVE = new HashMap<>();
    /** 已经告诉过目标恒星系里的人"种子来了"的飞行。 */
    private static final Set<Integer> ANNOUNCED = new HashSet<>();
    /** 上次发出去的星图数据，没变就不重发。 */
    private static int lastSignature;
    private static boolean mapDirty = true;

    private DarkFogFlights() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("darkfog/flights", () -> tick(event.getServer())));
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, map(player.level().getServer()));
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            LIVE.clear();
            ANNOUNCED.clear();
            lastSignature = 0;
            mapDirty = true;
        });
    }

    public static void registerModEvents(IEventBus modBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(UniverseEntities.DARK_FOG_FLYING_RELAY.get(), DarkFogCraft.createAttributes().build());
            event.put(UniverseEntities.DARK_FOG_SEED.get(), DarkFogCraft.createAttributes().build());
        });
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    // ---- 给巢穴（H）

    /**
     * 巢穴派出一个中继站，从 {@code from}（太空维度里巢穴的位置）出发。
     *
     * @return 没有能去的星球时是 false（巢穴别扣物质）
     */
    public static boolean launchRelay(MinecraftServer server, int system, int darkFogLevel, Vec3 from) {
        if (!UniverseRules.darkFog(server).enabled()) {
            return false;
        }
        Optional<ResourceKey<Level>> target = pickPlanet(server, system);
        return target.isPresent() && launchRelayTo(server, system, darkFogLevel, from, target.get()).isPresent();
    }

    /** 往指定的星球派一个中继站（调试命令、游戏内测试用）。不是这个恒星系里能落的星球（母星也不行）时是空。 */
    public static Optional<Flight> launchRelayTo(MinecraftServer server, int system, int darkFogLevel, Vec3 from, ResourceKey<Level> dimension) {
        Optional<Planet> planet = Planets.generatedPlanet(Planets.galaxy(server), dimension);
        if (dimension == Level.OVERWORLD || planet.isEmpty() || planet.get().id().system() != system || !planet.get().landable()
                || planet.get().id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
            return Optional.empty();
        }
        DarkFogFlightData data = DarkFogFlightData.get(server);
        int id = data.nextId();
        Flight flight = FlightPlan.relay(id, system, dimension.identifier().toString(), darkFogLevel, from.x, from.y, from.z, now(server),
                Rng.of(server.overworld().getRandom().nextLong()));
        data.add(flight);
        mapDirty = true;
        return Optional.of(flight);
    }

    /**
     * 巢穴放出一颗种子，飞到最近的还没有巢穴的恒星系。
     *
     * @return 没有能去的恒星系时是 false
     */
    public static boolean launchSeed(MinecraftServer server, int fromSystem, int darkFogLevel, Vec3 from) {
        if (!UniverseRules.darkFog(server).enabled()) {
            return false;
        }
        Galaxy galaxy = Planets.galaxy(server);
        OptionalInt target = FlightPlan.seedTarget(galaxy.systems(), fromSystem, system -> taken(server, system, -1));
        return target.isPresent() && launchSeedTo(server, fromSystem, target.getAsInt(), darkFogLevel, from).isPresent();
    }

    /** 往指定的恒星系放一颗种子（调试命令、游戏内测试用）。 */
    public static Optional<Flight> launchSeedTo(MinecraftServer server, int fromSystem, int toSystem, int darkFogLevel, Vec3 from) {
        Galaxy galaxy = Planets.galaxy(server);
        Optional<StarSystem> origin = galaxy.system(fromSystem);
        Optional<StarSystem> destination = galaxy.system(toSystem);
        if (origin.isEmpty() || destination.isEmpty() || fromSystem == toSystem) {
            return Optional.empty();
        }
        DarkFogFlightData data = DarkFogFlightData.get(server);
        Flight flight = FlightPlan.seed(data.nextId(), fromSystem, toSystem, origin.get().distanceTo(destination.get()), darkFogLevel,
                from.x, from.y, from.z, now(server));
        data.add(flight);
        mapDirty = true;
        noticeSystem(server, fromSystem, Component.translatable("message.universe.dark_fog.seed_launched", destination.get().name().zh(),
                destination.get().name().en()).withStyle(ChatFormatting.RED));
        return Optional.of(flight);
    }

    /** 这个恒星系里正在飞的中继站有几个。 */
    public static int relaysInFlight(MinecraftServer server, int system) {
        return (int) DarkFogFlightData.get(server).flights().stream().filter(f -> f.kind() == Flight.Kind.RELAY && f.system() == system).count();
    }

    /** 从这个恒星系出发、还在路上的种子有几颗。 */
    public static int seedsInFlight(MinecraftServer server, int fromSystem) {
        return (int) DarkFogFlightData.get(server).flights().stream().filter(f -> f.kind() == Flight.Kind.SEED && f.system() == fromSystem).count();
    }

    /** 种子不去的恒星系：有巢穴了，或者已经有别的种子往那边飞。 */
    private static boolean taken(MinecraftServer server, int system, int except) {
        if (DarkFogHives.hasHive(server, system)) {
            return true;
        }
        for (Flight flight : DarkFogFlightData.get(server).flights()) {
            if (flight.kind() == Flight.Kind.SEED && flight.target() == system && flight.id() != except) {
                return true;
            }
        }
        return false;
    }

    // ---- 挑星球、挑落点

    private static ResourceKey<Level> dimensionKey(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return ResourceKey.create(Registries.DIMENSION, identifier == null ? Identifier.withDefaultNamespace("overworld") : identifier);
    }

    /** 中继站去哪颗星球：先去基地丢了中继站的，再去玩家用电最多的（母星不去）。 */
    private static Optional<ResourceKey<Level>> pickPlanet(MinecraftServer server, int system) {
        for (DarkFogBases.BaseRef ref : DarkFogBases.bases(server, system)) {
            if (ref.dimension() != Level.OVERWORLD && ref.base().status() == BaseState.Status.ACTIVE && !ref.base().relayAlive()
                    && relaysTo(server, ref.dimension()) < MAX_RELAYS_PER_PLANET) {
                return Optional.of(ref.dimension());
            }
        }
        ResourceKey<Level> best = null;
        double bestDemand = 0;
        for (ResourceKey<Level> dimension : landable(server, system)) {
            double demand = demand(server, dimension);
            if (demand <= 0 && DarkFogFlightData.get(server).facility(dimension.identifier().toString()).isEmpty()) {
                continue;
            }
            if (relaysTo(server, dimension) >= MAX_RELAYS_PER_PLANET || activeBases(server, dimension) >= MAX_BASES_PER_PLANET) {
                continue;
            }
            if (best == null || demand > bestDemand) {
                best = dimension;
                bestDemand = demand;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 这个恒星系里能落的星球（母星不算）。 */
    private static List<ResourceKey<Level>> landable(MinecraftServer server, int system) {
        List<ResourceKey<Level>> list = new ArrayList<>();
        Planets.galaxy(server).system(system).ifPresent(s -> s.bodies().stream()
                .filter(Planet::landable)
                .filter(p -> !p.id().equals(GalaxyGenerator.HOME_PLANET_ID))
                .forEach(p -> list.add(Planets.dimension(p.id()))));
        return list;
    }

    /** 这颗星球最近一小时的用电（焦）。 */
    private static double demand(MinecraftServer server, ResourceKey<Level> dimension) {
        ProductionStats.Ledger ledger = ProductionData.get(server).stats().ledgers().get(dimension.identifier().toString());
        return ledger == null ? 0 : ledger.demand().sum(ProductionStats.Range.HOUR, now(server));
    }

    private static int relaysTo(MinecraftServer server, ResourceKey<Level> dimension) {
        String key = dimension.identifier().toString();
        return (int) DarkFogFlightData.get(server).flights().stream().filter(f -> f.kind() == Flight.Kind.RELAY && f.dimension().equals(key)).count();
    }

    private static int activeBases(MinecraftServer server, ResourceKey<Level> dimension) {
        return DarkFogData.get(server).planet(dimension.identifier().toString())
                .map(planet -> (int) planet.bases().stream().filter(base -> base.status() == BaseState.Status.ACTIVE).count()).orElse(0);
    }

    /** 星球加载着时记下玩家设施的一个位置：用电最多的电网里离上次记的点最近的设备。 */
    private static void recordFacilities(MinecraftServer server, DarkFogFlightData data) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() == Level.OVERWORLD || Planets.planet(level).isEmpty()) {
                continue;
            }
            facilityAnchor(level, data).ifPresent(pos -> data.setFacility(level.dimension().identifier().toString(), pos[0], pos[1]));
        }
    }

    private static Optional<int[]> facilityAnchor(ServerLevel level, DarkFogFlightData data) {
        LevelPower power = LevelPower.existing(level);
        if (power == null) {
            return Optional.empty();
        }
        PowerSystem.Grid best = null;
        for (PowerSystem.Grid grid : power.grids()) {
            if (grid.demand() > 0 && (best == null || grid.demand() > best.demand())) {
                best = grid;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        int[] last = data.facility(level.dimension().identifier().toString()).orElse(new int[] {0, 0});
        long id = best.nearest(last[0], last[1]);
        if (id == Long.MIN_VALUE) {
            return Optional.empty();
        }
        BlockPos pos = BlockPos.of(id);
        return Optional.of(new int[] {pos.getX(), pos.getZ()});
    }

    /**
     * 中继站到了，挑落点：恒星系里有丢了中继站的基地就去补；没有就在目标星球（不能去了就换一颗）的玩家设施几百格外挑一处。
     *
     * @return 挑到了
     */
    private static boolean chooseSite(MinecraftServer server, DarkFogFlightData data, Flight flight, boolean restore) {
        if (restore) {
            List<DarkFogBases.BaseRef> refs = new ArrayList<>(DarkFogBases.bases(server, flight.system()));
            // 先看本来要去的那颗星球
            refs.sort(Comparator.comparingInt(ref -> ref.dimension().identifier().toString().equals(flight.dimension()) ? 0 : 1));
            for (DarkFogBases.BaseRef ref : refs) {
                BaseState base = ref.base();
                if (ref.dimension() != Level.OVERWORLD && base.status() == BaseState.Status.ACTIVE && !base.relayAlive()) {
                    flight.land(ref.dimension().identifier().toString(), base.x(), base.z(), base.id());
                    data.setDirty();
                    return true;
                }
            }
        }
        ResourceKey<Level> dimension = dimensionKey(flight.dimension());
        if (!landable(server, flight.system()).contains(dimension) || activeBases(server, dimension) >= MAX_BASES_PER_PLANET) {
            Optional<ResourceKey<Level>> other = pickPlanet(server, flight.system());
            if (other.isEmpty()) {
                return false;
            }
            dimension = other.get();
        }
        String key = dimension.identifier().toString();
        ServerLevel planet = server.getLevel(dimension);
        int[] anchor = planet == null ? null : facilityAnchor(planet, data).orElse(null);
        if (anchor == null) {
            anchor = data.facility(key).orElse(null);
        }
        Rng rng = Rng.of(flight.id() * 0x9E3779B97F4A7C15L + flight.attempts() * 31L + now(server));
        if (anchor == null) {
            // 不知道玩家的设施在哪：在出生点一带随便挑
            anchor = new int[] {rng.range(-600, 600), rng.range(-600, 600)};
        }
        List<int[]> bases = new ArrayList<>();
        DarkFogData.get(server).planet(key).ifPresent(p -> p.bases().stream()
                .filter(base -> base.status() != BaseState.Status.VOID)
                .forEach(base -> bases.add(new int[] {base.x(), base.z()})));
        ResourceKey<Level> target = dimension;
        Optional<int[]> site = FlightPlan.landingSite(anchor[0] + 0.5, anchor[1] + 0.5, rng, bases, DarkFogBases.LANDING_SPACING,
                (x, z) -> Shields.covers(server, target, x, z));
        if (site.isEmpty()) {
            return false;
        }
        flight.land(key, site.get()[0], site.get()[1], -1);
        data.setDirty();
        return true;
    }

    // ---- 每秒结算

    private static void tick(MinecraftServer server) {
        long time = now(server);
        if (time % INTERVAL != 0 || !server.tickRateManager().runsNormally()) {
            return;
        }
        DarkFogFlightData data = DarkFogFlightData.get(server);
        if (time % FACILITY_INTERVAL == 0) {
            recordFacilities(server, data);
        }
        for (Flight flight : List.copyOf(data.flights())) {
            tickFlight(server, data, flight, time);
        }
        if (time % SYNC_INTERVAL == 0 || mapDirty) {
            sync(server, false);
        }
    }

    private static void tickFlight(MinecraftServer server, DarkFogFlightData data, Flight flight, long now) {
        Live live = LIVE.get(flight.id());
        if (live != null && (live.craft().isRemoved() || !live.craft().isAlive())) {
            // 区块卸载、人走开了：收回，进度照走
            LIVE.remove(flight.id());
            live = null;
        }
        if (live != null) {
            float health = live.craft().getHealth() / live.craft().getMaxHealth();
            if (Math.abs(health - flight.health()) > 1e-4) {
                flight.setHealth(health);
                data.setDirty();
            }
        }
        Flight.Phase phase = flight.phase(now);
        if (live != null && live.phase() != phase) {
            discard(flight.id());
            live = null;
        }
        switch (phase) {
            case DEPART -> {
                if (live == null) {
                    spawnDeparture(server, flight, now);
                }
            }
            case CRUISE -> {
            }
            case ARRIVE -> {
                if (flight.kind() == Flight.Kind.RELAY) {
                    arriveRelay(server, data, flight, now, live);
                } else {
                    arriveSeed(server, data, flight, now, live);
                }
            }
            case DONE -> {
                discard(flight.id());
                if (flight.kind() == Flight.Kind.RELAY) {
                    completeRelay(server, data, flight, now);
                } else {
                    completeSeed(server, data, flight, now);
                }
            }
        }
    }

    private static void discard(int id) {
        Live live = LIVE.remove(id);
        if (live != null && !live.craft().isRemoved()) {
            live.craft().discard();
        }
    }

    /** 这一点附近有没有人看着（区块要加载着）。 */
    private static boolean watched(ServerLevel level, Vec3 at, double range, boolean horizontal) {
        if (!level.isLoaded(BlockPos.containing(at))) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - at.x;
            double dz = player.getZ() - at.z;
            double dy = horizontal ? 0 : player.getY() - at.y;
            if (dx * dx + dy * dy + dz * dz <= range * range) {
                return true;
            }
        }
        return false;
    }

    /** 起飞往哪个方向：中继站朝目标星球，种子朝目标恒星系（星团里的方向，压到太空维度的水平面上，稍微往上）。 */
    private static Vec3 departureDirection(MinecraftServer server, Flight flight) {
        Galaxy galaxy = Planets.galaxy(server);
        Vec3 from = new Vec3(flight.fromX(), flight.fromY(), flight.fromZ());
        if (flight.kind() == Flight.Kind.RELAY) {
            Optional<BodyId> body = Planets.bodyId(dimensionKey(flight.dimension()));
            Optional<StarSystem> system = galaxy.system(flight.system());
            if (body.isPresent() && system.isPresent()) {
                Optional<SpaceLayout.Body> target = SpaceLayout.find(SpaceLayout.of(system.get()), body.get());
                if (target.isPresent()) {
                    Vec3 to = new Vec3(target.get().x(), target.get().y(), target.get().z()).subtract(from);
                    if (to.lengthSqr() > 1) {
                        return to.normalize();
                    }
                }
            }
            return from.lengthSqr() > 1 ? from.normalize() : new Vec3(1, 0, 0);
        }
        return galacticDirection(galaxy, flight.system(), flight.target()).add(0, 0.15, 0).normalize();
    }

    /** 星团里从 {@code from} 指向 {@code to} 的水平方向。 */
    private static Vec3 galacticDirection(Galaxy galaxy, int from, int to) {
        Optional<StarSystem> a = galaxy.system(from);
        Optional<StarSystem> b = galaxy.system(to);
        if (a.isEmpty() || b.isEmpty()) {
            return new Vec3(1, 0, 0);
        }
        Vec3 d = new Vec3(b.get().x() - a.get().x(), 0, b.get().z() - a.get().z());
        return d.lengthSqr() < 1e-9 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private static void spawnDeparture(MinecraftServer server, Flight flight, long now) {
        ServerLevel space = server.getLevel(SpaceDimensions.key(flight.system()));
        if (space == null) {
            return;
        }
        boolean seed = flight.kind() == Flight.Kind.SEED;
        Vec3 origin = new Vec3(flight.fromX(), flight.fromY(), flight.fromZ());
        Vec3 direction = departureDirection(server, flight);
        double speed = seed ? SEED_DEPART_SPEED : RELAY_DEPART_SPEED;
        Vec3 at = origin.add(direction.scale(speed * Math.max(0, now - flight.launched())));
        if (!watched(space, at, SPACE_WATCH, false)) {
            return;
        }
        DarkFogCraft craft = DarkFogCraft.line(space, seed, flight.id(), flight.level(), flight.health(), origin, direction, speed,
                flight.launched(), flight.departEnd());
        space.addFreshEntity(craft);
        LIVE.put(flight.id(), new Live(craft, Flight.Phase.DEPART));
    }

    private static void arriveRelay(MinecraftServer server, DarkFogFlightData data, Flight flight, long now, @Nullable Live live) {
        if (!flight.siteChosen() && !chooseSite(server, data, flight, true)) {
            retry(server, data, flight, now);
            return;
        }
        if (live != null) {
            return;
        }
        ServerLevel planet = server.getLevel(dimensionKey(flight.dimension()));
        if (planet == null) {
            return;
        }
        Vec3 ground = landingPoint(server, planet, flight);
        if (ground == null || !watched(planet, ground, DESCENT_WATCH, true)) {
            return;
        }
        DarkFogCraft craft = DarkFogCraft.descent(planet, flight.id(), flight.level(), flight.health(), ground, flight.arriveStart(), flight.end());
        planet.addFreshEntity(craft);
        LIVE.put(flight.id(), new Live(craft, Flight.Phase.ARRIVE));
        notice(planet, ground, Component.translatable("message.universe.dark_fog.relay_descending", flight.siteX(), flight.siteZ())
                .withStyle(ChatFormatting.RED), NOTICE_RANGE);
    }

    /** 降落到哪：基地里中继站悬着的位置（补中继站的按那处基地，新落地的按落点的地面估）；区块没加载是空。 */
    private static @Nullable Vec3 landingPoint(MinecraftServer server, ServerLevel planet, Flight flight) {
        BlockPos column = new BlockPos(flight.siteX(), 0, flight.siteZ());
        if (!planet.isLoaded(column)) {
            return null;
        }
        BaseLayout.Slot slot = BaseLayout.slot(BaseLayout.RELAY);
        if (flight.baseId() >= 0) {
            Optional<BaseState> base = DarkFogData.get(server).planet(flight.dimension()).flatMap(p -> p.base(flight.baseId()));
            if (base.isPresent() && base.get().ground()) {
                return new Vec3(base.get().x() + slot.dx() + 0.5, base.get().y() + 1 + BaseLayout.RELAY_HEIGHT, base.get().z() + slot.dz() + 0.5);
            }
        }
        int y = planet.getHeight(Heightmap.Types.WORLD_SURFACE, flight.siteX(), flight.siteZ());
        return new Vec3(flight.siteX() + slot.dx() + 0.5, y + 1 + BaseLayout.RELAY_HEIGHT, flight.siteZ() + slot.dz() + 0.5);
    }

    /** 落点挑不到、落不下：等一阵再试，试够了就放弃。 */
    private static void retry(MinecraftServer server, DarkFogFlightData data, Flight flight, long now) {
        if (flight.attempts() + 1 >= FlightPlan.MAX_LANDING_ATTEMPTS) {
            data.remove(flight.id());
            mapDirty = true;
            return;
        }
        flight.retryArrival(now, FlightPlan.LANDING_RETRY_TICKS);
        data.setDirty();
    }

    private static void completeRelay(MinecraftServer server, DarkFogFlightData data, Flight flight, long now) {
        if (!flight.siteChosen() && !chooseSite(server, data, flight, true)) {
            retry(server, data, flight, now);
            return;
        }
        ResourceKey<Level> dimension = dimensionKey(flight.dimension());
        if (flight.baseId() >= 0) {
            if (DarkFogBases.restoreRelay(server, dimension, flight.baseId())) {
                finish(server, data, flight, dimension, "message.universe.dark_fog.relay_restored");
                return;
            }
            // 那处基地已经补上、或者没了：在这颗星球上另找地方落
            if (!chooseSite(server, data, flight, false)) {
                retry(server, data, flight, now);
                return;
            }
            dimension = dimensionKey(flight.dimension());
        }
        if (DarkFogBases.landRelay(server, dimension, flight.siteX(), flight.siteZ(), flight.level()).isPresent()) {
            finish(server, data, flight, dimension, "message.universe.dark_fog.relay_landed");
        } else {
            retry(server, data, flight, now);
        }
    }

    private static void finish(MinecraftServer server, DarkFogFlightData data, Flight flight, ResourceKey<Level> dimension, String message) {
        data.remove(flight.id());
        mapDirty = true;
        ServerLevel planet = server.getLevel(dimension);
        if (planet != null) {
            Component text = Component.translatable(message, flight.siteX(), flight.siteZ()).withStyle(ChatFormatting.RED);
            planet.players().forEach(player -> player.sendSystemMessage(text));
        }
    }

    private static void arriveSeed(MinecraftServer server, DarkFogFlightData data, Flight flight, long now, @Nullable Live live) {
        if (DarkFogHives.hasHive(server, flight.target())) {
            retarget(server, data, flight, now);
            return;
        }
        Galaxy galaxy = Planets.galaxy(server);
        if (ANNOUNCED.add(flight.id())) {
            galaxy.system(flight.system()).ifPresent(origin -> noticeSystem(server, flight.target(),
                    Component.translatable("message.universe.dark_fog.seed_arriving", origin.name().zh(), origin.name().en())
                            .withStyle(ChatFormatting.RED)));
        }
        if (live != null) {
            return;
        }
        ServerLevel space = server.getLevel(SpaceDimensions.key(flight.target()));
        if (space == null) {
            return;
        }
        // 从来的那个方向进入恒星系，往恒星飞
        Vec3 outward = galacticDirection(galaxy, flight.target(), flight.system());
        Vec3 entry = outward.scale(SEED_ENTRY_DISTANCE).add(0, SEED_ENTRY_HEIGHT, 0);
        Vec3 inward = entry.scale(-1).normalize();
        Vec3 at = entry.add(inward.scale(SEED_ARRIVE_SPEED * Math.max(0, now - flight.arriveStart())));
        if (!watched(space, at, SPACE_WATCH, false)) {
            return;
        }
        DarkFogCraft craft = DarkFogCraft.line(space, true, flight.id(), flight.level(), flight.health(), entry, inward, SEED_ARRIVE_SPEED,
                flight.arriveStart(), flight.end());
        space.addFreshEntity(craft);
        LIVE.put(flight.id(), new Live(craft, Flight.Phase.ARRIVE));
    }

    /** 种子的目的地已经有巢穴了：改去离那里最近的另一个；没有就散了。 */
    private static void retarget(MinecraftServer server, DarkFogFlightData data, Flight flight, long now) {
        discard(flight.id());
        Galaxy galaxy = Planets.galaxy(server);
        OptionalInt next = FlightPlan.seedTarget(galaxy.systems(), flight.target(), system -> system == flight.system()
                || taken(server, system, flight.id()));
        if (next.isEmpty() || flight.attempts() >= FlightPlan.MAX_LANDING_ATTEMPTS) {
            data.remove(flight.id());
        } else {
            double distance = galaxy.systems().get(flight.target()).distanceTo(galaxy.systems().get(next.getAsInt()));
            flight.retarget(next.getAsInt(), now, FlightPlan.seedCruiseTicks(distance));
            ANNOUNCED.remove(flight.id());
            data.setDirty();
        }
        mapDirty = true;
    }

    private static void completeSeed(MinecraftServer server, DarkFogFlightData data, Flight flight, long now) {
        if (DarkFogHives.hasHive(server, flight.target())) {
            retarget(server, data, flight, now);
            return;
        }
        data.remove(flight.id());
        ANNOUNCED.remove(flight.id());
        mapDirty = true;
        DarkFogHives.found(server, flight.target(), flight.level());
    }

    // ---- 实体的回调

    /** 飞着的中继站、种子挨了打（伤害已经扣过）：中继站挨打，巢穴的威胁涨。 */
    static void onCraftHurt(ServerLevel level, DarkFogCraft craft, float damage) {
        MinecraftServer server = level.getServer();
        DarkFogFlightData.get(server).flight(craft.flightId()).ifPresent(flight -> {
            flight.setHealth(craft.getHealth() / craft.getMaxHealth());
            DarkFogFlightData.get(server).setDirty();
            if (flight.kind() == Flight.Kind.RELAY && damage > 0) {
                DarkFogHives.onRelayHurt(server, flight.system(), damage);
            }
        });
    }

    /** 被打掉了：这次飞行没了，掉东西。 */
    static void onCraftDestroyed(ServerLevel level, DarkFogCraft craft, DamageSource source) {
        MinecraftServer server = level.getServer();
        DarkFogFlightData data = DarkFogFlightData.get(server);
        LIVE.remove(craft.flightId());
        ANNOUNCED.remove(craft.flightId());
        if (data.remove(craft.flightId())) {
            mapDirty = true;
        }
        double weight = craft.seed() ? SEED_LOOT : DarkFogLoot.weight(BuildingKind.RELAY);
        if (SpaceDimensions.isSpace(level.dimension())) {
            salvage(level, craft, source, weight);
        } else {
            DarkFogBases.dropLoot(level, craft.position(), craft.darkFogLevel(), weight);
        }
        notice(level, craft.position(), Component.translatable(craft.seed() ? "message.universe.dark_fog.seed_shot_down"
                : "message.universe.dark_fog.relay_shot_down").withStyle(ChatFormatting.GREEN), craft.seed() ? NOTICE_RANGE * 3 : NOTICE_RANGE);
    }

    /** 太空里掉的东西：装进打掉它的人的飞船，装不下就飘在原地。 */
    private static void salvage(ServerLevel level, DarkFogCraft craft, DamageSource source, double weight) {
        ServerPlayer killer = Pirates.killer(source);
        ShipEntity ship = killer != null && killer.getRootVehicle() instanceof ShipEntity s ? s : null;
        Rng rng = Rng.of(level.getRandom().nextLong());
        for (DarkFogLoot.Drop drop : DarkFogLoot.roll(craft.darkFogLevel(), weight, rng)) {
            Identifier id = Identifier.tryParse(drop.item());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.get(id).ifPresent(item -> {
                ItemStack stack = new ItemStack(item, drop.count());
                if (ship != null) {
                    ShipCargo.insert(ship, level.registryAccess(), stack);
                }
                if (!stack.isEmpty()) {
                    ItemEntity entity = new ItemEntity(level, craft.getX(), craft.getY(0.5), craft.getZ(), stack);
                    entity.setNoGravity(true);
                    entity.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.1, (level.getRandom().nextDouble() - 0.5) * 0.1,
                            (level.getRandom().nextDouble() - 0.5) * 0.1);
                    entity.lifespan = DarkFogLoot.LIFESPAN_TICKS * 3;
                    level.addFreshEntity(entity);
                }
            });
        }
    }

    // ---- 提示和星图

    private static void notice(ServerLevel level, Vec3 at, Component message, double range) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) <= range * range) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** 发给在这个恒星系里的人（太空里、星球上都算）。 */
    private static void noticeSystem(MinecraftServer server, int system, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dimension = player.level().dimension();
            OptionalInt space = SpaceDimensions.system(dimension);
            int here = space.isPresent() ? space.getAsInt() : Planets.bodyId(dimension).map(BodyId::system).orElse(-1);
            if (here == system) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** 星图上的黑雾：巢穴的概况、往外飞的中继站数、路上的种子。 */
    public static DarkFogMapPayload map(MinecraftServer server) {
        long now = now(server);
        List<Flight> flights = DarkFogFlightData.get(server).flights();
        List<DarkFogMapPayload.Hive> hives = new ArrayList<>();
        for (DarkFogHives.Summary summary : DarkFogHives.summaries(server)) {
            int relays = (int) flights.stream().filter(f -> f.kind() == Flight.Kind.RELAY && f.system() == summary.system()).count();
            hives.add(new DarkFogMapPayload.Hive(summary.system(), summary.alive(), summary.level(), (float) summary.threat(), relays));
        }
        List<DarkFogMapPayload.Seed> seeds = new ArrayList<>();
        for (Flight flight : flights) {
            if (flight.kind() == Flight.Kind.SEED) {
                seeds.add(new DarkFogMapPayload.Seed(flight.id(), flight.system(), flight.target(), (int) Math.max(0, now - flight.launched()),
                        (int) Math.max(1, flight.end() - flight.launched())));
            }
        }
        return new DarkFogMapPayload(hives, seeds);
    }

    /** 星图的数据变了就发给所有人。 */
    static void sync(MinecraftServer server, boolean force) {
        mapDirty = false;
        DarkFogMapPayload payload = map(server);
        int signature = payload.signature();
        if (!force && signature == lastSignature) {
            return;
        }
        lastSignature = signature;
        PacketDistributor.sendToAllPlayers(payload);
    }

    // ---- 调试命令和游戏内测试用

    /** 让这次飞行马上跳到 {@code phase}；{@link Flight.Phase#DONE} 会在这一刻就结算掉。 */
    public static boolean skip(MinecraftServer server, int id, Flight.Phase phase) {
        DarkFogFlightData data = DarkFogFlightData.get(server);
        Optional<Flight> flight = data.flight(id);
        if (flight.isEmpty()) {
            return false;
        }
        long now = now(server);
        flight.get().skipTo(phase, now);
        data.setDirty();
        tickFlight(server, data, flight.get(), now);
        mapDirty = true;
        return true;
    }

    /** 调试命令用：中继站改落到这颗星球的 ({@code x}, {@code z})，马上开始降落。不是中继站、星球不在它的恒星系里时是 false。 */
    public static boolean arriveAt(MinecraftServer server, int id, ResourceKey<Level> dimension, int x, int z) {
        Optional<Flight> flight = DarkFogFlightData.get(server).flight(id);
        if (flight.isEmpty() || flight.get().kind() != Flight.Kind.RELAY || !landable(server, flight.get().system()).contains(dimension)) {
            return false;
        }
        discard(id);
        flight.get().land(dimension.identifier().toString(), x, z, -1);
        return skip(server, id, Flight.Phase.ARRIVE);
    }

    /** 收回一次飞行（不掉东西）。 */
    public static boolean remove(MinecraftServer server, int id) {
        discard(id);
        ANNOUNCED.remove(id);
        mapDirty = true;
        return DarkFogFlightData.get(server).remove(id);
    }

    /** 收回全部飞行。 */
    public static int clear(MinecraftServer server) {
        List<Integer> ids = DarkFogFlightData.get(server).flights().stream().map(Flight::id).toList();
        ids.forEach(id -> remove(server, id));
        return ids.size();
    }

    /** 这次飞行放出来的实体（没有是空）。 */
    public static Optional<DarkFogCraft> craft(int id) {
        Live live = LIVE.get(id);
        return live == null || live.craft().isRemoved() ? Optional.empty() : Optional.of(live.craft());
    }

    /** 游戏内测试用：给这次飞行放一个实体在 {@code at}，沿 x 轴慢慢飞（起飞那一段）。 */
    public static DarkFogCraft spawnForTest(ServerLevel level, Flight flight, Vec3 at) {
        DarkFogCraft craft = DarkFogCraft.line(level, flight.kind() == Flight.Kind.SEED, flight.id(), flight.level(), flight.health(), at,
                new Vec3(1, 0, 0), 0.01, flight.launched(), flight.departEnd());
        level.addFreshEntity(craft);
        LIVE.put(flight.id(), new Live(craft, flight.phase(now(level.getServer()))));
        return craft;
    }
}
