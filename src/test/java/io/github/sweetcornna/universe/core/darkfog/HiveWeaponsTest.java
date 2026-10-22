package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HiveWeaponsTest {
    @Test
    void onlyTowersCarryWeapons() {
        assertEquals(HiveWeapons.Weapon.PLASMA, HiveWeapons.of(HiveBuildingKind.PLASMA_TOWER).orElseThrow());
        assertEquals(HiveWeapons.Weapon.LASER, HiveWeapons.of(HiveBuildingKind.PHASE_LASER).orElseThrow());
        for (HiveBuildingKind kind : HiveBuildingKind.values()) {
            assertEquals(kind.isTower(), HiveWeapons.of(kind).isPresent(), kind.id());
        }
    }

    @Test
    void plasmaOutrangesTheLaserButFiresSlower() {
        HiveWeapons.Weapon plasma = HiveWeapons.Weapon.PLASMA;
        HiveWeapons.Weapon laser = HiveWeapons.Weapon.LASER;
        assertTrue(plasma.range() > laser.range());
        assertTrue(plasma.interval(0) > laser.interval(0) * 4);
        assertTrue(plasma.damage(0) > laser.damage(0) * 4);
        // 打出去的弹体要飞得到射程
        for (HiveWeapons.Weapon weapon : HiveWeapons.Weapon.values()) {
            assertTrue(weapon.lifetime() * weapon.speed() >= weapon.range());
        }
    }

    @Test
    void levelsHitHarderAndFireFaster() {
        HiveWeapons.Weapon plasma = HiveWeapons.Weapon.PLASMA;
        assertEquals(10 * DarkFogLevels.scale(30), plasma.damage(30), 1e-4);
        assertTrue(plasma.interval(30) < plasma.interval(0));
        assertEquals(plasma.interval(30), plasma.interval(99), "levels past 30 change nothing");
        assertTrue(HiveWeapons.Weapon.LASER.interval(30) >= 1);
        assertTrue(plasma.damagePerSecond(30) > plasma.damagePerSecond(0) * 1.4);
    }

    @Test
    void stationaryTargetIsReachedAtDistanceOverSpeed() {
        double t = HiveWeapons.interceptTime(30, 40, 0, 0, 0, 0, 2.5);
        assertEquals(20, t, 1e-9);
        double[] aim = HiveWeapons.aim(30, 40, 0, 0, 0, 0, 2.5, 100, 1);
        assertEquals(30, aim[0], 1e-9);
        assertEquals(40, aim[1], 1e-9);
    }

    @Test
    void crossingTargetIsLed() {
        // 目标在 100 格外横着飞，一刻 1 格；弹体一刻 2 格
        double t = HiveWeapons.interceptTime(100, 0, 0, 0, 0, 1, 2);
        assertTrue(t > 50);
        double[] aim = HiveWeapons.aim(100, 0, 0, 0, 0, 1, 2, 200, 1);
        // 弹体朝着碰头的地方飞 t 刻正好到
        assertEquals(2 * t, Math.sqrt(aim[0] * aim[0] + aim[1] * aim[1] + aim[2] * aim[2]), 1e-6);
        assertEquals(t, aim[2], 1e-9);
        double[] half = HiveWeapons.aim(100, 0, 0, 0, 0, 1, 2, 200, 0.5);
        assertEquals(t / 2, half[2], 1e-9);
    }

    @Test
    void fleeingFasterTargetCannotBeCaught() {
        assertTrue(Double.isNaN(HiveWeapons.interceptTime(50, 0, 0, 3, 0, 0, 2)));
        assertTrue(Double.isNaN(HiveWeapons.interceptTime(50, 0, 0, 2, 0, 0, 2)), "same speed straight away never meets");
        double[] aim = HiveWeapons.aim(50, 0, 0, 3, 0, 0, 2, 200, 1);
        assertEquals(50, aim[0], 1e-9, "falls back to shooting where it is");
    }

    @Test
    void sameSpeedTargetComingCloserIsMet() {
        // 迎面来的目标和弹体一样快：在中间碰上
        double t = HiveWeapons.interceptTime(60, 0, 0, -2, 0, 0, 2);
        assertEquals(15, t, 1e-9);
    }

    @Test
    void interceptBeyondTheLimitAimsStraight() {
        double[] aim = HiveWeapons.aim(100, 0, 0, 0, 0, 1, 2, 10, 1);
        assertEquals(0, aim[2], 1e-9);
    }

    @Test
    void splashFallsOffToHalfAtTheEdge() {
        HiveWeapons.Weapon plasma = HiveWeapons.Weapon.PLASMA;
        assertEquals(plasma.damage(0), HiveWeapons.splashDamage(plasma, 0, 0), 1e-6);
        assertEquals(plasma.damage(0) / 2, HiveWeapons.splashDamage(plasma, 0, plasma.splash()), 1e-6);
        assertEquals(0, HiveWeapons.splashDamage(plasma, 0, plasma.splash() + 0.1), 1e-6);
        assertEquals(0, HiveWeapons.splashDamage(HiveWeapons.Weapon.LASER, 0, 0), 1e-6, "the laser has no splash");
    }
}
