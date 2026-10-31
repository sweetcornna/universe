package io.github.sweetcornna.universe.core.chem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.chem.Electrolysis.Recipe;

/** 电解槽（ADR-0036 §三）：不做永动机，几条配方之间的比例照现实。 */
class ElectrolysisTest {
    @Test
    void hydrogenRecoveryFigures() {
        assertEquals(70_500, Electrolysis.HYDROGEN_BURN_JOULES);
        assertEquals(95_100, Electrolysis.HYDROGEN_ROD_JOULES);
        assertEquals(95_100, Electrolysis.hydrogenRecovery());
    }

    @Test
    void noPerpetualMotion() {
        for (Recipe recipe : Recipe.values()) {
            if (recipe.hydrogen() > 0) {
                assertTrue(recipe.joulesPerHydrogen() >= Electrolysis.MIN_RATIO * Electrolysis.hydrogenRecovery(), recipe.id());
            }
        }
    }

    @Test
    void ratiosFollowReality() {
        double water = Recipe.WATER.joulesPerHydrogen();
        assertEquals(280_000, Recipe.WATER.joules());
        // MOXIE 和电解水的焓变差不多
        assertEquals(Recipe.WATER.joules(), Recipe.MOXIE.joules());
        // 氯碱每摩尔氯约是电解水每摩尔氢的 1.7 倍
        assertEquals(1.7, Recipe.BRINE.joules() / water, 0.05);
        // 唐斯电解槽每摩尔钠按 2 倍
        assertEquals(2.0, Recipe.MOLTEN_SALT.joules() / 2.0 / water, 1e-9);
        // 电积铜照冶炼的电，远比电解水便宜
        assertTrue(Recipe.ELECTROWINNING.joules() < Recipe.WATER.joules() / 20);
    }

    @Test
    void ticksAtKeepsEnergy() {
        assertEquals(Recipe.WATER.ticks(), Recipe.WATER.ticksAt(Electrolysis.POWER));
        assertEquals(12, Recipe.ELECTROWINNING.ticksAt(Electrolysis.POWER));
        assertEquals(1120, Recipe.WATER.ticksAt(250));
    }
}
