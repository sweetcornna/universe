package io.github.sweetcornna.universe.core.research;

import java.util.List;

/**
 * 一个科技（玩法设计 §9.5）。前期的科技直接上交物品；之后的要矩阵研究站消耗矩阵，每完成一"点"消耗每种所需矩阵
 * 各一个，攒够 {@link #points()} 点就研究完成。
 *
 * @param id       科技 ID，语言键是 {@code tech.universe.<id>}
 * @param requires 前置科技
 * @param handIn   上交的物品（物品 ID 和数量）；不为空时这是上交物品的科技
 * @param matrices 研究站每点消耗的矩阵（物品 ID）
 * @param points   要攒的点数
 * @param unlocks  解锁的东西（物品 ID），只给科技树界面显示，真正的门控写在配方里
 */
public record Tech(String id, List<String> requires, List<ItemCost> handIn, List<String> matrices, int points, List<String> unlocks) {
    public Tech {
        requires = List.copyOf(requires);
        handIn = List.copyOf(handIn);
        matrices = List.copyOf(matrices);
        unlocks = List.copyOf(unlocks);
        if (handIn.isEmpty() == matrices.isEmpty()) {
            throw new IllegalArgumentException(id + ": a tech either takes items or matrices");
        }
        if (!matrices.isEmpty() && points <= 0) {
            throw new IllegalArgumentException(id + ": lab tech needs points");
        }
    }

    /** 上交物品就完成的科技。 */
    public static Tech handIn(String id, List<String> requires, List<ItemCost> cost, List<String> unlocks) {
        return new Tech(id, requires, cost, List.of(), 0, unlocks);
    }

    /** 要研究站研究的科技。 */
    public static Tech lab(String id, List<String> requires, List<String> matrices, int points, List<String> unlocks) {
        return new Tech(id, requires, List.of(), matrices, points, unlocks);
    }

    public boolean isHandIn() {
        return !this.handIn.isEmpty();
    }

    /** 上交或消耗的一种物品。 */
    public record ItemCost(String item, int count) {
        public ItemCost {
            if (count <= 0) {
                throw new IllegalArgumentException(item + " x" + count);
            }
        }
    }
}
