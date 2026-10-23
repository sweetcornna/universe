package io.github.sweetcornna.universe.core.dyson;

/**
 * 戴森球上色的预设色（ADR-0030 §四）和默认色。颜色 0 表示默认色；画的时候按天空亮暗调深浅。
 */
public final class DysonPalette {
    /** 预设：金、白、橙、红、粉、紫、蓝、青、绿、黄绿、银灰、深灰。 */
    public static final int[] PRESETS = {
            0xFFC86E, 0xFFF2DC, 0xFF8A3C, 0xE84A3A, 0xF07AB4, 0x9C6CF0,
            0x4C8CF0, 0x48D8E0, 0x58D068, 0xC8E050, 0xB8C4CC, 0x5A646E};
    /** 默认色：节点、框架、球壳。 */
    public static final int NODE = 0xFFEFC8;
    public static final int FRAME = 0xFFC86E;
    public static final int SHELL = 0xF0B040;

    private DysonPalette() {
    }

    /** 颜色（0 是默认色）换成 RGB。 */
    public static int rgb(int color, int fallback) {
        return color == 0 ? fallback & 0xFFFFFF : color & 0xFFFFFF;
    }

    /** 按 t 混合两个 RGB。 */
    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    /** RGB 写成 #RRGGBB。 */
    public static String hex(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }
}
