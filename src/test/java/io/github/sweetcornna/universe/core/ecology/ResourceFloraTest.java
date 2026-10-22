package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;

class ResourceFloraTest {
    @Test
    void everyVegetatedPlanetHasOxygenAndSodium() {
        for (PlanetType type : PlanetType.values()) {
            if (!type.landable()) {
                assertTrue(ResourceFlora.plants(type, false).isEmpty(), type.name());
                continue;
            }
            List<ResourcePlant> plants = ResourceFlora.plants(type, false);
            assertTrue(plants.contains(ResourcePlant.OXYGEN_BLADDER), type.name());
            assertTrue(plants.contains(ResourcePlant.SALTWORT), type.name());
            // 没有植被的星球也有钠，氧气靠精炼机从岩石里提
            assertEquals(List.of(ResourcePlant.SALT_CRYSTAL), ResourceFlora.plants(type, true), type.name());
        }
    }

    @Test
    void signaturesMatchThePlanetType() {
        assertEquals(ResourcePlant.FROSTWORT, ResourceFlora.signature(PlanetType.FROZEN).orElseThrow());
        assertEquals(ResourcePlant.SOLAR_VINE, ResourceFlora.signature(PlanetType.SCORCHED).orElseThrow());
        assertEquals(ResourcePlant.GAMMA_WEED, ResourceFlora.signature(PlanetType.IRRADIATED).orElseThrow());
        assertEquals(ResourcePlant.FUNGAL_CLUSTER, ResourceFlora.signature(PlanetType.TOXIC).orElseThrow());
        assertEquals(ResourcePlant.STAR_BRAMBLE, ResourceFlora.signature(PlanetType.HABITABLE).orElseThrow());
        assertTrue(ResourceFlora.signature(PlanetType.OCEAN).isEmpty());
        // 每种招牌植物只属于一类星球
        Set<ResourcePlant> seen = new HashSet<>();
        for (PlanetType type : PlanetType.values()) {
            ResourceFlora.signature(type).ifPresent(plant -> assertTrue(seen.add(plant), plant.name()));
        }
    }

    @Test
    void spotsAreDeterministicAndInsideTheChunk() {
        ResourceFlora a = new ResourceFlora(42L, PlanetType.FROZEN, false);
        ResourceFlora b = new ResourceFlora(42L, PlanetType.FROZEN, false);
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                List<ResourceFlora.Spot> spots = a.spots(cx, cz);
                assertEquals(spots, b.spots(cx, cz));
                for (ResourceFlora.Spot spot : spots) {
                    assertEquals(cx, Math.floorDiv(spot.x(), 16));
                    assertEquals(cz, Math.floorDiv(spot.z(), 16));
                }
            }
        }
    }

    @Test
    void patchesCrossChunkBordersWithoutGaps() {
        // 同一丛跨了区块：每个区块各放自己那一部分，合起来和一次算整片一样
        ResourceFlora flora = new ResourceFlora(7L, PlanetType.HABITABLE, false);
        int total = 0;
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                total += flora.spots(cx, cz).size();
            }
        }
        // 128×128 格，每 20×20 格有 45% 的机会长 3–7 株：期望约 80 株，留出余量
        assertTrue(total > 30 && total < 200, "total " + total);
    }

    @Test
    void otherSeedsGiveOtherPatches() {
        ResourceFlora a = new ResourceFlora(1L, PlanetType.TOXIC, false);
        ResourceFlora b = new ResourceFlora(2L, PlanetType.TOXIC, false);
        boolean differs = false;
        for (int cx = 0; cx < 6 && !differs; cx++) {
            differs = !a.spots(cx, 0).equals(b.spots(cx, 0));
        }
        assertTrue(differs);
    }

    @Test
    void pickFollowsTheGround() {
        ResourceFlora flora = new ResourceFlora(3L, PlanetType.SCORCHED, false);
        Map<ResourcePlant, Integer> counts = new EnumMap<>(ResourcePlant.class);
        Rng rng = Rng.of(9);
        for (int i = 0; i < 2000; i++) {
            ResourcePlant plant = flora.pick(Biotope.MEADOW, false, rng.nextDouble());
            assertNotNull(plant);
            counts.merge(plant, 1, Integer::sum);
        }
        assertTrue(counts.get(ResourcePlant.OXYGEN_BLADDER) > 500);
        assertTrue(counts.get(ResourcePlant.SOLAR_VINE) > 400);
        assertFalse(counts.containsKey(ResourcePlant.FROSTWORT));
        assertNull(flora.pick(null, false, 0.5));
        // 别处的矿物地面和土一样；干旱地带的矿物地面上是盐晶簇和仙人掌
        assertEquals(flora.pick(Biotope.MEADOW, false, 0.5), flora.pick(Biotope.MEADOW, true, 0.5));
        assertEquals(ResourcePlant.SALT_CRYSTAL, flora.pick(Biotope.ARID, true, 0.5));
        assertEquals(ResourcePlant.ECHINOCACTUS, flora.pick(Biotope.ARID, true, 0.9));
        ResourceFlora dead = new ResourceFlora(3L, PlanetType.BARREN, true);
        assertEquals(ResourcePlant.SALT_CRYSTAL, dead.pick(null, true, 0.1));
        assertNull(dead.pick(Biotope.ARID, false, 0.1));
        assertTrue(dead.caveColumns(0, 0).isEmpty());
    }

    @Test
    void plantingFollowsThePlanet() {
        assertTrue(ResourceFlora.grows(ResourcePlant.FROSTWORT, PlanetType.FROZEN, false));
        assertFalse(ResourceFlora.grows(ResourcePlant.FROSTWORT, PlanetType.SCORCHED, false));
        assertTrue(ResourceFlora.grows(ResourcePlant.OXYGEN_BLADDER, PlanetType.TOXIC, false));
        assertFalse(ResourceFlora.grows(ResourcePlant.OXYGEN_BLADDER, PlanetType.BARREN, true));
        assertFalse(ResourceFlora.grows(ResourcePlant.CAVE_MARROW, PlanetType.HABITABLE, false));
        assertFalse(ResourceFlora.grows(ResourcePlant.SALT_CRYSTAL, PlanetType.BARREN, true));
    }

    @Test
    void productsPlantBack() {
        assertEquals(ResourcePlant.SALTWORT, ResourcePlant.fromProduct("sodium").orElseThrow());
        assertEquals(ResourcePlant.OXYGEN_BLADDER, ResourcePlant.fromProduct("oxygen").orElseThrow());
        assertTrue(ResourcePlant.fromProduct("iron_ingot").isEmpty());
        for (ResourcePlant plant : ResourcePlant.values()) {
            assertTrue(plant.farmable() == (plant.habitat() == ResourcePlant.Habitat.SOIL), plant.name());
        }
    }

    @Test
    void wildPlantsRegrowInAboutFifteenMinutes() {
        double chance = ResourcePlant.regrowChance(3);
        double expectedTicks = 4096.0 / 3 / chance;
        assertEquals(ResourcePlant.REGROW_TICKS, expectedTicks, 1);
        assertEquals(0, ResourcePlant.regrowChance(0));
        Rng rng = Rng.of(5);
        for (int i = 0; i < 100; i++) {
            int tray = ResourcePlant.SOLAR_VINE.trayYield(rng);
            assertTrue(tray >= ResourcePlant.TRAY_MIN && tray <= ResourcePlant.TRAY_MAX);
            assertTrue(ResourcePlant.GRAVITINO_HOST.trayYield(rng) <= 2);
        }
        assertEquals(20 * 60 * 20, ResourcePlant.SOLAR_VINE.trayTicks());
    }

    // ---------------------------------------------------------------- 海底（ADR-0026）

    @Test
    void seabedLifeNeedsALandableLivingSeaPlanet() {
        Galaxy galaxy = GalaxyGenerator.generate(20261016L, 64);
        int seas = 0;
        for (StarSystem system : galaxy.systems()) {
            for (Planet planet : system.bodies()) {
                boolean expected = planet.landable() && !planet.ecology().lifeless() && Fauna.hasSea(planet.liquid());
                assertEquals(expected, ResourceFlora.seabedLife(planet), planet.id() + " " + planet.type() + " " + planet.liquid());
                if (expected) {
                    seas++;
                }
            }
        }
        assertTrue(seas >= 5, "too few planets with seabed life: " + seas);
    }

    @Test
    void kelpPatchesAreDeterministicAndInsideTheChunk() {
        ResourceFlora a = new ResourceFlora(42L, PlanetType.OCEAN, false);
        ResourceFlora b = new ResourceFlora(42L, PlanetType.OCEAN, false);
        ResourceFlora other = new ResourceFlora(43L, PlanetType.OCEAN, false);
        int chunks = 0;
        int patches = 0;
        boolean differs = false;
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                List<ResourceFlora.Spot> spots = a.seabedSpots(cx, cz);
                assertEquals(spots, b.seabedSpots(cx, cz));
                differs |= !spots.equals(other.seabedSpots(cx, cz));
                chunks++;
                if (spots.isEmpty()) {
                    continue;
                }
                patches++;
                assertTrue(spots.size() >= 3 && spots.size() <= 7, "一丛 3–7 株：" + spots.size());
                int minX = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxZ = Integer.MIN_VALUE;
                for (ResourceFlora.Spot spot : spots) {
                    assertEquals(cx, Math.floorDiv(spot.x(), 16));
                    assertEquals(cz, Math.floorDiv(spot.z(), 16));
                    minX = Math.min(minX, spot.x());
                    maxX = Math.max(maxX, spot.x());
                    minZ = Math.min(minZ, spot.z());
                    maxZ = Math.max(maxZ, spot.z());
                }
                // 一丛围着一个中心，最远离中心 PATCH_RADIUS 格
                assertTrue(maxX - minX <= 2 * ResourceFlora.PATCH_RADIUS && maxZ - minZ <= 2 * ResourceFlora.PATCH_RADIUS);
            }
        }
        assertEquals(ResourceFlora.KELP_CHANCE, patches / (double) chunks, 0.06);
        assertTrue(differs, "换了星球种子海带的位置要变");
    }

    @Test
    void clamsAreRareAndInsideTheChunk() {
        ResourceFlora flora = new ResourceFlora(7L, PlanetType.HABITABLE, false);
        int chunks = 0;
        int withClams = 0;
        int pairs = 0;
        for (int cx = -20; cx <= 20; cx++) {
            for (int cz = -20; cz <= 20; cz++) {
                List<ResourceFlora.Spot> clams = flora.clamSpots(cx, cz);
                assertEquals(clams, flora.clamSpots(cx, cz));
                chunks++;
                assertTrue(clams.size() <= 2);
                if (clams.isEmpty()) {
                    continue;
                }
                withClams++;
                if (clams.size() == 2) {
                    pairs++;
                }
                for (ResourceFlora.Spot clam : clams) {
                    assertEquals(cx, Math.floorDiv(clam.x(), 16));
                    assertEquals(cz, Math.floorDiv(clam.z(), 16));
                    assertTrue(clam.roll() >= 0 && clam.roll() < 1);
                }
            }
        }
        assertEquals(ResourceFlora.CLAM_CHANCE, withClams / (double) chunks, 0.04);
        assertEquals(0.3, pairs / (double) withClams, 0.12);
    }

    @Test
    void candleKelpOnlyGrowsOnTheSeabed() {
        ResourcePlant kelp = ResourcePlant.CANDLE_KELP;
        assertEquals("candle_kelp", kelp.id());
        assertEquals("kelp_pod", kelp.product());
        assertEquals(ResourcePlant.Habitat.WATER, kelp.habitat());
        assertFalse(kelp.farmable(), "水培槽种不了海带");
        assertTrue(kelp.wildMin() >= 1 && kelp.wildMax() >= kelp.wildMin());
        // 新植物往后加，前面的序号不变
        assertEquals(ResourcePlant.values().length - 1, kelp.ordinal());
        for (PlanetType type : PlanetType.values()) {
            assertFalse(ResourceFlora.plants(type, false).contains(kelp), "海带不在地表和洞穴的植物里：" + type);
            assertFalse(ResourceFlora.grows(kelp, type, false), type.name());
            ResourceFlora flora = new ResourceFlora(11L, type, false);
            for (Biotope biotope : Biotope.values()) {
                for (double roll = 0; roll < 1; roll += 0.05) {
                    assertTrue(flora.pick(biotope, false, roll) != kelp && flora.pick(biotope, true, roll) != kelp);
                }
            }
        }
    }
}
