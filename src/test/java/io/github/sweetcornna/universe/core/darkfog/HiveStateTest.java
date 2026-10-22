package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HiveStateTest {
    private static final HiveState.Environment IDLE = HiveState.Environment.IDLE;
    private static final long START = 100_000;

    private static HiveState active(int level) {
        HiveState hive = new HiveState(3, 30000, 20, -5000);
        hive.activate(level, START, 1, 1);
        return hive;
    }

    private static long seconds(long seconds) {
        return START + seconds * 20;
    }

    @Test
    void activationGrowsTheFirstBuildingsAndLancers() {
        HiveState hive = active(0);
        assertEquals(HiveState.Status.ACTIVE, hive.status());
        assertTrue(hive.alive(HiveLayout.CORE));
        assertTrue(hive.alive(HiveLayout.FIRST_RECEIVER));
        // 半小时攒的物质够盖 0 级能盖的两座：枪骑港和相位激光塔
        assertTrue(hive.alive(2), "lancer port");
        assertTrue(hive.alive(3), "phase laser");
        assertFalse(hive.alive(4), "the second receiver needs level 1");
        assertTrue(hive.units(SpaceUnitKind.LANCER) > 0);
        assertEquals(0, hive.units(SpaceUnitKind.HUMPBACK));
        assertEquals(START, hive.updated());
    }

    @Test
    void foundedHiveStartsBareAtTheSeedLevel() {
        HiveState hive = new HiveState(5, 0, 0, 40000);
        hive.found(6, START);
        assertEquals(6, hive.level());
        assertEquals(2, hive.buildings(), "only the core and one receiver");
        assertEquals(0, hive.matter());
        assertEquals(HiveState.BUILD_SECONDS, hive.buildDelay());
    }

    @Test
    void receiversAndStarlightMakeMoreMatter() {
        HiveState hive = new HiveState(0, 0, 0, 0);
        hive.found(0, START);
        double dim = hive.matterPerSecond(new HiveState.Environment(1, 0.5, 0, 0, 1));
        double bright = hive.matterPerSecond(new HiveState.Environment(1, 2, 0, 0, 1));
        double siphon = hive.matterPerSecond(new HiveState.Environment(1, 2, 5000, 0, 1));
        assertTrue(bright > dim);
        assertTrue(siphon > bright, "stealing Dyson power adds matter");
        assertEquals(2 * bright, hive.matterPerSecond(new HiveState.Environment(2, 2, 0, 0, 1)), 1e-9);
    }

    @Test
    void buildsOneAtATimeAndWaitsAfterLosses() {
        HiveState hive = new HiveState(0, 0, 0, 0);
        hive.found(0, START);
        hive.setMatter(1000);
        hive.advanceTo(seconds(HiveState.BUILD_SECONDS + 1), IDLE);
        assertTrue(hive.alive(2));
        assertFalse(hive.alive(3), "the next one waits");
        hive.advanceTo(seconds(2L * HiveState.BUILD_SECONDS + 2), IDLE);
        assertTrue(hive.alive(3));
        hive.destroy(3);
        assertEquals(HiveState.REBUILD_DELAY_SECONDS, hive.buildDelay());
        hive.advanceTo(seconds(2L * HiveState.BUILD_SECONDS + HiveState.REBUILD_DELAY_SECONDS - 10), IDLE);
        assertFalse(hive.alive(3), "a lost tower doesn't pop straight back");
        hive.advanceTo(seconds(2L * HiveState.BUILD_SECONDS + HiveState.REBUILD_DELAY_SECONDS + 10), IDLE);
        assertTrue(hive.alive(3));
    }

    @Test
    void portsBreedUpToTheirCapacity() {
        HiveState hive = active(0);
        hive.setMatter(HiveState.MAX_MATTER);
        hive.advanceTo(seconds(3600), IDLE);
        assertEquals(hive.capacity(SpaceUnitKind.LANCER), hive.units(SpaceUnitKind.LANCER));
        hive.destroy(2);
        assertEquals(0, hive.capacity(SpaceUnitKind.LANCER));
        assertEquals(0, hive.units(SpaceUnitKind.LANCER), "lancers without a port are lost");
    }

    @Test
    void threatFillsSlowlyFromPowerAndFastFromRelayHits() {
        DarkFogSettings settings = DarkFogSettings.DEFAULT;
        double perTick = HiveState.powerThreatPerTick(400_000, settings);
        assertEquals(Threat.powerPerTick(400_000, settings) / 3, perTick, 1e-12);
        HiveState hive = active(0);
        hive.addThreat(HiveState.RELAY_DAMAGE_TO_FILL * HiveState.relayThreatPerDamage(settings));
        assertTrue(hive.threatFull(), "a relay's worth of damage fills the threat");
        HiveState passive = active(0);
        passive.addThreat(1000 * HiveState.relayThreatPerDamage(new DarkFogSettings(true, DarkFogSettings.PASSIVE, 0, 1, 1, 1, 1)));
        assertEquals(0, passive.threat());
    }

    @Test
    void raidTakesAShareOfLancersAndCoolsDownAfterwards() {
        HiveState hive = active(0);
        hive.setUnits(SpaceUnitKind.LANCER, 10);
        hive.setThreat(1);
        assertTrue(hive.raidReady());
        int size = hive.raidSize(DarkFogSettings.NORMAL);
        assertEquals(5, size);
        assertEquals(0, hive.raidSize(DarkFogSettings.PASSIVE));
        hive.raidLaunched(size);
        assertEquals(5, hive.units(SpaceUnitKind.LANCER));
        hive.raidOver(3);
        assertEquals(8, hive.units(SpaceUnitKind.LANCER));
        assertEquals(0, hive.threat());
        assertEquals(HiveState.RAID_COOLDOWN_TICKS, hive.cooldown());
        hive.advanceTo(seconds(HiveState.RAID_COOLDOWN_TICKS / 20 - 5), new HiveState.Environment(1, 1, 0, 1.0 / 1200, 1));
        assertEquals(0, hive.threat(), "no threat while cooling down");
    }

    @Test
    void relaysAndSeedsNeedLevelMatterAndTime() {
        HiveState hive = active(0);
        hive.setMatter(HiveState.MAX_MATTER);
        assertFalse(hive.relayReady(), "level 0 sends no relays");
        hive.setLevel(HiveState.RELAY_MIN_LEVEL);
        assertTrue(hive.relayReady());
        hive.relayLaunched();
        assertFalse(hive.relayReady());
        assertEquals(HiveState.MAX_MATTER - HiveState.RELAY_COST, hive.matter(), 1e-9);
        hive.setLevel(HiveState.SEED_MIN_LEVEL);
        hive.setMatter(HiveState.MAX_MATTER);
        assertTrue(hive.seedReady());
        hive.seedFailed();
        assertFalse(hive.seedReady());
        hive.advanceTo(seconds(HiveState.SEED_RETRY_SECONDS + 1), IDLE);
        assertTrue(hive.seedReady());
    }

    @Test
    void coreLossWithRelaysRebuildsLower() {
        HiveState hive = active(0);
        hive.setLevel(7);
        hive.collapse(true);
        assertEquals(HiveState.Status.REBUILDING, hive.status());
        assertEquals(0, hive.buildings());
        assertEquals(0, hive.units(SpaceUnitKind.LANCER));
        hive.advanceTo(seconds(HiveState.RESURRECT_SECONDS - 5), IDLE);
        assertEquals(HiveState.Status.REBUILDING, hive.status());
        hive.advanceTo(seconds(HiveState.RESURRECT_SECONDS + 5), IDLE);
        assertEquals(HiveState.Status.ACTIVE, hive.status());
        assertEquals(7 - HiveState.RESURRECT_LEVEL_LOSS, hive.level());
        assertTrue(hive.alive(HiveLayout.CORE));
    }

    @Test
    void coreLossWithoutRelaysEndsTheHive() {
        HiveState gone = active(0);
        gone.collapse(false);
        assertEquals(HiveState.Status.GONE, gone.status());
        HiveState waiting = active(0);
        waiting.collapse(true);
        waiting.advanceTo(seconds(60), new HiveState.Environment(1, 1, 0, 0, 0));
        assertEquals(HiveState.Status.GONE, waiting.status(), "relays lost while waiting");
        assertEquals(seconds(60), waiting.updated());
    }

    @Test
    void strikeKillsUnitsAndBuildingsButNeverTheCore() {
        HiveState hive = active(0);
        hive.setUnits(SpaceUnitKind.LANCER, 4);
        hive.setUnits(SpaceUnitKind.HUMPBACK, 2);
        int before = hive.buildings();
        int[] result = hive.strike(2);
        assertArrayEquals(new int[] {6, 1}, result);
        assertEquals(0, hive.units(SpaceUnitKind.LANCER));
        assertEquals(0, hive.units(SpaceUnitKind.HUMPBACK));
        assertEquals(before - 1, hive.buildings());
        hive.strike(100);
        assertTrue(hive.alive(HiveLayout.CORE));
        assertEquals(1, hive.buildings());
    }

    @Test
    void siphonGrowsWithReceivers() {
        HiveState hive = new HiveState(0, 0, 0, 0);
        hive.found(0, START);
        assertEquals(HiveState.SIPHON_PER_RECEIVER, hive.siphonShare(), 1e-12);
        hive.collapse(true);
        assertEquals(0, hive.siphonShare());
    }

    @Test
    void catchUpIsCapped() {
        HiveState hive = active(0);
        hive.advanceTo(START + 1000L * 24 * 60 * 1200, IDLE);
        assertEquals(START + 1000L * 24 * 60 * 1200, hive.updated());
    }

    @Test
    void timersRoundTripThroughTheSaveConstructor() {
        HiveState hive = active(2);
        hive.relayFailed();
        hive.raidOver(0);
        HiveState copy = new HiveState(hive.system(), hive.x(), hive.y(), hive.z(), hive.status(), hive.level(), hive.xp(), hive.matter(),
                hive.threat(), hive.healthArray(), hive.unitArray(), hive.progressArray(), hive.updated(), hive.timers());
        assertArrayEquals(hive.timers(), copy.timers());
        assertArrayEquals(hive.healthArray(), copy.healthArray());
        assertEquals(hive.units(SpaceUnitKind.LANCER), copy.units(SpaceUnitKind.LANCER));
    }
}
