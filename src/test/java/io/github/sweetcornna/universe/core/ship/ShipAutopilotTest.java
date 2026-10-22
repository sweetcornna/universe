package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ship.ShipFlight.Controls;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Environment;
import io.github.sweetcornna.universe.core.tech.Grade;

class ShipAutopilotTest {
    /** 一艘卖的穿梭机，开着自动驾驶从原点飞到远处的目标。 */
    private static double[] fly(double tx, double ty, double tz, double stop, float yaw, int maxTicks) {
        ShipStats stats = ShipDesigner.design(5, ShipType.SHUTTLE, Grade.B).stats();
        ShipFlight flight = new ShipFlight(yaw);
        double x = 0;
        double y = 0;
        double z = 0;
        ShipAutopilot.Target target = new ShipAutopilot.Target(tx, ty, tz, stop);
        for (int t = 0; t < maxTicks; t++) {
            Controls controls = ShipAutopilot.steer(x, y, z, flight.yaw(), flight.speed(), target, stats);
            if (controls == null) {
                return new double[] {x, y, z, t};
            }
            flight.step(stats, controls, Environment.SPACE, true, false);
            x += flight.vx();
            y += flight.vy();
            z += flight.vz();
        }
        return null;
    }

    @Test
    void fliesAcrossASystemAndStopsShort() {
        double[] end = fly(30000, 40, -18000, 500, 0, 20 * 60 * 5);
        assertNotNull(end, "never arrived");
        double dx = 30000 - end[0];
        double dy = 40 - end[1];
        double dz = -18000 - end[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        assertTrue(distance > 500 - ShipAutopilot.ARRIVED - 50 && distance < 500 + ShipAutopilot.ARRIVED + 50, "stopped at " + distance);
        // 开着脉冲引擎：35000 格几分钟就到
        assertTrue(end[3] < 20 * 60 * 3, "took " + end[3] + " ticks");
    }

    @Test
    void turnsAroundForATargetBehind() {
        double[] end = fly(0, 0, -2000, 100, 0, 20 * 60 * 2);
        assertNotNull(end, "never arrived");
        assertTrue(Math.abs(end[2] + 1900) < 120, "z " + end[2]);
    }

    /** 空间站那么大的一块：x 100–140、y -4–18、z -20–20。 */
    private static final int[] STATION = {100, -4, -20, 140, 18, 20};

    @Test
    void routesOverAStationInTheWay() {
        ShipAutopilot.Target to = new ShipAutopilot.Target(300, 2, 0, 0);
        List<ShipAutopilot.Target> route = ShipAutopilot.around(0, 2, 0, to, STATION, 10);
        assertEquals(3, route.size());
        assertTrue(route.get(0).pass() && route.get(1).pass());
        assertEquals(0, route.get(0).x(), 1e-9);
        assertTrue(route.get(0).y() > 19 + 10, "y " + route.get(0).y());
        assertEquals(300, route.get(1).x(), 1e-9);
        assertEquals(route.get(0).y(), route.get(1).y(), 1e-9);
        assertSame(to, route.get(2));
        // 不挡路就直接去
        assertEquals(List.of(to), ShipAutopilot.around(0, 2, 100, to, STATION, 10));
        // 船在站底下就从下面绕
        List<ShipAutopilot.Target> below = ShipAutopilot.around(0, -6, 0, new ShipAutopilot.Target(300, -6, 0, 0), STATION, 10);
        assertTrue(below.get(0).y() < -4 - 10, "y " + below.get(0).y());
    }

    @Test
    void routeUsesTheStopPointOfAPlanet() {
        // 星球中心在 x 1000、停在 300 格外：站在 400–450 挡路，站在 750 以外不挡
        ShipAutopilot.Target planet = new ShipAutopilot.Target(1000, 0, 0, 300);
        List<ShipAutopilot.Target> blocked = ShipAutopilot.around(0, 0, 0, planet, new int[] {400, -4, -20, 450, 18, 20}, 10);
        assertEquals(3, blocked.size());
        assertEquals(700, blocked.get(1).x(), 1e-6);
        assertEquals(List.of(planet), ShipAutopilot.around(0, 0, 0, planet, new int[] {750, -4, -20, 800, 18, 20}, 10));
    }

    @Test
    void segmentCrossesBox() {
        double[] lo = {0, 0, 0};
        double[] hi = {10, 10, 10};
        assertTrue(ShipAutopilot.crosses(-5, 5, 5, 15, 5, 5, lo, hi));
        assertFalse(ShipAutopilot.crosses(-5, 15, 5, 15, 15, 5, lo, hi));
        assertFalse(ShipAutopilot.crosses(-5, 5, 5, -1, 5, 5, lo, hi));
        assertTrue(ShipAutopilot.crosses(5, 5, 5, 5, 5, 5, lo, hi));
    }

    @Test
    void flyingTheRouteNeverTouchesTheStation() {
        ShipStats stats = ShipDesigner.design(5, ShipType.SHUTTLE, Grade.B).stats();
        ShipFlight flight = new ShipFlight(270);
        double x = 0;
        double y = 2;
        double z = 0;
        ShipAutopilot.Target to = new ShipAutopilot.Target(300, 2, 0, 0);
        java.util.ArrayDeque<ShipAutopilot.Target> route = new java.util.ArrayDeque<>(ShipAutopilot.around(x, y, z, to, STATION, 10));
        boolean arrived = false;
        for (int t = 0; t < 20 * 60 * 3 && !arrived; t++) {
            Controls controls = ShipAutopilot.steer(x, y, z, flight.yaw(), flight.speed(), route.peekFirst(), stats);
            while (controls == null && route.size() > 1) {
                route.removeFirst();
                controls = ShipAutopilot.steer(x, y, z, flight.yaw(), flight.speed(), route.peekFirst(), stats);
            }
            if (controls == null) {
                arrived = true;
                break;
            }
            flight.step(stats, controls, Environment.SPACE, true, false);
            x += flight.vx();
            y += flight.vy();
            z += flight.vz();
            boolean inside = x > STATION[0] - 2 && x < STATION[3] + 3 && y > STATION[1] - 2 && y < STATION[4] + 3
                    && z > STATION[2] - 2 && z < STATION[5] + 3;
            assertFalse(inside, "hit the station at " + x + " " + y + " " + z);
        }
        assertTrue(arrived, "never arrived, at " + x + " " + y + " " + z);
        double dx = x - 300;
        double dy = y - 2;
        assertTrue(Math.sqrt(dx * dx + dy * dy + z * z) < ShipAutopilot.ARRIVED, "ended at " + x + " " + y + " " + z);
    }

    /** 照着去货船的路线：先到机库口前面的点停稳，再顺着中线飞进去；返回每一段结束时的位置。 */
    private static List<double[]> dock(double x, double y, double z, float yaw, List<ShipAutopilot.Target> legs) {
        ShipStats stats = ShipDesigner.design(5, ShipType.SHUTTLE, Grade.B).stats();
        ShipFlight flight = new ShipFlight(yaw);
        java.util.ArrayDeque<ShipAutopilot.Target> route = new java.util.ArrayDeque<>(legs);
        List<double[]> ends = new java.util.ArrayList<>();
        for (int t = 0; t < 20 * 60 * 2 && !route.isEmpty(); t++) {
            Controls controls = ShipAutopilot.steer(x, y, z, flight.yaw(), flight.speed(), flight.vy(), route.peekFirst(), stats);
            if (controls == null) {
                ends.add(new double[] {x, y, z, t});
                route.removeFirst();
                continue;
            }
            flight.step(stats, controls, Environment.SPACE, true, false);
            x += flight.vx();
            y += flight.vy();
            z += flight.vz();
        }
        return ends;
    }

    @Test
    void docksOnTheAxisOfAHangar() {
        // 机库口朝 -x，中线 y = 4、z = 0.5：从斜上方过来
        List<double[]> ends = dock(-80, 30, -45, 0, List.of(ShipAutopilot.Target.dock(-53, 4, 0.5), ShipAutopilot.Target.dock(-5, 4, 0.5)));
        assertEquals(2, ends.size(), "didn't finish");
        for (double[] end : ends) {
            assertTrue(Math.abs(end[1] - 4) < 1 && Math.abs(end[2] - 0.5) < ShipAutopilot.DOCKED, "ended at " + end[0] + " " + end[1] + " " + end[2]);
        }
        assertTrue(Math.abs(ends.get(1)[0] + 5) < ShipAutopilot.DOCKED, "inside at " + ends.get(1)[0]);
    }

    @Test
    void dockingStaysInsideTheMouth() {
        // 停稳以后往里飞：一路都在机库口（z ±7、y 2–8）以内
        ShipStats stats = ShipDesigner.design(5, ShipType.SHUTTLE, Grade.B).stats();
        ShipFlight flight = new ShipFlight(270);
        double x = -53;
        double y = 5.2;
        double z = 2;
        ShipAutopilot.Target inside = ShipAutopilot.Target.dock(-5, 4, 0.5);
        for (int t = 0; t < 20 * 60 && x < -11; t++) {
            Controls controls = ShipAutopilot.steer(x, y, z, flight.yaw(), flight.speed(), flight.vy(), inside, stats);
            if (controls == null) {
                break;
            }
            flight.step(stats, controls, Environment.SPACE, true, false);
            x += flight.vx();
            y += flight.vy();
            z += flight.vz();
            if (x > -24) {
                assertTrue(Math.abs(z - 0.5) < 5 && y > 2.5 && y < 6.5, "off the axis at " + x + " " + y + " " + z);
            }
        }
        assertTrue(x >= -11, "never got in, at " + x);
    }

    @Test
    void arrivedReturnsNull() {
        ShipStats stats = ShipDesigner.design(5, ShipType.SHUTTLE, Grade.B).stats();
        assertTrue(ShipAutopilot.steer(0, 0, 0, 0, 0, new ShipAutopilot.Target(0, 0, 100, 100), stats) == null);
    }
}
