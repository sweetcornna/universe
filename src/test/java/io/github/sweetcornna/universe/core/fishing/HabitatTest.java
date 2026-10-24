package io.github.sweetcornna.universe.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

/** 水域怎么分（ADR-0034 §一 的表）。 */
class HabitatTest {
    private static Habitat planet(PlanetType type, BiomeKind biome, int depth) {
        return Habitat.of(type, biome, null, false, true, depth);
    }

    @Test
    void planetBiomes() {
        assertEquals(Habitat.FRESH, planet(PlanetType.HABITABLE, BiomeKind.GRASSLAND, 3));
        assertEquals(Habitat.FRESH, planet(PlanetType.HABITABLE, BiomeKind.WETLAND, 2));
        assertEquals(Habitat.FRESH, planet(PlanetType.HABITABLE, BiomeKind.RIVER, 5));
        assertEquals(Habitat.WARM, planet(PlanetType.HABITABLE, BiomeKind.JUNGLE, 3));
        assertEquals(Habitat.COASTAL, planet(PlanetType.OCEAN, BiomeKind.OCEAN, 11));
        assertEquals(Habitat.DEEP, planet(PlanetType.OCEAN, BiomeKind.OCEAN, 12));
        assertEquals(Habitat.DEEP, planet(PlanetType.OCEAN, BiomeKind.DEEP_OCEAN, 5));
        assertEquals(Habitat.COASTAL, planet(PlanetType.HABITABLE, BiomeKind.BEACH, 2));
        assertEquals(Habitat.ICE, planet(PlanetType.HABITABLE, BiomeKind.TUNDRA, 2));
        assertEquals(Habitat.ICE, planet(PlanetType.HABITABLE, BiomeKind.FROZEN_OCEAN, 20));
    }

    @Test
    void planetTypesOverrideBiomes() {
        assertEquals(Habitat.ICE, planet(PlanetType.FROZEN, BiomeKind.GLACIER, 4));
        assertEquals(Habitat.ACID, planet(PlanetType.TOXIC, BiomeKind.FUNGAL_FOREST, 4));
        assertEquals(Habitat.EXOTIC, planet(PlanetType.EXOTIC, BiomeKind.GRASSLAND, 4));
        assertEquals(Habitat.EXOTIC, planet(PlanetType.IRRADIATED, BiomeKind.RADIANT_WASTES, 4));
        // 灼热星球上的水是温泉
        assertEquals(Habitat.WARM, planet(PlanetType.SCORCHED, BiomeKind.ASH_WASTES, 2));
    }

    @Test
    void acidAndCaves() {
        assertEquals(Habitat.ACID, Habitat.of(PlanetType.HABITABLE, BiomeKind.GRASSLAND, null, true, true, 3));
        // 洞里的水不管上面是什么
        assertEquals(Habitat.CAVE, Habitat.of(PlanetType.OCEAN, BiomeKind.DEEP_OCEAN, null, false, false, 30));
        assertEquals(Habitat.CAVE, Habitat.of(null, null, Habitat.Vanilla.OCEAN, true, false, 3));
    }

    @Test
    void overworldFollowsVanillaBiomes() {
        assertEquals(Habitat.COASTAL, Habitat.of(null, null, Habitat.Vanilla.OCEAN, false, true, 8));
        assertEquals(Habitat.DEEP, Habitat.of(null, null, Habitat.Vanilla.OCEAN, false, true, 14));
        assertEquals(Habitat.DEEP, Habitat.of(null, null, Habitat.Vanilla.DEEP_OCEAN, false, true, 3));
        assertEquals(Habitat.FRESH, Habitat.of(null, null, Habitat.Vanilla.RIVER, false, true, 3));
        assertEquals(Habitat.ICE, Habitat.of(null, null, Habitat.Vanilla.COLD, false, true, 3));
        assertEquals(Habitat.WARM, Habitat.of(null, null, Habitat.Vanilla.HOT, false, true, 3));
        assertEquals(Habitat.FRESH, Habitat.of(null, null, Habitat.Vanilla.OTHER, false, true, 3));
    }
}
