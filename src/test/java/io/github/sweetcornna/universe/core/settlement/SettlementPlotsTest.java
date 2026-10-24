package io.github.sweetcornna.universe.core.settlement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkType;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;

/** 接管以后的 8 块地基和路（ADR-0034 §二）。 */
class SettlementPlotsTest {
    private static Landmark settlement(long seed, int rotation) {
        return new Landmark(LandmarkType.SETTLEMENT, 100, -40, 0, 0, 0, seed, rotation);
    }

    @Test
    void eightPlotsInTheRing() {
        assertEquals(8, SettlementLayout.plotCount());
        for (int i = 0; i < SettlementLayout.plotCount(); i++) {
            int[] c = SettlementLayout.plot(i);
            double d = Math.hypot(c[0], c[1]);
            assertTrue(d >= 16 && d <= 30, "地基 " + i + " 离中心 " + d);
        }
    }

    @Test
    void plotsDoNotOverlapEachOtherOrTheVillage() {
        int half = SettlementLayout.PLOT_HALF;
        for (int i = 0; i < SettlementLayout.plotCount(); i++) {
            int[] a = SettlementLayout.plot(i);
            for (int j = i + 1; j < SettlementLayout.plotCount(); j++) {
                int[] b = SettlementLayout.plot(j);
                assertTrue(Math.abs(a[0] - b[0]) > 2 * half || Math.abs(a[1] - b[1]) > 2 * half, i + " 和 " + j + " 重叠");
            }
            // 离小屋（|x| ≤ 11）、货摊（z ≤ 10）、环路（≤ 14）都有空
            assertTrue(Math.max(Math.abs(a[0]), Math.abs(a[1])) - half > SettlementLayout.RING_OUTER, "地基 " + i + " 压到环路");
        }
    }

    @Test
    void buildingsFaceTheRing() {
        for (int i = 0; i < SettlementLayout.plotCount(); i++) {
            int[] c = SettlementLayout.plot(i);
            int[] door = SettlementLayout.plotLocal(i, 0, -SettlementLayout.PLOT_HALF);
            // 门比中心离聚落中间近
            assertTrue(Math.hypot(door[0], door[1]) < Math.hypot(c[0], c[1]), "地基 " + i + " 的门朝外");
        }
    }

    @Test
    void plotAtFindsThePlot() {
        for (int i = 0; i < SettlementLayout.plotCount(); i++) {
            int[] c = SettlementLayout.plot(i);
            assertEquals(i, SettlementLayout.plotAt(c[0], c[1]));
            assertEquals(i, SettlementLayout.plotAt(c[0] + SettlementLayout.PLOT_HALF, c[1] - SettlementLayout.PLOT_HALF));
        }
        assertEquals(-1, SettlementLayout.plotAt(0, 0));
    }

    @Test
    void localUndoesWorld() {
        for (int rotation = 0; rotation < 4; rotation++) {
            Landmark landmark = settlement(5, rotation);
            for (int[] p : List.of(new int[] {3, -7}, new int[] {-20, 17}, new int[] {0, 0})) {
                int[] world = SettlementLayout.world(landmark, p[0], p[1]);
                assertArrayEquals(p, SettlementLayout.local(landmark, world[0], world[1]));
            }
        }
    }

    @Test
    void roadsReachEveryDoorAndAvoidHuts() {
        for (long seed = 0; seed < 8; seed++) {
            Landmark landmark = settlement(seed, 0);
            List<int[]> roads = SettlementLayout.roads(landmark);
            for (int[] cell : roads) {
                for (int[] hut : SettlementLayout.huts(landmark)) {
                    assertFalse(Math.abs(cell[0] - hut[0]) <= SettlementLayout.HUT_HALF && Math.abs(cell[1] - hut[1]) <= SettlementLayout.HUT_HALF,
                            "路压到小屋");
                }
                assertEquals(-1, SettlementLayout.plotAt(cell[0], cell[1]));
            }
            for (int i = 0; i < SettlementLayout.plotCount(); i++) {
                int[] door = SettlementLayout.plotLocal(i, 0, -SettlementLayout.PLOT_HALF - 1);
                assertTrue(roads.stream().anyMatch(c -> c[0] == door[0] && c[1] == door[1]), "地基 " + i + " 门口没有路");
            }
        }
    }
}
