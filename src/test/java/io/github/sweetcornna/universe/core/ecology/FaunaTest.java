package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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

class FaunaTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);

    private static List<Planet> landable() {
        return GALAXY.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).toList();
    }

    private static List<CreatureSpecies> allSpecies() {
        List<CreatureSpecies> all = new ArrayList<>();
        for (Planet planet : landable()) {
            all.addAll(Fauna.of(planet).species());
        }
        all.addAll(Fauna.FALLBACK.species());
        return all;
    }

    @Test
    void sameEcologyGivesSameFauna() {
        for (Planet planet : landable().subList(0, 20)) {
            assertEquals(Fauna.of(planet).species(), Fauna.of(planet).species());
        }
    }

    @Test
    void speciesCountFollowsEcology() {
        int living = 0;
        for (Planet planet : landable()) {
            Fauna fauna = Fauna.of(planet);
            assertEquals(planet.ecology().animalSpecies(), fauna.species().size(), planet.id().toString());
            if (!fauna.lifeless()) {
                living++;
            }
        }
        assertTrue(living > 5, "too few planets with animals: " + living);
        assertFalse(Fauna.FALLBACK.lifeless());
    }

    @Test
    void temperamentRules() {
        for (Planet planet : landable()) {
            List<CreatureSpecies> species = Fauna.of(planet).species();
            if (species.isEmpty()) {
                continue;
            }
            assertEquals(Temperament.DOCILE, species.get(0).temperament(), planet.id().toString());
            long predators = species.stream().filter(s -> s.temperament() == Temperament.PREDATOR).count();
            assertTrue(predators <= 2);
            for (int i = 0; i < species.size(); i++) {
                CreatureSpecies s = species.get(i);
                assertEquals(i, s.index());
                assertFalse(s.biotopes().isEmpty());
                assertTrue(s.groupMin() >= 1 && s.groupMax() >= s.groupMin());
                assertTrue(s.health() >= 4 && s.health() <= 60);
                assertEquals(s.temperament().fights(), s.attack() > 0, s.name().en());
                assertFalse(s.plan() == BodyPlan.FLYER && s.temperament() == Temperament.PREDATOR);
            }
        }
    }

    @Test
    void pickRespectsBiotopes() {
        for (Planet planet : landable()) {
            Fauna fauna = Fauna.of(planet);
            for (BiomeKind biome : BiomeKind.values()) {
                Biotope biotope = Biotope.of(biome);
                for (double roll = 0; roll < 1; roll += 0.13) {
                    CreatureSpecies s = fauna.pick(biome, roll);
                    if (s != null) {
                        assertTrue(biotope != null ? s.biotopes().contains(biotope) : s.plan().flying());
                    }
                }
            }
        }
    }

    @Test
    void anatomyIsWellFormed() {
        for (CreatureSpecies species : allSpecies()) {
            CreatureAnatomy anatomy = CreatureAnatomy.of(species);
            String id = species.name().en();
            assertNotNull(anatomy.part("head"), id);
            assertTrue(anatomy.part("body") != null || anatomy.part("segment0") != null, id);
            int tw = anatomy.textureWidth();
            int th = anatomy.textureHeight();
            assertTrue(Integer.bitCount(tw) == 1 && Integer.bitCount(th) == 1, id);
            List<int[]> rects = new ArrayList<>();
            float lowestFoot = Float.NEGATIVE_INFINITY;
            for (Part part : anatomy.parts()) {
                if (part.parent() != null) {
                    assertNotNull(anatomy.part(part.parent()), id + " " + part.name());
                }
                float[] o = anatomy.origin(part);
                for (Box box : part.boxes()) {
                    assertTrue(box.w() > 0 && box.h() > 0 && box.d() > 0, id + " " + part.name());
                    assertTrue(box.u() >= 0 && box.v() >= 0, id);
                    assertTrue(box.u() + box.uvWidth() <= tw && box.v() + box.uvHeight() <= th, id + " " + part.name());
                    for (int[] r : rects) {
                        boolean apart = box.u() >= r[0] + r[2] || r[0] >= box.u() + box.uvWidth()
                                || box.v() >= r[1] + r[3] || r[1] >= box.v() + box.uvHeight();
                        assertTrue(apart, id + ": overlapping texture regions at " + part.name());
                    }
                    rects.add(new int[] {box.u(), box.v(), box.uvWidth(), box.uvHeight()});
                    if (part.role() == Role.LEG && part.zRot() == 0) {
                        lowestFoot = Math.max(lowestFoot, o[1] + box.y() + box.h());
                    }
                }
            }
            // 直立的腿正好踩在地面上
            if (lowestFoot != Float.NEGATIVE_INFINITY) {
                assertEquals(CreatureAnatomy.GROUND, lowestFoot, 0.01, id);
            }
            assertTrue(anatomy.hitWidth() > 0.1 && anatomy.hitWidth() < 3, id + " width " + anatomy.hitWidth());
            assertTrue(anatomy.hitHeight() > 0.1 && anatomy.hitHeight() < 3, id + " height " + anatomy.hitHeight());
        }
    }

    @Test
    void skinCoversEveryBoxAndOnlyGlowsWhenAllowed() {
        for (CreatureSpecies species : allSpecies()) {
            CreatureAnatomy anatomy = CreatureAnatomy.of(species);
            CreatureSkin skin = CreatureSkin.paint(species, anatomy);
            assertEquals(anatomy.textureWidth(), skin.width());
            assertEquals(anatomy.textureHeight(), skin.height());
            int[] pixels = skin.pixels();
            for (Part part : anatomy.parts()) {
                for (Box box : part.boxes()) {
                    // 顶面和正面各取一个像素
                    int top = box.v() * skin.width() + box.u() + box.d();
                    int front = (box.v() + box.d()) * skin.width() + box.u() + box.d();
                    assertEquals(0xFF, pixels[top] >>> 24, species.name().en());
                    assertEquals(0xFF, pixels[front] >>> 24, species.name().en());
                }
            }
            boolean mayGlow = species.skin().glowingEyes() || species.skin().glowingPattern();
            if (!mayGlow) {
                assertFalse(skin.hasGlow(), species.name().en());
            }
            if (species.skin().glowingEyes()) {
                assertTrue(skin.hasGlow(), species.name().en());
            }
        }
    }

    @Test
    void bodyPlansAreVaried() {
        // 陆地的物种表覆盖所有陆地体型，水生的（有海的星球）覆盖四种水生体型，两边不混
        List<CreatureSpecies> land = allSpecies();
        List<CreatureSpecies> marine = allMarine();
        for (BodyPlan plan : BodyPlan.values()) {
            List<CreatureSpecies> table = plan.aquatic() ? marine : land;
            assertTrue(table.stream().anyMatch(s -> s.plan() == plan), plan.name());
        }
        assertTrue(land.stream().noneMatch(s -> s.plan().aquatic()));
        assertTrue(marine.stream().allMatch(s -> s.plan().aquatic()));
        for (Temperament temperament : Temperament.values()) {
            assertTrue(land.stream().anyMatch(s -> s.temperament() == temperament), temperament.name());
        }
    }

    // ---------------------------------------------------------------- 陆地物种不变（ADR-0026）

    @Test
    void landFaunaIsUnchangedByAquaticSpecies() {
        // 这个值是加水生动物之前（提交 0cef9a7）的代码算出来的；陆地物种属于存档格式，不能变
        assertEquals(0xFA3A025FAAEF34A3L, LandFaunaFingerprint.compute());
    }

    @Test
    void seaDoesNotChangeLandSpecies() {
        for (Planet planet : landable()) {
            Fauna dry = Fauna.generate(planet.ecology(), planet.type(), planet.palette());
            Fauna wet = Fauna.generate(planet.ecology(), planet.type(), planet.palette(), Liquid.WATER);
            assertEquals(dry.species(), wet.species(), planet.id().toString());
            assertTrue(dry.marine().isEmpty());
        }
    }

    // ---------------------------------------------------------------- 水生动物（ADR-0026）

    private static List<CreatureSpecies> allMarine() {
        List<CreatureSpecies> all = new ArrayList<>();
        for (Planet planet : landable()) {
            all.addAll(Fauna.of(planet).marine());
        }
        all.addAll(Fauna.FALLBACK.marine());
        for (Fauna fauna : syntheticSeas()) {
            all.addAll(fauna.marine());
        }
        return all;
    }

    /** 一批合成的有海星球：覆盖巨型乌贼这种少见的。 */
    private static List<Fauna> syntheticSeas() {
        List<Fauna> list = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            PlanetType type = switch (i % 4) {
                case 0 -> PlanetType.OCEAN;
                case 1 -> PlanetType.HABITABLE;
                case 2 -> PlanetType.FROZEN;
                default -> PlanetType.EXOTIC;
            };
            Ecology ecology = new Ecology(0.6, 0.3, 0.6, 4, 1 + i % 8, 1000L + i * 7919L);
            list.add(Fauna.generate(ecology, type, Palette.NEUTRAL, i % 3 == 0 ? Liquid.ICE : Liquid.WATER));
        }
        return list;
    }

    @Test
    void onlySeaPlanetsWithLifeHaveMarineSpecies() {
        int seas = 0;
        for (Planet planet : landable()) {
            Fauna fauna = Fauna.of(planet);
            String id = planet.id() + " " + planet.type() + " " + planet.liquid();
            if (!Fauna.hasSea(planet.liquid()) || fauna.lifeless()) {
                assertTrue(fauna.marine().isEmpty(), id);
                continue;
            }
            seas++;
            int count = fauna.marine().size();
            assertTrue(count >= (planet.type() == PlanetType.OCEAN ? 5 : 3) && count <= 6, id + ": " + count);
            assertEquals(fauna.species().size() + count + fauna.giants().size(), fauna.all().size());
        }
        assertTrue(seas >= 5, "too few sea planets: " + seas);
        assertTrue(Fauna.hasSea(Liquid.WATER) && Fauna.hasSea(Liquid.ICE));
        assertFalse(Fauna.hasSea(Liquid.ACID) || Fauna.hasSea(Liquid.LAVA) || Fauna.hasSea(Liquid.NONE));
        assertFalse(Fauna.FALLBACK.marine().isEmpty(), "命令召唤的水生动物要有物种可用");
    }

    @Test
    void marineIndicesStartAfterLandAndRoundTrip() {
        List<Fauna> faunas = new ArrayList<>(syntheticSeas());
        faunas.add(Fauna.FALLBACK);
        for (Planet planet : landable()) {
            faunas.add(Fauna.of(planet));
        }
        for (Fauna fauna : faunas) {
            for (CreatureSpecies s : fauna.species()) {
                assertTrue(s.index() < Fauna.MARINE_INDEX);
                assertEquals(s, fauna.find(s.index()).orElseThrow());
            }
            List<CreatureSpecies> marine = fauna.marine();
            for (int i = 0; i < marine.size(); i++) {
                CreatureSpecies s = marine.get(i);
                assertEquals(Fauna.MARINE_INDEX + i, s.index());
                assertEquals(s, fauna.get(s.index()));
                assertEquals(s, fauna.find(s.index()).orElseThrow());
            }
            assertTrue(fauna.find(Fauna.MARINE_INDEX + marine.size()).isEmpty());
            assertTrue(fauna.find(-1).isEmpty());
            assertTrue(fauna.find(fauna.species().size()).isEmpty());
            if (!fauna.lifeless()) {
                // 存档里的序号对不上时总有个物种可用：水生的序号在没有海的物种表里退回陆地物种
                List<CreatureSpecies> expected = marine.isEmpty() ? fauna.species() : marine;
                assertTrue(expected.contains(fauna.get(Fauna.MARINE_INDEX + 100)));
            }
        }
    }

    @Test
    void marineSpeciesFollowTheirPlans() {
        List<Fauna> faunas = new ArrayList<>(syntheticSeas());
        faunas.add(Fauna.FALLBACK);
        for (Planet planet : landable()) {
            faunas.add(Fauna.of(planet));
        }
        int giants = 0;
        for (Fauna fauna : faunas) {
            List<CreatureSpecies> marine = fauna.marine();
            if (marine.isEmpty()) {
                continue;
            }
            assertEquals(BodyPlan.FISH, marine.get(0).plan(), "第一种是成群的鱼");
            assertTrue(marine.get(1).plan() == BodyPlan.JELLY || marine.get(1).plan() == BodyPlan.SQUID);
            assertTrue(marine.stream().filter(s -> s.plan() == BodyPlan.EEL).count() <= 2);
            for (CreatureSpecies s : marine) {
                String id = s.name().en();
                assertTrue(s.plan().aquatic(), id);
                assertTrue(s.biotopes().isEmpty(), id);
                assertTrue(s.groupMin() >= 1 && s.groupMax() >= s.groupMin(), id);
                assertTrue(s.health() >= 3 && s.health() <= 200, id + " health " + s.health());
                assertTrue(s.speed() > 0 && s.abundance() > 0, id);
                assertFalse(Husbandry.rideable(s), id);
                assertEquals(Husbandry.Product.GEL, Husbandry.product(s), id);
                assertTrue(id.endsWith(s.plan().nameEn()), id);
                assertTrue(s.name().zh().endsWith(s.plan().nameZh()), s.name().zh());
                switch (s.plan()) {
                    case FISH -> {
                        assertTrue(s.size() >= 0.35 && s.size() <= 0.8, id);
                        assertTrue(s.temperament() == Temperament.SKITTISH || s.temperament() == Temperament.DOCILE, id);
                        assertEquals(0, s.attack(), id);
                        assertTrue(s.groupMin() >= 4, "鱼成群：" + id);
                    }
                    case EEL -> {
                        assertEquals(Temperament.PREDATOR, s.temperament(), id);
                        assertTrue(s.attack() > 0, id);
                        assertTrue(Fauna.deepWater(s), id);
                    }
                    case JELLY -> {
                        assertEquals(Temperament.DOCILE, s.temperament(), id);
                        assertTrue(s.attack() > 0, "水母会蜇人：" + id);
                        assertEquals(0, s.body().eyes(), id);
                    }
                    case SQUID -> {
                        if (Fauna.giant(s)) {
                            giants++;
                            assertTrue(s.size() >= 1.9 && s.size() <= 6, id);
                            assertEquals(Temperament.TERRITORIAL, s.temperament(), id);
                            assertTrue(s.attack() > 0 && Fauna.deepWater(s), id);
                            assertTrue(id.contains("Giant") && s.name().zh().contains("巨型"), id);
                            assertEquals(1, s.groupMax(), id);
                        } else {
                            assertTrue(s.size() >= 0.7 && s.size() <= 1.3, id);
                            assertEquals(0, s.attack(), id);
                            assertFalse(Fauna.deepWater(s), id);
                            assertFalse(id.contains("Giant"), id);
                        }
                    }
                    default -> throw new AssertionError(s.plan());
                }
            }
        }
        assertTrue(giants > 0, "no giant squid in the sample");
    }

    @Test
    void pickMarineKeepsDeepWaterSpeciesInTheDeep() {
        for (Fauna fauna : syntheticSeas()) {
            if (fauna.marine().isEmpty()) {
                continue;
            }
            boolean anyDeep = fauna.marine().stream().anyMatch(Fauna::deepWater);
            boolean pickedDeep = false;
            for (double roll = 0; roll < 1; roll += 0.01) {
                CreatureSpecies shallow = fauna.pickMarine(false, roll);
                assertNotNull(shallow, "浅水里总有鱼");
                assertFalse(Fauna.deepWater(shallow), shallow.name().en());
                CreatureSpecies deep = fauna.pickMarine(true, roll);
                assertNotNull(deep);
                assertTrue(fauna.marine().contains(deep));
                pickedDeep |= Fauna.deepWater(deep);
            }
            if (anyDeep) {
                assertTrue(pickedDeep, "深水里也刷得出鳗和巨型乌贼");
            }
            // 边界上的随机数也有结果
            assertNotNull(fauna.pickMarine(true, 0.999999));
        }
        Fauna dry = Fauna.generate(new Ecology(0.6, 0.3, 0.6, 4, 4, 5L), PlanetType.HABITABLE, Palette.NEUTRAL);
        assertEquals(null, dry.pickMarine(true, 0.5));
    }

    @Test
    void marineSpeciesArePartOfTheSaveFormat() {
        // 实体只存序号，同一颗星球的水生物种表不能变；这几个值算出来以后就固定
        List<CreatureSpecies> marine = Fauna.FALLBACK.marine();
        assertEquals(6, marine.size());
        assertEquals(List.of(BodyPlan.FISH, BodyPlan.SQUID, BodyPlan.EEL, BodyPlan.FISH, BodyPlan.FISH, BodyPlan.FISH),
                marine.stream().map(CreatureSpecies::plan).toList());
        assertEquals(List.of("Fuporos Fish", "Pidaru Squid", "Mesa Eel", "Dithola Fish", "Mishe Fish", "Vazofal Fish"),
                marine.stream().map(s -> s.name().en()).toList());
        assertEquals("梅萨鳗", marine.get(2).name().zh());
        assertEquals(0.5999374985564614, marine.get(0).size(), 1e-12);
        assertEquals(1.2722822559560343, marine.get(1).size(), 1e-12);
        assertEquals(4.5, marine.get(2).attack(), 1e-12);
        assertEquals(18, marine.get(2).health(), 1e-12);
        // 同一个生态再算一遍完全一样
        Fauna again = Fauna.generate(new Ecology(1, 1, 1, 0, 6, 0x5EED), PlanetType.HABITABLE, Palette.NEUTRAL, Liquid.WATER);
        assertEquals(marine, again.marine());
        for (Planet planet : landable().subList(0, 20)) {
            assertEquals(Fauna.of(planet).marine(), Fauna.of(planet).marine());
        }
    }

    @Test
    void marineAnatomyAndSkinAreWellFormed() {
        for (CreatureSpecies species : allMarine()) {
            CreatureAnatomy anatomy = CreatureAnatomy.of(species);
            String id = species.name().en() + " " + species.plan();
            assertNotNull(anatomy.part("head"), id);
            assertTrue(anatomy.part("body") != null || anatomy.part("segment0") != null, id);
            int tw = anatomy.textureWidth();
            int th = anatomy.textureHeight();
            assertTrue(Integer.bitCount(tw) == 1 && Integer.bitCount(th) == 1, id);
            List<int[]> rects = new ArrayList<>();
            for (Part part : anatomy.parts()) {
                if (part.parent() != null) {
                    assertNotNull(anatomy.part(part.parent()), id + " " + part.name());
                }
                for (Box box : part.boxes()) {
                    assertTrue(box.w() > 0 && box.h() > 0 && box.d() > 0, id + " " + part.name());
                    assertTrue(box.u() >= 0 && box.v() >= 0, id);
                    assertTrue(box.u() + box.uvWidth() <= tw && box.v() + box.uvHeight() <= th, id + " " + part.name());
                    for (int[] r : rects) {
                        boolean apart = box.u() >= r[0] + r[2] || r[0] >= box.u() + box.uvWidth()
                                || box.v() >= r[1] + r[3] || r[1] >= box.v() + box.uvHeight();
                        assertTrue(apart, id + ": overlapping texture regions at " + part.name());
                    }
                    rects.add(new int[] {box.u(), box.v(), box.uvWidth(), box.uvHeight()});
                }
            }
            switch (species.plan()) {
                case FISH -> {
                    assertNotNull(anatomy.part("tail0"), id);
                    assertEquals(Role.FIN, anatomy.part("finl").role(), id);
                }
                case JELLY, SQUID -> assertTrue(anatomy.parts().stream().filter(p -> p.role() == Role.TENTACLE).count()
                        == species.body().legPairs() * 2L, id);
                case EEL -> assertEquals(species.body().segments(),
                        anatomy.parts().stream().filter(p -> p.role() == Role.SEGMENT).count(), id);
                default -> throw new AssertionError(species.plan());
            }
            // 碰撞箱（没乘大小）不为零、不离谱；乘上大小后巨型乌贼也不超过 5 格
            assertTrue(anatomy.hitWidth() > 0.1 && anatomy.hitWidth() < 3, id + " width " + anatomy.hitWidth());
            assertTrue(anatomy.hitHeight() > 0.1 && anatomy.hitHeight() < 3, id + " height " + anatomy.hitHeight());
            assertTrue(anatomy.hitWidth() * species.size() < 5 && anatomy.hitHeight() * species.size() < 4, id);

            CreatureSkin skin = CreatureSkin.paint(species, anatomy);
            assertEquals(tw, skin.width());
            assertEquals(th, skin.height());
            int[] pixels = skin.pixels();
            for (Part part : anatomy.parts()) {
                for (Box box : part.boxes()) {
                    int top = box.v() * skin.width() + box.u() + box.d();
                    int front = (box.v() + box.d()) * skin.width() + box.u() + box.d();
                    assertEquals(0xFF, pixels[top] >>> 24, id);
                    assertEquals(0xFF, pixels[front] >>> 24, id);
                }
            }
            if (!species.skin().glowingEyes() && !species.skin().glowingPattern()) {
                assertFalse(skin.hasGlow(), id);
            }
            if (species.skin().glowingEyes()) {
                assertTrue(skin.hasGlow(), id);
            }
        }
    }
}
