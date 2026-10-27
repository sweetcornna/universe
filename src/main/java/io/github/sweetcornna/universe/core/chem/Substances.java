package io.github.sweetcornna.universe.core.chem;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.core.chem.Substance.Kind;

/**
 * 物品是什么做的（ADR-0036 §一），按物品的注册名查。矿石照现实找对应的矿物（钛矿是钛铁矿，有机晶体是卡帕奇石），
 * 戴森球计划里现实没有的东西写"?"。零件、设备不算物质，不在表里。
 */
public final class Substances {
    private static final Map<String, Substance> TABLE = build();

    private Substances() {
    }

    /** 这样物品的成分；不在表里（零件、设备、生物组织）为空。 */
    public static Optional<Substance> of(String item) {
        return Optional.ofNullable(TABLE.get(item));
    }

    /** 气体对应的那一条。 */
    public static Substance of(Gas gas) {
        return TABLE.get(item(gas));
    }

    /** 表里的全部，按登记的顺序。 */
    public static Collection<Substance> all() {
        return Collections.unmodifiableCollection(TABLE.values());
    }

    /** 气体物品的注册名。 */
    public static String item(Gas gas) {
        return "universe:" + gas.item();
    }

    private static Map<String, Substance> build() {
        Map<String, Substance> table = new LinkedHashMap<>();
        Builder b = new Builder(table);

        // 星球矿石掉的东西，照现实找对应的矿物（ADR-0036 §一的表）；铁、铜矿掉原版的粗矿
        b.pure("minecraft:raw_iron", "赤铁矿", "Hematite", "Fe₂O₃");
        b.pure("universe:raw_magnetite", "磁铁矿", "Magnetite", "Fe₃O₄");
        b.pure("minecraft:raw_copper", "黄铜矿", "Chalcopyrite", "CuFeS₂");
        b.pure("universe:raw_titanium", "钛铁矿", "Ilmenite", "FeTiO₃");
        b.pure("universe:raw_silicon", "石英", "Quartz", "SiO₂");
        b.pure("universe:sulfur", "自然硫", "Native Sulfur", "S₈");
        b.pure("universe:fire_ice", "甲烷水合物", "Methane Hydrate", "CH₄·5.75H₂O");
        b.pure("universe:optical_crystal", "冰洲石", "Iceland Spar", "CaCO₃");
        b.pure("universe:organic_crystal", "卡帕奇石", "Karpatite", "C₂₄H₁₂");
        b.pure("universe:raw_uranium", "晶质铀矿", "Uraninite", "UO₂");
        b.pure("universe:salt", "岩盐", "Halite", "NaCl");
        // 奇异物质在现实里只是一种假说
        b.unknown("universe:strange_matter", "奇异物质", "Strange Matter");
        b.mixture("minecraft:coal", "煤", "Coal", "C");

        // 冶炼出来的
        b.pure("minecraft:iron_ingot", "铁", "Iron", "Fe");
        b.pure("minecraft:copper_ingot", "铜", "Copper", "Cu");
        b.pure("universe:titanium_ingot", "钛", "Titanium", "Ti");
        b.pure("universe:silicon", "硅", "Silicon", "Si");
        b.pure("universe:uranium_ingot", "铀", "Uranium", "U");
        b.pure("universe:magnet", "磁铁", "Magnet", "Fe₃O₄");
        b.pure("universe:nickel_ingot", "镍", "Nickel", "Ni");
        b.mixture("universe:steel", "钢", "Steel", "Fe-C");
        b.mixture("universe:titanium_alloy", "钛合金", "Titanium Alloy", "Ti-6Al-4V");

        // 原版里几样常见的
        b.pure("minecraft:raw_gold", "自然金", "Native Gold", "Au");
        b.pure("minecraft:gold_ingot", "金", "Gold", "Au");
        b.pure("minecraft:diamond", "金刚石", "Diamond", "C");
        b.pure("minecraft:quartz", "石英", "Quartz", "SiO₂");
        b.pure("minecraft:amethyst_shard", "紫水晶", "Amethyst", "SiO₂");
        b.pure("minecraft:emerald", "祖母绿", "Emerald", "Be₃Al₂Si₆O₁₈");
        b.pure("minecraft:calcite", "方解石", "Calcite", "CaCO₃");
        b.pure("minecraft:clay_ball", "高岭石", "Kaolinite", "Al₂Si₂O₅(OH)₄");
        b.pure("minecraft:sugar", "蔗糖", "Sucrose", "C₁₂H₂₂O₁₁");
        b.pure("minecraft:bone_meal", "羟基磷灰石", "Hydroxyapatite", "Ca₅(PO₄)₃(OH)");
        b.pure("minecraft:ice", "冰", "Ice", "H₂O");
        b.pure("minecraft:packed_ice", "冰", "Ice", "H₂O");
        b.pure("minecraft:snowball", "雪", "Snow", "H₂O");
        b.mixture("minecraft:charcoal", "木炭", "Charcoal", "C");
        b.mixture("minecraft:sand", "石英砂", "Quartz Sand", "SiO₂");
        b.mixture("minecraft:glass", "钠钙玻璃", "Soda-lime Glass", "SiO₂ + Na₂O + CaO");
        b.mixture("minecraft:obsidian", "火山玻璃", "Volcanic Glass", "SiO₂");
        b.mixture("minecraft:gunpowder", "黑火药", "Black Powder", "KNO₃ + C + S");
        b.unknown("minecraft:redstone", "红石", "Redstone");
        b.unknown("minecraft:glowstone_dust", "萤石粉", "Glowstone");

        // 流体（物品化，ADR-0011）和化工品
        b.pure("universe:water", "水", "Water", "H₂O");
        b.mixture("universe:crude_oil", "原油", "Crude Oil", "CₙHₘ");
        b.mixture("universe:refined_oil", "精炼油", "Refined Oil", "CₙH₂ₙ₊₂");
        b.pure("universe:deuterium", "重氢", "Deuterium", "D₂");
        b.pure("universe:sulfuric_acid", "硫酸", "Sulfuric Acid", "H₂SO₄");
        b.pure("universe:sodium", "钠", "Sodium", "Na");
        b.pure("universe:plastic", "聚乙烯", "Polyethylene", "(C₂H₄)ₙ");
        b.pure("universe:energetic_graphite", "石墨", "Graphite", "C");
        b.pure("universe:graphene", "石墨烯", "Graphene", "C");
        b.pure("universe:carbon_nanotube", "碳纳米管", "Carbon Nanotube", "C");
        b.pure("universe:synthetic_diamond", "合成金刚石", "Synthetic Diamond", "C");
        b.pure("universe:crystal_silicon", "单晶硅", "Monocrystalline Silicon", "Si");
        // 掺一点二氧化钛的石英玻璃几乎不热胀冷缩（康宁的 ULE 玻璃）
        b.mixture("universe:titanium_glass", "钛硅玻璃", "Titania-silica Glass", "SiO₂ + TiO₂");

        // 气体
        for (Gas gas : Gas.values()) {
            b.pure(item(gas), gas.nameZh(), gas.nameEn(), gas.formula());
        }

        // M20 的化工品（ADR-0036 §三、§四）
        b.pure("universe:sodium_hydroxide", "氢氧化钠", "Sodium Hydroxide", "NaOH");
        b.pure("universe:nitric_acid", "硝酸", "Nitric Acid", "HNO₃");
        b.pure("universe:ammonium_nitrate", "硝酸铵", "Ammonium Nitrate", "NH₄NO₃");
        b.pure("universe:titanium_tetrachloride", "四氯化钛", "Titanium Tetrachloride", "TiCl₄");
        // 精制四氯化钛时除掉的三氯氧钒
        b.mixture("universe:vanadium_slag", "钒渣", "Vanadium Slag", "VOCl₃");
        b.mixture("universe:copper_leach_solution", "含铜浸出液", "Copper Leach Solution", "CuSO₄ + H₂SO₄");
        // 现实里难浸的金矿先用细菌氧化掉包着金的硫化物，再用氰化物把金溶出来
        b.mixture("universe:gold_leach_solution", "含金浸出液", "Gold Leach Solution", "Na[Au(CN)₂]");
        b.mixture("universe:methalox_fuel", "甲烷液氧燃料", "Methalox", "CH₄ + 2O₂");
        // 催化剂：合成氨的熔铁加钾、铝的助剂；接触法的钒催化剂加硫酸钾；镍载在氧化铝上
        b.mixture("universe:iron_catalyst", "熔铁催化剂", "Fused Iron Catalyst", "Fe + K₂O + Al₂O₃");
        b.mixture("universe:vanadium_catalyst", "钒催化剂", "Vanadium Catalyst", "V₂O₅ + K₂SO₄");
        b.mixture("universe:nickel_catalyst", "镍催化剂", "Nickel Catalyst", "Ni + Al₂O₃");

        // M20 的生物耗材（ADR-0036 §五）
        b.mixture("universe:agar", "琼脂", "Agar", "(C₁₂H₁₈O₉)ₙ");
        b.mixture("universe:culture_medium", "培养基", "Culture Medium", "(C₁₂H₁₈O₉)ₙ + C₁₂H₂₂O₁₁ + NaCl");
        b.mixture("universe:editing_kit", "编辑试剂", "Editing Kit", "Cas9 + sgRNA");
        // 微藻干重的经验式
        b.mixture("universe:algal_biomass", "藻类生物质", "Algal Biomass", "CH₁.₇O₀.₄N₀.₁₅");

        // 戴森球计划里现实没有的
        b.unknown("universe:titanium_crystal", "钛晶石", "Titanium Crystal");
        b.unknown("universe:casimir_crystal", "卡西米尔晶体", "Casimir Crystal");
        b.unknown("universe:critical_photon", "临界光子", "Critical Photon");
        b.unknown("universe:antimatter", "反物质", "Antimatter");
        b.unknown("universe:annihilation_constraint_sphere", "湮灭约束球", "Annihilation Constraint Sphere");
        b.unknown("universe:graviton_lens", "引力透镜", "Graviton Lens");
        b.unknown("universe:energy_shard", "能量碎片", "Energy Shard");
        b.unknown("universe:silicon_neuron", "硅基神经元", "Silicon-based Neuron");
        b.unknown("universe:matter_recombinator", "物质重组器", "Matter Recombinator");
        b.unknown("universe:negentropy_singularity", "负熵奇点", "Negentropy Singularity");
        b.unknown("universe:core_element", "核心素", "Core Element");
        return table;
    }

    private record Builder(Map<String, Substance> table) {
        void pure(String item, String zh, String en, String formula) {
            this.put(new Substance(item, zh, en, formula, Kind.PURE));
        }

        void mixture(String item, String zh, String en, String formula) {
            this.put(new Substance(item, zh, en, formula, Kind.MIXTURE));
        }

        void unknown(String item, String zh, String en) {
            this.put(new Substance(item, zh, en, Substance.UNKNOWN_FORMULA, Kind.UNKNOWN));
        }

        private void put(Substance substance) {
            if (this.table.putIfAbsent(substance.item(), substance) != null) {
                throw new IllegalStateException("重复的物质：" + substance.item());
            }
        }
    }
}
