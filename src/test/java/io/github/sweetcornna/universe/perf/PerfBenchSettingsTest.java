package io.github.sweetcornna.universe.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** {@code -PperfBench} 的参数（ADR-0038）：空着的按默认，写错的在登记测试时就说清楚是哪一项。 */
class PerfBenchSettingsTest {
    @Test
    void fullAndShortForms() {
        assertEquals(new PerfBench.Settings(5000, 500, 100, 1200), PerfBench.Settings.parse("5000,500,100,1200"));
        assertEquals(new PerfBench.Settings(5000, 500, 0, 1200), PerfBench.Settings.parse("5000,500"));
        assertEquals(new PerfBench.Settings(5000, 0, 100, 600), PerfBench.Settings.parse(" 5000 , , 100 , 600 "));
    }

    @Test
    void badValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PerfBench.Settings.parse("5000,abc"));
        assertThrows(IllegalArgumentException.class, () -> PerfBench.Settings.parse("-16,500"));
        assertThrows(IllegalArgumentException.class, () -> PerfBench.Settings.parse("5000,500,100,0"));
        assertThrows(IllegalArgumentException.class, () -> PerfBench.Settings.parse("50001,500"));
        assertThrows(IllegalArgumentException.class, () -> PerfBench.Settings.parse("1,2,3,4,5"));
    }
}
