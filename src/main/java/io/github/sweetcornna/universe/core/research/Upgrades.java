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

    /** 机甲核心（ADR-0023）：外骨骼能量核心的容量。 */
    public static final String MECHA_CORE = "mecha_core";
    /** 能量回路：燃烧室的功率。 */
    public static final String ENERGY_CIRCUIT = "energy_circuit";
    /** 驱动引擎：1 级解锁持续飞行，之后加飞行速度。 */
    public static final String DRIVE_ENGINE = "drive_engine";
    /** 机械骨骼：走路和跑步的速度。 */
    public static final String MECHANICAL_FRAME = "mechanical_frame";
    /** 通讯控制：建造无人机的数量，3 级起每趟多带任务。 */
    public static final String COMMUNICATION_CONTROL = "communication_control";
    /** 无人机引擎：建造无人机的速度。 */
    public static final String DRONE_ENGINE = "drone_engine";
    /** 批量建造：一次能放多大的蓝图。 */
    public static final String MASS_CONSTRUCTION = "mass_construction";

    /** 太阳帆原来的寿命：90 分钟（刻）。 */
    public static final long BASE_SAIL_LIFETIME = 108_000;
    /** 每级加的寿命：15 分钟（刻）。 */
    public static final long SAIL_LIFETIME_STEP = 18_000;
    public static final int BASE_DRONE_CAPACITY = 50;
    public static final int BASE_VESSEL_CAPACITY = 500;
    /** 配送机器人一次带几个；"运输载具容量"每级 +2。 */
    public static final int BASE_BOT_CAPACITY = 8;
    /** 能量核心原来的容量（焦）：戴森球计划的 120 MJ。 */
    public static final long BASE_MECHA_CAPACITY = 120_000;
    /** 燃烧室原来的功率（焦/刻）：800 W。 */
    public static final long BASE_FUEL_POWER = 40;
    /** "批量建造"满级以后不限方块数。 */
    public static final int UNLIMITED = Integer.MAX_VALUE;
    private static final long[] MECHA_CAPACITY = {BASE_MECHA_CAPACITY, 200_000, 400_000, 800_000, 1_600_000, 3_200_000};
    private static final long[] FUEL_POWER = {BASE_FUEL_POWER, 50, 60, 70, 80, 120};
    private static final double[] FRAME_BONUS = {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0};
    private static final double[] FLIGHT_SPEED = {0, 1, 1.3, 1.6, 2};
    private static final long[] DRONES = {3, 4, 6, 6, 9, 12};
    private static final long[] DRONE_SPEED = {6, 8, 10, 12, 15, 18};
    private static final int[] BUILD_LIMIT = {300, 600, 1800, 7200, UNLIMITED};

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

    /** 配送机器人一次带几个物品。 */
    public static int botCapacity(int carrierCapacityLevel) {
        return BASE_BOT_CAPACITY + 2 * Math.max(0, carrierCapacityLevel);
    }

    /** 能量核心的容量（焦）：五级到 3.2 MJ，之后每级 +2.4 MJ。 */
    public static long mechaCapacity(int level) {
        return tail(MECHA_CAPACITY, level, 2_400_000);
    }

    /** 燃烧室的功率（焦/刻）：四级每级 +200 W，第五级 2.4 kW，之后每级 +1 kW。 */
    public static long fuelPower(int level) {
        return tail(FUEL_POWER, level, 50);
    }

    /** 机械骨骼加的走路速度（比例）：前六级每级一成，后两级每级两成。 */
    public static double frameBonus(int level) {
        return FRAME_BONUS[Math.clamp(level, 0, FRAME_BONUS.length - 1)];
    }

    /** 飞行速度的倍数；0 是还不能飞。 */
    public static double flightSpeed(int level) {
        return FLIGHT_SPEED[Math.clamp(level, 0, FLIGHT_SPEED.length - 1)];
    }

    /** 建造无人机的数量。 */
    public static int drones(int level) {
        return (int) Math.min(Integer.MAX_VALUE, tail(DRONES, level, 3));
    }

    /** 建造无人机每趟建几格：原作 3 级起多带一个任务，这里从 4 格变成 8 格。 */
    public static int droneTasks(int level) {
        return level >= 3 ? 8 : 4;
    }

    /** 建造无人机的速度（m/s）。 */
    public static int droneSpeed(int level) {
        return (int) Math.min(Integer.MAX_VALUE, tail(DRONE_SPEED, level, 3));
    }

    /** 一次能放的蓝图最多有几个方块。 */
    public static int buildLimit(int level) {
        return BUILD_LIMIT[Math.clamp(level, 0, BUILD_LIMIT.length - 1)];
    }

    /** 表里有的级数查表，之后每级加 step。 */
    private static long tail(long[] table, int level, long step) {
        int last = table.length - 1;
        if (level <= last) {
            return table[Math.max(0, level)];
        }
        return table[last] + step * (level - last);
    }

    /** 120000 → "120 kJ"，3200000 → "3.2 MJ"。 */
    public static String energy(double joules) {
        return scaled(joules, "J");
    }

    /** 每刻的焦耳换成瓦：40 → "800 W"，120 → "2.4 kW"。 */
    public static String watts(double joulesPerTick) {
        return scaled(joulesPerTick * 20, "W");
    }

    private static String scaled(double value, String unit) {
        String[] prefixes = {"", "k", "M", "G", "T"};
        int index = 0;
        while (Math.abs(value) >= 999.95 && index < prefixes.length - 1) {
            value /= 1000;
            index++;
        }
        return multiplier(Math.round(value * 10) / 10.0) + " " + prefixes[index] + unit;
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
            case MECHA_CORE -> List.of(energy(mechaCapacity(level)));
            case ENERGY_CIRCUIT -> List.of(watts(fuelPower(level)));
            case DRIVE_ENGINE -> List.of(multiplier(flightSpeed(level)));
            case MECHANICAL_FRAME -> List.of(percent(frameBonus(level)));
            case COMMUNICATION_CONTROL -> List.of(Integer.toString(drones(level)), Integer.toString(droneTasks(level)));
            case DRONE_ENGINE -> List.of(Integer.toString(droneSpeed(level)));
            case MASS_CONSTRUCTION -> List.of(buildLimit(level) == UNLIMITED ? "\u221e" : Integer.toString(buildLimit(level)));
            default -> List.of();
        };
    }

    /** 1.3 → "1.3"，2.0 → "2"。 */
    static String multiplier(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** 0.116 → "11.6"，整数不带小数点。 */
    static String percent(double fraction) {
        double value = Math.round(fraction * 1000) / 10.0;
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
