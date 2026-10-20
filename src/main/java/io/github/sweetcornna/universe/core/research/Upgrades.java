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
            default -> List.of();
        };
    }

    /** 0.116 → "11.6"，整数不带小数点。 */
    static String percent(double fraction) {
        double value = Math.round(fraction * 1000) / 10.0;
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
