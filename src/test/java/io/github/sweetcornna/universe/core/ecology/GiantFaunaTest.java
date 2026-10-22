package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.Survey;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Box;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Part;
import io.github.sweetcornna.universe.core.ecology.CreatureAnatomy.Role;
import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

/** 巨型陆地物种（ADR-0026 §九）。 */
class GiantFaunaTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);
    private static final Set<PlanetType> GIANT_TYPES = EnumSet.of(PlanetType.HABITABLE, PlanetType.EXOTIC, PlanetType.OCEAN);
    private static final Set<Biotope> OPEN = EnumSet.of(Biotope.MEADOW, Biotope.ARID, Biotope.COLD, Biotope.WETLAND, Biotope.CRYSTAL);

    private static Ecology ecology(int i, int animals) {
        return new Ecology(0.6, 0.3, 0.6, 4, animals, 4000L + i * 104729L);
    }

    /** 一批合成的宜居、奇异、海洋星球，有没有海的都有。 */
    private static List<Fauna> sample(int count) {
        List<Fauna> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PlanetType type = List.copyOf(GIANT_TYPES).get(i % 3);
            list.add(Fauna.generate(ecology(i, 1 + i % 8), type, Palette.NEUTRAL, i % 2 == 0 ? Liquid.WATER : Liquid.NONE));
        }
        return list;
    }

    private static List<CreatureSpecies> sampleGiants() {
        List<CreatureSpecies> giants = new ArrayList<>(Fauna.FALLBACK.giants());
        for (Fauna fauna : sample(900)) {
            giants.addAll(fauna.giants());
        }
        return giants;
    }

    @Test
    void aboutFortyPercentOfLivingWorldsHaveOne() {
        List<Fauna> sample = sample(3000);
        long with = sample.stream().filter(f -> !f.giants().isEmpty()).count();
        double share = with / (double) sample.size();
        assertTrue(share > 0.36 && share < 0.44, "share " + share);
        assertTrue(sample.stream().allMatch(f -> f.giants().size() <= 1));
    }

    @Test
    void onlyHabitableExoticAndOceanWorldsWithLife() {
        for (PlanetType type : PlanetType.values()) {
            int with = 0;
            for (int i = 0; i < 200; i++) {
                Fauna fauna = Fauna.generate(ecology(i, 1 + i % 8), type, Palette.NEUTRAL, Liquid.WATER);
                with += fauna.giants().size();
                // 没有动物的星球也没有巨型的
                assertTrue(Fauna.generate(ecology(i, 0), type, Palette.NEUTRAL, Liquid.WATER).giants().isEmpty(), type.name());
            }
            assertEquals(GIANT_TYPES.contains(type), with > 0, type + ": " + with);
            assertEquals(GIANT_TYPES.contains(type), Fauna.giantsPossible(type));
        }
        for (Planet planet : GALAXY.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).toList()) {
            Fauna fauna = Fauna.of(planet);
            if (!fauna.giants().isEmpty()) {
                assertTrue(GIANT_TYPES.contains(planet.type()) && !fauna.lifeless(), planet.id().toString());
            }
        }
        assertEquals(1, Fauna.FALLBACK.giants().size(), "命令召唤的巨型生物要有物种可用");
    }

    @Test
    void giantsAreBigSlowAndCalm() {
        List<CreatureSpecies> giants = sampleGiants();
        assertTrue(giants.size() > 200, "sample " + giants.size());
        Set<BodyPlan> plans = EnumSet.noneOf(BodyPlan.class);
        Set<Temperament> temperaments = EnumSet.noneOf(Temperament.class);
        for (CreatureSpecies s : giants) {
            String id = s.name().en();
            plans.add(s.plan());
            temperaments.add(s.temperament());
            assertTrue(Set.of(BodyPlan.QUADRUPED, BodyPlan.HEXAPOD, BodyPlan.BIPED).contains(s.plan()), id);
            assertTrue(s.temperament() == Temperament.DOCILE || s.temperament() == Temperament.TERRITORIAL, id);
            assertEquals(s.temperament().fights(), s.attack() > 0, id);
            // 生命上限也乘个体的体型系数
            assertTrue(Math.round(s.health() * CreatureTraits.NATURAL_MIN) >= Fauna.GIANT_MIN_HEALTH, id + " health " + s.health());
            assertTrue(Math.round(s.health() * CreatureTraits.NATURAL_MAX) <= Fauna.GIANT_MAX_HEALTH, id + " health " + s.health());
            // 比普通的走兽（0.18–0.33）慢
            assertTrue(s.speed() >= 0.16 && s.speed() <= 0.21, id + " speed " + s.speed());
            assertEquals(1, s.groupMin());
            assertEquals(1, s.groupMax());
            assertTrue(s.name().en().contains(" Giant ") && s.name().zh().contains("巨型"), id);
            assertTrue(Fauna.giant(s), id);
            assertFalse(Fauna.deepWater(s), id);
            assertFalse(s.biotopes().isEmpty());
            assertTrue(OPEN.containsAll(s.biotopes()), id + " " + s.biotopes());

            // 站着的身高：乘上野生个体的体型系数（0.85–1.15）还在 3.5–6 格
            CreatureAnatomy anatomy = CreatureAnatomy.of(s);
            double height = anatomy.hitHeight() * s.size();
            assertTrue(height * CreatureTraits.NATURAL_MIN >= Fauna.GIANT_MIN_HEIGHT - 1e-9, id + " height " + height);
            assertTrue(height * CreatureTraits.NATURAL_MAX <= Fauna.GIANT_MAX_HEIGHT + 1e-9, id + " height " + height);
            // 碰撞箱是正方形，太宽了在开阔地上也走不动
            double width = anatomy.hitWidth() * s.size();
            assertTrue(width >= 1.5 && width <= Fauna.GIANT_MAX_WIDTH + 1e-9, id + " width " + width);
            assertTrue(anatomy.saddleHeight() * s.size() < height + 1e-6, id);
        }
        assertEquals(EnumSet.of(BodyPlan.QUADRUPED, BodyPlan.HEXAPOD, BodyPlan.BIPED), plans);
        assertEquals(EnumSet.of(Temperament.DOCILE, Temperament.TERRITORIAL), temperaments);
    }

    @Test
    void giantAnatomyAndSkinAreWellFormed() {
        for (CreatureSpecies s : sampleGiants()) {
            String id = s.name().en();
            CreatureAnatomy anatomy = CreatureAnatomy.of(s);
            float lowestFoot = Float.NEGATIVE_INFINITY;
            for (Part part : anatomy.parts()) {
                float[] o = anatomy.origin(part);
                for (Box box : part.boxes()) {
                    assertTrue(box.u() + box.uvWidth() <= anatomy.textureWidth() && box.v() + box.uvHeight() <= anatomy.textureHeight(), id);
                    if (part.role() == Role.LEG && part.zRot() == 0) {
                        lowestFoot = Math.max(lowestFoot, o[1] + box.y() + box.h());
                    }
                }
            }
            assertEquals(CreatureAnatomy.GROUND, lowestFoot, 0.01, id);
            CreatureSkin skin = CreatureSkin.paint(s, anatomy);
            assertEquals(anatomy.textureWidth() * anatomy.textureHeight(), skin.pixels().length, id);
        }
    }

    @Test
    void indicesStartAtGiantIndexAndRoundTrip() {
        List<Fauna> faunas = new ArrayList<>(sample(300));
        faunas.add(Fauna.FALLBACK);
        for (Fauna fauna : faunas) {
            for (CreatureSpecies s : fauna.species()) {
                assertTrue(s.index() < Fauna.MARINE_INDEX);
            }
            for (CreatureSpecies s : fauna.marine()) {
                assertTrue(s.index() >= Fauna.MARINE_INDEX && s.index() < Fauna.GIANT_INDEX);
            }
            assertTrue(fauna.find(Fauna.GIANT_INDEX + fauna.giants().size()).isEmpty());
            if (fauna.giants().isEmpty()) {
                assertTrue(fauna.find(Fauna.GIANT_INDEX).isEmpty());
                // 对不上的序号和加巨型物种之前一样落到水生或陆地的物种上
                CreatureSpecies fallback = fauna.get(Fauna.GIANT_INDEX);
                assertTrue(fauna.marine().contains(fallback) || fauna.marine().isEmpty() && fauna.species().contains(fallback));
                continue;
            }
            CreatureSpecies giant = fauna.giants().get(0);
            assertEquals(Fauna.GIANT_INDEX, giant.index());
            assertSame(giant, fauna.get(Fauna.GIANT_INDEX));
            assertSame(giant, fauna.find(Fauna.GIANT_INDEX).orElseThrow());
            List<CreatureSpecies> all = fauna.all();
            assertEquals(giant, all.get(all.size() - 1));
            assertEquals(fauna.species().size() + fauna.marine().size() + 1, all.size());
            // 巨型的不在群系的抽签里，只由刷怪器单独刷
            for (BiomeKind biome : BiomeKind.values()) {
                for (double roll = 0; roll < 1; roll += 0.1) {
                    CreatureSpecies picked = fauna.pick(biome, roll);
                    assertTrue(picked == null || !Fauna.giant(picked));
                }
            }
        }
    }

    @Test
    void giantsOnlyTurnUpInOpenCountry() {
        int found = 0;
        for (Fauna fauna : sample(300)) {
            for (BiomeKind biome : BiomeKind.values()) {
                CreatureSpecies giant = fauna.pickGiant(biome);
                Biotope biotope = Biotope.of(biome);
                if (fauna.giants().isEmpty() || biotope == null || !OPEN.contains(biotope)) {
                    assertNull(giant, biome.name());
                } else if (giant != null) {
                    assertTrue(giant.biotopes().contains(biotope));
                    found++;
                }
            }
        }
        assertTrue(found > 0);
        assertNull(Fauna.FALLBACK.pickGiant(BiomeKind.FOREST));
        assertSame(Fauna.FALLBACK.giants().get(0), Fauna.FALLBACK.pickGiant(BiomeKind.GRASSLAND));
    }

    @Test
    void landAndSeaSpeciesDoNotChange() {
        for (int i = 0; i < 300; i++) {
            PlanetType type = PlanetType.values()[i % PlanetType.values().length];
            Fauna wet = Fauna.generate(ecology(i, 1 + i % 8), type, Palette.NEUTRAL, Liquid.WATER);
            Fauna dry = Fauna.generate(ecology(i, 1 + i % 8), type, Palette.NEUTRAL);
            assertEquals(dry.species(), wet.species());
            // 有没有海不影响巨型物种
            assertEquals(dry.giants(), wet.giants());
            for (CreatureSpecies s : wet.species()) {
                assertFalse(Fauna.giant(s), s.name().en());
            }
        }
        // 陆地物种的指纹由 FaunaTest 钉住；巨型物种也是存档格式，这里钉住备用物种表里的那一种
        CreatureSpecies giant = Fauna.FALLBACK.giants().get(0);
        assertEquals("Duren Giant Quadruped", giant.name().en());
        assertEquals("杜雷恩巨型四足兽", giant.name().zh());
        assertEquals(Temperament.DOCILE, giant.temperament());
        assertEquals(2.053899036683988, giant.size(), 1e-12);
        assertEquals(156, giant.health(), 1e-12);
        assertEquals(EnumSet.of(Biotope.MEADOW, Biotope.ARID), EnumSet.copyOf(giant.biotopes()));
    }

    @Test
    void ridingAndSteppingSuitTheirBulk() {
        for (CreatureSpecies s : sampleGiants()) {
            assertTrue(Husbandry.rideable(s), s.name().en());
            assertEquals(Husbandry.GIANT_JUMP, Husbandry.rideJump(s));
            assertEquals(Fauna.GIANT_STEP, Fauna.stepHeight(s));
            assertEquals(3, Husbandry.productCount(s));
        }
        for (CreatureSpecies s : Fauna.FALLBACK.species()) {
            assertEquals(0.6, Fauna.stepHeight(s));
            assertTrue(Husbandry.rideJump(s) > Husbandry.GIANT_JUMP);
        }
        // 蹬一下能上一格半，跨一步能上一格，栅栏（1.5 格）还拦得住
        assertTrue(Fauna.GIANT_STEP >= 1 && Fauna.GIANT_STEP < 1.5);
    }

    @Test
    void censusAndRewardsCountTheGiant() {
        int checked = 0;
        for (Planet planet : GALAXY.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).toList()) {
            Fauna fauna = Fauna.of(planet);
            if (fauna.giants().isEmpty() || planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
                continue;
            }
            assertEquals(fauna.all().size(), Survey.census(planet).creatures(), planet.id().toString());
            assertEquals(5, Survey.reward(DiscoveryKey.creature(planet.id(), Fauna.GIANT_INDEX), GALAXY));
            assertEquals(2, Survey.reward(DiscoveryKey.creature(planet.id(), 0), GALAXY));
            checked++;
        }
        assertTrue(checked > 0, "no giant in the test galaxy");
    }
}
