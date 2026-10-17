package io.github.sweetcornna.universe.core.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class IdleTimerTest {
    @Test
    void expiresAfterContinuousIdleTime() {
        IdleTimer<String> timer = new IdleTimer<>(100);
        List<String> dims = List.of("moon", "mars");
        for (int i = 0; i < 4; i++) {
            assertTrue(timer.advance(dims, dim -> dim.equals("mars"), 20).isEmpty());
        }
        assertEquals(80, timer.idleTicks("moon"));
        assertEquals(0, timer.idleTicks("mars"));
        assertEquals(List.of("moon"), timer.advance(dims, dim -> dim.equals("mars"), 20));
        // 返回过一次就重新计时
        assertEquals(0, timer.idleTicks("moon"));
        assertTrue(timer.advance(dims, dim -> dim.equals("mars"), 20).isEmpty());
    }

    @Test
    void activityResetsTheClock() {
        IdleTimer<String> timer = new IdleTimer<>(100);
        List<String> dims = List.of("moon");
        timer.advance(dims, dim -> false, 90);
        timer.advance(dims, dim -> true, 20);
        assertEquals(0, timer.idleTicks("moon"));
        assertTrue(timer.advance(dims, dim -> false, 90).isEmpty());
        assertEquals(List.of("moon"), timer.advance(dims, dim -> false, 10));
    }

    @Test
    void forgetsDimensionsThatAreNoLongerCandidates() {
        IdleTimer<String> timer = new IdleTimer<>(100);
        timer.advance(List.of("moon", "mars"), dim -> false, 90);
        // 月球已经卸载了（或者成了出生维度），不再是候选
        timer.advance(List.of("mars"), dim -> false, 5);
        assertEquals(0, timer.idleTicks("moon"));
        assertEquals(95, timer.idleTicks("mars"));
        // 重新加载后从零开始
        assertEquals(Set.of("mars"), Set.copyOf(timer.advance(List.of("moon", "mars"), dim -> false, 5)));
        assertEquals(5, timer.idleTicks("moon"));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new IdleTimer<String>(0));
    }
}
