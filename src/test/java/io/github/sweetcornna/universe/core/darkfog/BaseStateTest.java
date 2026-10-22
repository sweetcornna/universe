package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BaseStateTest {
    private static final BaseState.Environment NORMAL = BaseState.Environment.IDLE;

    private static BaseState active(int level) {
        BaseState base = new BaseState(0, 1000, 0);
        base.activate(level, 100_000, 1);
        return base;
    }

    @Test
    void activationGrowsTheFirstBuildingsAndUnits() {
        BaseState base = active(0);
        assertEquals(BaseState.Status.ACTIVE, base.status());
        assertTrue(base.alive(BaseLayout.CORE));
        assertTrue(base.alive(BaseLayout.RELAY));
        // 半小时攒的物质够盖 0 级能盖的两座：突袭者营地和激光塔
        assertTrue(base.alive(2), "raider camp");
        assertTrue(base.alive(3), "laser tower");
        assertFalse(base.alive(4), "ranger camp needs level 2");
        assertTrue(base.units(UnitKind.RAIDER) > 0);
        assertEquals(0, base.units(UnitKind.RANGER));
        assertEquals(100_000, base.updated());
    }

    @Test
    void campsCapUnits() {
        BaseState base = active(0);
        base.advanceTo(100_000 + 6 * 60 * 1200, NORMAL);
        assertEquals(BaseLayout.campCapacity(UnitKind.RAIDER) * base.camps(UnitKind.RAIDER), base.units(UnitKind.RAIDER));
    }

    @Test
    void overdriveBreedsTenTimesFaster() {
        BaseState slow = active(0);
        BaseState fast = active(0);
        slow.setUnits(UnitKind.RAIDER, 0);
        fast.setUnits(UnitKind.RAIDER, 0);
        slow.advanceTo(100_000 + 21 * 20, NORMAL);
        fast.advanceTo(100_000 + 21 * 20, new BaseState.Environment(1, true, 0));
        assertEquals(1, slow.units(UnitKind.RAIDER));
        assertEquals(10, fast.units(UnitKind.RAIDER));
    }

    @Test
    void levelsUpFromDamageAndUnlocksBuildings() {
        BaseState base = active(0);
        base.hurt(DarkFogLevels.xpToNext(0) + DarkFogLevels.xpToNext(1) + 1, 0);
        assertEquals(2, base.level());
        base.advanceTo(100_000 + 20 * 60 * 1200, NORMAL);
        assertTrue(base.alive(4), "ranger camp at level 2");
    }

    @Test
    void losingTheRelayStallsTheBaseAfterItsEnergyRunsOut() {
        BaseState base = active(0);
        base.destroy(BaseLayout.RELAY);
        assertTrue(base.powered());
        long now = 100_000 + BaseState.ENERGY_TICKS + 20;
        base.advanceTo(now, NORMAL);
        assertFalse(base.powered());
        double matter = base.matter();
        base.advanceTo(now + 6000, new BaseState.Environment(1, false, 0.001));
        assertEquals(matter, base.matter());
        assertEquals(0, base.threat());
        assertFalse(base.alive(BaseLayout.RELAY), "relay is not rebuilt in part one");
    }

    @Test
    void destroyedCampsAreRebuiltInOrder() {
        BaseState base = active(0);
        base.destroy(2);
        assertFalse(base.alive(2));
        assertEquals(0, base.units(UnitKind.RAIDER));
        base.advanceTo(100_000 + 10 * 60 * 1200, NORMAL);
        assertTrue(base.alive(2));
    }

    @Test
    void destroyedBuildingsWaitBeforeTheyAreRebuilt() {
        BaseState base = active(0);
        base.destroy(3);
        base.advanceTo(100_000 + (BaseState.REBUILD_DELAY_SECONDS - 10) * 20, NORMAL);
        assertFalse(base.alive(3), "a lost tower doesn't pop straight back");
        base.advanceTo(100_000 + (BaseState.REBUILD_DELAY_SECONDS + 10) * 20, NORMAL);
        assertTrue(base.alive(3));
        assertEquals(BaseState.BUILD_SECONDS - 10, base.buildDelay(), 1);
    }

    @Test
    void destroyingTheCoreEndsTheBase() {
        BaseState base = active(5);
        base.destroy(BaseLayout.CORE);
        assertEquals(BaseState.Status.DESTROYED, base.status());
        assertEquals(0, base.buildings());
        assertEquals(0, base.units(UnitKind.RAIDER));
    }

    @Test
    void threatFillsThenCoolsDownAfterAWave() {
        BaseState base = active(0);
        base.advanceTo(100_000 + 1200, new BaseState.Environment(1, false, 1.0 / 1200));
        assertTrue(base.threatFull());
        Threat.Wave wave = base.launch(DarkFogSettings.NORMAL);
        assertTrue(wave.raiders() > 0);
        base.waveOver();
        assertEquals(0, base.threat());
        base.advanceTo(100_000 + 1200 + Threat.COOLDOWN_TICKS, new BaseState.Environment(1, false, 1.0 / 1200));
        assertEquals(0, base.threat(), 1e-9);
    }

    @Test
    void catchUpIsCapped() {
        BaseState base = active(0);
        base.advanceTo(100_000 + 1000L * 24 * 60 * 1200, NORMAL);
        assertEquals(100_000 + 1000L * 24 * 60 * 1200, base.updated());
    }

    @Test
    void landedRelayStartsBareAtTheHiveLevel() {
        BaseState base = new BaseState(4, 300, -200);
        base.land(7, 50_000);
        assertEquals(BaseState.Status.ACTIVE, base.status());
        assertEquals(7, base.level());
        assertEquals(2, base.buildings(), "only the core and the relay");
        assertTrue(base.relayAlive());
        assertEquals(0, base.matter());
        assertEquals(50_000, base.updated());
        assertEquals(BaseState.BUILD_SECONDS, base.buildDelay());
    }

    @Test
    void relayCanBeRestoredOnlyWhileTheCoreStands() {
        BaseState base = active(0);
        assertFalse(base.restoreRelay(), "the relay is still there");
        base.destroy(BaseLayout.RELAY);
        base.advanceTo(100_000 + BaseState.ENERGY_TICKS + 200, NORMAL);
        assertFalse(base.powered());
        assertTrue(base.restoreRelay());
        assertTrue(base.relayAlive());
        assertTrue(base.powered());
        base.destroy(BaseLayout.CORE);
        assertFalse(base.restoreRelay());
    }

    @Test
    void pendingSitesDoNothing() {
        BaseState base = new BaseState(0, 0, 0);
        base.advanceTo(50_000, NORMAL);
        assertEquals(BaseState.Status.PENDING, base.status());
        assertEquals(0, base.buildings());
    }
}
