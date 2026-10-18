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
    void thresholdsMapToLevels() {
        assertEquals(0, Wanted.levelFor(29.9));
        assertEquals(1, Wanted.levelFor(30));
        assertEquals(2, Wanted.levelFor(60));
        assertEquals(3, Wanted.levelFor(119));
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
    void hidingForTwentySecondsEscapes() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int i = 0; i < Wanted.ESCAPE_TICKS - 1; i++) {
            assertFalse(wanted.tick(false));
        }
        assertEquals(1, wanted.level());
        assertTrue(wanted.tick(false));
        assertEquals(0, wanted.level());
        assertEquals(0, wanted.alert());
    }

    @Test
    void beingSeenResetsTheEscapeTimer() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        for (int i = 0; i < Wanted.ESCAPE_TICKS - 10; i++) {
            wanted.tick(false);
        }
        wanted.tick(true);
        assertEquals(0, wanted.escapeProgress(), 1e-9);
        for (int i = 0; i < Wanted.ESCAPE_TICKS - 1; i++) {
            assertFalse(wanted.tick(false));
        }
    }

    @Test
    void destroyingSentinelsEscalates() {
        Wanted wanted = new Wanted();
        wanted.offend(Offense.ATTACK_SENTINEL, 1);
        wanted.offend(Offense.DESTROY_SENTINEL, 1);
        wanted.offend(Offense.DESTROY_SENTINEL, 1);
        assertEquals(2, wanted.level());
        for (int i = 0; i < 4; i++) {
            wanted.offend(Offense.DESTROY_SENTINEL, 1);
        }
        assertEquals(3, wanted.level());
        assertTrue(wanted.alert() <= Wanted.MAX);
    }

    @Test
    void responseGrowsWithLevel() {
        assertEquals(0, Wanted.drones(0, SentinelLevel.NORMAL));
        assertEquals(2, Wanted.drones(1, SentinelLevel.NORMAL));
        assertEquals(4, Wanted.drones(2, SentinelLevel.NORMAL));
        assertEquals(5, Wanted.drones(3, SentinelLevel.AGGRESSIVE));
        assertEquals(0, Wanted.heavies(2));
        assertEquals(1, Wanted.heavies(3));
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
