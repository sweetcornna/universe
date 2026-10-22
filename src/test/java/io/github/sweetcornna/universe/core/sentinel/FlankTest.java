package io.github.sweetcornna.universe.core.sentinel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FlankTest {
    @Test
    void facingSouthTheBackIsNorth() {
        // 偏航 0° 朝南（+z）：北边（-z）是背后，南边是正面，东西两侧不算
        assertTrue(Flank.fromBehind(0, 0, -3));
        assertFalse(Flank.fromBehind(0, 0, 3));
        assertFalse(Flank.fromBehind(0, 3, 0));
        assertFalse(Flank.fromBehind(0, -3, 0));
    }

    @Test
    void theRearArcIsSixtyDegreesEachSide() {
        // 正后方偏 55° 还算背后，偏 65° 不算
        double in = Math.toRadians(180 - 55);
        double out = Math.toRadians(180 - 65);
        assertTrue(Flank.fromBehind(0, -Math.sin(in), Math.cos(in)));
        assertTrue(Flank.fromBehind(0, Math.sin(in), Math.cos(in)));
        assertFalse(Flank.fromBehind(0, -Math.sin(out), Math.cos(out)));
        assertFalse(Flank.fromBehind(0, Math.sin(out), Math.cos(out)));
    }

    @Test
    void followsTheBodyYaw() {
        // 偏航 90° 朝西（-x）：东边（+x）是背后
        assertTrue(Flank.fromBehind(90, 4, 0));
        assertFalse(Flank.fromBehind(90, -4, 0));
        // 偏航 -90° 和 270° 一样朝东
        assertTrue(Flank.fromBehind(-90, -4, 0));
        assertTrue(Flank.fromBehind(270, -4, 0));
        // 偏航 180° 朝北：南边是背后
        assertTrue(Flank.fromBehind(180, 0.5, 6));
    }

    @Test
    void distanceDoesNotMatterAndTheCentreIsNotBehind() {
        assertTrue(Flank.fromBehind(0, 0, -0.01));
        assertTrue(Flank.fromBehind(0, 0, -500));
        assertFalse(Flank.fromBehind(0, 0, 0));
    }

    @Test
    void damageDoublesOnlyFromBehind() {
        assertEquals(16F, Flank.damage(8, 0, 0, -2));
        assertEquals(8F, Flank.damage(8, 0, 0, 2));
        assertEquals(8F, Flank.damage(8, 0, 2, 0));
    }
}
