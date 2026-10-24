package io.github.sweetcornna.universe.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;

/** 收线（ADR-0034 §一）：张力、进度、猛冲、线断和脱钩。 */
class ReelingTest {
    private record Result(Reeling.Outcome outcome, int ticks) {
    }

    /** 一直按着或者一直松开。 */
    private static Result steady(FishSpecies species, boolean holding, long seed) {
        Rng rng = Rng.of(seed);
        Reeling reeling = new Reeling(species, rng);
        for (int tick = 1; tick < 10_000; tick++) {
            Reeling.Outcome outcome = reeling.tick(holding, rng);
            if (outcome != Reeling.Outcome.REELING) {
                return new Result(outcome, tick);
            }
        }
        throw new AssertionError("never ended");
    }

    /** 会收线的人：张力低了按住，高了松开，看到要冲了就松开。 */
    private static Result skilled(FishSpecies species, long seed) {
        Rng rng = Rng.of(seed);
        Reeling reeling = new Reeling(species, rng);
        boolean holding = true;
        for (int tick = 1; tick < 10_000; tick++) {
            if (reeling.dashing() || reeling.warning() || reeling.tension() > 0.68) {
                holding = false;
            } else if (reeling.tension() < 0.4) {
                holding = true;
            }
            Reeling.Outcome outcome = reeling.tick(holding, rng);
            if (outcome != Reeling.Outcome.REELING) {
                return new Result(outcome, tick);
            }
        }
        throw new AssertionError("never ended");
    }

    @Test
    void holdingForeverSnapsTheLine() {
        assertEquals(Reeling.Outcome.SNAPPED, steady(FishTable.byId("creek_dace").orElseThrow(), true, 1).outcome());
        assertEquals(Reeling.Outcome.SNAPPED, steady(FishTable.byId("megamouth").orElseThrow(), true, 1).outcome());
    }

    @Test
    void slackLineLetsTheFishGo() {
        Result result = steady(FishTable.byId("creek_dace").orElseThrow(), false, 2);
        assertEquals(Reeling.Outcome.ESCAPED, result.outcome());
        // 从 0.3 降到 0 要 8 刻，再贴着 0 两秒
        assertTrue(result.ticks() > Reeling.SLACK_TICKS && result.ticks() < Reeling.SLACK_TICKS + 20, "脱钩用了 " + result.ticks());
    }

    @Test
    void smallFishTakeFiveOrSixSeconds() {
        FishSpecies dace = FishTable.byId("creek_dace").orElseThrow();
        double total = 0;
        for (long seed = 0; seed < 50; seed++) {
            Result result = skilled(dace, seed);
            assertEquals(Reeling.Outcome.CAUGHT, result.outcome(), "种子 " + seed);
            total += result.ticks();
        }
        double seconds = total / 50 / 20;
        assertTrue(seconds >= 4.5 && seconds <= 7.5, "小鱼平均 " + seconds + " 秒");
    }

    @Test
    void legendsTakeAboutTwentySeconds() {
        FishSpecies megamouth = FishTable.byId("megamouth").orElseThrow();
        double total = 0;
        int caught = 0;
        for (long seed = 0; seed < 50; seed++) {
            Result result = skilled(megamouth, seed);
            if (result.outcome() == Reeling.Outcome.CAUGHT) {
                caught++;
                total += result.ticks();
            }
        }
        assertTrue(caught >= 45, "会收线的人 50 次只钓上 " + caught + " 次");
        double seconds = total / caught / 20;
        assertTrue(seconds >= 17 && seconds <= 28, "传说的平均 " + seconds + " 秒");
    }

    @Test
    void progressOnlyInTheGreenZone() {
        Rng rng = Rng.of(9);
        Reeling reeling = new Reeling(0, 100, rng);
        // 张力从 0.3 往下掉，掉出绿区以后进度不再涨
        reeling.tick(false, rng);
        double inGreen = reeling.progress();
        assertTrue(inGreen > 0);
        for (int i = 0; i < 3; i++) {
            reeling.tick(false, rng);
        }
        double low = reeling.progress();
        reeling.tick(false, rng);
        assertTrue(reeling.tension() < Reeling.GREEN_LOW);
        assertEquals(low, reeling.progress(), 1e-9);
    }
}
