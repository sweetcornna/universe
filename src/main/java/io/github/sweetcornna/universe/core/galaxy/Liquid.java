package io.github.sweetcornna.universe.core.galaxy;

/** 星球表面的液体，填充海平面以下的低地和湖泊。 */
public enum Liquid {
    NONE("无", "None"),
    WATER("水", "Water"),
    LAVA("熔岩", "Lava"),
    ACID("酸液", "Acid"),
    /** 冰封的海：海面是冰，冰下是水。 */
    ICE("冰封海", "Frozen sea");

    private final String nameZh;
    private final String nameEn;

    Liquid(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }
}
