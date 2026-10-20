package io.github.sweetcornna.universe.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PerfStatsTest {
    @Test
    void reportAveragesPerTickAndSortsBySlowest() {
        PerfStats stats = new PerfStats();
        for (int tick = 0; tick < 4; tick++) {
            stats.add("belts", 1_000_000);
            stats.add("machines", 250_000);
            stats.add("machines", 250_000);
            stats.tick(10_000_000);
        }
        stats.add("power", 4_000_000);
        List<PerfStats.Line> report = stats.report();
        assertEquals(List.of("belts", "power", "machines"), report.stream().map(PerfStats.Line::section).toList());
        PerfStats.Line belts = report.get(0);
        assertEquals(1.0, belts.msPerTick(), 1e-9);
        assertEquals(1.0, belts.callsPerTick(), 1e-9);
        assertEquals(0.1, belts.share(), 1e-9);
        PerfStats.Line machines = report.get(2);
        assertEquals(0.5, machines.msPerTick(), 1e-9);
        assertEquals(2.0, machines.callsPerTick(), 1e-9);
        assertEquals(10.0, stats.serverMsPerTick(), 1e-9);
        assertEquals(2.5, stats.totalMsPerTick(), 1e-9);
    }

    @Test
    void emptyStatsReportNothing() {
        PerfStats stats = new PerfStats();
        stats.add("belts", 5);
        assertTrue(stats.report().isEmpty());
        assertEquals(0, stats.serverMsPerTick());
        assertEquals(0, stats.totalMsPerTick());
    }
}
