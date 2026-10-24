package io.github.sweetcornna.universe.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 总督每天的决定（ADR-0034 §二）。 */
class DecisionsTest {
    @Test
    void twentyFourInFourKinds() {
        assertEquals(24, Decisions.ALL.size());
        for (Decisions.Kind kind : Decisions.Kind.values()) {
            assertEquals(6, Decisions.ALL.stream().filter(d -> d.kind() == kind).count(), kind.id());
        }
        Set<String> ids = new HashSet<>();
        for (Decisions.Decision d : Decisions.ALL) {
            assertTrue(ids.add(d.id()), "重复的 " + d.id());
            assertTrue(d.options().size() >= 2 && d.options().size() <= 4, d.id());
        }
    }

    @Test
    void everyDecisionHasADifferentChoice() {
        for (Decisions.Decision d : Decisions.ALL) {
            assertEquals(d.options().size(), new HashSet<>(d.options()).size(), d.id() + " 有两个一样的选项");
        }
    }

    @Test
    void pickSkipsMissingBuildingsAndRecentOnes() {
        for (long seed = 0; seed < 200; seed++) {
            Decisions.Decision d = Decisions.pick(seed, type -> false, List.of("well", "festival"));
            assertEquals(null, d.requires(), "没有建筑时不出要建筑的");
            assertFalse(d.id().equals("well") || d.id().equals("festival"));
        }
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            seen.add(Decisions.pick(seed, type -> true, List.of()).id());
        }
        assertEquals(24, seen.size(), "什么建筑都有时 24 个都会出");
    }

    @Test
    void sameSeedSameDecision() {
        assertEquals(Decisions.pick(9, type -> true, List.of()), Decisions.pick(9, type -> true, List.of()));
        assertNotEquals(Decisions.byId("well"), Decisions.byId("noise"));
    }
}
