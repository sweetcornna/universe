package io.github.sweetcornna.universe.core.fossil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.poi.LandmarkType;

/** 古生物发掘场的布局和石哨兵的规矩（ADR-0034 §三）。 */
class DigSiteLayoutTest {
    @Test
    void digSiteIsAppendedAfterSettlements() {
        assertEquals(LandmarkType.SETTLEMENT.ordinal() + 1, LandmarkType.DIG_SITE.ordinal());
        assertEquals("landmark/dig_site", LandmarkType.DIG_SITE.seedKey());
        assertFalse(LandmarkType.DIG_SITE.seabed());
        assertTrue(LandmarkType.DIG_SITE.radius() <= 15);
    }

    @Test
    void fieldTentsAndSentriesDoNotOverlap() {
        for (long seed = 0; seed < 300; seed++) {
            DigSiteLayout.Layout layout = DigSiteLayout.layout(seed * 7919L);
            assertEquals(6, layout.squares().size());
            int min = 6 * DigSiteLayout.MIN_FOSSILS_PER_SQUARE;
            int max = 6 * DigSiteLayout.MAX_FOSSILS_PER_SQUARE;
            assertTrue(layout.fossils().size() >= min && layout.fossils().size() <= max, "化石土 " + layout.fossils().size());
            Set<String> cells = new HashSet<>();
            for (DigSiteLayout.Cell cell : layout.fossils()) {
                assertTrue(cells.add(cell.dx() + "," + cell.dz()), "两块化石土叠在一起");
                assertTrue(Math.abs(cell.dx()) <= DigSiteLayout.FIELD_HALF_X && Math.abs(cell.dz()) <= DigSiteLayout.FIELD_HALF_Z);
                assertTrue(cell.dy() == -1 || cell.dy() == -2);
                assertTrue(layout.squares().stream().anyMatch(s -> s.contains(cell.dx(), cell.dz()) && -s.depth() == cell.dy()), "化石土不在坑底");
            }
            // 隔梁：x = ±2、z = 0 不挖
            for (DigSiteLayout.Square square : layout.squares()) {
                assertFalse(square.contains(2, 0) || square.contains(-2, 1) || square.contains(0, 0));
            }
            assertTrue(layout.sentries().size() >= DigSiteLayout.MIN_SENTRIES && layout.sentries().size() <= DigSiteLayout.MAX_SENTRIES);
            Set<String> sentries = new HashSet<>();
            for (int[] sentry : layout.sentries()) {
                assertTrue(sentries.add(sentry[0] + "," + sentry[1]));
                assertTrue(Math.hypot(sentry[0], sentry[1]) <= LandmarkType.DIG_SITE.radius(), "石哨兵离中心太远");
                assertFalse(Math.abs(sentry[0]) <= DigSiteLayout.FIELD_HALF_X && Math.abs(sentry[1]) <= DigSiteLayout.FIELD_HALF_Z, "石哨兵睡在探方里");
                for (DigSiteLayout.Tent tent : layout.tents()) {
                    assertFalse(tent.covers(sentry[0], sentry[1]), "石哨兵睡在帐篷里");
                }
            }
        }
    }

    @Test
    void tentsFaceTheFieldAndLeaveRoomForTheCollector() {
        DigSiteLayout.Layout layout = DigSiteLayout.layout(1);
        assertEquals(3, layout.tents().size());
        for (DigSiteLayout.Tent tent : layout.tents()) {
            int[] forward = tent.forward();
            // 门朝着中心
            assertTrue(forward[0] * -tent.x() + forward[1] * -tent.z() > 0, "帐篷的门没朝探方：" + tent);
            for (int x = -DigSiteLayout.FIELD_HALF_X; x <= DigSiteLayout.FIELD_HALF_X; x++) {
                for (int z = -DigSiteLayout.FIELD_HALF_Z; z <= DigSiteLayout.FIELD_HALF_Z; z++) {
                    assertFalse(tent.covers(x, z), "帐篷搭在探方上");
                }
            }
            assertFalse(tent.covers(DigSiteLayout.COLLECTOR[0], DigSiteLayout.COLLECTOR[1]));
            assertFalse(tent.covers(DigSiteLayout.TABLE[0], DigSiteLayout.TABLE[1]));
            for (int dx = -2; dx <= 1; dx++) {
                // 每顶帐篷占顺着门 4 格、横着 3 格
                assertTrue(tent.covers(tent.x() + forward[0] * dx, tent.z() + forward[1] * dx));
            }
        }
    }

    @Test
    void collectorSpotTurnsWithTheLandmark() {
        Landmark north = new Landmark(LandmarkType.DIG_SITE, 100, 200, 0, 0, 0, 0, 0);
        Landmark turned = new Landmark(LandmarkType.DIG_SITE, 100, 200, 0, 0, 0, 0, 1);
        assertArrayEquals(new int[] {100 + DigSiteLayout.COLLECTOR[0], 200 + DigSiteLayout.COLLECTOR[1]}, DigSiteLayout.collectorSpot(north));
        // 顺时针转 90°：(x, z) → (-z, x)
        assertArrayEquals(new int[] {100 - DigSiteLayout.COLLECTOR[1], 200 + DigSiteLayout.COLLECTOR[0]}, DigSiteLayout.collectorSpot(turned));
    }

    @Test
    void sentriesWakeOnTheThirdDig() {
        int unrest = 0;
        for (int dig = 1; dig < StoneSentryRules.WAKE_DIGS; dig++) {
            unrest = StoneSentryRules.disturb(unrest);
            assertFalse(StoneSentryRules.awake(unrest), "挖第 " + dig + " 块就醒了");
        }
        unrest = StoneSentryRules.disturb(unrest);
        assertTrue(StoneSentryRules.awake(unrest));
        assertEquals(StoneSentryRules.WAKE_DIGS, StoneSentryRules.disturb(unrest));
        assertTrue(StoneSentryRules.fuse(10, 40));
        assertFalse(StoneSentryRules.fuse(20, 40));
        // 石块近的飞得快、远的飞得慢，但都在 10–24 刻之间
        assertEquals(10, StoneSentryRules.flightTicks(2));
        assertEquals(24, StoneSentryRules.flightTicks(StoneSentryRules.THROW_RANGE * 2));
        assertTrue(StoneSentryRules.flightTicks(8) < StoneSentryRules.flightTicks(20));
    }
}
