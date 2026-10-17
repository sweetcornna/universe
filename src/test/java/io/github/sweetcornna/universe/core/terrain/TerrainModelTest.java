package io.github.sweetcornna.universe.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Landform;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.SimplexNoise;

class TerrainModelTest {
    private static final List<Planet> PLANETS = landablePlanets(20261016L);

    private static List<Planet> landablePlanets(long seed) {
        Galaxy galaxy = GalaxyGenerator.generate(seed, 64);
        List<Planet> planets = new ArrayList<>();
        for (StarSystem system : galaxy.systems()) {
            for (Planet planet : system.bodies()) {
                if (planet.landable()) {
                    planets.add(planet);
                }
            }
        }
        return planets;
    }

    @Test
    void simplexNoiseIsDeterministicAndBounded() {
        SimplexNoise a = new SimplexNoise(7);
        SimplexNoise b = new SimplexNoise(7);
        SimplexNoise c = new SimplexNoise(8);
        double maxAbs = 0;
        boolean differs = false;
        for (int i = 0; i < 2000; i++) {
            double x = i * 0.37 - 300;
            double y = i * 0.11 + 50;
            assertEquals(a.noise(x, y), b.noise(x, y));
            assertEquals(a.noise(x, y, x * 0.5), b.noise(x, y, x * 0.5));
            differs |= a.noise(x, y) != c.noise(x, y);
            maxAbs = Math.max(maxAbs, Math.abs(a.noise(x, y)));
            maxAbs = Math.max(maxAbs, Math.abs(a.noise(x, y, -x)));
            double r = a.ridged(x, y, 5);
            assertTrue(r >= 0 && r <= 1, "ridged out of range: " + r);
        }
        assertTrue(differs);
        assertTrue(maxAbs <= 1.05, "simplex out of range: " + maxAbs);
        assertTrue(maxAbs > 0.6, "simplex suspiciously flat: " + maxAbs);
    }

    @Test
    void sameSeedGivesSameTerrain() {
        Planet planet = PLANETS.get(0);
        TerrainModel a = TerrainModel.of(planet);
        TerrainModel b = TerrainModel.of(planet);
        for (int i = 0; i < 200; i++) {
            int x = i * 37 - 3000;
            int z = i * 53 + 1000;
            assertEquals(a.compute(x, z), b.compute(x, z));
            // 列缓存和直接计算结果相同
            assertEquals(a.compute(x, z), a.sample(x, z));
        }
    }

    @Test
    void heightsStayInsideTheWorld() {
        for (Planet planet : PLANETS) {
            TerrainModel model = TerrainModel.of(planet);
            for (int x = -4000; x <= 4000; x += 97) {
                for (int z = -4000; z <= 4000; z += 101) {
                    double h = model.sample(x, z).height();
                    assertTrue(h >= TerrainModel.MIN_Y + 8 && h <= TerrainModel.MAX_Y - 20,
                            planet.id() + " height " + h + " at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void oceanPlanetsAreMostlyWaterAndHabitableOnesHaveLand() {
        for (Planet planet : PLANETS) {
            if (planet.type() != PlanetType.OCEAN && planet.type() != PlanetType.HABITABLE) {
                continue;
            }
            double land = landFraction(planet);
            if (planet.type() == PlanetType.OCEAN) {
                assertTrue(land < 0.55, planet.id() + " ocean planet land " + land);
            } else {
                assertTrue(land > 0.25, planet.id() + " habitable planet land " + land);
            }
        }
    }

    private static double landFraction(Planet planet) {
        TerrainModel model = TerrainModel.of(planet);
        int land = 0;
        int total = 0;
        for (int x = -6000; x <= 6000; x += 150) {
            for (int z = -6000; z <= 6000; z += 150) {
                total++;
                if (model.sample(x, z).elevation() > 0) {
                    land++;
                }
            }
        }
        return (double) land / total;
    }

    @Test
    void pickedBiomesAreAlwaysRegistered() {
        for (Planet planet : PLANETS) {
            TerrainModel model = TerrainModel.of(planet);
            BiomeLayout layout = BiomeLayout.of(planet);
            Set<BiomeKind> possible = layout.possible();
            for (int x = -5000; x <= 5000; x += 73) {
                for (int z = -5000; z <= 5000; z += 79) {
                    BiomeKind kind = layout.pick(model.sample(x, z));
                    assertTrue(possible.contains(kind), planet.id() + " " + planet.type() + " picked unregistered " + kind);
                }
            }
        }
    }

    @Test
    void planetsShowVarietyOfBiomesAndLandforms() {
        Set<BiomeKind> seen = EnumSet.noneOf(BiomeKind.class);
        Set<Landform> landforms = EnumSet.noneOf(Landform.class);
        for (Planet planet : PLANETS) {
            TerrainModel model = TerrainModel.of(planet);
            BiomeLayout layout = BiomeLayout.of(planet);
            for (int x = -6000; x <= 6000; x += 211) {
                for (int z = -6000; z <= 6000; z += 223) {
                    seen.add(layout.pick(model.sample(x, z)));
                }
            }
            // 母星就是主世界，不用这套地形
            assertTrue(planet.id().equals(GalaxyGenerator.HOME_PLANET_ID) || !planet.terrain().landforms().isEmpty(),
                    planet.id() + " has no landform");
            planet.terrain().landforms().forEach(f -> landforms.add(f.landform()));
        }
        assertTrue(seen.size() >= 20, "only " + seen.size() + " biomes seen: " + seen);
        assertEquals(EnumSet.allOf(Landform.class), landforms);
    }

    @Test
    void airlessPlanetsHaveNoSeaBiomes() {
        for (Planet planet : PLANETS) {
            if (planet.liquid() != Liquid.NONE) {
                continue;
            }
            for (BiomeKind kind : BiomeLayout.of(planet).possible()) {
                assertFalse(kind.ocean(), planet.id() + " dry planet registers " + kind);
            }
        }
    }

    @Test
    void lavaLakeShortcutMatchesFullSample() {
        int lakes = 0;
        for (Planet planet : PLANETS) {
            TerrainModel model = TerrainModel.of(planet);
            if (!model.hasVolcanoes()) {
                continue;
            }
            for (int x = -3000; x <= 3000; x += 6) {
                for (int z = -3000; z <= 3000; z += 6) {
                    boolean full = model.sample(x, z).lava() > 0.5;
                    assertEquals(full, model.lavaLake(x, z), planet.id() + " at " + x + "," + z);
                    if (full) {
                        lakes++;
                    }
                }
            }
        }
        assertTrue(lakes > 0, "no lava lake found");
    }

    @Test
    void caveRoofNeverAboveSurface() {
        for (Planet planet : PLANETS.subList(0, 6)) {
            TerrainModel model = TerrainModel.of(planet);
            for (int x = -2000; x <= 2000; x += 53) {
                for (int z = -2000; z <= 2000; z += 59) {
                    // 网格点上就是周围的最低地表，不会高于本列
                    int gx = Math.floorDiv(x, 8) * 8;
                    int gz = Math.floorDiv(z, 8) * 8;
                    assertTrue(model.caveRoof(gx, gz) <= model.sample(gx, gz).height() + 1e-9, planet.id() + " at " + gx + "," + gz);
                }
            }
        }
    }

    @Test
    void strataCoverTheWholeWorldHeight() {
        Planet planet = PLANETS.get(3);
        int[] colors = Strata.colors(planet.palette(), planet.seed());
        assertEquals(TerrainModel.MAX_Y - TerrainModel.MIN_Y + 1, colors.length);
        Set<Integer> distinct = new java.util.HashSet<>();
        for (int c : colors) {
            distinct.add(c);
        }
        assertTrue(distinct.size() > 20, "strata too uniform: " + distinct.size());
        assertNotEquals(colors[100], 0);
    }
}
