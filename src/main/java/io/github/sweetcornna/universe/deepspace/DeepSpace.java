package io.github.sweetcornna.universe.deepspace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.core.darkfog.HiveSites;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Kind;
import io.github.sweetcornna.universe.core.deepspace.DeepSpaceSites.Site;
import io.github.sweetcornna.universe.core.deepspace.Integrity;
import io.github.sweetcornna.universe.core.deepspace.Layout;
import io.github.sweetcornna.universe.core.deepspace.OutpostLayout;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.darkfog.hive.DarkFogHives;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.gravity.GravityCoils;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 深空（ADR-0034 §四）：每个星系太空里按种子定下的碎片带、废弃前哨站、太空残骸。
 *
 * <p>位置一开始就定了（{@link DeepSpaceSites}），方块等玩家飞到 {@link #BUILD_RANGE} 格内才搭：先挂加载票让那几个区块在后台生成，
 * 都好了再搭（{@link Structures#ready}）。搭过的记在 {@link DeepSpaceData}，不再搭第二次。每秒把玩家附近的兴趣点发给客户端在 HUD 上标出来。
 */
public final class DeepSpace {
    /** 玩家离兴趣点的外沿这么近时把它搭起来（格）。 */
    static final double BUILD_RANGE = 450;
    private static final int SYNC_INTERVAL = 20;
    private static final int DEBRIS_INTERVAL = 10;
    /** 一次最多标这么多个（离得近的优先）。 */
    private static final int MAX_MARKERS = 24;
    private static final Map<Integer, List<Site>> SITES = new HashMap<>();
    /** 开发命令放的前哨站的中心（前哨站没有 {@link Hulks.Wreck} 那样的登记）。 */
    private static final Map<String, Vec3> OUTPOST_CENTERS = new HashMap<>();

    private DeepSpace() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        Hulks.registerEvents(gameBus);
        GravityCoils.registerEvents(gameBus);
        gameBus.addListener(ServerTickEvent.Post.class, event -> tick(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            SITES.clear();
            OUTPOST_CENTERS.clear();
            DebrisFields.clear();
        });
        gameBus.addListener(RegisterCommandsEvent.class, event -> DeepSpaceCommand.register(event.getDispatcher()));
    }

    /** 星系 {@code system} 的深空兴趣点（按星系缓存）。 */
    public static List<Site> sites(MinecraftServer server, int system) {
        return SITES.computeIfAbsent(system, index -> Planets.galaxy(server).system(index).map(star -> compute(server, star)).orElse(List.of()));
    }

    private static List<Site> compute(MinecraftServer server, StarSystem system) {
        double[] station = StationLayout.site(system).map(StationLayout.Site::center).orElse(null);
        List<double[]> avoid = new ArrayList<>();
        HiveSites.Site hive = DarkFogHives.site(server, system);
        avoid.add(new double[] {hive.x(), hive.y(), hive.z()});
        BlackHoles.in(system).ifPresent(hole -> avoid.add(new double[] {hole.x(), hole.y(), hole.z()}));
        return DeepSpaceSites.of(system.seed(), ShipTransit.layout(server, system.index()), SpaceLayout.starRadius(system), station, avoid);
    }

    // ---- 每刻

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % DEBRIS_INTERVAL == 0) {
            DebrisFields.tick(server);
        }
        if (now % SYNC_INTERVAL != 0) {
            return;
        }
        DeepSpaceData data = DeepSpaceData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            OptionalInt system = SpaceDimensions.system(level.dimension());
            if (system.isEmpty() || level.players().isEmpty()) {
                continue;
            }
            List<Site> sites = sites(server, system.getAsInt());
            for (Site site : sites) {
                if (site.kind() == Kind.DEBRIS) {
                    continue;
                }
                String key = DeepSpaceData.key(system.getAsInt(), site.index());
                if (data.find(key).map(DeepSpaceData.State::built).orElse(false)
                        || level.getNearestPlayer(site.x(), site.y(), site.z(), BUILD_RANGE + site.kind().extent(), false) == null) {
                    continue;
                }
                if (Structures.ready(level, box(site))) {
                    build(level, site, data.state(key, site.kind()));
                }
            }
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, new DeepSpacePayloads.Sites(markers(level, player.position(), system.getAsInt(), sites, data)));
            }
        }
    }

    /** 一处残骸、前哨站搭起来以后占的方块范围（和 {@link #build} 的摆法一样）。 */
    static BoundingBox box(Site site) {
        Layout layout = site.kind() == Kind.HULK ? Hulks.layout(site.seed(), 0) : OutpostLayout.generate(site.seed());
        return Placement.centered(layout, new Vec3(site.x(), site.y(), site.z()), rotation(site)).box(layout);
    }

    /** 朝向按种子定。 */
    private static int rotation(Site site) {
        return (int) (site.seed() >>> 62);
    }

    /** 搭一处按种子定下的残骸、前哨站。 */
    static void build(ServerLevel level, Site site, DeepSpaceData.State state) {
        Vec3 center = new Vec3(site.x(), site.y(), site.z());
        int rotation = rotation(site);
        if (site.kind() == Kind.HULK) {
            Layout layout = Hulks.layout(site.seed(), 0);
            Hulks.place(level, Placement.centered(layout, center, rotation), layout, site.seed(), 0, state);
        } else if (site.kind() == Kind.OUTPOST) {
            placeOutpost(level, center, rotation, site.seed(), state);
        }
    }

    /** 搭一座前哨站，记进存档。 */
    static Placement placeOutpost(ServerLevel level, Vec3 center, int rotation, long seed, DeepSpaceData.State state) {
        Layout layout = OutpostLayout.generate(seed);
        Placement placement = Placement.centered(layout, center, rotation);
        Structures.place(level, placement, layout, true);
        state.dimension = level.dimension().identifier().toString();
        state.origin = placement.origin().asLong();
        state.rotation = placement.rotation();
        state.seed = seed;
        state.built = true;
        DeepSpaceData.get(level.getServer()).changed();
        OUTPOST_CENTERS.put(state.key, center);
        return placement;
    }

    /** 玩家附近要在 HUD 上标的兴趣点。 */
    static List<DeepSpacePayloads.Marker> markers(ServerLevel level, Vec3 at, int system, List<Site> sites, DeepSpaceData data) {
        List<DeepSpacePayloads.Marker> markers = new ArrayList<>();
        List<String> covered = new ArrayList<>();
        // 搭好了的残骸（星系的、开发命令放的、测试放的）按实际位置标，带上完整度
        for (Hulks.Wreck wreck : Hulks.wrecks(level)) {
            covered.add(wreck.key);
            if (wreck.center.distanceTo(at) <= DeepSpaceSites.HUD_RANGE) {
                Integrity integrity = wreck.integrity;
                markers.add(new DeepSpacePayloads.Marker(Kind.HULK.ordinal(), wreck.center.x, wreck.center.y, wreck.center.z, integrity.value(),
                        integrity.countdown(), integrity.salvaged()));
            }
        }
        for (Site site : sites) {
            String key = DeepSpaceData.key(system, site.index());
            if (covered.contains(key) || site.distanceTo(at.x, at.y, at.z) > DeepSpaceSites.HUD_RANGE) {
                continue;
            }
            DeepSpaceData.State state = data.find(key).orElse(null);
            if (site.kind() == Kind.HULK && state != null && state.integrity.gone()) {
                continue;
            }
            markers.add(new DeepSpacePayloads.Marker(site.kind().ordinal(), site.x(), site.y(), site.z(),
                    site.kind() == Kind.HULK ? Integrity.FULL : -1, -1, false));
        }
        String dimension = level.dimension().identifier().toString();
        for (DeepSpaceData.State state : data.all()) {
            if (state.kind == Kind.OUTPOST && state.built && state.key.startsWith("custom/") && state.dimension.equals(dimension)) {
                Vec3 center = OUTPOST_CENTERS.computeIfAbsent(state.key, k -> outpostCenter(state));
                if (center.distanceTo(at) <= DeepSpaceSites.HUD_RANGE) {
                    markers.add(new DeepSpacePayloads.Marker(Kind.OUTPOST.ordinal(), center.x, center.y, center.z, -1, -1, false));
                }
            }
        }
        markers.sort(Comparator.comparingDouble(m -> at.distanceToSqr(m.x(), m.y(), m.z())));
        return markers.size() > MAX_MARKERS ? List.copyOf(markers.subList(0, MAX_MARKERS)) : markers;
    }

    private static Vec3 outpostCenter(DeepSpaceData.State state) {
        Layout layout = OutpostLayout.generate(state.seed);
        double[] c = layout.center();
        return new Placement(net.minecraft.core.BlockPos.of(state.origin), state.rotation).world(c[0], c[1], c[2]);
    }
}
