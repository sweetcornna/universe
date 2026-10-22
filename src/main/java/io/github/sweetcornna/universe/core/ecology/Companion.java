package io.github.sweetcornna.universe.core.ecology;

/**
 * 伙伴（ADR-0026 §九）：驯服的动物的信任、饱食和心情，还有每天一次的挖掘、每两天一个卵。
 * 实体每刻调一次 {@link #tick}，时间都按游戏刻算，一个游戏日 {@link #DAY} 刻。
 *
 * <ul>
 * <li>信任 0–100：驯服时 50，喂一口 +5，一个游戏日最多 +10；饿空了每个游戏日掉 10。</li>
 * <li>饱食：满格够吃两个游戏日，喂一口补一半；九成饱以上不吃。</li>
 * <li>心情：吃得饱、信任高是开心，饿着或者信任低是难过，别的是平静。</li>
 * <li>下蛋：饱食过半、信任 ≥ 50 的时候进度在走，攒满两个游戏日下一个。</li>
 * </ul>
 */
public final class Companion {
    public static final int DAY = 24000;
    public static final int TAME_TRUST = 50;
    public static final int MAX_TRUST = 100;
    public static final int FEED_TRUST = 5;
    public static final int DAILY_TRUST = 10;
    /** 饱食满格：两个游戏日吃空。 */
    public static final int FULL = 2 * DAY;
    /** 喂一口补一个游戏日。 */
    public static final int MEAL = DAY;
    /** 九成饱以上不吃。 */
    public static final int SATED = FULL * 9 / 10;
    /** 饿空了每隔这么久掉 1 点信任：一个游戏日掉 10 点。 */
    public static final int STARVE_INTERVAL = DAY / 10;
    public static final int LAY_INTERVAL = 2 * DAY;
    public static final int LAY_TRUST = 50;
    public static final int DIG_INTERVAL = DAY;
    /** 幼崽两个游戏日长成。 */
    public static final int GROW_TIME = 2 * DAY;
    /** 开心：饱食过半、信任 ≥ 70；难过：饱食不到两成或者信任不到 30。 */
    public static final int HAPPY_TRUST = 70;
    public static final int SAD_TRUST = 30;

    public enum Mood {
        HAPPY,
        CALM,
        SAD
    }

    private int trust;
    private int satiety;
    /** 最近一次涨信任是第几个游戏日，那天涨了多少。 */
    private long trustDay;
    private int trustToday;
    private int layProgress;
    private int starving;
    /** 到这一刻才能再挖。 */
    private long digReadyAt;

    private Companion(int trust, int satiety, long trustDay, int trustToday, int layProgress, int starving, long digReadyAt) {
        this.trust = Math.clamp(trust, 0, MAX_TRUST);
        this.satiety = Math.clamp(satiety, 0, FULL);
        this.trustDay = trustDay;
        this.trustToday = Math.clamp(trustToday, 0, DAILY_TRUST);
        this.layProgress = Math.clamp(layProgress, 0, LAY_INTERVAL);
        this.starving = Math.clamp(starving, 0, STARVE_INTERVAL);
        this.digReadyAt = digReadyAt;
    }

    /** 刚驯服（或者刚孵出来）：信任 50，吃得饱饱的。 */
    public static Companion tamed() {
        return new Companion(TAME_TRUST, FULL, Long.MIN_VALUE, 0, 0, 0, 0);
    }

    /** 读档。 */
    public static Companion restore(int trust, int satiety, long trustDay, int trustToday, int layProgress, int starving, long digReadyAt) {
        return new Companion(trust, satiety, trustDay, trustToday, layProgress, starving, digReadyAt);
    }

    /** 还吃得下。 */
    public boolean hungry() {
        return this.satiety < SATED;
    }

    /**
     * 喂一口：饱食补 {@link #MEAL}，信任 +5，同一个游戏日里最多涨 10。
     *
     * @param time 游戏刻
     * @return 这一口涨了多少信任
     */
    public int feed(long time) {
        this.satiety = Math.min(FULL, this.satiety + MEAL);
        this.starving = 0;
        long day = Math.floorDiv(time, DAY);
        if (day != this.trustDay) {
            this.trustDay = day;
            this.trustToday = 0;
        }
        int gain = Math.max(0, Math.min(FEED_TRUST, Math.min(DAILY_TRUST - this.trustToday, MAX_TRUST - this.trust)));
        this.trust += gain;
        this.trustToday += gain;
        return gain;
    }

    /**
     * 过一刻：饱食往下掉，饿空了慢慢掉信任；条件够的话攒下蛋的进度。
     *
     * @param adult 成年的才下蛋
     * @return 这一刻该下一个卵
     */
    public boolean tick(boolean adult) {
        if (this.satiety > 0) {
            this.satiety--;
        } else if (++this.starving >= STARVE_INTERVAL) {
            this.starving = 0;
            this.trust = Math.max(0, this.trust - 1);
        }
        if (adult && this.canLay() && ++this.layProgress >= LAY_INTERVAL) {
            this.layProgress = 0;
            return true;
        }
        return false;
    }

    /** 吃饱了（饱食过半）、信任够：下蛋的进度在走。 */
    public boolean canLay() {
        return this.satiety > FULL / 2 && this.trust >= LAY_TRUST;
    }

    public Mood mood() {
        if (this.satiety < FULL / 5 || this.trust < SAD_TRUST) {
            return Mood.SAD;
        }
        if (this.satiety > FULL / 2 && this.trust >= HAPPY_TRUST) {
            return Mood.HAPPY;
        }
        return Mood.CALM;
    }

    public boolean canDig(long time) {
        return time >= this.digReadyAt;
    }

    /** 还要多少刻才能再挖，0 是现在就能。 */
    public long digWait(long time) {
        return Math.max(0, this.digReadyAt - time);
    }

    /** 挖了一次：一个游戏日以后才能再挖。 */
    public void dig(long time) {
        this.digReadyAt = time + DIG_INTERVAL;
    }

    /** 幼崽的体型：出生时是成年的一半，到长成时匀速长到一倍。 @param age 出生了多少刻 */
    public static double youngScale(int age) {
        return 0.5 + 0.5 * Math.clamp(age / (double) GROW_TIME, 0, 1);
    }

    public int trust() {
        return this.trust;
    }

    public int satiety() {
        return this.satiety;
    }

    /** 饱食占满格的几成（0–1）。 */
    public double satietyShare() {
        return this.satiety / (double) FULL;
    }

    public long trustDay() {
        return this.trustDay;
    }

    public int trustToday() {
        return this.trustToday;
    }

    public int layProgress() {
        return this.layProgress;
    }

    public int starving() {
        return this.starving;
    }

    public long digReadyAt() {
        return this.digReadyAt;
    }
}
