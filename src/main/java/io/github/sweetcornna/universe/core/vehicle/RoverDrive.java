package io.github.sweetcornna.universe.core.vehicle;

/**
 * 探索车的驾驶（ADR-0017）：油门、刹车、滑行和转向，按刻算。只管沿车头方向的速度和每刻转多少度，碰撞、爬台阶和重力交给实体。
 * <p>
 * 像车不像飞船：A、D 是打方向盘，不是平移；速度越快方向盘越"重"，停着不能原地打转，倒车时方向反过来。
 * 算法在 {@link WheelDrive}，这里是探索车的那组数（{@link WheelDrive#ROVER}）。
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
        return WheelDrive.ROVER.speed(speed, throttle, powered, grounded);
    }

    /**
     * 这一刻转多少度（正数向右转，和 Minecraft 的偏航角同向）。
     *
     * @param speed 这一刻的速度（格/刻）
     * @param steer 方向盘，-1 到 1（D 为正）
     */
    public static float turn(double speed, double steer, boolean grounded) {
        return WheelDrive.ROVER.turn(speed, steer, grounded);
    }
}
