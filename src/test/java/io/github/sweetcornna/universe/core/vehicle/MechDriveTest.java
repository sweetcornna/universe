package io.github.sweetcornna.universe.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MechDriveTest {
    private static double[] walkFor(int ticks, float yaw, double forward, double strafe, boolean jetting) {
        double[] v = {0, 0};
        for (int i = 0; i < ticks; i++) {
            v = MechDrive.walk(v[0], v[1], yaw, forward, strafe, true, true, jetting);
        }
        return v;
    }

    @Test
    void walksForwardAtWalkingSpeed() {
        double[] v = walkFor(40, 0, 1, 0, false);
        assertEquals(0, v[0], 1e-9);
        assertEquals(MechDrive.WALK_SPEED, v[1], 1e-9, "偏航 0 朝 +z 走");
        assertEquals(RoverDrive.MAX_SPEED * 0.6, MechDrive.WALK_SPEED, 1e-9, "探索车的 0.6 倍");
    }

    @Test
    void jetsAreFaster() {
        double[] v = walkFor(60, 90, 1, 0, true);
        assertEquals(-MechDrive.JET_SPEED, v[0], 1e-9, "偏航 90 朝 -x 冲");
        assertEquals(RoverDrive.MAX_SPEED * 1.2, MechDrive.JET_SPEED, 1e-9, "喷气时探索车的 1.2 倍");
    }

    @Test
    void strafesLeftSlower() {
        double[] v = walkFor(40, 0, 0, 1, false);
        assertEquals(MechDrive.WALK_SPEED * MechDrive.STRAFE, v[0], 1e-9, "A 往左（偏航 0 时是 +x）");
        double[] diagonal = walkFor(40, 0, 1, 1, false);
        assertTrue(Math.hypot(diagonal[0], diagonal[1]) <= MechDrive.WALK_SPEED + 1e-9, "斜着走不会更快");
    }

    @Test
    void stopsWhenReleasedOrUnpowered() {
        double[] v = walkFor(40, 0, 1, 0, false);
        double[] stop = MechDrive.walk(v[0], v[1], 0, 0, 0, true, true, false);
        assertTrue(stop[1] < v[1], "松开方向键就减速");
        double[] dead = MechDrive.walk(0, 0, 0, 1, 0, false, true, false);
        assertEquals(0, dead[1], 1e-12, "没燃料走不动");
    }

    @Test
    void jetClimbsAndGravityPullsDown() {
        double vy = 0;
        for (int i = 0; i < 40; i++) {
            vy = MechDrive.climb(vy, true, 1);
        }
        assertEquals(MechDrive.MAX_CLIMB, vy, 1e-9, "喷气往上推，到最快为止");
        assertTrue(MechDrive.climb(0, true, 1) > 0, "地球重力下也推得起来");
        assertTrue(MechDrive.climb(0, true, 1.6) > 0, "重力 1.6 倍的星球也推得起来");
        assertTrue(MechDrive.climb(0, false, 1) < 0, "不喷就往下掉");
    }

    @Test
    void jetRunsOutAndRecharges() {
        MechDrive.Jet jet = new MechDrive.Jet();
        int thrusting = 0;
        for (int i = 0; i < 200; i++) {
            if (jet.tick(true, false)) {
                thrusting++;
            }
        }
        assertEquals(MechDrive.Jet.MAX, thrusting, "一次最多喷 3 秒");
        assertTrue(jet.exhausted());
        assertFalse(jet.tick(true, true), "用空了不能马上再喷");
        for (int i = 0; i < 2 * MechDrive.Jet.RESTART; i++) {
            jet.tick(false, true);
        }
        assertTrue(jet.tick(true, true), "落地歇一会儿能再喷");
        MechDrive.Jet air = new MechDrive.Jet();
        air.tick(true, false);
        double before = air.share();
        air.tick(false, false);
        assertEquals(before, air.share(), 1e-12, "在空中不回");
    }

    @Test
    void stepsOverTwoBlocks() {
        assertTrue(MechDrive.STEP >= 2.0F, "腿长，跨得上两格的台阶");
    }

    @Test
    void bodyTurnsTowardLook() {
        assertEquals(MechDrive.TURN, MechDrive.turn(0, 90), 1e-6, "每刻最多转 5 度");
        assertEquals(-MechDrive.TURN, MechDrive.turn(0, -170), 1e-6, "走近的那边");
        assertEquals(3, MechDrive.turn(0, 3), 1e-6);
    }
}
