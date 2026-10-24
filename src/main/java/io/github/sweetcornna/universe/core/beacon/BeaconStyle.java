package io.github.sweetcornna.universe.core.beacon;

/** 全息信标的样子（ADR-0033 §六）：八种颜色、一行字的长度。 */
public final class BeaconStyle {
    /** 光柱和字的颜色：青、琥珀、绿、红、紫、蓝、白、粉。 */
    private static final int[] COLORS = {0x6EDCF5, 0xF2B544, 0x6EE07A, 0xF06A5A, 0xB07CF0, 0x5A8CF0, 0xE8EEF2, 0xF07CC8};
    public static final int MAX_LABEL = 32;

    private BeaconStyle() {
    }

    public static int colors() {
        return COLORS.length;
    }

    /** 颜色的编号对应的 RGB，越界的取第一种。 */
    public static int color(int index) {
        return COLORS[clampColor(index)];
    }

    public static int clampColor(int index) {
        return index >= 0 && index < COLORS.length ? index : 0;
    }

    public static String cleanLabel(String raw) {
        return Memo.cleanText(raw, MAX_LABEL);
    }
}
