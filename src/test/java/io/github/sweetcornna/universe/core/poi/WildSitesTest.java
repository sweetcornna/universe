package io.github.sweetcornna.universe.core.poi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

/** 野外地标（ADR-0026）：化石坑的化石土、雷击石的管子。 */
class WildSitesTest {
    @Test
    void pitIsTerracedSevenBySeven() {
        assertEquals(3, FossilPit.depth(0, 0));
        assertEquals(3, FossilPit.depth(1, -1));
        assertEquals(2, FossilPit.depth(-2, 1));
        assertEquals(1, FossilPit.depth(3, 0));
        assertEquals(1, FossilPit.depth(-3, -3));
        assertEquals(0, FossilPit.depth(4, 0));
        assertEquals(0, FossilPit.depth(0, -4));
    }

    @Test
    void fossilsLieOnThePitFloor() {
        int total = 0;
        int deep = 0;
        for (long seed = 0; seed < 500; seed++) {
            List<FossilPit.Cell> cells = FossilPit.layout(seed);
            assertTrue(cells.size() >= FossilPit.MIN_FOSSILS && cells.size() <= FossilPit.MAX_FOSSILS, "seed " + seed);
            assertEquals(cells.size(), new HashSet<>(cells).size(), "seed " + seed);
            for (FossilPit.Cell cell : cells) {
                assertTrue(Math.abs(cell.dx()) <= FossilPit.HALF && Math.abs(cell.dz()) <= FossilPit.HALF, cell.toString());
                assertEquals(-FossilPit.depth(cell.dx(), cell.dz()), cell.dy(), cell.toString());
                total++;
                deep += cell.dy() == -3 ? 1 : 0;
            }
            assertEquals(cells, FossilPit.layout(seed));
        }
        // 正中 3×3 只占坑底的 9/49，按深度加权以后化石土差不多一半在那里
        assertTrue(deep / (double) total > 0.3, "deep share " + deep / (double) total);
        assertNotEquals(FossilPit.layout(1), FossilPit.layout(2));
    }

    @Test
    void fulguriteTubeStartsAtTheStrikeAndGoesDown() {
        Set<Integer> lengths = new HashSet<>();
        boolean branched = false;
        for (long seed = 0; seed < 2000; seed++) {
            List<int[]> cells = Fulgurite.tube(Rng.of(seed));
            assertArrayEquals(new int[] {0, 0, 0}, cells.get(0));
            int depth = 0;
            for (int i = 1; i < cells.size(); i++) {
                int[] cell = cells.get(i);
                if (cell[0] == 0 && cell[2] == 0) {
                    // 主干一格一格往下
                    assertEquals(-i, cell[1]);
                    depth = -cell[1];
                } else {
                    // 分岔：最后一格，在主干旁边，不比主干深
                    assertEquals(cells.size() - 1, i);
                    assertEquals(1, Math.abs(cell[0]) + Math.abs(cell[2]));
                    assertTrue(cell[1] <= 0 && -cell[1] <= depth);
                    branched = true;
                }
            }
            assertTrue(depth <= Fulgurite.MAX_DEPTH);
            assertTrue(cells.size() <= Fulgurite.MAX_DEPTH + 2);
            lengths.add(depth);
        }
        assertEquals(Set.of(0, 1, 2, 3), lengths);
        assertTrue(branched);
    }

    @Test
    void volcanicStrikesAreRare() {
        // 每个玩家平均 20 秒以上才落一道雷
        assertTrue(1 / Fulgurite.VOLCANIC_STRIKE_CHANCE >= 20);
        assertTrue(Fulgurite.VOLCANIC_MIN_DISTANCE >= 16 && Fulgurite.VOLCANIC_MAX_DISTANCE > Fulgurite.VOLCANIC_MIN_DISTANCE);
    }
}
