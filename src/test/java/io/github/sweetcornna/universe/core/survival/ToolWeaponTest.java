package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ToolWeaponTest {
    /** 按住扳机 {@code ticks} 刻，数打出几发（过热了就停火）。 */
    private static int shots(ToolWeapon weapon, int ticks) {
        PlasmaWeapon rate = new PlasmaWeapon(weapon.rate());
        int total = 0;
        for (int i = 0; i < ticks; i++) {
            total += rate.tick(true, 1);
        }
        return total;
    }

    /** 一直按着扳机，几刻后过热。 */
    private static int ticksToOverheat(ToolMode mode) {
        LaserHeat heat = new LaserHeat();
        int ticks = 0;
        while (heat.tick(true, mode.heatFactor()) && !heat.overheated() && ticks < 10000) {
            ticks++;
        }
        return ticks + 1;
    }

    @Test
    void scatterFiresEightPelletsWithinSixteenBlocks() {
        ToolWeapon scatter = ToolWeapon.SCATTER_BLASTER;
        assertEquals(8, scatter.projectiles());
        assertEquals(1.5F, scatter.damage(1), 1e-6);
        double reach = scatter.lifetime(ToolWeapon.BOLT_SPEED) * ToolWeapon.BOLT_SPEED;
        assertTrue(reach >= 16 && reach < 16 + ToolWeapon.BOLT_SPEED, "弹丸飞 " + reach + " 格");
    }

    @Test
    void scatterFiresEveryOnePointTwoSeconds() {
        // 第一发立刻打出，24 刻后第二发
        assertEquals(1, shots(ToolWeapon.SCATTER_BLASTER, 24));
        assertEquals(2, shots(ToolWeapon.SCATTER_BLASTER, 25));
        assertEquals(6, shots(ToolWeapon.SCATTER_BLASTER, 121));
    }

    @Test
    void pulseSpitterFiresTenPerSecondAndOverheatsFast() {
        assertEquals(10, shots(ToolWeapon.PULSE_SPITTER, 20));
        int pulse = ticksToOverheat(ToolMode.PULSE_SPITTER);
        int blaster = ticksToOverheat(ToolMode.COMBAT);
        assertEquals(64, pulse, 1);
        assertTrue(pulse < blaster, "脉冲喷射器 " + pulse + " 刻过热，爆能枪 " + blaster + " 刻");
    }

    @Test
    void javelinChargesInOnePointFiveSeconds() {
        assertEquals(30, ToolWeapon.chargeTicks(1));
        // 射速 +35%：蓄力快三成
        assertEquals(22, ToolWeapon.chargeTicks(1.35));
        assertEquals(18F, ToolWeapon.BLAZE_JAVELIN.damage(1), 1e-6);
        assertEquals(120, ToolWeapon.BLAZE_JAVELIN.range(), 1e-9);
    }

    @Test
    void grenadeFiresEveryThreeSecondsAndBurstsAfterTwo() {
        assertEquals(1, shots(ToolWeapon.PLASMA_LAUNCHER, 60));
        assertEquals(2, shots(ToolWeapon.PLASMA_LAUNCHER, 61));
        assertEquals(40, ToolWeapon.GRENADE_FUSE);
    }

    @Test
    void grenadeDamageFallsOffToTheEdge() {
        assertEquals(10F, ToolWeapon.grenadeDamage(0, 1), 1e-6);
        assertEquals(7.5F, ToolWeapon.grenadeDamage(1.5, 1), 1e-6);
        assertEquals(5F, ToolWeapon.grenadeDamage(3, 1), 1e-6);
        assertEquals(0F, ToolWeapon.grenadeDamage(3.01, 1), 1e-6);
        // 伤害加成照样乘上去
        assertEquals(17F, ToolWeapon.grenadeDamage(0, 1.7), 1e-6);
    }

    @Test
    void modeCyclesThroughInstalledWeapons() {
        Set<ToolWeapon> installed = EnumSet.of(ToolWeapon.PULSE_SPITTER, ToolWeapon.PLASMA_LAUNCHER);
        assertEquals(ToolMode.COMBAT, ToolMode.MINING.next(installed));
        assertEquals(ToolMode.PULSE_SPITTER, ToolMode.COMBAT.next(installed));
        assertEquals(ToolMode.PLASMA_LAUNCHER, ToolMode.PULSE_SPITTER.next(installed));
        assertEquals(ToolMode.MINING, ToolMode.PLASMA_LAUNCHER.next(installed));
        // 没装武器：采矿和爆能枪来回切
        assertEquals(ToolMode.MINING, ToolMode.COMBAT.next(Set.of()));
        // 装满四种：一圈六个模式
        Set<ToolWeapon> all = EnumSet.allOf(ToolWeapon.class);
        ToolMode mode = ToolMode.MINING;
        for (int i = 0; i < ToolMode.values().length; i++) {
            assertEquals(ToolMode.values()[i], mode);
            mode = mode.next(all);
        }
        assertEquals(ToolMode.MINING, mode);
    }

    @Test
    void removedWeaponFallsBackToTheBlaster() {
        Set<ToolWeapon> installed = EnumSet.of(ToolWeapon.SCATTER_BLASTER);
        assertEquals(ToolMode.COMBAT, ToolMode.BLAZE_JAVELIN.effective(installed));
        assertEquals(ToolMode.SCATTER_BLASTER, ToolMode.SCATTER_BLASTER.effective(installed));
        assertEquals(ToolMode.MINING, ToolMode.MINING.effective(Set.of()));
        // 从拆掉的武器往下切，接着找下一个装了的
        assertEquals(ToolMode.MINING, ToolMode.BLAZE_JAVELIN.next(installed));
        for (ToolWeapon weapon : ToolWeapon.values()) {
            assertEquals(weapon, ToolMode.of(weapon).weapon());
        }
    }
}
