package io.github.sweetcornna.universe.core.tech;

import java.util.List;

import io.github.sweetcornna.universe.core.research.Tech.ItemCost;

/**
 * 外骨骼的格子（ADR-0014）：技术槽开局 4 个、最多 12 个；货舱开局 9 格、最多 54 格。每修好一个掉落舱，技术槽加 1、
 * 货舱加 9，到上限为止。修理材料按已经修过的个数分三档。多功能工具的技术槽固定 6 个。
 */
public final class SuitSlots {
    public static final int TECH_START = 4;
    public static final int TECH_MAX = 12;
    public static final int CARGO_START = 9;
    public static final int CARGO_MAX = 54;
    public static final int CARGO_PER_POD = 9;
    public static final int TOOL_SLOTS = 6;

    private SuitSlots() {
    }

    public static int tech(int podsRepaired) {
        return Math.min(TECH_MAX, TECH_START + Math.max(0, podsRepaired));
    }

    public static int cargo(int podsRepaired) {
        return Math.min(CARGO_MAX, CARGO_START + CARGO_PER_POD * Math.max(0, podsRepaired));
    }

    /** 再修一个还有没有用（两样都到上限后掉落舱只剩材料）。 */
    public static boolean canGrow(int podsRepaired) {
        return tech(podsRepaired) < TECH_MAX || cargo(podsRepaired) < CARGO_MAX;
    }

    /** 修第 {@code podsRepaired + 1} 个掉落舱要的材料。 */
    public static List<ItemCost> podCost(int podsRepaired) {
        if (podsRepaired < 3) {
            return List.of(new ItemCost("minecraft:iron_ingot", 2), new ItemCost("universe:circuit_board", 1));
        }
        if (podsRepaired < 6) {
            return List.of(new ItemCost("universe:titanium_ingot", 1), new ItemCost("universe:circuit_board", 2),
                    new ItemCost("universe:magnetic_coil", 1));
        }
        return List.of(new ItemCost("universe:titanium_ingot", 2), new ItemCost("universe:plasma_exciter", 1));
    }
}
