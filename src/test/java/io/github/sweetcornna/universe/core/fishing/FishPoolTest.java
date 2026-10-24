package io.github.sweetcornna.universe.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 每颗星球的鱼（ADR-0034 §一）：普通的全有，少见四分之三，稀有一半，传说三成；换一颗星球不全一样。 */
class FishPoolTest {
    @Test
    void presenceRatesFollowRarity() {
        Map<Rarity, Integer> present = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> total = new EnumMap<>(Rarity.class);
        for (long seed = 0; seed < 4000; seed++) {
            for (FishSpecies species : FishTable.ALL) {
                total.merge(species.rarity(), 1, Integer::sum);
                if (FishPool.present(seed * 7919, species)) {
                    present.merge(species.rarity(), 1, Integer::sum);
                }
            }
        }
        for (Rarity rarity : Rarity.values()) {
            assertEquals(rarity.presence(), present.get(rarity) / (double) total.get(rarity), 0.02, rarity.name());
        }
    }

    @Test
    void samePlanetSamePoolDifferentPlanetsDiffer() {
        List<FishSpecies> a = FishPool.of(42, Habitat.DEEP);
        assertEquals(a, FishPool.of(42, Habitat.DEEP));
        assertTrue(a.containsAll(FishTable.of(Habitat.DEEP).stream().filter(s -> s.rarity() == Rarity.COMMON).toList()));
        boolean differs = false;
        for (long seed = 43; seed < 60 && !differs; seed++) {
            differs = !FishPool.of(seed, Habitat.DEEP).equals(a);
        }
        assertTrue(differs, "换了十几颗星球，鱼一模一样");
        assertNotEquals(FishPool.of(1, Habitat.ICE), FishPool.of(1, Habitat.FRESH));
    }
}
