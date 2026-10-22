package io.github.sweetcornna.universe.core.vehicle;

/**
 * 潜艇的驾驶（ADR-0026）：W、S 是推进器，艇头慢慢转向驾驶员看的方向（抬头上浮、低头下潜），A、D 是侧推。
 * 只在水里推得动；离了水只会往下掉，速度很快就没了。碰撞、浮出水面的限制交给实体。
 */
public final class SubmarineDrive {
    /** 水下最快多少格/刻：探索车的 1.6 倍（19 格/秒）。 */
    public static final double MAX_SPEED = RoverDrive.MAX_SPEED * 1.6;
    /** 倒车最快多少格/刻。 */
    public static final double MAX_REVERSE = 0.3;
    /** 推进器每刻加速多少：三秒左右到最快。 */
    public static final double ACCELERATION = 0.016;
    /** 反着推：每刻减速多少。 */
    public static final double BRAKE = 0.05;
    /** 松开推进器：水的阻力每刻减速多少。 */
    public static final double DRAG = 0.01;
    /** 侧推的速度（格/刻）。 */
    public static final double STRAFE = 0.12;
    /** 艇头每刻最多转多少度：停着时只有一半。 */
    public static final float MAX_TURN = 4.0F;
    /** 艇头最多抬起、压下多少度。 */
    public static final float MAX_PITCH = 60;
    /** 离了水，每刻速度剩多少。 */
    public static final double BEACHED = 0.8;

    private SubmarineDrive() {
    }

    /**
     * 下一刻沿艇头方向的速度。
     *
     * @param speed     现在的速度（格/刻，倒车为负）
     * @param throttle  推进器，-1 到 1（W 为正）
     * @param powered   有燃料、有人驾驶
     * @param submerged 艇身在水里
     */
    public static double speed(double speed, double throttle, boolean powered, boolean submerged) {
        if (!submerged) {
            double next = speed * BEACHED;
            return Math.abs(next) < 1e-3 ? 0 : next;
        }
        double input = powered ? Math.max(-1, Math.min(1, throttle)) : 0;
        if (input != 0 && Math.signum(input) != Math.signum(speed) && Math.abs(speed) > 1e-6) {
            return towardZero(speed, BRAKE * Math.abs(input));
        }
        double top = input > 0 ? MAX_SPEED * input : MAX_REVERSE * input;
        if (input == 0 || Math.abs(speed) > Math.abs(top)) {
            return towardZero(speed, DRAG);
        }
        double gain = ACCELERATION * input * (input > 0 ? 1 : 0.6);
        return input > 0 ? Math.min(speed + gain, top) : Math.max(speed + gain, top);
    }

    /** 从 {@code current} 往 {@code target} 转，最多转 {@code maxStep} 度，走近的那一边（角度按 360° 绕回）。 */
    public static float turnToward(float current, float target, float maxStep) {
        float delta = wrap(target - current);
        if (Math.abs(delta) <= maxStep) {
            return current + delta;
        }
        return current + Math.signum(delta) * maxStep;
    }

    /** 这一刻艇头最多转几度：越快转得越灵，停着时只有一半。 */
    public static float turnRate(double speed) {
        return (float) (MAX_TURN * (0.5 + 0.5 * Math.min(1, Math.abs(speed) / (MAX_SPEED * 0.5))));
    }

    /** 驾驶员的俯仰角限在 ±{@link #MAX_PITCH} 以内。 */
    public static float clampPitch(float pitch) {
        return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    /**
     * 沿艇头方向走 {@code speed}、再加上侧推的速度 {x, y, z}（Minecraft 的坐标：偏航 0 朝 +z，俯仰正数朝下）。
     *
     * @param strafe 侧推，-1 到 1（A 为正，向艇的左边）
     */
    public static double[] velocity(float yaw, float pitch, double speed, double strafe) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        double cos = Math.cos(p);
        double fx = -Math.sin(y) * cos;
        double fy = -Math.sin(p);
        double fz = Math.cos(y) * cos;
        // 左边：朝向转 -90°
        double lx = Math.cos(y);
        double lz = Math.sin(y);
        double side = Math.max(-1, Math.min(1, strafe)) * STRAFE;
        return new double[] {fx * speed + lx * side, fy * speed, fz * speed + lz * side};
    }

    static float wrap(float degrees) {
        float d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }

    private static double towardZero(double value, double amount) {
        double next = value > 0 ? value - amount : value + amount;
        return Math.signum(next) != Math.signum(value) || Math.abs(next) < 1e-9 ? 0 : next;
    }
}
