package io.github.sweetcornna.universe.core.poi;

/**
 * 用采矿激光烧开制造设施的加固门（ADR-0026）：激光照在门上的刻数攒够 {@link #TICKS} 就开。
 * 过热停火的那一会儿不清零；离开门 {@link #FORGET} 刻以上，攒的就散了，要从头烧。
 */
public final class DoorBreach {
    /** 照满 10 秒。 */
    public static final int TICKS = 200;
    /** 多久没照就从头来。 */
    public static final int FORGET = 200;

    private int heat;
    private long last = Long.MIN_VALUE;

    /**
     * 激光照了一刻。
     *
     * @param now 当前的游戏时间
     * @return 烧开了没有
     */
    public boolean hit(long now) {
        if (this.last == Long.MIN_VALUE || now - this.last > FORGET || now < this.last) {
            this.heat = 0;
        }
        this.last = now;
        this.heat = Math.min(TICKS, this.heat + 1);
        return this.heat >= TICKS;
    }

    /** 进度 0–1。 */
    public float progress() {
        return (float) this.heat / TICKS;
    }

    /** 到 {@code now} 为止还记不记得（没照太久的可以扔掉）。 */
    public boolean stale(long now) {
        return this.last != Long.MIN_VALUE && now - this.last > FORGET;
    }
}
