package io.github.sweetcornna.universe.core.gravity;

/**
 * 引力抓取器（ADR-0034 §七）的规则：抓得多远、抓什么、悬在哪、扔多快、撞上去多疼、推开哪些。
 *
 * <p>按住右键抓准星对着的东西（16 格内），悬在面前 3 格，跟着视线走；松开轻轻放下；抓着时左键扔出去，每刻 3 格。
 * 扔出去的东西撞到别的生物按速度和重量伤它；潜行左键把前方 8 格锥形里没固定的东西全推开。
 */
public final class GravityRules {
    /** 抓得到多远（格）。 */
    public static final double REACH = 16;
    /** 抓着的东西悬在眼前多远（格）。 */
    public static final double HOLD_DISTANCE = 3;
    /** 个头（宽、高里大的那个）不超过这么大的生物才抓得动（格）。 */
    public static final double MAX_SIZE = 1.5;
    /** 扔出去的速度（格/刻）。 */
    public static final double THROW_SPEED = 3;
    /** 推的范围（格）和锥形的半角。 */
    public static final double PUSH_RANGE = 8;
    public static final double PUSH_HALF_ANGLE = Math.toRadians(35);
    /** 被扔的守卫机晕多久（刻）。 */
    public static final int SENTINEL_STUN = 60;
    /** 速度低于这个（格/刻）就不算撞，扔出去的东西也到此为止。 */
    public static final double IMPACT_SPEED = 0.45;
    /** 悬着的东西跟过去的快慢：每刻补上差距的几成、最快多少。 */
    static final double FOLLOW = 0.45;
    static final double MAX_FOLLOW_SPEED = 1.8;
    /** 推出去的速度：贴着最快，到边上剩这么多。 */
    static final double PUSH_NEAR = 2.2;
    static final double PUSH_FAR = 0.5;

    private GravityRules() {
    }

    /** 抓着的东西按重量分几档，撞上去的伤害不同。 */
    public enum Weight {
        /** 掉落物。 */
        ITEM(2),
        /** 残骸的小块碎片。 */
        DEBRIS(4),
        /** 小生物。 */
        CREATURE(6),
        /** 守卫机的无人机、黑雾的突袭者。 */
        DRONE(10);

        private final double damage;

        Weight(double damage) {
            this.damage = damage;
        }

        /** 用 {@link #THROW_SPEED} 扔出去正面撞上的伤害。 */
        public double damage() {
            return this.damage;
        }
    }

    /** 宽 {@code width}、高 {@code height} 的生物抓不抓得动。 */
    public static boolean liftable(double width, double height) {
        return Math.max(width, height) <= MAX_SIZE + 1e-6;
    }

    /** 抓着的东西该在哪：眼睛往视线方向 {@link #HOLD_DISTANCE} 格。{@code look} 是单位向量。 */
    public static double[] holdPoint(double[] eye, double[] look) {
        return new double[] {eye[0] + look[0] * HOLD_DISTANCE, eye[1] + look[1] * HOLD_DISTANCE, eye[2] + look[2] * HOLD_DISTANCE};
    }

    /**
     * 悬着的东西这一刻的速度：往该在的点补上差距的一部分，快了夹住。不让东西一下子瞬移过去，也不晃。
     *
     * @return {x, y, z}
     */
    public static double[] follow(double[] position, double[] target) {
        double dx = (target[0] - position[0]) * FOLLOW;
        double dy = (target[1] - position[1]) * FOLLOW;
        double dz = (target[2] - position[2]) * FOLLOW;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed > MAX_FOLLOW_SPEED) {
            double k = MAX_FOLLOW_SPEED / speed;
            dx *= k;
            dy *= k;
            dz *= k;
        }
        return new double[] {dx, dy, dz};
    }

    /** 以 {@code speed} 格/刻撞上去的伤害：按扔出去的速度的比例算，最多一倍半；太慢不算。 */
    public static double impact(Weight weight, double speed) {
        if (speed < IMPACT_SPEED) {
            return 0;
        }
        return weight.damage() * Math.min(1.5, speed / THROW_SPEED);
    }

    /** {@code point} 在不在推的锥形里：从 {@code origin} 往 {@code look}（单位向量）方向 {@link #PUSH_RANGE} 格内、半角以内。 */
    public static boolean inCone(double[] origin, double[] look, double[] point) {
        double dx = point[0] - origin[0];
        double dy = point[1] - origin[1];
        double dz = point[2] - origin[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > PUSH_RANGE || distance < 1e-6) {
            return distance < 1e-6;
        }
        double cos = (dx * look[0] + dy * look[1] + dz * look[2]) / distance;
        return cos >= Math.cos(PUSH_HALF_ANGLE);
    }

    /** 离 {@code distance} 格的东西被推出去的速度（格/刻）。 */
    public static double pushSpeed(double distance) {
        double t = Math.clamp(distance / PUSH_RANGE, 0, 1);
        return PUSH_NEAR + (PUSH_FAR - PUSH_NEAR) * t;
    }
}
