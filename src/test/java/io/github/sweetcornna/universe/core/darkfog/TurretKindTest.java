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
        // 第二块：等离子炮塔地面 56 格、高空 96 格；近程的 16 格，只用电
        assertEquals(56, TurretKind.PLASMA.range());
        assertEquals(96, TurretKind.PLASMA.reach(true));
        assertEquals(56, TurretKind.PLASMA.reach(false));
        assertEquals(96, TurretKind.PLASMA.maxReach());
        assertEquals(1, TurretKind.PLASMA.shotsPerSecond(), 1e-9);
        assertEquals(16, TurretKind.SR_PLASMA.range());
        assertFalse(TurretKind.SR_PLASMA.usesAmmo());
        assertEquals(0.6, TurretKind.SR_PLASMA.shotsPerSecond(), 1e-9);
    }

    @Test
    void onlyMissileAndPlasmaTurretsReachHighAltitude() {
        for (TurretKind kind : TurretKind.values()) {
            boolean expected = kind == TurretKind.MISSILE || kind == TurretKind.PLASMA;
            assertEquals(expected, kind.antiAir(), kind.name());
            assertEquals(expected, kind.reach(true) > 0, kind.name());
        }
        assertTrue(TurretKind.PLASMA.reach(true) > TurretKind.MISSILE.reach(true), "等离子炮塔打高空更远");
    }

    @Test
    void laserUsesTheMostPowerAndHitsLeast() {
        for (TurretKind kind : TurretKind.values()) {
            assertTrue(kind.idlePower() < kind.activePower(), kind.name());
            if (kind != TurretKind.LASER && kind != TurretKind.SR_PLASMA) {
                assertTrue(kind.activePower() < TurretKind.LASER.activePower(), kind.name());
            }
        }
        // 近程等离子炮塔比激光还耗电，待机也要交战时的一半（戴森球计划里就这样）
        assertTrue(TurretKind.SR_PLASMA.activePower() > TurretKind.LASER.activePower());
        assertEquals(TurretKind.SR_PLASMA.activePower() / 2, TurretKind.SR_PLASMA.idlePower());
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
    void everyAmmoTurretHasTiersGettingStronger() {
        Set<String> ids = new HashSet<>();
        for (TurretKind kind : TurretKind.values()) {
            List<AmmoKind> ammo = kind.ammo();
            // 等离子炮塔两种胶囊，别的用弹药的三档
            assertEquals(!kind.usesAmmo() ? 0 : kind == TurretKind.PLASMA ? 2 : 3, ammo.size(), kind.name());
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
    void antimatterCapsulesHitHarderAndWider() {
        AmmoKind plasma = AmmoKind.PLASMA_CAPSULE;
        AmmoKind antimatter = AmmoKind.ANTIMATTER_CAPSULE;
        assertTrue(antimatter.damage() > plasma.damage() * 2);
        assertTrue(antimatter.radius() > plasma.radius());
        assertEquals(10, plasma.rounds());
        assertEquals(0, plasma.push(), 1e-9);
        // 等离子炮塔装等离子胶囊，每秒的伤害比超音速导弹略高
        assertTrue(TurretKind.PLASMA.damagePerSecond(plasma) > TurretKind.MISSILE.damagePerSecond(AmmoKind.SUPERSONIC_MISSILE));
        // 近程等离子炮塔一发的伤害比哪种子弹都高
        assertTrue(TurretKind.SR_PLASMA_DAMAGE > AmmoKind.SUPERALLOY.damage() * 4);
        assertEquals(TurretKind.SR_PLASMA_DAMAGE, AmmoKind.splash(TurretKind.SR_PLASMA_DAMAGE, TurretKind.SR_PLASMA_RADIUS, 0), 1e-6);
        assertEquals(TurretKind.SR_PLASMA_DAMAGE / 2, AmmoKind.splash(TurretKind.SR_PLASMA_DAMAGE, TurretKind.SR_PLASMA_RADIUS,
                TurretKind.SR_PLASMA_RADIUS), 1e-6);
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
