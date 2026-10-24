package io.github.sweetcornna.universe.core.fishing;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 甩竿以后多久咬钩（ADR-0034 §一）：8–20 秒，按鱼饵和放生缩短。鱼来之前 2–4 秒水下有鱼影游过来；咬钩后 25 刻内右键才钩得住。
 */
public final class BiteTimer {
    public static final int MIN_WAIT = 160;
    public static final int MAX_WAIT = 400;
    public static final int MIN_APPROACH = 40;
    public static final int MAX_APPROACH = 80;
    /** 咬钩以后多少刻内右键算钩住。 */
    public static final int BITE_WINDOW = 25;
    /** 放生以后接下来几次甩竿有加成。 */
    public static final int RELEASE_CASTS = 3;
    /** 放生以后等鱼的时间是原来的几倍。 */
    public static final double RELEASE_WAIT = 0.5;

    private BiteTimer() {
    }

    /** 落水以后等几刻咬钩（含鱼影游过来的那段）。 */
    public static int waitTicks(Bait bait, boolean released, Rng rng) {
        double ticks = rng.range(MIN_WAIT, MAX_WAIT) * bait.waitFactor() * (released ? RELEASE_WAIT : 1);
        return Math.max(MIN_APPROACH + 10, (int) Math.round(ticks));
    }

    /** 鱼影游过来要几刻（不超过整个等鱼的时间）。 */
    public static int approachTicks(int waitTicks, Rng rng) {
        return Math.min(rng.range(MIN_APPROACH, MAX_APPROACH), waitTicks - 10);
    }
}
