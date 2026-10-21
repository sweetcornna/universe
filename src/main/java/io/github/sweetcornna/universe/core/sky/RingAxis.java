package io.github.sweetcornna.universe.core.sky;

/**
 * 太空里有环的行星的自转轴（ADR-0021）。太空里的行星停在固定的位置上，没有季节：要是自转轴正好横着对恒星，
 * 恒星就一直在环面上，环永远侧对着光、一片黑。这里把轴的倾斜方向转到离恒星方向（或反方向）{@value #MAX_TURN_DEGREES}°
 * 以内，倾角至少 {@value #MIN_TILT_DEGREES}°，像土星在至日前后，环总有一面被照亮。
 *
 * <p>坐标是太空维度的世界坐标：y 是黄道面的法线。
 */
public final class RingAxis {
    static final double MIN_TILT_DEGREES = 15;
    static final double MAX_TURN_DEGREES = 35;

    private RingAxis() {
    }

    /**
     * @param axis 原来的自转轴（单位向量）
     * @param sun  从行星指向恒星的方向（单位向量）
     * @return 调过的自转轴（单位向量）
     */
    public static double[] lit(double[] axis, double[] sun) {
        double sunLength = Math.hypot(sun[0], sun[2]);
        if (sunLength < 1e-6) {
            return axis.clone();
        }
        double sx = sun[0] / sunLength;
        double sz = sun[2] / sunLength;
        double up = axis[1] >= 0 ? 1 : -1;
        double tilt = Math.acos(Math.min(1, Math.abs(axis[1])));
        double hLength = Math.hypot(axis[0], axis[2]);
        double hx = hLength < 1e-6 ? sx : axis[0] / hLength;
        double hz = hLength < 1e-6 ? sz : axis[2] / hLength;
        // 倾斜方向和恒星方向的夹角；超过 90° 就以反方向为准，环照样被照亮，只是亮的是另一面
        double angle = Math.atan2(sx * hz - sz * hx, sx * hx + sz * hz);
        double base = Math.atan2(sz, sx);
        if (Math.abs(angle) > Math.PI / 2) {
            base += Math.PI;
            angle = angle > 0 ? angle - Math.PI : angle + Math.PI;
        }
        double limit = Math.toRadians(MAX_TURN_DEGREES);
        double direction = base + Math.max(-limit, Math.min(limit, angle));
        tilt = Math.max(tilt, Math.toRadians(MIN_TILT_DEGREES));
        double s = Math.sin(tilt);
        return new double[] {s * Math.cos(direction), up * Math.cos(tilt), s * Math.sin(direction)};
    }
}
