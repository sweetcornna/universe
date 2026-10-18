package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ship.ShipCombat.Hit;
import io.github.sweetcornna.universe.core.space.Warp;

class ShipCombatTest {
    @Test
    void hullAndShieldComeFromParts() {
        ShipStats small = ShipStats.of(ShipPart.HULL, 20, ShipPart.HELM, 1, ShipPart.THRUSTER, 4);
        assertEquals(40 + 25, small.hull(), 1e-9);
        assertEquals(0, small.shield(), 1e-9);
        ShipStats big = ShipStats.of(ShipPart.HULL, 500, ShipPart.ALLOY_PLATING, 10, ShipPart.SHIELD_GENERATOR, 2,
                ShipPart.PHOTON_CANNON, 3, ShipPart.MINING_BEAM, 1);
        // 方块最多算 200 个，合金船体板另加
        assertEquals(40 + 200 + 10 * ShipStats.HULL_PER_ALLOY, big.hull(), 1e-9);
        assertEquals(2 * ShipStats.SHIELD_PER_GENERATOR, big.shield(), 1e-9);
        assertEquals(3, big.cannons());
        assertEquals(1, big.beams());
        assertTrue(big.armed());
        assertTrue(!small.armed());
    }

    @Test
    void advancedPartsAreStronger() {
        ShipStats mk1 = ShipStats.of(ShipPart.HULL, 30, ShipPart.THRUSTER, 2, ShipPart.FUEL_TANK, 2, ShipPart.IMPULSE_ENGINE, 1,
                ShipPart.WARP_DRIVE, 1);
        ShipStats mk2 = ShipStats.of(ShipPart.HULL, 30, ShipPart.THRUSTER_MK2, 2, ShipPart.FUEL_TANK_MK2, 2,
                ShipPart.IMPULSE_ENGINE_MK2, 1, ShipPart.WARP_DRIVE_MK2, 1);
        assertEquals(2 * ShipStats.THRUST_PER_THRUSTER_MK2, mk2.thrust(), 1e-9);
        assertEquals(2 * ShipStats.FUEL_PER_TANK_MK2, mk2.fuelCapacity(), 1e-9);
        assertEquals(2, mk2.thrusters());
        assertEquals(2, mk2.fuelTanks());
        assertEquals(1, mk2.impulse());
        assertEquals(1, mk2.warpDrives());
        assertTrue(mk2.impulseSpeed() > mk1.impulseSpeed() + 15, mk1.impulseSpeed() + " vs " + mk2.impulseSpeed());
        assertEquals(ShipStats.IMPULSE_SPEED, mk1.impulseSpeed(), 1e-9);
        assertEquals(ShipStats.IMPULSE_SPEED_MK2, mk2.impulseSpeed(), 1e-9);
        assertEquals(10, mk1.warpRange(), 1e-9);
        assertEquals(15, mk2.warpRange(), 1e-9);
    }

    @Test
    void warpRangeCountsAdvancedDrivesFirst() {
        assertEquals(0, Warp.range(0, 0), 1e-9);
        assertEquals(15, Warp.range(0, 1), 1e-9);
        assertEquals(20, Warp.range(1, 1), 1e-9);
        // 最多算 4 台，Mk.II 优先
        assertEquals(45, Warp.range(3, 4), 1e-9);
        assertEquals(35, Warp.range(4, 2), 1e-9);
        assertEquals(Warp.range(4), Warp.range(4, 0), 1e-9);
    }

    @Test
    void heavyShipIsSlowerUnderImpulse() {
        ShipStats light = ShipStats.of(ShipPart.HULL, 50, ShipPart.IMPULSE_ENGINE, 1);
        ShipStats heavy = ShipStats.of(ShipPart.HULL, 800, ShipPart.IMPULSE_ENGINE, 1);
        assertTrue(heavy.impulseSpeed() < light.impulseSpeed());
        assertTrue(heavy.impulseSpeed() > 6);
        assertEquals(0, ShipStats.of(ShipPart.HULL, 50).impulseSpeed(), 1e-9);
    }

    @Test
    void shieldAbsorbsBeforeHull() {
        ShipCombat combat = new ShipCombat(100, 40);
        assertEquals(Hit.SHIELD, combat.damage(30));
        assertEquals(10, combat.shield(), 1e-9);
        assertEquals(100, combat.hull(), 1e-9);
        assertEquals(Hit.HULL, combat.damage(30));
        assertEquals(0, combat.shield(), 1e-9);
        assertEquals(80, combat.hull(), 1e-9);
        assertEquals(Hit.DESTROYED, combat.damage(500));
        assertEquals(0, combat.hull(), 1e-9);
    }

    @Test
    void shieldRegeneratesAfterDelay() {
        ShipCombat combat = new ShipCombat(100, 40);
        combat.damage(40);
        for (int i = 0; i < ShipCombat.SHIELD_DELAY; i++) {
            combat.tick(40, false, 0, false);
        }
        assertEquals(0, combat.shield(), 1e-9);
        for (int i = 0; i < 20; i++) {
            combat.tick(40, false, 0, false);
        }
        // 每秒 10 点
        assertEquals(10, combat.shield(), 0.6);
        for (int i = 0; i < 200; i++) {
            combat.tick(40, false, 0, false);
        }
        assertEquals(40, combat.shield(), 1e-9);
        // 挨打打断回盾
        combat.damage(20);
        combat.tick(40, false, 0, false);
        assertEquals(20, combat.shield(), 1e-9);
    }

    @Test
    void cannonsFireThreeShotsPerSecondEachAndOverheat() {
        ShipCombat combat = new ShipCombat(100, 0);
        int shots = 0;
        for (int i = 0; i < 20; i++) {
            shots += combat.tick(0, true, 2, false);
        }
        // 两门炮一秒 6 发，按下时立即打出第一发
        assertTrue(shots >= 6 && shots <= 7, "shots " + shots);
        boolean overheated = false;
        for (int i = 0; i < 200 && !overheated; i++) {
            combat.tick(0, true, 2, false);
            overheated = combat.overheated();
        }
        assertTrue(overheated);
        // 过热时不开火，冷却到底才恢复
        assertEquals(0, combat.tick(0, true, 2, false));
        for (int i = 0; i < 60; i++) {
            combat.tick(0, false, 2, false);
        }
        assertTrue(!combat.overheated());
        assertEquals(1, combat.tick(0, true, 2, false));
    }

    @Test
    void beamReportsWhileFiring() {
        ShipCombat combat = new ShipCombat(100, 0);
        assertEquals(-1, combat.tick(0, true, 2, true));
        assertEquals(0, combat.tick(0, false, 2, true));
    }

    @Test
    void aimIsClampedToTheNoseCone() {
        double[] forward = ShipCombat.forward(0);
        double[] side = {1, 0, 0};
        double[] clamped = ShipCombat.clampToCone(forward, side, ShipCombat.AIM_CONE);
        assertEquals(ShipCombat.AIM_CONE, ShipCombat.angle(forward, clamped), 1e-6);
        // 仍然偏向视线那一侧
        assertTrue(clamped[0] > 0);
        double[] inside = ShipCombat.normalize(new double[] {0.2, 0.1, 1});
        double[] kept = ShipCombat.clampToCone(forward, inside, ShipCombat.AIM_CONE);
        assertEquals(0, ShipCombat.angle(inside, kept), 1e-3);
        double[] back = {0, 0, -1};
        assertEquals(ShipCombat.AIM_CONE, ShipCombat.angle(forward, ShipCombat.clampToCone(forward, back, ShipCombat.AIM_CONE)), 1e-6);
    }

    @Test
    void leadHitsAMovingTarget() {
        double[] target = {0, 0, 60};
        double[] velocity = {1.5, 0, 0};
        double speed = ShipCombat.BOLT_SPEED;
        double[] aim = ShipCombat.lead(target, velocity, speed);
        // 沿着瞄准方向飞，和目标同时到达同一点
        double t = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 400; i++) {
            t = i * 0.1;
            double dx = aim[0] * speed * t - (target[0] + velocity[0] * t);
            double dz = aim[2] * speed * t - (target[2] + velocity[2] * t);
            best = Math.min(best, Math.hypot(dx, dz));
        }
        assertTrue(best < 0.5, "miss " + best);
        // 追不上就直接瞄准
        double[] fast = ShipCombat.lead(target, new double[] {0, 0, 10}, speed);
        assertEquals(0, ShipCombat.angle(fast, ShipCombat.normalize(target)), 1e-3);
    }
}
