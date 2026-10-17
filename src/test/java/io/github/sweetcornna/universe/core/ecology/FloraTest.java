package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Ecology;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

class FloraTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);

    private static List<Planet> landable() {
        return GALAXY.systems().stream().flatMap(s -> s.bodies().stream()).filter(Planet::landable).toList();
    }

    @Test
    void sameEcologyGivesSameFlora() {
        for (Planet planet : landable().subList(0, 20)) {
            Flora a = Flora.of(planet);
            Flora b = Flora.of(planet);
            assertEquals(a.plants(), b.plants());
            assertEquals(a.trees(), b.trees());
        }
    }

    @Test
    void lifelessPlanetsHaveNoSpecies() {
        for (Planet planet : landable()) {
            if (planet.ecology().lifeless()) {
                assertTrue(Flora.of(planet).lifeless(), planet.id().toString());
            }
        }
    }

    @Test
    void variantsAreUniquePerForm() {
        for (Planet planet : landable()) {
            Flora flora = Flora.of(planet);
            Map<PlantForm, Set<Integer>> seen = new HashMap<>();
            for (PlantSpecies plant : flora.plants()) {
                assertTrue(plant.variant() >= 0 && plant.variant() < PlantForm.VARIANTS);
                assertTrue(seen.computeIfAbsent(plant.form(), f -> new HashSet<>()).add(plant.variant()),
                        planet.id() + " " + plant);
                assertFalse(plant.biotopes().isEmpty());
                assertTrue(plant.biotopes().stream().allMatch(b -> plant.form().affinity(b) > 0), plant.toString());
                assertEquals(plant.body(), flora.plantColor(plant.form(), plant.variant(), 0));
                assertEquals(plant.detail(), flora.plantColor(plant.form(), plant.variant(), 1));
            }
            assertTrue(flora.trees().size() <= TreeSpecies.MAX);
            for (int i = 0; i < flora.trees().size(); i++) {
                assertEquals(i, flora.trees().get(i).index());
            }
        }
    }

    @Test
    void habitablePlanetsCoverTheirMainBiotopes() {
        Ecology ecology = new Ecology(0.8, 0.7, 0.7, 8, 6, 42);
        for (long seed = 0; seed < 50; seed++) {
            Ecology e = new Ecology(ecology.flora(), ecology.trees(), ecology.fauna(), 8, 6, seed);
            Flora flora = Flora.generate(e, PlanetType.HABITABLE, Palette.HOME);
            assertEquals(8, flora.plants().size());
            Set<Biotope> covered = EnumSet.noneOf(Biotope.class);
            flora.plants().forEach(p -> covered.addAll(p.biotopes()));
            assertTrue(covered.containsAll(List.of(Biotope.MEADOW, Biotope.FOREST, Biotope.WETLAND, Biotope.COLD, Biotope.ARID)));
            assertTrue(flora.trees().stream().anyMatch(t -> t.biotopes().contains(Biotope.FOREST)), "seed " + seed);
        }
    }

    @Test
    void layoutRespectsBiotopes() {
        Ecology ecology = new Ecology(0.9, 0.8, 0.5, 9, 4, 7);
        Flora flora = Flora.generate(ecology, PlanetType.HABITABLE, Palette.HOME);
        FloraLayout layout = new FloraLayout(flora, ecology);
        Rng rng = Rng.of(1);
        int grown = 0;
        for (int i = 0; i < 20000; i++) {
            int x = rng.range(-5000, 5000);
            int z = rng.range(-5000, 5000);
            BiomeKind biome = BiomeKind.values()[rng.nextInt(BiomeKind.values().length)];
            PlantSpecies plant = layout.plantAt(x, z, biome, rng.nextDouble(), rng.nextDouble());
            if (plant != null) {
                grown++;
                assertTrue(plant.biotopes().contains(Biotope.of(biome)), biome + " " + plant);
            }
            if (Biotope.of(biome) == null) {
                assertEquals(0, layout.treeAttempts(x, z, biome, rng.nextDouble()));
            }
        }
        assertTrue(grown > 2000, "grown " + grown);
        int forestTrees = 0;
        for (int i = 0; i < 1000; i++) {
            forestTrees += layout.treeAttempts(rng.range(-5000, 5000), rng.range(-5000, 5000), BiomeKind.FOREST, rng.nextDouble());
        }
        assertTrue(forestTrees > 2000 && forestTrees < 9000, "forest trees " + forestTrees);
    }

    @Test
    void treesStayInBoundsAndHangTogether() {
        Ecology ecology = new Ecology(0.9, 0.9, 0.5, 4, 4, 0);
        for (TreeShape shape : TreeShape.values()) {
            for (long seed = 0; seed < 300; seed++) {
                Flora flora = Flora.generate(new Ecology(0.9, 0.9, 0.5, 4, 4, seed), PlanetType.EXOTIC, Palette.NEUTRAL);
                TreeSpecies base = flora.trees().get(0);
                TreeSpecies species = new TreeSpecies(0, base.name(), shape, base.height(), base.heightRange(),
                        shapeCanopy(shape, seed), base.leafDensity(), base.lean(), seed % 3 == 0, base.wood(), base.leaves(),
                        base.bloom(), seed % 2 == 0 ? 0.1 : 0, base.bloomGlows(), base.biotopes(), 1);
                List<TreeBuilder.Block> blocks = TreeBuilder.build(fix(species), Rng.of(seed));
                assertFalse(blocks.isEmpty(), shape + " " + seed);
                Set<Long> solid = new HashSet<>();
                boolean grounded = false;
                for (TreeBuilder.Block b : blocks) {
                    assertTrue(Math.abs(b.x()) <= TreeBuilder.REACH && Math.abs(b.z()) <= TreeBuilder.REACH, b.toString());
                    assertTrue(b.y() >= 0 && b.y() < TreeBuilder.MAX_HEIGHT, b.toString());
                    if (b.part().structural()) {
                        solid.add(TreeBuilder.pack(b.x(), b.y(), b.z()));
                    }
                    grounded |= b.y() == 0;
                }
                assertTrue(grounded, shape + " " + seed);
                // 主干、枝条、菌盖、晶柱从地面起连成一体（只看共面相邻）
                ArrayDeque<Long> queue = new ArrayDeque<>();
                Set<Long> reached = new HashSet<>();
                for (long key : solid) {
                    if (TreeBuilder.unpackY(key) == 0) {
                        queue.add(key);
                        reached.add(key);
                    }
                }
                while (!queue.isEmpty()) {
                    long key = queue.poll();
                    int x = TreeBuilder.unpackX(key);
                    int y = TreeBuilder.unpackY(key);
                    int z = TreeBuilder.unpackZ(key);
                    int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int[] d : around) {
                        long next = TreeBuilder.pack(x + d[0], y + d[1], z + d[2]);
                        if (solid.contains(next) && reached.add(next)) {
                            queue.add(next);
                        }
                    }
                }
                assertEquals(solid.size(), reached.size(), shape + " seed " + seed + " has floating parts");
            }
        }
    }

    private static int shapeCanopy(TreeShape shape, long seed) {
        return switch (shape) {
            case SHRUB, SPIRAL -> 1 + (int) (seed % 2);
            case COLUMN -> (int) (seed % 4);
            case BAOBAB, CRYSTAL -> 2 + (int) (seed % 2);
            default -> 3 + (int) (seed % 3);
        };
    }

    /** 拿第一个树种的数值换个形态，高度按形态的常见范围调一下。 */
    private static TreeSpecies fix(TreeSpecies s) {
        int height = switch (s.shape()) {
            case SHRUB -> s.height() % 2;
            case COLUMN -> 3 + s.height() % 4;
            case CONIFER -> 7 + s.height() % 6;
            default -> 4 + s.height() % 7;
        };
        boolean thick = s.shape() == TreeShape.BAOBAB || s.thick() && s.shape() != TreeShape.SHRUB && s.shape() != TreeShape.COLUMN;
        return new TreeSpecies(s.index(), s.name(), s.shape(), height, 3, s.canopy(), s.leafDensity(),
                s.shape() == TreeShape.PALM ? 0.5 : s.lean(), thick, s.wood(), s.leaves(), s.bloom(), s.bloomChance(),
                s.bloomGlows(), s.biotopes(), s.abundance());
    }

    @Test
    void neutralFloraHasColorsForEveryState() {
        for (PlantForm form : PlantForm.values()) {
            for (int v = 0; v < PlantForm.VARIANTS; v++) {
                assertNotNull(Flora.NEUTRAL.plantColor(form, v, 0));
            }
        }
        assertTrue(Flora.NEUTRAL.lifeless());
    }
}
