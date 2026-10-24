package io.github.sweetcornna.universe.core.deepspace;

/**
 * 飞船的牵引光束（ADR-0034 §四）：开着时把 40 格内的掉落物和小块碎片吸过来，贴到船身就收进货舱；货舱满了就不吸。
 * 牵引光束模块的加成让范围变远（{@code 40 × (1 + 加成)}）。
 *
 * <p>吸的时候不是一下子拉过去：往船的方向加速，越近越慢，到船边速度降下来，看得清东西是怎么飘进去的。
 */
public final class Tractor {
    /** 基础范围（格）。 */
    public static final double RANGE = 40;
    /** 每刻往船那边加的速度（格/刻²）。 */
    static final double PULL = 0.08;
    /** 最快的速度（格/刻）。 */
    static final double MAX_SPEED = 1.4;
    /** 离船身这么近就收进货舱（格，从船的外接圆算起）。 */
    public static final double CATCH = 2.0;
    /** 隔几刻扫一遍范围里的东西。 */
    public static final int SCAN_INTERVAL = 5;

    private Tractor() {
    }

    /** 装着的牵引光束模块加成是 {@code bonus} 时的范围。 */
    public static double range(double bonus) {
        return RANGE * (1 + Math.max(0, bonus));
    }

    /** 东西离船的中心 {@code distance} 格，船的外接圆半径是 {@code hull}：收不收得进来。 */
    public static boolean caught(double distance, double hull) {
        return distance <= hull + CATCH;
    }

    /**
     * 这一刻的新速度：往船那边加速，越靠近越慢（不超过剩下的距离的五分之一），同时把横着的速度慢慢去掉。
     *
     * @param offset   船的中心减去东西的位置
     * @param velocity 东西现在的速度
     * @param hull     船的外接圆半径
     * @return 新的速度 {x, y, z}
     */
    public static double[] pull(double[] offset, double[] velocity, double hull) {
        double distance = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
        if (distance < 1e-6) {
            return new double[] {0, 0, 0};
        }
        double[] dir = {offset[0] / distance, offset[1] / distance, offset[2] / distance};
        double along = velocity[0] * dir[0] + velocity[1] * dir[1] + velocity[2] * dir[2];
        // 横着的那部分衰减掉
        double[] side = {velocity[0] - dir[0] * along, velocity[1] - dir[1] * along, velocity[2] - dir[2] * along};
        double limit = Math.min(MAX_SPEED, Math.max(0.15, (distance - hull) / 5));
        double speed = Math.clamp(along + PULL, -limit, limit);
        return new double[] {dir[0] * speed + side[0] * 0.8, dir[1] * speed + side[1] * 0.8, dir[2] * speed + side[2] * 0.8};
    }
}
