package io.github.sweetcornna.universe.core.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;

class DiscoveryLogTest {
    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(2, 2);
    private static final BodyId MOON = BodyId.moon(3, 1, 0);

    @Test
    void keysRoundTripThroughTheirIds() {
        List<DiscoveryKey> keys = List.of(
                DiscoveryKey.system(12),
                DiscoveryKey.body(MOON),
                DiscoveryKey.body(BodyId.planet(0, 2)),
                DiscoveryKey.creature(MOON, 4),
                DiscoveryKey.plant(MOON, 0),
                DiscoveryKey.tree(MOON, 3),
                DiscoveryKey.mineral(MOON, "universe:titanium_ore"));
        for (DiscoveryKey key : keys) {
            assertEquals(Optional.of(key), DiscoveryKey.parse(key.id()), key.id());
        }
        assertEquals("creature:s3/p1/m0#4", DiscoveryKey.creature(MOON, 4).id());
        assertEquals("mineral:s3/p1/m0#universe:titanium_ore", DiscoveryKey.mineral(MOON, "universe:titanium_ore").id());
        for (String bad : List.of("", "system", "system:-1", "comet:3", "body:x3", "creature:s3/p1", "creature:s3/p1#x", "plant:s3/p1#-2")) {
            assertTrue(DiscoveryKey.parse(bad).isEmpty(), bad);
        }
        assertEquals(Optional.of(DiscoveryKey.body(MOON)), DiscoveryKey.creature(MOON, 1).parent());
        assertEquals(Optional.of(DiscoveryKey.system(3)), DiscoveryKey.body(MOON).parent());
        assertTrue(DiscoveryKey.system(3).parent().isEmpty());
    }

    @Test
    void onlyTheFirstDiscoveryCounts() {
        DiscoveryLog log = new DiscoveryLog();
        DiscoveryKey key = DiscoveryKey.body(MOON);
        assertTrue(log.discover(key, ALICE, "Alice", 3).isPresent());
        assertTrue(log.discover(key, BOB, "Bob", 4).isEmpty());
        DiscoveryLog.Entry entry = log.get(key).orElseThrow();
        assertEquals(ALICE, entry.discoverer());
        assertEquals("Alice", entry.discovererName());
        assertEquals(3, entry.day());
        assertNull(entry.customName());
    }

    @Test
    void onlyTheDiscovererCanRename() {
        DiscoveryLog log = new DiscoveryLog();
        DiscoveryKey key = DiscoveryKey.system(5);
        assertEquals(DiscoveryLog.RenameResult.UNKNOWN, log.rename(key, ALICE, "Eden"));
        log.discover(key, ALICE, "Alice", 0);
        assertEquals(DiscoveryLog.RenameResult.NOT_DISCOVERER, log.rename(key, BOB, "Bobland"));
        assertEquals(DiscoveryLog.RenameResult.OK, log.rename(key, ALICE, "  新   伊甸  "));
        assertEquals("新 伊甸", log.get(key).orElseThrow().customName());
        assertEquals(DiscoveryLog.RenameResult.INVALID_NAME, log.rename(key, ALICE, "a\nb"));
        assertEquals(DiscoveryLog.RenameResult.INVALID_NAME, log.rename(key, ALICE, "名".repeat(DiscoveryLog.MAX_NAME + 1)));
        assertEquals(DiscoveryLog.RenameResult.OK, log.rename(key, ALICE, "名".repeat(DiscoveryLog.MAX_NAME)));
        // 清空恢复原名
        assertEquals(DiscoveryLog.RenameResult.OK, log.rename(key, ALICE, "   "));
        assertNull(log.get(key).orElseThrow().customName());
        // 开局就测绘好的不能改名
        DiscoveryKey home = DiscoveryKey.system(0);
        log.discover(home, null, "", 0);
        assertEquals(DiscoveryLog.RenameResult.NOT_DISCOVERER, log.rename(home, ALICE, "Home"));
    }

    @Test
    void childrenAndCounts() {
        DiscoveryLog log = new DiscoveryLog();
        DiscoveryKey body = DiscoveryKey.body(MOON);
        log.discover(DiscoveryKey.system(3), ALICE, "Alice", 0);
        log.discover(body, ALICE, "Alice", 0);
        log.discover(DiscoveryKey.creature(MOON, 0), ALICE, "Alice", 0);
        log.discover(DiscoveryKey.creature(MOON, 2), BOB, "Bob", 0);
        log.discover(DiscoveryKey.plant(MOON, 1), BOB, "Bob", 0);
        log.discover(DiscoveryKey.creature(BodyId.planet(3, 0), 0), BOB, "Bob", 0);
        assertEquals(2, log.countOn(body, DiscoveryKey.Kind.CREATURE));
        assertEquals(1, log.countOn(body, DiscoveryKey.Kind.PLANT));
        assertEquals(3, log.count(DiscoveryKey.Kind.CREATURE));
        assertEquals(3, log.children(body).size());
        assertEquals(List.of(body), log.children(DiscoveryKey.system(3)).stream().map(DiscoveryLog.Entry::key).toList());
    }

    @Test
    void rewardsFavourExoticStarsAndLandableWorlds() {
        Galaxy galaxy = GalaxyGenerator.generate(43, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        for (StarSystem system : galaxy.systems()) {
            int reward = Survey.reward(DiscoveryKey.system(system.index()), galaxy);
            if (system.star().starClass() == StarClass.BLACK_HOLE) {
                assertEquals(20, reward);
            } else {
                assertTrue(reward >= 5 && reward <= 12, "reward " + reward);
            }
            for (Planet planet : system.bodies()) {
                assertEquals(planet.landable() ? 4 : 3, Survey.reward(DiscoveryKey.body(planet.id()), galaxy));
            }
        }
        assertEquals(2, Survey.reward(DiscoveryKey.creature(MOON, 0), galaxy));
        assertEquals(1, Survey.reward(DiscoveryKey.mineral(MOON, "minecraft:iron_ore"), galaxy));
    }

    @Test
    void aPlanetIsCompleteWhenEverySpeciesIsKnown() {
        Galaxy galaxy = GalaxyGenerator.generate(43, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);
        Planet planet = galaxy.systems().stream().flatMap(system -> system.bodies().stream())
                .filter(body -> Survey.census(body).total() > 3)
                .findFirst().orElseThrow();
        Survey.Census census = Survey.census(planet);
        DiscoveryLog log = new DiscoveryLog();
        DiscoveryKey body = DiscoveryKey.body(planet.id());
        assertFalse(Survey.complete(log, body, census));
        for (int i = 0; i < census.creatures(); i++) {
            log.discover(DiscoveryKey.creature(planet.id(), i), ALICE, "Alice", 0);
        }
        for (int i = 0; i < census.plants(); i++) {
            log.discover(DiscoveryKey.plant(planet.id(), i), ALICE, "Alice", 0);
        }
        assertEquals(census.trees() == 0, Survey.complete(log, body, census));
        for (int i = 0; i < census.trees(); i++) {
            log.discover(DiscoveryKey.tree(planet.id(), i), ALICE, "Alice", 0);
        }
        assertTrue(Survey.complete(log, body, census));
        assertEquals(Survey.Census.NONE, Survey.census(galaxy.body(GalaxyGenerator.HOME_PLANET_ID).orElseThrow()));
    }
}
