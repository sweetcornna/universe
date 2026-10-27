package io.github.sweetcornna.universe.core.chem.reaction;

import java.util.List;

/**
 * 反应釜里做的五条反应（ADR-0036 §四），都受平衡或选择性限制，要催化剂。顺序和名字是存档格式的一部分
 * （反应釜按名字存选的反应），只往后加。
 *
 * <p>热力学数据是 298 K 的标准值，由标准生成焓和标准熵算出：
 * <ul>
 *   <li>合成氨 N₂ + 3H₂ ⇌ 2NH₃：ΔH° = 2 × (−46.1) = −92.2 kJ，ΔS° = 2 × 192.5 − 191.6 − 3 × 130.7 = −198.7 J/K。</li>
 *   <li>接触法 2SO₂ + O₂ ⇌ 2SO₃(g)：ΔH° = 2 × (−395.7 + 296.8) = −197.8 kJ，ΔS° = 2 × 256.8 − 2 × 248.2 − 205.2 = −188.0 J/K。</li>
 *   <li>萨巴蒂尔 CO₂ + 4H₂ → CH₄ + 2H₂O(g)：ΔH° = −74.8 − 2 × 241.8 + 393.5 = −164.9 kJ，
 *       ΔS° = 186.3 + 2 × 188.8 − 213.8 − 4 × 130.7 = −172.7 J/K。</li>
 *   <li>甲烷蒸汽重整 CH₄ + H₂O(g) ⇌ CO + 3H₂：ΔH° = −110.5 + 74.8 + 241.8 = +206.1 kJ，
 *       ΔS° = 197.7 + 3 × 130.7 − 186.3 − 188.8 = +214.7 J/K。</li>
 *   <li>费托合成 CO + 2H₂ → (CH₂) + H₂O：每摩尔一氧化碳放热约 165 kJ，反应条件下几乎走到底，按不可逆算，看的是选择性。</li>
 * </ul>
 * 上面是 298 K 的值。平衡和反应热用 {@link #enthalpyAt}、{@link #entropyAt}，按 {@link Shomate} 的热容随温度修正：
 * 合成氨在 450 °C、200 bar 的平衡氨含量算出来约 25%，100 atm 时 16.0%（手册 16.4%），300 atm 时 31.8%（手册 35.5%，
 * 差的是高压下的非理想性，这里按理想气体算）。
 *
 * <p>物品的配方按摩尔比：接触法连吸收一起算，2 二氧化硫 + 1 氧 + 2 水 → 2 硫酸。费托合成一个精炼油算两节 CH₂，
 * 2 一氧化碳 + 4 氢 → 1 精炼油 + 2 水；温度越高，越多的碳变成甲烷（副产物），甲烷化要多用的氢不另算。
 *
 * <p>反应快慢写成无量纲的达姆科勒数 Da = A × e^(−Ea/RT) × 催化剂活性 × P/(P + P½)，单程转化率 = 平衡转化率 × (1 − e^(−Da))。
 * 指前因子 A 调到在教科书的压强下产量最高的温度落在现实的工况里（{@link #industrialMin()}–{@link #industrialMax()}）。
 */
public enum Reaction {
    HABER("合成氨", "Haber–Bosch", "N₂ + 3H₂ ⇄ 2NH₃",
            List.of(new Term(Species.NITROGEN, 1), new Term(Species.HYDROGEN, 3)),
            List.of(new Term(Species.AMMONIA, 2)),
            -92_200, -198.7, true, 100_000, 5.7e7, 30, Catalyst.IRON,
            new ReactorSettings(723, 200), 673, 773, 30,
            List.of(new ItemCount("universe:nitrogen", 1), new ItemCount("universe:hydrogen", 3)),
            List.of(new ItemCount("universe:ammonia", 2)), null),
    CONTACT("接触法制硫酸", "Contact Process", "2SO₂ + O₂ ⇄ 2SO₃",
            List.of(new Term(Species.SULFUR_DIOXIDE, 2), new Term(Species.OXYGEN, 1)),
            List.of(new Term(Species.SULFUR_TRIOXIDE, 2)),
            -197_800, -188.0, true, 90_000, 4.15e7, 0.5, Catalyst.VANADIUM_PENTOXIDE,
            new ReactorSettings(703, 1.5), 673, 723, 20,
            List.of(new ItemCount("universe:sulfur_dioxide", 2), new ItemCount("universe:oxygen", 1), new ItemCount("universe:water", 2)),
            List.of(new ItemCount("universe:sulfuric_acid", 2)), null),
    SABATIER("萨巴蒂尔反应", "Sabatier Reaction", "CO₂ + 4H₂ → CH₄ + 2H₂O",
            List.of(new Term(Species.CARBON_DIOXIDE, 1), new Term(Species.HYDROGEN, 4)),
            List.of(new Term(Species.METHANE, 1), new Term(Species.WATER, 2)),
            -164_900, -172.7, true, 80_000, 4.16e7, 2, Catalyst.NICKEL,
            new ReactorSettings(623, 5), 573, 673, 20,
            List.of(new ItemCount("universe:carbon_dioxide", 1), new ItemCount("universe:hydrogen", 4)),
            List.of(new ItemCount("universe:methane", 1), new ItemCount("universe:water", 2)), null),
    STEAM_REFORMING("甲烷蒸汽重整", "Steam Reforming", "CH₄ + H₂O ⇄ CO + 3H₂",
            List.of(new Term(Species.METHANE, 1), new Term(Species.WATER, 1)),
            List.of(new Term(Species.CARBON_MONOXIDE, 1), new Term(Species.HYDROGEN, 3)),
            206_100, 214.7, true, 240_000, 1.96e11, 5, Catalyst.NICKEL,
            new ReactorSettings(1123, 25), 1073, 1173, 30,
            List.of(new ItemCount("universe:methane", 1), new ItemCount("universe:water", 1)),
            List.of(new ItemCount("universe:carbon_monoxide", 1), new ItemCount("universe:hydrogen", 3)), null),
    FISCHER_TROPSCH("费托合成", "Fischer–Tropsch", "CO + 2H₂ → (CH₂) + H₂O",
            List.of(new Term(Species.CARBON_MONOXIDE, 1), new Term(Species.HYDROGEN, 2)),
            List.of(new Term(Species.HYDROCARBON, 1), new Term(Species.WATER, 1)),
            -165_000, 0, false, 100_000, 3.06e10, 10, Catalyst.IRON,
            new ReactorSettings(548, 25), 473, 623, 20,
            List.of(new ItemCount("universe:carbon_monoxide", 2), new ItemCount("universe:hydrogen", 4)),
            List.of(new ItemCount("universe:refined_oil", 1), new ItemCount("universe:water", 2)),
            new ItemCount("universe:methane", 2));

    /** 费托合成的甲烷选择性：低温约 4%（现实的低温费托 4–5%），中点 700 K，高温以甲烷为主。 */
    static final double FT_METHANE_FLOOR = 0.04;
    static final double FT_METHANE_MID = 700;
    static final double FT_METHANE_WIDTH = 35;

    private final String nameZh;
    private final String nameEn;
    private final String equation;
    private final List<Term> reactants;
    private final List<Term> products;
    private final double enthalpy;
    private final double entropy;
    private final boolean reversible;
    private final double activation;
    private final double prefactor;
    private final double pressureHalf;
    private final Catalyst catalyst;
    private final ReactorSettings defaults;
    private final double industrialMin;
    private final double industrialMax;
    private final double baseBatches;
    private final List<ItemCount> inputs;
    private final List<ItemCount> outputs;
    private final ItemCount byproduct;

    Reaction(String nameZh, String nameEn, String equation, List<Term> reactants, List<Term> products,
            double enthalpy, double entropy, boolean reversible, double activation, double prefactor, double pressureHalf,
            Catalyst catalyst, ReactorSettings defaults, double industrialMin, double industrialMax, double baseBatches,
            List<ItemCount> inputs, List<ItemCount> outputs, ItemCount byproduct) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.equation = equation;
        this.reactants = reactants;
        this.products = products;
        this.enthalpy = enthalpy;
        this.entropy = entropy;
        this.reversible = reversible;
        this.activation = activation;
        this.prefactor = prefactor;
        this.pressureHalf = pressureHalf;
        this.catalyst = catalyst;
        this.defaults = defaults;
        this.industrialMin = industrialMin;
        this.industrialMax = industrialMax;
        this.baseBatches = baseBatches;
        this.inputs = inputs;
        this.outputs = outputs;
        this.byproduct = byproduct;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 反应式，可逆的用 ⇄（原版位图字体里有这个字；⇌ 落到 unifont，只有 4 像素宽）。 */
    public String equation() {
        return this.equation;
    }

    /** 平衡计算用的反应物（按化学计量数进料）。 */
    public List<Term> reactants() {
        return this.reactants;
    }

    public List<Term> products() {
        return this.products;
    }

    /** 标准反应焓（J/mol，按反应式写的那么多摩尔），负的放热。 */
    public double enthalpy() {
        return this.enthalpy;
    }

    /** 标准反应熵（J/(mol·K)）。不可逆的反应不用。 */
    public double entropy() {
        return this.entropy;
    }

    /**
     * 这个温度下的反应焓（J/mol）：反应式里的物质都有 {@link Shomate} 的数据时按热容修正，不然用 298 K 的值。
     */
    public double enthalpyAt(double temperature) {
        if (!this.shomate()) {
            return this.enthalpy;
        }
        double sum = 0;
        for (Term term : this.products) {
            sum += term.moles() * Shomate.enthalpy(term.species(), temperature);
        }
        for (Term term : this.reactants) {
            sum -= term.moles() * Shomate.enthalpy(term.species(), temperature);
        }
        return sum;
    }

    /** 这个温度下的反应熵（J/(mol·K)），同 {@link #enthalpyAt}。 */
    public double entropyAt(double temperature) {
        if (!this.shomate()) {
            return this.entropy;
        }
        double sum = 0;
        for (Term term : this.products) {
            sum += term.moles() * Shomate.entropy(term.species(), temperature);
        }
        for (Term term : this.reactants) {
            sum -= term.moles() * Shomate.entropy(term.species(), temperature);
        }
        return sum;
    }

    private boolean shomate() {
        for (Term term : this.reactants) {
            if (!Shomate.covers(term.species())) {
                return false;
            }
        }
        for (Term term : this.products) {
            if (!Shomate.covers(term.species())) {
                return false;
            }
        }
        return true;
    }

    /** 受不受平衡限制。费托合成不受，看选择性。 */
    public boolean reversible() {
        return this.reversible;
    }

    /** 活化能（J/mol）。 */
    public double activation() {
        return this.activation;
    }

    /** 达姆科勒数的指前因子。 */
    public double prefactor() {
        return this.prefactor;
    }

    /** 压强加快反应的半饱和压强（bar）：压强等于它时反应快慢是高压极限的一半。 */
    public double pressureHalf() {
        return this.pressureHalf;
    }

    /** 用哪种催化剂。 */
    public Catalyst catalyst() {
        return this.catalyst;
    }

    /** 研究完科技以后放下的反应釜默认的工况：教科书的工况。 */
    public ReactorSettings defaults() {
        return this.defaults;
    }

    /** 现实的工况温度下限（K），产量最高的温度应当落在这一段里。 */
    public double industrialMin() {
        return this.industrialMin;
    }

    public double industrialMax() {
        return this.industrialMax;
    }

    /** 单程转化率为 1 时每分钟做几批。 */
    public double baseBatches() {
        return this.baseBatches;
    }

    /** 一批用掉的物品。 */
    public List<ItemCount> inputs() {
        return this.inputs;
    }

    /** 一批做出的物品；第一样是主产物。费托合成的主产物按选择性打折。 */
    public List<ItemCount> outputs() {
        return this.outputs;
    }

    public ItemCount mainProduct() {
        return this.outputs.get(0);
    }

    /** 随温度变多的副产物，选择性为 1 时一批出几个；没有为 {@code null}。 */
    public ItemCount byproduct() {
        return this.byproduct;
    }

    /** 碳变成甲烷的比例（只有费托合成有），0–1。 */
    public double methaneSelectivity(double temperature) {
        if (this != FISCHER_TROPSCH) {
            return 0;
        }
        return FT_METHANE_FLOOR + (1 - FT_METHANE_FLOOR) / (1 + Math.exp(-(temperature - FT_METHANE_MID) / FT_METHANE_WIDTH));
    }

    /** 小写的名字，用在语言键里。 */
    public String id() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 按名字找，找不到为 {@code null}。 */
    public static Reaction byName(String name) {
        for (Reaction reaction : values()) {
            if (reaction.name().equals(name)) {
                return reaction;
            }
        }
        return null;
    }
}
