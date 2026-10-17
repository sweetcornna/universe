package io.github.sweetcornna.universe.core.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class SpaceLayoutTest {
    private static final Galaxy GALAXY = GalaxyGenerator.fromGalaxySeed(12345L, 40);

    @Test
    void everyBodyIsPlacedOutsideTheStar() {
        for (StarSystem system : GALAXY.systems()) {
            List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
            assertEquals(system.bodies().size(), bodies.size());
            for (SpaceLayout.Body body : bodies) {
                double d = Math.sqrt(body.x() * body.x() + body.z() * body.z());
                assertTrue(d - body.radius() > SpaceLayout.STAR_RADIUS, body.id() + " overlaps the star");
                assertEquals(0, body.y(), 1e-9);
            }
        }
    }

    @Test
    void planetsDoNotOverlap() {
        for (StarSystem system : GALAXY.systems()) {
            List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
            for (int i = 0; i < bodies.size(); i++) {
                for (int j = i + 1; j < bodies.size(); j++) {
                    SpaceLayout.Body a = bodies.get(i);
                    SpaceLayout.Body b = bodies.get(j);
                    double d = a.distanceTo(b.x(), b.y(), b.z());
                    // 卫星绕着母星排，行星之间隔得更开；任何两个大气球都不该相交
                    assertTrue(d > a.radius() + b.radius(), a.id() + " overlaps " + b.id());
                }
            }
        }
    }

    @Test
    void orbitDistanceGrowsButStaysCompact() {
        assertTrue(SpaceLayout.orbitDistance(0.4) < SpaceLayout.orbitDistance(1));
        assertTrue(SpaceLayout.orbitDistance(1) < SpaceLayout.orbitDistance(30));
        // 再远的行星开脉冲引擎一分钟内也能飞到
        assertTrue(SpaceLayout.orbitDistance(300) < 40 * 20 * 60);
    }

    @Test
    void insideFindsTheSphereAndExitPointIsOutside() {
        StarSystem home = GALAXY.home();
        List<SpaceLayout.Body> bodies = SpaceLayout.of(home);
        SpaceLayout.Body first = bodies.get(0);
        assertEquals(first.id(), SpaceLayout.inside(bodies, first.x() + first.radius() * 0.5, 0, first.z()).orElseThrow().id());
        assertFalse(SpaceLayout.inside(bodies, 0, 5000, 0).isPresent());
        for (float yaw = 0; yaw < 360; yaw += 45) {
            double[] exit = SpaceLayout.exitPoint(first, yaw);
            assertTrue(first.distanceTo(exit[0], exit[1], exit[2]) > first.radius());
            assertFalse(SpaceLayout.inside(bodies, exit[0], exit[1], exit[2]).isPresent());
        }
    }

    @Test
    void landingPointFollowsTheApproachDirection() {
        Planet planet = GALAXY.home().planets().get(0);
        SpaceLayout.Body body = new SpaceLayout.Body(planet, 1000, 0, 0, 100);
        double[] east = SpaceLayout.landingPoint(body, 1090, 0, 0, 400);
        double[] west = SpaceLayout.landingPoint(body, 910, 0, 1e-3, 400);
        double[] above = SpaceLayout.landingPoint(body, 1000, 90, 0, 400);
        assertEquals(0, east[0], 1e-9);
        assertEquals(400, Math.abs(west[0]), 0.01);
        assertEquals(-400, above[1], 1e-6);
        for (double[] p : new double[][] {east, west, above}) {
            assertTrue(Math.abs(p[0]) <= 400 && Math.abs(p[1]) <= 400);
        }
    }
}
