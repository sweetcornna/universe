package io.github.sweetcornna.universe.core.chem.reaction;

import io.github.sweetcornna.universe.core.chem.Gas;

/**
 * 反应釜里出现的物质（ADR-0036 §四）：气体对应 {@link Gas}，另有反应里的水蒸气、中间产物三氧化硫、硫酸和烃链。
 *
 * @param formula 化学式
 * @param gas     平衡计算时算不算气相（算分压）
 * @param item    对应的物品；没有物品的中间产物为 {@code null}
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
    /** 三氧化硫：接触法的中间产物，马上被浓硫酸吸收，没有物品。 */
    SULFUR_TRIOXIDE("SO₃", "三氧化硫", "Sulfur Trioxide", true, null),
    /** 反应温度下是水蒸气；物品是水。 */
    WATER("H₂O", "水", "Water", true, "universe:water"),
    SULFURIC_ACID("H₂SO₄", "硫酸", "Sulfuric Acid", false, "universe:sulfuric_acid"),
    /** 费托合成的烃链，一节 CH₂；物品是精炼油。 */
    HYDROCARBON("(CH₂)ₙ", "烃", "Hydrocarbon", false, "universe:refined_oil");

    private final String formula;
    private final String nameZh;
    private final String nameEn;
    private final boolean gas;
    private final String item;

    Species(Gas gas) {
        this(gas.formula(), gas.nameZh(), gas.nameEn(), true, "universe:" + gas.item());
    }

    Species(String formula, String nameZh, String nameEn, boolean gas, String item) {
        this.formula = formula;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.gas = gas;
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

    /** 平衡计算时算不算气相。 */
    public boolean gas() {
        return this.gas;
    }

    /** 对应的物品（{@code universe:xxx}）；中间产物为 {@code null}。 */
    public String item() {
        return this.item;
    }
}
