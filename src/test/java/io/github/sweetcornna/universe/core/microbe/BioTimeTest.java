package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BioTimeTest {
    @Test
    void oneRealHourIsSixGameSeconds() {
        assertEquals(120, BioTime.TICKS_PER_REAL_HOUR, 1e-12);
        assertEquals(24 * 120, BioTime.ticks(24), 1e-12);
        assertEquals(1, BioTime.hours(120), 1e-12);
    }

    @Test
    void cardinalModelPeaksAtTheOptimum() {
        assertEquals(1, MicrobeStrain.cardinal(283, 305, 315, 305), 1e-12);
        assertEquals(0, MicrobeStrain.cardinal(283, 305, 315, 283));
        assertEquals(0, MicrobeStrain.cardinal(283, 305, 315, 315));
        assertEquals(0, MicrobeStrain.cardinal(283, 305, 315, 400));
        // 往最高温度那一侧掉得陡：离最适同样 5 K，高的一侧长得慢
        assertTrue(MicrobeStrain.cardinal(283, 305, 315, 310) < MicrobeStrain.cardinal(283, 305, 315, 300));
        // 单峰：从最低温度到最适一直升，再一直降
        double previous = 0;
        for (double t = 283.5; t <= 305; t += 0.5) {
            double g = MicrobeStrain.cardinal(283, 305, 315, t);
            assertTrue(g >= previous - 1e-12, "at " + t);
            previous = g;
        }
        for (double t = 305.5; t < 315; t += 0.5) {
            double g = MicrobeStrain.cardinal(283, 305, 315, t);
            assertTrue(g <= previous + 1e-12, "at " + t);
            previous = g;
        }
    }
}
