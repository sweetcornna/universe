package io.github.sweetcornna.universe.core.ecology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CreatureTraitsTest {
    private static final CreatureSpecies SPECIES = Fauna.FALLBACK.get(0);

    @Test
    void naturalTraitsStayInRange() {
        Set<Integer> tints = new HashSet<>();
        double min = 2;
        double max = 0;
        for (long seed = 0; seed < 2000; seed++) {
            CreatureTraits traits = CreatureTraits.natural(SPECIES, seed);
            assertTrue(traits.size() >= CreatureTraits.NATURAL_MIN && traits.size() <= CreatureTraits.NATURAL_MAX, "体型 " + traits.size());
            for (int shift = 0; shift <= 16; shift += 8) {
                int channel = traits.tint() >> shift & 0xFF;
                assertTrue(channel >= 255 - 24, "色差太大：" + Integer.toHexString(traits.tint()));
            }
            assertEquals(SPECIES.temperament(), traits.temperament(), "性情随物种");
            tints.add(traits.tint());
            min = Math.min(min, traits.size());
            max = Math.max(max, traits.size());
        }
        // 两千只里大小、颜色都有差别
        assertTrue(max - min > 0.25, "体型差 " + (max - min));
        assertTrue(tints.size() > 1000, "颜色只有 " + tints.size() + " 种");
    }

    @Test
    void sameSeedSameTraits() {
        assertEquals(CreatureTraits.natural(SPECIES, 42), CreatureTraits.natural(SPECIES, 42));
        assertNotEquals(CreatureTraits.natural(SPECIES, 42), CreatureTraits.natural(SPECIES, 43));
    }

    @Test
    void editedTraitsAreClamped() {
        CreatureTraits traits = CreatureTraits.of(SPECIES);
        assertEquals(1, traits.size());
        assertEquals(CreatureTraits.NO_TINT, traits.tint());
        assertEquals(CreatureTraits.MAX_SIZE, traits.withSize(9).size());
        assertEquals(CreatureTraits.MIN_SIZE, traits.withSize(0.1).size());
        assertEquals(0x123456, traits.withTint(0xFF123456).tint(), "只留 RGB");
        assertEquals(Temperament.PREDATOR, traits.withTemperament(Temperament.PREDATOR).temperament());
    }
}
