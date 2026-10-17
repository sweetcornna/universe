package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LaserHeatTest {
    @Test
    void overheatsAfterFourSecondsAndCoolsForTwo() {
        LaserHeat laser = new LaserHeat();
        int fired = 0;
        while (laser.tick(true)) {
            fired++;
            if (laser.overheated()) {
                break;
            }
        }
        assertEquals(80, fired);
        assertTrue(laser.overheated());
        // 过热时按着也不开火，强制冷却 40 刻
        int locked = 0;
        while (!laser.tick(true)) {
            locked++;
        }
        assertEquals(40, locked);
        assertFalse(laser.overheated());
    }

    @Test
    void shortBurstsCoolAfterAShortDelay() {
        LaserHeat laser = new LaserHeat();
        for (int i = 0; i < 20; i++) {
            laser.tick(true);
        }
        assertEquals(25, laser.heat(), 1e-4);
        for (int i = 0; i < LaserHeat.COOL_DELAY; i++) {
            laser.tick(false);
        }
        assertEquals(25, laser.heat(), 1e-4);
        for (int i = 0; i < 10; i++) {
            laser.tick(false);
        }
        assertEquals(0, laser.heat(), 1e-4);
        // 隔一会儿点射一次能一直开下去
        int fired = 0;
        for (int i = 0; i < 1800; i++) {
            if (laser.tick(i % 30 < 10)) {
                fired++;
            }
        }
        assertEquals(600, fired);
        assertFalse(laser.overheated());
    }
}
