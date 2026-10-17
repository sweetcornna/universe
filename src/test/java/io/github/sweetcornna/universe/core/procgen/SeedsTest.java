package io.github.sweetcornna.universe.core.procgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SeedsTest {
    @Test
    void sameInputGivesSameSeed() {
        assertEquals(Seeds.derive(42L, "galaxy"), Seeds.derive(42L, "galaxy"));
        assertEquals(Seeds.derive(42L, 7L), Seeds.derive(42L, 7L));
    }

    @Test
    void differentInputsGiveDifferentSeeds() {
        assertNotEquals(Seeds.derive(42L, "galaxy"), Seeds.derive(42L, "planet"));
        assertNotEquals(Seeds.derive(42L, "galaxy"), Seeds.derive(43L, "galaxy"));
        assertNotEquals(Seeds.derive(42L, 0L), Seeds.derive(42L, 1L));
    }

    @Test
    void childSeedsDoNotCollide() {
        Set<Long> seen = new HashSet<>();
        for (long i = 0; i < 100_000; i++) {
            long child = Seeds.derive(42L, i);
            assertTrue(seen.add(child), "第 " + i + " 个子种子与之前的重复");
        }
    }

    @Test
    void flippingOneParentBitChangesAboutHalfTheOutputBits() {
        long changedBits = 0;
        int samples = 0;
        for (long parent = 0; parent < 1_000; parent++) {
            long base = Seeds.derive(parent, "galaxy");
            for (int bit = 0; bit < 64; bit++) {
                changedBits += Long.bitCount(base ^ Seeds.derive(parent ^ (1L << bit), "galaxy"));
                samples++;
            }
        }
        double average = (double) changedBits / samples;
        assertTrue(average > 31 && average < 33, "平均变化位数为 " + average + "，期望接近 32");
    }

    /** 这些值一旦变化，已有存档的宇宙就会变样；确实要改算法时，必须同时提供存档迁移。 */
    @Test
    void derivedValuesAreStableAcrossVersions() {
        assertEquals(0x12E3EC182130EF31L, Seeds.derive(0L, "galaxy"));
        assertEquals(0xE832E2047186CA9EL, Seeds.derive(20261016L, 0L));
        assertEquals(0x122781D7F4C3E726L, Seeds.derive(20261016L, "恒星系"));
    }
}
