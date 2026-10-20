package io.github.sweetcornna.universe.core.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TeleportRulesTest {
    @Test
    void baseNumbersFillTheLowestGap() {
        assertEquals(1, TeleportRules.nextBaseNumber(Set.of()));
        assertEquals(3, TeleportRules.nextBaseNumber(List.of(1, 2)));
        assertEquals(2, TeleportRules.nextBaseNumber(List.of(1, 3, 4)));
        assertEquals(1, TeleportRules.nextBaseNumber(List.of(2)));
    }

    @Test
    void chargingTakesTenSecondsAtFullPower() {
        long stored = 0;
        int ticks = 0;
        while (stored < TeleportRules.CHARGE) {
            stored = TeleportRules.charge(stored, 1.0);
            ticks++;
        }
        assertEquals(200, ticks);
        assertEquals(TeleportRules.CHARGE, stored);
        assertEquals(0, TeleportRules.demand(stored));
    }

    @Test
    void halfPowerChargesHalfAsFastAndNeverOverfills() {
        assertEquals(30, TeleportRules.charge(0, 0.5));
        assertEquals(TeleportRules.CHARGE, TeleportRules.charge(TeleportRules.CHARGE - 10, 1.0));
        assertEquals(10, TeleportRules.demand(TeleportRules.CHARGE - 10));
        assertEquals(0, TeleportRules.charge(0, -1));
    }

    @Test
    void reachIsSixBlocks() {
        assertTrue(TeleportRules.inReach(36));
        assertFalse(TeleportRules.inReach(36.01));
    }
}
