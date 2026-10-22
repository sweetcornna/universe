package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IncubationTest {
    @Test
    void shellCracksTwiceBeforeHatching() {
        assertEquals(0, Incubation.stage(0));
        assertEquals(0, Incubation.stage(Incubation.HATCH_TICKS / 3 - 1));
        assertEquals(1, Incubation.stage(Incubation.HATCH_TICKS / 3));
        assertEquals(2, Incubation.stage(Incubation.HATCH_TICKS * 2 / 3));
        // 等主人回来的卵停在最后一个样子
        assertEquals(2, Incubation.stage(Incubation.HATCH_TICKS * 5));
        assertFalse(Incubation.hatched(Incubation.HATCH_TICKS - 1));
        assertTrue(Incubation.hatched(Incubation.HATCH_TICKS));
    }

    @Test
    void takesFiveMinutes() {
        assertEquals(5 * 60 * 20, Incubation.HATCH_TICKS);
        assertEquals(0.5, Incubation.progress(Incubation.HATCH_TICKS / 2));
        assertEquals(1, Incubation.progress(Incubation.HATCH_TICKS * 2));
    }
}
