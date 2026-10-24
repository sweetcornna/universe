package io.github.sweetcornna.universe.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.fishing.CatchRoll.Odds;
import io.github.sweetcornna.universe.core.fishing.CatchRoll.Situation;
import io.github.sweetcornna.universe.core.procgen.Rng;

/** 咬钩的是什么（ADR-0034 §一）：各样东西的概率、条件、鱼饵和重量。 */
class CatchRollTest {
    private static final Situation DAY = new Situation(true, false, 6);
    private static final Situation NIGHT = new Situation(false, false, 6);
    private static final List<FishSpecies> FRESH = FishTable.of(Habitat.FRESH);

    private static FishSpecies fish(String id) {
        return FishTable.byId(id).orElseThrow();
    }

    @Test
    void rodOddsMatchTheCharter() {
        Map<Catch.Kind, Integer> counts = new EnumMap<>(Catch.Kind.class);
        Rng rng = Rng.of(1);
        int rolls = 200_000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(CatchRoll.roll(FRESH, DAY, Bait.NONE, false, Odds.ROD, rng).kind(), 1, Integer::sum);
        }
        assertEquals(0.87, counts.get(Catch.Kind.FISH) / (double) rolls, 0.005);
        assertEquals(0.10, counts.get(Catch.Kind.JUNK) / (double) rolls, 0.005);
        double treasure = (counts.get(Catch.Kind.BOTTLE) + counts.get(Catch.Kind.CACHE)) / (double) rolls;
        assertEquals(0.025, treasure, 0.002);
        assertEquals(0.005, counts.get(Catch.Kind.MODULE) / (double) rolls, 0.001);
    }

    @Test
    void timeConditions() {
        FishSpecies loach = fish("mud_loach");
        FishSpecies gudgeon = fish("reed_gudgeon");
        assertEquals(0, CatchRoll.weight(loach, DAY, Bait.NONE, false, Odds.ROD));
        assertTrue(CatchRoll.weight(loach, NIGHT, Bait.NONE, false, Odds.ROD) > 0);
        assertEquals(0, CatchRoll.weight(gudgeon, NIGHT, Bait.NONE, false, Odds.ROD));
        // 夜光饵：白天也咬，概率减半；夜里翻倍
        double night = CatchRoll.weight(loach, NIGHT, Bait.NONE, false, Odds.ROD);
        assertEquals(night / 2, CatchRoll.weight(loach, DAY, Bait.GLOW, false, Odds.ROD), 1e-9);
        assertEquals(night * 2, CatchRoll.weight(loach, NIGHT, Bait.GLOW, false, Odds.ROD), 1e-9);
    }

    @Test
    void stormConditionAndStaticLure() {
        FishSpecies thunder = fish("thunder_char");
        Situation storm = new Situation(true, true, 6);
        assertEquals(0, CatchRoll.weight(thunder, DAY, Bait.NONE, false, Odds.ROD));
        double inStorm = CatchRoll.weight(thunder, storm, Bait.NONE, false, Odds.ROD);
        assertTrue(inStorm > 0);
        assertEquals(inStorm / 3, CatchRoll.weight(thunder, DAY, Bait.STATIC, false, Odds.ROD), 1e-9);
    }

    @Test
    void depthConditions() {
        FishSpecies sturgeon = fish("river_sturgeon");
        FishSpecies crucian = fish("silver_crucian");
        assertEquals(0, CatchRoll.weight(sturgeon, new Situation(true, false, 7), Bait.NONE, false, Odds.ROD));
        assertTrue(CatchRoll.weight(sturgeon, new Situation(true, false, 8), Bait.NONE, false, Odds.ROD) > 0);
        assertTrue(CatchRoll.weight(crucian, new Situation(true, false, 4), Bait.NONE, false, Odds.ROD) > 0);
        assertEquals(0, CatchRoll.weight(crucian, new Situation(true, false, 5), Bait.NONE, false, Odds.ROD));
    }

    @Test
    void baitsShiftTheOdds() {
        FishSpecies trout = fish("brook_trout");
        FishSpecies dace = fish("creek_dace");
        FishSpecies pike = fish("striped_pike");
        Situation deep = new Situation(true, false, 10);
        FishSpecies sturgeon = fish("river_sturgeon");
        double base = CatchRoll.weight(trout, DAY, Bait.NONE, false, Odds.ROD);
        assertEquals(base * 2, CatchRoll.weight(trout, DAY, Bait.SPINNER, false, Odds.ROD), 1e-9);
        assertEquals(base * Bait.DISH_BONUS, CatchRoll.weight(trout, DAY, Bait.DISH, false, Odds.ROD), 1e-9);
        assertEquals(base * CatchRoll.RELEASE_BONUS, CatchRoll.weight(trout, DAY, Bait.NONE, true, Odds.ROD), 1e-9);
        // 普通的鱼不吃这些加成
        double common = CatchRoll.weight(dace, DAY, Bait.NONE, false, Odds.ROD);
        assertEquals(common, CatchRoll.weight(dace, DAY, Bait.SPINNER, true, Odds.ROD), 1e-9);
        assertEquals(CatchRoll.weight(pike, DAY, Bait.NONE, false, Odds.ROD) * 2, CatchRoll.weight(pike, DAY, Bait.SINKER, false, Odds.ROD),
                1e-9);
        assertEquals(CatchRoll.weight(sturgeon, deep, Bait.NONE, false, Odds.ROD) * 4,
                CatchRoll.weight(sturgeon, deep, Bait.STAR_CORE, false, Odds.ROD), 1e-9);
    }

    @Test
    void nothingBitingGivesJunk() {
        // 只放一条夜里才咬的鱼，白天钓
        Catch result = CatchRoll.fish(List.of(fish("mud_loach")), DAY, Bait.NONE, false, Odds.ROD, Rng.of(3));
        assertEquals(Catch.Kind.JUNK, result.kind());
    }

    @Test
    void trapOnlyGivesCommonAndUncommon() {
        Rng rng = Rng.of(7);
        int junk = 0;
        for (int i = 0; i < 20_000; i++) {
            Catch result = CatchRoll.roll(FRESH, new Situation(true, true, 10), Bait.STAR_CORE, false, Odds.TRAP, rng);
            assertTrue(result.kind() == Catch.Kind.FISH || result.kind() == Catch.Kind.JUNK, result.kind().name());
            if (result.isFish()) {
                assertTrue(result.species().rarity().ordinal() <= Rarity.UNCOMMON.ordinal(), result.species().id());
            } else {
                junk++;
            }
        }
        assertEquals(0.15, junk / 20_000.0, 0.01);
    }

    @Test
    void betterPondsGiveBetterFish() {
        double low = specialShare(Odds.pond(0));
        double high = specialShare(Odds.pond(3));
        assertTrue(high > low + 0.15, low + " → " + high);
    }

    private static double specialShare(Odds odds) {
        Rng rng = Rng.of(11);
        int special = 0;
        int fish = 0;
        for (int i = 0; i < 20_000; i++) {
            Catch result = CatchRoll.roll(FRESH, new Situation(true, true, 10), Bait.NONE, false, odds, rng);
            if (result.isFish()) {
                fish++;
                if (result.species().rarity().special()) {
                    special++;
                }
            }
        }
        return special / (double) fish;
    }

    @Test
    void weightsStayInRangeAndDeepWaterIsHeavier() {
        FishSpecies pike = fish("striped_pike");
        Rng rng = Rng.of(5);
        double shallow = 0;
        double deep = 0;
        double sinker = 0;
        for (int i = 0; i < 5000; i++) {
            double a = CatchRoll.kilograms(pike, 2, Bait.NONE, rng);
            double b = CatchRoll.kilograms(pike, 24, Bait.NONE, rng);
            double c = CatchRoll.kilograms(pike, 2, Bait.SINKER, rng);
            for (double kg : new double[] {a, b, c}) {
                assertTrue(kg >= SizeClass.LARGE.min() && kg <= SizeClass.LARGE.max(), "超出个头的范围：" + kg);
            }
            shallow += a;
            deep += b;
            sinker += c;
        }
        assertTrue(deep > shallow * 1.15, shallow + " / " + deep);
        assertTrue(sinker > shallow * 1.1, shallow + " / " + sinker);
    }
}
