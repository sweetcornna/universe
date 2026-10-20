package io.github.sweetcornna.universe.core.factory;

/**
 * 几台资源设备和研究站的产出速度。设备的方块实体和平衡模型（{@code core.balance.Progression}）都从这里取，
 * 改一处两边一起变。
 */
public final class Throughput {
    /** 采矿机：每个矿脉节点多少刻出一个矿（每个节点每分钟 1.5 个）。 */
    public static final int MINER_TICKS_PER_ITEM = 800;
    /** 抽水站：多少刻抽一个水（每分钟 50 个）。 */
    public static final int PUMP_TICKS_PER_ITEM = 24;
    /** 分馏塔：每刻最多处理几个氢（每秒 30 个）。 */
    public static final double FRACTIONATOR_PER_TICK = 1.5;
    /** 分馏塔：每个氢变成重氢的概率。 */
    public static final double FRACTIONATOR_CHANCE = 0.01;
    /** 研究站：满电时攒一点要几刻。 */
    public static final int LAB_TICKS_PER_POINT = 100;

    private Throughput() {
    }
}
