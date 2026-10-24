package io.github.sweetcornna.universe.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Location;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.SpaceRoute;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.Trip;
import io.github.sweetcornna.universe.core.logistics.Vehicle;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.network.LogisticsFlightsPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 物流网络的服务端入口（ADR-0015）：每刻推进全服的账本（按主世界的游戏时间），每秒存一次盘，行程变了就把玩家所在
 * 维度里在飞的载具发给他。恒星系的太空维度里还发这个恒星系里往来的运输船，两头换算成太空坐标（ADR-0030）。
 */
public final class Logistics {
    /** 给每个玩家发过的版本：账本的版本号和维度，有一个变了就重发。 */
    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

    private record Sent(long revision, ResourceKey<Level> dimension) {
    }

    private Logistics() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("logistics", () -> tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> SENT.clear());
    }

    public static LogisticsNetwork network(MinecraftServer server) {
        return LogisticsData.get(server).network();
    }

    private static void tick(MinecraftServer server) {
        LogisticsData data = LogisticsData.get(server);
        LogisticsNetwork network = data.network();
        if (network.stations().isEmpty() && network.trips().isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        ResearchState research = ResearchData.get(server).state();
        network.setUpgrades(Upgrades.carrierSpeed(research.level(Upgrades.CARRIER_ENGINE)), research.level(Upgrades.CARRIER_CAPACITY));
        network.tick(now);
        if (now % LogisticsNetwork.DISPATCH_INTERVAL == 0) {
            data.setDirty();
            sync(server, network);
        }
    }

    /** 行程变了、或者玩家换了维度，就重发一份。 */
    private static void sync(MinecraftServer server, LogisticsNetwork network) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Sent sent = SENT.get(player);
            ResourceKey<Level> dimension = player.level().dimension();
            if (sent != null && sent.revision == network.revision() && sent.dimension == dimension) {
                continue;
            }
            SENT.put(player, new Sent(network.revision(), dimension));
            PacketDistributor.sendToPlayer(player, new LogisticsFlightsPayload(flights(server, network, dimension)));
        }
    }

    private static List<LogisticsFlightsPayload.Flight> flights(MinecraftServer server, LogisticsNetwork network, ResourceKey<Level> key) {
        String dimension = key.identifier().toString();
        OptionalInt space = SpaceDimensions.system(key);
        SpaceView view = space.isPresent() ? SpaceView.of(Planets.galaxy(server), space.getAsInt()) : null;
        List<LogisticsFlightsPayload.Flight> flights = new ArrayList<>();
        for (Trip trip : network.trips()) {
            Station home = network.get(trip.home());
            Station other = network.get(trip.other());
            if (home == null || other == null) {
                continue;
            }
            boolean vessel = trip.vehicle() == Vehicle.VESSEL;
            int homeKind = view != null && vessel ? view.kind(home, dimension) : home.dimension().equals(dimension) ? SpaceRoute.PAD : SpaceRoute.AWAY;
            int otherKind = view != null && vessel ? view.kind(other, dimension) : other.dimension().equals(dimension) ? SpaceRoute.PAD : SpaceRoute.AWAY;
            if (homeKind == SpaceRoute.AWAY && otherKind == SpaceRoute.AWAY || homeKind == SpaceRoute.EDGE && otherKind == SpaceRoute.EDGE) {
                continue;
            }
            if (view != null && vessel) {
                // 太空里的运输船：两头都换算成太空坐标，航线绕开恒星
                double[] homeAnchor = view.anchor(home, homeKind);
                double[] otherAnchor = view.anchor(other, otherKind);
                double[] from = view.end(home, homeKind, otherAnchor);
                double[] to = view.end(other, otherKind, homeAnchor);
                if (from == null || to == null) {
                    continue;
                }
                double[] via = SpaceRoute.control(from, to, view.starRadius);
                flights.add(new LogisticsFlightsPayload.Flight(true, true, homeKind, otherKind, from[0], from[1], from[2], to[0], to[1], to[2],
                        via[0], via[1], via[2], trip.departAt(), trip.arriveAt(), trip.returnAt()));
                continue;
            }
            double[] from = pad(home);
            double[] to = pad(other);
            flights.add(new LogisticsFlightsPayload.Flight(vessel, false, homeKind, otherKind, from[0], from[1], from[2], to[0], to[1], to[2],
                    0, 0, 0, trip.departAt(), trip.arriveAt(), trip.returnAt()));
        }
        return flights;
    }

    /** 一个恒星系的太空维度：天体的位置、恒星系的边，把站换算成运输船航线的端点。 */
    private static final class SpaceView {
        private final StarSystem system;
        private final List<SpaceLayout.Body> bodies;
        private final double edge;
        private final double starRadius;

        private SpaceView(StarSystem system) {
            this.system = system;
            this.bodies = SpaceLayout.of(system);
            this.edge = SpaceRoute.edgeRadius(this.bodies);
            this.starRadius = SpaceLayout.starRadius(system);
        }

        static @Nullable SpaceView of(Galaxy galaxy, int system) {
            return galaxy.system(system).map(SpaceView::new).orElse(null);
        }

        /** 站在这个太空维度里是哪种端。 */
        int kind(Station station, String dimension) {
            if (station.dimension().equals(dimension)) {
                return SpaceRoute.PAD;
            }
            return station.location().system() == this.system.index() ? SpaceRoute.SPHERE : SpaceRoute.EDGE;
        }

        /** 端点的大致位置，用来定另一头从大气球哪里钻出来：停机坪、星球中心、恒星系边上的跃迁点。 */
        double @Nullable [] anchor(Station station, int kind) {
            return switch (kind) {
                case SpaceRoute.PAD -> pad(station);
                case SpaceRoute.SPHERE -> this.body(station).map(body -> new double[] {body.x(), body.y(), body.z()}).orElse(null);
                case SpaceRoute.EDGE -> SpaceRoute.edgePoint(this.system, station.location().starX(), station.location().starZ(), this.edge);
                default -> null;
            };
        }

        /** 航线这一头的点：星球那头在大气球外朝着另一头 {@code target}。 */
        double @Nullable [] end(Station station, int kind, double @Nullable [] target) {
            if (kind != SpaceRoute.SPHERE) {
                return this.anchor(station, kind);
            }
            Optional<SpaceLayout.Body> body = this.body(station);
            return body.isEmpty() || target == null ? null : SpaceRoute.spherePoint(body.get(), target);
        }

        private Optional<SpaceLayout.Body> body(Station station) {
            return BodyId.parse(station.location().body()).flatMap(id -> SpaceLayout.find(this.bodies, id));
        }
    }

    /** 载具起降的位置：物流站顶上的停机坪，采集器的正上方。 */
    public static double[] pad(Station station) {
        double height = station.kind() == StationKind.COLLECTOR ? 1.2 : 2.1;
        return new double[] {station.x() + 0.5, station.y() + height, station.z() + 0.5};
    }

    /** 新建一座站的账本记录。 */
    public static Station create(ServerLevel level, BlockPos pos, StationKind kind, UUID id) {
        return new Station(id, kind, level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(), locate(level, pos));
    }

    /**
     * 物流站在星团里的位置：星球维度按它的天体（卫星按它的行星算轨道），太空维度按离得最近的天体，其他维度
     * （下界、末地、别的模组的维度）当作在母星上。
     */
    public static Location locate(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        Galaxy galaxy = Planets.galaxy(server);
        OptionalInt space = SpaceDimensions.system(level.dimension());
        if (space.isPresent()) {
            StarSystem system = galaxy.systems().get(space.getAsInt());
            Optional<SpaceLayout.Body> nearest = nearestBody(system, pos.getX(), pos.getY(), pos.getZ());
            if (nearest.isPresent()) {
                return location(galaxy, nearest.get().id());
            }
            return new Location(system.index(), 0, system.x(), system.y(), system.z(), "s" + system.index() + "/space");
        }
        BodyId body = Planets.bodyId(level.dimension()).orElse(GalaxyGenerator.HOME_PLANET_ID);
        return location(galaxy, body);
    }

    private static Location location(Galaxy galaxy, BodyId id) {
        StarSystem system = galaxy.systems().get(id.system());
        Optional<Planet> parent = galaxy.body(id.parent());
        double au = parent.map(planet -> planet.orbit().radius()).orElse(1.0);
        return new Location(system.index(), au, system.x(), system.y(), system.z(), id.path());
    }

    /** 太空维度里离一点最近的天体（按到大气球表面的距离）。 */
    public static Optional<SpaceLayout.Body> nearestBody(StarSystem system, double x, double y, double z) {
        SpaceLayout.Body best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SpaceLayout.Body body : SpaceLayout.of(system)) {
            double distance = body.distanceTo(x, y, z) - body.radius();
            if (distance < bestDistance) {
                bestDistance = distance;
                best = body;
            }
        }
        return Optional.ofNullable(best);
    }
}
