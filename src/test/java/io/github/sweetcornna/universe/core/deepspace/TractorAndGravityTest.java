package io.github.sweetcornna.universe.core.deepspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.gravity.GravityRules;
import io.github.sweetcornna.universe.core.gravity.GravityRules.Weight;

/** 牵引光束、出舱和抛货的位置、引力抓取器的规则。 */
class TractorAndGravityTest {
    @Test
    void tractorRangeGrowsWithTheModule() {
        assertEquals(40, Tractor.range(0), 1e-9);
        assertEquals(60, Tractor.range(0.5), 1e-9);
        assertEquals(40, Tractor.range(-1), 1e-9, "没有负的加成");
    }

    @Test
    void tractorPullsItemsAllTheWayIn() {
        double hull = 4;
        double[] pos = {30, 5, -10};
        double[] v = {0.3, 0, 0.4};
        int ticks = 0;
        while (!Tractor.caught(Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]), hull) && ticks < 400) {
            v = Tractor.pull(new double[] {-pos[0], -pos[1], -pos[2]}, v, hull);
            double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            assertTrue(speed <= 1.5, "不会快得穿过去：" + speed);
            pos[0] += v[0];
            pos[1] += v[1];
            pos[2] += v[2];
            ticks++;
        }
        assertTrue(ticks < 400, "吸进来了");
        assertTrue(ticks > 15, "不是一下子拽过来：" + ticks + " 刻");
    }

    @Test
    void exitSpotsAreOutsideTheHull() {
        double[] center = {100, 64, -50};
        List<double[]> spots = ShipSides.exitSpots(center, 37, 5, 4);
        assertEquals(6, spots.size());
        for (int i = 0; i < 2; i++) {
            double[] spot = spots.get(i);
            assertTrue(Math.hypot(spot[0] - center[0], spot[2] - center[2]) > 5, "放在船外面");
        }
        // 偏航 0 朝 +z：左舷在 +x
        double[] left = ShipSides.exitSpots(new double[] {0, 0, 0}, 0, 3, 2).get(0);
        assertTrue(left[0] > 3 && Math.abs(left[2]) < 1e-9, "左舷 " + left[0] + ", " + left[2]);
    }

    @Test
    void jettisonedItemsLeaveFromTheStern() {
        double[] at = ShipSides.jettison(new double[] {0, 0, 0}, 0, 4, 0);
        assertTrue(at[2] < -4, "船尾在 −z");
        assertTrue(at[5] < 0, "往后飘");
        double[] second = ShipSides.jettison(new double[] {0, 0, 0}, 0, 4, 1);
        assertFalse(at[0] == second[0] && at[1] == second[1], "几件错开");
    }

    @Test
    void onlySmallThingsAreLiftable() {
        assertTrue(GravityRules.liftable(0.9, 1.0));
        assertTrue(GravityRules.liftable(1.4, 1.1), "重型守卫机也抓得动");
        assertFalse(GravityRules.liftable(0.6, 1.8), "玩家那么高的抓不动");
        assertFalse(GravityRules.liftable(2.0, 1.0));
    }

    @Test
    void heldThingsFollowWithoutTeleporting() {
        double[] eye = {0, 70, 0};
        double[] look = {0, 0, 1};
        assertArrayEquals(new double[] {0, 70, 3}, GravityRules.holdPoint(eye, look), 1e-9);
        double[] pos = {0, 70, 15};
        double[] target = GravityRules.holdPoint(eye, look);
        for (int i = 0; i < 40; i++) {
            double[] v = GravityRules.follow(pos, target);
            assertTrue(Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) <= 1.8 + 1e-9);
            pos[0] += v[0];
            pos[1] += v[1];
            pos[2] += v[2];
        }
        assertEquals(3, pos[2], 0.05, "拉到面前 3 格");
    }

    @Test
    void impactDamageByWeightAndSpeed() {
        assertEquals(2, GravityRules.impact(Weight.ITEM, GravityRules.THROW_SPEED), 1e-9);
        assertEquals(6, GravityRules.impact(Weight.CREATURE, GravityRules.THROW_SPEED), 1e-9);
        assertEquals(10, GravityRules.impact(Weight.DRONE, GravityRules.THROW_SPEED), 1e-9);
        assertEquals(5, GravityRules.impact(Weight.DRONE, 1.5), 1e-9, "慢一半，伤害少一半");
        assertEquals(0, GravityRules.impact(Weight.DRONE, 0.2), 1e-9, "飘过去不疼");
    }

    @Test
    void pushConeIsEightBlocksAhead() {
        double[] origin = {0, 0, 0};
        double[] look = {1, 0, 0};
        assertTrue(GravityRules.inCone(origin, look, new double[] {5, 1, 0}));
        assertFalse(GravityRules.inCone(origin, look, new double[] {9, 0, 0}), "太远");
        assertFalse(GravityRules.inCone(origin, look, new double[] {2, 0, 3}), "不在锥形里");
        assertFalse(GravityRules.inCone(origin, look, new double[] {-3, 0, 0}), "身后的不推");
        assertTrue(GravityRules.pushSpeed(1) > GravityRules.pushSpeed(7), "近的推得远");
    }
}
