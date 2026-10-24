package io.github.sweetcornna.universe.core.deepspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.deepspace.Integrity.Phase;

class IntegrityTest {
    @Test
    void costsFollowTheAdr() {
        assertEquals(1, Part.PLATING.cost());
        assertEquals(8, Part.CONDUIT.cost());
        assertEquals(5, Part.SHIELDING.cost());
        assertEquals(30, Part.REACTOR.cost());
        assertEquals(1, Part.RADIATOR.cost());
    }

    @Test
    void alarmBelowThirtyThenMeltdownAtZero() {
        Integrity integrity = new Integrity();
        assertEquals(Phase.STABLE, integrity.phase());
        for (int i = 0; i < 8; i++) {
            integrity.remove(Part.CONDUIT);
        }
        assertEquals(100 - 64, integrity.value());
        assertEquals(Phase.STABLE, integrity.phase());
        integrity.remove(Part.CONDUIT);
        assertEquals(28, integrity.value());
        assertEquals(Phase.ALARM, integrity.phase(), "低于 30 报警");
        for (int i = 0; i < 4; i++) {
            integrity.remove(Part.CONDUIT);
        }
        assertEquals(0, integrity.value(), "扣到 0 为止");
        assertEquals(Phase.MELTDOWN, integrity.phase());
        assertEquals(Integrity.MELTDOWN_TICKS, integrity.countdown());
    }

    @Test
    void takingTheReactorStartsMeltdownAndCountsAsSalvaged() {
        Integrity integrity = new Integrity();
        integrity.remove(Part.SHIELDING);
        assertEquals(Phase.MELTDOWN, integrity.remove(Part.REACTOR));
        assertTrue(integrity.salvaged());
        assertEquals(65, integrity.value());
        // 倒计时中再拆东西不会重新计时
        for (int i = 0; i < 100; i++) {
            integrity.tick();
        }
        integrity.remove(Part.CONDUIT);
        assertEquals(Integrity.MELTDOWN_TICKS - 100, integrity.countdown());
        boolean exploded = false;
        for (int i = 0; i < Integrity.MELTDOWN_TICKS; i++) {
            exploded |= integrity.tick();
        }
        assertTrue(exploded, "20 秒后炸了");
        assertEquals(Phase.GONE, integrity.phase());
        assertFalse(integrity.tick(), "只炸一次");
    }

    @Test
    void blastHurtsWithinTwentyFourBlocks() {
        assertEquals(Integrity.BLAST_DAMAGE, Integrity.blastDamage(0), 1e-9);
        assertEquals(Integrity.BLAST_EDGE, Integrity.blastDamage(Integrity.BLAST_RADIUS), 1e-9);
        assertTrue(Integrity.blastDamage(12) > 20, "受重伤");
        assertEquals(0, Integrity.blastDamage(24.5), 1e-9);
    }
}
