package io.github.sweetcornna.universe.core.tech;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 技术格的网格（ADR-0034 §五）。格子按行排，序号从左到右、从上到下，和界面上画的一样。
 *
 * <p>上下左右挨着的同一种模块互相加成，每挨一个 +{@value #ADJACENT}，最多 +{@value #ADJACENT_MAX}；种子定下的超频格里的模块
 * 再 ×{@value #SUPERCHARGE}，和相邻的加成相乘。这只改算加成的那一步，格子、模块和存档都还是原来的。
 *
 * @param columns     每行几格
 * @param rows        几行
 * @param supercharge 超频格的个数
 */
public record TechGrid(int columns, int rows, int supercharge) {
    /** 每挨着一个同种模块加多少。 */
    public static final double ADJACENT = 0.05;
    /** 相邻加成的上限。 */
    public static final double ADJACENT_MAX = 0.20;
    /** 超频格的倍数。 */
    public static final double SUPERCHARGE = 1.25;

    public static final TechGrid EXOSUIT = new TechGrid(4, 3, 3);
    public static final TechGrid MULTITOOL = new TechGrid(4, 2, 2);
    public static final TechGrid SHIP = new TechGrid(3, 2, 2);

    public TechGrid {
        if (columns <= 0 || rows <= 0 || supercharge < 0 || supercharge > columns * rows || columns * rows > 31) {
            throw new IllegalArgumentException(columns + "x" + rows + ", " + supercharge);
        }
    }

    public static TechGrid of(ModuleHost host) {
        return switch (host) {
            case EXOSUIT -> EXOSUIT;
            case MULTITOOL -> MULTITOOL;
            case SHIP -> SHIP;
        };
    }

    public int size() {
        return this.columns * this.rows;
    }

    /** 第 {@code index} 格上下左右的格子，按上、左、右、下的顺序。 */
    public List<Integer> neighbors(int index) {
        List<Integer> list = new ArrayList<>(4);
        int column = index % this.columns;
        int row = index / this.columns;
        if (row > 0) {
            list.add(index - this.columns);
        }
        if (column > 0) {
            list.add(index - 1);
        }
        if (column < this.columns - 1 && index + 1 < this.size()) {
            list.add(index + 1);
        }
        if (row < this.rows - 1) {
            list.add(index + this.columns);
        }
        return list;
    }

    /**
     * 种子定下的超频格，按位记（第 i 位是第 i 格）。只在前 {@code available} 格里挑：多功能工具按档次只有 4–8 格，
     * 超频格不会落在它没有的格子上。外骨骼在全部 12 格里挑，没解锁的那几格要等修好掉落舱才用得上。
     */
    public int superchargedSlots(long seed, int available) {
        int n = Math.clamp(available, 0, this.size());
        int count = Math.min(this.supercharge, n);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Rng rng = Rng.of(Seeds.derive(seed, "supercharge"));
        int mask = 0;
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(n - i);
            int picked = order[j];
            order[j] = order[i];
            order[i] = picked;
            mask |= 1 << picked;
        }
        return mask;
    }

    public static boolean supercharged(int mask, int index) {
        return index >= 0 && index < 31 && (mask >> index & 1) != 0;
    }

    /**
     * 一格的加成。
     *
     * @param types 每格装着的模块种类，按格子序号；空格、没解锁、坏了的格子写 {@code null}
     */
    public Boost boost(List<ModuleType> types, int mask, int index) {
        ModuleType type = at(types, index);
        if (type == null) {
            return Boost.NONE;
        }
        int links = 0;
        for (int neighbor : this.neighbors(index)) {
            if (at(types, neighbor) == type) {
                links++;
            }
        }
        boolean supercharged = supercharged(mask, index);
        return links == 0 && !supercharged ? Boost.NONE : new Boost(links, supercharged);
    }

    /** 挨着的同种模块，每对一项 {@code {a, b}}（{@code a < b}），界面上在这两格之间画连线。 */
    public List<int[]> links(List<ModuleType> types) {
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < this.size(); i++) {
            ModuleType type = at(types, i);
            if (type == null) {
                continue;
            }
            for (int neighbor : this.neighbors(i)) {
                if (neighbor > i && at(types, neighbor) == type) {
                    pairs.add(new int[] {i, neighbor});
                }
            }
        }
        return pairs;
    }

    /**
     * 装好的模块合起来的加成。
     *
     * @param slots 每格装着的模块，按格子序号；空格、没解锁、坏了的格子写 {@code null}
     */
    public TechBonuses bonuses(List<TechModule> slots, int mask) {
        List<ModuleType> types = types(slots);
        List<TechModule> modules = new ArrayList<>();
        List<Double> factors = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            TechModule module = slots.get(i);
            if (module != null) {
                modules.add(module);
                factors.add(i < this.size() ? this.boost(types, mask, i).factor() : 1.0);
            }
        }
        return TechBonuses.of(modules, factors);
    }

    /** 每格的模块种类，空格是 {@code null}。 */
    public static List<ModuleType> types(List<TechModule> slots) {
        List<ModuleType> types = new ArrayList<>(slots.size());
        for (TechModule module : slots) {
            types.add(module == null ? null : module.type());
        }
        return types;
    }

    private static ModuleType at(List<ModuleType> types, int index) {
        return index >= 0 && index < types.size() ? types.get(index) : null;
    }

    /**
     * 一格的加成。
     *
     * @param links        挨着几个同种模块
     * @param supercharged 是不是超频格
     */
    public record Boost(int links, boolean supercharged) {
        public static final Boost NONE = new Boost(0, false);

        /** 相邻给的比例（0.10 就是 +10%）。 */
        public double adjacency() {
            return Math.min(ADJACENT_MAX, ADJACENT * this.links);
        }

        /** 这一格的模块数值乘多少。 */
        public double factor() {
            return (1 + this.adjacency()) * (this.supercharged ? SUPERCHARGE : 1);
        }

        public boolean any() {
            return this.links > 0 || this.supercharged;
        }
    }
}
