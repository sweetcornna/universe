package io.github.sweetcornna.universe.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.stats.ProductionStats.Range;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Row;
import io.github.sweetcornna.universe.core.stats.ProductionStats.Snapshot;

class ProductionStatsTest {
    private static final String HOME = "minecraft:overworld";
    private static final String MOON = "universe:s0/p1/m0";

    /** 从第 0 刻起每秒做一个，记 {@code seconds} 秒，返回最后一刻。 */
    private static long steady(ProductionStats stats, String dimension, String item, int seconds, long start) {
        long now = start;
        for (int s = 0; s < seconds; s++) {
            now = start + s * 20L;
            stats.produced(dimension, item, 1, now);
        }
        return now;
    }

    @Test
    void steadyProductionReadsSixtyPerMinuteInEveryRange() {
        ProductionStats stats = new ProductionStats();
        long now = steady(stats, HOME, "iron", 3600, 0);
        for (Range range : Range.values()) {
            Row row = stats.snapshot(HOME, range, now).rows().get(0);
            assertEquals(60, row.produced(), 1.5, range.name());
            assertEquals(0, row.consumed());
        }
    }

    @Test
    void aFreshLedgerIsNotDilutedByTheWholeWindow() {
        ProductionStats stats = new ProductionStats();
        // 记了 30 秒：一小时的窗口也按 30 秒折算
        long now = steady(stats, HOME, "iron", 30, 100_000);
        assertEquals(60, stats.snapshot(HOME, Range.HOUR, now).rows().get(0).produced(), 2.5);
        assertEquals(60, stats.snapshot(HOME, Range.MINUTE, now).rows().get(0).produced(), 2.5);
    }

    @Test
    void oldProductionLeavesTheShortWindowFirst() {
        ProductionStats stats = new ProductionStats();
        long end = steady(stats, HOME, "iron", 600, 0);
        // 停产五分钟后：一分钟窗口里没有了，十分钟窗口里还有一半
        long now = end + 5 * 1200;
        assertTrue(stats.snapshot(HOME, Range.MINUTE, now).rows().isEmpty());
        assertEquals(30, stats.snapshot(HOME, Range.TEN_MINUTES, now).rows().get(0).produced(), 3);
        // 过了一小时整理掉
        stats.prune(end + 61 * 1200);
        assertTrue(stats.dimensions().isEmpty());
    }

    @Test
    void allDimensionsAddUpAndRowsAreSortedByVolume() {
        ProductionStats stats = new ProductionStats();
        long now = 0;
        for (int s = 0; s < 120; s++) {
            now = s * 20L;
            stats.produced(HOME, "gear", 1, now);
            stats.consumed(HOME, "iron", 2, now);
            stats.produced(MOON, "gear", 1, now);
            stats.produced(MOON, "titanium", 5, now);
        }
        Snapshot all = stats.snapshot(null, Range.MINUTE, now);
        List<String> order = all.rows().stream().map(Row::item).toList();
        assertEquals(List.of("titanium", "gear", "iron"), order);
        assertEquals(120, all.rows().get(1).produced(), 3);
        assertEquals(120, all.rows().get(2).consumed(), 3);
        Snapshot home = stats.snapshot(HOME, Range.MINUTE, now);
        assertEquals(2, home.rows().size());
    }

    @Test
    void powerIsAveragedPerTickAndSatisfactionIsSuppliedOverDemand() {
        ProductionStats stats = new ProductionStats();
        long now = 0;
        for (int s = 1; s <= 60; s++) {
            now = s * 20L;
            // 每秒：发电能力 20 × 30 J，需求 20 × 40 J，只供上 20 × 30 J，蓄电器充了 20 × 5 J
            stats.power(HOME, 600, 800, 600, 100, now);
        }
        Snapshot snapshot = stats.snapshot(HOME, Range.MINUTE, now);
        assertEquals(30, snapshot.generation(), 1.5);
        assertEquals(40, snapshot.demand(), 2);
        assertEquals(5, snapshot.charging(), 0.3);
        assertEquals(0.75, snapshot.satisfaction(), 1e-9);
        assertEquals(1, stats.snapshot(MOON, Range.MINUTE, now).satisfaction());
    }

    @Test
    void seriesSurviveARoundTrip() {
        ProductionStats.Series series = new ProductionStats.Series();
        for (long t = 0; t < 5000; t += 20) {
            series.add(3, t);
        }
        ProductionStats.Series copy = ProductionStats.Series.restore(series.shortStamps(), series.shortValues(), series.longStamps(),
                series.longValues());
        for (Range range : Range.values()) {
            assertEquals(series.sum(range, 4980), copy.sum(range, 4980));
        }
    }
}
