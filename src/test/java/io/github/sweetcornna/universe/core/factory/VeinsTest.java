package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VeinsTest {
    @Test
    void reserveIsDeterministicAndInRange() {
        int first = Veins.initialReserve(42, 10, 64, -7, 1.0);
        assertEquals(first, Veins.initialReserve(42, 10, 64, -7, 1.0));
        for (int x = -50; x < 50; x++) {
            int reserve = Veins.initialReserve(42, x, 60, x * 3, 1.0);
            assertTrue(reserve >= Veins.MIN_RESERVE && reserve <= Veins.MAX_RESERVE, String.valueOf(reserve));
        }
        // 位置或种子不同，储量多半不同
        assertNotEquals(first, Veins.initialReserve(42, 11, 64, -7, 1.0));
        assertNotEquals(first, Veins.initialReserve(43, 10, 64, -7, 1.0));
    }

    @Test
    void richnessScalesReserve() {
        int base = Veins.initialReserve(7, 1, 2, 3, 1.0);
        assertEquals(Math.round(base * 0.5), Veins.initialReserve(7, 1, 2, 3, 0.5), 1);
        assertTrue(Veins.initialReserve(7, 1, 2, 3, 0.0001) >= 1);
    }

    @Test
    void seepDecaysToFloor() {
        double initial = Veins.initialSeepRate(42, 100, 70, 100);
        assertTrue(initial >= Veins.MIN_SEEP_RATE && initial <= Veins.MAX_SEEP_RATE);
        assertEquals(initial, Veins.seepRate(initial, 0), 1e-9);
        assertEquals(initial / Math.E, Veins.seepRate(initial, (long) Veins.SEEP_DECAY), 1e-9);
        assertEquals(initial * Veins.SEEP_FLOOR, Veins.seepRate(initial, 10_000_000), 1e-9);
        assertTrue(Veins.seepRate(initial, 1000) < initial);
    }

    @Test
    void packKeepsNegativeCoordinatesApart() {
        assertNotEquals(Veins.pack(-1, 64, 0), Veins.pack(1, 64, 0));
        assertNotEquals(Veins.pack(0, -1, 0), Veins.pack(0, 1, 0));
        assertNotEquals(Veins.pack(0, 64, -1), Veins.pack(0, 64, 1));
    }
}
