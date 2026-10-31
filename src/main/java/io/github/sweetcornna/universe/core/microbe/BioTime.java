package io.github.sweetcornna.universe.core.microbe;

/**
 * 微生物的时间怎么换成游戏时间（ADR-0036 §五）。所有的翻倍时间、培养时间都按现实的小时写，
 * 再统一除以 {@link #COMPRESSION}，菌和菌之间快慢的比例和现实一样。
 *
 * <p>压缩 600 倍：现实 1 小时是游戏 6 秒（120 刻），现实 1 天是游戏 2.4 分钟。这样最快的费氏弧菌（1 小时）
 * 6 秒翻一倍，最慢的巴氏甲烷八叠球菌（15 小时）一分半翻一倍，一罐从接种到长满在一分钟到一刻钟之间。
 * 比我的世界自己的昼夜（72 倍）快得多：按 72 倍，产甲烷菌一罐要长两个多小时。
 */
public final class BioTime {
    /** 现实时间是游戏时间的多少倍。 */
    public static final double COMPRESSION = 600;
    /** 现实的一小时是游戏的多少刻。 */
    public static final double TICKS_PER_REAL_HOUR = 3600 / COMPRESSION * 20;

    private BioTime() {
    }

    /** 现实的 {@code hours} 小时是游戏的多少刻。 */
    public static double ticks(double hours) {
        return hours * TICKS_PER_REAL_HOUR;
    }

    /** 游戏的 {@code ticks} 刻是现实的多少小时。 */
    public static double hours(double ticks) {
        return ticks / TICKS_PER_REAL_HOUR;
    }
}
