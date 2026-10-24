package io.github.sweetcornna.universe.core.fishing;

import java.util.Locale;

/**
 * 鱼的档次（ADR-0034 §一）：每种水域普通 5 种、少见 4 种、稀有 2 种、传说 1 种。
 *
 * @param presence 一颗星球上有这种鱼的概率（照星球的种子定）
 * @param weight   咬钩时挑中这种鱼的基础权重
 * @param price    价钱是个头基准价的几倍
 * @param fight    收线时比同个头的普通鱼多出的力气
 */
public enum Rarity {
    COMMON("普通", "Common", 5, 1.0, 60, 1, 0, 1.0, 0xFFC8D0D8),
    UNCOMMON("少见", "Uncommon", 4, 0.75, 25, 2.5, 0.05, 1.1, 0xFF6FD86F),
    RARE("稀有", "Rare", 2, 0.5, 9, 6, 0.1, 1.2, 0xFF5AA8FF),
    LEGENDARY("传说", "Legendary", 1, 0.3, 2, 20, 0.15, 1.33, 0xFFFFB43C);

    private final String nameZh;
    private final String nameEn;
    private final int perHabitat;
    private final double presence;
    private final double weight;
    private final double price;
    private final double fight;
    private final double reelFactor;
    private final int color;

    Rarity(String nameZh, String nameEn, int perHabitat, double presence, double weight, double price, double fight, double reelFactor,
            int color) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.perHabitat = perHabitat;
        this.presence = presence;
        this.weight = weight;
        this.price = price;
        this.fight = fight;
        this.reelFactor = reelFactor;
        this.color = color;
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

    /** 每种水域里这一档有几种鱼。 */
    public int perHabitat() {
        return this.perHabitat;
    }

    public double presence() {
        return this.presence;
    }

    public double weight() {
        return this.weight;
    }

    public double price() {
        return this.price;
    }

    public double fight() {
        return this.fight;
    }

    /** 收线的时长是同个头普通鱼的几倍。 */
    public double reelFactor() {
        return this.reelFactor;
    }

    /** 名字和图鉴边框的颜色（ARGB）。 */
    public int color() {
        return this.color;
    }

    /** 少见及以上（鱼饵、放生加成的那几档）。 */
    public boolean special() {
        return this != COMMON;
    }
}
