package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BallisticsTest {
    @Test
    void landsOnTheTarget() {
        double[][] targets = {{30, -2, 10}, {5, 0, 0}, {-12, 6, 25}, {0, -10, 39}};
        for (double[] t : targets) {
            int ticks = Ballistics.flightTicks(Math.hypot(t[0], t[2]), 1.1);
            double[] v = Ballistics.launchVelocity(t[0], t[1], t[2], ticks, Ballistics.GRAVITY);
            double[] p = Ballistics.displacement(v[0], v[1], v[2], ticks, Ballistics.GRAVITY);
            for (int i = 0; i < 3; i++) {
                assertEquals(t[i], p[i], 1e-9);
            }
            // 往上射出去，走弧线
            assertTrue(v[1] > 0, "没往上射：" + v[1]);
        }
    }

    @Test
    void flightTimeIsClamped() {
        assertEquals(Ballistics.MIN_TICKS, Ballistics.flightTicks(1, 1.1));
        assertEquals(Ballistics.MAX_TICKS, Ballistics.flightTicks(1000, 1.1));
        assertEquals(28, Ballistics.flightTicks(30, 1.1));
    }
}
