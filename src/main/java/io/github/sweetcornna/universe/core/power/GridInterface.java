package io.github.sweetcornna.universe.core.power;

/**
 * 电网接口（ADR-0030 §三）：把覆盖式电网和别的模组的能量系统（NeoForge 的能量能力，FE）接起来。1 J 当 1 FE。
 *
 * <p>输入模式下它是电网里的一台发电机：别的模组灌进来的能量先存在缓冲里，电网按负荷取走。输出模式下它是一台用电器：
 * 从电网取电存进缓冲，别的模组抽走或者它自己推给相邻的设备。两边每刻都最多走 {@link #RATE}：电网那边由
 * {@link #generation()}、{@link #demand()} 限住，外面那边按游戏刻记账，{@link #insertable}、{@link #extractable} 限住。
 *
 * <p>缓冲只有两刻的量，起削峰的作用，不当蓄电器用。换模式时缓冲里的能量留着，往新的方向走。
 */
public final class GridInterface {
    /** 每刻最多走多少焦耳：和能量枢纽一样，戴森球计划的 45 MW 按本模组的比例。 */
    public static final long RATE = 2250;
    /** 缓冲：两刻的量。 */
    public static final long BUFFER = RATE * 2;

    private boolean output;
    private long buffer;
    /** 这一刻和外面走了多少（不分方向，同一时间只有一个方向）。 */
    private long external;
    /** {@link #external} 记的是哪一刻。 */
    private long externalTick = Long.MIN_VALUE;

    /** 输出模式：从电网取电给别的模组；否则是输入模式。 */
    public boolean output() {
        return this.output;
    }

    public void setOutput(boolean output) {
        this.output = output;
    }

    public long buffer() {
        return this.buffer;
    }

    public void setBuffer(long buffer) {
        this.buffer = Math.max(0, Math.min(BUFFER, buffer));
    }

    // ---- 电网那边

    /** 输入模式下这一刻最多给电网多少。 */
    public long generation() {
        return this.output ? 0 : Math.min(RATE, this.buffer);
    }

    /** 输出模式下这一刻要多少。 */
    public long demand() {
        return this.output ? Math.min(RATE, BUFFER - this.buffer) : 0;
    }

    /**
     * 电网结算的结果。
     *
     * @param generation 结算前报的发电量
     * @param demand     结算前报的需求
     * @return 这一刻和电网走了多少（给电网的或从电网拿的）
     */
    public long settle(long generation, long demand, double satisfaction, double load) {
        if (generation > 0) {
            long given = Math.min(this.buffer, Math.round(generation * clamp(load)));
            this.buffer -= given;
            return given;
        }
        if (demand > 0) {
            long taken = Math.min(BUFFER - this.buffer, Math.round(demand * clamp(satisfaction)));
            this.buffer += taken;
            return taken;
        }
        return 0;
    }

    // ---- 外面那边

    /** 这一刻外面还能灌进来多少（只有输入模式收）。 */
    public long insertable(long tick) {
        return this.output ? 0 : Math.min(BUFFER - this.buffer, this.quota(tick));
    }

    /** 这一刻外面还能抽走多少（只有输出模式给）。 */
    public long extractable(long tick) {
        return this.output ? Math.min(this.buffer, this.quota(tick)) : 0;
    }

    /** 灌进来最多 {@code amount}，返回实际收了多少。 */
    public long insert(long amount, long tick) {
        long accepted = Math.max(0, Math.min(amount, this.insertable(tick)));
        this.buffer += accepted;
        this.external += accepted;
        return accepted;
    }

    /** 抽走最多 {@code amount}，返回实际给了多少。 */
    public long extract(long amount, long tick) {
        long given = Math.max(0, Math.min(amount, this.extractable(tick)));
        this.buffer -= given;
        this.external += given;
        return given;
    }

    /** 这一刻和外面走了多少。 */
    public long external(long tick) {
        return tick == this.externalTick ? this.external : 0;
    }

    private long quota(long tick) {
        if (tick != this.externalTick) {
            this.externalTick = tick;
            this.external = 0;
        }
        return RATE - this.external;
    }

    // ---- 事务

    /** 现在的状态，事务回滚时用 {@link #restore} 恢复。 */
    public long[] snapshot() {
        return new long[] {this.buffer, this.external, this.externalTick};
    }

    public void restore(long[] snapshot) {
        this.buffer = snapshot[0];
        this.external = snapshot[1];
        this.externalTick = snapshot[2];
    }

    private static double clamp(double share) {
        return Math.max(0, Math.min(1, share));
    }
}
