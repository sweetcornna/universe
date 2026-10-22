package io.github.sweetcornna.universe.core.civ;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.TechModule;

class TechMerchantTest {
    @Test
    void stockIsDeterministicAndSized() {
        for (Race race : Race.values()) {
            List<TechModule> a = TechMerchant.stock(race, Standing.Tier.STRANGER, 0, 42L);
            assertEquals(TechMerchant.OFFERS, a.size());
            assertEquals(a, TechMerchant.stock(race, Standing.Tier.STRANGER, 0, 42L));
        }
    }

    @Test
    void mostlyFromTheRacePool() {
        for (Race race : Race.values()) {
            int favoured = 0;
            int total = 0;
            for (long seed = 0; seed < 400; seed++) {
                for (TechModule module : TechMerchant.stock(race, Standing.Tier.STRANGER, 0, seed * 31)) {
                    total++;
                    if (TechMerchant.pool(race).contains(module.type())) {
                        favoured++;
                    }
                }
            }
            assertTrue(favoured > total * 0.7, race + ": " + favoured + "/" + total);
        }
    }

    @Test
    void betterStandingBetterGoodsSameTypes() {
        double stranger = 0;
        double kindred = 0;
        for (long seed = 0; seed < 500; seed++) {
            List<TechModule> low = TechMerchant.stock(Race.SCHOLAR, Standing.Tier.STRANGER, 0, seed * 17);
            List<TechModule> high = TechMerchant.stock(Race.SCHOLAR, Standing.Tier.KINDRED, 0, seed * 17);
            for (int i = 0; i < TechMerchant.OFFERS; i++) {
                assertEquals(low.get(i).type(), high.get(i).type());
                stranger += low.get(i).grade().ordinal();
                kindred += high.get(i).grade().ordinal();
            }
            assertTrue(high.getLast().grade().compareTo(Grade.A) >= 0);
        }
        assertTrue(kindred > stranger * 1.5, stranger + " vs " + kindred);
    }

    @Test
    void hostileGetsNothing() {
        assertFalse(TechMerchant.sells(Standing.Tier.HOSTILE));
        assertTrue(TechMerchant.sells(Standing.Tier.STRANGER));
        assertEquals(0, TechMerchant.bonus(Standing.Tier.ACQUAINTED));
        assertEquals(1, TechMerchant.bonus(Standing.Tier.REGULAR));
        assertEquals(2, TechMerchant.bonus(Standing.Tier.KINDRED));
    }
}
