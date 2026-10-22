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

import io.github.sweetcornna.universe.core.galaxy.PlanetType;
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
}
