package io.github.sweetcornna.universe.core.microbe;

/**
 * 采样管取样的地方（ADR-0036 §五）。微生物组按来源挑菌，组织样本只测基因、不分离菌种。
 * 顺序和名字是存档格式的一部分（样本物品按名字存），只往后加。
 */
public enum SampleSource {
    SOIL("土壤", "Soil"),
    WATER("水", "Water"),
    /** 间歇泉的结壳旁、熔岩边上：滚烫的水。 */
    HOT_SPRING("热泉", "Hot Spring"),
    ACID("酸液", "Acid"),
    ICE("冰雪", "Ice"),
    SALT("盐", "Salt"),
    /** 岩石内部：岩石内生菌住在石头的缝里，挡住紫外和干燥。 */
    ROCK("岩石内部", "Rock Interior"),
    /** 湿地的泥、海底：不见氧的沉积物。 */
    SEDIMENT("沉积物", "Sediment"),
    /** 地下的铜、铁、硫矿：现实里的酸性矿山排水。 */
    ORE("矿石", "Ore"),
    /** 钓到的杂物、废弃货船里的东西，菌是人带过去的。 */
    JUNK("垃圾", "Junk"),
    CREATURE_TISSUE("生物组织", "Creature Tissue"),
    PLANT_TISSUE("植物组织", "Plant Tissue");

    private final String nameZh;
    private final String nameEn;

    SampleSource(String nameZh, String nameEn) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 小写的名字，用在语言键里。 */
    public String id() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 组织样本：测序仪直接测基因，培养箱里长不出菌。 */
    public boolean tissue() {
        return this == CREATURE_TISSUE || this == PLANT_TISSUE;
    }

    /** 按名字找，找不到为 {@code null}。 */
    public static SampleSource byName(String name) {
        for (SampleSource source : values()) {
            if (source.name().equals(name)) {
                return source;
            }
        }
        return null;
    }
}
