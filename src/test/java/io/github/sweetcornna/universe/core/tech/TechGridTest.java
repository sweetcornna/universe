package io.github.sweetcornna.universe.core.tech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class TechGridTest {
    private static final TechModule FOCUS = new TechModule(ModuleType.BEAM_FOCUS, Grade.A, List.of(0.4));
    private static final TechModule SINK = new TechModule(ModuleType.HEAT_SINK, Grade.B, List.of(0.25));

    @Test
    void gridsMatchTheAdr() {
        assertEquals(12, TechGrid.EXOSUIT.size());
        assertEquals(4, TechGrid.EXOSUIT.columns());
        assertEquals(8, TechGrid.MULTITOOL.size());
        assertEquals(6, TechGrid.SHIP.size());
        assertEquals(3, TechGrid.SHIP.columns());
        assertEquals(SuitSlots.TECH_MAX, TechGrid.of(ModuleHost.EXOSUIT).size());
        assertEquals(SuitSlots.TOOL_SLOTS, TechGrid.of(ModuleHost.MULTITOOL).size());
    }

    @Test
    void neighborsAreOrthogonalAndStayInsideTheGrid() {
        // 4 × 3：0 在左上角，5 在中间，11 在右下角
        assertEquals(List.of(1, 4), TechGrid.EXOSUIT.neighbors(0));
        assertEquals(List.of(1, 4, 6, 9), TechGrid.EXOSUIT.neighbors(5));
        assertEquals(List.of(7, 10), TechGrid.EXOSUIT.neighbors(11));
        // 行尾不和下一行的行首相连
        assertFalse(TechGrid.EXOSUIT.neighbors(3).contains(4));
        assertEquals(List.of(1, 3), TechGrid.SHIP.neighbors(0));
        assertEquals(List.of(2, 4), TechGrid.SHIP.neighbors(5));
    }

    @Test
    void superchargedSlotsAreFixedBySeedAndCountMatches() {
        for (long seed = 0; seed < 200; seed++) {
            int mask = TechGrid.EXOSUIT.superchargedSlots(seed, 12);
            assertEquals(3, Integer.bitCount(mask));
            assertEquals(mask, TechGrid.EXOSUIT.superchargedSlots(seed, 12));
            assertEquals(0, mask >>> 12);
            int tool = TechGrid.MULTITOOL.superchargedSlots(seed, 5);
            assertEquals(2, Integer.bitCount(tool));
            assertEquals(0, tool >>> 5, "工具只有 5 格时超频格不落在后面");
            assertEquals(2, Integer.bitCount(TechGrid.SHIP.superchargedSlots(seed, 6)));
        }
        assertEquals(0b1, TechGrid.MULTITOOL.superchargedSlots(9, 1));
        assertEquals(0, TechGrid.MULTITOOL.superchargedSlots(9, 0));
    }

    @Test
    void superchargedSlotsSpreadOverTheWholeGrid() {
        int[] hits = new int[12];
        for (long seed = 0; seed < 1200; seed++) {
            int mask = TechGrid.EXOSUIT.superchargedSlots(seed, 12);
            for (int i = 0; i < 12; i++) {
                if (TechGrid.supercharged(mask, i)) {
                    hits[i]++;
                }
            }
        }
        // 每格大约 300 次
        for (int hit : hits) {
            assertTrue(hit > 220 && hit < 380, Arrays.toString(hits));
        }
    }

    @Test
    void adjacencyGivesFivePercentPerLinkUpToTwenty() {
        assertEquals(0.0, new TechGrid.Boost(0, false).adjacency(), 1e-9);
        assertEquals(0.10, new TechGrid.Boost(2, false).adjacency(), 1e-9);
        assertEquals(0.20, new TechGrid.Boost(4, false).adjacency(), 1e-9);
        assertEquals(0.20, new TechGrid.Boost(6, false).adjacency(), 1e-9);
        assertEquals(1.25, new TechGrid.Boost(0, true).factor(), 1e-9);
        // 相邻和超频相乘：1.10 × 1.25
        assertEquals(1.375, new TechGrid.Boost(2, true).factor(), 1e-9);
    }

    @Test
    void onlySameTypeOrthogonalNeighborsLink() {
        // 0 1 2 3
        // 4 5 6 7
        List<ModuleType> types = types(8, 0, ModuleType.BEAM_FOCUS, 1, ModuleType.BEAM_FOCUS, 5, ModuleType.BEAM_FOCUS,
                2, ModuleType.HEAT_SINK, 4, ModuleType.HEAT_SINK);
        assertEquals(new TechGrid.Boost(1, false), TechGrid.MULTITOOL.boost(types, 0, 0));
        assertEquals(new TechGrid.Boost(2, false), TechGrid.MULTITOOL.boost(types, 0, 1));
        assertEquals(new TechGrid.Boost(1, false), TechGrid.MULTITOOL.boost(types, 0, 5));
        // 散热片 2 和 4 不挨着；斜着的 4 和 1 也不算
        assertSame(TechGrid.Boost.NONE, TechGrid.MULTITOOL.boost(types, 0, 2));
        assertSame(TechGrid.Boost.NONE, TechGrid.MULTITOOL.boost(types, 0, 4));
        assertSame(TechGrid.Boost.NONE, TechGrid.MULTITOOL.boost(types, 0, 3));
        List<int[]> links = TechGrid.MULTITOOL.links(types);
        assertEquals(2, links.size());
        assertTrue(Arrays.equals(new int[] {0, 1}, links.get(0)));
        assertTrue(Arrays.equals(new int[] {1, 5}, links.get(1)));
        // 空格是超频格时没有加成，装了模块才算
        assertSame(TechGrid.Boost.NONE, TechGrid.MULTITOOL.boost(types, 1 << 3, 3));
        assertEquals(new TechGrid.Boost(0, true), TechGrid.MULTITOOL.boost(types, 1 << 2, 2));
    }

    @Test
    void bonusesMultiplyAdjacencyAndSupercharge() {
        List<TechModule> slots = new ArrayList<>(java.util.Collections.nCopies(8, (TechModule) null));
        slots.set(0, FOCUS);
        slots.set(1, FOCUS);
        slots.set(6, SINK);
        // 没有超频：两个光束聚焦各 +5%
        TechBonuses plain = TechGrid.MULTITOOL.bonuses(slots, 0);
        assertEquals(0.4 * 1.05 * 2, plain.get(Stat.MINING), 1e-9);
        assertEquals(0.25, plain.get(Stat.COOLING), 1e-9);
        // 第 1 格和第 6 格超频
        TechBonuses charged = TechGrid.MULTITOOL.bonuses(slots, 1 << 1 | 1 << 6);
        assertEquals(0.4 * 1.05 + 0.4 * 1.05 * 1.25, charged.get(Stat.MINING), 1e-9);
        assertEquals(0.25 * 1.25, charged.get(Stat.COOLING), 1e-9);
        // 分开放就没有相邻
        slots.set(1, null);
        slots.set(3, FOCUS);
        assertEquals(0.8, TechGrid.MULTITOOL.bonuses(slots, 0).get(Stat.MINING), 1e-9);
    }

    @Test
    void withoutBoostsTheResultIsTheOldOne() {
        List<TechModule> slots = List.of(FOCUS, SINK, FOCUS, SINK);
        assertEquals(TechBonuses.of(List.of(FOCUS, SINK, FOCUS, SINK)).totals(), TechGrid.MULTITOOL.bonuses(slots, 0).totals());
    }

    @Test
    void bestThreeAreChosenAfterBoosting() {
        TechModule good = new TechModule(ModuleType.BEAM_FOCUS, Grade.S, List.of(0.60));
        TechModule fair = new TechModule(ModuleType.BEAM_FOCUS, Grade.S, List.of(0.52));
        // 四个同种：不加成时最差的是 0.52 那个；它在超频格里 ×1.25 = 0.65，比别的都好，挤掉一个 0.60
        List<TechModule> modules = List.of(good, good, good, fair);
        assertEquals(1.80, TechBonuses.of(modules, List.of(1.0, 1.0, 1.0, 1.0)).get(Stat.MINING), 1e-9);
        assertEquals(0.60 * 2 + 0.52 * 1.25, TechBonuses.of(modules, List.of(1.0, 1.0, 1.0, 1.25)).get(Stat.MINING), 1e-9);
        // 两项的模块按两项折算以后比
        TechModule jets = new TechModule(ModuleType.JET_BOOSTER, Grade.A, List.of(0.40, 0.20));
        TechModule thrust = new TechModule(ModuleType.JET_BOOSTER, Grade.A, List.of(0.30, 0.22));
        assertTrue(TechBonuses.score(jets, 1) > TechBonuses.score(thrust, 1));
    }

    /** 按 (格子, 种类) 成对给出的种类表。 */
    private static List<ModuleType> types(int size, Object... pairs) {
        List<ModuleType> list = new ArrayList<>(java.util.Collections.nCopies(size, (ModuleType) null));
        for (int i = 0; i < pairs.length; i += 2) {
            list.set((Integer) pairs[i], (ModuleType) pairs[i + 1]);
        }
        return list;
    }
}
