package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class LifeSupportTest {
    private static final Environment VACUUM = new Environment(false, Map.of(), false, 0, false, false, false);
    private static final Environment COLD_VACUUM = new Environment(false, Map.of(Hazard.COLD, 1.0), false, 0, false, false, false);

    private static int ticksUntil(LifeSupport support, Environment env, Suit suit, java.util.function.Predicate<LifeSupport> done) {
        for (int tick = 1; tick < 100_000; tick++) {
            support.tick(env, suit, false, true);
            if (done.test(support)) {
                return tick;
            }
        }
        throw new AssertionError("never");
    }

    @Test
    void sealedSuitLastsTenMinutesBareFifteenSeconds() {
        int sealed = ticksUntil(new LifeSupport(), VACUUM, Suit.FULL, s -> s.oxygen() <= 0);
        assertEquals(10 * 60 * 20, sealed, 60);
        int helmetOnly = ticksUntil(new LifeSupport(), VACUUM, new Suit(true, false, false, false), s -> s.oxygen() <= 0);
        assertEquals(15 * 20, helmetOnly, 2);
    }

    @Test
    void suffocationHurtsEverySecondAfterOxygenRunsOut() {
        LifeSupport support = new LifeSupport(0, Map.of(), LifeSupport.JET_MAX);
        float total = 0;
        for (int i = 0; i < 100; i++) {
            total += support.tick(VACUUM, Suit.FULL, false, true).suffocation();
        }
        // 100 刻：第 0、20、40、60、80 刻各一次
        assertEquals(5 * LifeSupport.SUFFOCATION_DAMAGE, total, 1e-6);
    }

    @Test
    void breathingAirRefillsQuickly() {
        LifeSupport support = new LifeSupport(0, Map.of(), LifeSupport.JET_MAX);
        int ticks = ticksUntil(support, Environment.SAFE, Suit.NONE, s -> s.oxygen() >= LifeSupport.OXYGEN_MAX);
        assertEquals(100, ticks, 2);
    }

    @Test
    void hazardDrainDependsOnSuitShelterAndStorm() {
        int full = ticksUntil(new LifeSupport(), COLD_VACUUM, Suit.FULL, s -> s.protection(Hazard.COLD) <= 0);
        assertEquals(5 * 60 * 20, full, 30);
        int bare = ticksUntil(new LifeSupport(), COLD_VACUUM, Suit.NONE, s -> s.protection(Hazard.COLD) <= 0);
        assertEquals(30 * 20, bare, 2);

        Environment storm = new Environment(false, Map.of(Hazard.COLD, 1.0), false, 2, false, false, false);
        int stormy = ticksUntil(new LifeSupport(), storm, Suit.FULL, s -> s.protection(Hazard.COLD) <= 0);
        assertEquals(full / 2, stormy, 15);

        Environment campfire = new Environment(false, Map.of(Hazard.COLD, 1.0), false, 0, true, false, false);
        int warm = ticksUntil(new LifeSupport(), campfire, Suit.FULL, s -> s.protection(Hazard.COLD) <= 0);
        assertEquals(4 * full, warm, 80);
        // 热源对高温没有帮助
        assertEquals(1.0, LifeSupport.modifier(Hazard.HEAT, campfire), 1e-9);
    }

    @Test
    void shelterRefillsProtectionEvenInAStorm() {
        // ADR-0026：屋里、洞里不再掉防护，20 秒回满；风暴照样
        Environment indoors = new Environment(false, Map.of(Hazard.COLD, 2.0, Hazard.RADIATION, 1.0), true, 2, false, false, false);
        LifeSupport support = new LifeSupport(LifeSupport.OXYGEN_MAX, Map.of(Hazard.COLD, 0F, Hazard.RADIATION, 10F), LifeSupport.JET_MAX);
        int ticks = ticksUntil(support, indoors, Suit.FULL, s -> s.protection(Hazard.COLD) >= LifeSupport.HAZARD_MAX);
        assertEquals(20 * 20, ticks, 2);
        assertEquals(LifeSupport.HAZARD_MAX, support.protection(Hazard.RADIATION), 1e-3);
        // 不会因为躲进屋里就掉血
        LifeSupport empty = new LifeSupport(LifeSupport.OXYGEN_MAX, Map.of(Hazard.COLD, 0F), LifeSupport.JET_MAX);
        assertFalse(empty.tick(indoors, Suit.FULL, false, true).any());
        // 氧气照样消耗
        assertTrue(empty.oxygen() < LifeSupport.OXYGEN_MAX);
    }

    @Test
    void emptyProtectionHurtsAndRecoversOutside() {
        LifeSupport support = new LifeSupport(LifeSupport.OXYGEN_MAX, Map.of(Hazard.COLD, 0F), LifeSupport.JET_MAX);
        float total = 0;
        for (int i = 0; i < 80; i++) {
            LifeSupport.Damage damage = support.tick(COLD_VACUUM, Suit.FULL, false, true);
            total += damage.hazards().getOrDefault(Hazard.COLD, 0F);
            assertFalse(damage.hazards().containsKey(Hazard.HEAT));
        }
        assertEquals(2 * LifeSupport.HAZARD_DAMAGE, total, 1e-6);
        int ticks = ticksUntil(support, Environment.SAFE, Suit.FULL, s -> s.protection(Hazard.COLD) >= LifeSupport.HAZARD_MAX);
        assertEquals(25 * 20, ticks, 2);
    }

    @Test
    void shipCabinSuppliesEverything() {
        Environment cabin = new Environment(false, Map.of(Hazard.RADIATION, 2.0), false, 2, false, false, true);
        assertFalse(cabin.needsOxygen());
        assertFalse(cabin.exposedTo(Hazard.RADIATION));
        LifeSupport support = new LifeSupport(10, Map.of(Hazard.RADIATION, 10F), 0);
        for (int i = 0; i < 200; i++) {
            assertFalse(support.tick(cabin, Suit.NONE, false, false).any());
        }
        assertEquals(LifeSupport.OXYGEN_MAX, support.oxygen());
        assertEquals(LifeSupport.HAZARD_MAX, support.protection(Hazard.RADIATION));
        assertEquals(LifeSupport.JET_MAX, support.jet());
    }

    @Test
    void jetpackBurnsInAirAndRechargesOnGround() {
        LifeSupport support = new LifeSupport();
        int burn = 0;
        while (support.canJet()) {
            support.tick(Environment.SAFE, Suit.FULL, true, false);
            burn++;
        }
        assertEquals(50, burn);
        // 没穿胸甲不能飞，也不耗
        LifeSupport bare = new LifeSupport();
        bare.tick(Environment.SAFE, Suit.NONE, true, false);
        assertEquals(LifeSupport.JET_MAX, bare.jet());
        // 空中不推进也不充电
        support.tick(Environment.SAFE, Suit.FULL, false, false);
        assertEquals(0, support.jet());
        int charge = 0;
        while (support.jet() < LifeSupport.JET_MAX) {
            support.tick(Environment.SAFE, Suit.FULL, false, true);
            charge++;
        }
        assertEquals(40, charge);
    }

    @Test
    void consumablesTopUp() {
        LifeSupport support = new LifeSupport(30, Map.of(Hazard.HEAT, 30F, Hazard.TOXIC, 90F), LifeSupport.JET_MAX);
        support.addOxygen(50);
        assertEquals(80, support.oxygen());
        support.rechargeHazards(50);
        assertEquals(80, support.protection(Hazard.HEAT));
        assertEquals(LifeSupport.HAZARD_MAX, support.protection(Hazard.TOXIC));
        Environment hot = new Environment(true, Map.of(Hazard.HEAT, 1.0), false, 0, false, false, false);
        assertTrue(new LifeSupport(100, Map.of(Hazard.HEAT, 10F), 0).low(Hazard.HEAT, hot, 0.2F));
        assertFalse(new LifeSupport(100, Map.of(Hazard.HEAT, 10F), 0).low(Hazard.COLD, hot, 0.2F));
    }

    @Test
    void hazardSeverityFollowsTemperature() {
        Galaxy galaxy = GalaxyGenerator.generate(20261017L, 32);
        int hot = 0;
        int cold = 0;
        for (StarSystem system : galaxy.systems()) {
            for (Planet planet : system.bodies()) {
                Map<Hazard, Double> hazards = Environment.hazardsOf(planet);
                assertEquals(java.util.Set.copyOf(planet.hazards()), hazards.keySet());
                for (Map.Entry<Hazard, Double> entry : hazards.entrySet()) {
                    assertTrue(entry.getValue() >= 0.6 && entry.getValue() <= 2.0);
                }
                if (hazards.containsKey(Hazard.HEAT) && planet.temperatureCelsius() > 200) {
                    assertTrue(hazards.get(Hazard.HEAT) > 1.5);
                    hot++;
                }
                if (hazards.containsKey(Hazard.COLD) && planet.temperatureCelsius() < -150) {
                    assertTrue(hazards.get(Hazard.COLD) > 1.5);
                    cold++;
                }
            }
        }
        assertTrue(hot > 0 && cold > 0);
    }

    @Test
    void gravityKeepsOneBlockJumpsAndScalesFalls() {
        assertEquals(1.0, GravityModel.gravity(1.0));
        assertEquals(1.0, GravityModel.jump(0.24));
        double moon = GravityModel.jumpHeight(0.24);
        assertTrue(moon > 3.5 && moon < 4.5, "月球上跳 " + moon + " 格");
        for (double g = 1.0; g <= 2.5; g += 0.1) {
            assertTrue(GravityModel.jumpHeight(g) > 1.0, "重力 " + g + " 时跳不上一格");
        }
        assertEquals(12.5, GravityModel.safeFall(0.24), 1e-9);
        assertEquals(2.0, GravityModel.safeFall(1.5), 1e-9);
        // 摔落伤害约等于 g·h - 3：月球上从 20 格摔下和母星上从 4.8 格摔下一样
        double moonDamage = (20 - GravityModel.safeFall(0.24)) * GravityModel.fallDamage(0.24);
        assertEquals(4.8 - 3, moonDamage, 1e-9);
    }
}
