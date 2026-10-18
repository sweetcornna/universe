package io.github.sweetcornna.universe.core.survival;

/**
 * 采矿激光的热量（玩法设计 §4）：开火时升温，连续开 4 秒过热；过热后强制冷却 2 秒才能再开。不开火半秒后开始冷却。
 */
public final class LaserHeat {
    public static final float MAX = 100;
    static final float HEAT_PER_TICK = MAX / 80;
    static final float COOL_PER_TICK = MAX / 40;
    /** 停火多少刻后开始冷却。 */
    static final int COOL_DELAY = 10;

    private float heat;
    private boolean overheated;
    private int idle;

    /**
     * 推进一刻。
     *
     * @param trigger 玩家这一刻按着开火
     * @return 这一刻激光真的开火了（没有过热）
     */
    public boolean tick(boolean trigger) {
        return this.tick(trigger, 1);
    }

    /**
     * 推进一刻。
     *
     * @param heatFactor 升温的倍数（散热片模块让它小于 1）
     */
    public boolean tick(boolean trigger, float heatFactor) {
        if (trigger && !this.overheated) {
            this.heat = Math.min(MAX, this.heat + HEAT_PER_TICK * heatFactor);
            this.idle = 0;
            if (this.heat >= MAX) {
                this.overheated = true;
            }
            return true;
        }
        this.idle++;
        if (this.overheated || this.idle > COOL_DELAY) {
            this.heat = Math.max(0, this.heat - COOL_PER_TICK);
            if (this.heat <= 0) {
                this.overheated = false;
            }
        }
        return false;
    }

    public float heat() {
        return this.heat;
    }

    public boolean overheated() {
        return this.overheated;
    }
}
