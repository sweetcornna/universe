package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlasmaWeaponTest {
    private static int shots(PlasmaWeapon weapon, int ticks, double rateBoost) {
        int total = 0;
        for (int i = 0; i < ticks; i++) {
            total += weapon.tick(true, rateBoost);
        }
        return total;
    }

    @Test
    void firstShotIsImmediate() {
        assertEquals(1, new PlasmaWeapon().tick(true, 1));
    }

    @Test
    void baseRateIsFourPerSecond() {
        PlasmaWeapon weapon = new PlasmaWeapon();
        assertEquals(4, shots(weapon, 20, 1));
        assertEquals(20, shots(weapon, 80, 1) + 4);
    }

    @Test
    void shotsAreEvenlySpaced() {
        PlasmaWeapon weapon = new PlasmaWeapon();
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            pattern.append(weapon.tick(true, 1));
        }
        assertEquals("10000100001", pattern.toString());
    }

    @Test
    void fireRateBoostAddsShots() {
        // 射速 +35%：每秒 5.4 发
        int boosted = shots(new PlasmaWeapon(), 100, 1.35);
        assertTrue(boosted >= 27 && boosted <= 28, "100 刻打出 " + boosted + " 发");
    }

    @Test
    void tappingCannotBeatTheRate() {
        PlasmaWeapon weapon = new PlasmaWeapon();
        int total = 0;
        // 每刻按一下、松一下，打 20 刻
        for (int i = 0; i < 20; i++) {
            total += weapon.tick(i % 2 == 0, 1);
        }
        assertTrue(total <= 4, "连点打出 " + total + " 发");
    }

    @Test
    void idleChargeIsCappedAtOneShot() {
        PlasmaWeapon weapon = new PlasmaWeapon();
        weapon.tick(true, 1);
        for (int i = 0; i < 200; i++) {
            weapon.tick(false, 1);
        }
        assertEquals(1, weapon.tick(true, 1));
        assertEquals(0, weapon.tick(true, 1));
    }

    @Test
    void damageScalesWithBoost() {
        assertEquals(5F, PlasmaWeapon.damage(1), 1e-6);
        assertEquals(8.5F, PlasmaWeapon.damage(1.7), 1e-6);
    }

    @Test
    void sustainedFireOverheatsAfterFiveSeconds() {
        LaserHeat heat = new LaserHeat();
        int ticks = 0;
        while (heat.tick(true, PlasmaWeapon.HEAT_FACTOR) && ticks < 1000) {
            ticks++;
            if (heat.overheated()) {
                break;
            }
        }
        assertEquals(100, ticks, 1);
    }

    @Test
    void modeToggles() {
        assertEquals(ToolMode.COMBAT, ToolMode.MINING.next());
        assertEquals(ToolMode.MINING, ToolMode.COMBAT.next());
    }
}
