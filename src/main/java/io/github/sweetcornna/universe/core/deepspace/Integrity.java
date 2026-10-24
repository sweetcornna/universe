package io.github.sweetcornna.universe.core.deepspace;

/**
 * 一艘残骸的结构完整度（ADR-0034 §四）：从 100 开始，拆一块按部件扣（{@link Part#cost()}）。低于 30 报警；
 * 降到 0 或者反应堆芯被拆下来，20 秒后熔毁，残骸剩下的部分炸掉，24 格内的人受重伤。拿到堆芯算拆完。
 */
public final class Integrity {
    public static final int FULL = 100;
    /** 低于这个数开始报警（红灯、警报声）。 */
    public static final int ALARM = 30;
    /** 开始熔毁到炸开（刻）。 */
    public static final int MELTDOWN_TICKS = 20 * 20;
    /** 爆炸波及的半径（格）。 */
    public static final double BLAST_RADIUS = 24;
    /** 爆心的伤害；往外线性减到 {@link #BLAST_EDGE}。 */
    public static final double BLAST_DAMAGE = 40;
    public static final double BLAST_EDGE = 6;

    /** 残骸处在哪一步。 */
    public enum Phase {
        STABLE,
        ALARM,
        MELTDOWN,
        /** 炸过了。 */
        GONE
    }

    private int value;
    /** 熔毁倒计时（刻）；没在熔毁时是 −1。 */
    private int countdown;
    private boolean salvaged;
    private boolean gone;

    public Integrity() {
        this(FULL, -1, false, false);
    }

    public Integrity(int value, int countdown, boolean salvaged, boolean gone) {
        this.value = Math.clamp(value, 0, FULL);
        this.countdown = countdown;
        this.salvaged = salvaged;
        this.gone = gone;
    }

    public int value() {
        return this.value;
    }

    public int countdown() {
        return this.countdown;
    }

    /** 拿到了反应堆芯（拆完了）。 */
    public boolean salvaged() {
        return this.salvaged;
    }

    public boolean gone() {
        return this.gone;
    }

    public Phase phase() {
        if (this.gone) {
            return Phase.GONE;
        }
        if (this.countdown >= 0) {
            return Phase.MELTDOWN;
        }
        return this.value < ALARM ? Phase.ALARM : Phase.STABLE;
    }

    /**
     * 拆掉一块：扣完整度；堆芯被拆、或者扣到 0，开始熔毁倒计时（已经在倒计时就不重来）。
     *
     * @return 这一下让残骸进了哪一步（没变就是原来那一步）
     */
    public Phase remove(Part part) {
        if (this.gone) {
            return Phase.GONE;
        }
        this.value = Math.max(0, this.value - part.cost());
        if (part == Part.REACTOR) {
            this.salvaged = true;
        }
        if (this.countdown < 0 && (this.value == 0 || part == Part.REACTOR)) {
            this.countdown = MELTDOWN_TICKS;
        }
        return this.phase();
    }

    /**
     * 过一刻。
     *
     * @return 这一刻该炸了
     */
    public boolean tick() {
        if (this.gone || this.countdown < 0) {
            return false;
        }
        if (--this.countdown <= 0) {
            this.countdown = -1;
            this.gone = true;
            return true;
        }
        return false;
    }

    /** 离爆心 {@code distance} 格的伤害；超出半径是 0。 */
    public static double blastDamage(double distance) {
        if (distance > BLAST_RADIUS) {
            return 0;
        }
        return BLAST_DAMAGE - (BLAST_DAMAGE - BLAST_EDGE) * Math.max(0, distance) / BLAST_RADIUS;
    }

    /** 报警的警报声多久响一次（刻）：越接近熔毁越急。 */
    public int sirenInterval() {
        if (this.countdown >= 0) {
            return this.countdown < 100 ? 10 : 20;
        }
        return 40;
    }
}
