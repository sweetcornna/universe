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
                assertTrue(d - body.radius() > SpaceLayout.starRadius(system), body.id() + " overlaps the star");
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
    void auAtInvertsOrbitDistance() {
        for (double au : new double[] {0.3, 1, 5.2, 30}) {
            assertEquals(au, SpaceLayout.auAt(SpaceLayout.orbitDistance(au)), 1e-9);
        }
        assertEquals(0, SpaceLayout.auAt(100));
    }

    @Test
    void orbitDistanceGrowsButStaysCompact() {
        assertTrue(SpaceLayout.orbitDistance(0.4) < SpaceLayout.orbitDistance(1));
        assertTrue(SpaceLayout.orbitDistance(1) < SpaceLayout.orbitDistance(30));
        // 从恒星附近飞到最外侧的行星，开脉冲引擎（40 格/刻）一刻钟以内
        for (StarSystem system : GALAXY.systems()) {
            for (SpaceLayout.Body body : SpaceLayout.of(system)) {
                assertTrue(Math.hypot(body.x(), body.z()) < 40 * 20 * 900, body.id() + " is too far out");
            }
        }
    }

    @Test
    void neighbouringPlanetsTakeHalfAMinuteOrMoreOnImpulse() {
        // 相邻两颗行星（大气球之间）开脉冲引擎至少半分钟，最多三分钟
        double impulse = 40 * 20;
        for (StarSystem system : GALAXY.systems()) {
            List<SpaceLayout.Body> planets = SpaceLayout.of(system).stream().filter(body -> !body.planet().moon()).toList();
            for (int i = 1; i < planets.size(); i++) {
                SpaceLayout.Body a = planets.get(i - 1);
                SpaceLayout.Body b = planets.get(i);
                double gap = Math.hypot(b.x(), b.z()) - Math.hypot(a.x(), a.z()) - a.radius() - b.radius();
                assertTrue(gap / impulse > 30 - 1e-6, system.index() + ": " + a.id() + " -> " + b.id() + " " + gap);
                assertTrue(gap / impulse < 180, system.index() + ": " + a.id() + " -> " + b.id() + " " + gap);
            }
        }
    }

    @Test
    void homeworldLooksLikeLowOrbitAfterTakeoff() {
        SpaceLayout.Body home = SpaceLayout.find(SpaceLayout.of(GALAXY.home()), GalaxyGenerator.HOME_PLANET_ID).orElseThrow();
        assertEquals(1200, home.surface(), 1);
        // 刚起飞时地平线比水平线低 20–30 度，和国际空间站上看地球差不多
        double dip = Math.toDegrees(Math.acos(home.surface() / SpaceLayout.exitDistance(home)));
        assertTrue(dip > 20 && dip < 30, "dip " + dip);
    }

    @Test
    void moonsSeeTheirParentLarge() {
        for (StarSystem system : GALAXY.systems()) {
            List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
            for (SpaceLayout.Body body : bodies) {
                if (!body.planet().moon() || body.id().moon() != 0) {
                    continue;
                }
                SpaceLayout.Body parent = SpaceLayout.find(bodies, body.id().parent()).orElseThrow();
                double angular = Math.toDegrees(2 * Math.asin(parent.surface() / parent.distanceTo(body.x(), body.y(), body.z())));
                assertTrue(angular > 15, body.id() + " sees its parent at " + angular);
            }
        }
    }

    @Test
    void insideFindsTheSphereAndExitPointIsOutside() {
        StarSystem home = GALAXY.home();
        List<SpaceLayout.Body> bodies = SpaceLayout.of(home);
        SpaceLayout.Body first = bodies.get(0);
        assertEquals(first.id(), SpaceLayout.inside(bodies, first.x() + first.radius() * 0.5, 0, first.z()).orElseThrow().id());
        assertFalse(SpaceLayout.inside(bodies, 0, 50000, 0).isPresent());
        for (double[] surface : new double[][] {{0, 0}, {300, -200}, {-5000, 40}, {1e6, 1e6}}) {
            double[] exit = SpaceLayout.exitPoint(first, surface[0], surface[1], first.planet().worldSize());
            assertTrue(first.distanceTo(exit[0], exit[1], exit[2]) > first.radius());
            assertTrue(exit[1] > first.y(), "takeoff should leave the planet below");
            assertFalse(SpaceLayout.inside(bodies, exit[0], exit[1], exit[2]).isPresent());
        }
    }

    @Test
    void landingPointFollowsTheApproachDirection() {
        Planet planet = GALAXY.home().planets().get(0);
        SpaceLayout.Body body = new SpaceLayout.Body(planet, 1000, 0, 0, 100, 95);
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
