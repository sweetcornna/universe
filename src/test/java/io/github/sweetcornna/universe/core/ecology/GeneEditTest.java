package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class GeneEditTest {
    private static final CreatureTraits PLAIN = new CreatureTraits(1, CreatureTraits.NO_TINT, Temperament.SKITTISH);

    @Test
    void gelGrowsAndBoneMealShrinksByOneStep() {
        assertEquals(1.1, GeneEdit.GROW.apply(PLAIN).orElseThrow().size());
        assertEquals(0.9, GeneEdit.SHRINK.apply(PLAIN).orElseThrow().size());
        // 野生的体型不是整级，照样加一级
        assertEquals(1.03, GeneEdit.GROW.apply(PLAIN.withSize(0.93)).orElseThrow().size());
    }

    @Test
    void sizeStopsAtTheLimitsWithoutRoundingDrift() {
        CreatureTraits traits = PLAIN;
        for (int i = 0; i < 5; i++) {
            traits = GeneEdit.GROW.apply(traits).orElseThrow();
        }
        assertEquals(CreatureTraits.MAX_SIZE, traits.size());
        assertEquals(Optional.empty(), GeneEdit.GROW.apply(traits));
        // 差半级到头的，最后一级只加到头
        assertEquals(CreatureTraits.MAX_SIZE, GeneEdit.GROW.apply(PLAIN.withSize(1.45)).orElseThrow().size());
        traits = PLAIN;
        for (int i = 0; i < 5; i++) {
            traits = GeneEdit.SHRINK.apply(traits).orElseThrow();
        }
        assertEquals(CreatureTraits.MIN_SIZE, traits.size());
        assertEquals(Optional.empty(), GeneEdit.SHRINK.apply(traits));
    }

    @Test
    void dyeAndFeedChangeOnlyTheirTrait() {
        CreatureTraits red = new GeneEdit.Recolor(0xFFB02E26).apply(PLAIN).orElseThrow();
        assertEquals(0xB02E26, red.tint());
        assertEquals(PLAIN.size(), red.size());
        assertEquals(PLAIN.temperament(), red.temperament());
        assertEquals(Optional.empty(), new GeneEdit.Recolor(0xB02E26).apply(red));

        CreatureTraits calm = GeneEdit.CALM.apply(PLAIN).orElseThrow();
        assertEquals(Temperament.DOCILE, calm.temperament());
        assertEquals(Optional.empty(), GeneEdit.CALM.apply(calm));
        assertEquals(Temperament.TERRITORIAL, GeneEdit.ROUSE.apply(calm).orElseThrow().temperament());
        assertEquals(Optional.empty(), GeneEdit.ROUSE.apply(PLAIN.withTemperament(Temperament.TERRITORIAL)));
        // 捕食者喂了生肉也只到有领地意识，不再主动扑人
        assertEquals(Temperament.TERRITORIAL, GeneEdit.ROUSE.apply(PLAIN.withTemperament(Temperament.PREDATOR)).orElseThrow().temperament());
    }

    @Test
    void editingTakesAFewSeconds() {
        assertTrue(GeneEdit.TICKS >= 40 && GeneEdit.TICKS <= 200);
    }
}
