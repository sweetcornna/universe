package io.github.sweetcornna.universe.core.sky;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

class RingAxisTest {
    @Test
    void ringsAlwaysFaceTheStarAtSomeAngle() {
        Rng rng = Rng.of(42);
        for (int i = 0; i < 2000; i++) {
            double tilt = Math.toRadians(rng.range(0, 40));
            double azimuth = rng.range(0, Math.PI * 2);
            double up = rng.nextDouble() < 0.5 ? -1 : 1;
            double[] axis = {Math.sin(tilt) * Math.cos(azimuth), up * Math.cos(tilt), Math.sin(tilt) * Math.sin(azimuth)};
            double sunAngle = rng.range(0, Math.PI * 2);
            double[] sun = {Math.cos(sunAngle), 0, Math.sin(sunAngle)};
            double[] lit = RingAxis.lit(axis, sun);
            assertEquals(1, Math.sqrt(lit[0] * lit[0] + lit[1] * lit[1] + lit[2] * lit[2]), 1e-9);
            assertEquals(Math.signum(axis[1]), Math.signum(lit[1]));
            // 恒星离环面的角度 = 90° − 自转轴和恒星方向的夹角
            double elevation = Math.toDegrees(Math.asin(Math.abs(lit[0] * sun[0] + lit[2] * sun[2])));
            double minimum = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(RingAxis.MIN_TILT_DEGREES))
                    * Math.cos(Math.toRadians(RingAxis.MAX_TURN_DEGREES))));
            assertTrue(elevation >= minimum - 1e-6, "elevation " + elevation);
        }
    }

    @Test
    void axisAlreadyTowardTheStarIsKept() {
        double tilt = Math.toRadians(25);
        double[] axis = {Math.sin(tilt), Math.cos(tilt), 0};
        assertArrayEquals(axis, RingAxis.lit(axis, new double[] {-1, 0, 0}), 1e-12);
        assertArrayEquals(axis, RingAxis.lit(axis, new double[] {Math.cos(0.3), 0, Math.sin(0.3)}), 1e-12);
    }
}
