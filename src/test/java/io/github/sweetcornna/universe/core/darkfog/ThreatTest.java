package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThreatTest {
    @Test
    void powerThreatMatchesTheAdr() {
        assertEquals(40, Threat.minutesToFill(25_000), 1e-9);
        assertEquals(10, Threat.minutesToFill(400_000), 1e-9);
        assertEquals(6, Threat.minutesToFill(10_000_000), 1e-9);
        assertTrue(Double.isInfinite(Threat.minutesToFill(0)));
        // 每刻涨的乘 40 分钟正好满
        assertEquals(1, Threat.powerPerTick(25_000, DarkFogSettings.DEFAULT) * 40 * 1200, 1e-9);
    }

    @Test
    void aggressivenessAndFactorsMultiply() {
        DarkFogSettings passive = new DarkFogSettings(true, 0, 0, 1, 1, 1, 1);
        DarkFogSettings rampage = new DarkFogSettings(true, 5, 0, 1, 1, 2, 1);
        assertEquals(0, Threat.powerPerTick(25_000, passive));
        assertEquals(6 * Threat.powerPerTick(25_000, DarkFogSettings.DEFAULT), Threat.powerPerTick(25_000, rampage), 1e-12);
        assertEquals(2 * Threat.buildingPerDamage(BuildingKind.LASER_TOWER, DarkFogSettings.DEFAULT),
                Threat.buildingPerDamage(BuildingKind.CORE, DarkFogSettings.DEFAULT), 1e-12);
    }

    @Test
    void wavesTakeAShareOfTheCamps() {
        assertEquals(Threat.Wave.NONE, Threat.wave(12, 12, 0, DarkFogSettings.PASSIVE));
        Threat.Wave normal = Threat.wave(12, 0, 0, DarkFogSettings.NORMAL);
        assertEquals(6, normal.raiders());
        assertEquals(0, normal.rangers());
        assertEquals(1, Threat.wave(1, 0, 0, 1).raiders());
        assertTrue(Threat.wave(12, 12, 30, 2).total() > Threat.wave(12, 12, 0, 2).total());
        assertEquals(Threat.MAX_PER_KIND, Threat.wave(36, 0, 30, 5).raiders());
    }
}
