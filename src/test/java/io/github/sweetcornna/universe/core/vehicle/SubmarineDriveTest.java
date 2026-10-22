package io.github.sweetcornna.universe.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubmarineDriveTest {
    private static int ticksToStop(double speed, double throttle) {
        int ticks = 0;
        while (speed != 0 && ticks < 10_000) {
            speed = SubmarineDrive.speed(speed, throttle, true, true);
            ticks++;
        }
        return ticks;
    }

    @Test
    void acceleratesToTopSpeedInAboutThreeSeconds() {
        double v = 0;
        int ticks = 0;
        while (v < SubmarineDrive.MAX_SPEED - 1e-9 && ticks < 500) {
            v = SubmarineDrive.speed(v, 1, true, true);
            ticks++;
        }
        assertEquals(SubmarineDrive.MAX_SPEED, v, 1e-9);
        assertEquals(RoverDrive.MAX_SPEED * 1.6, SubmarineDrive.MAX_SPEED, 1e-12, "探索车的 1.6 倍");
        assertEquals(60, ticks, 2, "三秒左右到最快");
        assertEquals(SubmarineDrive.MAX_SPEED, SubmarineDrive.speed(v, 1, true, true), 1e-9, "不会超过最快");
    }

    @Test
    void reverseIsSlowAndCapped() {
        double v = 0;
        for (int i = 0; i < 500; i++) {
            v = SubmarineDrive.speed(v, -1, true, true);
        }
        assertEquals(-SubmarineDrive.MAX_REVERSE, v, 1e-9);
        // 倒车加速是前进的六成
        assertEquals(-SubmarineDrive.ACCELERATION * 0.6, SubmarineDrive.speed(0, -1, true, true), 1e-12);
    }

    @Test
    void partialThrottleHasALowerTopSpeed() {
        double v = 0;
        for (int i = 0; i < 500; i++) {
            v = SubmarineDrive.speed(v, 0.5, true, true);
        }
        assertEquals(SubmarineDrive.MAX_SPEED * 0.5, v, SubmarineDrive.DRAG + 1e-9);
        // 从全速松一半油门：靠水的阻力慢慢降下来，不会一下掉到一半
        double next = SubmarineDrive.speed(SubmarineDrive.MAX_SPEED, 0.5, true, true);
        assertEquals(SubmarineDrive.MAX_SPEED - SubmarineDrive.DRAG, next, 1e-12);
    }

    @Test
    void counterThrustBrakesFasterThanCoasting() {
        int coast = ticksToStop(SubmarineDrive.MAX_SPEED, 0);
        int brake = ticksToStop(SubmarineDrive.MAX_SPEED, -1);
        assertEquals(Math.ceil(SubmarineDrive.MAX_SPEED / SubmarineDrive.DRAG), coast, 1);
        assertEquals(Math.ceil(SubmarineDrive.MAX_SPEED / SubmarineDrive.BRAKE), brake, 1);
        assertTrue(brake * 3 < coast, "反推比松开快得多：" + brake + " / " + coast);
        // 倒车时往前推也是先刹住
        assertEquals(-SubmarineDrive.MAX_REVERSE + SubmarineDrive.BRAKE, SubmarineDrive.speed(-SubmarineDrive.MAX_REVERSE, 1, true, true), 1e-12);
        // 刹到零就停，不会越过零反着走
        assertEquals(0, SubmarineDrive.speed(SubmarineDrive.BRAKE / 2, -1, true, true));
    }

    @Test
    void withoutPowerOrWaterItOnlySlowsDown() {
        assertEquals(SubmarineDrive.MAX_SPEED - SubmarineDrive.DRAG, SubmarineDrive.speed(SubmarineDrive.MAX_SPEED, 1, false, true), 1e-12,
                "没燃料：推进器不推，水的阻力照样");
        assertEquals(0, SubmarineDrive.speed(0, 1, false, true));
        // 离了水：推进器没用，速度按比例衰减，很快停下
        assertEquals(0.5 * SubmarineDrive.BEACHED, SubmarineDrive.speed(0.5, 1, true, false), 1e-12);
        assertEquals(-0.2 * SubmarineDrive.BEACHED, SubmarineDrive.speed(-0.2, -1, true, false), 1e-12);
        assertEquals(0, SubmarineDrive.speed(0, 1, true, false));
        double v = SubmarineDrive.MAX_SPEED;
        int ticks = 0;
        while (v != 0 && ticks < 100) {
            v = SubmarineDrive.speed(v, 1, true, false);
            ticks++;
        }
        assertEquals(0, v);
        assertTrue(ticks < 40, "搁浅后两秒内停下：" + ticks);
    }

    @Test
    void turnsTheShortWayAndNoFurtherThanTheStep() {
        assertEquals(10, SubmarineDrive.turnToward(0, 10, 15), 1e-6);
        assertEquals(4, SubmarineDrive.turnToward(0, 10, 4), 1e-6);
        assertEquals(-4, SubmarineDrive.turnToward(0, -10, 4), 1e-6);
        // 从 170° 转到 -170°：走近的那边（往上 20°），不是往回绕 340°
        assertEquals(174, SubmarineDrive.turnToward(170, -170, 4), 1e-6);
        assertEquals(190, SubmarineDrive.turnToward(170, -170, 30), 1e-6);
        assertEquals(-174, SubmarineDrive.turnToward(-170, 170, 4), 1e-6);
        // 目标差了好几圈也一样
        assertEquals(725, SubmarineDrive.turnToward(720, 5 + 360 * 3, 10), 1e-4);
        // 角度差折到 [-180, 180)
        assertEquals(-180, SubmarineDrive.wrap(-180), 1e-6);
        assertEquals(-180, SubmarineDrive.wrap(180), 1e-6);
        assertEquals(-180, SubmarineDrive.wrap(540), 1e-6);
        assertEquals(10, SubmarineDrive.wrap(-350), 1e-6);
    }

    @Test
    void turnRateGrowsWithSpeed() {
        assertEquals(SubmarineDrive.MAX_TURN * 0.5F, SubmarineDrive.turnRate(0), 1e-6, "停着时只有一半");
        assertEquals(SubmarineDrive.MAX_TURN, SubmarineDrive.turnRate(SubmarineDrive.MAX_SPEED), 1e-6);
        assertEquals(SubmarineDrive.MAX_TURN, SubmarineDrive.turnRate(-SubmarineDrive.MAX_SPEED), 1e-6);
        assertTrue(SubmarineDrive.turnRate(0.1) > SubmarineDrive.turnRate(0));
    }

    @Test
    void pitchIsClamped() {
        assertEquals(SubmarineDrive.MAX_PITCH, SubmarineDrive.clampPitch(90), 1e-6);
        assertEquals(-SubmarineDrive.MAX_PITCH, SubmarineDrive.clampPitch(-90), 1e-6);
        assertEquals(30, SubmarineDrive.clampPitch(30), 1e-6);
    }

    @Test
    void velocityFollowsMinecraftAngles() {
        // 偏航 0 朝 +z（南）
        double[] v = SubmarineDrive.velocity(0, 0, 1, 0);
        assertEquals(0, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
        assertEquals(1, v[2], 1e-9);
        // 偏航 90 朝 -x（西）
        v = SubmarineDrive.velocity(90, 0, 1, 0);
        assertEquals(-1, v[0], 1e-9);
        assertEquals(0, v[2], 1e-9);
        // 俯仰正数朝下（低头下潜）
        v = SubmarineDrive.velocity(0, 30, 1, 0);
        assertEquals(-0.5, v[1], 1e-9);
        assertEquals(Math.cos(Math.toRadians(30)), v[2], 1e-9);
        assertEquals(1, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-9, "沿艇头走的速度大小不变");
        // 侧推向左为正：面朝南时左手是东（+x），和原版的 xxa 一样
        v = SubmarineDrive.velocity(0, 0, 0, 1);
        assertEquals(SubmarineDrive.STRAFE, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
        assertEquals(0, v[2], 1e-9);
        // 侧推不受俯仰影响，也限在 ±1 以内
        v = SubmarineDrive.velocity(0, 45, 0, -3);
        assertEquals(-SubmarineDrive.STRAFE, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
        // 倒车沿艇头反方向
        v = SubmarineDrive.velocity(0, 0, -0.3, 0);
        assertEquals(-0.3, v[2], 1e-9);
    }
}
