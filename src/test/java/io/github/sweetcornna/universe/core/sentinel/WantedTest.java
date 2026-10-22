package io.github.sweetcornna.universe.core.sentinel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class WantedTest {
    @Test
    void alertStartsLevelOneOnly() {
        assertEquals(0, Wanted.levelFor(29.9));
        assertEquals(1, Wanted.levelFor(30));
        assertEquals(1, Wanted.levelFor(119));
    }

    @Test
    void miningOresEventuallyRaisesWanted() {
        Wanted wanted = new Wanted();
        int ores = 0;
        while (!wanted.wanted()) {
            wanted.offend(Offense.LASER_MINING_ORE, SentinelLevel.NORMAL.alertFactor());
            wanted.tick(false);
            ores++;
        }
        assertEquals(15, ores);
        assertEquals(1, wanted.level());
    }

    @Test
    void lowSentinelsTolerateTwiceAsMuch() {
        Wanted low = new Wanted();
        for (int i = 0; i < 15; i++) {
            low.offend(Offense.LASER_MINING_ORE, SentinelLevel.LOW.alertFactor());
        }
        assertFalse(low.wanted());
    }

    @Test
    void noSentinelsNoAlert() {
        Wanted wanted = new Wanted();
        assertFalse(wanted.offend(Offense.ATTACK_SENTINEL, SentinelLevel.NONE.alertFactor()));
        assertEquals(0, wanted.alert());
    }

    @Test
    void attackingASentinelProvokesImmediately() {
        Wanted wanted = new Wanted();
        assertTrue(wanted.offend(Offense.ATTACK_SENTINEL, 1));
        assertEquals(1, wanted.level());
    }

    @Test
    void alertDecaysAfterGraceWhenNotWanted() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.KILL_CREATURE, 1);
        for (int i = 0; i < Wanted.GRACE; i++) {
            wanted.tick(false);
        }
        assertEquals(12, wanted.alert(), 1e-9);
        // 之后每秒降 1
        for (int i = 0; i < 20 * 5; i++) {
            wanted.tick(false);
        }
        assertEquals(7, wanted.alert(), 1e-6);
    }

    @Test
    void wantedLevelDoesNotDecay() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int i = 0; i < 2000; i++) {
            wanted.tick(true);
        }
        assertEquals(1, wanted.level());
    }

    @Test
    void hidingForThirtySecondsEscapesLevelOne() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        assertEquals(30 * 20, Wanted.escapeTicks(1));
        for (int i = 0; i < Wanted.escapeTicks(1) - 1; i++) {
            assertFalse(wanted.tick(false));
        }
        assertEquals(1, wanted.level());
        assertTrue(wanted.tick(false));
        assertEquals(0, wanted.level());
        assertEquals(0, wanted.alert());
    }

    @Test
    void higherLevelsTakeLongerToEscape() {
        // 20 + 10 × 级别 秒
        assertEquals(40 * 20, Wanted.escapeTicks(2));
        assertEquals(70 * 20, Wanted.escapeTicks(5));
        Wanted wanted = new Wanted(0, 5);
        for (int i = 0; i < 69 * 20; i++) {
            assertFalse(wanted.tick(false));
        }
        assertEquals(5, wanted.level());
        assertTrue(wanted.escapeProgress() > 0.98F);
    }

    @Test
    void beingSeenResetsTheEscapeTimer() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int i = 0; i < Wanted.escapeTicks(1) - 10; i++) {
            wanted.tick(false);
        }
        wanted.tick(true);
        assertEquals(0, wanted.escapeProgress(), 1e-9);
        for (int i = 0; i < Wanted.escapeTicks(1) - 1; i++) {
            assertFalse(wanted.tick(false));
        }
    }

    @Test
    void offendingWhileWantedDoesNotEscalate() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int i = 0; i < 20; i++) {
            assertFalse(wanted.offend(Offense.DESTROY_SENTINEL, 1.5));
        }
        assertEquals(1, wanted.level());
        assertTrue(wanted.alert() <= Wanted.MAX);
    }

    @Test
    void clearingWavesClimbsToFiveThenWithdraws() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int level = 2; level <= Wanted.MAX_LEVEL; level++) {
            assertEquals(Wanted.Outcome.ESCALATED, wanted.waveCleared());
            assertEquals(level, wanted.level());
        }
        assertEquals(Wanted.Outcome.WITHDRAWN, wanted.waveCleared());
        assertFalse(wanted.wanted());
        assertEquals(0, wanted.alert());
        assertEquals(Wanted.Outcome.NONE, wanted.waveCleared());
    }

    @Test
    void clearingAWaveResetsTheEscapeTimer() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int i = 0; i < 200; i++) {
            wanted.tick(false);
        }
        wanted.waveCleared();
        assertEquals(0, wanted.escapeProgress(), 1e-9);
        assertEquals(Wanted.escapeTicks(2), wanted.escapeRemaining());
    }

    @Test
    void severeOffensesJumpStraightToLevelThree() {
        for (Offense offense : new Offense[] {Offense.GRAVITINO, Offense.FACILITY_DOOR}) {
            Wanted fresh = new Wanted();
            assertTrue(fresh.offend(offense, 0.5));
            assertEquals(3, fresh.level(), offense.name());
            // 已经在一级，也跳到三级；已经在四级，不降
            Wanted low = new Wanted();
            low.offend(Offense.ATTACK_SENTINEL, 1);
            assertTrue(low.offend(offense, 1));
            assertEquals(3, low.level());
            Wanted high = new Wanted(60, 4);
            assertFalse(high.offend(offense, 1));
            assertEquals(4, high.level());
        }
    }

    @Test
    void savedLevelsAreClampedToFive() {
        assertEquals(3, new Wanted(90, 3).level());
        assertEquals(Wanted.MAX_LEVEL, new Wanted(500, 9).level());
        assertEquals(Wanted.MAX, new Wanted(500, 9).alert());
    }

    @Test
    void planetLevelsFollowTheRules() {
        Galaxy galaxy = GalaxyGenerator.generate(12345L, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        Map<SentinelLevel, Integer> counts = new EnumMap<>(SentinelLevel.class);
        for (StarSystem system : galaxy.systems()) {
            for (Planet planet : system.planets()) {
                check(planet, counts);
                for (Planet moon : planet.moons()) {
                    check(moon, counts);
                }
            }
        }
        for (SentinelLevel level : SentinelLevel.values()) {
            assertTrue(counts.getOrDefault(level, 0) > 0, "没有一颗星球是" + level);
        }
    }

    private static void check(Planet planet, Map<SentinelLevel, Integer> counts) {
        SentinelLevel level = SentinelLevel.of(planet);
        assertEquals(level, SentinelLevel.of(planet), "同一颗星球的守卫强度要固定");
        if (!planet.landable()) {
            assertEquals(SentinelLevel.NONE, level);
        }
        if (planet.id().system() == GalaxyGenerator.HOME_SYSTEM) {
            assertTrue(level.ordinal() <= SentinelLevel.LOW.ordinal(), "母星系的守卫最多是稀少");
        }
        if (planet.ecology().lifeless()) {
            assertTrue(level != SentinelLevel.AGGRESSIVE, "没有生命的星球不会是警觉");
        }
        counts.merge(level, 1, Integer::sum);
    }
}
