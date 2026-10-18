package io.github.sweetcornna.universe.core.power;

/**
 * 太阳能板（ADR-0015）：发电量 = 满功率 × 星球的光照比例 × 太阳高度 × 天气，看不见天空时不发电。
 *
 * <p>光照比例照戴森球计划的"太阳能利用率"：(光度 / 距离²)^0.3，母星（一倍光度、1 AU）正好是 1，
 * 靠近亮星的星球能到两倍多，外围气态巨行星的卫星只有三四成，暗淡的致密天体旁边几乎没有。
 */
public final class Solar {
    /** 光照比例的上限（贴着恒星时）。 */
    public static final double MAX_RATIO = 2.5;
    /** 追踪太阳时最多倾斜多少度。 */
    public static final double MAX_TILT = 75;

    private Solar() {
    }

    /** 离光度为 {@code luminosity}（太阳为 1）的恒星 {@code distanceAu} 天文单位处的光照比例。 */
    public static double ratio(double luminosity, double distanceAu) {
        double distance = Math.max(0.05, distanceAu);
        return Math.min(MAX_RATIO, Math.pow(Math.max(0, luminosity) / (distance * distance), 0.3));
    }

    /** 太阳高度系数：正午 1，日出日落和夜里 0。{@code sunAngle} 是原版的太阳角度（度），0 是正午，180 是午夜。 */
    public static double elevation(double sunAngle) {
        return Math.max(0, Math.cos(Math.toRadians(sunAngle)));
    }

    /** 天气系数：下雨六成，雷雨三成。 */
    public static double weather(boolean raining, boolean thundering) {
        return thundering ? 0.3 : raining ? 0.6 : 1;
    }

    /**
     * 板子追踪太阳的倾角（度，正值朝西）：白天对着太阳，最多倾斜 {@value #MAX_TILT} 度；日落后慢慢放平，
     * 天亮前再转向东边等着。随太阳角度连续变化，画的时候不会跳。
     */
    public static double trackingAngle(double sunAngle) {
        double angle = wrap(sunAngle);
        double size = Math.abs(angle);
        double tilt;
        if (size <= MAX_TILT) {
            tilt = size;
        } else if (size <= 90) {
            tilt = MAX_TILT;
        } else if (size < 120) {
            tilt = MAX_TILT * (120 - size) / 30;
        } else {
            tilt = 0;
        }
        return Math.copySign(tilt, angle);
    }

    /** 换算到 (-180, 180]。 */
    static double wrap(double degrees) {
        double result = degrees % 360;
        if (result <= -180) {
            result += 360;
        } else if (result > 180) {
            result -= 360;
        }
        return result;
    }
}
