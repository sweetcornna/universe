package io.github.sweetcornna.universe.core.chem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 大气提取器（ADR-0036 §三）。 */
class ExtractionTest {
    @Test
    void earthAirAtOneAtmosphere() {
        AtmosphereMix air = AtmosphereMixes.earth();
        // 40 × √1 × √0.78 ≈ 35；氧 40 × √0.21 ≈ 18；氩 40 × √0.0093 ≈ 3.9
        assertEquals(35.3, Extraction.perMinute(air, Gas.NITROGEN, 1), 0.1);
        assertEquals(18.3, Extraction.perMinute(air, Gas.OXYGEN, 1), 0.1);
        assertEquals(3.87, Extraction.perMinute(air, Gas.ARGON, 1), 0.02);
        // 二氧化碳只有万分之四，不到千分之一，选不了
        assertEquals(0, Extraction.perMinute(air, Gas.CARBON_DIOXIDE, 1));
        assertEquals(List.of(Gas.NITROGEN, Gas.OXYGEN, Gas.ARGON), Extraction.collectable(air));
    }

    @Test
    void thinMartianAirIsSlow() {
        AtmosphereMix mars = AtmosphereMix.of(java.util.Map.of(Gas.CARBON_DIOXIDE, 0.95, Gas.NITROGEN, 0.03, Gas.ARGON, 0.02));
        // 0.1 个大气压：40 × 0.316 × 0.975 ≈ 12.3
        assertEquals(12.3, Extraction.perMinute(mars, Gas.CARBON_DIOXIDE, 0.1), 0.1);
        assertEquals(1200 / 12.33, Extraction.ticksPerItem(mars, Gas.CARBON_DIOXIDE, 0.1), 0.5);
        assertEquals(Double.POSITIVE_INFINITY, Extraction.ticksPerItem(mars, Gas.OXYGEN, 0.1));
    }

    @Test
    void densePressureIsCapped() {
        AtmosphereMix venus = AtmosphereMix.of(java.util.Map.of(Gas.CARBON_DIOXIDE, 0.965, Gas.NITROGEN, 0.035));
        assertEquals(Extraction.perMinute(venus, Gas.CARBON_DIOXIDE, 10), Extraction.perMinute(venus, Gas.CARBON_DIOXIDE, 50));
    }

    @Test
    void thinnerAirCostsMorePower() {
        assertEquals(Extraction.BASE_POWER, Extraction.power(1));
        assertEquals(Extraction.BASE_POWER, Extraction.power(5));
        assertTrue(Extraction.power(0.1) > Extraction.power(0.5));
        assertEquals(24 * (1 + 0.4 * Math.log(10)), Extraction.power(0.1), 1e-9);
        assertEquals(24 * 3, Extraction.power(0.001), 1e-9);
        assertEquals(24 * 3, Extraction.power(0), 1e-9);
    }

    @Test
    void emptyAirGivesNothing() {
        assertTrue(Extraction.collectable(AtmosphereMix.EMPTY).isEmpty());
        assertFalse(Extraction.perMinute(AtmosphereMix.EMPTY, Gas.NITROGEN, 0) > 0);
    }
}
