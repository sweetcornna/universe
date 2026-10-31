package io.github.sweetcornna.universe.core.microbe;

/**
 * 生物反应器里的一罐培养物（ADR-0036 §五）。密度 0–1 按 logistic 曲线长：接种后先慢、再快、到满罐停下，
 * 生长速度 μ = ln2 ÷ 翻倍时间 × 温度系数（基数温度模型）× 代谢负担。产量和密度成正比。
 *
 * <p>活的要一直养着：断料 {@link #STARVE_GRACE_TICKS} 以后开始死，半衰期 {@link #STARVE_HALF_LIFE_TICKS}；
 * 冷过最低温度不长，和断料一样算；热过最高温度死得快（巴氏消毒 72 °C 十几秒就杀死绝大部分细菌）。
 * 密度掉到 {@link #LOST_BELOW} 以下培养就没了，要重新接种。
 *
 * <p>死的速度比现实快：按 {@link BioTime} 换算，宽限期是现实的 20 小时、半衰期 10 小时，现实里断了营养的菌
 * 大多能撑几天到几周。这里收紧，让反应器断料有代价（ADR-0036 收住的地方）。
 *
 * <p>只存两个数（密度、断了多久的料），适配层存进方块实体。
 */
public final class Culture {
    /** 接种时的密度。 */
    public static final double INOCULUM = 0.02;
    /** 密度低于这个就算死光了。 */
    public static final double LOST_BELOW = 0.01;
    /** 断料多久以后开始死（2 分钟）。 */
    public static final int STARVE_GRACE_TICKS = 2400;
    /** 饿着的半衰期（1 分钟）。 */
    public static final int STARVE_HALF_LIFE_TICKS = 1200;
    /** 热过最高温度的半衰期（15 秒，现实的 2.5 小时）。 */
    public static final int HOSTILE_HALF_LIFE_TICKS = 300;

    /** 这一步之后的状态。 */
    public enum State {
        /** 有料、温度对，在长（或者满罐了停着）。 */
        GROWING,
        /** 断了料或者太冷，不长，还没开始死。 */
        STARVING,
        /** 在死：饿太久了、太冷太久了，或者太热。 */
        DYING,
        /** 死光了。 */
        LOST
    }

    private double density;
    private int starvedTicks;
    private State state;

    public Culture(double density, int starvedTicks) {
        this.density = density < LOST_BELOW ? 0 : Math.min(1, density);
        this.starvedTicks = Math.max(0, starvedTicks);
        this.state = this.density <= 0 ? State.LOST : State.GROWING;
    }

    /** 空罐。 */
    public static Culture empty() {
        return new Culture(0, 0);
    }

    /** 刚接种的一罐。 */
    public static Culture inoculated() {
        return new Culture(INOCULUM, 0);
    }

    public double density() {
        return this.density;
    }

    public int starvedTicks() {
        return this.starvedTicks;
    }

    public State state() {
        return this.state;
    }

    public boolean alive() {
        return this.density > 0;
    }

    /**
     * 过 {@code ticks} 刻。
     *
     * @param fed          这段时间里有没有料
     * @param temperatureK 罐里的温度（K）
     */
    public State step(Organism organism, int ticks, boolean fed, double temperatureK) {
        if (!this.alive() || ticks <= 0) {
            this.state = this.alive() ? this.state : State.LOST;
            return this.state;
        }
        Tolerance tolerance = organism.tolerance();
        double factor = tolerance.growthFactor(temperatureK);
        if (tolerance.scalding(temperatureK)) {
            this.decay(ticks, HOSTILE_HALF_LIFE_TICKS);
            this.state = State.DYING;
        } else if (!fed || factor <= 0) {
            int before = this.starvedTicks;
            this.starvedTicks += ticks;
            int dying = this.starvedTicks - Math.max(before, STARVE_GRACE_TICKS);
            if (dying > 0) {
                this.decay(dying, STARVE_HALF_LIFE_TICKS);
                this.state = State.DYING;
            } else {
                this.state = State.STARVING;
            }
        } else {
            this.starvedTicks = 0;
            double rate = Math.log(2) / organism.doublingTicks() * factor;
            // 有料、不冷不热：logistic 方程的解析解，一步走多少刻都准
            this.density = 1 / (1 + (1 / this.density - 1) * Math.exp(-rate * ticks));
            this.state = State.GROWING;
        }
        if (this.density < LOST_BELOW) {
            this.density = 0;
            this.state = State.LOST;
        }
        return this.state;
    }

    private void decay(int ticks, int halfLife) {
        this.density *= Math.pow(0.5, (double) ticks / halfLife);
    }

    /** 产出的速度：密度乘产率。生物反应器按它推进配方。 */
    public double outputRate(Organism organism) {
        return this.density * organism.productivity();
    }
}
