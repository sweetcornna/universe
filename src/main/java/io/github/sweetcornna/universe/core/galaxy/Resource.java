package io.github.sweetcornna.universe.core.galaxy;

import java.util.Locale;

/** 星球上的资源种类（玩法设计 §9.1）。具体的方块和物品在适配层注册。 */
public enum Resource {
    IRON("铁", "Iron"),
    COPPER("铜", "Copper"),
    COAL("煤", "Coal"),
    TITANIUM("钛", "Titanium"),
    SILICON("硅", "Silicon"),
    SULFUR("硫", "Sulfur"),
    CRUDE_OIL("原油", "Crude oil"),
    /** 可燃冰。 */
    FIRE_ICE("可燃冰", "Fire ice"),
    WATER_ICE("水冰", "Water ice"),
    HYDROGEN("氢", "Hydrogen"),
    DEUTERIUM("重氢", "Deuterium"),
    OPTICAL_CRYSTAL("光学晶体", "Optical crystal"),
    ORGANIC_CRYSTAL("有机晶体", "Organic crystal"),
    URANIUM("放射性矿", "Radioactive ore"),
    SALT("盐", "Salt"),
    /** 中子星附近的强磁性矿物。 */
    MAGNETIC_ORE("磁性矿", "Magnetic ore"),
    /** 黑洞附近的奇异物质。 */
    STRANGE_MATTER("奇异物质", "Strange matter");

    private final String nameZh;
    private final String nameEn;

    Resource(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public String serializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 一种资源在某颗星球上的储量。
     *
     * @param abundance 丰度 0–1，决定矿脉节点的密度和储量
     */
    public record Deposit(Resource resource, double abundance) {
    }
}
