package io.github.sweetcornna.universe.core.vehicle;

/**
 * 车一类载具的驾驶手感（ADR-0017、ADR-0026）：油门、刹车、滑行和转向的一组数。探索车、悬浮车、重型车用同一套算法，只是数不同。
 * 只管沿车头方向的速度和每刻转多少度，碰撞、爬台阶、重力和悬浮交给实体。
 *
 * @param maxSpeed      最快多少格/刻
 * @param maxReverse    倒车最快多少格/刻
 * @param acceleration  踩油门每刻加速多少
 * @param brake         反着踩每刻减速多少
 * @param coast         松开油门每刻滑行减速多少
 * @param airborne      离地时油门和方向盘只剩这么多
 * @param maxTurn       方向盘打满每刻转多少度
 * @param turnSpeed     速度到这么快以后方向才转得满
 * @param highSpeedTurn 最快时方向盘只剩这么多
 */
public record WheelDrive(double maxSpeed, double maxReverse, double acceleration, double brake, double coast, double airborne,
        float maxTurn, double turnSpeed, double highSpeedTurn) {
    /** 探索车：12 格/秒，两秒左右到最快（数写在 {@link RoverDrive}）。 */
    public static final WheelDrive ROVER = new WheelDrive(RoverDrive.MAX_SPEED, RoverDrive.MAX_REVERSE, RoverDrive.ACCELERATION,
            RoverDrive.BRAKE, RoverDrive.COAST, RoverDrive.AIRBORNE, RoverDrive.MAX_TURN, RoverDrive.TURN_SPEED, RoverDrive.HIGH_SPEED_TURN);
    /**
     * 悬浮车：探索车的 1.4 倍（17 格/秒）。离地悬着，地面抓不住：滑行减速只有一半，刹车也软一些，高速时方向更重。
     * "离地"指离开悬浮的高度，在悬浮高度上算着地。
     */
    public static final WheelDrive HOVER = new WheelDrive(RoverDrive.MAX_SPEED * VehicleKind.HOVER_CAR_SPEED, 0.25, 0.02, 0.035, 0.006, 0.1, 4.0F, 0.12, 0.45);
    /** 重型车：探索车的 0.7 倍（8.4 格/秒），起步慢、刹得住，方向盘灵一点（八个轮子，转弯半径不大）。 */
    public static final WheelDrive HEAVY = new WheelDrive(RoverDrive.MAX_SPEED * VehicleKind.HEAVY_ROVER_SPEED, 0.18, 0.009, 0.06, 0.016, 0.15, 4.0F, 0.08, 0.75);

    /**
     * 下一刻沿车头方向的速度。
     *
     * @param speed    现在的速度（格/刻，倒车为负）
     * @param throttle 油门，-1 到 1（W 为正）
     * @param powered  有燃料、有人驾驶
     * @param grounded 着地（悬浮车是在悬浮高度上）
     */
    public double speed(double speed, double throttle, boolean powered, boolean grounded) {
        double grip = grounded ? 1 : this.airborne;
        double input = powered ? clamp(throttle, -1, 1) : 0;
        if (input != 0 && Math.signum(input) != Math.signum(speed) && Math.abs(speed) > 1e-6) {
            // 反着踩先刹车，刹停了才往反方向走
            return towardZero(speed, this.brake * Math.abs(input) * grip);
        }
        double top = input > 0 ? this.maxSpeed * input : this.maxReverse * input;
        if (input == 0 || Math.abs(speed) > Math.abs(top)) {
            // 松开油门，或者比这一档快（下坡冲快了、油门只踩了一半）：滑行减速
            return towardZero(speed, this.coast * grip);
        }
        double gain = this.acceleration * (input > 0 ? 1 : 0.6) * input * grip;
        return input > 0 ? Math.min(speed + gain, top) : Math.max(speed + gain, top);
    }

    /**
     * 这一刻转多少度（正数向右转，和 Minecraft 的偏航角同向）。停着不能原地打转，倒车时方向反过来。
     *
     * @param speed 这一刻的速度（格/刻）
     * @param steer 方向盘，-1 到 1（D 为正）
     */
    public float turn(double speed, double steer, boolean grounded) {
        double s = Math.abs(speed);
        double rate = this.maxTurn * Math.min(1, s / this.turnSpeed) * (1 - (1 - this.highSpeedTurn) * Math.min(1, s / this.maxSpeed));
        if (!grounded) {
            rate *= this.airborne;
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
