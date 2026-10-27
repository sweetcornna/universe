package io.github.sweetcornna.universe.core.microbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CultureTest {
    private static final Organism ALGA = Organism.wild(EarthMicrobes.SYNECHOCOCCUS);
    private static final double OPT = EarthMicrobes.SYNECHOCOCCUS.optK();

    @Test
    void growsAlongTheLogisticCurve() {
        Culture culture = Culture.inoculated();
        assertEquals(Culture.INOCULUM, culture.density());
        // 0.02 → 0.95 要 ln(0.95/0.05 × 0.98/0.02) / μ
        double mu = Math.log(2) / ALGA.doublingTicks();
        int ticks = (int) Math.ceil(Math.log(0.95 / 0.05 * 0.98 / 0.02) / mu);
        culture.step(ALGA, ticks, true, OPT);
        assertEquals(0.95, culture.density(), 1e-3);
        assertEquals(Culture.State.GROWING, culture.state());
        // 现实 3 小时翻一倍，游戏里 18 秒，三分钟左右长满
        assertEquals(360, ALGA.doublingTicks(), 1e-9);
        assertTrue(ticks > 2.5 * 1200 && ticks < 3.5 * 1200, "ticks to fill: " + ticks);
        culture.step(ALGA, 100_000, true, OPT);
        assertEquals(1, culture.density(), 1e-9);
    }

    @Test
    void stepSizeDoesNotMatter() {
        Culture big = Culture.inoculated();
        Culture small = Culture.inoculated();
        big.step(ALGA, 2000, true, OPT - 10);
        for (int i = 0; i < 100; i++) {
            small.step(ALGA, 20, true, OPT - 10);
        }
        assertEquals(big.density(), small.density(), 1e-9);
    }

    @Test
    void slowerAwayFromTheOptimumAndWithBurden() {
        Culture warm = Culture.inoculated();
        Culture cool = Culture.inoculated();
        Culture edited = Culture.inoculated();
        warm.step(ALGA, 2400, true, OPT);
        cool.step(ALGA, 2400, true, 300);
        edited.step(ALGA.withGene(Gene.DNA_REPAIR).withGene(Gene.UV_SHIELD), 2400, true, OPT);
        assertTrue(warm.density() > edited.density());
        assertTrue(edited.density() > cool.density());
    }

    @Test
    void starvesAfterTwoMinutes() {
        Culture culture = new Culture(0.8, 0);
        assertEquals(Culture.State.STARVING, culture.step(ALGA, Culture.STARVE_GRACE_TICKS, false, OPT));
        assertEquals(0.8, culture.density(), 1e-12);
        assertEquals(Culture.State.DYING, culture.step(ALGA, Culture.STARVE_HALF_LIFE_TICKS, false, OPT));
        assertEquals(0.4, culture.density(), 1e-12);
        // 分两步越过宽限期也一样
        Culture split = new Culture(0.8, 0);
        split.step(ALGA, Culture.STARVE_GRACE_TICKS - 100, false, OPT);
        split.step(ALGA, Culture.STARVE_HALF_LIFE_TICKS + 100, false, OPT);
        assertEquals(0.4, split.density(), 1e-12);
        // 有料就不饿了
        culture.step(ALGA, 1, true, OPT);
        assertEquals(0, culture.starvedTicks());
        assertEquals(Culture.State.STARVING, culture.step(ALGA, 100, false, OPT));
    }

    @Test
    void tooColdStopsGrowingWithoutKilling() {
        Culture culture = new Culture(0.8, 0);
        assertEquals(Culture.State.STARVING, culture.step(ALGA, Culture.STARVE_GRACE_TICKS, true, 280));
        assertEquals(0.8, culture.density(), 1e-12);
        assertEquals(Culture.State.DYING, culture.step(ALGA, Culture.STARVE_HALF_LIFE_TICKS, true, 280));
        assertEquals(0.4, culture.density(), 1e-12);
    }

    @Test
    void wrongTemperatureKillsFaster() {
        Culture culture = new Culture(0.8, 0);
        assertEquals(Culture.State.DYING, culture.step(ALGA, Culture.HOSTILE_HALF_LIFE_TICKS, true, 340));
        assertEquals(0.4, culture.density(), 1e-12);
        // 0.4 → 0.01 以下：六个半衰期
        assertEquals(Culture.State.LOST, culture.step(ALGA, Culture.HOSTILE_HALF_LIFE_TICKS * 6, true, 340));
        assertFalse(culture.alive());
        assertEquals(0, culture.density());
        // 死光了就不会再长
        assertEquals(Culture.State.LOST, culture.step(ALGA, 10_000, true, OPT));
        assertEquals(0, culture.density());
    }

    @Test
    void outputFollowsDensityAndProductivity() {
        Culture culture = new Culture(0.5, 0);
        assertEquals(0.5, culture.outputRate(ALGA), 1e-12);
        assertEquals(0.6, culture.outputRate(ALGA.withDomestication(0.2)), 1e-12);
        assertEquals(0, Culture.empty().outputRate(ALGA));
        assertFalse(Culture.empty().alive());
        assertEquals(0, new Culture(0.005, 0).density());
    }
}
