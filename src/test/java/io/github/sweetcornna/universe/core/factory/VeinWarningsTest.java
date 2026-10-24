package io.github.sweetcornna.universe.core.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VeinWarningsTest {
    @Test
    void modesDecideWhenToWarn() {
        assertEquals(VeinWarnings.Mode.ALL, VeinWarnings.DEFAULT);
        assertFalse(VeinWarnings.warn(VeinWarnings.Mode.NONE, true, true));
        assertTrue(VeinWarnings.warn(VeinWarnings.Mode.NODE, true, false));
        assertTrue(VeinWarnings.warn(VeinWarnings.Mode.NODE, true, true));
        assertFalse(VeinWarnings.warn(VeinWarnings.Mode.ALL, true, false));
        assertTrue(VeinWarnings.warn(VeinWarnings.Mode.ALL, true, true));
        assertFalse(VeinWarnings.warn(VeinWarnings.Mode.NODE, false, false));
    }

    @Test
    void sameMachineWarnsOncePerFiveMinutes() {
        assertTrue(VeinWarnings.cooledDown(-1, 0));
        assertFalse(VeinWarnings.cooledDown(1000, 1000 + 5999));
        assertTrue(VeinWarnings.cooledDown(1000, 1000 + 6000));
        // 时钟倒回去了
        assertTrue(VeinWarnings.cooledDown(10_000, 500));
    }

    @Test
    void modesCycleAndParse() {
        assertEquals(VeinWarnings.Mode.NODE, VeinWarnings.Mode.NONE.next());
        assertEquals(VeinWarnings.Mode.NONE, VeinWarnings.Mode.ALL.next());
        assertEquals(VeinWarnings.Mode.NODE, VeinWarnings.Mode.byKey("node"));
        assertEquals(VeinWarnings.Mode.ALL, VeinWarnings.Mode.byKey("?"));
        assertEquals(VeinWarnings.Mode.ALL, VeinWarnings.Mode.byOrdinal(9));
    }
}
