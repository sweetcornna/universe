package io.github.sweetcornna.universe.core.fleet;

import java.util.Locale;

/** 护卫舰的四项属性，也是远征里遭遇的四种类型（ADR-0024）。 */
public enum Area {
    COMBAT("战斗", "Combat"),
    EXPLORATION("探索", "Exploration"),
    INDUSTRY("工业", "Industry"),
    TRADE("贸易", "Trade");

    private final String nameZh;
    private final String nameEn;

    Area(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
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
}
