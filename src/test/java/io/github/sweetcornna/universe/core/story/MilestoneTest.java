package io.github.sweetcornna.universe.core.story;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class MilestoneTest {
    @Test
    void idsAreStableAndUnique() {
        // ID 存进存档，改了老存档的剧情进度就丢了
        assertEquals(List.of("first_space", "first_warp", "first_shell", "first_universe_matrix", "mission_complete"),
                Arrays.stream(Milestone.values()).map(Milestone::id).toList());
        for (Milestone milestone : Milestone.values()) {
            assertEquals(Optional.of(milestone), Milestone.byId(milestone.id()));
        }
        assertEquals(Optional.empty(), Milestone.byId("nope"));
    }

    @Test
    void onlyTheEndingHasNoLog() {
        assertFalse(Milestone.MISSION_COMPLETE.hasLog());
        assertTrue(Milestone.FIRST_SHELL.hasLog());
    }
}
