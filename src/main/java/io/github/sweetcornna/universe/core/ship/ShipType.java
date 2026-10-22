package io.github.sweetcornna.universe.core.ship;

import java.util.Locale;

/**
 * 按部件评出的飞船类型（ADR-0024）。类型决定档次加成加在哪一项上。
 */
public enum ShipType {
    /** 什么都不突出的船：省燃料。 */
    SHUTTLE("穿梭机", "Shuttle"),
    /** 武器多：加伤害。 */
    FIGHTER("战斗机", "Fighter"),
    /** 货舱大：加船体和护盾。 */
    HAULER("运输船", "Hauler"),
    /** 跃迁引擎和燃料舱多：加跃迁航程。 */
    EXPLORER("探索船", "Explorer");

    private final String nameZh;
    private final String nameEn;

    ShipType(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 语言键里用的名字。 */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
