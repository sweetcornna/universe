package io.github.sweetcornna.universe.core.ship;

/**
 * 街机式的飞行模型（玩法设计 §5.2：不做真实的牛顿力学）。
 *
 * <p>每刻按操作算出"想要的速度"，实际速度以有限的加速度向它靠拢：松开按键时有惯性阻尼，飞船会慢慢停下；
 * 没有垂直输入时自动悬停。推重比决定能不能起飞、爬升多快；没燃料时推进器熄火，飞船按重力下坠。
 *
 * <p>单位：长度是格，时间是刻；朝向和原版实体一致（偏航 0 朝南 +z，增大是向右转）。
 */
public final class ShipFlight {
    /** 1 g 下的重力加速度（格/刻²）。比原版实体的 0.08 温和，飞船才好操纵。 */
    public static final double GRAVITY = 0.05;
    /** 一个推进器满负荷时每刻消耗的燃料。 */
    public static final double FUEL_PER_THRUSTER = 0.05;
    /** 一台脉冲引擎加速时每刻消耗的燃料。 */
    public static final double FUEL_PER_IMPULSE = 0.8;
    /** 太空里开脉冲引擎能到的最高速度（格/刻）。 */
    public static final double MAX_IMPULSE_SPEED = ShipStats.IMPULSE_SPEED;
    /** 装 Mk.II 脉冲引擎时的最高速度（ADR-0017）。 */
    public static final double MAX_IMPULSE_SPEED_MK2 = ShipStats.IMPULSE_SPEED_MK2;

    private double vx;
    private double vy;
    private double vz;
    private float yaw;
    private float yawRate;
    private double throttle;

    /**
     * 驾驶员的操作。
     *
     * @param boost 加力：大气里加大推力，太空里开脉冲引擎
     */
    public record Controls(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down, boolean boost) {
        public static final Controls NONE = new Controls(false, false, false, false, false, false, false);

        boolean moving() {
            return this.forward != this.back || this.left != this.right || this.up != this.down;
        }
    }

    /**
     * 飞船所处的环境。
     *
     * @param gravity 重力（g）
     * @param density 大气密度（母星 1，真空 0）
     * @param space   在太空维度里
     */
    public record Environment(double gravity, double density, boolean space) {
        public static final Environment SPACE = new Environment(0, 0, true);
        public static final Environment HOMEWORLD = new Environment(1, 1, false);
    }

    public ShipFlight(float yaw) {
        this.yaw = yaw;
    }

    /**
     * 推进一刻，返回这一刻消耗的燃料。算出的速度就是这一刻想移动的距离，碰撞由调用方处理（见 {@link #collided}）。
     *
     * @param hasFuel  还有燃料（创造模式视为一直有）
     * @param onGround 停在地面上
     */
    public double step(ShipStats stats, Controls controls, Environment env, boolean hasFuel, boolean onGround) {
        boolean powered = hasFuel && stats.thrusters() > 0;
        double g = env.space() ? 0 : GRAVITY * env.gravity();
        double twr = stats.thrustToWeight(env.space() ? 0 : env.gravity());
        // 推力和质量之比（在 1 g 下的推重比），决定加速和转向的灵活程度
        double power = stats.thrust() / Math.max(1, stats.mass());
        double agility = clamp(power / 2, 0, 1);
        boolean boostImpulse = controls.boost() && env.space() && stats.impulse() > 0;

        // ---- 水平
        double vmax;
        double accel;
        // 脉冲引擎关掉以后反推减速的加速度：两三秒内从巡航速度降下来，不会一路漂出去上千格
        double brake = 0;
        if (env.space()) {
            // 一台脉冲引擎能推满两百吨（Mk.II 三百吨），见 ShipStats#impulseSpeed
            double impulseMax = stats.impulseSpeed();
            if (boostImpulse) {
                vmax = impulseMax;
                accel = vmax / 60;
            } else {
                vmax = 2.5 * clamp(power, 0.3, 2);
                accel = 0.02 + 0.04 * agility;
                brake = impulseMax / 30;
            }
        } else {
            vmax = (0.6 + 1.4 * agility) * (controls.boost() ? 1.7 : 1) / (1 + 0.3 * Math.max(0, env.density() - 1));
            accel = (0.015 + 0.035 * agility) * (controls.boost() ? 1.5 : 1);
        }
        double fx = -Math.sin(Math.toRadians(this.yaw));
        double fz = Math.cos(Math.toRadians(this.yaw));
        double hx;
        double hz;
        if (powered) {
            double drive = (controls.forward() ? 1 : 0) - (controls.back() ? 0.5 : 0);
            double tx = fx * drive * vmax;
            double tz = fz * drive * vmax;
            double dx = tx - this.vx;
            double dz = tz - this.vz;
            double d = Math.sqrt(dx * dx + dz * dz);
            // 太空里松开按键也慢慢停下（惯性阻尼），但减速比加速慢
            double limit = drive == 0 ? accel * 0.6 : accel;
            if (brake > limit && this.vx * this.vx + this.vz * this.vz > vmax * vmax) {
                limit = brake;
            }
            if (d > limit) {
                dx *= limit / d;
                dz *= limit / d;
            }
            hx = this.vx + dx;
            hz = this.vz + dz;
        } else {
            double drag = onGround ? 0.4 : env.space() ? 0.002 : 0.02 + 0.02 * env.density();
            hx = this.vx * (1 - drag);
            hz = this.vz * (1 - drag);
        }
        this.vx = hx;
        this.vz = hz;

        // ---- 垂直
        if (powered) {
            double climb;
            double up;
            double down;
            if (env.space()) {
                climb = Math.max(0.8, vmax * 0.5);
                up = accel;
                down = accel;
            } else {
                climb = clamp(twr - 1, 0, 1.0);
                up = Math.max(0, Math.min(0.06, g * (twr - 1)));
                down = g + 0.02;
            }
            double target;
            if (controls.up() && !controls.down()) {
                target = climb;
            } else if (controls.down() && !controls.up()) {
                target = -Math.max(0.6, climb * 1.2);
            } else if (env.space() || twr >= 1) {
                // 悬停
                target = 0;
            } else {
                // 推力不够：减缓下坠
                target = -3;
                down = g * (1 - twr);
            }
            double dy = target - this.vy;
            this.vy += dy > 0 ? Math.min(dy, up) : Math.max(dy, -down);
        } else {
            this.vy -= g;
            this.vy *= 1 - 0.01 * env.density();
        }
        this.vy = clamp(this.vy, -4, env.space() ? MAX_IMPULSE_SPEED_MK2 : 2);
        if (onGround && this.vy < 0) {
            this.vy = 0;
        }

        // ---- 偏航
        double maxRate = clamp(3.0 * clamp(power, 0.3, 1.5) / Math.sqrt(Math.max(1, stats.mass() / 40)), 0.6, 4);
        double turn = powered ? ((controls.right() ? 1 : 0) - (controls.left() ? 1 : 0)) * maxRate : 0;
        double rateAccel = powered ? maxRate * 0.2 : 0.3;
        double dr = turn - this.yawRate;
        this.yawRate += (float) clamp(dr, -rateAccel, rateAccel);
        if (onGround && !powered) {
            this.yawRate = 0;
        }
        this.yaw = wrap(this.yaw + this.yawRate);

        // ---- 燃料：悬停按推重比的倒数付出，加上操纵的部分
        if (!powered) {
            this.throttle = 0;
            return 0;
        }
        double hover = !env.space() && !onGround && twr > 0 ? Math.min(1, 1 / twr) : 0;
        double steer = controls.moving() ? 0.5 : 0;
        this.throttle = clamp(hover + steer * (controls.boost() ? 1.6 : 1), 0, 1.6);
        double fuel = FUEL_PER_THRUSTER * stats.thrusters() * this.throttle;
        if (boostImpulse && controls.forward()) {
            fuel += FUEL_PER_IMPULSE * stats.impulse();
        }
        return fuel;
    }

    /** 碰撞后把被挡住方向上的速度清零。 */
    public void collided(boolean x, boolean y, boolean z) {
        if (x) {
            this.vx = 0;
        }
        if (y) {
            this.vy = 0;
        }
        if (z) {
            this.vz = 0;
        }
    }

    /** 转向被挡住（转过去会撞上东西）时撤销这一刻的偏航。 */
    public void undoYaw() {
        this.yaw = wrap(this.yaw - this.yawRate);
        this.yawRate = 0;
    }

    public double vx() {
        return this.vx;
    }

    public double vy() {
        return this.vy;
    }

    public double vz() {
        return this.vz;
    }

    public double speed() {
        return Math.sqrt(this.vx * this.vx + this.vy * this.vy + this.vz * this.vz);
    }

    public float yaw() {
        return this.yaw;
    }

    public float yawRate() {
        return this.yawRate;
    }

    /** 推进器的输出（0–1.6），用于画尾焰和算油耗。 */
    public double throttle() {
        return this.throttle;
    }

    /** 读档、跨维度后恢复状态。 */
    public void restore(double vx, double vy, double vz, float yaw) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.yaw = wrap(yaw);
        this.yawRate = 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360;
        return d < 0 ? d + 360 : d;
    }
}
