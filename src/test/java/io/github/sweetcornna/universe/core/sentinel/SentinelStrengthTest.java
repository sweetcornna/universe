package io.github.sweetcornna.universe.core.sentinel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 守卫机强度（ADR-0028 §七）。 */
class SentinelStrengthTest {
    @Test
    void fullStrengthKeepsTheTable() {
        for (int level = 1; level <= Wanted.MAX_LEVEL; level++) {
            for (SentinelLevel planet : SentinelLevel.values()) {
                Wave plain = Wave.of(level, planet);
                Wave full = Wave.of(level, planet, 1);
                for (SentinelKind kind : SentinelKind.values()) {
                    assertEquals(plain.count(kind), full.count(kind), level + " " + planet + " " + kind);
                }
            }
        }
    }

    @Test
    void zeroStrengthMeansNoSentinels() {
        assertFalse(SentinelStrength.present(0));
        assertTrue(Wave.of(3, SentinelLevel.NORMAL, 0).empty());
        assertEquals(0, SentinelStrength.count(4, 0));
    }

    @Test
    void countsScaleButNeverVanish() {
        // 一级：两架无人机
        assertEquals(1, Wave.of(1, SentinelLevel.NORMAL, 0.5).count(SentinelKind.DRONE));
        assertEquals(1, Wave.of(1, SentinelLevel.NORMAL, 0.1).count(SentinelKind.DRONE));
        assertEquals(6, Wave.of(1, SentinelLevel.NORMAL, 3).count(SentinelKind.DRONE));
        // 本来没有的还是没有
        assertEquals(0, Wave.of(1, SentinelLevel.NORMAL, 3).count(SentinelKind.WALKER));
        // 超过上限按 3 倍算
        assertEquals(6, SentinelStrength.count(2, 10));
    }

    @Test
    void healthAndDamageScale() {
        assertEquals(40, SentinelStrength.scale(20, 2), 1e-9);
        assertEquals(10, SentinelStrength.scale(20, 0.5), 1e-9);
        assertEquals(60, SentinelStrength.scale(20, 9), 1e-9);
        assertEquals(20, SentinelStrength.scale(20, 0), 1e-9);
    }
}
