package io.github.sweetcornna.universe.core.fossil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.PlanetType;

/** 挖到哪种骨头（ADR-0034 §三）。 */
class FossilFindsTest {
    private static Map<BoneFamily, Integer> sample(PlanetType type, boolean sentry) {
        Map<BoneFamily, Integer> counts = new EnumMap<>(BoneFamily.class);
        Random random = new Random(42);
        for (int i = 0; i < 20000; i++) {
            Bone bone = sentry ? FossilFinds.sentry(type, random.nextDouble(), random.nextDouble())
                    : FossilFinds.dig(type, random.nextDouble(), random.nextDouble());
            counts.merge(bone.family(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void exoticOnlyOnExoticPlanets() {
        for (PlanetType type : PlanetType.values()) {
            double exotic = FossilFinds.weights(type).get(BoneFamily.EXOTIC);
            assertEquals(type == PlanetType.EXOTIC, exotic > 0, type.name());
            assertEquals(type == PlanetType.EXOTIC, sample(type, false).getOrDefault(BoneFamily.EXOTIC, 0) > 0, type.name());
        }
    }

    @Test
    void planetTypesLeanTowardTheirFamilies() {
        Map<BoneFamily, Integer> ocean = sample(PlanetType.OCEAN, false);
        Map<BoneFamily, Integer> habitable = sample(PlanetType.HABITABLE, false);
        assertTrue(ocean.get(BoneFamily.MARINE) > 2 * habitable.get(BoneFamily.MARINE), "海洋星球的海兽不多：" + ocean);
        Map<BoneFamily, Integer> scorched = sample(PlanetType.SCORCHED, false);
        assertTrue(scorched.get(BoneFamily.WORM) > 2 * habitable.get(BoneFamily.WORM), "灼热星球的巨蠕不多：" + scorched);
        // 常见的确实常见
        assertTrue(habitable.get(BoneFamily.TITAN) > habitable.get(BoneFamily.FLYER));
        assertTrue(habitable.get(BoneFamily.FLYER) > habitable.get(BoneFamily.ARTHROPOD));
    }

    @Test
    void sentriesDropRarerBones() {
        for (PlanetType type : PlanetType.values()) {
            Map<BoneFamily, Integer> dirt = sample(type, false);
            Map<BoneFamily, Integer> sentry = sample(type, true);
            int dirtCommon = dirt.getOrDefault(BoneFamily.TITAN, 0) + dirt.getOrDefault(BoneFamily.PREDATOR, 0)
                    + dirt.getOrDefault(BoneFamily.LONGNECK, 0);
            int sentryCommon = sentry.getOrDefault(BoneFamily.TITAN, 0) + sentry.getOrDefault(BoneFamily.PREDATOR, 0)
                    + sentry.getOrDefault(BoneFamily.LONGNECK, 0);
            assertTrue(sentryCommon * 2 < dirtCommon, type + "：石哨兵掉的常见骨头不比化石土少");
        }
    }

    @Test
    void partsAreSpreadWithFewerSkulls() {
        Map<BonePart, Integer> counts = new EnumMap<>(BonePart.class);
        Random random = new Random(7);
        for (int i = 0; i < 20000; i++) {
            counts.merge(FossilFinds.part(random.nextDouble()), 1, Integer::sum);
        }
        assertEquals(BonePart.values().length, counts.size());
        assertTrue(counts.get(BonePart.SKULL) < counts.get(BonePart.TAIL));
        assertEquals(BonePart.SKULL, FossilFinds.part(0));
        assertEquals(BonePart.TAIL, FossilFinds.part(0.999999));
    }
}
