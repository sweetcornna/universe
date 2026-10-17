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
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
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
        List<CreatureSpecies> all = allSpecies();
        for (BodyPlan plan : BodyPlan.values()) {
            assertTrue(all.stream().anyMatch(s -> s.plan() == plan), plan.name());
        }
        for (Temperament temperament : Temperament.values()) {
            assertTrue(all.stream().anyMatch(s -> s.temperament() == temperament), temperament.name());
        }
    }
}
