package io.github.sweetcornna.universe.core.sky;

/**
 * 星球挡住恒星（日食）：按两个圆面在天上的视半径和圆心的角距离算恒星还露出多少。
 * 视半径都是几度以内，按平面上的两个圆算就够了。
 */
public final class Eclipse {
    private Eclipse() {
    }

    /**
     * 恒星圆面没被挡住的比例。
     *
     * @param star       恒星的视半径
     * @param body       挡在前面的星球的视半径（和 {@code star} 同一个单位）
     * @param separation 两个圆心的角距离
     * @return 0 是全挡住，1 是没挡
     */
    public static double visible(double star, double body, double separation) {
        if (star <= 0) {
            return 1;
        }
        return Math.max(0, 1 - overlap(star, body, separation) / (Math.PI * star * star));
    }

    /** 半径 {@code a}、{@code b}，圆心相距 {@code d} 的两个圆的公共面积。 */
    static double overlap(double a, double b, double d) {
        if (a <= 0 || b <= 0 || d >= a + b) {
            return 0;
        }
        double small = Math.min(a, b);
        if (d <= Math.abs(a - b)) {
            return Math.PI * small * small;
        }
        double alpha = Math.acos(clamp((d * d + a * a - b * b) / (2 * d * a)));
        double beta = Math.acos(clamp((d * d + b * b - a * a) / (2 * d * b)));
        double kite = Math.sqrt(Math.max(0, (-d + a + b) * (d + a - b) * (d - a + b) * (d + a + b)));
        return a * a * alpha + b * b * beta - 0.5 * kite;
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }
}
