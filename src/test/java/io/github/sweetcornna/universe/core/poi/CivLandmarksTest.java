package io.github.sweetcornna.universe.core.poi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/** 知识石和聚落这两种地标（ADR-0027 §五）：往后加，不动已有的地标。 */
class CivLandmarksTest {
    private static List<Planet> planets() {
        return GalaxyGenerator.generate(20261016L, 64).systems().stream().flatMap(s -> s.bodies().stream()).toList();
    }

    @Test
    void newTypesAreAppendedWithTheirOwnSeedKeys() {
        assertEquals(LandmarkType.ABANDONED_BUILDING.ordinal() + 1, LandmarkType.KNOWLEDGE_STONE.ordinal());
        assertEquals(LandmarkType.KNOWLEDGE_STONE.ordinal() + 1, LandmarkType.SETTLEMENT.ordinal());
        assertEquals(LandmarkType.values().length, LandmarkType.SETTLEMENT.ordinal() + 1);
        assertEquals("landmark/knowledge_stone", LandmarkType.KNOWLEDGE_STONE.seedKey());
        assertEquals("landmark/settlement", LandmarkType.SETTLEMENT.seedKey());
        // ADR 的数：知识石网格 500 格、一格五成、占地半径 2；聚落网格 1400 格、一格四成
        assertEquals(500, LandmarkType.KNOWLEDGE_STONE.grid());
        assertEquals(0.5, LandmarkType.KNOWLEDGE_STONE.chance());
        assertEquals(2, LandmarkType.KNOWLEDGE_STONE.radius());
        assertEquals(1400, LandmarkType.SETTLEMENT.grid());
        assertEquals(0.4, LandmarkType.SETTLEMENT.chance());
        assertTrue(LandmarkType.SETTLEMENT.radius() >= 12 && LandmarkType.SETTLEMENT.radius() <= 14);
        assertTrue(!LandmarkType.KNOWLEDGE_STONE.seabed() && !LandmarkType.SETTLEMENT.seabed());
    }

    /**
     * 加了两种以后，已有的 8 种地标一个都没挪：前 12 颗能登陆的星球、原点 2500 格以内的指纹，是加之前用当时的代码算的。
     * 排在后面的种类不影响前面的（挤占只看排在前面的），派生键也各是各的。
     */
    @Test
    void oldLandmarksStayWhereTheyWere() {
        List<Planet> landable = planets().stream().filter(Planet::landable).limit(12).toList();
        int count = 0;
        long sum = 0;
        for (Planet planet : landable) {
            for (Landmark l : LandmarkLayout.of(planet, LandmarkLayout.Blocked.NONE).within(0, 0, 2500)) {
                if (l.type().ordinal() > LandmarkType.ABANDONED_BUILDING.ordinal()) {
                    continue;
                }
                count++;
                long h = l.type().ordinal();
                h = h * 1_000_003L + l.x();
                h = h * 1_000_003L + l.z();
                h = h * 1_000_003L + l.rotation();
                sum += h ^ l.seed();
            }
        }
        assertEquals(309, count);
        assertEquals(-7155984814654522723L, sum);
    }

    @Test
    void stonesOnEveryLandablePlanetSettlementsOnlyWithAirAndNotBarren() {
        int settled = 0;
        int unsettled = 0;
        for (Planet planet : planets()) {
            Set<LandmarkType> kinds = LandmarkLayout.kinds(planet);
            String id = planet.id() + " " + planet.type();
            assertEquals(planet.landable(), kinds.contains(LandmarkType.KNOWLEDGE_STONE), id);
            boolean settlements = planet.landable() && planet.atmosphere().present() && planet.type() != PlanetType.BARREN;
            assertEquals(settlements, kinds.contains(LandmarkType.SETTLEMENT), id);
            if (planet.landable()) {
                if (settlements) {
                    settled++;
                } else {
                    unsettled++;
                }
            }
        }
        assertTrue(settled > 0 && unsettled > 0);
    }

    @Test
    void realPlanetsGetStonesAndSettlementsOnDryGround() {
        int stones = 0;
        int settlements = 0;
        for (Planet planet : planets()) {
            if (!LandmarkLayout.kinds(planet).contains(LandmarkType.SETTLEMENT) || settlements >= 6) {
                continue;
            }
            LandmarkLayout layout = LandmarkLayout.of(planet, LandmarkLayout.Blocked.NONE);
            for (Landmark l : layout.within(0, 0, 4000)) {
                if (l.type() == LandmarkType.KNOWLEDGE_STONE) {
                    stones++;
                } else if (l.type() == LandmarkType.SETTLEMENT) {
                    settlements++;
                    // 聚落离别的地标够远：排在前面的种类都让开了
                    for (Landmark other : layout.within(l.x(), l.z(), 40)) {
                        assertTrue(other.equals(l) || other.type() == LandmarkType.SETTLEMENT
                                || l.distance(other.x(), other.z()) >= l.type().radius() + other.type().radius() + LandmarkLayout.SPACING - 1,
                                planet.id() + " " + l + " " + other);
                    }
                }
            }
        }
        assertTrue(stones > 20, "stones " + stones);
        assertTrue(settlements > 0);
    }
}
