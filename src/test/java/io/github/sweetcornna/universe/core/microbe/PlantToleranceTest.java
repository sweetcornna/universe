package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ecology.ResourceFlora;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class PlantToleranceTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(20261016L, 64);
    private static final Set<Gene> ALL_TOLERANCES = EnumSet.of(Gene.ANTIFREEZE_PROTEIN, Gene.THERMOSTABLE_ENZYMES, Gene.DNA_REPAIR,
            Gene.PROTON_PUMP, Gene.COMPATIBLE_SOLUTES, Gene.UV_SHIELD);

    @Test
    void barriersFollowThePlanet() {
        int checked = 0;
        for (StarSystem system : GALAXY.systems()) {
            for (Planet planet : system.bodies()) {
                if (!planet.landable()) {
                    continue;
                }
                checked++;
                Set<PlantTolerance.Barrier> barriers = PlantTolerance.barriers(planet);
                String where = planet.id().path() + " " + planet.type() + " " + barriers;
                assertEquals(!planet.atmosphere().present(), barriers.contains(PlantTolerance.Barrier.VACUUM), where);
                assertEquals(planet.atmosphere().kind() == Atmosphere.Kind.THIN, barriers.contains(PlantTolerance.Barrier.UV), where);
                assertEquals(planet.temperature() > PlantTolerance.MAX_PLANT_K, barriers.contains(PlantTolerance.Barrier.TOO_HOT), where);
                // 每样都带上的改造种苗：除了真空和太热，哪里都种得了
                boolean possible = barriers.stream().noneMatch(PlantTolerance.Barrier::impossible);
                boolean nativeHere = ResourceFlora.grows(ResourcePlant.STAR_BRAMBLE, planet.type(), planet.ecology().lifeless());
                assertEquals(possible || nativeHere, PlantTolerance.canPlant(ResourcePlant.STAR_BRAMBLE, ALL_TOLERANCES, true, planet), where);
                // 没改过的照原样
                assertEquals(ResourceFlora.grows(ResourcePlant.STAR_BRAMBLE, planet.type(), planet.ecology().lifeless()),
                        PlantTolerance.canPlant(ResourcePlant.STAR_BRAMBLE, ALL_TOLERANCES, false, planet), where);
                // 海底和矿物不能种在地里
                assertFalse(PlantTolerance.canPlant(ResourcePlant.CANDLE_KELP, ALL_TOLERANCES, true, planet));
                assertFalse(PlantTolerance.canPlant(ResourcePlant.SALT_CRYSTAL, ALL_TOLERANCES, true, planet));
            }
        }
        assertTrue(checked > 100);
        assertTrue(PlantTolerance.barriers(GALAXY.homePlanet()).isEmpty());
    }

    @Test
    void frozenPlanetsNeedAntifreezeAndUvShield() {
        int frozen = 0;
        for (StarSystem system : GALAXY.systems()) {
            for (Planet planet : system.bodies()) {
                if (planet.type() != PlanetType.FROZEN || planet.atmosphere().kind() != Atmosphere.Kind.THIN
                        || planet.hasHazard(io.github.sweetcornna.universe.core.galaxy.Hazard.RADIATION)) {
                    continue;
                }
                Set<PlantTolerance.Barrier> barriers = PlantTolerance.barriers(planet);
                if (barriers.contains(PlantTolerance.Barrier.ARID) || barriers.contains(PlantTolerance.Barrier.TOXIC)) {
                    continue;
                }
                frozen++;
                EngineeredPlant antifreeze = new EngineeredPlant(ResourcePlant.STAR_BRAMBLE, List.of(Gene.ANTIFREEZE_PROTEIN));
                assertFalse(antifreeze.canPlant(planet), planet.id().path());
                assertTrue(antifreeze.withGene(Gene.UV_SHIELD).canPlant(planet), planet.id().path());
                // 霜苔天生就有抗冻蛋白，只缺紫外防护
                assertTrue(new EngineeredPlant(ResourcePlant.FROSTWORT, List.of(Gene.UV_SHIELD)).canPlant(planet), planet.id().path());
            }
        }
        assertTrue(frozen > 0, "no frozen planet with a thin atmosphere");
    }

    @Test
    void allowedTypes() {
        // 氧囊草有生命的星球都长
        Set<PlanetType> bladder = PlantTolerance.allowedTypes(ResourcePlant.OXYGEN_BLADDER, Set.of());
        for (PlanetType type : PlanetType.values()) {
            assertEquals(type.landable(), bladder.contains(type), type.name());
        }
        // 改造过的星莓丛：没有障碍的星球都种得了
        assertEquals(EnumSet.of(PlanetType.HABITABLE, PlanetType.OCEAN, PlanetType.EXOTIC),
                PlantTolerance.allowedTypes(ResourcePlant.STAR_BRAMBLE, Set.of()));
        Set<PlanetType> toxic = PlantTolerance.allowedTypes(ResourcePlant.STAR_BRAMBLE, Set.of(Gene.PROTON_PUMP));
        assertTrue(toxic.containsAll(EnumSet.of(PlanetType.HABITABLE, PlanetType.OCEAN, PlanetType.EXOTIC, PlanetType.TOXIC)));
        assertFalse(toxic.contains(PlanetType.FROZEN));
        Set<PlanetType> cold = PlantTolerance.allowedTypes(ResourcePlant.STAR_BRAMBLE, Set.of(Gene.ANTIFREEZE_PROTEIN, Gene.UV_SHIELD));
        assertTrue(cold.contains(PlanetType.FROZEN));
        assertFalse(cold.contains(PlanetType.BARREN));
        Set<PlanetType> barren = PlantTolerance.allowedTypes(ResourcePlant.STAR_BRAMBLE, Set.of(Gene.COMPATIBLE_SOLUTES, Gene.UV_SHIELD));
        assertTrue(barren.contains(PlanetType.BARREN));
        assertFalse(barren.contains(PlanetType.IRRADIATED));
        assertFalse(PlantTolerance.allowedTypes(ResourcePlant.STAR_BRAMBLE, ALL_TOLERANCES).contains(PlanetType.SCORCHED));
        assertTrue(PlantTolerance.allowedTypes(ResourcePlant.CANDLE_KELP, ALL_TOLERANCES).isEmpty());
    }

    @Test
    void trayBonusesComeFromEditedGenesOnly() {
        EngineeredPlant plain = new EngineeredPlant(ResourcePlant.OXYGEN_BLADDER, List.of());
        assertEquals(1, plain.trayGrowth(false), 1e-12);
        assertEquals(EngineeredPlant.FERTILIZER_GROWTH, plain.trayGrowth(true), 1e-12);
        assertEquals(1, plain.trayYield(), 1e-12);
        // 星莓丛天生固氮，已经算在原来的生长时间里
        EngineeredPlant bramble = new EngineeredPlant(ResourcePlant.STAR_BRAMBLE, List.of());
        assertFalse(bramble.selfFertilizing());
        assertEquals(GeneEditing.Result.ALREADY_PRESENT, bramble.canAdd(Gene.NITROGENASE));

        EngineeredPlant fast = new EngineeredPlant(ResourcePlant.FROSTWORT, List.of(Gene.CARBON_CONCENTRATING));
        assertEquals(1.5 * 0.9, fast.trayGrowth(false), 1e-12);
        EngineeredPlant fixer = fast.withGene(Gene.NITROGENASE);
        assertTrue(fixer.selfFertilizing());
        // 固氮的总当施过肥，施肥不再叠
        assertEquals(1.5 * 1.5 * 0.81, fixer.trayGrowth(false), 1e-12);
        assertEquals(fixer.trayGrowth(false), fixer.trayGrowth(true), 1e-12);
        assertEquals(EngineeredPlant.NITROGENASE_YIELD, fixer.trayYield(), 1e-12);
        assertEquals(Set.of(Gene.ANTIFREEZE_PROTEIN, Gene.CARBON_CONCENTRATING, Gene.NITROGENASE), fixer.genes());

        assertThrows(IllegalArgumentException.class, () -> new EngineeredPlant(ResourcePlant.SALT_CRYSTAL, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EngineeredPlant(ResourcePlant.FROSTWORT, List.of(Gene.ANTIFREEZE_PROTEIN)));
        assertFalse(EngineeredPlant.editable(ResourcePlant.SALT_CRYSTAL));
    }
}
