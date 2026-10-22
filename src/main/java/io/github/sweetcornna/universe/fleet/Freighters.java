package io.github.sweetcornna.universe.fleet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.FreighterInterior;
import io.github.sweetcornna.universe.core.fleet.FreighterLayout;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateModel;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipTransit;
import io.github.sweetcornna.universe.network.FleetPayloads;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.freighter.FreighterDimensions;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 货船（ADR-0024）：外壳放在恒星系的太空里（锚点上，或者召唤时玩家前面），不出远征的护卫舰停在右舷外；
 * 开船飞进外壳的机库口就到了船舱维度，从船舱的机库门飞出去回到机库口外。
 *
 * <p>放外壳、拆外壳要建维度、加载区块，一律排到服务器刻末尾（{@link #later}）。拆的时候只拆货船自己的方块，
 * 玩家贴在外面的东西不动。
 */
public final class Freighters {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 船舱里从机库门掉下去这么多格，就送回机库。 */
    private static final int FALL_LIMIT = 16;
    /** 召唤的货船离空间站至少这么远。 */
    private static final double STATION_CLEARANCE = 500;

    /** 外壳放在太空里的什么地方、朝哪边：飞出船舱时回到这里。 */
    public record Exit(int system, Vec3 position, float yaw) {
    }

    private static final Map<Long, FreighterLayout.Exterior> EXTERIORS = new HashMap<>();
    private static final List<Runnable> JOBS = new ArrayList<>();
    /** 已经提醒过"远征回来了"的：主人/出发时刻/任务。 */
    private static final Set<String> NOTIFIED = new HashSet<>();

    private Freighters() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("freighters", () -> tick(event.getServer())));
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                keepAboard(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendLocation(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendLocation(player);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                sendLocation(player);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            EXTERIORS.clear();
            JOBS.clear();
            NOTIFIED.clear();
            FreighterConsole.clear();
            FleetTerminal.clear();
        });
    }

    public static FreighterLayout.Exterior exterior(long seed) {
        return EXTERIORS.computeIfAbsent(seed, FreighterLayout::exterior);
    }

    /** 排到这一刻末尾再做：那时没有维度在 tick，能建维度、加载区块。 */
    public static void later(Runnable job) {
        JOBS.add(job);
    }

    private static void tick(MinecraftServer server) {
        if (!JOBS.isEmpty()) {
            List<Runnable> batch = List.copyOf(JOBS);
            JOBS.clear();
            for (Runnable job : batch) {
                try {
                    job.run();
                } catch (RuntimeException e) {
                    LOGGER.error("Freighter job failed", e);
                }
            }
        }
        FreighterConsole.tick(server);
        if (server.getTickCount() % 20 == 0) {
            notifyReturns(server);
        }
    }

    /** 远征回来了：主人在线就提醒一声（不在线的等他上线）。 */
    private static void notifyReturns(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Map.Entry<UUID, FleetData.Entry> entry : FleetData.get(server).all().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            for (Fleet.Voyage voyage : entry.getValue().fleet().voyages()) {
                if (voyage.done(now) && NOTIFIED.add(entry.getKey() + "/" + voyage.start() + "/" + voyage.slot())) {
                    player.sendSystemMessage(Component.translatable("message.universe.fleet.returned",
                            Component.translatable("gui.universe.fleet.kind." + (voyage.sortie() ? "hive" : voyage.offer().kind().key()))));
                    player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
                }
            }
        }
    }

    // ---- 找货船

    /** 这艘货船（按编号）的主人和他那一份。 */
    public static Optional<Map.Entry<UUID, FleetData.Entry>> byId(MinecraftServer server, int freighter) {
        for (Map.Entry<UUID, FleetData.Entry> entry : FleetData.get(server).all().entrySet()) {
            if (entry.getValue().fleet().freighter().map(f -> f.id() == freighter).orElse(false)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** 玩家所在的船舱是哪艘货船的；不在船舱里时为空。 */
    public static OptionalInt aboard(ServerPlayer player) {
        return FreighterDimensions.id(player.level().dimension());
    }

    /** 货船现在在哪个恒星系（船舱算在货船所在的系里）。 */
    public static OptionalInt system(MinecraftServer server, int freighter) {
        return byId(server, freighter).flatMap(e -> e.getValue().placed()).map(p -> OptionalInt.of(p.system())).orElse(OptionalInt.empty());
    }

    // ---- 外壳

    private static BlockPos origin(FleetData.Placed placed) {
        return new BlockPos(placed.x(), placed.y(), placed.z());
    }

    private static void build(ServerLevel level, BlockPos origin, List<FreighterLayout.Cell> cells) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (FreighterLayout.Cell cell : cells) {
            level.setBlock(cursor.setWithOffset(origin, cell.x(), cell.y(), cell.z()), FreighterBlocks.exterior(cell.role()), Block.UPDATE_CLIENTS);
        }
    }

    private static void demolish(ServerLevel level, BlockPos origin, List<FreighterLayout.Cell> cells) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (FreighterLayout.Cell cell : cells) {
            cursor.setWithOffset(origin, cell.x(), cell.y(), cell.z());
            if (FreighterBlocks.isHull(level.getBlockState(cursor))) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 每个泊位该停的种类：舰队里第 i 艘停在第 i 个泊位，出远征的那几个空着。 */
    private static List<Integer> berths(Fleet fleet) {
        List<Integer> want = new ArrayList<>();
        List<Frigate> frigates = fleet.frigates();
        for (int slot = 0; slot < FreighterRules.MAX_FRIGATES; slot++) {
            want.add(slot < frigates.size() && !fleet.busy(frigates.get(slot).id()) ? frigates.get(slot).type().ordinal() : -1);
        }
        return want;
    }

    /** 按 {@code want} 重新摆护卫舰：没变的泊位不动。 */
    private static void park(ServerLevel level, BlockPos origin, FreighterLayout.Exterior exterior, List<Integer> had, List<Integer> want) {
        FrigateType[] types = FrigateType.values();
        for (int slot = 0; slot < FreighterRules.MAX_FRIGATES; slot++) {
            int before = slot < had.size() ? had.get(slot) : -1;
            int after = slot < want.size() ? want.get(slot) : -1;
            if (before == after) {
                continue;
            }
            int[] berth = FrigateModel.berth(exterior, slot);
            BlockPos at = origin.offset(berth[0], berth[1], berth[2]);
            if (before >= 0 && before < types.length) {
                demolish(level, at, FrigateModel.cells(types[before]));
            }
            if (after >= 0 && after < types.length) {
                build(level, at, FrigateModel.cells(types[after]));
            }
        }
    }

    /**
     * 把货船（连同边上的护卫舰）从原来的系拆掉，放进 {@code system}：{@code spot} 为空时停在锚点上。只能在服务器刻末尾调。
     */
    static void moveTo(MinecraftServer server, UUID owner, int system, double @Nullable [] spot) {
        FleetData data = FleetData.get(server);
        FleetData.Entry entry = data.entry(owner);
        Fleet.Freighter freighter = entry.fleet().freighter().orElse(null);
        if (freighter == null) {
            return;
        }
        FreighterLayout.Exterior exterior = exterior(freighter.seed());
        entry.placed().ifPresent(old -> {
            ServerLevel from = SpaceDimensions.getOrCreate(server, old.system());
            BlockPos origin = origin(old);
            park(from, origin, exterior, old.parked(), List.of());
            demolish(from, origin, exterior.cells());
        });
        double[] at = spot != null ? spot : anchor(server, system, owner);
        BlockPos origin = BlockPos.containing(at[0], at[1], at[2]);
        ServerLevel to = SpaceDimensions.getOrCreate(server, system);
        build(to, origin, exterior.cells());
        List<Integer> parked = berths(entry.fleet());
        park(to, origin, exterior, List.of(), parked);
        entry.setPlaced(new FleetData.Placed(system, origin.getX(), origin.getY(), origin.getZ(), parked));
        data.changed();
        sendLocationToInterior(server, freighter.id());
        LOGGER.info("Freighter {} placed in system {} at {}", freighter.id(), system, origin);
    }

    /** 舰队变了（雇、出发、回来、解散）：重新摆停着的护卫舰。 */
    public static void refreshFrigates(MinecraftServer server, UUID owner) {
        later(() -> {
            FleetData data = FleetData.get(server);
            FleetData.Entry entry = data.entry(owner);
            Optional<FleetData.Placed> placed = entry.placed();
            Optional<Fleet.Freighter> freighter = entry.fleet().freighter();
            if (placed.isEmpty() || freighter.isEmpty()) {
                return;
            }
            List<Integer> want = berths(entry.fleet());
            if (want.equals(placed.get().parked())) {
                return;
            }
            ServerLevel level = SpaceDimensions.getOrCreate(server, placed.get().system());
            park(level, origin(placed.get()), exterior(freighter.get().seed()), placed.get().parked(), want);
            FleetData.Placed p = placed.get();
            entry.setPlaced(new FleetData.Placed(p.system(), p.x(), p.y(), p.z(), want));
            data.changed();
        });
    }

    /** 这个系里给这名玩家的货船找一个锚点：按序号从近到远，跳过别人的货船占着的。 */
    private static double[] anchor(MinecraftServer server, int system, UUID owner) {
        Galaxy galaxy = Planets.galaxy(server);
        StarSystem star = galaxy.system(system).orElseThrow();
        List<SpaceLayout.Body> bodies = ShipTransit.layout(server, system);
        double starRadius = SpaceLayout.starRadius(star);
        double[] station = Stations.site(galaxy, system).map(StationLayout.Site::center).orElse(new double[] {starRadius + 6000, 0, 0});
        double[] planet = StationLayout.anchor(star).flatMap(p -> SpaceLayout.find(bodies, p.id()))
                .map(b -> new double[] {b.x(), b.y(), b.z()}).orElse(new double[] {0, 0, 0});
        List<FleetData.Placed> others = new ArrayList<>();
        for (Map.Entry<UUID, FleetData.Entry> entry : FleetData.get(server).inSystem(system)) {
            if (!entry.getKey().equals(owner)) {
                entry.getValue().placed().ifPresent(others::add);
            }
        }
        double[] first = null;
        for (int slot = 0; slot < 32; slot++) {
            double[] spot = FreighterRules.anchor(station, planet, slot, bodies, starRadius);
            if (first == null) {
                first = spot;
            }
            boolean free = true;
            for (FleetData.Placed other : others) {
                if (Math.hypot(other.x() - spot[0], other.z() - spot[2]) < FreighterRules.ANCHOR_SPACING / 2) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return spot;
            }
        }
        return first;
    }

    /** 给玩家一艘货船（买下的、救下来的），停到 {@code system} 里。{@code spot} 为空时停在锚点上。 */
    public static Fleet.Freighter grant(MinecraftServer server, UUID owner, long seed, Grade grade, int system, double @Nullable [] spot) {
        FleetData data = FleetData.get(server);
        Fleet.Freighter freighter = new Fleet.Freighter(data.takeFreighterId(), seed, grade);
        data.entry(owner).fleet().setFreighter(freighter);
        data.changed();
        later(() -> moveTo(server, owner, system, spot));
        return freighter;
    }

    // ---- 机库

    /** 太空里：这个位置在哪艘货船的机库里。 */
    public static OptionalInt hangar(MinecraftServer server, int system, double x, double y, double z) {
        for (Map.Entry<UUID, FleetData.Entry> entry : FleetData.get(server).inSystem(system)) {
            FleetData.Placed placed = entry.getValue().placed().orElseThrow();
            Optional<Fleet.Freighter> freighter = entry.getValue().fleet().freighter();
            if (freighter.isPresent() && exterior(freighter.get().seed()).inHangar(x - placed.x(), y - placed.y(), z - placed.z())) {
                return OptionalInt.of(freighter.get().id());
            }
        }
        return OptionalInt.empty();
    }

    /** 船舱里飞出机库门以后回到哪里：外壳机库口外面，船头朝外（-x）。 */
    public static Optional<Exit> exit(MinecraftServer server, int freighter) {
        return byId(server, freighter).flatMap(entry -> {
            Optional<FleetData.Placed> placed = entry.getValue().placed();
            if (placed.isEmpty()) {
                return Optional.empty();
            }
            double[] mouth = exterior(entry.getValue().fleet().freighter().orElseThrow().seed()).mouth();
            FleetData.Placed p = placed.get();
            return Optional.of(new Exit(p.system(), new Vec3(p.x() + mouth[0], p.y() + mouth[1] - 1, p.z() + mouth[2]), 90));
        });
    }

    /** 外壳在太空里的包围盒（世界坐标），自动驾驶绕开它。 */
    public static int[] bounds(FleetData.Placed placed, FreighterLayout.Exterior exterior) {
        int[] b = exterior.bounds();
        int reach = FrigateModel.berth(exterior, FreighterRules.MAX_FRIGATES - 1)[0] + FrigateModel.HALF_WIDTH;
        return new int[] {placed.x() + b[0], placed.y() + b[1], placed.z() + b[2], placed.x() + Math.max(b[3], reach), placed.y() + b[4] + 6,
                placed.z() + b[5]};
    }

    // ---- 召唤

    /** 自动驾驶列表里选了"召唤货船"：把自己的货船叫到船头前面，冷却 5 分钟。 */
    public static Component summon(ServerPlayer player, ShipEntity ship) {
        MinecraftServer server = player.level().getServer();
        FleetData data = FleetData.get(server);
        Optional<FleetData.Entry> found = data.find(player.getUUID()).filter(e -> e.fleet().freighter().isPresent());
        if (found.isEmpty()) {
            return Component.translatable("message.universe.freighter.none");
        }
        FleetData.Entry entry = found.get();
        long now = server.overworld().getGameTime();
        if (now < entry.summonReady()) {
            return Component.translatable("message.universe.freighter.cooldown", (entry.summonReady() - now + 19) / 20);
        }
        OptionalInt system = SpaceDimensions.system(ship.level().dimension());
        if (system.isEmpty()) {
            return Component.translatable("message.universe.freighter.not_in_space");
        }
        double yaw = Math.toRadians(ship.getYRot());
        double[] spot = {ship.getX() - Math.sin(yaw) * FreighterRules.SUMMON_DISTANCE, ship.getY() - FreighterLayout.HANGAR_MIDDLE,
                ship.getZ() + Math.cos(yaw) * FreighterRules.SUMMON_DISTANCE};
        Galaxy galaxy = Planets.galaxy(server);
        StarSystem star = galaxy.system(system.getAsInt()).orElseThrow();
        boolean clear = FreighterRules.clear(spot, ShipTransit.layout(server, system.getAsInt()), SpaceLayout.starRadius(star))
                && Stations.site(galaxy, system.getAsInt()).map(site -> site.distanceTo(spot[0], spot[1], spot[2]) > STATION_CLEARANCE).orElse(true);
        for (Map.Entry<UUID, FleetData.Entry> other : data.inSystem(system.getAsInt())) {
            FleetData.Placed p = other.getValue().placed().orElseThrow();
            if (!other.getKey().equals(player.getUUID()) && Math.hypot(p.x() - spot[0], p.z() - spot[2]) < FreighterRules.ANCHOR_SPACING) {
                clear = false;
            }
        }
        if (!clear) {
            return Component.translatable("message.universe.freighter.no_room");
        }
        entry.setSummonReady(now + FreighterRules.SUMMON_COOLDOWN);
        data.changed();
        UUID owner = player.getUUID();
        int target = system.getAsInt();
        later(() -> moveTo(server, owner, target, spot));
        ship.level().playSound(null, ship.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.0F, 0.6F);
        return Component.translatable("message.universe.freighter.summoned");
    }

    // ---- 船舱

    /** 船舱里掉出机库门的人：落得太深就送回机库。 */
    private static void keepAboard(ServerPlayer player) {
        if (player.isPassenger() || player.isSpectator() || !(player.level() instanceof ServerLevel level)
                || !FreighterDimensions.isInterior(level.dimension()) || player.getY() > FreighterInterior.ORIGIN_Y - FALL_LIMIT) {
            return;
        }
        int[] arrival = FreighterInterior.ARRIVAL;
        player.teleport(new TeleportTransition(level, new Vec3(arrival[0] + 0.5, FreighterInterior.ORIGIN_Y + arrival[1], arrival[2] + 0.5), Vec3.ZERO,
                0, 0, TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        player.sendOverlayMessage(Component.translatable("message.universe.freighter.vacuum"));
    }

    /** 人在船舱里：告诉客户端货船停在哪，好画太空。 */
    private static void sendLocation(ServerPlayer player) {
        OptionalInt id = aboard(player);
        if (id.isEmpty()) {
            return;
        }
        location(player.level().getServer(), id.getAsInt()).ifPresent(location -> PacketDistributor.sendToPlayer(player, location));
    }

    private static Optional<FleetPayloads.Location> location(MinecraftServer server, int freighter) {
        return byId(server, freighter).flatMap(entry -> entry.getValue().placed())
                .map(p -> new FleetPayloads.Location(freighter, p.system(), p.x(), p.y(), p.z()));
    }

    static void sendLocationToInterior(MinecraftServer server, int freighter) {
        ServerLevel interior = server.getLevel(FreighterDimensions.key(freighter));
        if (interior == null || interior.players().isEmpty()) {
            return;
        }
        location(server, freighter).ifPresent(location -> {
            for (ServerPlayer player : interior.players()) {
                PacketDistributor.sendToPlayer(player, location);
            }
        });
    }

    /** 这名玩家的远征今天的任务（终端上用）。 */
    static List<Expedition.Offer> offers(Fleet.Freighter freighter, long day) {
        return Expedition.offers(freighter.seed(), day);
    }
}
