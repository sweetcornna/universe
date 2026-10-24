package io.github.sweetcornna.universe.core.hazard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.survival.Environment;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.core.survival.Suit;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;

class LocalHazardsTest {
    @Test
    void geyserFollowsQuietRumbleErupt() {
        for (long seed = 0; seed < 50; seed++) {
            GeyserCycle cycle = GeyserCycle.of(seed);
            assertTrue(cycle.quiet() >= GeyserCycle.QUIET_MIN && cycle.quiet() <= GeyserCycle.QUIET_MAX);
            assertEquals(cycle, GeyserCycle.of(seed));
            int quiet = 0;
            int rumble = 0;
            int erupt = 0;
            for (long t = 0; t < cycle.length(); t++) {
                switch (cycle.phase(t)) {
                    case QUIET -> quiet++;
                    case RUMBLE -> rumble++;
                    case ERUPT -> erupt++;
                }
            }
            assertEquals(cycle.quiet(), quiet);
            assertEquals(GeyserCycle.RUMBLE, rumble);
            assertEquals(GeyserCycle.ERUPT, erupt);
        }
        GeyserCycle cycle = new GeyserCycle(1000, 0);
        assertEquals(GeyserCycle.Phase.QUIET, cycle.phase(999));
        assertTrue(cycle.starts(1000, GeyserCycle.Phase.RUMBLE));
        assertEquals(60, cycle.untilEruption(1000));
        assertTrue(cycle.starts(1060, GeyserCycle.Phase.ERUPT));
        assertEquals(0, cycle.untilEruption(1100));
        // 下一圈从头来
        assertTrue(cycle.starts(1160, GeyserCycle.Phase.QUIET));
    }

    @Test
    void columnRisesHoldsAndFalls() {
        GeyserCycle cycle = new GeyserCycle(1000, 0);
        assertEquals(0, cycle.column(500));
        assertTrue(cycle.column(1060) > 0 && cycle.column(1060) < GeyserCycle.HEIGHT);
        assertEquals(GeyserCycle.HEIGHT, cycle.column(1100), 1e-9);
        assertTrue(cycle.column(1155) < GeyserCycle.HEIGHT / 2);
        assertEquals(0, cycle.column(1160));
    }

    @Test
    void columnLiftsThingsAllTheWayUp() {
        // 一路推着走：每刻先取推力和现有速度里大的，再减重力，看能升多高
        double y = 0;
        double vy = 0;
        double top = 0;
        for (int i = 0; i < 70; i++) {
            vy = Math.max(vy, GeyserCycle.lift(y, GeyserCycle.HEIGHT));
            y += vy;
            vy = (vy - 0.08) * 0.98;
            top = Math.max(top, y);
        }
        assertTrue(top >= 12 && top <= GeyserCycle.HEIGHT + 3, "升到了 " + top);
        assertEquals(0, GeyserCycle.lift(16, GeyserCycle.HEIGHT));
        assertEquals(0, GeyserCycle.lift(3, 0));
        assertTrue(GeyserCycle.inColumn(1.0));
        assertFalse(GeyserCycle.inColumn(1.5));
    }

    @Test
    void geyserKindsFollowThePlanet() {
        assertEquals(GeyserKind.HOT, GeyserKind.of(PlanetType.SCORCHED).orElseThrow());
        assertEquals(GeyserKind.COLD, GeyserKind.of(PlanetType.FROZEN).orElseThrow());
        assertEquals(GeyserKind.TOXIC, GeyserKind.of(PlanetType.TOXIC).orElseThrow());
        assertEquals(GeyserKind.STRANGE, GeyserKind.of(PlanetType.EXOTIC).orElseThrow());
        assertTrue(GeyserKind.of(PlanetType.HABITABLE).isEmpty());
        assertEquals(Hazard.HEAT, GeyserKind.HOT.hazard(List.of(Hazard.RADIATION)));
        assertEquals(Hazard.RADIATION, GeyserKind.STRANGE.hazard(List.of(Hazard.RADIATION)));
        assertEquals(Hazard.HEAT, GeyserKind.STRANGE.hazard(List.of()));
        assertEquals("universe:frost_crystal", GeyserKind.COLD.crustDrop());
    }

    @Test
    void sitesMatchTheAdr() {
        assertTrue(HazardSites.geyser(PlanetType.FROZEN, Liquid.ICE, BiomeKind.GLACIER));
        assertTrue(HazardSites.geyser(PlanetType.SCORCHED, Liquid.LAVA, BiomeKind.TUNDRA));
        // 灼热星球没有水的地方没有
        assertFalse(HazardSites.geyser(PlanetType.SCORCHED, Liquid.LAVA, BiomeKind.ASH_WASTES));
        assertFalse(HazardSites.geyser(PlanetType.HABITABLE, Liquid.WATER, BiomeKind.GRASSLAND));
        assertFalse(HazardSites.geyser(PlanetType.FROZEN, Liquid.WATER, BiomeKind.OCEAN));
        assertTrue(HazardSites.eruption(PlanetType.SCORCHED, BiomeKind.VOLCANIC));
        assertFalse(HazardSites.eruption(PlanetType.BARREN, BiomeKind.VOLCANIC));
        assertFalse(HazardSites.eruption(PlanetType.SCORCHED, BiomeKind.DESERT));
        assertTrue(HazardSites.sporeCloud(PlanetType.TOXIC, BiomeKind.FUNGAL_FOREST));
        assertFalse(HazardSites.sporeCloud(PlanetType.TOXIC, BiomeKind.DESERT));
        assertTrue(HazardSites.dustDevil(true, 0.5, BiomeKind.MESA));
        assertFalse(HazardSites.dustDevil(false, 0.5, BiomeKind.MESA), "没有大气就没有风");
        assertFalse(HazardSites.dustDevil(true, 0.05, BiomeKind.MESA));
        assertFalse(HazardSites.allows(HazardKind.DUST_DEVIL, PlanetType.BARREN, true, 0.5, Liquid.NONE, null));
    }

    @Test
    void frequencyScalesAndZeroTurnsItOff() {
        for (HazardKind kind : HazardKind.values()) {
            assertEquals(0, HazardOdds.chance(kind, 0, 2, 1));
        }
        double normal = HazardOdds.chance(HazardKind.ERUPTION, 100, 0, 0);
        assertEquals(normal * 2, HazardOdds.chance(HazardKind.ERUPTION, 200, 0, 0), 1e-9);
        // 风暴里孢子云多
        assertTrue(HazardOdds.chance(HazardKind.SPORE_CLOUD, 100, 2, 0) > 2 * HazardOdds.chance(HazardKind.SPORE_CLOUD, 100, 0, 0));
        // 风大尘卷风多
        assertTrue(HazardOdds.chance(HazardKind.DUST_DEVIL, 100, 0, 1) > HazardOdds.chance(HazardKind.DUST_DEVIL, 100, 0, 0.2));
        // 默认频率下火山几分钟到十几分钟一次，不至于一直来
        double minutes = HazardOdds.meanTicks(HazardKind.ERUPTION, 100, 0, 0) / 1200;
        assertTrue(minutes > 4 && minutes < 15, minutes + " 分钟");
        assertEquals(Double.POSITIVE_INFINITY, HazardOdds.meanTicks(HazardKind.GEYSER, 100, 0, 0));
    }

    @Test
    void eruptionDropsOneOrTwoBombsASecond() {
        Rng rng = Rng.of(3);
        for (int age = 0; age < EruptionPlan.WARNING; age++) {
            assertEquals(0, EruptionPlan.bombs(age, rng), "预警的三秒里不落");
        }
        int total = 0;
        for (int second = 0; second < EruptionPlan.DURATION / 20; second++) {
            int count = 0;
            for (int tick = 0; tick < 20; tick++) {
                count += EruptionPlan.bombs(EruptionPlan.WARNING + second * 20 + tick, rng);
            }
            assertTrue(count == 1 || count == 2, "第 " + second + " 秒落了 " + count);
            total += count;
        }
        assertTrue(total > 20 && total < 40);
        assertEquals(EruptionPlan.Phase.OVER, EruptionPlan.phase(EruptionPlan.length()));
        assertEquals(0, EruptionPlan.bombs(EruptionPlan.length(), rng));
        for (int i = 0; i < 200; i++) {
            double[] landing = EruptionPlan.landing(rng);
            double distance = Math.hypot(landing[0], landing[1]);
            assertTrue(distance >= EruptionPlan.MIN_RANGE - 1e-9 && distance <= EruptionPlan.MAX_RANGE + 1e-9);
        }
    }

    @Test
    void bombsLandWhereAimedAndBurnNearby() {
        double[] v = EruptionPlan.launch(30, -12, -18, 50, EruptionPlan.GRAVITY);
        double[] end = EruptionPlan.position(v, 50, EruptionPlan.GRAVITY);
        assertEquals(30, end[0], 1e-6);
        assertEquals(-12, end[1], 1e-6);
        assertEquals(-18, end[2], 1e-6);
        assertTrue(v[1] > 0, "往上抛");
        assertEquals(7, EruptionPlan.burn(0), 1e-6);
        assertTrue(EruptionPlan.burn(2) < EruptionPlan.burn(1));
        assertEquals(1, EruptionPlan.burn(2.9), 1e-6);
        assertEquals(0, EruptionPlan.burn(3.5));
        assertEquals(0, EruptionPlan.fireTicks(4));
        assertTrue(EruptionPlan.fireTicks(0) > EruptionPlan.fireTicks(2));
    }

    @Test
    void sporeCloudDriftsWithTheWindAndFades() {
        long planet = 0x4000;
        double[] v = SporeDrift.velocity(planet, 0.8, 0, 0);
        double angle = SporeDrift.windAngle(planet);
        // 大致顺着风
        assertTrue(v[0] * Math.cos(angle) + v[1] * Math.sin(angle) > 0);
        assertTrue(Math.hypot(v[0], v[1]) > Math.hypot(SporeDrift.velocity(planet, 0, 0, 0)[0], SporeDrift.velocity(planet, 0, 0, 0)[1]));
        // 从上风处冒出来
        double[] spawn = SporeDrift.upwind(planet, Rng.of(1));
        assertTrue(spawn[0] * Math.cos(angle) + spawn[1] * Math.sin(angle) < 0);
        assertEquals(0, SporeDrift.density(-1, 1200));
        assertTrue(SporeDrift.density(0, 1200) < 0.1);
        assertEquals(1, SporeDrift.density(600, 1200), 1e-9);
        assertTrue(SporeDrift.density(1195, 1200) < 0.1);
        assertTrue(SporeDrift.inside(3, 0, 3));
        assertFalse(SporeDrift.inside(4, 0, 4));
        assertFalse(SporeDrift.inside(0, 6, 0));
        int life = SporeDrift.lifetime(Rng.of(9));
        assertTrue(life >= 60 * 20 && life <= 90 * 20);
    }

    @Test
    void sporeCloudDrainsToxicProtectionFourTimesFaster() {
        Environment toxic = new Environment(false, Map.of(Hazard.TOXIC, 1.0), false, 0, false, false, false);
        Environment cloud = toxic.withLocal(LocalExposure.NONE.times(Hazard.TOXIC, SporeDrift.TOXIC_FACTOR));
        assertEquals(4, cloud.severity(Hazard.TOXIC), 1e-9);
        LifeSupport outside = new LifeSupport();
        LifeSupport inside = new LifeSupport();
        for (int i = 0; i < 100; i++) {
            outside.tick(toxic, Suit.FULL, false, true);
            inside.tick(cloud, Suit.FULL, false, true);
        }
        double lostOutside = LifeSupport.HAZARD_MAX - outside.protection(Hazard.TOXIC);
        double lostInside = LifeSupport.HAZARD_MAX - inside.protection(Hazard.TOXIC);
        assertEquals(4, lostInside / lostOutside, 1e-3);
        // 两团云叠着也只是 4 倍
        LocalExposure twice = LocalExposure.NONE.times(Hazard.TOXIC, 4).times(Hazard.TOXIC, 4);
        assertEquals(4, twice.apply(Hazard.TOXIC, 1), 1e-9);
    }

    @Test
    void geyserSteamHurtsEvenWithoutThePlanetHazard() {
        Environment calm = new Environment(false, Map.of(), false, 0, false, false, false);
        Environment steam = calm.withLocal(LocalExposure.NONE.plus(Hazard.HEAT, 6));
        assertFalse(calm.exposedTo(Hazard.HEAT));
        assertTrue(steam.exposedTo(Hazard.HEAT));
        assertEquals(6, steam.severity(Hazard.HEAT), 1e-9);
        LifeSupport support = new LifeSupport();
        support.tick(steam, Suit.FULL, false, true);
        assertTrue(support.protection(Hazard.HEAT) < LifeSupport.HAZARD_MAX);
        // 在飞船里不算
        Environment cabin = new Environment(false, Map.of(), false, 0, false, false, true).withLocal(LocalExposure.NONE.plus(Hazard.HEAT, 6));
        assertFalse(cabin.exposedTo(Hazard.HEAT));
    }

    @Test
    void dustDevilSpinsLiftsAndFlings() {
        assertTrue(Vortex.reaches(2, 0, 2));
        assertFalse(Vortex.reaches(4, 0, 0));
        // 上面粗
        assertTrue(Vortex.reaches(4, 10, 0));
        assertFalse(Vortex.reaches(0, 13, 0));
        double[] v = Vortex.carry(2, 1, 0, 1);
        // 在 +x 处：逆时针转，切向是 +z；往上带
        assertTrue(v[2] > 0.3);
        assertTrue(v[1] > 0);
        // 轻的转得快
        assertTrue(Vortex.carry(2, 1, 0, 0.6)[2] > v[2]);
        assertTrue(Vortex.flings(80, 75, 3));
        assertTrue(Vortex.flings(10, 75, 11.5));
        assertFalse(Vortex.flings(10, 75, 3));
        double[] out = Vortex.fling(2, 0, 1);
        assertTrue(out[0] > 0 && out[1] > 0, "往外、往上甩");
        assertTrue(Vortex.liftable(0.6, 1.8));
        assertFalse(Vortex.liftable(3, 3));
        assertEquals(0.6, Vortex.weight(0.1, true), 1e-9);
        assertTrue(Vortex.weight(8, false) > Vortex.weight(0.6, false));
        // 游走的方向一刻只偏一点
        Rng rng = Rng.of(5);
        double heading = 1;
        for (int i = 0; i < 100; i++) {
            double next = Vortex.wander(heading, rng);
            assertTrue(Math.abs(next - heading) <= 0.08 + 1e-9);
            heading = next;
        }
    }
}
