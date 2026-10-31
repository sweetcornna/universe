package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

class StrainNamesTest {
    @Test
    void epithetDropsTrailingVowels() {
        assertEquals("kelarensis", StrainNames.epithet("Kelar"));
        assertEquals("mubensis", StrainNames.epithet("Mubei"));
        assertEquals("moonensis", StrainNames.epithet("Moon"));
        assertEquals("aoensis", StrainNames.epithet("Ao"));
    }

    @Test
    void genusComesFromTraitAndShape() {
        StrainNames.Name thermo = StrainNames.name(Metabolism.HETEROTROPH, Morphology.COCCUS, Set.of(Gene.THERMOSTABLE_ENZYMES),
                "克拉尔", "Kelar");
        assertEquals("克拉尔嗜热球菌", thermo.zh());
        assertEquals("Thermococcus kelarensis", thermo.en());

        StrainNames.Name alga = StrainNames.name(Metabolism.OXYGENIC_PHOTOTROPH, Morphology.SPIRILLUM,
                Set.of(Gene.OXYGENIC_PHOTOSYNTHESIS), "克拉尔", "Kelar");
        assertEquals("克拉尔蓝螺旋藻", alga.zh());
        assertEquals("Cyanospira kelarensis", alga.en());

        StrainNames.Name methanogen = StrainNames.name(Metabolism.METHANOGEN, Morphology.ARCHAEON,
                Set.of(Gene.METHANOGENESIS, Gene.THERMOSTABLE_ENZYMES), "穆贝", "Mubei");
        assertEquals("穆贝产甲烷古菌", methanogen.zh());
        assertEquals("Methanoarchaeum mubensis", methanogen.en());

        StrainNames.Name endolith = StrainNames.name(Metabolism.OXYGENIC_PHOTOTROPH, Morphology.COCCUS,
                Set.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.UV_SHIELD, Gene.COMPATIBLE_SOLUTES), "瑞法露", "Rifalu");
        assertEquals("瑞法露石生球藻", endolith.zh());
        assertEquals("Lithocystis rifalensis", endolith.en());

        StrainNames.Name plain = StrainNames.name(Metabolism.HETEROTROPH, Morphology.FILAMENT, Set.of(), "克拉尔", "Kelar");
        assertEquals("克拉尔异丝菌", plain.zh());
        assertEquals("Exothrix kelarensis", plain.en());
    }
}
