package io.github.sweetcornna.universe.core.hazard;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 孢子云（ADR-0034 §六）：半径 {@value #RADIUS} 格的一团，飘 60–90 秒，顺着星球的风走。云里毒性防护掉得快
 * {@value #TOXIC_FACTOR} 倍，画面发绿。刚飘来时和快散时淡一些，淡到一定程度就不算了。
 *
 * <p>风向和天气粒子用的一样（{@link #windAngle}），按星球的种子固定，风力 0–1 定飘多快；再加一点慢慢的左右摆。
 */
public final class SporeDrift {
    public static final double RADIUS = 5;
    /** 云比中心低多少、高多少还算在里面：贴着地面一层。 */
    public static final double BELOW = 2.5;
    public static final double ABOVE = 4;
    public static final int LIFE_MIN = 60 * 20;
    public static final int LIFE_MAX = 90 * 20;
    /** 飘来、散开要几刻。 */
    static final int FADE_IN = 5 * 20;
    static final int FADE_OUT = 10 * 20;
    /** 云里毒性防护掉得快几倍。 */
    public static final double TOXIC_FACTOR = 4;
    /** 淡到这个程度以下就不算了。 */
    static final double THRESHOLD = 0.35;
    /** 离玩家多远的上风处冒出来。 */
    public static final int SPAWN_MIN = 18;
    public static final int SPAWN_MAX = 30;

    private SporeDrift() {
    }

    /** 星球的风向（弧度，0 是正东 +x），和天气粒子一样。 */
    public static double windAngle(long planetSeed) {
        return (planetSeed & 0xFFFF) / 65536.0 * Math.PI * 2;
    }

    /** 飘的速度（格/刻）：无风时也慢慢挪，大风时一秒一格多。 */
    public static double speed(double wind) {
        return 0.012 + 0.05 * Math.clamp(wind, 0, 1);
    }

    /**
     * 这一刻往哪飘、多快。
     *
     * @return {@code {vx, vz}}（格/刻）
     */
    public static double[] velocity(long planetSeed, double wind, long time, long cloudSeed) {
        double angle = windAngle(planetSeed) + 0.45 * Math.sin(time / 240.0 + (cloudSeed & 0xFF));
        double speed = speed(wind);
        return new double[] {Math.cos(angle) * speed, Math.sin(angle) * speed};
    }

    /** 飘多久（刻）。 */
    public static int lifetime(Rng rng) {
        return rng.range(LIFE_MIN, LIFE_MAX);
    }

    /** 这团云现在有多浓：0 没有，1 最浓。 */
    public static double density(int age, int life) {
        if (age < 0 || age >= life) {
            return 0;
        }
        double in = Math.min(1, (age + 1) / (double) FADE_IN);
        double out = Math.min(1, (life - age) / (double) FADE_OUT);
        return Math.min(in, out);
    }

    /** 离云中心 {@code (dx, dy, dz)} 的地方在不在云里。 */
    public static boolean inside(double dx, double dy, double dz) {
        return dy >= -BELOW && dy <= ABOVE && dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    /** 这么浓的云算不算数（扣防护、画面发绿）。 */
    public static boolean thick(double density) {
        return density >= THRESHOLD;
    }

    /**
     * 在玩家上风处多远冒出来，好让风把它吹过来。
     *
     * @return {@code {dx, dz}}
     */
    public static double[] upwind(long planetSeed, Rng rng) {
        double angle = windAngle(planetSeed) + Math.PI + (rng.nextDouble() - 0.5) * 0.9;
        double distance = rng.range((double) SPAWN_MIN, (double) SPAWN_MAX);
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance};
    }
}
