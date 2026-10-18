package io.github.sweetcornna.universe.core.sentinel;

/**
 * 一个玩家在一颗星球上的警戒值和通缉级别（ADR-0014）。
 *
 * <ul>
 *   <li>违规（{@link Offense}）提高警戒值；到 30、60、90 分别是一、二、三级通缉。</li>
 *   <li>没被通缉时，10 秒不再违规，警戒值每秒降 1。</li>
 *   <li>被通缉后级别只升不降：躲出所有敌对守卫机的视线 20 秒才算脱离，警戒值和级别一起清零；
 *       离开星球或者死亡也清零（由适配层调 {@link #clear()}）。</li>
 * </ul>
 */
public final class Wanted {
    public static final double[] THRESHOLDS = {30, 60, 90};
    public static final double MAX = 120;
    public static final int MAX_LEVEL = 3;
    /** 不再违规多少刻后警戒值开始下降。 */
    static final int GRACE = 200;
    static final double DECAY_PER_TICK = 0.05;
    /** 躲出视线多少刻算脱离。 */
    public static final int ESCAPE_TICKS = 400;

    private double alert;
    private int level;
    private int sinceOffense = GRACE;
    private int hidden;

    public Wanted() {
    }

    public Wanted(double alert, int level) {
        this.alert = Math.clamp(alert, 0, MAX);
        this.level = Math.clamp(level, 0, MAX_LEVEL);
    }

    /** 警戒值对应的通缉级别。 */
    public static int levelFor(double alert) {
        int level = 0;
        while (level < THRESHOLDS.length && alert >= THRESHOLDS[level]) {
            level++;
        }
        return level;
    }

    /**
     * 违规一次。
     *
     * @param factor 星球守卫强度的倍数（{@link SentinelLevel#alertFactor()}）
     * @return 通缉级别有没有升高
     */
    public boolean offend(Offense offense, double factor) {
        if (factor <= 0) {
            return false;
        }
        int before = this.level;
        if (offense.provokes()) {
            this.alert = Math.max(this.alert, THRESHOLDS[0]);
        }
        this.alert = Math.min(MAX, this.alert + offense.alert() * factor);
        this.sinceOffense = 0;
        this.level = Math.max(this.level, levelFor(this.alert));
        if (this.level > 0) {
            this.hidden = 0;
        }
        return this.level > before;
    }

    /**
     * 推进一刻。
     *
     * @param seen 这一刻有敌对的守卫机看得见玩家
     * @return 这一刻脱离了通缉
     */
    public boolean tick(boolean seen) {
        boolean idle = this.sinceOffense >= GRACE;
        if (!idle) {
            this.sinceOffense++;
        }
        if (this.level == 0) {
            if (idle) {
                this.alert = Math.max(0, this.alert - DECAY_PER_TICK);
            }
            return false;
        }
        if (seen) {
            this.hidden = 0;
            return false;
        }
        this.hidden++;
        if (this.hidden >= ESCAPE_TICKS) {
            this.clear();
            return true;
        }
        return false;
    }

    public void clear() {
        this.alert = 0;
        this.level = 0;
        this.hidden = 0;
        this.sinceOffense = GRACE;
    }

    public double alert() {
        return this.alert;
    }

    public int level() {
        return this.level;
    }

    public boolean wanted() {
        return this.level > 0;
    }

    /** 脱离的进度 0–1（躲出视线的时间占 20 秒的比例）。 */
    public float escapeProgress() {
        return this.level == 0 ? 0 : (float) this.hidden / ESCAPE_TICKS;
    }

    /** 还要躲多少刻。 */
    public int escapeRemaining() {
        return ESCAPE_TICKS - this.hidden;
    }

    /** 被通缉时要有几架普通守卫机、几架重型守卫机追捕（警觉的星球多一架普通的）。 */
    public static int drones(int level, SentinelLevel planet) {
        if (level <= 0) {
            return 0;
        }
        int base = level == 1 ? 2 : 4;
        return base + (planet == SentinelLevel.AGGRESSIVE ? 1 : 0);
    }

    public static int heavies(int level) {
        return level >= MAX_LEVEL ? 1 : 0;
    }
}
