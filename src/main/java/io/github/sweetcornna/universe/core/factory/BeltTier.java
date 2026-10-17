package io.github.sweetcornna.universe.core.factory;

/**
 * 传送带的三档速度（玩法设计 §9.3）：每秒 6、12、30 个物品。
 *
 * <p>物品之间最少隔 {@link BeltLine#SPACING} 个单位，所以满载时每秒过去的物品数 = 速度 × 20 / 间距。
 */
public enum BeltTier {
    MK1(12),
    MK2(24),
    MK3(60);

    private final int speed;

    BeltTier(int speed) {
        this.speed = speed;
    }

    /** 每刻前进多少个单位（一格 {@link BeltLine#UNITS_PER_BLOCK} 个单位）。 */
    public int speed() {
        return this.speed;
    }

    /** 满载时每秒运送的物品数。 */
    public double itemsPerSecond() {
        return this.speed * 20.0 / BeltLine.SPACING;
    }

    /** 每秒前进多少格。 */
    public double blocksPerSecond() {
        return this.speed * 20.0 / BeltLine.UNITS_PER_BLOCK;
    }
}
