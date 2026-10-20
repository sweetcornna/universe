package io.github.sweetcornna.universe.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.network.StatsRequestPayload;
import io.github.sweetcornna.universe.network.StatsSnapshotPayload;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.story.Story;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 生产统计的记账入口（ADR-0015）：设备每做完一次在这里记产出和消耗，电网每秒记一次发电、用电；统计面板来要快照时
 * 从这里算。时间统一用主世界的游戏刻。
 *
 * <p>记账的设备：配方设备、采矿机、抽水站、萃取站、分馏塔（只算变成重氢的那部分）、轨道采集器，火力发电厂烧掉的燃料
 * 记成消耗；物流站搬运不算。
 */
public final class ProductionLog {
    /** 电网每多少刻记一次。 */
    private static final int POWER_INTERVAL = 20;
    /** 每个世界在攒的电：发电能力、需求、供上的、充进去的、攒了几刻。 */
    private static final Map<ServerLevel, long[]> POWER = new WeakHashMap<>();

    private ProductionLog() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerStartedEvent.class, event -> {
            MinecraftServer server = event.getServer();
            Logistics.network(server).setProductionListener((station, item, count) ->
                    record(server, station.dimension(), item, count, true));
        });
        gameBus.addListener(ServerTickEvent.Post.class, event -> {
            MinecraftServer server = event.getServer();
            long now = now(server);
            if (now % ProductionStats.LONG_TICKS == 0) {
                ProductionData.get(server).stats().prune(now);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> POWER.clear());
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static String key(Level level) {
        return level.dimension().identifier().toString();
    }

    public static void produced(Level level, Item item, int count) {
        if (level instanceof ServerLevel serverLevel && count > 0) {
            record(serverLevel.getServer(), key(level), BuiltInRegistries.ITEM.getKey(item).toString(), count, true);
            if (item == UniverseItems.UNIVERSE_MATRIX.get()) {
                Story.reach(serverLevel.getServer(), Milestone.FIRST_UNIVERSE_MATRIX);
            }
        }
    }

    public static void produced(Level level, ItemStack stack) {
        produced(level, stack.getItem(), stack.getCount());
    }

    public static void consumed(Level level, Item item, int count) {
        if (level instanceof ServerLevel serverLevel && count > 0) {
            record(serverLevel.getServer(), key(level), BuiltInRegistries.ITEM.getKey(item).toString(), count, false);
        }
    }

    private static void record(MinecraftServer server, String dimension, String item, int count, boolean produced) {
        ProductionData data = ProductionData.get(server);
        if (produced) {
            data.stats().produced(dimension, item, count, now(server));
        } else {
            data.stats().consumed(dimension, item, count, now(server));
        }
        data.setDirty();
    }

    /** 电网结算完以后调用：把这一刻各个电网的数加起来，攒够一秒记一次。 */
    public static void power(ServerLevel level, List<PowerSystem.Grid> grids) {
        long[] tally = POWER.computeIfAbsent(level, key -> new long[5]);
        for (PowerSystem.Grid grid : grids) {
            tally[0] += grid.generation();
            tally[1] += grid.demand();
            tally[2] += grid.supplied();
            tally[3] += grid.charged();
        }
        if (++tally[4] >= POWER_INTERVAL) {
            ProductionData data = ProductionData.get(level.getServer());
            data.stats().power(key(level), tally[0], tally[1], tally[2], tally[3], now(level.getServer()));
            data.setDirty();
            java.util.Arrays.fill(tally, 0);
        }
    }

    public static void handleRequest(StatsRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ProductionStats stats = ProductionData.get(server).stats();
        ProductionStats.Range[] ranges = ProductionStats.Range.values();
        ProductionStats.Range range = ranges[Math.floorMod(payload.range(), ranges.length)];
        String dimension = payload.dimension().isEmpty() ? null : payload.dimension();
        ProductionStats.Snapshot snapshot = stats.snapshot(dimension, range, now(server));
        List<StatsSnapshotPayload.Row> rows = new ArrayList<>();
        for (ProductionStats.Row row : snapshot.rows()) {
            if (rows.size() >= StatsSnapshotPayload.MAX_ROWS) {
                break;
            }
            rows.add(new StatsSnapshotPayload.Row(row.item(), (float) row.produced(), (float) row.consumed()));
        }
        PacketDistributor.sendToPlayer(player, new StatsSnapshotPayload(payload.dimension(), range.ordinal(), List.copyOf(stats.dimensions()),
                rows, snapshot.generation(), snapshot.demand(), snapshot.charging(), snapshot.satisfaction()));
    }
}
