package io.github.sweetcornna.universe.core.sentinel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaveTest {
    @Test
    void noWaveWithoutWantedOrSentinels() {
        assertTrue(Wave.of(0, SentinelLevel.NORMAL).empty());
        assertTrue(Wave.of(3, SentinelLevel.NONE).empty());
    }

    @Test
    void tableMatchesTheAdr() {
        int[][] expected = {
                {2, 0, 0, 0, 0, 0},
                {3, 0, 1, 0, 0, 0},
                {2, 1, 0, 1, 1, 0},
                {2, 1, 1, 1, 2, 0},
                {2, 1, 2, 1, 1, 1},
        };
        for (int level = 1; level <= Wanted.MAX_LEVEL; level++) {
            Wave wave = Wave.of(level, SentinelLevel.NORMAL);
            assertEquals(level, wave.level());
            for (SentinelKind kind : SentinelKind.values()) {
                assertEquals(expected[level - 1][kind.ordinal()], wave.count(kind), "level " + level + " " + kind);
            }
        }
    }

    @Test
    void groundUnitsFromLevelThreeWalkerOnlyAtFive() {
        for (int level = 1; level <= Wanted.MAX_LEVEL; level++) {
            Wave wave = Wave.of(level, SentinelLevel.NORMAL);
            assertEquals(level >= 3, wave.count(SentinelKind.QUADRUPED) > 0);
            assertEquals(level == 5, wave.count(SentinelKind.WALKER) > 0);
        }
    }

    @Test
    void aggressivePlanetsSendOneMoreDrone() {
        for (int level = 1; level <= Wanted.MAX_LEVEL; level++) {
            Wave normal = Wave.of(level, SentinelLevel.NORMAL);
            Wave aggressive = Wave.of(level, SentinelLevel.AGGRESSIVE);
            assertEquals(normal.count(SentinelKind.DRONE) + 1, aggressive.count(SentinelKind.DRONE));
            assertEquals(normal.total() + 1, aggressive.total());
            assertEquals(normal.total(), Wave.of(level, SentinelLevel.LOW).total());
        }
    }

    @Test
    void wavesGrowWithLevel() {
        int previous = 0;
        for (int level = 1; level <= Wanted.MAX_LEVEL; level++) {
            int total = Wave.of(level, SentinelLevel.NORMAL).total();
            assertTrue(total >= previous, "level " + level);
            previous = total;
        }
        assertEquals(Wave.of(Wanted.MAX_LEVEL, SentinelLevel.NORMAL).total(), Wave.of(9, SentinelLevel.NORMAL).total());
    }
}
