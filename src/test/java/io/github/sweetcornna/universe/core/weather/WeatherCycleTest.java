package io.github.sweetcornna.universe.core.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.procgen.Rng;

class WeatherCycleTest {
    @Test
    void activeFractionMatchesFrequency() {
        for (double frequency : new double[] {0.1, 0.3, 0.6}) {
            WeatherProfile profile = new WeatherProfile(WeatherProfile.Precipitation.RAIN, frequency, 0.3, 0.5, true);
            Rng rng = Rng.of(7);
            WeatherCycle cycle = WeatherCycle.start(profile, rng::nextDouble);
            long active = 0;
            long total = 20_000_000;
            for (long t = 0; t < total; t++) {
                cycle.tick(profile, rng::nextDouble);
                if (cycle.active()) {
                    active++;
                }
            }
            assertEquals(frequency, (double) active / total, 0.05, "frequency " + frequency);
        }
    }

    @Test
    void calmPlanetsNeverChange() {
        Rng rng = Rng.of(1);
        WeatherCycle cycle = new WeatherCycle(true, true, 100);
        assertTrue(cycle.tick(WeatherProfile.CALM, rng::nextDouble));
        for (int t = 0; t < 100_000; t++) {
            assertFalse(cycle.tick(WeatherProfile.CALM, rng::nextDouble));
        }
        assertFalse(cycle.active());
        assertEquals(0, cycle.targetIntensity(WeatherProfile.CALM));
    }

    @Test
    void dustStormsAreStrongerThanDust() {
        WeatherProfile dust = new WeatherProfile(WeatherProfile.Precipitation.DUST, 0.4, 0.5, 0.8, false);
        WeatherCycle cycle = new WeatherCycle(false, false, 10);
        cycle.force(true, false, 100);
        float normal = cycle.targetIntensity(dust);
        cycle.force(true, true, 100);
        assertTrue(cycle.targetIntensity(dust) > normal);
        assertFalse(cycle.thundering(dust));
    }

    @Test
    void approachMovesOneStep() {
        assertEquals(0.01F, WeatherCycle.approach(0, 1), 1e-6);
        assertEquals(0.55F, WeatherCycle.approach(0.555F, 0.55F), 1e-6);
        assertEquals(1F, WeatherCycle.approach(1, 1));
    }
}
