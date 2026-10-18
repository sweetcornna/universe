package io.github.sweetcornna.universe.core.dyson;

/**
 * 射线接收站的数值（ADR-0016），照戴森球计划：连续接收 20 分钟，最大输出从 6 MW 升到 15 MW、效率从 30% 升到 58%；
 * 光子模式从戴森球取 48 → 120 MW，每 1.2 GJ 一个临界光子；引力透镜让功率翻倍，每分钟耗 0.1 个。
 * 本模组按千瓦除以 20 换成焦/刻，1 焦相当于戴森球计划的 1 千焦。
 */
public final class RayReceiver {
    /** 连续接收的程度从 0 涨到 1 要几刻，中断后从 1 降到 0 要几刻。 */
    public static final double RISE_TICKS = 24_000;
    public static final double FALL_TICKS = 2_400;
    /** 发电模式的最大输出（焦/刻）。 */
    public static final double OUTPUT_MIN = 300;
    public static final double OUTPUT_MAX = 750;
    /** 光子模式从戴森球取的功率（焦/刻）。 */
    public static final double PHOTON_DRAW_MIN = 2400;
    public static final double PHOTON_DRAW_MAX = 6000;
    /** 一个临界光子要多少焦。 */
    public static final double PHOTON_ENERGY = 1_200_000;
    /** 一个引力透镜工作多少刻。 */
    public static final int LENS_TICKS = 12_000;

    private RayReceiver() {
    }

    /** 过了一刻以后的连续程度。 */
    public static double continuity(double current, boolean receiving) {
        double next = receiving ? current + 1 / RISE_TICKS : current - 1 / FALL_TICKS;
        return Math.max(0, Math.min(1, next));
    }

    /** 效率：1 − 0.7 × (1 − 0.4 × 连续程度)，30% 到 58%。 */
    public static double efficiency(double continuity) {
        return 1 - 0.7 * (1 - 0.4 * clamp(continuity));
    }

    /** 发电模式的最大输出（焦/刻），装着透镜时翻倍。 */
    public static double maxOutput(double continuity, boolean lens) {
        double output = OUTPUT_MIN + (OUTPUT_MAX - OUTPUT_MIN) * clamp(continuity);
        return lens ? output * 2 : output;
    }

    /** 发电模式输出满时要从戴森球取多少（焦/刻）。 */
    public static double powerRequest(double continuity, boolean lens) {
        return maxOutput(continuity, lens) / efficiency(continuity);
    }

    /** 光子模式从戴森球取多少（焦/刻），装着透镜时翻倍。 */
    public static double photonRequest(double continuity, boolean lens) {
        double draw = PHOTON_DRAW_MIN + (PHOTON_DRAW_MAX - PHOTON_DRAW_MIN) * clamp(continuity);
        return lens ? draw * 2 : draw;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
