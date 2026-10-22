package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupportTest {
    @Test
    void capsuleJamsHarderAndFurther() {
        Support.Jam power = Support.jam(false, 1);
        Support.Jam capsule = Support.jam(true, 1);
        assertEquals(Support.POWER_ONLY, power);
        assertEquals(Support.CAPSULE, capsule);
        assertTrue(capsule.radius() > power.radius());
        assertTrue(capsule.factor() < power.factor());
        // 干扰比间隔长，连着发不会断
        assertTrue(Support.JAM_TICKS > Support.JAM_INTERVAL);
    }

    @Test
    void lowPowerWeakensTheJam() {
        Support.Jam half = Support.jam(true, 0.5);
        assertEquals(Support.CAPSULE.radius(), half.radius());
        assertEquals(1 - (1 - Support.CAPSULE.factor()) / 2, half.factor(), 1e-9);
        assertNull(Support.jam(true, 0));
        assertNull(Support.jam(false, -1));
        // 满足率超过 1 也按 1 算
        assertEquals(Support.CAPSULE.factor(), Support.CAPSULE.scaled(3).factor(), 1e-9);
    }

    @Test
    void chargesFallOffToHalfAtTheEdge() {
        for (Support.Charge charge : Support.Charge.values()) {
            float center = charge.damage(0);
            assertTrue(center > 0, charge.name());
            assertEquals(center / 2, charge.damage(charge.radius()), 1e-4, charge.name());
            assertEquals(0, charge.damage(charge.radius() + 0.01), charge.name());
            assertTrue(charge.damage(1) < center, charge.name());
        }
        // 一档比一档狠、炸得远
        assertTrue(Support.Charge.EXPLOSIVE.damage(0) > Support.Charge.COMBUSTIBLE.damage(0));
        assertTrue(Support.Charge.CRYSTAL.damage(0) > Support.Charge.EXPLOSIVE.damage(0));
        assertTrue(Support.Charge.CRYSTAL.radius() > Support.Charge.EXPLOSIVE.radius());
        // 只有燃烧单元让人着火
        assertTrue(Support.Charge.COMBUSTIBLE.fireTicks() > 0);
        assertEquals(0, Support.Charge.EXPLOSIVE.fireTicks());
    }

    @Test
    void signalTowerReachesFurtherThanATeslaTower() {
        // 电力感应塔连 12 格、覆盖 6 格
        assertTrue(Support.SIGNAL_CONNECT > 12 && Support.SIGNAL_COVER > 6);
        assertEquals(48, Support.TAUNT_RANGE);
    }
}
