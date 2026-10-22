package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShieldMathTest {
    private static ShieldMath.State charge(ShieldMath.State state, int ticks, double supplied) {
        for (int i = 0; i < ticks; i++) {
            state = ShieldMath.tick(state, supplied);
        }
        return state;
    }

    @Test
    void chargesToTheRaiseLineInAMinute() {
        ShieldMath.State state = charge(ShieldMath.State.EMPTY, 1199, ShieldMath.CHARGE_POWER);
        assertFalse(state.raised());
        state = ShieldMath.tick(state, ShieldMath.CHARGE_POWER);
        assertTrue(state.raised());
        assertEquals(ShieldMath.RAISE_ENERGY, state.energy(), 1e-6);
    }

    @Test
    void upkeepComesOutOfTheChargeOnceRaised() {
        ShieldMath.State raised = new ShieldMath.State(ShieldMath.RAISE_ENERGY, true);
        ShieldMath.State next = ShieldMath.tick(raised, ShieldMath.CHARGE_POWER);
        assertEquals(ShieldMath.RAISE_ENERGY + ShieldMath.CHARGE_POWER - ShieldMath.UPKEEP_POWER, next.energy(), 1e-6);
        // 没电时从存的能量里扣
        ShieldMath.State dark = ShieldMath.tick(raised, 0);
        assertEquals(ShieldMath.RAISE_ENERGY - ShieldMath.UPKEEP_POWER, dark.energy(), 1e-6);
        // 没展开时没电也不漏
        ShieldMath.State idle = new ShieldMath.State(1000, false);
        assertEquals(1000, ShieldMath.tick(idle, 0).energy(), 1e-6);
    }

    @Test
    void fullGeneratorOnlyAsksForUpkeep() {
        ShieldMath.State full = new ShieldMath.State(ShieldMath.CAPACITY, true);
        assertEquals(ShieldMath.UPKEEP_POWER, ShieldMath.demand(full));
        assertEquals(ShieldMath.CAPACITY, ShieldMath.tick(full, ShieldMath.UPKEEP_POWER).energy(), 1e-6);
        assertEquals(ShieldMath.CHARGE_POWER, ShieldMath.demand(ShieldMath.State.EMPTY));
        // 冲过上限的部分不要
        assertEquals(ShieldMath.CAPACITY, ShieldMath.tick(new ShieldMath.State(ShieldMath.CAPACITY - 10, true), 5000).energy(), 1e-6);
    }

    @Test
    void collapsesWhenDrainedAndWaitsForTheRaiseLineAgain() {
        ShieldMath.State state = ShieldMath.tick(new ShieldMath.State(ShieldMath.UPKEEP_POWER / 2.0, true), 0);
        assertFalse(state.raised());
        assertEquals(0, state.energy(), 1e-9);
        state = charge(state, 600, ShieldMath.CHARGE_POWER);
        assertFalse(state.raised(), "half way to the raise line");
    }

    @Test
    void radiusGrowsFromTheRaiseLineToFull() {
        assertEquals(ShieldMath.MIN_RADIUS, ShieldMath.radius(ShieldMath.RAISE_ENERGY), 1e-9);
        assertEquals(ShieldMath.MIN_RADIUS, ShieldMath.radius(10), 1e-9);
        assertEquals((ShieldMath.MIN_RADIUS + ShieldMath.RADIUS) / 2,
                ShieldMath.radius((ShieldMath.RAISE_ENERGY + ShieldMath.FULL_ENERGY) / 2), 1e-9);
        assertEquals(ShieldMath.RADIUS, ShieldMath.radius(ShieldMath.FULL_ENERGY), 1e-9);
        assertEquals(ShieldMath.RADIUS, ShieldMath.radius(ShieldMath.CAPACITY), 1e-9);
    }

    @Test
    void absorbTakesEnergyAndPassesTheRest() {
        ShieldMath.Absorbed all = ShieldMath.absorb(50_000, 20);
        assertEquals(30_000, all.energy(), 1e-9);
        assertEquals(0, all.leftover(), 1e-9);
        ShieldMath.Absorbed part = ShieldMath.absorb(5_000, 20);
        assertEquals(0, part.energy(), 1e-9);
        assertEquals(15, part.leftover(), 1e-6);
        assertEquals(7, ShieldMath.absorb(0, 7).leftover(), 1e-6);
    }

    @Test
    void segmentEntersTheSphereFromOutside() {
        // 半球：从正上方 200 格往下落到中心
        double t = ShieldMath.entry(0, 200, 0, 0, 0, 0, 40, 40);
        assertEquals(160 / 200.0, t, 1e-9);
        // 斜着擦过去
        assertEquals(-1, ShieldMath.entry(-300, 50, 0, 300, 50, 0, 40, 40));
        // 起点在里面
        assertEquals(-1, ShieldMath.entry(0, 10, 0, 0, 300, 0, 40, 40));
        // 还没飞到
        assertEquals(-1, ShieldMath.entry(0, 400, 0, 0, 300, 0, 40, 40));
        assertTrue(ShieldMath.inside(20, 20, 20, 35, 35));
        assertFalse(ShieldMath.inside(20, 20, 20, 34, 34));
    }

    @Test
    void bigDomesAreFlattened() {
        assertEquals(ShieldMath.MIN_RADIUS, ShieldMath.height(ShieldMath.MIN_RADIUS), 1e-9);
        assertEquals(ShieldMath.MAX_HEIGHT, ShieldMath.height(ShieldMath.RADIUS), 1e-9);
        double r = ShieldMath.RADIUS;
        double h = ShieldMath.height(r);
        // 从正上方落下来，碰在 64 格高的顶上
        double t = ShieldMath.entry(0, 200, 0, 0, 0, 0, r, h);
        assertEquals((200 - h) / 200, t, 1e-9);
        // 水平 150 格、离地 30 格已经在外面；离地 20 格在里面
        assertFalse(ShieldMath.inside(150, 30, 0, r, h));
        assertTrue(ShieldMath.inside(150, 20, 0, r, h));
        assertEquals(h, ShieldMath.surface(0, 0, r, h), 1e-9);
        assertEquals(h * Math.sqrt(1 - 0.25), ShieldMath.surface(r / 2, 0, r, h), 1e-9);
        assertTrue(Double.isNaN(ShieldMath.surface(r + 1, 0, r, h)));
    }
}
