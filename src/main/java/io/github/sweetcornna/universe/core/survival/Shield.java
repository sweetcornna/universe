package io.github.sweetcornna.universe.core.survival;

/**
 * 外骨骼的个人护盾（ADR-0014），也就是机甲的能量护盾（ADR-0033 §二）：先替玩家挡下战斗伤害；5 秒没受伤后开始回充。
 *
 * <p>容量分两部分：护盾模块加的（自己回充，3 秒充满这一部分）和升级"能量护盾"加的。护盾不满时机甲核心有电就再用电回充
 * （{@link #recharge}，每点 {@link #CORE_JOULES_PER_POINT} 焦，每秒最多 {@link #CORE_POINTS_PER_SECOND} 点），
 * 被打以后一样等 5 秒。
 */
public final class Shield {
    public static final int RECHARGE_DELAY = 100;
    static final float RECHARGE_TICKS = 60;
    /** 核心回充一点护盾要的电（焦）。 */
    public static final double CORE_JOULES_PER_POINT = 200;
    /** 核心每秒最多回充几点。 */
    public static final float CORE_POINTS_PER_SECOND = 4;

    private float value;
    private float capacity;
    private int sinceHit = RECHARGE_DELAY;

    /** 推进一刻，容量全是模块加的。 */
    public void tick(float capacity) {
        this.tick(capacity, 0);
    }

    /**
     * 推进一刻。容量变小时当前值跟着截断；刚装上护盾模块时从零开始充。
     *
     * @param modules 护盾模块加的容量：这一部分自己回充
     * @param extra   别的来源加的容量（升级"能量护盾"）：只靠 {@link #recharge} 用电回充
     */
    public void tick(float modules, float extra) {
        float own = Math.max(0, modules);
        this.capacity = own + Math.max(0, extra);
        this.value = Math.min(this.value, this.capacity);
        if (this.sinceHit < RECHARGE_DELAY) {
            this.sinceHit++;
        } else if (this.value < own) {
            // 自己回充只回模块加的那部分；升级加的靠核心的电
            this.value = Math.min(own, this.value + own / RECHARGE_TICKS);
        }
    }

    /** 正在回充的时候：没满，离上次挨打够 5 秒了。 */
    public boolean recharging() {
        return this.sinceHit >= RECHARGE_DELAY && this.value < this.capacity;
    }

    /** 还差几点满。 */
    public float missing() {
        return Math.max(0, this.capacity - this.value);
    }

    /**
     * 用别的能源回充（机甲核心）：只在 {@link #recharging} 时回，最多 {@code points} 点，不超过容量。
     *
     * @return 回了几点
     */
    public float recharge(float points) {
        if (!this.recharging() || points <= 0) {
            return 0;
        }
        float added = Math.min(points, this.missing());
        this.value += added;
        return added;
    }

    /** 这一刻核心最多回几点：每秒的上限分到每刻，也不超过核心里的电够回的（{@code energy} 焦）。 */
    public static float corePoints(double energy) {
        return (float) Math.max(0, Math.min(CORE_POINTS_PER_SECOND / 20.0, energy / CORE_JOULES_PER_POINT));
    }

    /**
     * 挡一次伤害。
     *
     * @return 护盾挡不住、还要落到玩家身上的部分
     */
    public float absorb(float damage) {
        if (this.capacity <= 0) {
            return damage;
        }
        this.sinceHit = 0;
        float used = Math.min(this.value, damage);
        this.value -= used;
        return damage - used;
    }

    /**
     * 主动用掉一部分护盾（护盾爆发），不算挨打，不推迟回充。
     *
     * @return 实际用掉的
     */
    public float spend(float amount) {
        float used = Math.clamp(amount, 0, this.value);
        this.value -= used;
        return used;
    }

    public float value() {
        return this.value;
    }

    public float capacity() {
        return this.capacity;
    }

    /** 补满（重生、创造模式）。 */
    public void refill() {
        this.value = this.capacity;
        this.sinceHit = RECHARGE_DELAY;
    }
}
