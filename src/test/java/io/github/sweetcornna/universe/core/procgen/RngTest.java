package io.github.sweetcornna.universe.core.procgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RngTest {
    @Test
    void sameSeedGivesSameSequence() {
        Rng a = Rng.of(123);
        Rng b = Rng.of(123);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
    }

    @Test
    void nextIntStaysInBoundsAndIsRoughlyUniform() {
        Rng rng = Rng.of(7);
        int[] counts = new int[10];
        for (int i = 0; i < 100_000; i++) {
            int value = rng.nextInt(10);
            assertTrue(value >= 0 && value < 10);
            counts[value]++;
        }
        for (int count : counts) {
            assertTrue(count > 9_500 && count < 10_500, "分布不均匀：" + count);
        }
    }

    @Test
    void rangesIncludeBothEndsForIntegers() {
        Rng rng = Rng.of(99);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 1000; i++) {
            int value = rng.range(3, 5);
            assertTrue(value >= 3 && value <= 5);
            sawMin |= value == 3;
            sawMax |= value == 5;
        }
        assertTrue(sawMin && sawMax);
    }

    @Test
    void doublesStayInUnitInterval() {
        Rng rng = Rng.of(5);
        double sum = 0;
        for (int i = 0; i < 100_000; i++) {
            double value = rng.nextDouble();
            assertTrue(value >= 0 && value < 1);
            sum += value;
        }
        assertEquals(0.5, sum / 100_000, 0.01);
    }

    @Test
    void gaussianHasUnitVariance() {
        Rng rng = Rng.of(11);
        double sum = 0;
        double sumSquares = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            double value = rng.gaussian();
            sum += value;
            sumSquares += value * value;
        }
        double mean = sum / n;
        assertEquals(0, mean, 0.02);
        assertEquals(1, sumSquares / n - mean * mean, 0.03);
    }

    @Test
    void weightedPickRespectsWeights() {
        Rng rng = Rng.of(3);
        List<String> options = List.of("a", "b", "never");
        int a = 0;
        for (int i = 0; i < 30_000; i++) {
            String pick = rng.weighted(options, s -> switch (s) {
                case "a" -> 3;
                case "b" -> 1;
                default -> 0;
            });
            assertTrue(!pick.equals("never"));
            if (pick.equals("a")) {
                a++;
            }
        }
        assertEquals(0.75, a / 30_000.0, 0.02);
    }

    /** 程序生成依赖这些值，变了就意味着所有星球都会变样。 */
    @Test
    void sequenceIsStableAcrossVersions() {
        Rng rng = Rng.of(20261016L);
        assertEquals(0x3F5AE038295733CBL, rng.nextLong());
        assertEquals(0x8145D6315E1361C5L, rng.nextLong());
    }
}
