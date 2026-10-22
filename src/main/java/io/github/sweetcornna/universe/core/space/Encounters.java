package io.github.sweetcornna.universe.core.space;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;

/**
 * 太空遭遇的规则（ADR-0024）：开船在开阔的太空里时每隔一阵判定一次，和海盗分开算。
 */
public final class Encounters {
    /** 两次判定之间隔多久（刻）：6–10 分钟。 */
    public static final int MIN_INTERVAL = 6 * 60 * 20;
    public static final int MAX_INTERVAL = 10 * 60 * 20;
    /** 离空间站这么近不判定。 */
    public static final double STATION_SAFE = 600;
    /** 废弃货船出现在前方多远。 */
    public static final double MIN_AHEAD = 1500;
    public static final double MAX_AHEAD = 2500;
    /** 遇险的货船近得多：海盗得在模拟距离里才会动。 */
    public static final double MIN_DISTRESS_AHEAD = 260;
    public static final double MAX_DISTRESS_AHEAD = 340;
    /** 飞到离遇险的货船这么近，围攻它的海盗才露面。 */
    public static final double RAIDER_RANGE = 170;
    /** 遭遇离星球表面、恒星、空间站至少这么远。 */
    public static final double BODY_CLEARANCE = 300;
    public static final double STATION_CLEARANCE = 800;
    /** 废弃货船留多久（刻）：30 分钟。 */
    public static final int DERELICT_LIFETIME = 30 * 60 * 20;
    /** 遇险的货船等多久（刻）：海盗露面以后 3 分钟内要打光。 */
    public static final int DISTRESS_TIME = 3 * 60 * 20;
    /** 遇险信号发出来以后，没人赶到就在这么久（刻）以后断掉。 */
    public static final int SIGNAL_TIME = 5 * 60 * 20;
    /** 围攻货船的海盗数。 */
    public static final int MIN_RAIDERS = 3;
    public static final int MAX_RAIDERS = 5;
    /** 救下货船、已经有货船的人拿到的信用点。 */
    public static final int RESCUE_REWARD = 15000;
    /** 太空守卫机：通缉星数到了多少，飞出大气层就追过来。 */
    public static final int SENTINEL_WANTED = 3;
    /** 拦截机的数量是 2 + 通缉星数。 */
    public static final int SENTINEL_BASE = 2;
    /** 拦截机：船体、护盾比海盗战机（24、16）厚，飞得快三成；甩开这么远就放弃。 */
    public static final double INTERCEPTOR_HULL = 40;
    public static final double INTERCEPTOR_SHIELD = 24;
    public static final double INTERCEPTOR_SPEED = 1.3;
    public static final double INTERCEPTOR_GIVE_UP = 1200;
    /** 漏电的地方：离多近会挨电（格），每次多少伤害，隔多久电一次（刻）。 */
    public static final double HAZARD_RANGE = 2.2;
    public static final float HAZARD_DAMAGE = 3;
    public static final int HAZARD_INTERVAL = 10;
    /** 打中遇险的货船这么多下，就算是在打它：货船跳走，守卫机追来。 */
    public static final int FRIENDLY_FIRE = 8;
    /** 围攻货船的海盗：你飞到这么近（格）或者开了火，就转过来打你。 */
    public static final double PROVOKE_RANGE = 80;
    /** 打光海盗以后，船长等你回话多久（刻）。 */
    public static final int OFFER_TIME = 2 * 60 * 20;
    /** 一枚信用点芯片换多少信用点。 */
    public static final int CREDIT_CHIP = 1000;

    public enum Kind {
        DERELICT, DISTRESS, NOTHING
    }

    private Encounters() {
    }

    /** 下一次判定在多久以后。 */
    public static int interval(Rng rng) {
        return rng.range(MIN_INTERVAL, MAX_INTERVAL);
    }

    /** 这次遇上什么：废弃货船 40%，遇险信号 40%，什么也没有 20%。 */
    public static Kind roll(Rng rng) {
        double r = rng.nextDouble();
        return r < 0.4 ? Kind.DERELICT : r < 0.8 ? Kind.DISTRESS : Kind.NOTHING;
    }

    /**
     * 遭遇放在哪：船头前方偏一点，高度限制在太空维度里。
     *
     * @param yaw 船的偏航（0 朝 +z）
     */
    public static double[] ahead(double x, double y, double z, float yaw, Rng rng) {
        return ahead(x, y, z, yaw, MIN_AHEAD, MAX_AHEAD, rng);
    }

    /** 同上，距离在 {@code min}–{@code max} 之间。 */
    public static double[] ahead(double x, double y, double z, float yaw, double min, double max, Rng rng) {
        double angle = Math.toRadians(yaw + rng.range(-25, 25));
        double distance = rng.range(min, max);
        double ty = Math.max(-80, Math.min(80, y + rng.range(-40, 40)));
        return new double[] {x - Math.sin(angle) * distance, ty, z + Math.cos(angle) * distance};
    }

    /** 救下来的货船是什么档次：C 一半，B 三成半，A 一成半。 */
    public static Grade rescueGrade(Rng rng) {
        double r = rng.nextDouble();
        return r < 0.5 ? Grade.C : r < 0.85 ? Grade.B : Grade.A;
    }

    /**
     * 这个位置离星球、恒星够不够远。
     *
     * @param bodies {@code {x, y, z, 半径}}，半径算到表面
     */
    public static boolean clear(double[] at, double starRadius, double[][] bodies) {
        if (Math.sqrt(at[0] * at[0] + at[1] * at[1] + at[2] * at[2]) < starRadius + BODY_CLEARANCE) {
            return false;
        }
        for (double[] b : bodies) {
            double dx = at[0] - b[0];
            double dy = at[1] - b[1];
            double dz = at[2] - b[2];
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) < b[3] + BODY_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    /** 离漏电的地方 {@code (hx, hy, hz)}（方块中心）这么近会挨电。 */
    public static boolean shocks(double x, double y, double z, double hx, double hy, double hz) {
        double dx = x - hx;
        double dy = y - hy;
        double dz = z - hz;
        return dx * dx + dy * dy + dz * dz < HAZARD_RANGE * HAZARD_RANGE;
    }

    /** 太空守卫机的拦截机数量；不够星数时 0。 */
    public static int sentinels(int wanted) {
        return wanted >= SENTINEL_WANTED ? SENTINEL_BASE + wanted : 0;
    }
}
