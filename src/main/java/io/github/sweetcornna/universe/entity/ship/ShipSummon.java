package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.block.ship.LandingPadBlockEntity;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;
import io.github.sweetcornna.universe.core.ship.ShipAutopilot;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.fleet.FreighterInterior;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.ship.ShipOwners.MainShip;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.fleet.FleetData;
import io.github.sweetcornna.universe.fleet.Freighters;
import io.github.sweetcornna.universe.network.ShipPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.sound.ShipSounds;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.DimensionTemplate;
import io.github.sweetcornna.universe.world.RuntimeDimensions;
import io.github.sweetcornna.universe.world.freighter.FreighterDimensions;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 召唤和自动驾驶（ADR-0024），都用召唤键（默认 N）。
 *
 * <p>在星球上或空间站里按：主力船飞到身边。{@link #PAD_RADIUS} 格内有自己的着陆台就停在台上，否则从 8–16 格外一块空地的上方
 * 降下来；在空间站里停到停机坪上。船在别的恒星系、停成了方块、有人在开，或者人在开阔的太空里，就叫不来。
 * 船所在的区块没加载时先加载，等实体读进来再搬过来。
 *
 * <p>在太空里开着船时按：列出本系的星球、卫星和空间站，选一个就开自动驾驶（{@link ShipAutopilot}）。
 */
public final class ShipSummon {
    /** 召唤的冷却（刻）。 */
    public static final int COOLDOWN = 200;
    /** 多远以内的着陆台算数。 */
    public static final double PAD_RADIUS = 64;
    private static final int MIN_SPOT = 8;
    private static final int MAX_SPOT = 16;
    /** 船从落点上方多高降下来。 */
    private static final int DROP_HEIGHT = 20;
    /** 等区块里的实体读进来最多等几刻。 */
    private static final int LOAD_TIMEOUT = 100;
    /** 自动驾驶停在星球大气层外多远（格）。 */
    public static final double PLANET_STOP = 300;
    /** 自动驾驶停在空间站机库口外多远（格）。 */
    private static final int STATION_APPROACH = 40;
    /** 自动驾驶停在黑洞视界外多远：进不进去由驾驶员自己决定。 */
    private static final double HOLE_STOP = 400;
    /** 去空间站先到引导灯外头这么远的地方，对准机库再进去。 */
    private static final int STATION_LEAD = 24;
    /** 绕开空间站时离它的包围盒至少这么远（格）。 */
    private static final double STATION_MARGIN = 12;
    private static final int MAX_DESTINATIONS = 64;
    /** 去货船先到机库口外头这么远的地方。 */
    private static final int FREIGHTER_LEAD = 30;
    /** 机库口外那一点往里多少格是机库中间（机库口外 12 格，机库 12 格深）。 */
    private static final int FREIGHTER_INSIDE = 18;

    private record Pending(UUID player, MainShip main, long deadline) {
    }

    /**
     * 一个目的地和它的名字。
     *
     * @param bounds 要绕开的包围盒（货船），没有时为 null
     */
    private record Destination(ShipAutopilot.Target target, Component name, ShipPayloads.Destination shown, int @Nullable [] bounds) {
    }

    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    /** 每名玩家最近一次打开的目的地列表：选的时候按下标找。 */
    private static final Map<UUID, List<Destination>> DESTINATIONS = new HashMap<>();

    private ShipSummon() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> {
            if (!PENDING.isEmpty()) {
                ServerPerf.time("ship_summon", () -> process(event.getServer()));
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            COOLDOWNS.clear();
            PENDING.clear();
            DESTINATIONS.clear();
            LandingPadBlockEntity.clear();
        });
    }

    /** 客户端按了召唤键。 */
    public static void handleSummon(ShipPayloads.Summon payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        if (player.getVehicle() instanceof ShipEntity ship) {
            if (ship.pilot() == player && SpaceDimensions.isSpace(player.level().dimension()) && !Stations.inside(player.level(), ship.position())) {
                openDestinations(player, ship);
            } else {
                player.sendOverlayMessage(Component.translatable("message.universe.summon.aboard"));
            }
            return;
        }
        summon(player);
    }

    /** 客户端选了自动驾驶的目的地。 */
    public static void handleNavigate(ShipPayloads.Navigate payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player) {
            return;
        }
        List<Destination> list = DESTINATIONS.get(player.getUUID());
        if (list == null || payload.index() < 0 || payload.index() >= list.size()) {
            return;
        }
        Destination destination = list.get(payload.index());
        if (destination.shown().kind() == ShipPayloads.Destination.SUMMON_FREIGHTER) {
            player.sendOverlayMessage(Freighters.summon(player, ship));
            return;
        }
        ship.engageAutopilot(route(player.level(), ship, destination), destination.name());
    }

    /**
     * 自动驾驶的路线：空间站挡在路上就从上面（或下面）绕过去。去空间站时先飞到机库正前方、引导灯外头，
     * 再顺着两排灯中间飞到口上；已经在两排灯中间就直接过去。
     */
    private static List<ShipAutopilot.Target> route(ServerLevel level, ShipEntity ship, Destination destination) {
        Optional<StationLayout.Site> found = Stations.site(level.getServer(), level.dimension());
        ShipAutopilot.Target to = destination.target();
        int kind = destination.shown().kind();
        if ((kind == ShipPayloads.Destination.FREIGHTER || kind == ShipPayloads.Destination.SIGNAL) && destination.bounds() != null) {
            return freighterRoute(ship, to, destination.bounds(), found.map(StationLayout.Site::bounds).orElse(null));
        }
        if (found.isEmpty()) {
            return List.of(to);
        }
        StationLayout.Site site = found.get();
        if (destination.shown().kind() != ShipPayloads.Destination.STATION) {
            return ShipAutopilot.around(ship.getX(), ship.getY(), ship.getZ(), to, site.bounds(), STATION_MARGIN);
        }
        int[] local = site.toLocal(ship.getBlockX(), ship.getBlockY(), ship.getBlockZ());
        boolean lined = local[0] > StationLayout.RADIUS && Math.abs(local[2]) < StationLayout.OPENING_HALF_WIDTH + 2
                && local[1] > StationLayout.MIN_Y && local[1] < StationLayout.CEILING;
        List<ShipAutopilot.Target> route = new ArrayList<>();
        if (!lined) {
            int[] lead = site.toWorld(StationLayout.RADIUS + StationLayout.APPROACH_LENGTH + STATION_LEAD, 2, 0);
            route.addAll(ShipAutopilot.around(ship.getX(), ship.getY(), ship.getZ(),
                    ShipAutopilot.Target.via(lead[0] + 0.5, lead[1], lead[2] + 0.5), site.bounds(), STATION_MARGIN));
        }
        route.add(to);
        return route;
    }

    /**
     * 去货船：绕开空间站和货船自己，先到机库口正前方 {@link #FREIGHTER_LEAD} 格，在那里对准停稳，再顺着机库的中线慢慢飞进去
     * （飞进机库就到了船舱，见 {@code ShipTransit}）。已经在机库口前面、对着中线时直接飞进去。
     *
     * @param mouth 机库口外面、中线上的那一点（从船舱飞出来也停在这里）
     */
    private static List<ShipAutopilot.Target> freighterRoute(ShipEntity ship, ShipAutopilot.Target mouth, int[] hull, int @Nullable [] station) {
        List<ShipAutopilot.Target> route = new ArrayList<>();
        ShipAutopilot.Target inside = ShipAutopilot.Target.dock(mouth.x() + FREIGHTER_INSIDE, mouth.y(), mouth.z());
        double ahead = mouth.x() - ship.getX();
        if (ahead > 0 && ahead < FREIGHTER_LEAD + 10 && Math.abs(ship.getZ() - mouth.z()) < 2 && Math.abs(ship.getY() - mouth.y()) < 2) {
            route.add(inside);
            return route;
        }
        ShipAutopilot.Target lead = ShipAutopilot.Target.via(mouth.x() - FREIGHTER_LEAD, mouth.y(), mouth.z());
        List<ShipAutopilot.Target> first = station == null ? List.of(lead)
                : ShipAutopilot.around(ship.getX(), ship.getY(), ship.getZ(), lead, station, STATION_MARGIN);
        for (ShipAutopilot.Target leg : first) {
            if (leg == lead) {
                route.addAll(ShipAutopilot.around(ship.getX(), ship.getY(), ship.getZ(), lead, hull, STATION_MARGIN));
            } else {
                route.add(leg);
            }
        }
        route.add(ShipAutopilot.Target.dock(lead.x(), lead.y(), lead.z()));
        route.add(inside);
        return route;
    }

    // ---- 召唤

    private static void summon(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        long now = server.getTickCount();
        Long ready = COOLDOWNS.get(player.getUUID());
        if (ready != null && now < ready) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.cooldown", (ready - now + 19) / 20));
            return;
        }
        ServerLevel level = player.level();
        if (SpaceDimensions.isSpace(level.dimension()) && !Stations.inside(level, player.position())) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.in_space"));
            return;
        }
        Optional<MainShip> found = ShipOwners.get(server).main(player.getUUID());
        if (found.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.none"));
            return;
        }
        MainShip main = found.get();
        if (main.parked()) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.parked"));
            return;
        }
        ResourceKey<Level> where = ResourceKey.create(Registries.DIMENSION, main.dimension());
        OptionalInt here = system(server, level.dimension());
        OptionalInt there = system(server, where);
        if (here.isEmpty() || there.isEmpty() || here.getAsInt() != there.getAsInt()) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.other_system"));
            return;
        }
        ServerLevel shipLevel = level(server, where);
        if (shipLevel == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.lost"));
            return;
        }
        COOLDOWNS.put(player.getUUID(), now + COOLDOWN);
        if (shipLevel.getEntity(main.ship()) instanceof ShipEntity ship) {
            deliver(player, ship);
            return;
        }
        // 船所在的区块没加载：加载一会儿，等实体读进来
        shipLevel.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(BlockPos.containing(main.x(), main.y(), main.z())), 1);
        PENDING.add(new Pending(player.getUUID(), main, now + LOAD_TIMEOUT));
        player.sendOverlayMessage(Component.translatable("message.universe.summon.calling"));
    }

    private static void process(MinecraftServer server) {
        long now = server.getTickCount();
        for (Pending pending : List.copyOf(PENDING)) {
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            ServerLevel shipLevel = level(server, ResourceKey.create(Registries.DIMENSION, pending.main().dimension()));
            Entity entity = shipLevel == null ? null : shipLevel.getEntity(pending.main().ship());
            if (player == null) {
                PENDING.remove(pending);
            } else if (entity instanceof ShipEntity ship) {
                PENDING.remove(pending);
                deliver(player, ship);
            } else if (now > pending.deadline()) {
                PENDING.remove(pending);
                player.sendOverlayMessage(Component.translatable("message.universe.summon.lost"));
            }
        }
    }

    /** 把船搬到玩家身边的落点上方，让它自己降下来。 */
    private static void deliver(ServerPlayer player, ShipEntity ship) {
        if (!ship.getPassengers().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.occupied"));
            return;
        }
        ServerLevel level = player.level();
        Vec3 spot = landingSpot(level, player, ship);
        if (spot == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.no_room"));
            return;
        }
        float yaw = ship.getYRot();
        ShipEntity arrived;
        if (ship.level() == level) {
            ship.setPos(spot);
            arrived = ship;
        } else {
            arrived = ship.teleport(new TeleportTransition(level, spot, Vec3.ZERO, yaw, 0, TeleportTransition.DO_NOTHING)) instanceof ShipEntity moved
                    ? moved : null;
        }
        if (arrived == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.summon.lost"));
            return;
        }
        arrived.flight().restore(0, 0, 0, yaw);
        arrived.setDeltaMovement(Vec3.ZERO);
        ShipOwners.get(level.getServer()).moved(arrived);
        level.playSound(null, BlockPos.containing(spot), ShipSounds.SUMMON.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        player.sendOverlayMessage(Component.translatable("message.universe.summon.arrived"));
    }

    /** 落点（船的原点格，船底在这个高度）；找不到放得下的地方时为 {@code null}。 */
    private static @Nullable Vec3 landingSpot(ServerLevel level, ServerPlayer player, ShipEntity ship) {
        ShipStructure structure = ship.structure();
        if (structure == null) {
            return null;
        }
        float relative = ship.getYRot() - ship.baseYaw();
        ShipCollider collider = new ShipCollider(level);
        // 空间站里停到停机坪上
        if (Stations.inside(level, player.position())) {
            Optional<StationLayout.Site> site = Stations.site(level.getServer(), level.dimension());
            if (site.isEmpty()) {
                return null;
            }
            double[] pad = site.get().pad();
            for (int lift = 1; lift <= 3; lift++) {
                Vec3 spot = new Vec3(pad[0], pad[1] + lift, pad[2]);
                if (fits(collider, structure, spot, relative)) {
                    return spot;
                }
            }
            return null;
        }
        // 货船的机库里停到两个起降台上
        if (FreighterDimensions.isInterior(level.dimension())) {
            for (int[] pad : FreighterInterior.PADS) {
                for (int lift = 1; lift <= 3; lift++) {
                    Vec3 spot = new Vec3(pad[0] + 0.5, FreighterInterior.ORIGIN_Y + 1 + lift, pad[1] + 0.5);
                    if (fits(collider, structure, spot, relative)) {
                        return spot;
                    }
                }
            }
            return null;
        }
        int top = level.getMaxY() - structure.shape().height() - 1;
        for (BlockPos pad : LandingPadBlockEntity.near(level, player.blockPosition(), PAD_RADIUS)) {
            if (!(level.getBlockEntity(pad) instanceof LandingPadBlockEntity entity) || !entity.usableBy(player.getUUID())) {
                continue;
            }
            ShipEntity there = entity.shipAbove();
            if (there != null && there != ship) {
                continue;
            }
            Vec3 spot = new Vec3(pad.getX() + 0.5, Math.min(top, pad.getY() + 1 + DROP_HEIGHT), pad.getZ() + 0.5);
            if (fits(collider, structure, spot, relative)) {
                return spot;
            }
        }
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = MIN_SPOT + random.nextDouble() * (MAX_SPOT - MIN_SPOT) + structure.shape().radius();
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 spot = new Vec3(x + 0.5, Math.min(top, ground + DROP_HEIGHT), z + 0.5);
            if (spot.y > ground && fits(collider, structure, spot, relative)) {
                return spot;
            }
        }
        return null;
    }

    private static boolean fits(ShipCollider collider, ShipStructure structure, Vec3 spot, float relative) {
        return collider.fits(ShipCollider.boxes(structure, spot.x, spot.y, spot.z, relative));
    }

    /** 维度属于哪个恒星系（星球、卫星和太空都算，货船的船舱算货船停的那个系）。 */
    private static OptionalInt system(MinecraftServer server, ResourceKey<Level> dimension) {
        OptionalInt space = SpaceDimensions.system(dimension);
        if (space.isPresent()) {
            return space;
        }
        OptionalInt freighter = FreighterDimensions.id(dimension);
        if (freighter.isPresent()) {
            return Freighters.system(server, freighter.getAsInt());
        }
        return Planets.bodyId(dimension).map(id -> OptionalInt.of(id.system())).orElse(OptionalInt.empty());
    }

    private static @Nullable ServerLevel level(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            return level;
        }
        OptionalInt space = SpaceDimensions.system(key);
        if (space.isPresent()) {
            return SpaceDimensions.getOrCreate(server, space.getAsInt());
        }
        OptionalInt freighter = FreighterDimensions.id(key);
        if (freighter.isPresent()) {
            return FreighterDimensions.getOrCreate(server, freighter.getAsInt());
        }
        return Planets.bodyId(key).isPresent() ? RuntimeDimensions.getOrCreate(server, key, DimensionTemplate.PLANET) : null;
    }

    // ---- 自动驾驶的目的地

    private static void openDestinations(ServerPlayer player, ShipEntity ship) {
        MinecraftServer server = player.level().getServer();
        int system = SpaceDimensions.system(player.level().dimension()).orElse(-1);
        if (system < 0) {
            return;
        }
        List<Destination> list = new ArrayList<>();
        for (SpaceLayout.Body body : ShipTransit.layout(server, system)) {
            Name name = Discoveries.resolve(server, DiscoveryKey.body(body.id()), body.planet().name());
            int kind = body.planet().moon() ? ShipPayloads.Destination.MOON : ShipPayloads.Destination.PLANET;
            list.add(destination(ship, new ShipAutopilot.Target(body.x(), body.y(), body.z(), body.radius() + PLANET_STOP), kind, name.zh(), name.en(),
                    Component.translatable("gui.universe.autopilot.name", name.zh(), name.en())));
        }
        Stations.site(server, player.level().dimension()).ifPresent(site -> {
            int[] mouth = site.toWorld(StationLayout.RADIUS + STATION_APPROACH, 2, 0);
            // 空间站的名字由客户端按种类翻译
            list.add(destination(ship, new ShipAutopilot.Target(mouth[0] + 0.5, mouth[1], mouth[2] + 0.5, 0), ShipPayloads.Destination.STATION,
                    "", "", Component.translatable("gui.universe.autopilot.station")));
        });
        ShipBlackHole.in(server, system).ifPresent(hole -> {
            double horizon = Planets.galaxy(server).system(system).map(BlackHoles::horizon).orElse(BlackHoles.HORIZON);
            list.add(destination(ship, new ShipAutopilot.Target(hole.x(), hole.y(), hole.z(), horizon + HOLE_STOP), ShipPayloads.Destination.BLACK_HOLE,
                    "", "", Component.translatable("gui.universe.autopilot.black_hole")));
        });
        freighterDestinations(player, ship, system, list);
        // 收到的信号：废弃货船照着进货船机库的路线飞进它的机库，遇险的货船停在战场外头（ADR-0024）
        SpaceEncounters.beacon(server, player.getUUID(), system).ifPresent(beacon -> {
            Vec3 at = beacon.target();
            Destination to = destination(ship, new ShipAutopilot.Target(at.x, at.y, at.z, beacon.stop()), ShipPayloads.Destination.SIGNAL,
                    beacon.derelict() ? "废弃货船" : "遇险货船", beacon.derelict() ? "Derelict Freighter" : "Freighter in Distress",
                    Component.translatable(beacon.derelict() ? "gui.universe.autopilot.derelict" : "gui.universe.autopilot.distress"));
            list.add(new Destination(to.target(), to.name(), to.shown(), beacon.bounds()));
        });
        list.sort(Comparator.comparingInt((Destination d) -> d.shown().kind() == ShipPayloads.Destination.SUMMON_FREIGHTER ? -1 : 0)
                .thenComparingInt(d -> d.shown().distance()));
        List<Destination> shown = list.subList(0, Math.min(MAX_DESTINATIONS, list.size()));
        DESTINATIONS.put(player.getUUID(), List.copyOf(shown));
        PacketDistributor.sendToPlayer(player, new ShipPayloads.Destinations(shown.stream().map(Destination::shown).toList()));
    }

    private static Destination destination(ShipEntity ship, ShipAutopilot.Target target, int kind, String nameZh, String nameEn, Component name) {
        double dx = target.x() - ship.getX();
        double dy = target.y() - ship.getY();
        double dz = target.z() - ship.getZ();
        int distance = (int) Math.max(0, Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) - target.stop()));
        return new Destination(target, name, new ShipPayloads.Destination(kind, nameZh, nameEn, distance), null);
    }

    /** 自己的货船：停在这个系里就能飞过去，有货船就能叫过来（排在最前面）。 */
    private static void freighterDestinations(ServerPlayer player, ShipEntity ship, int system, List<Destination> list) {
        FleetData data = FleetData.get(player.level().getServer());
        Optional<FleetData.Entry> entry = data.find(player.getUUID()).filter(e -> e.fleet().freighter().isPresent());
        if (entry.isEmpty()) {
            return;
        }
        Optional<FleetData.Placed> placed = entry.get().placed();
        if (placed.isPresent() && placed.get().system() == system) {
            Optional<Freighters.Exit> exit = Freighters.exit(player.level().getServer(), entry.get().fleet().freighter().orElseThrow().id());
            if (exit.isPresent()) {
                Vec3 mouth = exit.get().position();
                Destination to = destination(ship, new ShipAutopilot.Target(mouth.x, mouth.y, mouth.z, 0), ShipPayloads.Destination.FREIGHTER, "", "",
                        Component.translatable("gui.universe.autopilot.freighter"));
                int[] bounds = Freighters.bounds(placed.get(), Freighters.exterior(entry.get().fleet().freighter().orElseThrow().seed()));
                list.add(new Destination(to.target(), to.name(), to.shown(), bounds));
            }
        }
        list.add(new Destination(new ShipAutopilot.Target(ship.getX(), ship.getY(), ship.getZ(), 0), Component.empty(),
                new ShipPayloads.Destination(ShipPayloads.Destination.SUMMON_FREIGHTER, "", "", 0), null));
    }
}
