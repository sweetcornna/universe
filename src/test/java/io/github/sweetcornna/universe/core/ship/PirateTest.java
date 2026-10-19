package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PirateTest {
    @Test
    void chanceGrowsWithCargoValueAndStaysBounded() {
        assertEquals(PirateRaid.BASE_CHANCE, PirateRaid.chance(0), 1e-9);
        assertEquals(PirateRaid.BASE_CHANCE, PirateRaid.chance(-50), 1e-9);
        double previous = 0;
        for (double value = 0; value <= 100_000; value += 500) {
            double chance = PirateRaid.chance(value);
            assertTrue(chance >= previous && chance < PirateRaid.MAX_CHANCE, "货值 " + value);
            previous = chance;
        }
        assertEquals((PirateRaid.BASE_CHANCE + PirateRaid.MAX_CHANCE) / 2, PirateRaid.chance(PirateRaid.HALF_VALUE), 1e-9);
    }

    @Test
    void twoToFourFightersMoreForRicherCargo() {
        for (double roll = 0; roll < 1; roll += 0.05) {
            for (double value : new double[] {0, 1000, 5000, 1e6}) {
                int n = PirateRaid.fighters(value, roll);
                assertTrue(n >= 2 && n <= 4);
            }
        }
        assertEquals(2, PirateRaid.fighters(0, 0.99));
        assertEquals(4, PirateRaid.fighters(1e7, 0.5));
        assertEquals(200, PirateRaid.bounty(1));
        assertEquals(600, PirateRaid.bounty(3));
        assertEquals(PirateRaid.CHECK_MIN, PirateRaid.nextCheck(0));
        assertTrue(PirateRaid.nextCheck(0.999) < PirateRaid.CHECK_MAX);
    }

    /** 一架战机对着一个目标飞若干刻，记下距离和开火次数。 */
    private static final class Run {
        final double[] distances;
        int shots;
        double maxSpeed;

        Run(PirateFlight flight, double[] start, double[] targetVel, int ticks, double hull, double shield) {
            this.distances = new double[ticks];
            double[] pos = start.clone();
            double[] vel = {0, 0, 0};
            double[] target = {0, 0, 0};
            for (int t = 0; t < ticks; t++) {
                PirateFlight.Step step = flight.step(pos, vel, target, targetVel, 3, hull, shield);
                vel = step.velocity();
                pos = PirateFlight.add(pos, vel);
                target = PirateFlight.add(target, targetVel);
                this.maxSpeed = Math.max(this.maxSpeed, ShipCombat.length(vel));
                this.distances[t] = ShipCombat.length(PirateFlight.sub(target, pos));
                if (step.fire()) {
                    this.shots++;
                }
            }
        }

        /** 冲到 {@code near} 以内、再拉开到 {@code far} 以外，算一轮；数一共几轮。 */
        int passes(double near, double far) {
            int passes = 0;
            boolean close = false;
            for (double d : this.distances) {
                if (!close && d < near) {
                    close = true;
                    passes++;
                } else if (close && d > far) {
                    close = false;
                }
            }
            return passes;
        }
    }

    @Test
    void fighterMakesRepeatedAttackRunsAndFires() {
        Run run = new Run(new PirateFlight(1, 0, 3), new double[] {0, 0, -90}, new double[] {0, 0, 0}, 800, 1, 1);
        assertTrue(run.passes(22, 45) >= 3, "俯冲的轮数 " + run.passes(22, 45));
        assertTrue(run.shots >= 9, "开火 " + run.shots + " 发");
        assertTrue(run.maxSpeed <= PirateFlight.SPEED + 1e-9, "最高速度 " + run.maxSpeed);
        for (double d : run.distances) {
            assertTrue(d > 3, "撞上了目标");
        }
    }

    @Test
    void fighterCatchesAMovingTarget() {
        Run run = new Run(new PirateFlight(7, 1, 2), new double[] {60, 10, -60}, new double[] {1.4, 0, 0}, 400, 1, 1);
        double nearest = Double.MAX_VALUE;
        for (double d : run.distances) {
            nearest = Math.min(nearest, d);
        }
        assertTrue(nearest < 25, "离移动的目标最近 " + nearest);
        assertTrue(run.shots > 0);
    }

    @Test
    void damagedFighterBacksOffWithoutFiring() {
        PirateFlight flight = new PirateFlight(3, 0, 2);
        Run run = new Run(flight, new double[] {0, 0, -30}, new double[] {0, 0, 0}, 200, 0.2, 0.3);
        assertEquals(PirateFlight.Mode.REGROUP, flight.mode());
        assertEquals(0, run.shots);
        assertTrue(run.distances[run.distances.length - 1] > 60, "后撤到 " + run.distances[run.distances.length - 1]);
    }

    @Test
    void regroupEndsOnceShieldIsBack() {
        PirateFlight flight = new PirateFlight(5, 0, 2);
        new Run(flight, new double[] {0, 0, -30}, new double[] {0, 0, 0}, 50, 0.2, 0.3);
        assertEquals(PirateFlight.Mode.REGROUP, flight.mode());
        new Run(flight, new double[] {0, 0, -80}, new double[] {0, 0, 0}, PirateFlight.REGROUP_MIN_TICKS + 5, 0.2, 1);
        // 回到进攻（可能已经冲到跟前、正在拉开）；船体还是很低，但刚后撤过，一段时间内不再后撤
        assertNotEquals(PirateFlight.Mode.REGROUP, flight.mode());
    }

    @Test
    void leavingFighterFliesAway() {
        PirateFlight flight = new PirateFlight(9, 0, 2);
        flight.leave();
        Run run = new Run(flight, new double[] {0, 0, -40}, new double[] {0, 0, 0}, 120, 1, 1);
        assertEquals(0, run.shots);
        assertTrue(run.distances[119] > 40 + 150, "撤走后的距离 " + run.distances[119]);
    }
}
