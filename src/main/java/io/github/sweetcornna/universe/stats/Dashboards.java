package io.github.sweetcornna.universe.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.core.stats.Dashboard.Pin;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.core.stats.ReferenceRates;
import io.github.sweetcornna.universe.dyson.DysonData;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.network.DashboardDataPayload;
import io.github.sweetcornna.universe.network.DashboardDataPayload.Entry;
import io.github.sweetcornna.universe.network.DashboardDataPayload.Reading;
import io.github.sweetcornna.universe.network.DashboardEditPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 仪表盘的服务端（ADR-0033 §四）：玩家钉的图存在玩家身上（{@link UniverseAttachments#DASHBOARD}，同步给本人），每 5 秒
 * 给钉了图的玩家发一次每张图的数，只算钉了的。改了图马上发一次。
 */
public final class Dashboards {
    /** 多久发一次。 */
    public static final int INTERVAL = 100;
    /** 戴森球的功率没有存下来的曲线，这里每 5 秒取一次样，留最近 4 分钟。 */
    private static final int DYSON_SAMPLES = 48;
    private static final Map<Integer, Dashboard.Samples> DYSON = new HashMap<>();

    private Dashboards() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> {
            MinecraftServer server = event.getServer();
            if (server.getTickCount() % INTERVAL != 0) {
                return;
            }
            long start = ServerPerf.begin();
            sampleDyson(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!pins(player).isEmpty()) {
                    send(player);
                }
            }
            ServerPerf.end("dashboard", start);
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && !pins(player).isEmpty()) {
                send(player);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> DYSON.clear());
    }

    public static List<Pin> pins(ServerPlayer player) {
        return player.getData(UniverseAttachments.DASHBOARD);
    }

    /** 改仪表盘（开发脚本、GameTest 也走这里）。 */
    public static void edit(ServerPlayer player, DashboardEditPayload.Action action, int index, Pin pin) {
        List<Pin> pins = pins(player);
        List<Pin> next = switch (action) {
            case ADD -> Dashboard.add(pins, Pin.of(pin.kind(), pin.target(), pin.scope(), pin.range()));
            case REMOVE -> Dashboard.remove(pins, index);
            case PLACE -> Dashboard.place(pins, index, pin.x(), pin.y());
            case TOGGLE -> Dashboard.toggle(pins, index);
            case RESET -> {
                List<Pin> reset = pins;
                for (int i = 0; i < reset.size(); i++) {
                    reset = Dashboard.place(reset, i, -1, -1);
                }
                yield reset;
            }
        };
        if (!next.equals(pins)) {
            player.setData(UniverseAttachments.DASHBOARD, next);
        }
        send(player);
    }

    public static void handleEdit(DashboardEditPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            edit(player, payload.action(), payload.index(), payload.pin());
        }
    }

    public static void send(ServerPlayer player) {
        if (player.connection == null) {
            // GameTest 的假玩家没有连接
            return;
        }
        MinecraftServer server = player.level().getServer();
        List<Reading> readings = new ArrayList<>();
        for (Pin pin : pins(player)) {
            readings.add(reading(server, pin));
        }
        PacketDistributor.sendToPlayer(player, new DashboardDataPayload(readings));
    }

    /** 一张图现在的数。 */
    public static Reading reading(MinecraftServer server, Pin pin) {
        long now = server.overworld().getGameTime();
        ProductionStats stats = ProductionData.get(server).stats();
        ProductionStats.Range range = ProductionLog.range(pin.range());
        return switch (pin.kind()) {
            case ITEM -> {
                List<String> dimensions = ProductionLog.dimensions(server, pin.scope());
                ProductionStats.Row row = stats.row(dimensions, pin.target(), range, now);
                ProductionStats.History history = stats.history(dimensions, pin.target(), range, now);
                ReferenceRates.Rate reference = ProductionLog.referenceRate(server, dimensions, pin.target());
                yield new Reading("", row.produced(), row.consumed(), reference.produced(), ProductionLog.floats(history.produced()),
                        ProductionLog.floats(history.consumed()), List.of());
            }
            case POWER -> {
                List<String> dimensions = ProductionLog.dimensions(server, pin.scope());
                ProductionStats.Snapshot snapshot = stats.snapshot(dimensions, range, now);
                ProductionStats.History history = stats.powerHistory(dimensions, range, now);
                yield new Reading("", snapshot.generation(), snapshot.demand(), snapshot.satisfaction(), ProductionLog.floats(history.produced()),
                        ProductionLog.floats(history.consumed()), List.of());
            }
            case RESEARCH -> research(server);
            case DYSON -> dyson(server, pin.target());
            case STATION -> station(server, pin.target());
        };
    }

    private static Reading research(MinecraftServer server) {
        ResearchState state = Research.state(server.overworld());
        String current = state.current();
        List<Entry> next = new ArrayList<>();
        for (int i = 0; i < Math.min(3, state.queue().size()); i++) {
            next.add(new Entry(state.queue().get(i), state.plannedLevel(Research.TREE, i), 0));
        }
        if (current == null) {
            return new Reading("", 0, 0, state.queue().size(), new float[0], new float[0], next);
        }
        int level = Research.TREE.upgrade(current).isPresent() ? state.level(current) + 1 : 0;
        // 正在研究的放在第一行，级数放在 max 里
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(current, 0, level));
        entries.addAll(next);
        return new Reading(current, state.progress(current), state.pointsNeeded(Research.TREE, current), state.queue().size(), new float[0],
                new float[0], entries);
    }

    private static Reading dyson(MinecraftServer server, String target) {
        int system;
        try {
            system = Integer.parseInt(target);
        } catch (NumberFormatException e) {
            return Reading.EMPTY;
        }
        DysonSphere sphere = DysonData.get(server).spheres().get(system);
        if (sphere == null) {
            return Reading.EMPTY;
        }
        Dashboard.Samples samples = DYSON.get(system);
        double[] line = samples == null ? new double[0] : samples.values();
        return new Reading("", sphere.generation(), sphere.lastDrawn(), sphere.sails(), ProductionLog.floats(line), new float[0], List.of());
    }

    private static Reading station(MinecraftServer server, String target) {
        Station station;
        try {
            station = Logistics.network(server).get(UUID.fromString(target));
        } catch (IllegalArgumentException e) {
            return Reading.EMPTY;
        }
        if (station == null) {
            return Reading.EMPTY;
        }
        List<Entry> entries = new ArrayList<>();
        for (StationSlot slot : station.slots()) {
            if (!slot.isEmpty()) {
                entries.add(new Entry(slot.item(), slot.count(), slot.max()));
            }
        }
        return new Reading(station.name(), station.energy(), 0, 0, new float[0], new float[0], entries);
    }

    /** 给钉着的戴森球取一次样。 */
    private static void sampleDyson(MinecraftServer server) {
        Set<Integer> pinned = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Pin pin : pins(player)) {
                if (pin.kind() == Dashboard.Kind.DYSON) {
                    try {
                        pinned.add(Integer.parseInt(pin.target()));
                    } catch (NumberFormatException e) {
                        // 坏的图不取样
                    }
                }
            }
        }
        DYSON.keySet().retainAll(pinned);
        Map<Integer, DysonSphere> spheres = DysonData.get(server).spheres();
        for (int system : pinned) {
            DysonSphere sphere = spheres.get(system);
            if (sphere != null) {
                DYSON.computeIfAbsent(system, key -> new Dashboard.Samples(DYSON_SAMPLES)).add(sphere.generation());
            }
        }
    }
}
