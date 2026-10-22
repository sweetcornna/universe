package io.github.sweetcornna.universe.core.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.sky.StarLook;

class BlackHolesTest {
    private static double[] station(StarSystem system) {
        return StationLayout.site(system).map(s -> new double[] {s.x(), s.y(), s.z()}).orElse(new double[] {0, 0, 0});
    }

    @Test
    void aboutOneSystemInSixHasOneClearOfEverything() {
        int found = 0;
        int total = 0;
        for (long seed = 1; seed <= 6; seed++) {
            Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(seed, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
            for (StarSystem system : galaxy.systems()) {
                total++;
                List<SpaceLayout.Body> bodies = SpaceLayout.of(system);
                Optional<BlackHoles.BlackHole> hole = BlackHoles.of(system, bodies, station(system));
                if (hole.isEmpty()) {
                    continue;
                }
                found++;
                BlackHoles.BlackHole h = hole.get();
                if (system.star().starClass() == StarClass.BLACK_HOLE) {
                    continue;
                }
                assertTrue(Math.abs(h.y()) <= BlackHoles.MAX_HEIGHT);
                double r = Math.sqrt(h.x() * h.x() + h.z() * h.z());
                assertTrue(r >= BlackHoles.MIN_DISTANCE && r <= BlackHoles.MAX_DISTANCE);
                for (SpaceLayout.Body body : bodies) {
                    assertTrue(h.distanceTo(body.x(), body.y(), body.z()) > body.radius() + BlackHoles.HORIZON);
                }
            }
        }
        double share = (double) found / total;
        assertTrue(share > 0.10 && share < 0.25, "share " + share);
    }

    @Test
    void horizonIsWhatIsDrawn() {
        Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(4, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        int stars = 0;
        int loose = 0;
        for (StarSystem system : galaxy.systems()) {
            Optional<BlackHoles.BlackHole> hole = BlackHoles.in(system);
            assertEquals(BlackHoles.of(system, SpaceLayout.of(system), station(system)), hole);
            if (hole.isEmpty()) {
                continue;
            }
            StarLook look = BlackHoles.look(system);
            assertEquals(StarLook.Style.BLACK_HOLE, look.style());
            assertEquals(look, BlackHoles.look(system));
            if (system.star().starClass() == StarClass.BLACK_HOLE) {
                stars++;
                // 恒星本身是黑洞：视界就是恒星挡住飞船的那个球，飞进去先被甩走，不会被"太热"推回来
                assertEquals(SpaceLayout.starRadius(system), BlackHoles.horizon(system));
                assertEquals(SpaceLayout.STAR_SCALE * look.spaceRadius(), BlackHoles.horizon(system), 1e-9);
            } else {
                loose++;
                assertEquals(BlackHoles.HORIZON, BlackHoles.horizon(system));
                assertEquals(BlackHoles.HORIZON, look.spaceRadius());
            }
        }
        assertTrue(loose > 0, "no loose black hole in this cluster");
        assertTrue(stars + loose > 0);
    }

    @Test
    void sameSystemSameBlackHole() {
        Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(3, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        for (StarSystem system : galaxy.systems()) {
            assertEquals(BlackHoles.of(system, SpaceLayout.of(system), station(system)),
                    BlackHoles.of(system, SpaceLayout.of(system), station(system)));
        }
    }

    @Test
    void flingsToTheFarSideOfTheCluster() {
        Galaxy galaxy = GalaxyGenerator.fromGalaxySeed(7, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        StarSystem from = galaxy.system(5).orElseThrow();
        double farthest = 0;
        for (StarSystem s : galaxy.systems()) {
            farthest = Math.max(farthest, from.distanceTo(s));
        }
        Set<Integer> seen = new HashSet<>();
        for (int jump = 0; jump < 20; jump++) {
            int to = BlackHoles.destination(galaxy, 5, 42, jump);
            assertNotEquals(5, to);
            seen.add(to);
            // 落在离得最远的那一片：至少是最远距离的一半
            assertTrue(from.distanceTo(galaxy.system(to).orElseThrow()) > farthest * 0.5);
        }
        assertTrue(seen.size() > 3, "always the same system");
    }
}
