package io.github.sweetcornna.universe.core.space;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.StationLayout.Dir;
import io.github.sweetcornna.universe.core.space.StationLayout.Part;
import io.github.sweetcornna.universe.core.space.StationLayout.Placement;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;

class StationLayoutTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(12345L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    @Test
    void everySystemWithPlanetsHasAStationClearOfBodies() {
        int clear = 0;
        int total = 0;
        for (StarSystem system : GALAXY.systems()) {
            Optional<Site> site = StationLayout.site(system);
            if (system.planets().isEmpty()) {
                assertTrue(site.isEmpty());
                continue;
            }
            assertTrue(site.isPresent(), system.name().toString());
            assertEquals(site, StationLayout.site(system), "同一个恒星系的空间站总在同一个地方");
            Site s = site.get();
            total++;
            boolean ok = true;
            for (SpaceLayout.Body body : SpaceLayout.of(system)) {
                double gap = body.distanceTo(s.x(), s.y(), s.z()) - body.radius();
                assertTrue(gap > StationLayout.RADIUS, "空间站不能压进大气球：" + system.name() + " 离 " + body.planet().id() + " " + gap);
                ok &= gap > StationLayout.RADIUS + 100;
            }
            clear += ok ? 1 : 0;
            assertFalse(StationLayout.anchor(system).orElseThrow().moon());
        }
        assertTrue(clear >= total * 0.95, "绝大多数空间站离别的天体都够远：" + clear + "/" + total);
        assertEquals(GalaxyGenerator.HOME_PLANET_ID, StationLayout.anchor(GALAXY.home()).orElseThrow().id(), "母星系的空间站绕着母星");
    }

    @Test
    void localAndWorldCoordinatesRoundTrip() {
        for (Dir hangar : Dir.values()) {
            Site site = new Site(100, 0, -50, hangar);
            int[] world = site.toWorld(7, 3, -4);
            assertArrayEquals(new int[] {7, 3, -4}, site.toLocal(world[0], world[1], world[2]));
            // 本地 +x 朝着机库开口的方向
            int[] ahead = site.toWorld(1, 0, 0);
            assertEquals(100 + hangar.dx(), ahead[0]);
            assertEquals(-50 + hangar.dz(), ahead[2]);
            assertEquals(hangar, Dir.EAST.rotate(hangar));
        }
    }

    @Test
    void layoutHasAnOpenHangarAFloorAndATerminal() {
        // 开口里没有方块，飞船开得进来
        for (int y = 1; y < StationLayout.CEILING; y++) {
            for (int z = -StationLayout.OPENING_HALF_WIDTH; z <= StationLayout.OPENING_HALF_WIDTH; z++) {
                for (int x = 0; x <= StationLayout.RADIUS + 1; x++) {
                    assertNull(StationLayout.at(x, y, z), "机库里挡路：" + x + "," + y + "," + z);
                }
            }
        }
        // 背面是墙
        assertNotNull(StationLayout.at(-StationLayout.RADIUS, 2, 0));
        // 站内到处有地板和天花板
        for (int x = -20; x <= 20; x += 4) {
            for (int z = -20; z <= 20; z += 4) {
                if (x * x + z * z < 400) {
                    assertNotNull(StationLayout.at(x, 0, z));
                    assertNotNull(StationLayout.at(x, StationLayout.CEILING, z));
                }
            }
        }
        assertEquals(Part.PAD, StationLayout.at(StationLayout.PAD_X, 0, 0).part(), "停机坪中间画着 H");
        Placement terminal = StationLayout.at(-15, 1, 0);
        assertEquals(Part.TERMINAL, terminal.part());
        assertEquals(Dir.EAST, terminal.facing());
        Placement teleporter = StationLayout.at(StationLayout.TELEPORTER_X, 1, StationLayout.TELEPORTER_Z);
        assertEquals(Part.TELEPORTER, teleporter.part());
        assertEquals(Dir.EAST, teleporter.facing());
        assertNull(StationLayout.at(StationLayout.TELEPORTER_X + 2, 1, StationLayout.TELEPORTER_Z), "传送器前面空着，到站的人站在那里");
        assertNull(StationLayout.at(StationLayout.TELEPORTER_X + 2, 2, StationLayout.TELEPORTER_Z));
        Placement board = StationLayout.at(StationLayout.MISSION_BOARD_X, 1, StationLayout.MISSION_BOARD_Z);
        assertEquals(Part.MISSION_BOARD, board.part());
        assertEquals(Dir.EAST, board.facing());
        assertNull(StationLayout.at(StationLayout.MISSION_BOARD_X + 1, 1, StationLayout.MISSION_BOARD_Z), "任务板前面空着");
        assertNull(StationLayout.at(0, StationLayout.MAX_Y + 1, 0));
        assertNotNull(StationLayout.at(0, StationLayout.MAX_Y, 0));
    }

    @Test
    void insideMeansBetweenFloorAndCeiling() {
        Site site = new Site(0, 0, 0, Dir.EAST);
        assertTrue(site.inside(0.5, 1, 0.5));
        assertTrue(site.inside(15.5, 5, 0.5));
        assertFalse(site.inside(0.5, 11, 0.5), "屋顶上面是太空");
        assertFalse(site.inside(0.5, -2, 0.5));
        assertFalse(site.inside(30.5, 1, 0.5), "开口外面是太空");
    }
}
