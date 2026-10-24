package io.github.sweetcornna.universe.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

/** 钓鱼图鉴（ADR-0034 §一）：条数、最重的一条和它的星球、放生和放生的加成。 */
class FishingLogTest {
    private static final FishSpecies TROUT = FishTable.byId("brook_trout").orElseThrow();

    @Test
    void keepsTheHeaviestAndWhereItWasCaught() {
        FishingLog log = FishingLog.EMPTY.record(TROUT, 1.2, "甲", "Alpha");
        assertTrue(log.caught(TROUT));
        assertTrue(log.record(TROUT, 1.5));
        assertFalse(log.record(TROUT, 1.0));
        log = log.record(TROUT, 1.0, "乙", "Beta").record(TROUT, 2.4, "丙", "Gamma");
        FishingLog.Entry entry = log.entry(TROUT).orElseThrow();
        assertEquals(3, entry.count());
        assertEquals(2.4, entry.heaviest(), 1e-9);
        assertEquals("丙", entry.planetZh());
        assertEquals("Gamma", entry.planetEn());
        assertEquals(1, log.species());
        assertEquals(3, log.total());
        assertEquals(1, log.species(Habitat.FRESH));
    }

    @Test
    void releaseBlessesTheNextThreeCasts() {
        FishingLog log = FishingLog.EMPTY.release();
        assertEquals(1, log.released());
        for (int i = 0; i < BiteTimer.RELEASE_CASTS; i++) {
            assertTrue(log.blessed(), "第 " + (i + 1) + " 竿");
            log = log.cast();
        }
        assertFalse(log.blessed());
        assertEquals(log, log.cast());
    }

    @Test
    void releaseHalvesTheWait() {
        double plain = 0;
        double blessed = 0;
        for (long seed = 0; seed < 500; seed++) {
            plain += BiteTimer.waitTicks(Bait.NONE, false, Rng.of(seed));
            blessed += BiteTimer.waitTicks(Bait.NONE, true, Rng.of(seed));
        }
        assertEquals(0.5, blessed / plain, 0.01);
        int stink = BiteTimer.waitTicks(Bait.STINK, false, Rng.of(1));
        assertEquals(BiteTimer.waitTicks(Bait.NONE, false, Rng.of(1)) * 0.5, stink, 1);
    }
}
