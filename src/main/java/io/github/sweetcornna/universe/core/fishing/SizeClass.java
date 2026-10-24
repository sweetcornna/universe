package io.github.sweetcornna.universe.core.fishing;

import java.util.Locale;

/**
 * 鱼的个头（ADR-0034 §一）：重量在这一档的范围里随机，水越深越偏重。
 *
 * @param min       最轻（千克）
 * @param max       最重（千克）
 * @param strength  力气（0–1）：收线时按住，张力涨得多快；猛冲得多勤、多狠
 * @param reelTicks 张力一直在绿区时，进度从 0 涨满要几刻（普通鱼）
 * @param price     普通档的基准价（信用点）
 */
public enum SizeClass {
    SMALL("小", "Small", 0.05, 0.8, 0.15, 100, 20),
    MEDIUM("中", "Medium", 0.8, 6, 0.35, 140, 45),
    LARGE("大", "Large", 6, 40, 0.6, 180, 110),
    HUGE("巨", "Huge", 40, 400, 0.85, 240, 300);

    private final String nameZh;
    private final String nameEn;
    private final double min;
    private final double max;
    private final double strength;
    private final int reelTicks;
    private final double price;

    SizeClass(String nameZh, String nameEn, double min, double max, double strength, int reelTicks, double price) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.min = min;
        this.max = max;
        this.strength = strength;
        this.reelTicks = reelTicks;
        this.price = price;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double strength() {
        return this.strength;
    }

    public int reelTicks() {
        return this.reelTicks;
    }

    public double price() {
        return this.price;
    }

    /** 大的、巨的（深水坠饵翻倍的那两档）。 */
    public boolean big() {
        return this == LARGE || this == HUGE;
    }
}
