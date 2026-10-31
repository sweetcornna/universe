package io.github.sweetcornna.universe.core.chem;

/**
 * 大气里和工厂里的气体（ADR-0036 §二）。每种对应一个物品，一个物品按一摩尔配平配方。
 * 顺序和名字是存档格式的一部分（改造状态按名字存），只往后加。
 *
 * @param item          物品的注册名
 * @param formula       化学式，下标用 Unicode 的下标数字
 * @param molarMass     摩尔质量（g/mol）
 * @param toxic         有毒：算进"有毒气体"的门槛（ADR-0036 §六）
 * @param greenhouse    温室效应的强弱，按每个分子的辐射效率和二氧化碳比（IPCC AR5 第 8 章：甲烷 3.63 × 10⁻⁴、
 *                      二氧化碳 1.37 × 10⁻⁵ W·m⁻²·ppb⁻¹，约 26 倍），没有温室效应为 0
 * @param exposureLimit 长期接触的限值（ppm，在一个标准大气压下；美国 NIOSH 的推荐暴露限值 REL），没有限值的为 0。
 *                      分压比一个大气压高或低时，按分压折成一个大气压下的浓度再比
 */
public enum Gas {
    NITROGEN("nitrogen", "N₂", "氮气", "Nitrogen", 28.014, false, 0, 0),
    OXYGEN("oxygen", "O₂", "氧气", "Oxygen", 31.998, false, 0, 0),
    /** 二氧化碳本身不算毒气，浓了会让人中毒：NIOSH 限值 5000 ppm，单独算一项门槛。 */
    CARBON_DIOXIDE("carbon_dioxide", "CO₂", "二氧化碳", "Carbon Dioxide", 44.009, false, 1, 5000),
    CARBON_MONOXIDE("carbon_monoxide", "CO", "一氧化碳", "Carbon Monoxide", 28.010, true, 0, 35),
    METHANE("methane", "CH₄", "甲烷", "Methane", 16.043, false, 26, 0),
    ARGON("argon", "Ar", "氩气", "Argon", 39.948, false, 0, 0),
    HELIUM("helium", "He", "氦气", "Helium", 4.003, false, 0, 0),
    HYDROGEN("hydrogen", "H₂", "氢气", "Hydrogen", 2.016, false, 0, 0),
    SULFUR_DIOXIDE("sulfur_dioxide", "SO₂", "二氧化硫", "Sulfur Dioxide", 64.066, true, 0, 2),
    /** 氨没有 IPCC 的辐射效率，按和甲烷同一个量级算（萨根 1972 年提出早期地球靠氨保温）。 */
    AMMONIA("ammonia", "NH₃", "氨气", "Ammonia", 17.031, true, 26, 25),
    /** NIOSH 给的是上限值（任何时候不超过 0.5 ppm）。 */
    CHLORINE("chlorine", "Cl₂", "氯气", "Chlorine", 70.906, true, 0, 0.5),
    /** 氰化氢：土卫六的大气里测到过（卡西尼号），NIOSH 的短时限值 4.7 ppm。 */
    HYDROGEN_CYANIDE("hydrogen_cyanide", "HCN", "氰化氢", "Hydrogen Cyanide", 27.025, true, 0, 4.7);

    private final String item;
    private final String formula;
    private final String nameZh;
    private final String nameEn;
    private final double molarMass;
    private final boolean toxic;
    private final double greenhouse;
    private final double exposureLimit;

    Gas(String item, String formula, String nameZh, String nameEn, double molarMass, boolean toxic, double greenhouse,
            double exposureLimit) {
        this.item = item;
        this.formula = formula;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.molarMass = molarMass;
        this.toxic = toxic;
        this.greenhouse = greenhouse;
        this.exposureLimit = exposureLimit;
    }

    public String item() {
        return this.item;
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

    public double molarMass() {
        return this.molarMass;
    }

    public boolean toxic() {
        return this.toxic;
    }

    public double greenhouse() {
        return this.greenhouse;
    }

    /** 长期接触的限值（ppm，一个标准大气压下），没有为 0。 */
    public double exposureLimit() {
        return this.exposureLimit;
    }

    /** 按名字找，找不到为 {@code null}（读旧存档、读别的版本写的名字时用）。 */
    public static Gas byName(String name) {
        for (Gas gas : values()) {
            if (gas.name().equals(name)) {
                return gas;
            }
        }
        return null;
    }
}
