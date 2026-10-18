package io.github.sweetcornna.universe.core.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class WarpTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(12345L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    @Test
    void rangeGrowsWithDrivesUpToALimit() {
        assertEquals(0, Warp.range(0));
        assertEquals(10, Warp.range(1));
        assertEquals(15, Warp.range(2));
        assertEquals(25, Warp.range(Warp.MAX_DRIVES));
        assertEquals(Warp.range(Warp.MAX_DRIVES), Warp.range(Warp.MAX_DRIVES + 3));
    }

    @Test
    void everySystemHasANeighbourWithinOneDrive() {
        // 一台跃迁引擎就能走遍星团：每个恒星系 10 光年内都有别的恒星系
        for (StarSystem system : GALAXY.systems()) {
            boolean reachable = GALAXY.systems().stream().anyMatch(other -> Warp.inRange(system, other, 1));
            assertTrue(reachable, "isolated system " + system.index());
        }
        assertFalse(Warp.inRange(GALAXY.home(), GALAXY.home(), 4));
    }

    @Test
    void cannotWarpInsideGravityWells() {
        StarSystem home = GALAXY.home();
        List<SpaceLayout.Body> bodies = SpaceLayout.of(home);
        assertEquals(Warp.Status.NEAR_STAR, Warp.check(bodies, 500, 0, 0));
        SpaceLayout.Body body = bodies.getFirst();
        assertEquals(Warp.Status.NEAR_BODY, Warp.check(bodies, body.x() + body.radius() + 10, body.y(), body.z()));
        assertEquals(body, Warp.blocker(bodies, body.x(), body.y() + body.radius() + 10, body.z()).orElseThrow());
    }

    @Test
    void arrivalIsNearAPlanetOnTheDepartureSide() {
        StarSystem home = GALAXY.home();
        for (StarSystem target : GALAXY.systems().subList(1, 20)) {
            List<SpaceLayout.Body> bodies = SpaceLayout.of(target);
            double[] arrival = Warp.arrival(home, target, bodies);
            assertEquals(Warp.Status.OK, Warp.check(bodies, arrival[0], arrival[1], arrival[2]));
            double dx = home.x() - target.x();
            double dz = home.z() - target.z();
            double length = Math.hypot(dx, dz);
            SpaceLayout.Body planet = Warp.approachTarget(bodies, dx / length, dz / length);
            // 离那颗行星不远，在它朝着来路的一侧
            double gap = planet.distanceTo(arrival[0], arrival[1], arrival[2]) - planet.radius();
            assertTrue(gap >= Warp.APPROACH - 1e-6 && gap < Warp.APPROACH + 64 * 250 + 1, "gap " + gap + " in " + target.index());
            double side = (arrival[0] - planet.x()) * dx + (arrival[2] - planet.z()) * dz;
            assertTrue(side > 0, "arrival on the wrong side for " + target.index());
            // 船头（偏航 0 朝 +z）指向来路的反方向，也就是对着那颗行星
            double yaw = Math.toRadians(arrival[3]);
            assertEquals(-dx / length, -Math.sin(yaw), 1e-6);
            assertEquals(-dz / length, Math.cos(yaw), 1e-6);
        }
    }
}
