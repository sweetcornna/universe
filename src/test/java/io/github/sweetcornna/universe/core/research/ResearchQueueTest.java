package io.github.sweetcornna.universe.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.research.Tech.ItemCost;
import io.github.sweetcornna.universe.core.research.Upgrade.Infinite;
import io.github.sweetcornna.universe.core.research.Upgrade.Tier;

class ResearchQueueTest {
    private static final TechTree TREE = new TechTree(List.of(
            Tech.handIn("a", List.of(), List.of(new ItemCost("x:coil", 2)), List.of()),
            Tech.lab("blue", List.of("a"), List.of("x:blue"), 1, List.of("x:blue")),
            Tech.lab("b", List.of("a"), List.of("x:blue"), 2, List.of()),
            Tech.lab("c", List.of("a"), List.of("x:blue"), 1, List.of()),
            Tech.lab("d", List.of("b", "c"), List.of("x:blue"), 1, List.of()),
            Tech.lab("e", List.of("d"), List.of("x:blue"), 1, List.of()),
            Tech.handIn("h", List.of("a"), List.of(new ItemCost("x:coil", 1)), List.of())),
            List.of(new Upgrade("u", "x:i", List.of("blue"), List.of(new Tier(List.of("x:blue"), 1), new Tier(List.of("x:blue"), 1)),
                    new Infinite(List.of("x:blue"), 1, 0)),
                    new Upgrade("f", "x:i", List.of("blue"), List.of(new Tier(List.of("x:blue"), 1), new Tier(List.of("x:blue"), 1)),
                            null)));

    private static ResearchState started() {
        return new ResearchState(Set.of("a"), null, Map.of());
    }

    private static void finish(ResearchState state) {
        String id = state.current();
        while (state.current() != null && id.equals(state.current()) && state.addPoint(TREE) == null) {
        }
    }

    @Test
    void enqueueWhileIdleStartsAtOnce() {
        ResearchState state = started();
        assertTrue(state.enqueue(TREE, "b", ResearchState.QUEUE_LIMIT));
        assertEquals("b", state.current());
        assertEquals(List.of(), state.queue());
    }

    @Test
    void nextEntryStartsWhenCurrentFinishes() {
        ResearchState state = started();
        state.start(TREE, "b");
        assertTrue(state.enqueue(TREE, "c", ResearchState.QUEUE_LIMIT));
        assertTrue(state.enqueue(TREE, "d", ResearchState.QUEUE_LIMIT));
        assertEquals(List.of("c", "d"), state.queue());
        finish(state);
        assertTrue(state.isDone("b"));
        assertEquals("c", state.current());
        assertEquals(List.of("d"), state.queue());
        finish(state);
        assertEquals("d", state.current());
        finish(state);
        assertNull(state.current());
        assertEquals(List.of(), state.queue());
    }

    @Test
    void blockedEntriesAreSkippedAndKept() {
        ResearchState state = started();
        state.start(TREE, "b");
        state.enqueue(TREE, "c", ResearchState.QUEUE_LIMIT);
        state.enqueue(TREE, "d", ResearchState.QUEUE_LIMIT);
        // d 挪到最前面：b 完成时 d 还差 c，跳过它先研究 c
        assertTrue(state.move(1, -1));
        assertEquals(List.of("d", "c"), state.queue());
        finish(state);
        assertEquals("c", state.current());
        assertEquals(List.of("d"), state.queue());
        finish(state);
        assertEquals("d", state.current());
    }

    @Test
    void stoppingLeavesBlockedEntriesInTheQueue() {
        ResearchState state = new ResearchState(Set.of("a", "c"), null, Map.of());
        state.start(TREE, "b");
        state.enqueue(TREE, "d", ResearchState.QUEUE_LIMIT);
        state.stop();
        assertNull(state.advance(TREE));
        assertNull(state.current());
        assertEquals(List.of("d"), state.queue());
    }

    @Test
    void prerequisitesMustBeDoneOrPlanned() {
        ResearchState state = started();
        assertFalse(state.canEnqueue(TREE, "d", ResearchState.QUEUE_LIMIT));
        state.start(TREE, "b");
        assertFalse(state.canEnqueue(TREE, "d", ResearchState.QUEUE_LIMIT));
        state.enqueue(TREE, "c", ResearchState.QUEUE_LIMIT);
        assertTrue(state.canEnqueue(TREE, "d", ResearchState.QUEUE_LIMIT));
        assertFalse(state.canEnqueue(TREE, "e", ResearchState.QUEUE_LIMIT));
        // 正在研究的、排过的、上交物品的、完成了的、不认识的都排不进去
        assertFalse(state.canEnqueue(TREE, "b", ResearchState.QUEUE_LIMIT));
        assertFalse(state.canEnqueue(TREE, "c", ResearchState.QUEUE_LIMIT));
        assertFalse(state.canEnqueue(TREE, "h", ResearchState.QUEUE_LIMIT));
        assertFalse(state.canEnqueue(TREE, "a", ResearchState.QUEUE_LIMIT));
        assertFalse(state.canEnqueue(TREE, "nope", ResearchState.QUEUE_LIMIT));
    }

    @Test
    void queueHasALimit() {
        ResearchState state = new ResearchState(Set.of("a", "blue"), "b", Map.of());
        assertTrue(state.enqueue(TREE, "c", 2));
        assertTrue(state.enqueue(TREE, "u", 2));
        assertFalse(state.enqueue(TREE, "u", 2));
        assertEquals(8, ResearchState.queueLimit(false));
        assertEquals(32, ResearchState.queueLimit(true));
    }

    @Test
    void removingAnEntryDropsWhatDependsOnIt() {
        ResearchState state = started();
        state.start(TREE, "b");
        state.enqueue(TREE, "c", ResearchState.QUEUE_LIMIT);
        state.enqueue(TREE, "d", ResearchState.QUEUE_LIMIT);
        state.enqueue(TREE, "e", ResearchState.QUEUE_LIMIT);
        assertTrue(state.dequeue(TREE, 0));
        assertEquals(List.of(), state.queue());
        assertFalse(state.dequeue(TREE, 0));
    }

    @Test
    void upgradesQueueOneLevelPerEntry() {
        ResearchState state = new ResearchState(Set.of("a", "blue"), "b", Map.of());
        assertTrue(state.enqueue(TREE, "f", ResearchState.QUEUE_LIMIT));
        assertTrue(state.enqueue(TREE, "f", ResearchState.QUEUE_LIMIT));
        // f 只有两级
        assertFalse(state.enqueue(TREE, "f", ResearchState.QUEUE_LIMIT));
        assertEquals(1, state.plannedLevel(TREE, 0));
        assertEquals(2, state.plannedLevel(TREE, 1));
        finish(state);
        assertEquals("f", state.current());
        assertEquals(List.of("f"), state.queue());
        finish(state);
        assertEquals(1, state.level("f"));
        assertEquals("f", state.current());
        finish(state);
        assertEquals(2, state.level("f"));
        assertNull(state.current());
    }

    @Test
    void queueTakesPriorityOverAContinuingUpgrade() {
        ResearchState state = new ResearchState(Set.of("a", "blue"), "u", Map.of());
        state.enqueue(TREE, "c", ResearchState.QUEUE_LIMIT);
        // u 下一级矩阵一样，本来会接着研究；队里有 c，先研究 c
        state.addPoint(TREE);
        assertEquals(1, state.level("u"));
        assertEquals("c", state.current());
        // 队里只剩研究不了的，u 接着研究
        ResearchState other = new ResearchState(Set.of("a", "blue"), "u", Map.of(), Map.of(), List.of("e"));
        other.addPoint(TREE);
        assertEquals("u", other.current());
        assertEquals(List.of("e"), other.queue());
    }

    @Test
    void finishedEntriesFallOutOfTheQueue() {
        ResearchState state = new ResearchState(Set.of("a"), null, Map.of(), Map.of(), List.of("c", "b"));
        state.complete("c");
        assertEquals("b", state.advance(TREE));
        assertEquals(List.of(), state.queue());
        state.clear();
        assertEquals(List.of(), state.queue());
    }

    @Test
    void movingStaysInBounds() {
        ResearchState state = new ResearchState(Set.of("a"), "b", Map.of(), Map.of(), List.of("c", "d"));
        assertFalse(state.move(0, -1));
        assertFalse(state.move(1, 1));
        assertTrue(state.move(0, 1));
        assertEquals(List.of("d", "c"), state.queue());
        state.clearQueue();
        assertEquals(List.of(), state.queue());
    }
}
