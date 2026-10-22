package io.github.sweetcornna.universe.core.portal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PortalLayoutTest {
    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Test
    void sixGlyphsOnTheUpperArcFromLeftToRight() {
        List<PortalLayout.Cell> glyphs = PortalLayout.cells().stream().filter(c -> c.part() == PortalLayout.Part.GLYPH).toList();
        assertEquals(PortalAddress.LENGTH, glyphs.size());
        int previousX = Integer.MAX_VALUE;
        for (int i = 0; i < PortalAddress.LENGTH; i++) {
            int index = i;
            PortalLayout.Cell cell = glyphs.stream().filter(c -> c.glyph() == index).findFirst().orElseThrow();
            assertTrue(cell.y() > PortalLayout.CENTER_Y, "glyph " + i + " below the middle");
            // 站在门前看从左到右：本地 x 从大到小
            assertTrue(cell.x() <= previousX, "glyph " + i + " out of order");
            previousX = cell.x();
        }
    }

    @Test
    void theFieldIsClosedByTheRingAndYouCanWalkIn() {
        Set<String> solid = new HashSet<>();
        Set<String> field = new HashSet<>();
        for (PortalLayout.Cell c : PortalLayout.cells()) {
            String k = key(c.x(), c.y(), c.z());
            assertFalse(solid.contains(k) || field.contains(k), "two parts at " + k);
            (c.part() == PortalLayout.Part.FIELD ? field : solid).add(k);
        }
        // 每一格光的上下左右不是光就是石头：门开着时光不会漏出环外
        for (int[] c : PortalLayout.field()) {
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                String n = key(c[0] + d[0], c[1] + d[1], 0);
                assertTrue(field.contains(n) || solid.contains(n), "leaks at " + n);
            }
        }
        // 台面上一格就是门：脚和头都在光里
        assertTrue(field.contains(key(0, 1, 0)));
        assertTrue(field.contains(key(0, 2, 0)));
        // 到达点和拨号台都在台面上，不在环里
        int[] arrival = PortalLayout.arrival();
        int[] dial = PortalLayout.dial();
        assertEquals(1, arrival[1]);
        assertEquals(1, dial[1]);
        assertFalse(field.contains(key(arrival[0], arrival[1], arrival[2])));
        assertFalse(solid.contains(key(arrival[0], arrival[1], arrival[2])) || solid.contains(key(arrival[0], 2, arrival[2])));
        assertTrue(Math.abs(dial[0]) <= PortalLayout.PLATFORM_HALF_WIDTH && Math.abs(dial[2]) <= PortalLayout.PLATFORM_HALF_DEPTH);
    }

    @Test
    void rotationKeepsTheFrontAndTheRightHand() {
        // 门朝南：门前是 +z，右手边（本地 +x）是西
        assertArrayEquals(new int[] {0, 3}, PortalLayout.rotate(0, 3, 0, 1));
        assertArrayEquals(new int[] {-2, 0}, PortalLayout.rotate(2, 0, 0, 1));
        // 门朝东：右手边是南
        assertArrayEquals(new int[] {0, 2}, PortalLayout.rotate(2, 0, 1, 0));
        assertArrayEquals(new int[] {3, 0}, PortalLayout.rotate(0, 3, 1, 0));
    }
}
