package io.github.sweetcornna.universe.core.poi;

/**
 * 地标的种类（ADR-0026）。地标是兴趣点之外的另一层：每种有自己的网格、概率和派生键 {@code landmark/<种类>}，
 * 单独判定放不放，以后再加一种也不动已有的。只往后加。
 *
 * @param grid    网格边长（格），每格最多一个
 * @param chance  一格里出这种地标的概率
 * @param radius  占地半径（格），建筑不超过中心 15 格
 * @param slope   占地范围里的高低差最多多少格
 * @param seabed  放在海底：比海平面低 {@link LandmarkLayout#SEABED_DEPTH} 格以上
 * @param color   扫描、声呐标记的颜色
 */
public enum LandmarkType {
    /** 沉船：海底一艘飞船设计器造的破船，船里的货箱有东西。 */
    SHIPWRECK("shipwreck", 900, 0.5, 12, 8, true, 0x6FC8E8),
    /** 海底遗迹：半埋在海底的建筑，里面 3 个海底密封箱。 */
    SEABED_RUIN("seabed_ruin", 1100, 0.4, 10, 6, true, 0xA08CFF);

    private final String id;
    private final int grid;
    private final double chance;
    private final int radius;
    private final int slope;
    private final boolean seabed;
    private final int color;

    LandmarkType(String id, int grid, double chance, int radius, int slope, boolean seabed, int color) {
        this.id = id;
        this.grid = grid;
        this.chance = chance;
        this.radius = radius;
        this.slope = slope;
        this.seabed = seabed;
        this.color = color;
    }

    public String id() {
        return this.id;
    }

    public int grid() {
        return this.grid;
    }

    public double chance() {
        return this.chance;
    }

    public int radius() {
        return this.radius;
    }

    public int slope() {
        return this.slope;
    }

    public boolean seabed() {
        return this.seabed;
    }

    public int color() {
        return this.color;
    }

    /** 派生键。 */
    public String seedKey() {
        return "landmark/" + this.id;
    }
}
