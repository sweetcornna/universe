package io.github.sweetcornna.universe.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoverDriveTest {
    @Test
    void acceleratesToTopSpeedInAboutTwoSeconds() {
        double v = 0;
        int ticks = 0;
        while (v < RoverDrive.MAX_SPEED - 1e-9 && ticks < 200) {
            v = RoverDrive.speed(v, 1, true, true);
            ticks++;
        }
        assertEquals(RoverDrive.MAX_SPEED, v, 1e-9, "最快 12 格/秒");
        assertTrue(ticks >= 30 && ticks <= 50, "两秒左右踩到最快：" + ticks);
        assertEquals(RoverDrive.MAX_SPEED, RoverDrive.speed(v, 1, true, true), 1e-9, "不会超过最快");
    }

    @Test
    void coastsAndBrakesToAStop() {
        double coast = RoverDrive.MAX_SPEED;
        int coastTicks = 0;
        while (coast > 0) {
            coast = RoverDrive.speed(coast, 0, true, true);
            coastTicks++;
        }
        double brake = RoverDrive.MAX_SPEED;
        int brakeTicks = 0;
        while (brake > 0) {
            brake = RoverDrive.speed(brake, -1, true, true);
            brakeTicks++;
        }
        assertTrue(brakeTicks < coastTicks / 3, "反着踩比松油门停得快得多：" + brakeTicks + " / " + coastTicks);
        assertEquals(0, brake, 1e-12, "刹停的那一刻不会直接倒车");
        assertTrue(RoverDrive.speed(0, -1, true, true) < 0, "停住以后再按才倒车");
    }

    @Test
    void reverseIsSlower() {
        double v = 0;
        for (int i = 0; i < 200; i++) {
            v = RoverDrive.speed(v, -1, true, true);
        }
        assertEquals(-RoverDrive.MAX_REVERSE, v, 1e-9);
    }

    @Test
    void noFuelNoThrottle() {
        assertEquals(0, RoverDrive.speed(0, 1, false, true), 1e-12, "没燃料踩不动");
        assertTrue(RoverDrive.speed(0.3, 1, false, true) < 0.3, "没燃料只能滑行");
    }

    @Test
    void airborneHasLittleGrip() {
        double ground = RoverDrive.speed(0.2, 1, true, true) - 0.2;
        double air = RoverDrive.speed(0.2, 1, true, false) - 0.2;
        assertTrue(air < ground * 0.3, "离地时油门几乎不管用");
        assertTrue(Math.abs(RoverDrive.turn(0.3, 1, false)) < Math.abs(RoverDrive.turn(0.3, 1, true)) * 0.3);
    }

    @Test
    void steeringNeedsSpeedAndFlipsInReverse() {
        assertEquals(0, RoverDrive.turn(0, 1, true), 1e-9, "停着不能原地打转");
        float slow = RoverDrive.turn(0.05, 1, true);
        float cruise = RoverDrive.turn(RoverDrive.TURN_SPEED, 1, true);
        float fast = RoverDrive.turn(RoverDrive.MAX_SPEED, 1, true);
        assertTrue(slow > 0 && slow < cruise, "慢的时候转得慢");
        assertTrue(fast < cruise && fast > 0, "快的时候方向盘重一些");
        assertEquals(RoverDrive.MAX_TURN * RoverDrive.HIGH_SPEED_TURN, fast, 1e-4);
        assertTrue(RoverDrive.turn(-0.15, 1, true) < 0, "倒车时打右方向，车头往左转");
        assertTrue(RoverDrive.turn(0.3, -1, true) < 0, "打左方向向左转");
    }

    @Test
    void turningCircleIsCarLike() {
        // 全速打满方向：转一圈走过的路换算成半径，十几格，像车不像坦克
        double radius = RoverDrive.MAX_SPEED / Math.toRadians(RoverDrive.turn(RoverDrive.MAX_SPEED, 1, true));
        assertTrue(radius > 8 && radius < 20, "全速转弯半径：" + radius);
    }
}
