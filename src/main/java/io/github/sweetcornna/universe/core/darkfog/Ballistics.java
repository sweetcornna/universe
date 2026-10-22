package io.github.sweetcornna.universe.core.darkfog;

/**
 * 聚爆炮的弹道（ADR-0028 §五）：炮弹没有空气阻力，每刻先减重力再走一步（和原版的投射物一样的顺序）。
 * 定下飞几刻，倒推出射出去的速度，正好落在目标上。
 */
public final class Ballistics {
    /** 炮弹每刻往下加的速度（格/刻²）。 */
    public static final double GRAVITY = 0.05;
    /** 飞行时间最少、最多几刻。 */
    public static final int MIN_TICKS = 10;
    public static final int MAX_TICKS = 60;

    private Ballistics() {
    }

    /** 水平飞 {@code horizontal} 格、按 {@code speed}（格/刻）的水平速度要飞几刻。 */
    public static int flightTicks(double horizontal, double speed) {
        int ticks = (int) Math.ceil(horizontal / Math.max(0.05, speed));
        return Math.max(MIN_TICKS, Math.min(MAX_TICKS, ticks));
    }

    /**
     * 飞 {@code ticks} 刻正好到达 (dx, dy, dz) 的初速度：{@code p(n) = n·v0 − g·n(n+1)/2}，所以
     * {@code v0 = (Δ + g·n(n+1)/2) / n}。
     *
     * @return {x, y, z}
     */
    public static double[] launchVelocity(double dx, double dy, double dz, int ticks, double gravity) {
        int n = Math.max(1, ticks);
        double drop = gravity * n * (n + 1) / 2.0;
        return new double[] {dx / n, (dy + drop) / n, dz / n};
    }

    /** 按 {@link #launchVelocity} 射出去，第 {@code ticks} 刻时的位移（测试用）。 */
    public static double[] displacement(double vx, double vy, double vz, int ticks, double gravity) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (int i = 0; i < ticks; i++) {
            vy -= gravity;
            x += vx;
            y += vy;
            z += vz;
        }
        return new double[] {x, y, z};
    }
}
