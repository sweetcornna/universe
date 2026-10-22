package io.github.sweetcornna.universe.core.sentinel;

/**
 * 一个玩家在一颗星球上的警戒值和通缉级别（ADR-0014，ADR-0026 改成五级）。
 *
 * <ul>
 *   <li>违规（{@link Offense}）提高警戒值；到 30 开始一级通缉。</li>
 *   <li>没被通缉时，10 秒不再违规，警戒值每秒降 1。</li>
 *   <li>被通缉以后再违规不加级，<b>打光当前这一波（{@link #waveCleared()}）才升一级</b>；严重的违规直接跳到某一级
 *       （{@link Offense#jumpTo()}）。打光五级那一波，通缉解除，这颗星球的守卫机撤走。</li>
 *   <li>级别只升不降：躲出所有敌对守卫机的视线 20 + 10 × 级别 秒才算脱离，警戒值和级别一起清零；
 *       离开星球或者死亡也清零（由适配层调 {@link #clear()}）。</li>
 * </ul>
 */
public final class Wanted {
    /** 警戒值到这里开始通缉。 */
    public static final double THRESHOLD = 30;
    public static final double MAX = 120;
    public static final int MAX_LEVEL = 5;
    /** 不再违规多少刻后警戒值开始下降。 */
    static final int GRACE = 200;
    static final double DECAY_PER_TICK = 0.05;
    /** 躲出视线多少刻算脱离：20 秒，每级多 10 秒。 */
    static final int ESCAPE_BASE = 400;
    static final int ESCAPE_PER_LEVEL = 200;

    /** 打光一波的结果。 */
    public enum Outcome {
        /** 没被通缉，没有波次可打。 */
        NONE,
        /** 升了一级，下一波要来了。 */
        ESCALATED,
        /** 打光了五级，通缉解除，守卫机撤走。 */
        WITHDRAWN
    }

    private double alert;
    private int level;
    private int sinceOffense = GRACE;
    private int hidden;

    public Wanted() {
    }

    /** 从存档恢复；旧存档里的 1–3 级照样有效。 */
    public Wanted(double alert, int level) {
        this.alert = Math.clamp(alert, 0, MAX);
        this.level = Math.clamp(level, 0, MAX_LEVEL);
    }

    /** 没被通缉时，警戒值对应的通缉级别：到门槛是一级，否则是零。 */
    public static int levelFor(double alert) {
        return alert >= THRESHOLD ? 1 : 0;
    }

    /** 这一级要躲多少刻才能脱离。 */
    public static int escapeTicks(int level) {
        return ESCAPE_BASE + ESCAPE_PER_LEVEL * Math.clamp(level, 1, MAX_LEVEL);
    }

    /**
     * 违规一次。没被通缉时加警戒值，到门槛开始一级通缉；被通缉以后只有跳级的违规能升级。
     *
     * @param factor 星球守卫强度的倍数（{@link SentinelLevel#alertFactor()}）
     * @return 通缉级别有没有升高
     */
    public boolean offend(Offense offense, double factor) {
        if (factor <= 0) {
            return false;
        }
        int before = this.level;
        int jump = Math.min(offense.jumpTo(), MAX_LEVEL);
        if (jump > 0) {
            this.alert = Math.max(this.alert, THRESHOLD);
        }
        this.alert = Math.min(MAX, this.alert + offense.alert() * factor);
        this.sinceOffense = 0;
        if (this.level == 0) {
            this.level = levelFor(this.alert);
        }
        this.level = Math.max(this.level, jump);
        if (this.level > 0) {
            this.hidden = 0;
        }
        return this.level > before;
    }

    /** 打光了当前这一波：升一级；五级打光就解除通缉。 */
    public Outcome waveCleared() {
        if (this.level == 0) {
            return Outcome.NONE;
        }
        if (this.level >= MAX_LEVEL) {
            this.clear();
            return Outcome.WITHDRAWN;
        }
        this.level++;
        this.hidden = 0;
        return Outcome.ESCALATED;
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
        if (this.hidden >= escapeTicks(this.level)) {
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

    /** 脱离的进度 0–1（躲出视线的时间占这一级要躲的时间的比例）。 */
    public float escapeProgress() {
        return this.level == 0 ? 0 : (float) this.hidden / escapeTicks(this.level);
    }

    /** 还要躲多少刻。 */
    public int escapeRemaining() {
        return this.level == 0 ? 0 : escapeTicks(this.level) - this.hidden;
    }
}
