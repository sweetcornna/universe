package io.github.sweetcornna.universe.core.chem.reaction;

import io.github.sweetcornna.universe.core.chem.Gas;

/**
 * 反应釜里出现的物质（ADR-0036 §四、ADR-0037）：气体对应 {@link Gas}，另有水蒸气、酸、盐、碳、硫和有机物。
 * 每种物质对应一样物品，一个物品就是反应式里的一个计量单位（精炼油一个算两节 CH₂，塑料一个算一节 C₂H₄）。
 *
 * <p>化学式是反应式的显示，也是原子守恒检查的依据（{@link io.github.sweetcornna.universe.core.chem.Formula}）。
 */
public enum Species {
    NITROGEN(Gas.NITROGEN),
    HYDROGEN(Gas.HYDROGEN),
    OXYGEN(Gas.OXYGEN),
    AMMONIA(Gas.AMMONIA),
    SULFUR_DIOXIDE(Gas.SULFUR_DIOXIDE),
    CARBON_DIOXIDE(Gas.CARBON_DIOXIDE),
    CARBON_MONOXIDE(Gas.CARBON_MONOXIDE),
    METHANE(Gas.METHANE),
    SULFUR_TRIOXIDE("SO₃", "三氧化硫", "Sulfur Trioxide", Phase.GAS, "universe:sulfur_trioxide"),
    /** 反应温度下是水蒸气；物品是水。 */
    WATER("H₂O", "水", "Water", Phase.GAS, "universe:water"),
    SULFURIC_ACID("H₂SO₄", "硫酸", "Sulfuric Acid", Phase.LIQUID, "universe:sulfuric_acid"),
    /** 费托合成的烃链，一个精炼油算两节 CH₂。 */
    HYDROCARBON("(CH₂)₂", "烃", "Hydrocarbon", Phase.LIQUID, "universe:refined_oil"),
    NITRIC_OXIDE("NO", "一氧化氮", "Nitric Oxide", Phase.GAS, "universe:nitric_oxide"),
    NITROGEN_DIOXIDE("NO₂", "二氧化氮", "Nitrogen Dioxide", Phase.GAS, "universe:nitrogen_dioxide"),
    NITRIC_ACID("HNO₃", "硝酸", "Nitric Acid", Phase.LIQUID, "universe:nitric_acid"),
    AMMONIUM_NITRATE("NH₄NO₃", "硝酸铵", "Ammonium Nitrate", Phase.SOLID, "universe:ammonium_nitrate"),
    UREA("CO(NH₂)₂", "尿素", "Urea", Phase.SOLID, "universe:urea"),
    HYDROGEN_CYANIDE(Gas.HYDROGEN_CYANIDE),
    /** 单质硫，按 S 计（固体是 S₈ 环，计量按原子）。 */
    SULFUR("S", "硫", "Sulfur", Phase.SOLID, "universe:sulfur"),
    /** 甲烷热解、布杜阿尔反应析出的碳，物品是石墨。 */
    CARBON("C", "石墨", "Graphite", Phase.SOLID, "universe:energetic_graphite"),
    /** 煤气化用的煤，热力学按石墨算。 */
    COAL("C", "煤", "Coal", Phase.SOLID, "minecraft:coal"),
    METHANOL("CH₃OH", "甲醇", "Methanol", Phase.GAS, "universe:methanol"),
    ETHYLENE("C₂H₄", "乙烯", "Ethylene", Phase.GAS, "universe:ethylene"),
    /** 聚乙烯，一个塑料算一节 C₂H₄。 */
    POLYETHYLENE("(C₂H₄)ₙ", "聚乙烯", "Polyethylene", Phase.SOLID, "universe:plastic"),
    HYDROGEN_CHLORIDE("HCl", "氯化氢", "Hydrogen Chloride", Phase.GAS, "universe:hydrogen_chloride"),
    CHLORINE(Gas.CHLORINE);

    /** 平衡计算里的相：只有气相算分压，凝聚相活度按 1。 */
    public enum Phase {
        GAS,
        LIQUID,
        SOLID
    }

    private final String formula;
    private final String nameZh;
    private final String nameEn;
    private final Phase phase;
    private final String item;

    Species(Gas gas) {
        this(gas.formula(), gas.nameZh(), gas.nameEn(), Phase.GAS, "universe:" + gas.item());
    }

    Species(String formula, String nameZh, String nameEn, Phase phase, String item) {
        this.formula = formula;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.phase = phase;
        this.item = item;
    }

    public String formula() {
        return this.formula;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public Phase phase() {
        return this.phase;
    }

    /** 平衡计算时算不算气相。 */
    public boolean gas() {
        return this.phase == Phase.GAS;
    }

    /** 对应的物品（{@code universe:xxx}）。 */
    public String item() {
        return this.item;
    }

    /** 这样物品是哪种物质；不是反应釜里的物质为 {@code null}。 */
    public static Species byItem(String item) {
        for (Species species : values()) {
            if (species.item.equals(item)) {
                return species;
            }
        }
        return null;
    }
}
