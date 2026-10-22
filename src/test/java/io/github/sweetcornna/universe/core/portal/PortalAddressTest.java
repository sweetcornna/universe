package io.github.sweetcornna.universe.core.portal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

class PortalAddressTest {
    @Test
    void everyPlanetRoundTripsAndAddressesAreUnique() {
        long seed = 123456789L;
        Set<String> seen = new HashSet<>();
        for (int system = 0; system < 64; system++) {
            for (int planet = 0; planet < 8; planet++) {
                for (int moon = -1; moon < 3; moon++) {
                    PortalAddress address = new PortalAddress(system, planet, moon);
                    int[] glyphs = address.glyphs(seed);
                    assertEquals(PortalAddress.LENGTH, glyphs.length);
                    assertEquals(Optional.of(address), PortalAddress.decode(seed, glyphs));
                    assertTrue(seen.add(java.util.Arrays.toString(glyphs)));
                }
            }
        }
    }

    @Test
    void addressesDependOnTheCluster() {
        PortalAddress address = new PortalAddress(4, 2, -1);
        assertNotEquals(java.util.Arrays.toString(address.glyphs(1)), java.util.Arrays.toString(address.glyphs(2)));
    }

    @Test
    void randomDialsRarelyConnect() {
        Rng rng = Rng.of(9);
        int hits = 0;
        for (int i = 0; i < 20000; i++) {
            int[] glyphs = new int[PortalAddress.LENGTH];
            for (int j = 0; j < glyphs.length; j++) {
                glyphs[j] = rng.nextInt(PortalAddress.GLYPHS);
            }
            if (PortalAddress.decode(77, glyphs).isPresent()) {
                hits++;
            }
        }
        assertTrue(hits < 20000 / 100, "hits " + hits);
    }

    @Test
    void glyphsAreLearnedOneByOne() {
        Rng rng = Rng.of(1);
        int known = 0;
        for (int i = 0; i < PortalAddress.GLYPHS; i++) {
            int glyph = Glyphs.next(known, rng);
            assertFalse(Glyphs.knows(known, glyph));
            known |= 1 << glyph;
        }
        assertEquals(Glyphs.ALL, known);
        assertEquals(-1, Glyphs.next(known, rng));
        int[] glyphs = new PortalAddress(1, 1, -1).glyphs(5);
        assertTrue(Glyphs.canDial(Glyphs.ALL, glyphs));
        assertFalse(Glyphs.canDial(0, glyphs));
    }

    @Test
    void portalSitsAwayFromTheLandingColumn() {
        for (long seed = 0; seed < 200; seed++) {
            int[] offset = Glyphs.offset(seed);
            double d = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
            assertTrue(d >= Glyphs.MIN_DISTANCE - 1 && d <= Glyphs.MAX_DISTANCE + 1);
        }
        assertArrayEquals(Glyphs.offset(3), Glyphs.offset(3));
    }
}
