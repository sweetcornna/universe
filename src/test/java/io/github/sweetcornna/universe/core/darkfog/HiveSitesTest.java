package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.BlackHoles;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;

class HiveSitesTest {
    private static List<double[]> avoid(StarSystem system) {
        List<double[]> avoid = new ArrayList<>();
        StationLayout.site(system).ifPresent(s -> avoid.add(new double[] {s.x(), s.y(), s.z()}));
        BlackHoles.in(system).ifPresent(hole -> avoid.add(new double[] {hole.x(), hole.y(), hole.z()}));
        return avoid;
    }

    private static HiveSites.Site site(StarSystem system) {
        return HiveSites.site(system.seed(), SpaceLayout.starRadius(system), SpaceLayout.of(system), avoid(system));
    }

    @Test
    void sitesAreDeterministicAndClearOfEverything() {
        for (long seed = 1; seed <= 3; seed++) {
            Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(seed, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
            for (StarSystem system : galaxy.systems()) {
                HiveSites.Site site = site(system);
                assertEquals(site, site(system), "same seed, same site");
                double fromStar = Math.hypot(site.x(), site.z());
                assertTrue(fromStar >= SpaceLayout.starRadius(system) + HiveSites.STAR_CLEARANCE - 1, "too close to the star in " + system.index());
                assertTrue(Math.abs(site.y()) <= HiveSites.MAX_HEIGHT, "off the ecliptic in " + system.index());
                for (SpaceLayout.Body body : SpaceLayout.of(system)) {
                    assertTrue(body.distanceTo(site.x(), site.y(), site.z()) >= body.radius() + HiveSites.CLEARANCE,
                            "inside " + body.id().path() + "'s clearance");
                }
                for (double[] point : avoid(system)) {
                    assertTrue(site.distanceTo(point[0], point[1], point[2]) >= HiveSites.CLEARANCE, "too close to the station or a black hole");
                }
            }
        }
    }

    @Test
    void mostSitesAreWithinPulseRange() {
        Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(7, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        long near = galaxy.systems().stream().filter(system -> Math.hypot(site(system).x(), site(system).z()) <= HiveSites.MAX_DISTANCE + 8000)
                .count();
        assertTrue(near >= galaxy.systems().size() * 0.9, "only " + near + " hives within reach");
    }

    @Test
    void hivesFollowGroundBases() {
        Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(11, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        int seeded = 0;
        for (StarSystem system : galaxy.systems()) {
            List<BaseSites.Body> bodies = new ArrayList<>();
            for (Planet planet : system.bodies()) {
                if (planet.landable() && !planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                    bodies.add(new BaseSites.Body(planet.id().path(), planet.seed(), planet.worldSize()));
                }
            }
            boolean home = system.index() == GalaxyGenerator.HOME_SYSTEM;
            boolean hive = HiveSites.seeded(bodies, home, system.seed(), 1);
            int bases = BaseSites.counts(bodies, home, system.seed(), 1).values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(bases > 0, hive);
            assertFalse(HiveSites.seeded(bodies, home, system.seed(), 0), "no occupation, no hives");
            if (home && !bodies.isEmpty()) {
                assertTrue(hive, "the home system always has one at 100%");
            }
            seeded += hive ? 1 : 0;
        }
        assertTrue(seeded > 0 && seeded < galaxy.systems().size(), "some systems have hives, some don't: " + seeded);
    }
}
