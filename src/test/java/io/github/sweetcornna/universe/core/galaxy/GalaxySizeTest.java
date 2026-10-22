package io.github.sweetcornna.universe.core.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** 建世界时定的恒星系数量（ADR-0028 §七）：32–256，默认 64。 */
class GalaxySizeTest {
    @Test
    void defaultIsSixtyFourAndUnchanged() {
        assertEquals(64, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        // 64 个时和加选项以前完全一样：固定几个具体的数
        Galaxy galaxy = GalaxyGenerator.generate(43, 64);
        assertEquals(64, galaxy.systems().size());
        assertEquals(349, galaxy.bodyCount());
        StarSystem fifth = galaxy.systems().get(5);
        assertEquals("Dalido", fifth.name().en());
        assertEquals(-12.835721276997731, fifth.x(), 1e-12);
        assertEquals("Feyek", galaxy.systems().get(63).name().en());
    }

    @Test
    void smallestAndLargestGalaxiesGenerate() {
        assertEquals(32, GalaxyGenerator.generate(43, 32).systems().size());
        assertEquals(256, GalaxyGenerator.generate(43, 256).systems().size());
    }

    @Test
    void homeSystemAndNamesDoNotDependOnCount() {
        Galaxy small = GalaxyGenerator.generate(43, 32);
        Galaxy normal = GalaxyGenerator.generate(43, 64);
        Galaxy large = GalaxyGenerator.generate(43, 256);
        assertEquals(normal.home(), small.home());
        assertEquals(normal.home(), large.home());
        // 同一个序号的恒星系名字一样，位置跟着星系的大小变
        assertEquals(normal.systems().get(5).name(), large.systems().get(5).name());
        assertNotEquals(normal.systems().get(5).x(), large.systems().get(5).x());
    }
}
