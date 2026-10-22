package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

class DarkFogLootTest {
    private static Map<String, Integer> totals(int level, double weight, int rolls) {
        Map<String, Integer> totals = new HashMap<>();
        Rng rng = Rng.of(42);
        for (int i = 0; i < rolls; i++) {
            DarkFogLoot.roll(level, weight, rng).forEach(drop -> totals.merge(drop.item(), drop.count(), Integer::sum));
        }
        return totals;
    }

    @Test
    void lowLevelsDropOnlyShardsAndStone() {
        Map<String, Integer> totals = totals(0, 1, 500);
        assertTrue(totals.get(DarkFogLoot.STONE) >= 500);
        assertTrue(totals.containsKey("universe:energy_shard"));
        assertFalse(totals.containsKey("universe:silicon_neuron"));
        assertFalse(totals.containsKey(DarkFogLoot.MATRIX));
    }

    @Test
    void tiersUnlockByLevel() {
        Map<String, Integer> totals = totals(18, 3, 1000);
        for (String item : new String[] {"universe:energy_shard", "universe:silicon_neuron", "universe:matter_recombinator",
                "universe:negentropy_singularity", "universe:core_element", DarkFogLoot.MATRIX}) {
            assertTrue(totals.containsKey(item), item);
        }
        assertTrue(totals.get("universe:energy_shard") > totals.get("universe:core_element"));
    }

    @Test
    void matrixChanceGrowsWithLevel() {
        assertEquals(0, DarkFogLoot.matrixChance(2));
        assertEquals(0.15, DarkFogLoot.matrixChance(3), 1e-9);
        assertTrue(DarkFogLoot.matrixChance(20) > DarkFogLoot.matrixChance(10));
        assertEquals(0.6, DarkFogLoot.matrixChance(30), 1e-9);
    }

    @Test
    void biggerThingsDropMore() {
        assertTrue(DarkFogLoot.weight(BuildingKind.CORE) > DarkFogLoot.weight(BuildingKind.RAIDER_CAMP));
        assertTrue(DarkFogLoot.weight(BuildingKind.RAIDER_CAMP) > DarkFogLoot.weight(UnitKind.RAIDER));
    }
}
