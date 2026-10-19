package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void aimIsClampedToTheFiringArc() {
        double[] forward = ShipCombat.forward(0);
        // 正侧面：转到左右的极限，仍然偏向视线那一侧
        double[] clamped = ShipCombat.clampToArc(0, new double[] {1, 0, 0});
        assertEquals(ShipCombat.AIM_YAW, ShipCombat.angle(forward, clamped), 1e-6);
        assertTrue(clamped[0] > 0);
        // 射界以内原样不动
        double[] inside = ShipCombat.normalize(new double[] {0.2, 0.1, 1});
        assertEquals(0, ShipCombat.angle(inside, ShipCombat.clampToArc(0, inside)), 1e-6);
        // 船头正前方抬高 50°：船不会俯仰，炮可以抬这么高
        double[] up50 = {0, Math.sin(Math.toRadians(50)), Math.cos(Math.toRadians(50))};
        assertEquals(0, ShipCombat.angle(up50, ShipCombat.clampToArc(0, up50)), 1e-6);
        // 正上方：抬到俯仰的极限，方向是船头
        double[] top = ShipCombat.clampToArc(0, new double[] {0, 1, 0});
        assertEquals(ShipCombat.AIM_PITCH, Math.toDegrees(Math.asin(top[1])), 1e-6);
        assertTrue(top[2] > 0);
        // 正后方：转到一侧的极限
        assertEquals(ShipCombat.AIM_YAW, ShipCombat.angle(forward, ShipCombat.clampToArc(0, new double[] {0, 0, -1})), 1e-6);
        // 船头朝西（偏航 90）时射界跟着转
        double[] west = ShipCombat.forward(90);
        assertEquals(0, ShipCombat.angle(west, ShipCombat.clampToArc(90, west)), 1e-6);
        assertEquals(ShipCombat.AIM_YAW, ShipCombat.angle(west, ShipCombat.clampToArc(90, new double[] {0, 0, 1})), 1e-6);
    }

    @Test
    void eachWeaponCoversItsOwnArc() {
        double[] north = {0, 0, -1};
        double[] south = {0, 0, 1};
        // 朝南（偏航 0）的炮打不到正北，朝北（偏航 180）的炮打得到
        assertFalse(ShipCombat.inArc(0, north));
        assertTrue(ShipCombat.inArc(180, north));
        assertTrue(ShipCombat.inArc(0, south));
        // 射界边缘以内一点点算转得过去，以外不算
        double in = Math.toRadians(ShipCombat.AIM_YAW - 1);
        double out = Math.toRadians(ShipCombat.AIM_YAW + 2);
        assertTrue(ShipCombat.inArc(0, new double[] {-Math.sin(in), 0, Math.cos(in)}));
        assertFalse(ShipCombat.inArc(0, new double[] {-Math.sin(out), 0, Math.cos(out)}));
        // 抬太高也打不到
        assertFalse(ShipCombat.inArc(0, new double[] {0, 1, 0.2}));
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

    @Test
    void leadWithAccelerationHitsATurningTarget() {
        // 目标在 50 格外横着飞、同时往上拐；弹速 5 格/刻
        double[] target = {0, 0, 50};
        double[] velocity = {2, 0, 0};
        double[] accel = {0, 0.14, 0};
        double speed = 5;
        double plain = miss(ShipCombat.lead(target, velocity, speed), target, velocity, accel, speed);
        double smart = miss(ShipCombat.lead(target, velocity, accel, speed, 30), target, velocity, accel, speed);
        assertTrue(smart < 0.5, "带加速度的提前量偏了 " + smart);
        assertTrue(plain > 3, "匀速的提前量应该偏得多：" + plain);
        // 没有加速度时和匀速的算法一样
        double[] a = ShipCombat.lead(target, velocity, speed);
        double[] b = ShipCombat.lead(target, velocity, new double[] {0, 0, 0}, speed, 30);
        assertTrue(ShipCombat.angle(a, b) < 0.01);
    }

    /** 沿 {@code aim} 打出去的弹，和匀加速的目标最近时差几格。 */
    private static double miss(double[] aim, double[] target, double[] velocity, double[] accel, double speed) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= 3000; i++) {
            double t = i * 0.01;
            double h = 0.5 * t * t;
            double dx = target[0] + velocity[0] * t + accel[0] * h - aim[0] * speed * t;
            double dy = target[1] + velocity[1] * t + accel[1] * h - aim[1] * speed * t;
            double dz = target[2] + velocity[2] * t + accel[2] * h - aim[2] * speed * t;
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }
}
