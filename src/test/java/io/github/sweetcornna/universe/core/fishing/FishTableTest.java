package io.github.sweetcornna.universe.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 鱼表（ADR-0034 §一）：96 种，8 种水域各 12 种，档次和条件的分布照立项写的来。 */
class FishTableTest {
    @Test
    void ninetySixSpeciesTwelvePerHabitat() {
        assertEquals(96, FishTable.ALL.size());
        for (Habitat habitat : Habitat.values()) {
            assertEquals(12, FishTable.of(habitat).size(), habitat.name());
        }
        for (int i = 0; i < FishTable.ALL.size(); i++) {
            assertEquals(i, FishTable.ALL.get(i).index(), "编号要和位置一致");
        }
    }

    @Test
    void raritySplitPerHabitat() {
        for (Habitat habitat : Habitat.values()) {
            for (Rarity rarity : Rarity.values()) {
                long count = FishTable.of(habitat).stream().filter(s -> s.rarity() == rarity).count();
                assertEquals(rarity.perHabitat(), count, habitat + " " + rarity);
            }
            // 传说的都是巨的
            assertTrue(FishTable.of(habitat).stream().filter(s -> s.rarity() == Rarity.LEGENDARY)
                    .allMatch(s -> s.size() == SizeClass.HUGE), habitat.name());
        }
    }

    @Test
    void conditionsPerHabitat() {
        for (Habitat habitat : Habitat.values()) {
            List<FishSpecies> list = FishTable.of(habitat);
            assertEquals(2, list.stream().filter(s -> s.time() == FishSpecies.Time.NIGHT).count(), habitat + " 夜里");
            assertEquals(1, list.stream().filter(s -> s.time() == FishSpecies.Time.DAY).count(), habitat + " 白天");
            assertEquals(1, list.stream().filter(s -> s.weather() == FishSpecies.Weather.STORM).count(), habitat + " 风暴");
        }
    }

    @Test
    void idsAndNamesAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> zh = new HashSet<>();
        Set<String> en = new HashSet<>();
        for (FishSpecies species : FishTable.ALL) {
            assertTrue(species.id().matches("[a-z][a-z_]*"), species.id());
            assertTrue(ids.add(species.id()), "重复的 ID " + species.id());
            assertTrue(zh.add(species.nameZh()), "重复的中文名 " + species.nameZh());
            assertTrue(en.add(species.nameEn()), "重复的英文名 " + species.nameEn());
            assertEquals(species, FishTable.byId(species.id()).orElseThrow());
        }
    }

    @Test
    void pricesFollowRarityAndSize() {
        FishSpecies cheap = FishTable.byId("creek_dace").orElseThrow();
        FishSpecies legend = FishTable.byId("megamouth").orElseThrow();
        assertEquals(20, cheap.price(), 1e-9);
        assertEquals(6000, legend.price(), 1e-9);
        // 96 种鱼、杂物、漂流瓶和补给舱，再加配方里用的鱼标签（全部的和每个档次的）
        assertEquals(96 + Junk.values().length + 2 + 1 + Rarity.values().length, FishValues.prices().size());
        assertEquals(20, FishValues.prices().get("#universe:fish"), 1e-9);
    }

    @Test
    void reelTimesMatchTheCharter() {
        // 张力一直在绿区时：小鱼五秒，传说的十五六秒（再加上猛冲退回去的，二十秒上下）
        FishSpecies small = FishTable.byId("creek_dace").orElseThrow();
        FishSpecies legend = FishTable.byId("megamouth").orElseThrow();
        assertTrue(small.reelTicks() >= 90 && small.reelTicks() <= 120, "小鱼 " + small.reelTicks());
        assertTrue(legend.reelTicks() >= 280 && legend.reelTicks() <= 400, "传说 " + legend.reelTicks());
    }
}
