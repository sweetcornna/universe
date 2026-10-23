package io.github.sweetcornna.universe.core.power;

/**
 * 太空里的太阳能板对着恒星（ADR-0030 §五）：板子先绕竖轴转到恒星的方位，再绕南北向的转轴（和星球上追太阳的那根一样）
 * 倾斜到正对恒星。恒星在太空维度的原点，太空里的建筑大多在黄道面附近，所以板子差不多是竖着的。
 * 恒星在下面时最多竖到 90°，免得翻过来压到底座。
 */
public final class StarFacing {
    private StarFacing() {
    }

    /**
     * 从 (x, y, z) 看原点的恒星，板子要转的角度（度）：{yaw, tilt}。先绕 +y 转 yaw，再绕转过以后的 +z 转 tilt，
     * 板子的法线（+y）就指向恒星；和星球上一样，tilt 为正时法线朝 −x 倒。
     */
    public static double[] facing(double x, double y, double z) {
        double dx = -x;
        double dy = -y;
        double dz = -z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) {
            return new double[] {0, 0};
        }
        double horizontal = Math.hypot(dx, dz);
        double tilt = Math.toDegrees(Math.atan2(horizontal, dy));
        double yaw = horizontal < 1e-9 ? 0 : Math.toDegrees(Math.atan2(dz, -dx));
        return new double[] {yaw, Math.min(90, tilt)};
    }
}
