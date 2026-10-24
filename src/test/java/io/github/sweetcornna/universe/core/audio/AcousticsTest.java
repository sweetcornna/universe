package io.github.sweetcornna.universe.core.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AcousticsTest {
    @Test
    void earthLikeAirChangesNothing() {
        assertSame(Acoustics.Filter.NONE, Acoustics.filter(1.0, 30, false, false));
        assertSame(Acoustics.Filter.NONE, Acoustics.filter(Acoustics.FULL_PRESSURE, 30, false, false));
    }

    @Test
    void vacuumIsTenDecibelsDownAndMuffled() {
        Acoustics.Filter f = Acoustics.filter(0, 10, false, false);
        assertEquals(-10, 20 * Math.log10(f.gain()), 0.05);
        assertEquals(Acoustics.VACUUM_HIGHS, f.highs(), 1e-9);
    }

    @Test
    void contactSoundsPassEvenInVacuum() {
        assertSame(Acoustics.Filter.NONE, Acoustics.filter(0, 1.5, false, false));
        assertSame(Acoustics.Filter.NONE, Acoustics.filter(0, 12, true, false));
    }

    @Test
    void marsIsAboutSevenDecibelsDownAndLosesHighsWithDistance() {
        double mars = 0.006;
        assertEquals(0.29, Acoustics.medium(mars), 0.01);
        Acoustics.Filter near = Acoustics.filter(mars, 4, false, false);
        Acoustics.Filter far = Acoustics.filter(mars, 16, false, false);
        assertEquals(-7, 20 * Math.log10(near.gain()), 0.5);
        assertEquals(near.gain(), far.gain(), 1e-9);
        assertTrue(far.highs() < near.highs());
        assertEquals(0.2, far.highs(), 0.05);
    }

    @Test
    void thinnerAirIsQuieter() {
        double previous = 0;
        for (double p : new double[]{0, 0.002, 0.01, 0.05, 0.2, 0.5}) {
            double gain = Acoustics.filter(p, 8, false, false).gain();
            assertTrue(gain >= previous, "pressure " + p);
            previous = gain;
        }
        assertEquals(1, previous, 1e-9);
    }

    @Test
    void underwaterCutsHighsOnly() {
        Acoustics.Filter f = Acoustics.filter(1.0, 5, false, true);
        assertEquals(1, f.gain(), 1e-9);
        assertEquals(Acoustics.UNDERWATER_HIGHS, f.highs(), 1e-9);
        Acoustics.Filter both = Acoustics.filter(0, 5, false, true);
        assertEquals(Acoustics.VACUUM_HIGHS * Acoustics.UNDERWATER_HIGHS, both.highs(), 1e-9);
    }
}
