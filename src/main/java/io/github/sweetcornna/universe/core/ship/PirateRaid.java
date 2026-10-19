package io.github.sweetcornna.universe.core.ship;

/**
 * 海盗袭击的规则（ADR-0017）。
 *
 * <p>在太空里驾驶飞船、离空间站 {@link #STATION_SAFE} 格以外时，每隔 6–10 分钟掷一次：货舱越值钱越容易被盯上。
 * 掷中了先提示"海盗正在扫描你的货舱"，{@link #WARNING_TICKS} 刻后 2–4 架海盗战机从身后出现。全部打下就是打退了袭击；
 * 跃迁、降落到星球、飞进空间站附近，海盗都会撤走；甩开到 {@link #GIVE_UP_DISTANCE} 格以外，海盗也会放弃。
 */
public final class PirateRaid {
    /** 两次掷骰子之间隔多少刻（6–10 分钟）。 */
    public static final int CHECK_MIN = 6 * 60 * 20;
    public static final int CHECK_MAX = 10 * 60 * 20;
    /** 提示扫描货舱以后过多少刻海盗出现。 */
    public static final int WARNING_TICKS = 100;
    /** 离空间站这么近就安全：不会遇到海盗，正在打的海盗也会撤走。 */
    public static final double STATION_SAFE = 600;
    /** 海盗在身后多远出现。 */
    public static final double SPAWN_DISTANCE = 90;
    /** 甩开这么远，海盗就放弃。 */
    public static final double GIVE_UP_DISTANCE = 400;
    /** 这么近之内有海盗，脉冲引擎被干扰、开不起来。 */
    public static final double JAM_DISTANCE = 250;
    /** 每架海盗战机的船体和护盾。 */
    public static final double FIGHTER_HULL = 24;
    public static final double FIGHTER_SHIELD = 16;
    /** 赏金：每架 200 信用点 × 财富档（贫穷 1、中等 2、富裕 3）。 */
    public static final long BOUNTY = 200;
    /** 货舱很空时也有一点概率；越值钱越高，最高不超过 {@link #MAX_CHANCE}。 */
    static final double BASE_CHANCE = 0.1;
    static final double MAX_CHANCE = 0.7;
    /** 货值到这么多时，概率涨到最低和最高的正中间。 */
    static final double HALF_VALUE = 2000;
    /** 货值到这么多时，平均多来一架（最多 4 架）。 */
    static final double EXTRA_FIGHTER_VALUE = 4000;

    private PirateRaid() {
    }

    /** 这一次掷骰子遇到海盗的概率。 */
    public static double chance(double cargoValue) {
        double v = Math.max(0, cargoValue);
        return BASE_CHANCE + (MAX_CHANCE - BASE_CHANCE) * v / (v + HALF_VALUE);
    }

    /** 来几架（2–4）：{@code roll} 是 [0, 1) 的随机数。 */
    public static int fighters(double cargoValue, double roll) {
        double v = Math.max(0, cargoValue);
        double expected = 2 + 2 * v / (v + EXTRA_FIGHTER_VALUE);
        return Math.max(2, Math.min(4, (int) Math.floor(expected + roll)));
    }

    /** 打下一架的赏金。 */
    public static long bounty(int wealthTier) {
        return BOUNTY * Math.max(1, wealthTier);
    }

    /** 下一次掷骰子在多少刻以后：{@code roll} 是 [0, 1) 的随机数。 */
    public static int nextCheck(double roll) {
        return CHECK_MIN + (int) (Math.max(0, Math.min(1, roll)) * (CHECK_MAX - CHECK_MIN));
    }
}
