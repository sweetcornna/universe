package io.github.sweetcornna.universe.core.dyson;

/**
 * 在天空里画戴森球用到的几何（ADR-0016）。
 *
 * <p>戴森球自己的坐标里 y 是黄道面的法线（和太空维度一致）；星球天空的模型（{@code SkyModel}）用黄道坐标，
 * 黄道面是 xy 平面、z 是法线。星球上的世界方向和天空渲染器一致：恒星沿原版太阳的路径走，太阳角为 a 时恒星在
 * (-sin a, cos a, 0)，别的天体按相对恒星的黄经差和黄纬偏开。
 *
 * <p>天空里的恒星画得比真实大得多（约 10°），戴森球的真实视大小常常比它还小。所以按到恒星中心的角距离重新映射：
 * 0° 映射到恒星圆盘外面一点，90° 不变，中间线性，整个球总是围在恒星外面。
 */
public final class DysonSky {
    private DysonSky() {
    }

    /** 戴森球坐标（y 是法线）换成黄道坐标（z 是法线），是一个旋转，不会镜像。 */
    public static double[] ecliptic(double[] dyson) {
        return new double[] {dyson[0], -dyson[2], dyson[1]};
    }

    /**
     * 星球上看黄道坐标里的点 point（以恒星为原点），世界里的单位方向。
     *
     * @param here     观察者的位置（黄道坐标）
     * @param sunAngle 太阳角（弧度，0 是正午）
     */
    public static double[] skyDirection(double[] here, double[] point, double sunAngle) {
        double[] toStar = {-here[0], -here[1], -here[2]};
        double[] toPoint = {point[0] - here[0], point[1] - here[1], point[2] - here[2]};
        double longitude = Math.atan2(toStar[0] * toPoint[1] - toStar[1] * toPoint[0], toStar[0] * toPoint[0] + toStar[1] * toPoint[1]);
        double latitude = Math.atan2(toPoint[2], Math.hypot(toPoint[0], toPoint[1]));
        double phi = sunAngle - longitude;
        double cosLat = Math.cos(latitude);
        return new double[] {-cosLat * Math.sin(phi), cosLat * Math.cos(phi), -Math.sin(latitude)};
    }

    /** 太阳角为 sunAngle 时恒星在世界里的方向。 */
    public static double[] starDirection(double sunAngle) {
        return new double[] {-Math.sin(sunAngle), Math.cos(sunAngle), 0};
    }

    /**
     * 把 direction 到 star 的角距离 θ 映射成 inner + θ·(90° - inner)/90°，方位不变；超过 90° 的按同样的斜率接着走，
     * 到 180° 为止。
     *
     * @param inner 角距离 0 映射到多远（弧度），一般取恒星显示半径的 1.3 倍
     */
    public static double[] remap(double[] star, double[] direction, double inner) {
        double cos = Math.max(-1, Math.min(1, dot(star, direction)));
        double theta = Math.acos(cos);
        double mapped = Math.min(Math.PI, inner + theta * (Math.PI / 2 - inner) / (Math.PI / 2));
        double[] side = {direction[0] - star[0] * cos, direction[1] - star[1] * cos, direction[2] - star[2] * cos};
        double length = Math.sqrt(dot(side, side));
        if (length < 1e-9) {
            // 正对着恒星：方位随便取一个和恒星垂直的方向
            side = Math.abs(star[2]) < 0.9 ? new double[] {-star[1], star[0], 0} : new double[] {0, -star[2], star[1]};
            length = Math.sqrt(dot(side, side));
        }
        double sin = Math.sin(mapped);
        double c = Math.cos(mapped);
        return new double[] {star[0] * c + side[0] / length * sin, star[1] * c + side[1] / length * sin, star[2] * c + side[2] / length * sin};
    }

    /** 点在球上朝着观察者的一面（以恒星为原点）。 */
    public static boolean facing(double[] point, double[] observer) {
        return dot(point, observer) > 0;
    }

    /** 单位向量 a、b 之间的球面插值。 */
    public static double[] slerp(double[] a, double[] b, double t) {
        double cos = Math.max(-1, Math.min(1, dot(a, b)));
        double angle = Math.acos(cos);
        if (angle < 1e-6) {
            return a.clone();
        }
        double sin = Math.sin(angle);
        double wa = Math.sin((1 - t) * angle) / sin;
        double wb = Math.sin(t * angle) / sin;
        return new double[] {a[0] * wa + b[0] * wb, a[1] * wa + b[1] * wb, a[2] * wa + b[2] * wb};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
