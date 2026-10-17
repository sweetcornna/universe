package io.github.sweetcornna.universe.core.procgen;

/**
 * 颜色工具。颜色一律用 {@code 0xRRGGBB} 整数表示，不带透明度。
 */
public final class Colors {
    private Colors() {
    }

    public static int rgb(int r, int g, int b) {
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int rgb(double r, double g, double b) {
        return rgb((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255));
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /**
     * HSV 转 RGB。
     *
     * @param hue        色相，单位是圈（0–1，超出范围按圈取余）
     * @param saturation 饱和度 0–1
     * @param value      明度 0–1
     */
    public static int hsv(double hue, double saturation, double value) {
        double h = (hue - Math.floor(hue)) * 6.0;
        double s = clamp01(saturation);
        double v = clamp01(value);
        int sector = (int) Math.floor(h) % 6;
        double f = h - Math.floor(h);
        double p = v * (1 - s);
        double q = v * (1 - s * f);
        double t = v * (1 - s * (1 - f));
        return switch (sector) {
            case 0 -> rgb(v, t, p);
            case 1 -> rgb(q, v, p);
            case 2 -> rgb(p, v, t);
            case 3 -> rgb(p, q, v);
            case 4 -> rgb(t, p, v);
            default -> rgb(v, p, q);
        };
    }

    /** 逐通道线性插值，{@code t = 0} 得到 {@code a}。 */
    public static int lerp(int a, int b, double t) {
        double k = clamp01(t);
        return rgb(
                (int) Math.round(red(a) + (red(b) - red(a)) * k),
                (int) Math.round(green(a) + (green(b) - green(a)) * k),
                (int) Math.round(blue(a) + (blue(b) - blue(a)) * k));
    }

    /** 逐通道相乘（{@code 0xFFFFFF} 是单位元）。 */
    public static int multiply(int a, int b) {
        return rgb(red(a) * red(b) / 255, green(a) * green(b) / 255, blue(a) * blue(b) / 255);
    }

    /** 感知亮度 0–1（Rec. 709 系数，未做伽马校正，够用来比较明暗）。 */
    public static double luminance(int color) {
        return (0.2126 * red(color) + 0.7152 * green(color) + 0.0722 * blue(color)) / 255.0;
    }

    /**
     * 黑体辐射的近似颜色，用于恒星（Tanner Helland 的拟合公式）。
     *
     * @param kelvin 色温，1,000–40,000 K
     */
    public static int blackbody(double kelvin) {
        double t = Math.max(1000, Math.min(40000, kelvin)) / 100.0;
        double r;
        double g;
        double b;
        if (t <= 66) {
            r = 255;
            g = 99.4708025861 * Math.log(t) - 161.1195681661;
        } else {
            r = 329.698727446 * Math.pow(t - 60, -0.1332047592);
            g = 288.1221695283 * Math.pow(t - 60, -0.0755148492);
        }
        if (t >= 66) {
            b = 255;
        } else if (t <= 19) {
            b = 0;
        } else {
            b = 138.5177312231 * Math.log(t - 10) - 305.0447927307;
        }
        return rgb((int) Math.round(r), (int) Math.round(g), (int) Math.round(b));
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
