package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpaceFlightTest {
    @Test
    void velocityChangesAtMostAccelPerTick() {
        double[] v = SpaceFlight.approach(new double[] {0, 0, 0}, new double[] {2, 0, 0}, 0.1);
        assertArrayEquals(new double[] {0.1, 0, 0}, v, 1e-9);
        double[] close = SpaceFlight.approach(new double[] {1.95, 0, 0}, new double[] {2, 0, 0}, 0.1);
        assertArrayEquals(new double[] {2, 0, 0}, close, 1e-9);
    }

    @Test
    void turningAroundTakesManyTicks() {
        double[] v = {1.9, 0, 0};
        int ticks = 0;
        while (v[0] > 0 && ticks < 1000) {
            v = SpaceFlight.approach(v, new double[] {-1.9, 0, 0}, SpaceUnitKind.LANCER.accel());
            ticks++;
        }
        assertTrue(ticks > 10, "a lancer can't flip its velocity in one tick: " + ticks);
    }

    @Test
    void seekSlowsDownNearTheGoal() {
        double[] far = SpaceFlight.seek(new double[] {0, 0, 0}, new double[] {100, 0, 0}, 2, 20);
        assertEquals(2, SpaceFlight.length(far), 1e-9);
        double[] near = SpaceFlight.seek(new double[] {0, 0, 0}, new double[] {10, 0, 0}, 2, 20);
        assertEquals(1, SpaceFlight.length(near), 1e-9);
        assertArrayEquals(new double[3], SpaceFlight.seek(new double[] {5, 5, 5}, new double[] {5, 5, 5}, 2, 20), 1e-9);
    }

    @Test
    void orbitPointsStayOnTheCircle() {
        double[] center = {10, 50, -20};
        for (int i = 0; i < 12; i++) {
            double[] p = SpaceFlight.orbitPoint(center, 40, i * 0.5, 6);
            assertEquals(40, Math.hypot(p[0] - center[0], p[2] - center[2]), 1e-9);
            assertTrue(Math.abs(p[1] - center[1]) <= 6 + 1e-9);
        }
    }

    @Test
    void standoffBacksOffClosesInAndCirclesBetween() {
        double[] target = {0, 0, 0};
        double[] tooClose = SpaceFlight.standoff(new double[] {10, 0, 0}, target, 28, 48, 0.5, 1);
        assertTrue(tooClose[0] > 0, "moves away from the target");
        double[] tooFar = SpaceFlight.standoff(new double[] {80, 0, 0}, target, 28, 48, 0.5, 1);
        assertTrue(tooFar[0] < 0, "moves towards the target");
        double[] between = SpaceFlight.standoff(new double[] {38, 0, 0}, target, 28, 48, 0.5, 1);
        assertEquals(0, between[0], 1e-6);
        assertEquals(0.5, Math.abs(between[2]), 1e-6);
        double[] other = SpaceFlight.standoff(new double[] {38, 0, 0}, target, 28, 48, 0.5, -1);
        assertEquals(-between[2], other[2], 1e-6);
        for (double[] v : new double[][] {tooClose, tooFar, between}) {
            assertEquals(0.5, SpaceFlight.length(v), 1e-9);
        }
    }

    @Test
    void yawAndPitchFollowTheMinecraftConvention() {
        assertEquals(0, SpaceFlight.yaw(new double[] {0, 0, 1}), 1e-4);
        assertEquals(90, SpaceFlight.yaw(new double[] {-1, 0, 0}), 1e-4);
        assertEquals(-90, SpaceFlight.yaw(new double[] {1, 0, 0}), 1e-4);
        assertEquals(45, SpaceFlight.pitch(new double[] {1, -1, 0}), 1e-4, "diving is a positive pitch");
        assertEquals(-90, SpaceFlight.pitch(new double[] {0, 1, 0}), 1e-4);
    }

    @Test
    void coneCheck() {
        assertTrue(SpaceFlight.inCone(new double[] {0, 0, 1}, new double[] {0.1, 0, 1}, 10));
        assertFalse(SpaceFlight.inCone(new double[] {0, 0, 1}, new double[] {1, 0, 1}, 10));
        assertFalse(SpaceFlight.inCone(new double[] {0, 0, 0}, new double[] {1, 0, 1}, 10));
    }
}
