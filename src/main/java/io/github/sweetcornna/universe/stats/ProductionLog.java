package io.github.sweetcornna.universe.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.logistics.LogisticsNetwork;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.power.PowerSystem;
import io.github.sweetcornna.universe.core.stats.ProductionStats;
import io.github.sweetcornna.universe.core.stats.RecipeGraph;
import io.github.sweetcornna.universe.core.stats.ReferenceRates;
import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.dyson.DysonData;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.logistics.Logistics;
import io.github.sweetcornna.universe.network.StatsDetailPayload;
import io.github.sweetcornna.universe.network.StatsDysonPayload;
import io.github.sweetcornna.universe.network.StatsRequestPayload;
import io.github.sweetcornna.universe.network.StatsSnapshotPayload;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.story.Story;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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
 *
 * <p>M18（ADR-0033 §四）加了：星际运输船卸货记进出口；各种发电、用电设备每秒取一次样；配方设备运转时每秒报一次满速
 * 产量（参考产量，只在内存里，不存档）；面板选中一种物品时发它的曲线、上下游、参考产量和进出口；戴森球每层的统计。
 */
public final class ProductionLog {
    /** 电网每多少刻记一次。 */
    private static final int POWER_INTERVAL = 20;
    /** 每个世界在攒的电：发电能力、需求、供上的、充进去的、攒了几刻。 */
    private static final Map<ServerLevel, long[]> POWER = new WeakHashMap<>();
    /** 每个世界这一秒取的样：设备种类（方块 ID）→ 发电能力、需求（焦耳，按一秒算）。 */
    private static final Map<ServerLevel, Map<String, long[]>> DEVICES = new WeakHashMap<>();
    private static final ReferenceRates REFERENCE = new ReferenceRates();
    /** 星系的统计范围写成 {@code system:<序号>}。 */
    public static final String SYSTEM_PREFIX = "system:";

    private ProductionLog() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerStartedEvent.class, event -> {
            MinecraftServer server = event.getServer();
            Logistics.network(server).setProductionListener(new LogisticsNetwork.ProductionListener() {
                @Override
                public void produced(Station station, String item, int count) {
                    record(server, station.dimension(), item, count, true);
                }

                @Override
                public void shipped(String fromDimension, String toDimension, String item, int count) {
                    ProductionLog.shipped(server, fromDimension, toDimension, item, count);
                }
            });
        });
        gameBus.addListener(ServerTickEvent.Post.class, event -> {
            MinecraftServer server = event.getServer();
            long now = now(server);
            if (now % ProductionStats.LONG_TICKS == 0) {
                long start = ServerPerf.begin();
                ProductionData.get(server).stats().prune(now);
                REFERENCE.prune(now);
                ServerPerf.end("production_log", start);
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            POWER.clear();
            DEVICES.clear();
            REFERENCE.clear();
            RecipeIndex.clear();
        });
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

    /** 星际运输船把货卸进了需求站（物流网络调用），记成运出的维度的出口、运到的维度的进口。 */
    public static void shipped(MinecraftServer server, String from, String to, String item, int count) {
        ProductionData data = ProductionData.get(server);
        data.stats().shipped(from, to, item, count, now(server));
        data.setDirty();
    }

    /**
     * 配方设备运转中（每刻调用，每秒真正记一次）：满速时每分钟做几次。
     *
     * @param speed        设备速度乘上增产剂的加速
     * @param outputFactor 增产剂额外产出时产物的倍数，否则是 1
     */
    public static void reference(@Nullable Level level, BlockPos pos, RecipeHolder<MachineRecipe> recipe, double speed, double outputFactor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = now(serverLevel.getServer());
        if (!ReferenceRates.due(now, pos.asLong()) || recipe.value().time() <= 0) {
            return;
        }
        double crafts = ProductionStats.LONG_TICKS * speed / recipe.value().time();
        REFERENCE.report(key(level), pos.asLong(), RecipeIndex.id(recipe), crafts, outputFactor, now);
    }

    /** 几个维度（{@code null} 是全部）里一种物品的参考产量。 */
    public static ReferenceRates.Rate referenceRate(MinecraftServer server, @Nullable List<String> dimensions, String item) {
        return REFERENCE.rate(RecipeIndex.graph(server), dimensions, item, now(server));
    }

    /** 这台配方设备现在报的配方（GameTest 用）；停了时为空。 */
    public static @Nullable String referenceRecipe(ServerLevel level, BlockPos pos) {
        return REFERENCE.recipeAt(key(level), pos.asLong(), now(level.getServer()));
    }

    /** 电网结算设备时调用（设备基类）：每秒取一次样，记下这种设备的发电能力和需求。没接上电网的不算。 */
    public static void device(MachineBlockEntity machine) {
        if (!(machine.getLevel() instanceof ServerLevel level) || level.getGameTime() % POWER_INTERVAL != 0) {
            return;
        }
        long generation = Math.max(0, machine.generation());
        long demand = Math.max(0, machine.demand());
        if (generation <= 0 && demand <= 0 || !machine.connected()) {
            return;
        }
        String kind = BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()).toString();
        long[] sample = DEVICES.computeIfAbsent(level, key -> new HashMap<>()).computeIfAbsent(kind, key -> new long[2]);
        sample[0] += generation * POWER_INTERVAL;
        sample[1] += demand * POWER_INTERVAL;
    }

    /** 电网结算完以后调用：把这一刻各个电网的数加起来，攒够一秒记一次；这一秒取的设备样也记下。 */
    public static void power(ServerLevel level, List<PowerSystem.Grid> grids) {
        long[] tally = POWER.computeIfAbsent(level, key -> new long[5]);
        for (PowerSystem.Grid grid : grids) {
            tally[0] += grid.generation();
            tally[1] += grid.demand();
            tally[2] += grid.supplied();
            tally[3] += grid.charged();
        }
        Map<String, long[]> devices = DEVICES.get(level);
        if (devices != null && !devices.isEmpty()) {
            ProductionData data = ProductionData.get(level.getServer());
            long now = now(level.getServer());
            devices.forEach((kind, sample) -> data.stats().device(key(level), kind, sample[0], sample[1], now));
            devices.clear();
            data.setDirty();
        }
        if (++tally[4] >= POWER_INTERVAL) {
            ProductionData data = ProductionData.get(level.getServer());
            data.stats().power(key(level), tally[0], tally[1], tally[2], tally[3], now(level.getServer()));
            data.setDirty();
            java.util.Arrays.fill(tally, 0);
        }
    }

    // ---- 统计面板

    /** 统计范围对应的维度：空是全部（{@code null}），{@code system:<序号>} 是这个星系里有账的维度，别的是那个维度自己。 */
    public static @Nullable List<String> dimensions(MinecraftServer server, String scope) {
        if (scope.isEmpty()) {
            return null;
        }
        if (!scope.startsWith(SYSTEM_PREFIX)) {
            return List.of(scope);
        }
        OptionalInt system = parseSystem(scope);
        List<String> dimensions = new ArrayList<>();
        for (String dimension : ProductionData.get(server).stats().dimensions()) {
            OptionalInt of = systemOf(dimension);
            if (of.isPresent() && system.isPresent() && of.getAsInt() == system.getAsInt()) {
                dimensions.add(dimension);
            }
        }
        return dimensions;
    }

    /** {@code system:<序号>} 里的序号。 */
    public static OptionalInt parseSystem(String scope) {
        if (!scope.startsWith(SYSTEM_PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(scope.substring(SYSTEM_PREFIX.length())));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** 维度所在的星系（客户端也用同一套算法排星系）。 */
    public static OptionalInt systemOf(String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? OptionalInt.empty() : Dyson.systemOf(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static ProductionStats.Range range(int ordinal) {
        ProductionStats.Range[] ranges = ProductionStats.Range.values();
        return ranges[Math.floorMod(ordinal, ranges.length)];
    }

    public static void handleRequest(StatsRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ProductionStats stats = ProductionData.get(server).stats();
        ProductionStats.Range range = range(payload.range());
        long now = now(server);
        List<String> dimensions = dimensions(server, payload.scope());
        ProductionStats.Snapshot snapshot = stats.snapshot(dimensions, range, now);
        List<StatsSnapshotPayload.Row> rows = new ArrayList<>();
        for (ProductionStats.Row row : snapshot.rows()) {
            if (rows.size() >= StatsSnapshotPayload.MAX_ROWS) {
                break;
            }
            rows.add(new StatsSnapshotPayload.Row(row.item(), (float) row.produced(), (float) row.consumed(), (float) row.imported(),
                    (float) row.exported()));
        }
        PacketDistributor.sendToPlayer(player, new StatsSnapshotPayload(payload.scope(), range.ordinal(), List.copyOf(stats.dimensions()),
                rows, snapshot.generation(), snapshot.demand(), snapshot.charging(), snapshot.satisfaction(), parts(snapshot.generators()),
                parts(snapshot.consumers())));
        if (!payload.item().isEmpty()) {
            PacketDistributor.sendToPlayer(player, detail(server, payload.scope(), dimensions, payload.item(), range, now));
        }
        if (payload.tab() == StatsRequestPayload.DYSON) {
            PacketDistributor.sendToPlayer(player, dyson(server, player, payload.scope()));
        }
    }

    private static List<StatsSnapshotPayload.Part> parts(List<ProductionStats.Part> parts) {
        List<StatsSnapshotPayload.Part> out = new ArrayList<>();
        for (ProductionStats.Part part : parts) {
            if (out.size() >= StatsSnapshotPayload.MAX_PARTS) {
                break;
            }
            out.add(new StatsSnapshotPayload.Part(part.kind(), (float) part.power()));
        }
        return out;
    }

    /** 选中的一种物品：曲线、参考产量、上下游、进出口。 */
    public static StatsDetailPayload detail(MinecraftServer server, String scope, @Nullable List<String> dimensions, String item,
            ProductionStats.Range range, long now) {
        ProductionStats stats = ProductionData.get(server).stats();
        ProductionStats.History history = stats.history(dimensions, item, range, now);
        RecipeGraph graph = RecipeIndex.graph(server);
        ReferenceRates.Rate reference = REFERENCE.rate(graph, dimensions, item, now);
        List<StatsDetailPayload.Trade> trades = new ArrayList<>();
        for (ProductionStats.Trade trade : stats.trade(dimensions, item, range, now)) {
            trades.add(new StatsDetailPayload.Trade(trade.partner(), (float) trade.imported(), (float) trade.exported()));
        }
        return new StatsDetailPayload(scope, range.ordinal(), item, history.bucketTicks(), floats(history.produced()),
                floats(history.consumed()), (float) reference.produced(), (float) reference.consumed(), reference.producers(),
                reference.consumers(), recipes(graph.producers(item)), recipes(graph.consumers(item)), trades);
    }

    static float[] floats(double[] values) {
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (float) values[i];
        }
        return out;
    }

    private static List<StatsDetailPayload.Recipe> recipes(List<RecipeGraph.Recipe> recipes) {
        List<StatsDetailPayload.Recipe> out = new ArrayList<>();
        for (RecipeGraph.Recipe recipe : recipes) {
            if (out.size() >= StatsDetailPayload.MAX_RECIPES) {
                break;
            }
            List<StatsDetailPayload.Stack> inputs = new ArrayList<>();
            for (RecipeGraph.Input input : recipe.inputs()) {
                if (!input.items().isEmpty()) {
                    inputs.add(new StatsDetailPayload.Stack(input.items().getFirst(), input.count()));
                }
            }
            List<StatsDetailPayload.Stack> outputs = new ArrayList<>();
            for (RecipeGraph.Output output : recipe.outputs()) {
                outputs.add(new StatsDetailPayload.Stack(output.item(), output.count()));
            }
            out.add(new StatsDetailPayload.Recipe(recipe.id(), recipe.machine(), inputs, outputs, recipe.time()));
        }
        return out;
    }

    /**
     * 戴森球那一页：统计范围是星系时看那个星系，是星球时看星球所在的星系，是全部时看玩家所在的星系；那里没有戴森球就看
     * 第一个有的。
     */
    public static StatsDysonPayload dyson(MinecraftServer server, ServerPlayer player, String scope) {
        Map<Integer, DysonSphere> spheres = DysonData.get(server).spheres();
        List<Integer> systems = new ArrayList<>();
        spheres.forEach((system, sphere) -> {
            if (sphere.generation() > 0 || sphere.builtLayers() > 0 || sphere.sails() > 0) {
                systems.add(system);
            }
        });
        systems.sort(null);
        OptionalInt wanted;
        if (scope.startsWith(SYSTEM_PREFIX)) {
            wanted = parseSystem(scope);
        } else if (!scope.isEmpty()) {
            wanted = systemOf(scope);
        } else {
            wanted = Dyson.systemOf(player.level().dimension());
        }
        int system = wanted.isPresent() && systems.contains(wanted.getAsInt()) ? wanted.getAsInt() : systems.isEmpty() ? -1 : systems.getFirst();
        DysonSphere sphere = system < 0 ? null : spheres.get(system);
        if (sphere == null) {
            return new StatsDysonPayload(-1, systems, 0, 0, 0, 0, 0, List.of());
        }
        List<StatsDysonPayload.Layer> layers = new ArrayList<>();
        for (DysonLayer layer : sphere.layers()) {
            layers.add(layer(sphere, layer));
        }
        return new StatsDysonPayload(system, systems, sphere.generation(), sphere.swarmPower(), sphere.sails(), sphere.siphon(),
                sphere.lastDrawn(), layers);
    }

    private static StatsDysonPayload.Layer layer(DysonSphere sphere, DysonLayer layer) {
        int nodeTotal = layer.count(DysonLayer.Element.NODE);
        int frameTotal = layer.count(DysonLayer.Element.FRAME);
        int nodes = 0;
        for (int i = 0; i < nodeTotal; i++) {
            if (layer.nodeBuilt(i)) {
                nodes++;
            }
        }
        int frames = 0;
        for (int i = 0; i < frameTotal; i++) {
            if (layer.frameBuilt(i)) {
                frames++;
            }
        }
        long sp = layer.structurePoints();
        long cells = layer.cellPoints();
        double power = (sp * DysonSphere.SP_POWER + cells * DysonSphere.CELL_POWER) * sphere.lightFactor();
        return new StatsDysonPayload.Layer(layer.id(), (float) layer.radius(), nodes, nodeTotal, frames, frameTotal, layer.fullShells(),
                layer.count(DysonLayer.Element.SHELL), sp, cells, power);
    }
}
