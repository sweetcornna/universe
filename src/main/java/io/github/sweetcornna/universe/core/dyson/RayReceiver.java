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
    public static final int LENS_TICKS = Lens.GRAVITON.ticks();

    /**
     * 透镜格里装的东西（ADR-0033 §七）：功率倍数、损耗的倍数、连续程度积累的倍数、一个能用几刻。
     * 黑雾透镜和引力透镜一样翻倍，另外损耗减半、积累快一倍、多用一半的时间。
     */
    public enum Lens {
        NONE("none", 1, 1, 1, 0),
        GRAVITON("graviton", 2, 1, 1, 12_000),
        DARK_FOG("dark_fog", 2, 0.5, 2, 18_000);

        private final String key;
        private final double power;
        private final double loss;
        private final double rise;
        private final int ticks;

        Lens(String key, double power, double loss, double rise, int ticks) {
            this.key = key;
            this.power = power;
            this.loss = loss;
            this.rise = rise;
            this.ticks = ticks;
        }

        /** 存档和语言键里用的名字。 */
        public String key() {
            return this.key;
        }

        public double power() {
            return this.power;
        }

        public double loss() {
            return this.loss;
        }

        public double rise() {
            return this.rise;
        }

        public int ticks() {
            return this.ticks;
        }

        /** 按名字找，认不出来的当作没装。 */
        public static Lens byKey(String key) {
            for (Lens lens : values()) {
                if (lens.key.equals(key)) {
                    return lens;
                }
            }
            return NONE;
        }

        public static Lens byOrdinal(int ordinal) {
            Lens[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NONE;
        }
    }

    private RayReceiver() {
    }

    /** 过了一刻以后的连续程度。 */
    public static double continuity(double current, boolean receiving) {
        return continuity(current, receiving, Lens.NONE);
    }

    /** 过了一刻以后的连续程度，黑雾透镜积累得快。 */
    public static double continuity(double current, boolean receiving, Lens lens) {
        double next = receiving ? current + lens.rise() / RISE_TICKS : current - 1 / FALL_TICKS;
        return Math.max(0, Math.min(1, next));
    }

    /** 效率：1 − 0.7 × (1 − 0.4 × 连续程度)，30% 到 58%。 */
    public static double efficiency(double continuity) {
        return efficiency(continuity, 1);
    }

    /** 效率，损耗（1 − 效率）乘上射线传输效率升级的倍数 lossFactor（ADR-0018）。 */
    public static double efficiency(double continuity, double lossFactor) {
        return 1 - 0.7 * (1 - 0.4 * clamp(continuity)) * lossFactor;
    }

    /** 效率，再乘上透镜的损耗倍数。 */
    public static double efficiency(double continuity, Lens lens, double lossFactor) {
        return efficiency(continuity, lossFactor * lens.loss());
    }

    /** 发电模式的最大输出（焦/刻），装着透镜时翻倍。 */
    public static double maxOutput(double continuity, boolean lens) {
        return maxOutput(continuity, lens ? Lens.GRAVITON : Lens.NONE);
    }

    public static double maxOutput(double continuity, Lens lens) {
        return (OUTPUT_MIN + (OUTPUT_MAX - OUTPUT_MIN) * clamp(continuity)) * lens.power();
    }

    /** 发电模式输出满时要从戴森球取多少（焦/刻）。 */
    public static double powerRequest(double continuity, boolean lens, double lossFactor) {
        return powerRequest(continuity, lens ? Lens.GRAVITON : Lens.NONE, lossFactor);
    }

    public static double powerRequest(double continuity, Lens lens, double lossFactor) {
        return maxOutput(continuity, lens) / efficiency(continuity, lens, lossFactor);
    }

    /** 光子模式从戴森球取多少（焦/刻），装着透镜时翻倍。 */
    public static double photonRequest(double continuity, boolean lens) {
        return photonRequest(continuity, lens ? Lens.GRAVITON : Lens.NONE);
    }

    public static double photonRequest(double continuity, Lens lens) {
        return (PHOTON_DRAW_MIN + (PHOTON_DRAW_MAX - PHOTON_DRAW_MIN) * clamp(continuity)) * lens.power();
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
