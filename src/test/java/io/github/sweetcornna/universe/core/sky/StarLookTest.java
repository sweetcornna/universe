package io.github.sweetcornna.universe.core.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.Warp;

class StarLookTest {
    @Test
    void styleFollowsTheStarClass() {
        Set<StarLook.Style> seen = EnumSet.noneOf(StarLook.Style.class);
        for (long seed = 1; seed <= 40; seed++) {
            Galaxy galaxy = GalaxyGenerator.generate(seed, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
            for (StarSystem system : galaxy.systems()) {
                StarLook look = StarLook.of(system);
                StarLook.Style expected = switch (system.star().starClass()) {
                    case RED_GIANT -> StarLook.Style.RED_GIANT;
                    case WHITE_DWARF -> StarLook.Style.WHITE_DWARF;
                    case NEUTRON_STAR -> StarLook.Style.PULSAR;
                    case BLACK_HOLE -> StarLook.Style.BLACK_HOLE;
                    default -> StarLook.Style.MAIN_SEQUENCE;
                };
                assertEquals(expected, look.style());
                assertEquals(look, StarLook.of(system), "deterministic");
                seen.add(look.style());
            }
        }
        assertEquals(EnumSet.allOf(StarLook.Style.class), seen);
    }

    @Test
    void starsFitInsideTheirSystems() {
        // 恒星（黑洞连同吸积盘）不碰到最里面的行星的大气球，也在跃迁的引力井以内
        for (long seed = 1; seed <= 40; seed++) {
            Galaxy galaxy = GalaxyGenerator.generate(seed, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
            for (StarSystem system : galaxy.systems()) {
                StarLook look = StarLook.of(system);
                double visible = look.style() == StarLook.Style.BLACK_HOLE ? look.spaceRadius() * look.diskOuter() : look.spaceRadius();
                assertTrue(visible < Warp.STAR_CLEARANCE, system.star().starClass() + " " + visible);
                for (SpaceLayout.Body body : SpaceLayout.of(system)) {
                    double gap = Math.sqrt(body.x() * body.x() + body.z() * body.z()) - body.radius() - visible;
                    assertTrue(gap > 100, "star overlaps " + body.planet().name().en() + " in " + system.index());
                }
            }
        }
    }

    @Test
    void blackHolesAreLargeInTheSkyAndCompactStarsAreSmall() {
        Galaxy galaxy = GalaxyGenerator.generate(7, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        StarSystem any = galaxy.systems().getFirst();
        for (StarSystem system : galaxy.systems()) {
            double diameter = StarLook.skyDiameter(system.star(), 10);
            StarClass type = system.star().starClass();
            if (type == StarClass.BLACK_HOLE) {
                StarLook look = StarLook.of(system);
                // 吸积盘在天上横跨二三十度
                assertTrue(diameter * look.diskOuter() > 20 && diameter * look.diskOuter() < 60, "disk " + diameter * look.diskOuter());
            } else if (type == StarClass.NEUTRON_STAR || type == StarClass.WHITE_DWARF) {
                assertTrue(diameter <= 3, "compact " + diameter);
            } else {
                assertEquals(10, diameter);
            }
        }
        assertTrue(StarLook.of(any).extent(1) >= 2);
    }
}
