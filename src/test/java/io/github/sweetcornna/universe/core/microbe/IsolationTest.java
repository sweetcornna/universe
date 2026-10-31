package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

class IsolationTest {
    static final MicrobeStrain MESO = new MicrobeStrain("t#meso", "常温", "Meso", Metabolism.HETEROTROPH, Morphology.BACILLUS, Set.of(),
            283, 305, 315, 0.5, 1, Set.of(SampleSource.SOIL));
    static final MicrobeStrain THERMO = new MicrobeStrain("t#thermo", "嗜热", "Thermo", Metabolism.CHEMOLITHOTROPH, Morphology.COCCUS,
            Set.of(Gene.THERMOSTABLE_ENZYMES, Gene.IRON_SULFUR_OXIDATION), 325, 343, 355, 1.2, 1, Set.of(SampleSource.HOT_SPRING));
    static final MicrobeStrain PSYCHRO = new MicrobeStrain("t#psychro", "嗜冷", "Psychro", Metabolism.HETEROTROPH, Morphology.COCCUS,
            Set.of(Gene.ANTIFREEZE_PROTEIN), 259, 280, 290, 0.5, 1, Set.of(SampleSource.ICE));

    @Test
    void onlyStrainsThatGrowAtTheSettingAppear() {
        List<MicrobeStrain> sample = List.of(MESO, THERMO, PSYCHRO);
        Rng rng = Rng.of(1);
        for (int i = 0; i < 50; i++) {
            assertEquals(THERMO, Isolation.isolate(sample, 343, rng).orElseThrow());
            assertEquals(PSYCHRO, Isolation.isolate(sample, 275, rng).orElseThrow());
        }
        assertTrue(Isolation.isolate(sample, 380, rng).isEmpty());
        assertTrue(Isolation.isolate(List.of(), 300, rng).isEmpty());
    }

    @Test
    void closerToTheOptimumGrowsMoreOften() {
        List<MicrobeStrain> sample = List.of(MESO, PSYCHRO);
        Rng rng = Rng.of(7);
        int psychro = 0;
        for (int i = 0; i < 2000; i++) {
            if (Isolation.isolate(sample, 284, rng).orElseThrow() == PSYCHRO) {
                psychro++;
            }
        }
        double psychroRate = MicrobeStrain.cardinal(259, 280, 290, 284);
        double mesoRate = MicrobeStrain.cardinal(283, 305, 315, 284);
        assertTrue(psychroRate > 0.5 && mesoRate < 0.05, psychroRate + " " + mesoRate);
        double expected = psychroRate / (psychroRate + mesoRate);
        assertEquals(expected, psychro / 2000.0, 0.03);
    }

    @Test
    void incubationTakesARealDayAndAnOvernight() {
        assertEquals(2880, Isolation.ISOLATION_TICKS);
        assertEquals(1920, Isolation.EXPAND_TICKS);
    }

    @Test
    void domesticationOnlyCountsAtTheEdge() {
        Organism organism = Organism.wild(MESO);
        assertFalse(Isolation.domesticates(organism, 305));
        assertFalse(Isolation.domesticates(organism, 300));
        assertTrue(Isolation.domesticates(organism, 287));
        assertTrue(Isolation.domesticates(organism, 313));
        assertFalse(Isolation.domesticates(organism, 316));
        assertEquals(organism, Isolation.expand(organism, 305));
        for (int i = 0; i < 20; i++) {
            organism = Isolation.expand(organism, 313);
        }
        assertEquals(Isolation.DOMESTICATION_MAX, organism.domestication(), 1e-12);
        assertEquals(1.5, organism.productivity(), 1e-12);
        assertEquals(0.05, Isolation.expand(Organism.wild(MESO), 287).domestication(), 1e-12);
    }
}
