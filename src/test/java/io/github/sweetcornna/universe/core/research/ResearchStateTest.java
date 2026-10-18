package io.github.sweetcornna.universe.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.research.Tech.ItemCost;

class ResearchStateTest {
    private static final TechTree TREE = new TechTree(List.of(
            Tech.handIn("a", List.of(), List.of(new ItemCost("x:coil", 2)), List.of()),
            Tech.lab("b", List.of("a"), List.of("x:blue"), 3, List.of()),
            Tech.lab("c", List.of("a"), List.of("x:blue"), 2, List.of()),
            Tech.lab("d", List.of("b", "c"), List.of("x:blue", "x:red"), 1, List.of())));

    @Test
    void prerequisitesGateAvailability() {
        ResearchState state = new ResearchState();
        assertTrue(state.available(TREE, "a"));
        assertFalse(state.available(TREE, "b"));
        assertFalse(state.start(TREE, "b"));
        state.complete("a");
        assertFalse(state.available(TREE, "a"));
        assertTrue(state.available(TREE, "b"));
        assertFalse(state.available(TREE, "d"));
        assertFalse(state.available(TREE, "missing"));
    }

    @Test
    void handInTechsCannotBeStartedInTheLab() {
        ResearchState state = new ResearchState();
        assertFalse(state.start(TREE, "a"));
        assertNull(state.current());
        assertNull(state.addPoint(TREE));
    }

    @Test
    void pointsAccumulateUntilDone() {
        ResearchState state = new ResearchState();
        state.complete("a");
        assertTrue(state.start(TREE, "b"));
        assertEquals(List.of("x:blue"), state.currentMatrices(TREE));
        assertNull(state.addPoint(TREE));
        assertNull(state.addPoint(TREE));
        assertEquals(2, state.progress("b"));
        assertEquals("b", state.addPoint(TREE));
        assertTrue(state.isDone("b"));
        assertNull(state.current());
        assertEquals(0, state.progress("b"));
        assertEquals(List.of(), state.currentMatrices(TREE));
    }

    @Test
    void switchingKeepsProgress() {
        ResearchState state = new ResearchState();
        state.complete("a");
        state.start(TREE, "b");
        state.addPoint(TREE);
        state.start(TREE, "c");
        state.addPoint(TREE);
        assertEquals(1, state.progress("b"));
        assertEquals(1, state.progress("c"));
        assertEquals("c", state.addPoint(TREE));
        state.start(TREE, "b");
        state.addPoint(TREE);
        assertEquals("b", state.addPoint(TREE));
        assertTrue(state.start(TREE, "d"));
        assertEquals(List.of("x:blue", "x:red"), state.currentMatrices(TREE));
    }

    @Test
    void restoredStateDropsEmptyProgress() {
        ResearchState state = new ResearchState(Set.of("a"), "b", Map.of("b", 2, "c", 0));
        assertEquals(Map.of("b", 2), state.progress());
        assertEquals("b", state.addPoint(TREE));
    }

    @Test
    void defaultTreeIsLaidOutByDepth() {
        TechTree tree = TechTree.DEFAULT;
        assertEquals(0, tree.depth("electromagnetism"));
        assertEquals(1, tree.depth("basic_logistics"));
        assertEquals(1, tree.row("automatic_metallurgy"));
        assertEquals(2, tree.depth("basic_assembling"));
        assertEquals(5, tree.depth("plasma_refining"));
        assertEquals(6, tree.depth("energy_matrix"));
        assertEquals(8, tree.columns());
        assertEquals(4, tree.rows());
        for (Tech tech : tree.techs()) {
            assertTrue(tech.id().matches("[a-z_]+"), tech.id());
            // 研究站只认清单里的矩阵
            tech.matrices().forEach(m -> assertTrue(TechTree.MATRICES.contains(m), m));
        }
        assertEquals(3, TREE.columns());
        assertEquals(2, TREE.rows());
    }

    @Test
    void treeRejectsBadDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> new TechTree(List.of(
                Tech.lab("b", List.of("a"), List.of("x:blue"), 1, List.of()),
                Tech.handIn("a", List.of(), List.of(new ItemCost("x:coil", 1)), List.of()))));
        assertThrows(IllegalArgumentException.class, () -> new TechTree(List.of(
                Tech.handIn("a", List.of(), List.of(new ItemCost("x:coil", 1)), List.of()),
                Tech.handIn("a", List.of(), List.of(new ItemCost("x:coil", 1)), List.of()))));
        assertThrows(IllegalArgumentException.class, () -> new Tech("a", List.of(), List.of(), List.of(), 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> Tech.lab("a", List.of(), List.of("x:blue"), 0, List.of()));
    }
}
