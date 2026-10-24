package io.github.sweetcornna.universe.core.hazard;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 尘卷风（ADR-0034 §六）：底下半径 {@value #RADIUS} 格、{@value #HEIGHT} 格高的旋转尘柱，越往上越粗。慢慢游走，
 * 把掉落物、生物、人卷进去，绕着转着往上带，转几圈或者到了顶就往外甩出去。不直接伤人，摔下来照算。
 *
 * <p>从上往下看是逆时针转（北半球的样子）。轻的东西转得快、升得快，重的慢；太大的（宽过 {@value #MAX_WIDTH} 格）卷不动。
 */
public final class Vortex {
    public static final double RADIUS = 3;
    public static final double HEIGHT = 12;
    /** 每升一格，柱子粗多少。 */
    static final double FLARE = 0.18;
    public static final int LIFE_MIN = 30 * 20;
    public static final int LIFE_MAX = 60 * 20;
    /** 游走的速度（格/刻）。 */
    public static final double SPEED = 0.07;
    /** 绕圈的切向速度（格/刻）。 */
    static final double SPIN = 0.55;
    /** 往上带的速度（格/刻）。 */
    static final double LIFT = 0.17;
    /** 卷进去以后最多转多久就甩出去。 */
    public static final int CATCH_MIN = 45;
    public static final int CATCH_MAX = 75;
    /** 甩出去的水平速度和往上的速度（格/刻）。 */
    static final double FLING = 1.0;
    static final double FLING_UP = 0.45;
    /** 卷得动的最大宽度（格）。 */
    public static final double MAX_WIDTH = 2.0;
    /** 甩出去以后多久不再卷（刻），免得一出去又被吸回来。 */
    public static final int COOLDOWN = 50;
    /** 离玩家多远冒出来。 */
    public static final int SPAWN_MIN = 16;
    public static final int SPAWN_MAX = 28;

    private Vortex() {
    }

    /** 离地 {@code dy} 格处柱子的半径。 */
    public static double radiusAt(double dy) {
        return RADIUS + FLARE * Math.clamp(dy, 0, HEIGHT);
    }

    /** 离柱子中心 {@code (dx, dy, dz)} 的东西卷不卷得着。 */
    public static boolean reaches(double dx, double dy, double dz) {
        return dy >= -1 && dy <= HEIGHT && dx * dx + dz * dz <= radiusAt(dy) * radiusAt(dy);
    }

    /** 这个东西卷不卷得动。 */
    public static boolean liftable(double width, double height) {
        return width <= MAX_WIDTH && height <= MAX_WIDTH * 1.5;
    }

    /**
     * 东西的重：掉落物 0.6，人 1，生物按个头 0.6–3。
     *
     * @param volume 碰撞箱的体积（立方格）
     */
    public static double weight(double volume, boolean item) {
        if (item) {
            return 0.6;
        }
        return Math.clamp(Math.sqrt(volume / 0.6), 0.6, 3);
    }

    /** 卷进去以后转多久甩出去（刻）。 */
    public static int catchTicks(Rng rng) {
        return rng.range(CATCH_MIN, CATCH_MAX);
    }

    /**
     * 卷着的东西这一刻的速度：绕着转、往目标半径收、往上带。
     *
     * @return {@code {vx, vy, vz}}（格/刻）
     */
    public static double[] carry(double dx, double dy, double dz, double weight) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r < 1e-4) {
            // 正中间：先往外推一点，免得除以零
            return new double[] {0.1, LIFT / weight, 0};
        }
        double tx = -dz / r;
        double tz = dx / r;
        // 绕的半径随高度张开一点，转着转着人就被带到柱子边上
        double target = 1.0 + 0.12 * Math.clamp(dy, 0, HEIGHT);
        double inward = (target - r) * 0.18;
        double spin = SPIN / Math.sqrt(weight);
        double lift = dy < HEIGHT - 1.5 ? LIFT / weight : 0.03;
        return new double[] {tx * spin + dx / r * inward, lift, tz * spin + dz / r * inward};
    }

    /** 该甩出去了：转够了，或者到了顶。 */
    public static boolean flings(int caught, int limit, double dy) {
        return caught >= limit || dy >= HEIGHT - 1;
    }

    /**
     * 甩出去的速度：沿着转的方向往外。
     *
     * @return {@code {vx, vy, vz}}（格/刻）
     */
    public static double[] fling(double dx, double dz, double weight) {
        double r = Math.max(1e-4, Math.sqrt(dx * dx + dz * dz));
        double ox = dx / r;
        double oz = dz / r;
        double tx = -oz;
        double tz = ox;
        double speed = FLING / Math.sqrt(weight);
        return new double[] {(ox * 0.7 + tx * 0.7) * speed, FLING_UP / Math.sqrt(weight), (oz * 0.7 + tz * 0.7) * speed};
    }

    /** 游走：方向每刻慢慢偏一点，返回新的方向（弧度）。 */
    public static double wander(double heading, Rng rng) {
        return heading + (rng.nextDouble() - 0.5) * 0.16;
    }

    /**
     * 在玩家附近哪里冒出来。
     *
     * @return {@code {dx, dz}}
     */
    public static double[] spawn(Rng rng) {
        double angle = rng.nextDouble() * Math.PI * 2;
        double distance = rng.range((double) SPAWN_MIN, (double) SPAWN_MAX);
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance};
    }

    /** 冒出来时朝哪走：大致朝着玩家，偏一点。 */
    public static double heading(double dx, double dz, Rng rng) {
        return Math.atan2(-dz, -dx) + (rng.nextDouble() - 0.5) * 1.2;
    }
}
