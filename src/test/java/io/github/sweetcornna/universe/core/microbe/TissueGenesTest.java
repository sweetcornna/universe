package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ecology.Biotope;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

class TissueGenesTest {
    @Test
    void fishFromColdAndDeepWater() {
        assertEquals(Set.of(Gene.ANTIFREEZE_PROTEIN), TissueGenes.fish(Habitat.ICE));
        assertEquals(Set.of(Gene.LUCIFERASE), TissueGenes.fish(Habitat.DEEP));
        assertEquals(Set.of(Gene.PROTON_PUMP), TissueGenes.fish(Habitat.ACID));
        assertTrue(TissueGenes.fish(Habitat.FRESH).isEmpty());
    }

    @Test
    void creaturesFollowWhereTheyLive() {
        assertEquals(Set.of(Gene.LUCIFERASE, Gene.ANTIFREEZE_PROTEIN),
                TissueGenes.creature(PlanetType.HABITABLE, List.of(Biotope.COLD), true));
        assertEquals(Set.of(Gene.DNA_REPAIR, Gene.COMPATIBLE_SOLUTES),
                TissueGenes.creature(PlanetType.IRRADIATED, List.of(Biotope.ARID, Biotope.WASTE), false));
        assertEquals(Set.of(Gene.ANTIFREEZE_PROTEIN), TissueGenes.creature(PlanetType.FROZEN, List.of(Biotope.WASTE), false));
        assertTrue(TissueGenes.creature(PlanetType.HABITABLE, List.of(Biotope.MEADOW), false).isEmpty());
    }

    @Test
    void resourcePlantsFollowTheirPrototypes() {
        assertEquals(Set.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.CARBON_CONCENTRATING), TissueGenes.plant(ResourcePlant.OXYGEN_BLADDER));
        assertEquals(Set.of(Gene.ANTIFREEZE_PROTEIN), TissueGenes.plant(ResourcePlant.FROSTWORT));
        assertEquals(Set.of(Gene.DNA_REPAIR), TissueGenes.plant(ResourcePlant.GAMMA_WEED));
        assertEquals(Set.of(Gene.THERMOSTABLE_ENZYMES), TissueGenes.plant(ResourcePlant.SOLAR_VINE));
        assertEquals(Set.of(Gene.LUCIFERASE, Gene.OXYGENIC_PHOTOSYNTHESIS), TissueGenes.plant(ResourcePlant.CANDLE_KELP));
        assertTrue(TissueGenes.plant(ResourcePlant.SALT_CRYSTAL).isEmpty());
    }
}
