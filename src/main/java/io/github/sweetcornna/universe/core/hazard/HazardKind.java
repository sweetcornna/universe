package io.github.sweetcornna.universe.core.hazard;

import java.util.Locale;

/** 局部危害的种类（ADR-0034 §六）。间歇泉是世界生成的，另外三种在玩家附近偶尔发生。 */
public enum HazardKind {
    GEYSER("间歇泉", "Geyser"),
    ERUPTION("火山喷发", "Volcanic eruption"),
    SPORE_CLOUD("孢子云", "Spore cloud"),
    DUST_DEVIL("尘卷风", "Dust devil");

    private final String nameZh;
    private final String nameEn;

    HazardKind(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 语言键、命令参数里用的名字，比如 {@code spore_cloud}。 */
    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** 按名字找；找不到为 {@code null}。 */
    public static HazardKind byId(String id) {
        for (HazardKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
