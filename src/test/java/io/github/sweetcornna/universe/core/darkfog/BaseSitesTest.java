package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BaseSitesTest {
    private static List<BaseSites.Body> bodies(int n, int worldSize) {
        List<BaseSites.Body> bodies = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            bodies.add(new BaseSites.Body("s0/p" + i, 1000L * i + 7, worldSize));
        }
        return bodies;
    }

    @Test
    void countsAreBoundedAndGrowWithOccupation() {
        int low = 0;
        int high = 0;
        for (long seed = 0; seed < 500; seed++) {
            int none = BaseSites.count(seed, 0);
            int normal = BaseSites.count(seed, 1);
            int dense = BaseSites.count(seed, 3);
            assertEquals(0, none);
            assertTrue(normal <= BaseSites.MAX_PER_BODY);
            assertTrue(dense >= normal);
            low += normal;
            high += dense;
        }
        assertTrue(high > low * 2, low + " vs " + high);
    }

    @Test
    void homeSystemAlwaysHasABaseAtFullOccupation() {
        for (long system = 0; system < 200; system++) {
            Map<String, Integer> counts = BaseSites.counts(bodies(2, 10_240), true, system, 1);
            assertTrue(counts.values().stream().mapToInt(Integer::intValue).sum() >= 1, "system " + system);
        }
        Map<String, Integer> none = BaseSites.counts(bodies(3, 10_240), true, 1, 0);
        assertEquals(0, none.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void sitesStayInsideTheBorderAndApart() {
        for (long seed = 0; seed < 100; seed++) {
            for (int size : new int[] {2048, 5120, 10_240, 30_208}) {
                BaseSites.Body body = new BaseSites.Body("s1/p0", seed, size);
                List<BaseSites.Site> sites = BaseSites.place(body, 3, 1);
                double half = size / 2.0 - BaseLayout.RADIUS;
                for (BaseSites.Site site : sites) {
                    double r = Math.hypot(site.x(), site.z());
                    assertTrue(Math.abs(site.x()) < half && Math.abs(site.z()) < half, site + " in " + size);
                    if (size >= 10_240) {
                        assertTrue(r >= BaseSites.MIN_DISTANCE - 1 && r <= BaseSites.MAX_DISTANCE + 1, site + " r=" + r);
                    }
                    for (BaseSites.Site other : sites) {
                        if (other != site) {
                            assertTrue(Math.hypot(other.x() - site.x(), other.z() - site.z()) >= Math.min(BaseSites.SPACING, 200));
                        }
                    }
                }
            }
        }
    }

    @Test
    void placementIsDeterministic() {
        BaseSites.Body body = new BaseSites.Body("s2/p1", 123456789L, 12_288);
        assertEquals(BaseSites.place(body, 2, 1), BaseSites.place(body, 2, 1));
    }
}
