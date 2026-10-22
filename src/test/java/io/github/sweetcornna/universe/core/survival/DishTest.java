package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DishTest {
    @Test
    void idsAreUniqueAndLowercase() {
        Set<String> ids = new HashSet<>();
        for (Dish dish : Dish.values()) {
            assertTrue(ids.add(dish.id()), "重复的 " + dish.id());
            assertEquals(dish.id().toLowerCase(java.util.Locale.ROOT), dish.id());
        }
    }

    @Test
    void edibleDishesFeedAndEffectsHaveAmounts() {
        for (Dish dish : Dish.values()) {
            if (dish.edible()) {
                assertTrue(dish.saturation() > 0, dish.id());
            } else {
                assertEquals(Dish.Effect.NONE, dish.effect(), dish.id() + " 不能吃就不该有效果");
            }
            assertEquals(dish.effect() != Dish.Effect.NONE, dish.amount() > 0, dish.id());
        }
    }

    @Test
    void combinedDishesFeedMoreThanTheirParts() {
        // 馅饼、蛋糕比单样原料做的顶饿，都在 8 点以上
        assertTrue(Stream.of(Dish.STAR_PIE, Dish.MEAT_PIE, Dish.SOLAR_PIE, Dish.FISH_PIE, Dish.ALIEN_CAKE, Dish.STAR_CAKE)
                .allMatch(dish -> dish.nutrition() >= 8));
        assertTrue(Dish.STAR_PIE.nutrition() > Dish.STAR_JAM.nutrition());
    }
}
