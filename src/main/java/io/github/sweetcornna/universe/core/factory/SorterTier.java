package io.github.sweetcornna.universe.core.factory;

/**
 * 分拣器的三档（ADR-0015）：每秒来回 1.5、3、6 次，功率是戴森球计划的 18、36、72 kW 除以 20（向上取整）。
 */
public enum SorterTier {
    MK1(1.5, 1),
    MK2(3, 2),
    MK3(6, 4);

    private final double tripsPerSecond;
    private final long power;

    SorterTier(double tripsPerSecond, long power) {
        this.tripsPerSecond = tripsPerSecond;
        this.power = power;
    }

    /** 每秒来回几次。 */
    public double tripsPerSecond() {
        return this.tripsPerSecond;
    }

    /** 一次来回的刻数。 */
    public double cycle() {
        return 20 / this.tripsPerSecond;
    }

    /** 抓着东西转到前面要几刻（一次来回的一半）。 */
    public double halfCycle() {
        return this.cycle() / 2;
    }

    /** 工作时的功率（焦耳/刻）。 */
    public long power() {
        return this.power;
    }
}
