package io.github.sweetcornna.universe.core.survival;

/**
 * 外骨骼的个人护盾（ADR-0014）：容量来自护盾模块，先替玩家挡下战斗伤害；5 秒没受伤后开始回充，3 秒充满。
 */
public final class Shield {
    public static final int RECHARGE_DELAY = 100;
    static final float RECHARGE_TICKS = 60;

    private float value;
    private float capacity;
    private int sinceHit = RECHARGE_DELAY;

    /** 推进一刻。容量变小时当前值跟着截断；刚装上护盾模块时从零开始充。 */
    public void tick(float capacity) {
        this.capacity = Math.max(0, capacity);
        this.value = Math.min(this.value, this.capacity);
        if (this.sinceHit < RECHARGE_DELAY) {
            this.sinceHit++;
        } else if (this.value < this.capacity) {
            this.value = Math.min(this.capacity, this.value + this.capacity / RECHARGE_TICKS);
        }
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
