package io.github.sweetcornna.universe.core.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.ship.PirateFlight;
import io.github.sweetcornna.universe.core.tech.Grade;

class EncountersTest {
    @Test
    void intervalStaysBetweenSixAndTenMinutes() {
        Rng rng = Rng.of(1);
        for (int i = 0; i < 1000; i++) {
            int t = Encounters.interval(rng);
            assertTrue(t >= Encounters.MIN_INTERVAL && t <= Encounters.MAX_INTERVAL, "间隔 " + t);
        }
    }

    @Test
    void rollIsFortyFortyTwenty() {
        Map<Encounters.Kind, Integer> counts = new EnumMap<>(Encounters.Kind.class);
        Rng rng = Rng.of(2);
        int n = 20000;
        for (int i = 0; i < n; i++) {
            counts.merge(Encounters.roll(rng), 1, Integer::sum);
        }
        assertEquals(0.4, counts.get(Encounters.Kind.DERELICT) / (double) n, 0.02);
        assertEquals(0.4, counts.get(Encounters.Kind.DISTRESS) / (double) n, 0.02);
        assertEquals(0.2, counts.get(Encounters.Kind.NOTHING) / (double) n, 0.02);
    }

    @Test
    void encounterAppearsAheadAtTheRightDistance() {
        Rng rng = Rng.of(3);
        for (int i = 0; i < 500; i++) {
            float yaw = (float) rng.range(-180.0, 180.0);
            double[] p = Encounters.ahead(100, 20, -50, yaw, rng);
            double dx = p[0] - 100;
            double dz = p[2] + 50;
            double distance = Math.sqrt(dx * dx + dz * dz);
            assertTrue(distance >= Encounters.MIN_AHEAD - 1e-6 && distance <= Encounters.MAX_AHEAD + 1e-6, "距离 " + distance);
            // 船头朝向（0 朝 +z）左右 25° 以内
            double bearing = Math.toDegrees(Math.atan2(-dx, dz));
            double off = Math.abs(((bearing - yaw) % 360 + 540) % 360 - 180);
            assertTrue(off <= 25.001, "偏了 " + off + "°");
            assertTrue(p[1] >= -80 && p[1] <= 80, "高度 " + p[1]);
        }
    }

    @Test
    void distressCallsAreCloseEnoughForThePiratesToMove() {
        Rng rng = Rng.of(5);
        for (int i = 0; i < 200; i++) {
            double[] p = Encounters.ahead(0, 0, 0, 90, Encounters.MIN_DISTRESS_AHEAD, Encounters.MAX_DISTRESS_AHEAD, rng);
            double distance = Math.hypot(p[0], p[2]);
            assertTrue(distance >= Encounters.MIN_DISTRESS_AHEAD - 1e-6 && distance <= Encounters.MAX_DISTRESS_AHEAD + 1e-6);
        }
        // 海盗在货船边上露面：要比挑衅的距离远，玩家才看得到它们在打货船
        assertTrue(Encounters.RAIDER_RANGE > Encounters.PROVOKE_RANGE);
        assertTrue(Encounters.MIN_DISTRESS_AHEAD > Encounters.RAIDER_RANGE);
    }

    @Test
    void encountersKeepAwayFromPlanetsAndTheStar() {
        double[][] bodies = {{5000, 0, 0, 400}};
        assertTrue(Encounters.clear(new double[] {5000, 0, 1000}, 1000, bodies));
        assertFalse(Encounters.clear(new double[] {5000, 0, 600}, 1000, bodies), "离星球表面不到 300 格");
        assertFalse(Encounters.clear(new double[] {1200, 0, 0}, 1000, bodies), "离恒星太近");
    }

    @Test
    void sentinelsOnlyChaseTheMostWanted() {
        assertEquals(0, Encounters.sentinels(0));
        assertEquals(0, Encounters.sentinels(2));
        assertEquals(5, Encounters.sentinels(3));
    }

    @Test
    void rescuedFreightersAreMostlyLowGrade() {
        Map<Grade, Integer> counts = new EnumMap<>(Grade.class);
        Rng rng = Rng.of(4);
        int n = 20000;
        for (int i = 0; i < n; i++) {
            counts.merge(Encounters.rescueGrade(rng), 1, Integer::sum);
        }
        assertEquals(0.5, counts.get(Grade.C) / (double) n, 0.02);
        assertEquals(0.35, counts.get(Grade.B) / (double) n, 0.02);
        assertEquals(0.15, counts.get(Grade.A) / (double) n, 0.02);
        assertFalse(counts.containsKey(Grade.S), "救下来的货船不会是 S 级");
    }

    @Test
    void hazardsOnlyShockUpClose() {
        assertTrue(Encounters.shocks(0.5, 1, 0.5, 0.5, 2.5, 0.5));
        assertFalse(Encounters.shocks(0.5, 1, 0.5, 0.5, 4, 0.5));
        assertFalse(Encounters.shocks(3, 1, 0.5, 0.5, 2.5, 0.5));
    }

    @Test
    void interceptorsFlyFasterThanPirates() {
        double pirate = topSpeed(new PirateFlight(9, 0, 1));
        double interceptor = topSpeed(new PirateFlight(9, 0, 1, Encounters.INTERCEPTOR_SPEED));
        assertEquals(PirateFlight.SPEED, pirate, 1e-6);
        assertEquals(PirateFlight.SPEED * Encounters.INTERCEPTOR_SPEED, interceptor, 1e-6);
    }

    /** 从静止开始追一个远处的目标，几百刻里最快飞到多快。 */
    private static double topSpeed(PirateFlight flight) {
        double[] pos = {0, 0, 0};
        double[] vel = {0, 0, 0.6};
        double best = 0;
        for (int t = 0; t < 400; t++) {
            PirateFlight.Step step = flight.step(pos, vel, new double[] {0, 0, 5000}, new double[] {0, 0, 0}, 0, 1, 1);
            vel = step.velocity();
            pos = new double[] {pos[0] + vel[0], pos[1] + vel[1], pos[2] + vel[2]};
            best = Math.max(best, Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]));
        }
        return best;
    }
}
