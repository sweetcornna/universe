package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShieldTest {
    @Test
    void moduleShieldRechargesItselfAfterFiveSeconds() {
        Shield shield = new Shield();
        shield.tick(30);
        shield.refill();
        assertEquals(30, shield.value(), 1e-6);
        assertEquals(0, shield.absorb(12), 1e-6);
        assertEquals(18, shield.value(), 1e-6);
        // 挡不住的部分落到人身上
        assertEquals(8, shield.absorb(26), 1e-6);
        assertEquals(0, shield.value(), 1e-6);
        for (int i = 0; i < Shield.RECHARGE_DELAY; i++) {
            shield.tick(30);
        }
        assertEquals(0, shield.value(), 1e-6);
        for (int i = 0; i < 60; i++) {
            shield.tick(30);
        }
        assertEquals(30, shield.value(), 1e-3);
    }

    @Test
    void upgradeCapacityOnlyRechargesFromTheCore() {
        Shield shield = new Shield();
        for (int i = 0; i < 200; i++) {
            shield.tick(20, 40);
        }
        // 模块的 20 点自己充满了，升级加的 40 点没有
        assertEquals(60, shield.capacity(), 1e-6);
        assertEquals(20, shield.value(), 1e-3);
        assertTrue(shield.recharging());
        assertEquals(40, shield.missing(), 1e-3);
        float added = shield.recharge(Shield.corePoints(1_000_000));
        assertEquals(0.2, added, 1e-6);
        // 电只够 0.1 点
        assertEquals(0.1, Shield.corePoints(20), 1e-6);
        assertEquals(0, Shield.corePoints(0), 1e-9);
    }

    @Test
    void coreWaitsAfterAHitButNotAfterABurst() {
        Shield shield = new Shield();
        shield.tick(0, 100);
        shield.recharge(50);
        assertEquals(50, shield.value(), 1e-6);
        shield.absorb(10);
        assertFalse(shield.recharging());
        assertEquals(0, shield.recharge(5), 1e-6);
        for (int i = 0; i < Shield.RECHARGE_DELAY; i++) {
            shield.tick(0, 100);
        }
        assertTrue(shield.recharging());
        // 护盾爆发主动用掉，不推迟回充
        assertEquals(20, shield.spend(20), 1e-6);
        assertEquals(20, shield.value(), 1e-6);
        assertTrue(shield.recharging());
        assertEquals(20, shield.spend(500), 1e-6);
        assertEquals(0, shield.value(), 1e-6);
        // 回充不超过容量
        shield.recharge(500);
        assertEquals(100, shield.value(), 1e-6);
    }
}
