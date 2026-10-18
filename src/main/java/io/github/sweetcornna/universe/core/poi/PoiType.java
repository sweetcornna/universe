package io.github.sweetcornna.universe.core.poi;

import java.util.Locale;

/**
 * 兴趣点的种类（ADR-0014）。
 *
 * <p>{@code radius} 是建筑占地的半径（格），{@code slope} 是这个范围里地面最多能差几格，
 * 选址时按这两个数挑平地；{@code weight} 是在一个格子里出现的相对权重。
 */
public enum PoiType {
    CRASHED_SHIP("坠毁飞船", "Crashed Ship", 0xFF8A5C, 13, 5, 12),
    DROP_POD("掉落舱", "Drop Pod", 0x6EDCF5, 4, 3, 22),
    OUTPOST("废弃据点", "Abandoned Outpost", 0xF2D06B, 8, 3, 16),
    RUINS("先驱者遗迹", "Precursor Ruins", 0xC79BFF, 10, 5, 14),
    BURIED_TECH("埋藏的科技", "Buried Technology", 0x7CF2B0, 3, 4, 22),
    SIGNAL_BOOSTER("信号增强器", "Signal Booster", 0xFF6E9A, 4, 3, 14);

    private final String nameZh;
    private final String nameEn;
    private final int color;
    private final int radius;
    private final int slope;
    private final int weight;

    PoiType(String nameZh, String nameEn, int color, int radius, int slope, int weight) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.color = color;
        this.radius = radius;
        this.slope = slope;
        this.weight = weight;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 扫描标记和航点的颜色（RGB）。 */
    public int color() {
        return this.color;
    }

    public int radius() {
        return this.radius;
    }

    public int slope() {
        return this.slope;
    }

    public int weight() {
        return this.weight;
    }

    /** 存档、网络、语言键里用的名称。 */
    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static PoiType byName(String name) {
        for (PoiType type : values()) {
            if (type.serializedName().equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的兴趣点种类：" + name);
    }
}
