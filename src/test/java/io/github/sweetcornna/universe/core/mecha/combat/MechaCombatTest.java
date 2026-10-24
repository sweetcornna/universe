package io.github.sweetcornna.universe.core.mecha.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;

class MechaCombatTest {
    @Test
    void weaponsUseTurretAmmoAtSixtyPercent() {
        assertTrue(MechaWeapon.KINETIC.accepts(AmmoKind.MAGNUM));
        assertTrue(MechaWeapon.KINETIC.accepts(AmmoKind.SUPERALLOY));
        assertFalse(MechaWeapon.KINETIC.accepts(AmmoKind.SHELL));
        assertTrue(MechaWeapon.MISSILE.accepts(AmmoKind.GRAVITY_MISSILE));
        assertTrue(MechaWeapon.PLASMA.accepts(AmmoKind.ANTIMATTER_CAPSULE));
        assertFalse(MechaWeapon.PLASMA.accepts(AmmoKind.MISSILE));
        assertEquals(1.5, MechaWeapon.KINETIC.damage(AmmoKind.MAGNUM), 1e-6);
        assertEquals(12, MechaWeapon.MISSILE.damage(AmmoKind.MISSILE), 1e-6);
        assertEquals(36, MechaWeapon.PLASMA.damage(AmmoKind.ANTIMATTER_CAPSULE), 1e-6);
        // 每秒 4 发、每 2 秒 1 发、每秒 1 发
        assertEquals(6, MechaWeapon.KINETIC.dps(AmmoKind.MAGNUM), 1e-6);
        assertEquals(6, MechaWeapon.MISSILE.dps(AmmoKind.MISSILE), 1e-6);
        assertEquals(24, MechaWeapon.KINETIC.range());
        assertEquals(40, MechaWeapon.MISSILE.range());
        assertEquals(48, MechaWeapon.PLASMA.range());
    }

    @Test
    void plasmaOnlyInSpaceOrAgainstHighTargets() {
        assertFalse(MechaWeapon.PLASMA.usable(false, false));
        assertTrue(MechaWeapon.PLASMA.usable(true, false));
        assertTrue(MechaWeapon.PLASMA.usable(false, true));
        assertTrue(MechaWeapon.KINETIC.usable(false, false));
        assertTrue(MechaWeapon.MISSILE.usable(true, true));
    }

    @Test
    void modesCycle() {
        assertEquals(WeaponMode.AIM, WeaponMode.AUTO.next());
        assertEquals(WeaponMode.OFF, WeaponMode.AIM.next());
        assertEquals(WeaponMode.AUTO, WeaponMode.OFF.next());
        assertEquals(WeaponMode.AUTO, WeaponMode.byOrdinal(9));
        assertEquals(WeaponMode.OFF, WeaponMode.byOrdinal(2));
    }

    @Test
    void burstSpendsHalfTheShieldWhenFullyCharged() {
        assertEquals(0.5, ShieldBurst.charge(20), 1e-9);
        assertEquals(1, ShieldBurst.charge(100), 1e-9);
        assertEquals(50, ShieldBurst.spent(100, 1), 1e-6);
        assertEquals(25, ShieldBurst.spent(100, 0.5), 1e-6);
        assertEquals(0, ShieldBurst.spent(-3, 1), 1e-6);
        // 伤害是用掉的护盾 × 2，太空里再加五成，半径 × 3
        assertEquals(100, ShieldBurst.damage(50, false), 1e-6);
        assertEquals(150, ShieldBurst.damage(50, true), 1e-6);
        assertEquals(6, ShieldBurst.radius(false), 1e-9);
        assertEquals(18, ShieldBurst.radius(true), 1e-9);
        assertTrue(ShieldBurst.knockback(0, 6) > ShieldBurst.knockback(6, 6));
        assertTrue(ShieldBurst.knockback(9, 6) > 0);
        // 一按就松开、护盾太少都不放
        assertFalse(ShieldBurst.fires(2, 40));
        assertFalse(ShieldBurst.fires(40, 0.5F));
        assertTrue(ShieldBurst.fires(40, 10));
    }
}
