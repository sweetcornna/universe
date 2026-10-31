package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OrganismTest {
    @Test
    void eachAddedGeneSlowsGrowthByATenth() {
        Organism organism = Organism.wild(EarthMicrobes.SYNECHOCOCCUS);
        assertEquals(1, organism.growthMultiplier(), 1e-12);
        organism = organism.withGene(Gene.ANTIFREEZE_PROTEIN).withGene(Gene.UV_SHIELD);
        assertEquals(0.81, organism.growthMultiplier(), 1e-12);
        assertEquals(EarthMicrobes.SYNECHOCOCCUS.doublingTicks() / 0.81, organism.doublingTicks(), 1e-9);
    }

    @Test
    void editingRules() {
        Organism organism = Organism.wild(EarthMicrobes.SYNECHOCOCCUS);
        assertEquals(GeneEditing.Result.ALREADY_PRESENT, organism.canAdd(Gene.OXYGENIC_PHOTOSYNTHESIS));
        organism = organism.withGene(Gene.DNA_REPAIR);
        assertEquals(GeneEditing.Result.ALREADY_PRESENT, organism.canAdd(Gene.DNA_REPAIR));
        organism = organism.withGene(Gene.PROTON_PUMP).withGene(Gene.UV_SHIELD).withGene(Gene.ANTIFREEZE_PROTEIN);
        assertEquals(Gene.MAX_ADDED, organism.added().size());
        assertEquals(GeneEditing.Result.FULL, organism.canAdd(Gene.LUCIFERASE));
        Organism full = organism;
        assertThrows(IllegalStateException.class, () -> full.withGene(Gene.LUCIFERASE));
        assertThrows(IllegalArgumentException.class,
                () -> new Organism(EarthMicrobes.SYNECHOCOCCUS, List.of(Gene.LUCIFERASE, Gene.LUCIFERASE), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Organism(EarthMicrobes.SYNECHOCOCCUS, List.of(Gene.CARBON_CONCENTRATING), 0));
    }

    @Test
    void functionsAreNativePlusAdded() {
        Organism organism = Organism.wild(EarthMicrobes.ACIDITHIOBACILLUS).withGene(Gene.METHANOGENESIS).withGene(Gene.DNA_REPAIR);
        assertEquals(Set.of(Gene.IRON_SULFUR_OXIDATION, Gene.METHANOGENESIS), organism.functions());
        assertEquals(List.of(BioProcess.BIOLEACHING, BioProcess.BIOMETHANATION), BioProcess.available(organism));
        assertEquals(List.of(BioProcess.ALGAE), BioProcess.available(Organism.wild(EarthMicrobes.SYNECHOCOCCUS)));
        assertTrue(BioProcess.available(Organism.wild(EarthMicrobes.PSYCHROBACTER)).isEmpty());
    }

    @Test
    void addedToleranceGenesWidenTheRange() {
        Tolerance wild = Organism.wild(EarthMicrobes.SYNECHOCOCCUS).tolerance();
        assertEquals(288.15, wild.minK(), 1e-9);
        assertEquals(318.15, wild.maxK(), 1e-9);
        assertFalse(wild.radiation() || wild.acid() || wild.uv() || wild.salt());
        assertEquals(Tolerance.NEUTROPHILE_PH, wild.minPH());
        assertEquals(Tolerance.ORDINARY_GY, wild.maxDoseGy());

        Tolerance hardy = Organism.wild(EarthMicrobes.SYNECHOCOCCUS)
                .withGene(Gene.ANTIFREEZE_PROTEIN).withGene(Gene.THERMOSTABLE_ENZYMES).withGene(Gene.DNA_REPAIR).withGene(Gene.UV_SHIELD)
                .tolerance();
        assertEquals(273.15, hardy.minK(), 1e-9);
        assertEquals(338.15, hardy.maxK(), 1e-9);
        assertEquals(311.15, hardy.optK(), 1e-9);
        assertTrue(hardy.radiation() && hardy.uv());
        assertEquals(Tolerance.RADIORESISTANT_GY, hardy.maxDoseGy());
        assertFalse(hardy.acid() || hardy.salt());
        assertTrue(hardy.grows(280));
        assertFalse(wild.grows(280));

        // 不会低过活细胞的下限，也不会高过 122 °C
        MicrobeStrain cold = new MicrobeStrain("t#c", "冷", "Cold", Metabolism.HETEROTROPH, Morphology.COCCUS, Set.of(), 262, 280, 290,
                0.5, 1, Set.of(SampleSource.ICE));
        assertEquals(MicrobeStrain.MIN_LIFE_K, Organism.wild(cold).withGene(Gene.ANTIFREEZE_PROTEIN).tolerance().minK());
        MicrobeStrain hot = new MicrobeStrain("t#h", "热", "Hot", Metabolism.HETEROTROPH, Morphology.COCCUS, Set.of(), 300, 370, 385,
                0.5, 1, Set.of(SampleSource.HOT_SPRING));
        assertEquals(MicrobeStrain.MAX_LIFE_K, Organism.wild(hot).withGene(Gene.THERMOSTABLE_ENZYMES).tolerance().maxK());
        // 范围往上推得多，最适温度跟着挪到中点以上
        Tolerance pushed = Organism.wild(EarthMicrobes.PSYCHROBACTER).withGene(Gene.THERMOSTABLE_ENZYMES).tolerance();
        assertEquals(321.15, pushed.maxK(), 1e-9);
        assertTrue(pushed.optK() > (pushed.minK() + pushed.maxK()) / 2);
        assertEquals(1, pushed.growthFactor(pushed.optK()), 1e-12);
        // 嗜酸、嗜盐、超嗜热古菌的耐辐射
        Tolerance acid = Organism.wild(EarthMicrobes.ACIDITHIOBACILLUS).tolerance();
        assertTrue(acid.acid());
        assertEquals(Tolerance.ACIDOPHILE_PH, acid.minPH());
        assertTrue(Organism.wild(EarthMicrobes.RHIZOBIUM).withGene(Gene.COMPATIBLE_SOLUTES).tolerance().salt());
        MicrobeStrain archaeon = new MicrobeStrain("t#a", "古", "Archaeon", Metabolism.HETEROTROPH, Morphology.ARCHAEON,
                Set.of(Gene.THERMOSTABLE_ENZYMES), 340, 368, 380, 0.6, 1, Set.of(SampleSource.HOT_SPRING));
        assertEquals(Tolerance.HYPERTHERMOPHILE_RADIORESISTANT_GY,
                Organism.wild(archaeon).withGene(Gene.DNA_REPAIR).tolerance().maxDoseGy());

        // 天生就有抗冻蛋白的不能再加
        assertEquals(GeneEditing.Result.ALREADY_PRESENT, Organism.wild(EarthMicrobes.PSYCHROBACTER).canAdd(Gene.ANTIFREEZE_PROTEIN));
    }

    @Test
    void domesticationRaisesProductivity() {
        Organism organism = Organism.wild(EarthMicrobes.RHIZOBIUM).withDomestication(0.2);
        assertEquals(1.2, organism.productivity(), 1e-12);
        assertEquals(Isolation.DOMESTICATION_MAX, organism.withDomestication(3).domestication());
        assertEquals(0, organism.withDomestication(-1).domestication());
    }
}
