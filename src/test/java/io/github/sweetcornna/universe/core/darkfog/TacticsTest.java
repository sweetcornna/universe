package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.darkfog.Tactics.Order;
import io.github.sweetcornna.universe.core.darkfog.Tactics.Situation;

class TacticsTest {
    private static final double NONE = Double.POSITIVE_INFINITY;

    private static Situation defend(double home, double taunt, double player, double playerHome, double revenge) {
        return new Situation(false, home, taunt, player, playerHome, revenge, NONE, NONE);
    }

    private static Situation attack(double taunt, double player, double revenge, double facility, double target) {
        return new Situation(true, 500, taunt, player, NONE, revenge, facility, target);
    }

    @Test
    void signalTowerComesFirst() {
        assertEquals(Order.TAUNT, Tactics.decide(defend(10, 40, 5, 5, 3)));
        assertEquals(Order.TAUNT, Tactics.decide(attack(30, 2, 2, 1, 1)));
        assertEquals(Order.PLAYER, Tactics.decide(defend(10, 60, 5, 5, NONE)));
    }

    @Test
    void defendersStayHome() {
        assertEquals(Order.PATROL, Tactics.decide(defend(10, NONE, NONE, NONE, NONE)));
        // 人离家近才打，单位自己离得近不算
        assertEquals(Order.PLAYER, Tactics.decide(defend(10, NONE, 20, 30, NONE)));
        assertEquals(Order.PATROL, Tactics.decide(defend(10, NONE, 20, 40, NONE)));
        assertEquals(Order.RETURN, Tactics.decide(defend(60, NONE, 5, 40, NONE)));
        // 被打了追一段，但不追出家门太远
        assertEquals(Order.PLAYER, Tactics.decide(defend(50, NONE, 20, 70, 20)));
        assertEquals(Order.RETURN, Tactics.decide(defend(70, NONE, 20, 90, 20)));
    }

    @Test
    void attackersGoForFacilities() {
        assertEquals(Order.ADVANCE, Tactics.decide(attack(NONE, NONE, NONE, NONE, 80)));
        assertEquals(Order.ADVANCE, Tactics.decide(attack(NONE, NONE, NONE, 20, 80)));
        // 路上经过的设施顺手打
        assertEquals(Order.FACILITY, Tactics.decide(attack(NONE, NONE, NONE, 10, 80)));
        assertEquals(Order.FACILITY, Tactics.decide(attack(NONE, NONE, NONE, 20, 10)));
        assertEquals(Order.PLAYER, Tactics.decide(attack(NONE, 12, NONE, 5, 10)));
        assertEquals(Order.FACILITY, Tactics.decide(attack(NONE, 20, NONE, 5, 10)));
        // 到了、没设施了，回家
        assertEquals(Order.RETURN, Tactics.decide(attack(NONE, NONE, NONE, NONE, 10)));
        assertEquals(Order.RETURN, Tactics.decide(attack(NONE, NONE, NONE, NONE, NONE)));
    }

    @Test
    void jammingStretchesCooldown() {
        assertEquals(20, Tactics.cooldown(20, 1));
        assertEquals(40, Tactics.cooldown(20, 0.5));
        assertEquals(100, Tactics.cooldown(20, 0));
        assertEquals(20, Tactics.cooldown(20, 3));
    }

    @Test
    void ballisticLandsOnTarget() {
        double gravity = 0.05;
        double[] v = Tactics.ballistic(24, -3, 7, gravity, 30);
        double x = 0;
        double y = 0;
        double z = 0;
        double vy = v[1];
        for (int i = 0; i < 30; i++) {
            vy -= gravity;
            x += v[0];
            y += vy;
            z += v[2];
        }
        assertEquals(24, x, 1e-9);
        assertEquals(-3, y, 1e-9);
        assertEquals(7, z, 1e-9);
        assertTrue(v[1] > 0, "吊射是往上打的");
        assertEquals(20, Tactics.flightTicks(5));
        assertEquals(36, Tactics.flightTicks(40));
        assertEquals(40, Tactics.flightTicks(100));
    }

    @Test
    void jumpingDodgesTheShell() {
        assertTrue(Tactics.splash(1.5, 0, 2.5));
        assertTrue(Tactics.splash(1.5, 0.8, 2.5));
        assertFalse(Tactics.splash(1.5, 1.2, 2.5));
        assertFalse(Tactics.splash(3, 0, 2.5));
        assertEquals(1, Tactics.splashFactor(0, 2.5), 1e-9);
        assertEquals(0.5, Tactics.splashFactor(2.5, 2.5), 1e-9);
    }

    @Test
    void compassPoints() {
        assertEquals(0, Tactics.compass(0, -10));
        assertEquals(1, Tactics.compass(10, -10));
        assertEquals(2, Tactics.compass(10, 0));
        assertEquals(3, Tactics.compass(10, 10));
        assertEquals(4, Tactics.compass(0, 10));
        assertEquals(5, Tactics.compass(-10, 10));
        assertEquals(6, Tactics.compass(-10, 0));
        assertEquals(7, Tactics.compass(-10, -10));
        assertEquals(0, Tactics.compass(-1, -10));
    }

    @Test
    void ranges() {
        assertTrue(Tactics.hover(UnitKind.RAIDER) < Tactics.hover(UnitKind.RANGER));
        assertTrue(Tactics.reach(UnitKind.RAIDER) < 5);
        for (UnitKind kind : UnitKind.values()) {
            assertTrue(Tactics.attackInterval(kind) > 0);
        }
    }
}
