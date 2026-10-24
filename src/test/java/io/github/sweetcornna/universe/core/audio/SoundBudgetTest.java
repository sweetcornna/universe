package io.github.sweetcornna.universe.core.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundBudgetTest {
    @Test
    void onlyTheFirstFewInOnePlacePlay() {
        SoundBudget budget = new SoundBudget(3, 20, 32);
        assertTrue(budget.allow(0, 0, 0, 0));
        assertTrue(budget.allow(0, 5, 0, 0));
        assertTrue(budget.allow(1, 0, 0, 5));
        assertFalse(budget.allow(2, 3, 0, 3));
        assertFalse(budget.allow(5, 0, 0, 0));
    }

    @Test
    void farAwayPlaysAnyway() {
        SoundBudget budget = new SoundBudget(1, 20, 32);
        assertTrue(budget.allow(0, 0, 0, 0));
        assertFalse(budget.allow(0, 10, 0, 0));
        assertTrue(budget.allow(0, 100, 0, 0));
    }

    @Test
    void theWindowMovesOn() {
        SoundBudget budget = new SoundBudget(1, 20, 32);
        assertTrue(budget.allow(0, 0, 0, 0));
        assertFalse(budget.allow(19, 0, 0, 0));
        assertTrue(budget.allow(20, 0, 0, 0));
    }

    @Test
    void skippedSoundsDoNotUseUpTheBudget() {
        SoundBudget budget = new SoundBudget(1, 20, 32);
        assertTrue(budget.allow(0, 0, 0, 0));
        assertFalse(budget.allow(10, 0, 0, 0));
        // 第 10 刻那声没放，不算：第 20 刻窗口里已经空了
        assertTrue(budget.allow(20, 0, 0, 0));
    }
}
