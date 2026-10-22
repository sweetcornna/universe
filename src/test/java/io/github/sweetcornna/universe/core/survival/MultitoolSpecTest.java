package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechBonuses;

class MultitoolSpecTest {
    @Test
    void slotsGrowWithGrade() {
        assertEquals(4, MultitoolSpec.slots(Grade.C));
        assertEquals(5, MultitoolSpec.slots(Grade.B));
        assertEquals(6, MultitoolSpec.slots(Grade.A));
        assertEquals(8, MultitoolSpec.slots(Grade.S));
        assertEquals(MultitoolSpec.MAX_SLOTS, MultitoolSpec.slots(Grade.S));
    }

    @Test
    void legacyToolIsAnAClassPistolWithSixSlots() {
        MultitoolSpec legacy = MultitoolSpec.LEGACY;
        assertEquals(MultitoolClass.PISTOL, legacy.type());
        assertEquals(Grade.A, legacy.grade());
        assertEquals(6, legacy.working());
        assertEquals(Map.of(Stat.MINING, 0.20), legacy.specialty());
    }

    @Test
    void specialtyFollowsTheClass() {
        assertEquals(Map.of(), new MultitoolSpec(MultitoolClass.RIFLE, Grade.C, 0).specialty(), "C 档没有专长加成");
        assertEquals(Map.of(Stat.DAMAGE, 0.10), new MultitoolSpec(MultitoolClass.RIFLE, Grade.B, 0).specialty());
        assertEquals(Map.of(Stat.SCAN_RADIUS, 0.35), new MultitoolSpec(MultitoolClass.EXPERIMENTAL, Grade.S, 0).specialty());
        assertEquals(Map.of(Stat.DAMAGE, 0.10, Stat.SCAN_RADIUS, 0.10), new MultitoolSpec(MultitoolClass.ALIEN, Grade.A, 0).specialty());
        assertEquals(0.35, new MultitoolSpec(MultitoolClass.PISTOL, Grade.S, 0).bonus(), 1e-9);
    }

    @Test
    void sentinelToolsHitHarderAndStartHalfBroken() {
        double[] expected = {0.15, 0.30, 0.45, 0.60};
        for (Grade grade : Grade.values()) {
            MultitoolSpec sentinel = MultitoolSpec.sentinel(grade);
            assertEquals(expected[grade.ordinal()], sentinel.bonus(), 1e-9);
            assertEquals(Map.of(Stat.DAMAGE, expected[grade.ordinal()]), sentinel.specialty());
            assertEquals(sentinel.slots() / 2, sentinel.broken());
            assertTrue(sentinel.bonus() > new MultitoolSpec(MultitoolClass.RIFLE, grade, 0).bonus());
        }
        assertEquals(2, MultitoolSpec.sentinel(Grade.B).broken(), "5 格坏 2 格（向下取整）");
    }

    @Test
    void brokenSlotsAreTheLastOnesAndRepairOneAtATime() {
        MultitoolSpec spec = MultitoolSpec.sentinel(Grade.S);
        assertEquals(4, spec.working());
        assertFalse(spec.isBroken(3));
        assertTrue(spec.isBroken(4));
        assertTrue(spec.isBroken(7));
        assertFalse(spec.isBroken(8), "格子以外不算坏的");
        MultitoolSpec repaired = spec.repaired();
        assertEquals(5, repaired.working());
        assertFalse(repaired.isBroken(4));
        assertTrue(repaired.isBroken(5));
        MultitoolSpec whole = new MultitoolSpec(MultitoolClass.PISTOL, Grade.C, 0);
        assertSame(whole, whole.repaired());
        assertEquals(4, new MultitoolSpec(MultitoolClass.PISTOL, Grade.C, 99).broken(), "坏格不超过总格数");
    }

    @Test
    void specialtyStacksOnTopOfModules() {
        TechBonuses bonuses = TechBonuses.NONE.with(Map.of(Stat.DAMAGE, 0.2)).with(Map.of(Stat.DAMAGE, 0.1, Stat.MINING, 0.05));
        assertEquals(0.3, bonuses.get(Stat.DAMAGE), 1e-9);
        assertEquals(1.05, bonuses.boost(Stat.MINING), 1e-9);
        assertSame(TechBonuses.NONE, TechBonuses.NONE.with(Map.of()));
    }
}
