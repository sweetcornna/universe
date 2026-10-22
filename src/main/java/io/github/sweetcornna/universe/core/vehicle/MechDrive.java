package io.github.sweetcornna.universe.core.vehicle;

/**
 * 机甲的驾驶（ADR-0026）：两条腿走，身子慢慢转向驾驶员看的方向；W、S 前后走，A、D 横着挪。按住跳跃开背上的喷气，
 * 往上推、往前冲，喷气用完要落地歇一会儿才回满。腿长，跨得上 {@link #STEP} 格的台阶（交给实体的爬台阶）。
 */
public final class MechDrive {
    /** 走路最快多少格/刻：探索车的 0.6 倍（7.2 格/秒），比人跑得快一点。 */
    public static final double WALK_SPEED = RoverDrive.MAX_SPEED * VehicleKind.EXO_MECH_SPEED;
    /** 喷气时水平最快多少格/刻：探索车的 1.2 倍。 */
    public static final double JET_SPEED = RoverDrive.MAX_SPEED * 1.2;
    /** 横着挪是往前走的几成。 */
    public static final double STRAFE = 0.6;
    /** 迈步加速：每刻往目标速度靠多少（格/刻）。 */
    static final double STRIDE = 0.05;
    /** 不按方向键：每刻减速多少。 */
    static final double HALT = 0.08;
    /** 在空中（没喷气）时方向键只剩这么多。 */
    static final double AIRBORNE = 0.2;
    /** 身子每刻最多转多少度。 */
    public static final float TURN = 5.0F;
    /** 跨得上多高的台阶（格）。 */
    public static final float STEP = 2.05F;
    /** 每刻的重力（乘上星球的表面重力）。 */
    public static final double GRAVITY = 0.08;
    /** 喷气每刻往上推多少（格/刻²），不算重力。 */
    public static final double JET_THRUST = 0.13;
    /** 喷气往上最快多少格/刻。 */
    public static final double MAX_CLIMB = 0.45;
    /** 摔下来最快多少格/刻。 */
    static final double MAX_FALL = 1.5;

    private MechDrive() {
    }

    /**
     * 下一刻的水平速度 {x, z}。
     *
     * @param vx       现在的 x 速度
     * @param vz       现在的 z 速度
     * @param yaw      身子的偏航角（度，0 朝 +z）
     * @param forward  前后，-1 到 1（W 为正）
     * @param strafe   左右，-1 到 1（A 为正，往左）
     * @param powered  有燃料、有人驾驶
     * @param grounded 脚踩着地
     * @param jetting  喷气开着
     */
    public static double[] walk(double vx, double vz, float yaw, double forward, double strafe, boolean powered, boolean grounded,
            boolean jetting) {
        double top = jetting ? JET_SPEED : WALK_SPEED;
        double f = powered ? clamp(forward) : 0;
        double s = powered ? clamp(strafe) * STRAFE : 0;
        double r = Math.toRadians(yaw);
        // 前方 (-sin, cos)，左边 (cos, sin)
        double tx = -Math.sin(r) * f + Math.cos(r) * s;
        double tz = Math.cos(r) * f + Math.sin(r) * s;
        double length = Math.sqrt(tx * tx + tz * tz);
        if (length > 1) {
            tx /= length;
            tz /= length;
        }
        double grip = grounded || jetting ? 1 : AIRBORNE;
        double step = (length < 1e-6 ? HALT : STRIDE) * grip;
        return new double[] {approach(vx, tx * top, step), approach(vz, tz * top, step)};
    }

    /**
     * 下一刻的上下速度。
     *
     * @param vy      现在的上下速度
     * @param thrust  这一刻喷气推着
     * @param gravity 星球的表面重力倍数
     */
    public static double climb(double vy, boolean thrust, double gravity) {
        double g = GRAVITY * gravity;
        if (thrust) {
            return Math.min(MAX_CLIMB, vy + JET_THRUST - g);
        }
        return Math.max(-MAX_FALL, (vy - g) * 0.98);
    }

    /** 身子从 {@code body} 转向 {@code look}，每刻最多 {@link #TURN} 度。 */
    public static float turn(float body, float look) {
        return SubmarineDrive.turnToward(body, look, TURN);
    }

    private static double approach(double value, double target, double step) {
        double delta = target - value;
        return Math.abs(delta) <= step ? target : value + Math.signum(delta) * step;
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    /**
     * 喷气的储量：按住跳跃时每刻用 1，最多 {@link #MAX} 刻（3 秒）；脚踩着地、没在喷时每刻回 0.5。
     * 用空了要歇到一半才能再喷，免得在地上一点一点地蹦。
     */
    public static final class Jet {
        public static final int MAX = 60;
        /** 用空以后回到多少才能再喷。 */
        public static final int RESTART = MAX / 2;
        private double fuel = MAX;
        private boolean exhausted;

        /**
         * 推进一刻。
         *
         * @param wanted   驾驶员按着跳跃、有燃料
         * @param grounded 脚踩着地
         * @return 这一刻喷气推着没有
         */
        public boolean tick(boolean wanted, boolean grounded) {
            if (this.exhausted && this.fuel >= RESTART) {
                this.exhausted = false;
            }
            if (wanted && !this.exhausted && this.fuel > 0) {
                this.fuel = Math.max(0, this.fuel - 1);
                if (this.fuel == 0) {
                    this.exhausted = true;
                }
                return true;
            }
            if (grounded) {
                this.fuel = Math.min(MAX, this.fuel + 0.5);
            }
            return false;
        }

        /** 剩多少，0 到 1。 */
        public double share() {
            return this.fuel / MAX;
        }

        public boolean exhausted() {
            return this.exhausted;
        }
    }
}
