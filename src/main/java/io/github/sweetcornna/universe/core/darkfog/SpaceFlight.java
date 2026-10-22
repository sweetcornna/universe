package io.github.sweetcornna.universe.core.darkfog;

/**
 * 太空单位的飞法（ADR-0028 §八）：三维自由飞，不受重力，高度不设限。速度靠有限的加速度改，所以转的是圈，不会原地掉头。
 * 向量都是 {x, y, z} 的 double 数组，单位是格、格/刻。
 */
public final class SpaceFlight {
    private static final double[] UP = {0, 1, 0};
    private static final double EPSILON = 1e-9;

    private SpaceFlight() {
    }

    /** 速度从 {@code vel} 朝 {@code wanted} 改，一刻最多改 {@code accel}。 */
    public static double[] approach(double[] vel, double[] wanted, double accel) {
        double[] delta = sub(wanted, vel);
        double d = length(delta);
        if (d <= accel) {
            return wanted.clone();
        }
        return add(vel, scale(delta, accel / d));
    }

    /** 去 {@code goal} 想要的速度：远处全速，进了 {@code slow} 格以内按距离线性减下来，到了停住。 */
    public static double[] seek(double[] pos, double[] goal, double speed, double slow) {
        double[] to = sub(goal, pos);
        double d = length(to);
        if (d < EPSILON) {
            return new double[3];
        }
        double s = slow > 0 && d < slow ? speed * d / slow : speed;
        return scale(to, s / d);
    }

    /** 绕着 {@code center} 巡逻的点：水平半径 {@code radius} 的圈上 {@code angle}（弧度）那里，高度跟着角度上下起伏 {@code lift}。 */
    public static double[] orbitPoint(double[] center, double radius, double angle, double lift) {
        return new double[] {center[0] + Math.cos(angle) * radius, center[1] + Math.sin(angle * 2) * lift, center[2] + Math.sin(angle) * radius};
    }

    /**
     * 隔着一段距离绕着目标打（座头鲸）想要的速度：比 {@code near} 近就往外退，比 {@code far} 远就往里靠，中间横着绕；
     * {@code side} 是绕的方向（1 或 -1）。
     */
    public static double[] standoff(double[] pos, double[] target, double near, double far, double speed, double side) {
        double[] to = sub(target, pos);
        double d = length(to);
        if (d < EPSILON) {
            return new double[] {speed, 0, 0};
        }
        double[] n = scale(to, 1 / d);
        double[] tangent = cross(n, UP);
        if (length(tangent) < 1e-3) {
            // 正上方、正下方：随便取一个水平方向绕
            tangent = new double[] {1, 0, 0};
        }
        tangent = scale(normalize(tangent), side);
        double radial;
        if (d > far) {
            radial = 1;
        } else if (d < near) {
            radial = -1;
        } else {
            double middle = (near + far) / 2;
            radial = (d - middle) / Math.max(EPSILON, (far - near) / 2) * 0.3;
        }
        double[] wanted = add(scale(n, radial), scale(tangent, 1 - Math.abs(radial) * 0.5));
        return scale(normalize(wanted), speed);
    }

    /** 机头朝 {@code v} 时的偏航角（度，原版的约定：0 朝南，顺时针为正）。 */
    public static float yaw(double[] v) {
        return (float) Math.toDegrees(Math.atan2(-v[0], v[2]));
    }

    /** 机头朝 {@code v} 时的俯仰角（度，往下为正）。 */
    public static float pitch(double[] v) {
        return (float) -Math.toDegrees(Math.atan2(v[1], Math.hypot(v[0], v[2])));
    }

    /** 机头 {@code forward} 和去目标的方向 {@code to} 夹角在 {@code degrees} 以内。 */
    public static boolean inCone(double[] forward, double[] to, double degrees) {
        double a = length(forward);
        double b = length(to);
        if (a < EPSILON || b < EPSILON) {
            return false;
        }
        double cos = dot(forward, to) / (a * b);
        return cos >= Math.cos(Math.toRadians(degrees));
    }

    public static double distance(double[] a, double[] b) {
        return length(sub(a, b));
    }

    public static double length(double[] v) {
        return Math.sqrt(dot(v, v));
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double[] scale(double[] v, double f) {
        return new double[] {v[0] * f, v[1] * f, v[2] * f};
    }

    public static double[] normalize(double[] v) {
        double d = length(v);
        return d < EPSILON ? new double[3] : scale(v, 1 / d);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
