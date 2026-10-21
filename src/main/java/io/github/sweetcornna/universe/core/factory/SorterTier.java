package io.github.sweetcornna.universe.core.factory;

/**
 * 分拣器的几档（ADR-0015）：每秒来回 1.5、3、6 次，功率是戴森球计划的 18、36、72 kW 除以 20（向上取整）。
 * 集装分拣器（ADR-0022）每秒 12 次、120 kW，一次从容器里拿一叠，叠好放上传送带。
 */
public enum SorterTier {
    MK1(1.5, 1, 1),
    MK2(3, 2, 1),
    MK3(6, 4, 1),
    PILE(12, 6, Cargo.MAX_STACK);

    private final double tripsPerSecond;
    private final long power;
    private final int stack;

    SorterTier(double tripsPerSecond, long power, int stack) {
        this.tripsPerSecond = tripsPerSecond;
        this.power = power;
        this.stack = stack;
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

    /** 从容器里一次最多拿几个：集装分拣器一叠（还要看"集装物流"研究到第几级），别的一个。 */
    public int stack() {
        return this.stack;
    }

    /** 工作时的功率（焦耳/刻）。 */
    public long power() {
        return this.power;
    }
}
