package io.github.sweetcornna.universe.core.deepspace;

import java.util.ArrayList;
import java.util.List;

/**
 * 飞船外面的几个位置（ADR-0034 §四）：出舱时人放在船的左舷、右舷、顶上或底下，抛货从船尾飘出去。
 * 偏航按原版的算法：0° 朝 +z，90° 朝 −x。
 */
public final class ShipSides {
    /** 抛出去的东西离开船尾的速度（格/刻）。 */
    public static final double JETTISON_SPEED = 0.18;

    private ShipSides() {
    }

    /** 偏航 {@code yaw} 度时船头朝的水平方向 {x, z}。 */
    public static double[] forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.sin(r), Math.cos(r)};
    }

    /**
     * 出舱时人放在哪：按左舷、右舷、顶上、底下、船头、船尾的顺序排，第一个放得下人的就是。
     *
     * @param center 船的中心
     * @param radius 船的外接圆半径
     * @param height 船有多高
     * @return 每个都是脚底的位置 {x, y, z}
     */
    public static List<double[]> exitSpots(double[] center, float yaw, double radius, double height) {
        double[] f = forward(yaw);
        // 左舷：船头方向往左转 90°
        double[] left = {f[1], -f[0]};
        double out = radius + 1.2;
        List<double[]> spots = new ArrayList<>();
        spots.add(new double[] {center[0] + left[0] * out, center[1] - 0.9, center[2] + left[1] * out});
        spots.add(new double[] {center[0] - left[0] * out, center[1] - 0.9, center[2] - left[1] * out});
        spots.add(new double[] {center[0], center[1] + height / 2 + 1.2, center[2]});
        spots.add(new double[] {center[0], center[1] - height / 2 - 2.4, center[2]});
        spots.add(new double[] {center[0] + f[0] * out, center[1] - 0.9, center[2] + f[1] * out});
        spots.add(new double[] {center[0] - f[0] * out, center[1] - 0.9, center[2] - f[1] * out});
        return spots;
    }

    /**
     * 抛货：第 {@code index} 件从船尾哪里出去、往哪飘。几件一起抛时在船尾错开一点，不叠在一起。
     *
     * @return {x, y, z, vx, vy, vz}
     */
    public static double[] jettison(double[] center, float yaw, double radius, int index) {
        double[] f = forward(yaw);
        double out = radius + 1.0;
        double spread = (index % 5 - 2) * 0.35;
        double lift = (index / 5 % 3 - 1) * 0.35;
        double[] left = {f[1], -f[0]};
        double x = center[0] - f[0] * out + left[0] * spread;
        double z = center[2] - f[1] * out + left[1] * spread;
        double y = center[1] + lift;
        double vx = -f[0] * JETTISON_SPEED + left[0] * spread * 0.05;
        double vz = -f[1] * JETTISON_SPEED + left[1] * spread * 0.05;
        return new double[] {x, y, z, vx, lift * 0.05, vz};
    }
}
