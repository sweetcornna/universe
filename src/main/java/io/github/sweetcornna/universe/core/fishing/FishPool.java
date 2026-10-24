package io.github.sweetcornna.universe.core.fishing;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一颗星球上有哪些鱼（ADR-0034 §一）：照星球的种子从对得上的水域里挑，普通的全有，少见的四分之三，稀有的一半，传说的三成。
 * 同一种水域换一颗星球，钓到的也不全一样。
 */
public final class FishPool {
    private FishPool() {
    }

    /** 这颗星球上有没有这种鱼。 */
    public static boolean present(long planetSeed, FishSpecies species) {
        if (species.rarity() == Rarity.COMMON) {
            return true;
        }
        return Rng.of(Seeds.derive(planetSeed, "fish/" + species.id())).chance(species.rarity().presence());
    }

    /** 这颗星球上这种水域里的鱼，按图鉴的顺序。 */
    public static List<FishSpecies> of(long planetSeed, Habitat habitat) {
        List<FishSpecies> pool = new ArrayList<>();
        for (FishSpecies species : FishTable.of(habitat)) {
            if (present(planetSeed, species)) {
                pool.add(species);
            }
        }
        return pool;
    }
}
