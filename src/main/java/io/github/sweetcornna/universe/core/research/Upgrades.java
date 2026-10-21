package io.github.sweetcornna.universe.core.research;

import java.util.List;
import java.util.Locale;

/**
 * 升级科技的效果（ADR-0018）。全服共用研究进度里的级数，各处设备按级数算倍数。
 */
public final class Upgrades {
    /** 矿脉利用：采矿速度每级 +10%，矿脉每采一个矿的消耗每级 ×0.94（按概率扣）。 */
    public static final String VEIN_UTILIZATION = "vein_utilization";
    /** 研究速度：研究站每级 +25%。 */
    public static final String RESEARCH_SPEED = "research_speed";
    /** 物流载具引擎：无人机和运输船每级 +25% 速度。 */
    public static final String CARRIER_ENGINE = "carrier_engine";
    /** 物流载具容量：无人机和运输船每级多装一成（取整）。 */
    public static final String CARRIER_CAPACITY = "carrier_capacity";
    /** 射线传输效率：射线接收站的损耗每级 ×0.85。 */
    public static final String RAY_EFFICIENCY = "ray_efficiency";
    /** 太阳帆寿命：每级 +15 分钟。 */
    public static final String SAIL_LIFETIME = "sail_lifetime";

    /** 集装物流（ADR-0022）：研究了"集装物流系统"传送带一格叠 2 个，以后每级多 1 个，最多 4 个。 */
    public static final String BELT_STACKING = "belt_stacking";
    /** 宇宙探索（ADR-0022）：星图上看得到多少资源，四级。 */
    public static final String UNIVERSE_EXPLORATION = "universe_exploration";

    /** 太阳帆原来的寿命：90 分钟（刻）。 */
    public static final long BASE_SAIL_LIFETIME = 108_000;
    /** 每级加的寿命：15 分钟（刻）。 */
    public static final long SAIL_LIFETIME_STEP = 18_000;
    public static final int BASE_DRONE_CAPACITY = 50;
    public static final int BASE_VESSEL_CAPACITY = 500;

    private Upgrades() {
    }

    public static double miningSpeed(int level) {
        return 1 + 0.1 * level;
    }

    /** 采一个矿有多大概率扣矿脉的储量。 */
    public static double veinConsumption(int level) {
        return Math.pow(0.94, level);
    }

    public static double researchSpeed(int level) {
        return 1 + 0.25 * level;
    }

    public static double carrierSpeed(int level) {
        return 1 + 0.25 * level;
    }

    /** 载具的容量：原来的容量每级多一成，向下取整。 */
    public static int carrierCapacity(int base, int level) {
        return (int) Math.floor(base * (10L + level) / 10.0);
    }

    /** 射线接收站损耗的倍数。 */
    public static double rayLoss(int level) {
        return Math.pow(0.85, level);
    }

    /** 太阳帆的寿命（刻）。 */
    public static long sailLifetime(int level) {
        return BASE_SAIL_LIFETIME + SAIL_LIFETIME_STEP * level;
    }

    /** 自动集装机和集装分拣器一格叠几个。 */
    public static int beltStack(int level) {
        return Math.min(4, 2 + Math.max(0, level));
    }

    /** 宇宙探索 2 级、3 级看得到的范围（光年，从最近的去过的星系算）。 */
    public static final double EXPLORATION_NEAR = 10;
    public static final double EXPLORATION_FAR = 30;
    public static final int EXPLORATION_MAX = 4;

    /** 星图上一个星系看得到多少（ADR-0022）。 */
    public enum Reveal {
        /** 只看得到恒星。 */
        STAR,
        /** 还看得到行星。 */
        PLANETS,
        /** 还看得到行星上的资源。 */
        RESOURCES
    }

    /**
     * 宇宙探索的级数决定星图上看得到什么：去过的星系一直看得到行星，1 级起还看得到资源；没去过的星系 2 级起看得到
     * {@link #EXPLORATION_NEAR} 光年内的行星，3 级起 {@link #EXPLORATION_FAR} 光年内的行星和资源，4 级起整个银河。
     *
     * @param nearest 离最近的去过的星系多少光年
     */
    public static Reveal reveal(int level, boolean explored, double nearest) {
        if (explored) {
            return level >= 1 ? Reveal.RESOURCES : Reveal.PLANETS;
        }
        if (level >= EXPLORATION_MAX || level >= 3 && nearest <= EXPLORATION_FAR) {
            return Reveal.RESOURCES;
        }
        return level >= 2 && nearest <= EXPLORATION_NEAR ? Reveal.PLANETS : Reveal.STAR;
    }

    /** 效果说明的参数，配语言键 {@code upgrade.universe.<id>.effect}。 */
    public static List<String> effectArgs(String id, int level) {
        return switch (id) {
            case VEIN_UTILIZATION -> List.of(percent(miningSpeed(level) - 1), percent(1 - veinConsumption(level)));
            case RESEARCH_SPEED -> List.of(percent(researchSpeed(level) - 1));
            case CARRIER_ENGINE -> List.of(percent(carrierSpeed(level) - 1));
            case CARRIER_CAPACITY -> List.of(Integer.toString(carrierCapacity(BASE_DRONE_CAPACITY, level)),
                    Integer.toString(carrierCapacity(BASE_VESSEL_CAPACITY, level)));
            case RAY_EFFICIENCY -> List.of(percent(1 - rayLoss(level)));
            case SAIL_LIFETIME -> List.of(Long.toString(sailLifetime(level) / 1200));
            case BELT_STACKING -> List.of(Integer.toString(beltStack(level)));
            case UNIVERSE_EXPLORATION -> List.of(Integer.toString((int) EXPLORATION_NEAR), Integer.toString((int) EXPLORATION_FAR));
            default -> List.of();
        };
    }

    /** 0.116 → "11.6"，整数不带小数点。 */
    static String percent(double fraction) {
        double value = Math.round(fraction * 1000) / 10.0;
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
