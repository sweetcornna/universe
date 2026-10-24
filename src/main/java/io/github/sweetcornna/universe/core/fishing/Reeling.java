package io.github.sweetcornna.universe.core.fishing;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 收线（ADR-0034 §一）：钩住以后按住右键收线、松开放线。服务端每刻推进一次，客户端只报按着还是松开。
 *
 * <ul>
 *   <li>张力在绿区（{@value #GREEN_LOW}–{@value #GREEN_HIGH}）里进度涨满速，过高涨一半，过低不涨。</li>
 *   <li>按住时张力按鱼的力气往上走，松开时每刻降 {@value #RELEASE_DROP}。</li>
 *   <li>鱼隔一阵猛冲一次：张力往上跳、进度往回掉，冲的那几刻按着收线张力涨得更快。冲之前 {@value #WARN_TICKS} 刻有预兆
 *       （浮标抽动、张力条闪），来得及松手。</li>
 *   <li>张力到 1 线断；张力贴着 0 超过 {@value #SLACK_TICKS} 刻鱼脱钩；进度满了钓上来。</li>
 * </ul>
 */
public final class Reeling {
    public static final double GREEN_LOW = 0.25;
    public static final double GREEN_HIGH = 0.8;
    public static final double RELEASE_DROP = 0.04;
    /** 张力低于这么多算"贴着 0"。 */
    public static final double SLACK = 0.02;
    public static final int SLACK_TICKS = 40;
    /** 钩住时的张力。 */
    public static final double START_TENSION = 0.3;
    /** 一次猛冲持续几刻。 */
    public static final int DASH_TICKS = 8;
    /** 猛冲之前几刻有预兆。 */
    public static final int WARN_TICKS = 10;

    public enum Outcome {
        REELING,
        CAUGHT,
        /** 线断了：饵没了，鱼跑了。 */
        SNAPPED,
        /** 线松太久，鱼脱钩了。 */
        ESCAPED
    }

    private final double strength;
    private final double speed;
    private double tension = START_TENSION;
    private double progress;
    private int slack;
    private int nextDash;
    private int dashLeft;
    private int ticks;

    /**
     * @param strength  鱼的力气（0–1）
     * @param reelTicks 张力一直在绿区时收上来要几刻
     */
    public Reeling(double strength, int reelTicks, Rng rng) {
        this.strength = Math.clamp(strength, 0, 1);
        this.speed = 1.0 / Math.max(1, reelTicks);
        this.nextDash = this.dashInterval(rng);
    }

    public Reeling(FishSpecies species, Rng rng) {
        this(species.strength(), species.reelTicks(), rng);
    }

    /** 按住时每刻张力涨多少。 */
    public double pull() {
        return 0.012 + 0.02 * this.strength;
    }

    /** 推进一刻。 */
    public Outcome tick(boolean holding, Rng rng) {
        this.ticks++;
        if (this.dashLeft > 0) {
            this.dashLeft--;
        } else if (--this.nextDash <= 0) {
            // 猛冲：张力一下子上去，进度往回掉
            this.dashLeft = DASH_TICKS;
            this.nextDash = this.dashInterval(rng);
            this.tension += 0.1 + 0.18 * this.strength;
            this.progress = Math.max(0, this.progress - (0.02 + 0.03 * this.strength));
        }
        if (holding) {
            this.tension += this.pull() * (this.dashLeft > 0 ? 2 : 1);
        } else {
            this.tension -= RELEASE_DROP;
        }
        this.tension = Math.max(0, this.tension);
        if (this.tension >= 1) {
            this.tension = 1;
            return Outcome.SNAPPED;
        }
        if (this.tension >= GREEN_LOW && this.tension <= GREEN_HIGH) {
            this.progress += this.speed;
        } else if (this.tension > GREEN_HIGH) {
            this.progress += this.speed / 2;
        }
        if (this.progress >= 1) {
            this.progress = 1;
            return Outcome.CAUGHT;
        }
        if (this.tension <= SLACK) {
            if (++this.slack > SLACK_TICKS) {
                return Outcome.ESCAPED;
            }
        } else {
            this.slack = 0;
        }
        return Outcome.REELING;
    }

    /** 下一次猛冲隔几刻：力气越大冲得越勤。 */
    private int dashInterval(Rng rng) {
        int low = (int) Math.round(70 - 40 * this.strength) + WARN_TICKS;
        int high = (int) Math.round(130 - 60 * this.strength) + WARN_TICKS;
        return rng.range(low, high);
    }

    public double tension() {
        return this.tension;
    }

    public double progress() {
        return this.progress;
    }

    /** 正在猛冲。 */
    public boolean dashing() {
        return this.dashLeft > 0;
    }

    /** 马上要冲了（{@value #WARN_TICKS} 刻以内）。 */
    public boolean warning() {
        return this.dashLeft == 0 && this.nextDash <= WARN_TICKS;
    }

    /** 线松了几刻（快脱钩时 HUD 上提醒）。 */
    public int slack() {
        return this.slack;
    }

    /** 收了几刻。 */
    public int ticks() {
        return this.ticks;
    }
}
