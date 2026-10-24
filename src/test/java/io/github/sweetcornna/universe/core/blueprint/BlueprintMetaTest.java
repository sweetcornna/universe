package io.github.sweetcornna.universe.core.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BlueprintMetaTest {
    @Test
    void sameNameBumpsVersion() {
        assertEquals(1, BlueprintMeta.nextVersion(List.of()));
        assertEquals(2, BlueprintMeta.nextVersion(List.of(1)));
        assertEquals(5, BlueprintMeta.nextVersion(List.of(1, 4, 2)));
    }

    @Test
    void devicesOnlySkipsFloorsAndWalls() {
        assertTrue(BlueprintMeta.keep(false, false));
        assertTrue(BlueprintMeta.keep(true, true));
        assertFalse(BlueprintMeta.keep(true, false));
    }

    @Test
    void costsListShortagesFirst() {
        Map<String, Integer> need = new LinkedHashMap<>();
        need.put("belt", 40);
        need.put("assembler", 4);
        need.put("sorter", 12);
        need.put("none", 0);
        Map<String, Integer> have = Map.of("belt", 64, "assembler", 1);
        List<BlueprintMeta.Cost<String>> costs = BlueprintMeta.costs(need, have);
        assertEquals(3, costs.size());
        // 缺的在前（分拣器缺 12，装配机缺 3），然后是够的
        assertEquals("sorter", costs.get(0).item());
        assertEquals("assembler", costs.get(1).item());
        assertEquals("belt", costs.get(2).item());
        assertTrue(costs.get(0).shortage());
        assertFalse(costs.get(2).shortage());
        assertEquals(0, costs.get(0).have());
    }
}
