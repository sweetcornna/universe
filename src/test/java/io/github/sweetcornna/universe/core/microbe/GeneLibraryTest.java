package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GeneLibraryTest {
    @Test
    void recordsWhatIsNew() {
        GeneLibrary library = new GeneLibrary();
        GeneLibrary.SequenceResult first = library.sequence(EarthMicrobes.ACIDITHIOBACILLUS.key(),
                EarthMicrobes.ACIDITHIOBACILLUS.genes());
        assertTrue(first.newStrain());
        assertEquals(Set.of(Gene.IRON_SULFUR_OXIDATION, Gene.PROTON_PUMP), first.newGenes());

        GeneLibrary.SequenceResult again = library.sequence(EarthMicrobes.ACIDITHIOBACILLUS.key(),
                EarthMicrobes.ACIDITHIOBACILLUS.genes());
        assertFalse(again.anythingNew());

        // 组织：没有菌的键，只记基因
        GeneLibrary.SequenceResult tissue = library.sequence(null, Set.of(Gene.PROTON_PUMP, Gene.ANTIFREEZE_PROTEIN));
        assertFalse(tissue.newStrain());
        assertEquals(Set.of(Gene.ANTIFREEZE_PROTEIN), tissue.newGenes());
        assertTrue(library.has(Gene.ANTIFREEZE_PROTEIN));
        assertTrue(library.sequenced(EarthMicrobes.ACIDITHIOBACILLUS.key()));
    }

    @Test
    void savesAndLoadsByName() {
        GeneLibrary library = new GeneLibrary();
        library.sequence("s3/p1#rock/0", Set.of(Gene.UV_SHIELD, Gene.DNA_REPAIR));
        assertEquals(List.of("DNA_REPAIR", "UV_SHIELD"), library.geneNames());
        GeneLibrary loaded = GeneLibrary.load(List.of("UV_SHIELD", "DNA_REPAIR", "NO_SUCH_GENE"), library.strains());
        assertEquals(library.genes(), loaded.genes());
        assertEquals(Set.of("s3/p1#rock/0"), loaded.strains());
    }
}
