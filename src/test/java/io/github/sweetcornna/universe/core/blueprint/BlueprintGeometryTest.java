package io.github.sweetcornna.universe.core.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BlueprintGeometryTest {
    @Test
    void selectionSizeCountsBothCorners() {
        assertArrayEquals(new int[] {1, 1, 1}, BlueprintGeometry.size(5, 64, 5, 5, 64, 5));
        assertArrayEquals(new int[] {32, 3, 10}, BlueprintGeometry.size(-10, 70, 4, 21, 68, -5));
        assertTrue(BlueprintGeometry.fits(new int[] {32, 32, 32}));
        assertFalse(BlueprintGeometry.fits(new int[] {33, 1, 1}));
    }

    @Test
    void packingRoundTrips() {
        for (int x : new int[] {0, 7, 31}) {
            for (int y : new int[] {0, 31}) {
                for (int z : new int[] {0, 19, 31}) {
                    int packed = BlueprintGeometry.pack(x, y, z);
                    assertEquals(x, BlueprintGeometry.x(packed));
                    assertEquals(y, BlueprintGeometry.y(packed));
                    assertEquals(z, BlueprintGeometry.z(packed));
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BlueprintGeometry.pack(-1, 0, 0));
    }

    @Test
    void rotationKeepsEveryBlockInsideTheRotatedBox() {
        int sx = 5;
        int sz = 3;
        for (int turns = 0; turns < 4; turns++) {
            int[] size = BlueprintGeometry.rotatedSize(new int[] {sx, 2, sz}, turns);
            Set<Integer> seen = new HashSet<>();
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    int[] r = BlueprintGeometry.rotate(x, z, sx, sz, turns);
                    assertTrue(r[0] >= 0 && r[0] < size[0] && r[1] >= 0 && r[1] < size[2], turns + ": " + x + "," + z);
                    assertTrue(seen.add(r[0] * 100 + r[1]), "两个方块落到了同一格");
                }
            }
        }
    }

    @Test
    void clockwiseTurnSendsNorthToEast() {
        // 北边一排（z = 0）转一次到东边（x 最大）
        int[] r = BlueprintGeometry.rotate(0, 0, 5, 3, 1);
        assertArrayEquals(new int[] {2, 0}, r);
        // 转四次回到原处
        for (int turns = 0; turns <= 4; turns += 4) {
            assertArrayEquals(new int[] {3, 1}, BlueprintGeometry.rotate(3, 1, 5, 3, turns));
        }
        assertArrayEquals(new int[] {1, 1}, BlueprintGeometry.rotate(3, 1, 5, 3, 2));
    }
}
