package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ecology.Husbandry.Diet;
import io.github.sweetcornna.universe.core.ecology.Husbandry.Product;
import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

class HusbandryTest {
    @Test
    void grazersEatPlantsAndFightersEatMeat() {
        assertEquals(Diet.HERBIVORE, Husbandry.diet(Temperament.DOCILE));
        assertEquals(Diet.HERBIVORE, Husbandry.diet(Temperament.SKITTISH));
        assertEquals(Diet.CARNIVORE, Husbandry.diet(Temperament.TERRITORIAL));
        assertEquals(Diet.CARNIVORE, Husbandry.diet(Temperament.PREDATOR));
    }

    @Test
    void fiercerAnimalsAreHarderToTame() {
        double previous = 1;
        for (Temperament temperament : Temperament.values()) {
            double chance = Husbandry.tameChance(temperament);
            assertTrue(chance < previous, temperament + " 应该比前一种更难驯服");
            assertTrue(chance > 0.05, temperament + " 喂十几次总能驯服");
            previous = chance;
        }
    }

    @Test
    void everyPlanetHasProductsAndMounts() {
        // 很多颗星球的动物加起来：三种产物都有，能骑的都是够大的走兽
        Map<Product, Integer> products = new EnumMap<>(Product.class);
        int mounts = 0;
        int walkers = 0;
        for (int seed = 0; seed < 200; seed++) {
            Fauna fauna = Fauna.generate(new Ecology(0.8, 0.8, 0.8, 6, 8, seed), PlanetType.HABITABLE, Palette.NEUTRAL);
            for (CreatureSpecies species : fauna.species()) {
                products.merge(Husbandry.product(species), 1, Integer::sum);
                int count = Husbandry.productCount(species);
                assertTrue(count >= 1 && count <= 3);
                boolean walker = Set.of(BodyPlan.QUADRUPED, BodyPlan.HEXAPOD, BodyPlan.BIPED).contains(species.plan());
                walkers += walker ? 1 : 0;
                if (Husbandry.rideable(species)) {
                    mounts++;
                    assertTrue(walker, species.plan() + " 不能骑");
                    assertTrue(species.size() >= Husbandry.RIDE_MIN_SIZE);
                }
            }
        }
        assertEquals(EnumSet.allOf(Product.class), products.keySet());
        assertTrue(mounts > walkers * 0.2 && mounts < walkers * 0.7, "能骑的走兽占两成到七成：" + mounts + " / " + walkers);
    }

    @Test
    void ridingIsFasterThanWalkingAndJumpsClearAWall() {
        for (int seed = 0; seed < 100; seed++) {
            Fauna fauna = Fauna.generate(new Ecology(0.8, 0.8, 0.8, 6, 8, seed), PlanetType.HABITABLE, Palette.NEUTRAL);
            for (CreatureSpecies species : fauna.species()) {
                if (!Husbandry.rideable(species)) {
                    continue;
                }
                double speed = Husbandry.rideSpeed(species);
                assertTrue(speed >= 0.16 && speed <= 0.32, "比走路（0.1）快，不比最快的马快：" + speed);
                // 蓄满力的一跳要跳得过两格高的墙
                assertTrue(jumpHeight(Husbandry.rideJump(species)) > 2, species.plan() + " 跳不过两格");
            }
        }
    }

    @Test
    void biggerAnimalsGiveMore() {
        Fauna fauna = Fauna.generate(new Ecology(0.8, 0.8, 0.8, 6, 8, 7), PlanetType.HABITABLE, Palette.NEUTRAL);
        CreatureSpecies any = fauna.species().get(0);
        assertEquals(1, Husbandry.productCount(withSize(any, 0.5)));
        assertEquals(2, Husbandry.productCount(withSize(any, 1.3)));
        assertEquals(3, Husbandry.productCount(withSize(any, 2.0)));
        assertFalse(Husbandry.rideable(withSize(any, 1.1)));
    }

    /** 原版的跳：每刻先走再减重力 0.08、乘空气阻力 0.98。 */
    private static double jumpHeight(double velocity) {
        double y = 0;
        double top = 0;
        for (int tick = 0; tick < 60; tick++) {
            y += velocity;
            top = Math.max(top, y);
            velocity = (velocity - 0.08) * 0.98;
        }
        return top;
    }

    private static CreatureSpecies withSize(CreatureSpecies s, double size) {
        return new CreatureSpecies(s.index(), s.name(), BodyPlan.QUADRUPED, s.temperament(), size, s.body(), s.skin(), s.health(), s.speed(),
                s.attack(), s.groupMin(), s.groupMax(), s.biotopes(), s.abundance(), s.voice());
    }
}
