package io.github.sweetcornna.universe.core.fleet;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * 护卫舰的五种（ADR-0024）。四种专长的各在自己那项上 +{@link Frigate#SPECIALIST}，支援舰不加专长，燃料消耗低，
 * 四项各 +{@link Frigate#SUPPORT_ALL}。
 */
public enum FrigateType {
    COMBAT("战斗", "Combat", Area.COMBAT, 22),
    EXPLORATION("探索", "Exploration", Area.EXPLORATION, 22),
    INDUSTRIAL("工业", "Industrial", Area.INDUSTRY, 20),
    TRADE("贸易", "Trade", Area.TRADE, 20),
    SUPPORT("支援", "Support", null, 16);

    private final String nameZh;
    private final String nameEn;
    private final @Nullable Area specialty;
    private final double weight;

    FrigateType(String nameZh, String nameEn, @Nullable Area specialty, double weight) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.specialty = specialty;
        this.weight = weight;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 专长；支援舰没有。 */
    public @Nullable Area specialty() {
        return this.specialty;
    }

    /** 交易终端上出现的权重。 */
    public double weight() {
        return this.weight;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
