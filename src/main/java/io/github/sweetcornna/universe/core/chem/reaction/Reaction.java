package io.github.sweetcornna.universe.core.chem.reaction;

import static io.github.sweetcornna.universe.core.chem.reaction.Species.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 反应釜里做的反应（ADR-0036 §四、ADR-0037）。顺序和名字是存档格式的一部分（反应釜按名字存选的反应，全服的反应手册按名字记），只往后加。
 *
 * <p>反应式就是物品：一批用掉的物品数等于反应物的系数，做出的等于产物的系数（{@link #inputs()}、{@link #outputs()}），
 * {@link #equation()} 由同一组系数和化学式拼出来，显示的和实际的不会对不上。单元测试按化学式查原子守恒。
 *
 * <p>平衡和反应热按 {@link SpeciesThermo} 随温度修正：合成氨在 450 °C、200 bar 的平衡氨含量算出来约 25%，100 atm 时 16.0%
 * （手册 16.4%），300 atm 时 31.8%（手册 35.5%，差的是高压下的非理想性，这里按理想气体算）。不可逆的反应平衡转化率按 1，
 * 反应焓只用来显示。
 *
 * <p>反应快慢写成无量纲的达姆科勒数 Da = A × e^(−Ea/RT) × 催化剂活性 × P/(P + P½) × 高温失活，单程转化率 = 平衡转化率 × (1 − e^(−Da))。
 * 指前因子 A 调到在教科书的压强下产量最高的温度落在现实的工况里（{@link #industrialMin()}–{@link #industrialMax()}）。
 * 放热又不可逆的反应没有平衡限制，靠催化剂烧结（{@link Catalyst#sinterTemperature()}）或 {@link #limitTemperature()}
 * （液相没了、产物分解、积碳）给出上限。
 */
public enum Reaction {
    HABER(def("合成氨", "Haber–Bosch", Category.NITROGEN, "catalysis")
            .in(NITROGEN, 1).in(HYDROGEN, 3).out(AMMONIA, 2).reversible()
            .kinetics(100_000, 5.7e7, 30).catalyst(Catalyst.IRON).defaults(723, 200).window(673, 773).batches(30)),
    CONTACT(def("接触法", "Contact Process", Category.SULFUR, "catalysis")
            .in(SULFUR_DIOXIDE, 2).in(OXYGEN, 1).out(SULFUR_TRIOXIDE, 2).reversible()
            .kinetics(90_000, 4.15e7, 0.5).catalyst(Catalyst.VANADIUM_PENTOXIDE).defaults(703, 1.5).window(673, 723).batches(20)),
    SABATIER(def("萨巴蒂尔反应", "Sabatier Reaction", Category.CARBON, "synthetic_fuels")
            .in(CARBON_DIOXIDE, 1).in(HYDROGEN, 4).out(METHANE, 1).out(WATER, 2).reversible()
            .kinetics(80_000, 4.16e7, 2).catalyst(Catalyst.NICKEL).defaults(623, 5).window(573, 673).batches(20)),
    STEAM_REFORMING(def("甲烷蒸汽重整", "Steam Reforming", Category.CARBON, "synthetic_fuels")
            .in(METHANE, 1).in(WATER, 1).out(CARBON_MONOXIDE, 1).out(HYDROGEN, 3).reversible()
            .kinetics(240_000, 1.96e11, 5).catalyst(Catalyst.NICKEL).defaults(1123, 25).window(1073, 1173).batches(30)),
    /** 高于 350 °C 铁上积碳、甲烷变多，长链烃的产量掉下来。 */
    FISCHER_TROPSCH(def("费托合成", "Fischer–Tropsch", Category.ORGANIC, "synthetic_fuels")
            .in(CARBON_MONOXIDE, 2).in(HYDROGEN, 4).out(HYDROCARBON, 1).out(WATER, 2)
            .kinetics(100_000, 3.06e10, 10).catalyst(Catalyst.IRON).limit(623).defaults(548, 25).window(473, 623).batches(20)),
    AMMONIA_CRACKING(def("氨分解", "Ammonia Cracking", Category.NITROGEN, "catalysis")
            .in(AMMONIA, 2).out(NITROGEN, 1).out(HYDROGEN, 3).reversible()
            .kinetics(90_000, 9e5, 1).catalyst(Catalyst.NICKEL).defaults(873, 5).window(773, 1273).batches(30)),
    /** 950 °C 往上一氧化氮分解、铂损失快。 */
    AMMONIA_OXIDATION(def("氨氧化", "Ammonia Oxidation", Category.NITROGEN, "catalysis")
            .in(AMMONIA, 4).in(OXYGEN, 5).out(NITRIC_OXIDE, 4).out(WATER, 6)
            .kinetics(60_000, 2.4e3, 1).catalyst(Catalyst.PLATINUM_RHODIUM).limit(1223).defaults(1173, 4).window(1073, 1253).batches(20)),
    /** 均相反应，表观活化能是负的：越冷越快。 */
    NO_OXIDATION(def("一氧化氮氧化", "Nitric Oxide Oxidation", Category.NITROGEN, "catalysis")
            .in(NITRIC_OXIDE, 2).in(OXYGEN, 1).out(NITROGEN_DIOXIDE, 2).reversible()
            .kinetics(-4_500, 0.67, 2).defaults(313, 8).window(300, 353).batches(30)),
    /** 吸收塔里有液态水才吸收。 */
    NO2_ABSORPTION(def("二氧化氮吸收", "Nitrogen Dioxide Absorption", Category.NITROGEN, "catalysis")
            .in(NITROGEN_DIOXIDE, 3).in(WATER, 1).out(NITRIC_ACID, 2).out(NITRIC_OXIDE, 1)
            .kinetics(0, 3, 2).limit(373).defaults(313, 8).window(300, 373).batches(30)),
    /** 硝酸铵 210 °C 往上分解。 */
    NEUTRALIZATION(def("中和", "Neutralization", Category.NITROGEN, "catalysis")
            .in(AMMONIA, 1).in(NITRIC_ACID, 1).out(AMMONIUM_NITRATE, 1)
            .kinetics(0, 3, 1).limit(473).defaults(433, 4).window(300, 473).batches(30)),
    /** 现实是液相的氨基甲酸铵脱水，要高压；210 °C 往上缩二脲变多。按不可逆算：气相加固体的平衡和液相差得远。 */
    UREA_SYNTHESIS(def("尿素合成", "Urea Synthesis", Category.NITROGEN, "catalysis")
            .in(AMMONIA, 2).in(CARBON_DIOXIDE, 1).out(UREA, 1).out(WATER, 1)
            .kinetics(50_000, 5e5, 100).limit(483).defaults(463, 150).window(433, 503).batches(20)),
    /** 1150 °C 往上铂网损失快、氰化氢分解。 */
    ANDRUSSOW(def("安德卢梭法", "Andrussow Process", Category.NITROGEN, "catalysis")
            .in(METHANE, 2).in(AMMONIA, 2).in(OXYGEN, 3).out(HYDROGEN_CYANIDE, 2).out(WATER, 6)
            .kinetics(80_000, 1e4, 1).catalyst(Catalyst.PLATINUM_RHODIUM).limit(1423).defaults(1373, 2).window(1273, 1453).batches(20)),
    BMA(def("BMA 法", "BMA Process", Category.NITROGEN, "catalysis")
            .in(METHANE, 1).in(AMMONIA, 1).out(HYDROGEN_CYANIDE, 1).out(HYDROGEN, 3).reversible()
            .kinetics(150_000, 1e6, 1).catalyst(Catalyst.PLATINUM_RHODIUM).defaults(1523, 1).window(1473, 1560).batches(20)),
    /** 吸收塔在 70–120 °C，硫酸 300 °C 往上分解。 */
    SO3_ABSORPTION(def("三氧化硫吸收", "Sulfur Trioxide Absorption", Category.SULFUR, "catalysis")
            .in(SULFUR_TRIOXIDE, 1).in(WATER, 1).out(SULFURIC_ACID, 1)
            .kinetics(0, 3, 1).limit(423).defaults(353, 1.5).window(300, 423).batches(30)),
    SULFUR_COMBUSTION(def("硫燃烧", "Sulfur Combustion", Category.SULFUR, "catalysis")
            .in(SULFUR, 1).in(OXYGEN, 1).out(SULFUR_DIOXIDE, 1)
            .kinetics(100_000, 3.2e4, 1).defaults(1373, 1.5).window(1173, 1560).batches(30)),
    CO_METHANATION(def("一氧化碳甲烷化", "CO Methanation", Category.CARBON, "synthetic_fuels")
            .in(CARBON_MONOXIDE, 1).in(HYDROGEN, 3).out(METHANE, 1).out(WATER, 1).reversible()
            .kinetics(100_000, 1.6e9, 5).catalyst(Catalyst.NICKEL).defaults(603, 25).window(523, 723).batches(20)),
    DRY_REFORMING(def("干重整", "Dry Reforming", Category.CARBON, "synthetic_fuels")
            .in(METHANE, 1).in(CARBON_DIOXIDE, 1).out(CARBON_MONOXIDE, 2).out(HYDROGEN, 2).reversible()
            .kinetics(150_000, 2.3e7, 1).catalyst(Catalyst.NICKEL).defaults(1123, 1).window(1073, 1173).batches(20)),
    /** 不加催化剂的部分氧化（德士古、壳牌气化炉），1300 °C 往上。 */
    PARTIAL_OXIDATION(def("甲烷部分氧化", "Partial Oxidation", Category.CARBON, "synthetic_fuels")
            .in(METHANE, 2).in(OXYGEN, 1).out(CARBON_MONOXIDE, 2).out(HYDROGEN, 4)
            .kinetics(200_000, 2e7, 5).defaults(1523, 30).window(1373, 1560).batches(20)),
    METHANE_PYROLYSIS(def("甲烷热解", "Methane Pyrolysis", Category.CARBON, "synthetic_fuels")
            .in(METHANE, 1).out(CARBON, 1).out(HYDROGEN, 2).reversible()
            .kinetics(350_000, 1.5e13, 1).defaults(1473, 1).window(1273, 1560).batches(20)),
    WATER_GAS_SHIFT(def("水煤气变换", "Water–Gas Shift", Category.CARBON, "synthetic_fuels")
            .in(CARBON_MONOXIDE, 1).in(WATER, 1).out(CARBON_DIOXIDE, 1).out(HYDROGEN, 1).reversible()
            .kinetics(110_000, 1e9, 5).catalyst(Catalyst.IRON).defaults(673, 25).window(623, 723).batches(30)),
    BOUDOUARD(def("布杜阿尔反应", "Boudouard Reaction", Category.CARBON, "synthetic_fuels")
            .in(CARBON_MONOXIDE, 2).out(CARBON_DIOXIDE, 1).out(CARBON, 1).reversible()
            .kinetics(120_000, 4e8, 1).catalyst(Catalyst.IRON).defaults(823, 1).window(773, 973).batches(20)),
    COAL_GASIFICATION(def("煤气化", "Coal Gasification", Category.CARBON, "synthetic_fuels")
            .in(COAL, 1).in(WATER, 1).out(CARBON_MONOXIDE, 1).out(HYDROGEN, 1).reversible()
            .kinetics(200_000, 4e8, 5).defaults(1473, 30).window(1173, 1560).batches(20)),
    HYDROGEN_COMBUSTION(def("氢燃烧", "Hydrogen Combustion", Category.CARBON, "synthetic_fuels")
            .in(HYDROGEN, 2).in(OXYGEN, 1).out(WATER, 2)
            .kinetics(150_000, 1e8, 1).defaults(1173, 1).window(873, 1560).batches(30)),
    METHANOL_SYNTHESIS(def("甲醇合成", "Methanol Synthesis", Category.ORGANIC, "synthetic_fuels")
            .in(CARBON_MONOXIDE, 1).in(HYDROGEN, 2).out(METHANOL, 1).reversible()
            .kinetics(80_000, 1e8, 20).catalyst(Catalyst.COPPER_ZINC).defaults(523, 80).window(493, 573).batches(20)),
    CO2_TO_METHANOL(def("二氧化碳制甲醇", "CO₂ to Methanol", Category.ORGANIC, "synthetic_fuels")
            .in(CARBON_DIOXIDE, 1).in(HYDROGEN, 3).out(METHANOL, 1).out(WATER, 1).reversible()
            .kinetics(70_000, 1e7, 20).catalyst(Catalyst.COPPER_ZINC).defaults(523, 60).window(473, 573).batches(30)),
    METHANOL_TO_OLEFINS(def("甲醇制烯烃", "Methanol to Olefins", Category.ORGANIC, "synthetic_fuels")
            .in(METHANOL, 2).out(ETHYLENE, 1).out(WATER, 2)
            .kinetics(100_000, 5e7, 1).catalyst(Catalyst.ZEOLITE).defaults(723, 2).window(673, 823).batches(20)),
    POLYMERIZATION(def("乙烯聚合", "Ethylene Polymerization", Category.ORGANIC, "synthetic_fuels")
            .in(ETHYLENE, 1).out(POLYETHYLENE, 1)
            .kinetics(40_000, 3e6, 10).catalyst(Catalyst.ZIEGLER_NATTA).defaults(353, 20).window(333, 413).batches(30)),
    HCL_SYNTHESIS(def("氯化氢合成", "Hydrogen Chloride Synthesis", Category.CHLORINE, "electrochemistry")
            .in(HYDROGEN, 1).in(CHLORINE, 1).out(HYDROGEN_CHLORIDE, 2)
            .kinetics(100_000, 1e6, 1).defaults(1073, 1).window(773, 1560).batches(30)),
    DEACON(def("迪肯法", "Deacon Process", Category.CHLORINE, "electrochemistry")
            .in(HYDROGEN_CHLORIDE, 4).in(OXYGEN, 1).out(CHLORINE, 2).out(WATER, 2).reversible()
            .kinetics(80_000, 5e6, 1).catalyst(Catalyst.COPPER_CHLORIDE).defaults(703, 2).window(653, 753).batches(20));

    /** 手册里的分类。 */
    public enum Category {
        NITROGEN("氮", "Nitrogen"),
        SULFUR("硫", "Sulfur"),
        CARBON("碳和合成气", "Carbon and Syngas"),
        ORGANIC("有机", "Organic"),
        CHLORINE("氯", "Chlorine");

        private final String nameZh;
        private final String nameEn;

        Category(String nameZh, String nameEn) {
            this.nameZh = nameZh;
            this.nameEn = nameEn;
        }

        public String nameZh() {
            return this.nameZh;
        }

        public String nameEn() {
            return this.nameEn;
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 高于 {@link #limitTemperature()} 后，每高这么多 K 反应快慢降到 1/e。比催化剂烧结陡：要压过阿伦尼乌斯的增长。 */
    public static final double LIMIT_WIDTH = 10;

    private final String nameZh;
    private final String nameEn;
    private final Category category;
    private final String research;
    private final List<Term> reactants;
    /** 反应物的物品（注册名），自由投料的反应釜每刻都拿它认反应，构造时算好。 */
    private final Set<String> reactantItems;
    private final List<Term> products;
    private final boolean reversible;
    private final double activation;
    private final double prefactor;
    private final double pressureHalf;
    private final Catalyst catalyst;
    private final double limitTemperature;
    private final ReactorSettings defaults;
    private final double industrialMin;
    private final double industrialMax;
    private final double baseBatches;

    Reaction(Def def) {
        this.nameZh = def.nameZh;
        this.nameEn = def.nameEn;
        this.category = def.category;
        this.research = def.research;
        this.reactants = List.copyOf(def.reactants);
        Set<String> items = new HashSet<>();
        for (Term term : this.reactants) {
            items.add(term.species().item());
        }
        this.reactantItems = Set.copyOf(items);
        this.products = List.copyOf(def.products);
        this.reversible = def.reversible;
        this.activation = def.activation;
        this.prefactor = def.prefactor;
        this.pressureHalf = def.pressureHalf;
        this.catalyst = def.catalyst;
        this.limitTemperature = def.limit;
        this.defaults = def.defaults;
        this.industrialMin = def.industrialMin;
        this.industrialMax = def.industrialMax;
        this.baseBatches = def.batches;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public Category category() {
        return this.category;
    }

    /** 研究了这项科技，手册里就列出这条反应（ADR-0037 §四）。 */
    public String research() {
        return this.research;
    }

    /** 反应式，系数和物品数一样；可逆的用 ⇄（原版位图字体里有这个字；⇌ 落到 unifont，只有 4 像素宽）。 */
    public String equation() {
        return side(this.reactants) + (this.reversible ? " ⇄ " : " → ") + side(this.products);
    }

    private static String side(List<Term> terms) {
        StringBuilder sb = new StringBuilder();
        for (Term term : terms) {
            if (!sb.isEmpty()) {
                sb.append(" + ");
            }
            int moles = (int) term.moles();
            if (moles != 1) {
                sb.append(moles);
            }
            sb.append(term.species().formula());
        }
        return sb.toString();
    }

    /** 反应物（系数是整数，就是物品数）。 */
    public List<Term> reactants() {
        return this.reactants;
    }

    public List<Term> products() {
        return this.products;
    }

    /** 298 K 的标准反应焓（J/mol，按反应式写的那么多摩尔），负的放热。 */
    public double enthalpy() {
        return this.enthalpyAt(SpeciesThermo.T0);
    }

    /** 298 K 的标准反应熵（J/(mol·K)）。 */
    public double entropy() {
        return this.entropyAt(SpeciesThermo.T0);
    }

    /** 这个温度下的反应焓（J/mol）。 */
    public double enthalpyAt(double temperature) {
        double sum = 0;
        for (Term term : this.products) {
            sum += term.moles() * SpeciesThermo.enthalpy(term.species(), temperature);
        }
        for (Term term : this.reactants) {
            sum -= term.moles() * SpeciesThermo.enthalpy(term.species(), temperature);
        }
        return sum;
    }

    /** 这个温度下的反应熵（J/(mol·K)）。 */
    public double entropyAt(double temperature) {
        double sum = 0;
        for (Term term : this.products) {
            sum += term.moles() * SpeciesThermo.entropy(term.species(), temperature);
        }
        for (Term term : this.reactants) {
            sum -= term.moles() * SpeciesThermo.entropy(term.species(), temperature);
        }
        return sum;
    }

    /** 受不受平衡限制（反应式里写 ⇄）。 */
    public boolean reversible() {
        return this.reversible;
    }

    /** 活化能（J/mol）；一氧化氮氧化是负的。 */
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

    /** 用哪种催化剂；不用催化剂的（燃烧、吸收、中和、热解）为 {@code null}。 */
    public Catalyst catalyst() {
        return this.catalyst;
    }

    /** 高温失活的温度（K）：液相没了、产物分解、积碳；没有为正无穷。 */
    public double limitTemperature() {
        return this.limitTemperature;
    }

    /** 这个温度下高温失活剩下的倍数，0–1。 */
    public double limitFactor(double temperature) {
        return temperature <= this.limitTemperature ? 1 : Math.exp(-(temperature - this.limitTemperature) / LIMIT_WIDTH);
    }

    /** 教科书的工况。 */
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

    /** 一批用掉的物品，就是反应物和系数。 */
    public List<ItemCount> inputs() {
        return items(this.reactants);
    }

    /** 一批做出的物品，就是产物和系数；第一样是主产物。 */
    public List<ItemCount> outputs() {
        return items(this.products);
    }

    public ItemCount mainProduct() {
        return this.outputs().getFirst();
    }

    private static List<ItemCount> items(List<Term> terms) {
        List<ItemCount> list = new ArrayList<>(terms.size());
        for (Term term : terms) {
            list.add(new ItemCount(term.species().item(), (int) term.moles()));
        }
        return List.copyOf(list);
    }

    /** 反应物的物品，认反应用（{@link ReactionMatcher}）。 */
    public Set<String> reactantItems() {
        return this.reactantItems;
    }

    /** 反应物里有没有这种物质。 */
    public boolean consumes(Species species) {
        for (Term term : this.reactants) {
            if (term.species() == species) {
                return true;
            }
        }
        return false;
    }

    /** 小写的名字，用在语言键和配方 ID 里。 */
    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
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

    private static Def def(String nameZh, String nameEn, Category category, String research) {
        return new Def(nameZh, nameEn, category, research);
    }

    /** 写反应表用的。 */
    private static final class Def {
        private final String nameZh;
        private final String nameEn;
        private final Category category;
        private final String research;
        private final List<Term> reactants = new ArrayList<>();
        private final List<Term> products = new ArrayList<>();
        private boolean reversible;
        private double activation;
        private double prefactor;
        private double pressureHalf;
        private Catalyst catalyst;
        private double limit = Double.POSITIVE_INFINITY;
        private ReactorSettings defaults;
        private double industrialMin;
        private double industrialMax;
        private double batches;

        Def(String nameZh, String nameEn, Category category, String research) {
            this.nameZh = nameZh;
            this.nameEn = nameEn;
            this.category = category;
            this.research = research;
        }

        Def in(Species species, int moles) {
            this.reactants.add(new Term(species, moles));
            return this;
        }

        Def out(Species species, int moles) {
            this.products.add(new Term(species, moles));
            return this;
        }

        Def reversible() {
            this.reversible = true;
            return this;
        }

        Def kinetics(double activation, double prefactor, double pressureHalf) {
            this.activation = activation;
            this.prefactor = prefactor;
            this.pressureHalf = pressureHalf;
            return this;
        }

        Def catalyst(Catalyst catalyst) {
            this.catalyst = catalyst;
            return this;
        }

        Def limit(double temperature) {
            this.limit = temperature;
            return this;
        }

        Def defaults(double temperature, double pressure) {
            this.defaults = new ReactorSettings(temperature, pressure);
            return this;
        }

        Def window(double min, double max) {
            this.industrialMin = min;
            this.industrialMax = max;
            return this;
        }

        Def batches(double batches) {
            this.batches = batches;
            return this;
        }
    }
}
