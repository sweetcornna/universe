package io.github.sweetcornna.universe.core.tech;

/**
 * 模块提供的一项加成（ADR-0014）。每个品级有一个取值范围，模块掉落时在范围里随机定下数值。
 *
 * <p>大多数是比例（0.25 表示 25%），护盾是绝对值（生命值点数）。"降低"类的加成（氧气、危害、热量）在使用时按
 * {@code 消耗 / (1 + 加成)} 算，叠得再多也不会降到零；冷却和分析时间按 {@code 1 - 加成} 算，有下限。
 */
public enum Stat {
    OXYGEN(false, 0.10, 0.20, 0.35, 0.50, 0.70),
    COLD(false, 0.15, 0.25, 0.40, 0.60, 0.85),
    HEAT(false, 0.15, 0.25, 0.40, 0.60, 0.85),
    TOXIC(false, 0.15, 0.25, 0.40, 0.60, 0.85),
    RADIATION(false, 0.15, 0.25, 0.40, 0.60, 0.85),
    JET_TIME(false, 0.10, 0.20, 0.30, 0.45, 0.60),
    JET_THRUST(false, 0.05, 0.10, 0.15, 0.22, 0.30),
    SPEED(false, 0.03, 0.05, 0.08, 0.11, 0.15),
    SHIELD(true, 2, 3, 5, 7, 10),
    MINING(false, 0.10, 0.20, 0.35, 0.50, 0.70),
    COOLING(false, 0.10, 0.20, 0.35, 0.50, 0.70),
    SCAN_RADIUS(false, 0.15, 0.25, 0.40, 0.60, 0.80),
    SCAN_COOLDOWN(false, 0.05, 0.10, 0.20, 0.30, 0.40),
    ANALYSIS(false, 0.10, 0.20, 0.30, 0.40, 0.50),
    DAMAGE(false, 0.10, 0.20, 0.35, 0.50, 0.70),
    FIRE_RATE(false, 0.05, 0.10, 0.18, 0.26, 0.35),
    // ---- 飞船（ADR-0024）
    /** 燃料消耗降低。 */
    FUEL_USE(false, 0.05, 0.10, 0.18, 0.28, 0.40),
    /** 脉冲引擎的最高速度。 */
    PULSE_SPEED(false, 0.04, 0.08, 0.13, 0.19, 0.26),
    /** 跃迁航程（光年，绝对值）。 */
    WARP_RANGE(true, 1, 2, 4, 6, 9),
    /** 飞船护盾。 */
    SHIP_SHIELD(false, 0.08, 0.15, 0.25, 0.38, 0.55),
    /** 飞船船体。 */
    HULL(false, 0.06, 0.12, 0.20, 0.30, 0.42);

    private final boolean absolute;
    /** 五个分界点：C 是 [0, 1]，B 是 [1, 2]，A 是 [2, 3]，S 是 [3, 4]。 */
    private final double[] bounds;

    Stat(boolean absolute, double... bounds) {
        this.absolute = absolute;
        this.bounds = bounds;
    }

    /** 是不是绝对值（护盾点数）；否则是比例。 */
    public boolean absolute() {
        return this.absolute;
    }

    public double min(Grade grade) {
        return this.bounds[grade.ordinal()];
    }

    public double max(Grade grade) {
        return this.bounds[grade.ordinal() + 1];
    }
}
