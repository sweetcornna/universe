package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TurretKindTest {
    @Test
    void rangesAndRatesFollowTheAdr() {
        assertEquals(32, TurretKind.GAUSS.range());
        assertEquals(6, TurretKind.GAUSS.shotsPerSecond(), 1e-9);
        assertEquals(24, TurretKind.LASER.range());
        assertEquals(40, TurretKind.IMPLOSION.range());
        assertEquals(48, TurretKind.MISSILE.range());
        assertFalse(TurretKind.LASER.usesAmmo());
        assertTrue(TurretKind.IMPLOSION.groundOnly());
        assertFalse(TurretKind.MISSILE.groundOnly());
        assertTrue(TurretKind.GAUSS.direct() && TurretKind.LASER.direct());
    }

    @Test
    void laserUsesTheMostPowerAndHitsLeast() {
        for (TurretKind kind : TurretKind.values()) {
            assertTrue(kind.idlePower() < kind.activePower(), kind.name());
            if (kind != TurretKind.LASER) {
                assertTrue(kind.activePower() < TurretKind.LASER.activePower(), kind.name());
            }
        }
        // 激光伤害低：比最差的子弹还低
        assertTrue(TurretKind.LASER.damagePerSecond(AmmoKind.MAGNUM) < TurretKind.GAUSS.damagePerSecond(AmmoKind.MAGNUM));
        assertEquals(1, TurretKind.LASER.hitChance(24), 1e-9);
    }

    @Test
    void gaussMissesMoreFarAway() {
        assertEquals(0.95, TurretKind.GAUSS.hitChance(0), 1e-9);
        assertEquals(0.75, TurretKind.GAUSS.hitChance(32), 1e-9);
        assertEquals(0.75, TurretKind.GAUSS.hitChance(100), 1e-9);
        assertTrue(TurretKind.GAUSS.hitChance(10) > TurretKind.GAUSS.hitChance(20));
    }

    @Test
    void everyAmmoTurretHasThreeTiersGettingStronger() {
        Set<String> ids = new HashSet<>();
        for (TurretKind kind : TurretKind.values()) {
            List<AmmoKind> ammo = kind.ammo();
            assertEquals(kind.usesAmmo() ? 3 : 0, ammo.size(), kind.name());
            for (int i = 1; i < ammo.size(); i++) {
                assertTrue(ammo.get(i).damage() > ammo.get(i - 1).damage(), ammo.get(i).name());
            }
        }
        for (AmmoKind ammo : AmmoKind.values()) {
            assertTrue(ids.add(ammo.id()), ammo.id());
            assertTrue(ammo.rounds() > 0);
            assertEquals(ammo, AmmoKind.byId(ammo.id()).orElseThrow());
            // 子弹单体，炮弹和导弹范围伤害
            assertEquals(ammo.turret() == TurretKind.GAUSS, ammo.radius() == 0, ammo.name());
        }
        assertTrue(AmmoKind.GRAVITY_MISSILE.push() < 0, "引力导弹要往里吸");
    }

    @Test
    void splashFallsOffToHalfAtTheEdge() {
        AmmoKind shell = AmmoKind.SHELL;
        assertEquals(shell.damage(), shell.splashDamage(0), 1e-6);
        assertEquals(shell.damage() / 2, shell.splashDamage(shell.radius()), 1e-6);
        assertEquals(0, shell.splashDamage(shell.radius() + 0.01), 1e-6);
        assertTrue(shell.splashDamage(1) > shell.splashDamage(2));
    }

    @Test
    void magnumKillsALevelZeroRaiderInAFewSeconds() {
        // 0 级突袭者 30 血：一座高斯炮塔用机枪弹箱三秒内打得死（按远处的命中率）
        double dps = TurretKind.GAUSS.damagePerSecond(AmmoKind.MAGNUM) * TurretKind.GAUSS.hitChance(32);
        assertTrue(UnitKind.RAIDER.baseHealth() / dps < 3, "要 " + UnitKind.RAIDER.baseHealth() / dps + " 秒");
        // 一个弹箱至少打死一个
        assertTrue(AmmoKind.MAGNUM.damage() * AmmoKind.MAGNUM.rounds() * 0.75 > UnitKind.RAIDER.baseHealth());
    }
}
