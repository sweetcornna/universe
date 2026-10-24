package io.github.sweetcornna.universe.core.stats;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.stats.ProductionStats.History;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Part;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Range;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Row;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Snapshot;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Trade;
import io.github.sweetcornna.universe.core.stats.RecipeGraph.Input;
import io.github.sweetcornna.universe.core.stats.RecipeGraph.Output;
import io.github.sweetcornna.universe.core.stats.RecipeGraph.Recipe;

/** M18 的统计：进出口、按设备分开的电、曲线、配方图、参考产量（ADR-0033 §四）。 */
class StatsDetailTest {
    private static final String HOME = "universe:s0/p0";
    private static final String MOON = "universe:s0/p0/m0";
    private static final String FAR = "universe:s1/p2";

    private static final Recipe GEAR = new Recipe("x:gear", "x:assembler", List.of(new Input(List.of("x:iron"), 1)),
            List.of(new Output("x:gear", 1)), 20);
    private static final Recipe MOTOR = new Recipe("x:motor", "x:assembler",
            List.of(new Input(List.of("x:iron"), 2), new Input(List.of("x:gear"), 1), new Input(List.of("x:coil", "x:copper"), 1)),
            List.of(new Output("x:motor", 1)), 40);
    private static final Recipe IRON = new Recipe("x:iron", "x:smelter", List.of(new Input(List.of("x:ore"), 1)),
            List.of(new Output("x:iron", 1)), 20);
    private static final Recipe HAND = new Recipe("x:hand_gear", "minecraft:crafting_table", List.of(new Input(List.of("x:iron"), 4)),
            List.of(new Output("x:gear", 1)), 0);
    private static final RecipeGraph GRAPH = new RecipeGraph(List.of(MOTOR, GEAR, IRON, HAND));

    @Test
    void shipmentsCountAsImportsAndExports() {
        ProductionStats stats = new ProductionStats();
        long now = 0;
        for (int s = 0; s < 60; s++) {
            now = s * 20L;
            stats.shipped(FAR, HOME, "x:iron", 2, now);
            stats.shipped(MOON, HOME, "x:iron", 1, now);
        }
        Row home = stats.snapshot(HOME, Range.MINUTE, now).rows().get(0);
        assertEquals("x:iron", home.item());
        assertEquals(0, home.produced());
        assertEquals(180, home.imported(), 5);
        assertEquals(60, stats.snapshot(MOON, Range.MINUTE, now).rows().get(0).exported(), 2);
        // 整个星系 s0 合起来看：月球运到主星的是内部往来，只剩从 s1 运进来的
        Row system = stats.snapshot(Set.of(HOME, MOON), Range.MINUTE, now).rows().get(0);
        assertEquals(120, system.imported(), 4);
        assertEquals(0, system.exported());
        // 全部合起来没有进出口
        assertTrue(stats.snapshot((String) null, Range.MINUTE, now).rows().isEmpty());
        List<Trade> trades = stats.trade(List.of(HOME), "x:iron", Range.MINUTE, now);
        assertEquals(2, trades.size());
        assertEquals(FAR, trades.get(0).partner());
        assertEquals(120, trades.get(0).imported(), 4);
        assertEquals(MOON, trades.get(1).partner());
        // 仪表盘只算一行，和整份快照里的那行一样
        assertEquals(home.imported(), stats.row(List.of(HOME), "x:iron", Range.MINUTE, now).imported(), 1e-9);
        assertEquals(system.imported(), stats.row(List.of(HOME, MOON), "x:iron", Range.MINUTE, now).imported(), 1e-9);
        assertEquals(0, stats.row(List.of(HOME, MOON), "x:iron", Range.MINUTE, now).exported());
        assertEquals(0, stats.row(null, "x:iron", Range.MINUTE, now).imported());
    }

    @Test
    void sameDimensionShipmentsAreIgnored() {
        ProductionStats stats = new ProductionStats();
        stats.shipped(HOME, HOME, "x:iron", 5, 10);
        assertTrue(stats.dimensions().isEmpty());
    }

    @Test
    void powerIsBrokenDownByDevice() {
        ProductionStats stats = new ProductionStats();
        long now = 0;
        for (int s = 0; s < 30; s++) {
            now = s * 20L;
            stats.power(HOME, 20 * 300, 20 * 100, 20 * 100, 0, now);
            stats.device(HOME, "x:wind", 20 * 100, 0, now);
            stats.device(HOME, "x:thermal", 20 * 200, 0, now);
            stats.device(HOME, "x:assembler", 0, 20 * 100, now);
        }
        Snapshot snapshot = stats.snapshot(HOME, Range.MINUTE, now);
        List<Part> generators = snapshot.generators();
        assertEquals("x:thermal", generators.get(0).kind());
        assertEquals(snapshot.generation() * 2 / 3, generators.get(0).power(), 1);
        assertEquals(snapshot.generation() / 3, generators.get(1).power(), 1);
        assertEquals(List.of("x:assembler"), snapshot.consumers().stream().map(Part::kind).toList());
    }

    @Test
    void historyHasOneBucketPerSlot() {
        ProductionStats stats = new ProductionStats();
        // 前 10 秒每秒一个，后面每秒两个
        long now = 0;
        for (int s = 0; s < 60; s++) {
            now = s * 20L;
            stats.produced(HOME, "x:gear", s < 10 ? 1 : 2, now);
            stats.consumed(HOME, "x:gear", 1, now);
        }
        History minute = stats.history(List.of(HOME), "x:gear", Range.MINUTE, now);
        assertEquals(ProductionStats.SHORT_TICKS, minute.bucketTicks());
        assertEquals(6, minute.produced().length);
        assertEquals(60, minute.produced()[0], 1);
        assertEquals(120, minute.produced()[3], 1);
        assertEquals(60, minute.consumed()[2], 1);
        History hour = stats.history(null, "x:gear", Range.HOUR, now);
        assertEquals(60, hour.produced().length);
        assertEquals(0, hour.produced()[0]);
        assertTrue(hour.produced()[59] > 0);
        History power = stats.powerHistory(List.of(HOME), Range.TEN_MINUTES, now);
        assertEquals(10, power.produced().length);
    }

    @Test
    void graphListsUpstreamAndDownstream() {
        assertEquals(List.of(GEAR, HAND), GRAPH.producers("x:gear"));
        assertEquals(List.of(MOTOR), GRAPH.consumers("x:gear"));
        assertEquals(List.of(GEAR, MOTOR, HAND), GRAPH.consumers("x:iron"));
        // 标签原料里的每一种都算下游
        assertEquals(List.of(MOTOR), GRAPH.consumers("x:copper"));
        assertEquals(List.of(), GRAPH.producers("x:ore"));
        assertEquals(2, MOTOR.consumes("x:iron"));
        assertEquals(0, MOTOR.produces("x:iron"));
        assertNull(GRAPH.get("x:nope"));
        assertEquals(4, GRAPH.size());
    }

    @Test
    void referenceRatesCountRunningMachines() {
        ReferenceRates rates = new ReferenceRates();
        // 两台装配机做齿轮，满速每分钟 60 次；一台开了额外产出 +25%
        rates.report(HOME, 1, "x:gear", 60, 1, 100);
        rates.report(HOME, 2, "x:gear", 60, 1.25, 100);
        rates.report(MOON, 3, "x:motor", 30, 1, 100);
        ReferenceRates.Rate gear = rates.rate(GRAPH, null, "x:gear", 110);
        assertEquals(135, gear.produced(), 1e-9);
        assertEquals(30, gear.consumed(), 1e-9);
        assertEquals(2, gear.producers());
        assertEquals(1, gear.consumers());
        ReferenceRates.Rate iron = rates.rate(GRAPH, List.of(HOME), "x:iron", 110);
        assertEquals(120, iron.consumed(), 1e-9);
        // 停了的设备过一会儿不算了
        rates.report(HOME, 1, "x:gear", 60, 1, 200);
        assertEquals(60, rates.rate(GRAPH, null, "x:gear", 200).produced(), 1e-9);
        rates.prune(200);
        assertEquals(1, rates.size());
        rates.remove(HOME, 1);
        assertEquals(0, rates.size());
    }

    @Test
    void reportsAreSpreadOverASecond() {
        int due = 0;
        for (long now = 0; now < ReferenceRates.REPORT_TICKS * 10; now++) {
            if (ReferenceRates.due(now, 12345L)) {
                due++;
            }
        }
        assertEquals(10, due);
    }

    @Test
    void dashboardKeepsSixDistinctPins() {
        List<Dashboard.Pin> pins = List.of();
        for (int i = 0; i < 8; i++) {
            pins = Dashboard.add(pins, Dashboard.Pin.of(Dashboard.Kind.ITEM, "x:item" + i, HOME, 0));
        }
        assertEquals(Dashboard.MAX_PINS, pins.size());
        pins = Dashboard.remove(pins, 5);
        pins = Dashboard.add(pins, Dashboard.Pin.of(Dashboard.Kind.ITEM, "x:item0", HOME, 1));
        assertEquals(5, pins.size());
        pins = Dashboard.add(pins, Dashboard.Pin.of(Dashboard.Kind.POWER, "", HOME, 0));
        assertEquals(Dashboard.Kind.POWER, pins.get(5).kind());
        pins = Dashboard.toggle(pins, 0);
        assertTrue(pins.get(0).collapsed());
        pins = Dashboard.place(pins, 1, 0.25F, 2);
        assertEquals(0.25F, pins.get(1).x());
        assertEquals(1, pins.get(1).y());
        pins = Dashboard.place(pins, 1, -1, -1);
        assertTrue(!pins.get(1).placed());
        List<Dashboard.Pin> doubled = List.of(pins.get(0), pins.get(0), pins.get(1));
        assertEquals(2, Dashboard.sanitize(doubled).size());
    }

    @Test
    void unplacedPinsStackOnTheRight() {
        List<Dashboard.Pin> pins = List.of(Dashboard.Pin.of(Dashboard.Kind.POWER, "", "", 0),
                Dashboard.Pin.of(Dashboard.Kind.RESEARCH, "", "", 0),
                new Dashboard.Pin(Dashboard.Kind.ITEM, "x:gear", "", 0, 0.1F, 0.5F, false),
                Dashboard.Pin.of(Dashboard.Kind.DYSON, "0", "", 0));
        int[] at = Dashboard.layout(pins, new int[] {80, 80, 80, 80}, 100, 400, 200, 10, 4, 2);
        assertArrayEquals(new int[] {296, 10, 296, 92, 40, 100, 194, 10}, at);
    }

    @Test
    void samplesKeepTheLatest() {
        Dashboard.Samples samples = new Dashboard.Samples(3);
        for (int i = 1; i <= 5; i++) {
            samples.add(i);
        }
        assertArrayEquals(new double[] {3, 4, 5}, samples.values());
    }
}
