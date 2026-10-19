package io.github.sweetcornna.universe.core.vehicle;

/**
 * 探索车的驾驶（ADR-0017）：油门、刹车、滑行和转向，按刻算。只管沿车头方向的速度和每刻转多少度，碰撞、爬台阶和重力交给实体。
 * <p>
 * 像车不像飞船：A、D 是打方向盘，不是平移；速度越快方向盘越"重"，停着不能原地打转，倒车时方向反过来。
 */
public final class RoverDrive {
    /** 最快多少格/刻（12 格/秒）。 */
    public static final double MAX_SPEED = 0.6;
    /** 倒车最快多少格/刻。 */
    public static final double MAX_REVERSE = 0.22;
    /** 踩油门每刻加速多少（格/刻）：两秒左右到最快。 */
    public static final double ACCELERATION = 0.016;
    /** 反着踩：刹车每刻减速多少。 */
    public static final double BRAKE = 0.05;
    /** 松开油门：每刻滑行减速多少。 */
    public static final double COAST = 0.012;
    /** 离地（在空中、在水里漂着）时油门和方向盘只剩这么多。 */
    public static final double AIRBORNE = 0.15;
    /** 方向盘打满时每刻转多少度。 */
    public static final float MAX_TURN = 4.5F;
    /** 速度到这么快（格/刻）以后方向才转得满；停着不能原地打转。 */
    public static final double TURN_SPEED = 0.1;
    /** 最快时方向盘只剩这么多。 */
    public static final double HIGH_SPEED_TURN = 0.6;

    private RoverDrive() {
    }

    /**
     * 下一刻沿车头方向的速度。
     *
     * @param speed    现在的速度（格/刻，倒车为负）
     * @param throttle 油门，-1 到 1（W 为正）
     * @param powered  有燃料、有人驾驶
     * @param grounded 轮子着地
     */
    public static double speed(double speed, double throttle, boolean powered, boolean grounded) {
        double grip = grounded ? 1 : AIRBORNE;
        double input = powered ? clamp(throttle, -1, 1) : 0;
        if (input != 0 && Math.signum(input) != Math.signum(speed) && Math.abs(speed) > 1e-6) {
            // 反着踩先刹车，刹停了才往反方向走
            return towardZero(speed, BRAKE * Math.abs(input) * grip);
        }
        double top = input > 0 ? MAX_SPEED * input : MAX_REVERSE * input;
        if (input == 0 || Math.abs(speed) > Math.abs(top)) {
            // 松开油门，或者比这一档快（下坡冲快了、油门只踩了一半）：滑行减速
            return towardZero(speed, COAST * grip);
        }
        double gain = ACCELERATION * (input > 0 ? 1 : 0.6) * input * grip;
        return input > 0 ? Math.min(speed + gain, top) : Math.max(speed + gain, top);
    }

    /**
     * 这一刻转多少度（正数向右转，和 Minecraft 的偏航角同向）。
     *
     * @param speed 这一刻的速度（格/刻）
     * @param steer 方向盘，-1 到 1（D 为正）
     */
    public static float turn(double speed, double steer, boolean grounded) {
        double s = Math.abs(speed);
        double rate = MAX_TURN * Math.min(1, s / TURN_SPEED) * (1 - (1 - HIGH_SPEED_TURN) * Math.min(1, s / MAX_SPEED));
        if (!grounded) {
            rate *= AIRBORNE;
        }
        return (float) (rate * clamp(steer, -1, 1) * Math.signum(speed));
    }

    private static double towardZero(double value, double amount) {
        // 减到很小就当停住了，免得浮点的零头让"反着踩"变成直接倒车
        double next = value > 0 ? value - amount : value + amount;
        return Math.signum(next) != Math.signum(value) || Math.abs(next) < 1e-9 ? 0 : next;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
