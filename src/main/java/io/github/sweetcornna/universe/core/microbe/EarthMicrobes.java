package io.github.sweetcornna.universe.core.microbe;

import java.util.List;
import java.util.Set;

/**
 * 母星上固定的一组真实的菌（ADR-0036 §五）。基数温度（最低、最适、最高）和最适条件下的翻倍时间照文献取，
 * 游戏里按 {@link BioTime} 统一压缩：
 *
 * <ul>
 *   <li>豌豆根瘤菌 Rhizobium leguminosarum：土壤里的快生型根瘤菌，和豌豆、蚕豆共生固氮。4–37 °C 能长，最适 28 °C 上下，
 *       翻倍约 3 小时（快生型根瘤菌 2–4 小时，Bergey's Manual）。</li>
 *   <li>聚球藻 Synechococcus elongatus PCC 7942：淡水的模式蓝藻，杆状。15–45 °C，最适 38 °C 上下，翻倍约 3 小时
 *       （PCC 7942 一般 3–5 小时；快的 UTEX 2973 在 41 °C、强光下 1.9 小时，Yu 等 2015）。</li>
 *   <li>巴氏甲烷八叠球菌 Methanosarcina barkeri：沼泽和沉积物里的产甲烷古菌，成团的球形。10–45 °C，最适 37 °C 上下，
 *       用 H₂/CO₂ 时翻倍 10–20 小时，这里取 15 小时。</li>
 *   <li>嗜酸氧化亚铁硫杆菌 Acidithiobacillus ferrooxidans：酸性矿山排水里氧化 Fe²⁺ 和硫化物，pH 1.3–4.5，
 *       10–40 °C，最适 30–35 °C，翻倍 6–10 小时，这里取 8 小时；生物浸出靠它（Rawlings 2002）。</li>
 *   <li>北极冷杆菌 Psychrobacter arcticus 273-4：从西伯利亚四万年的永久冻土里分出来，−10 到 28 °C 都能长，最适 22 °C
 *       （Bakermans 等 2003）。4 °C 下一两天翻一倍；按这里的温度模型反推，最适时约 6 小时。</li>
 *   <li>费氏弧菌 Aliivibrio fischeri：海水里会发光的弧菌，夏威夷短尾乌贼发光器官里的共生菌。4–32 °C，最适 24–28 °C，
 *       翻倍约 1 小时。</li>
 *   <li>大阪堺菌 Ideonella sakaiensis 201-F6：2016 年在日本堺市一家塑料瓶回收厂外的沉积物里分出来，能把 PET 分解成单体
 *       再吃掉（Yoshida 等 2016）。15–42 °C，最适 30–37 °C；只靠 PET 长得慢，这里取 12 小时。</li>
 * </ul>
 */
public final class EarthMicrobes {
    /** 母星的菌的键都以这个开头。 */
    public static final String KEY_PREFIX = "earth:";

    public static final MicrobeStrain RHIZOBIUM = strain("rhizobium_leguminosarum", "豌豆根瘤菌", "Rhizobium leguminosarum",
            Metabolism.DIAZOTROPH, Morphology.BACILLUS, Set.of(Gene.NITROGENASE), 277.15, 301.15, 310.15, 3, Set.of(SampleSource.SOIL));
    public static final MicrobeStrain SYNECHOCOCCUS = strain("synechococcus_elongatus", "聚球藻", "Synechococcus elongatus",
            Metabolism.OXYGENIC_PHOTOTROPH, Morphology.BACILLUS, Set.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.CARBON_CONCENTRATING),
            288.15, 311.15, 318.15, 3, Set.of(SampleSource.WATER));
    public static final MicrobeStrain METHANOSARCINA = strain("methanosarcina_barkeri", "巴氏甲烷八叠球菌", "Methanosarcina barkeri",
            Metabolism.METHANOGEN, Morphology.ARCHAEON, Set.of(Gene.METHANOGENESIS), 283.15, 310.15, 318.15, 15,
            Set.of(SampleSource.SEDIMENT));
    public static final MicrobeStrain ACIDITHIOBACILLUS = strain("acidithiobacillus_ferrooxidans", "嗜酸氧化亚铁硫杆菌",
            "Acidithiobacillus ferrooxidans", Metabolism.CHEMOLITHOTROPH, Morphology.BACILLUS,
            Set.of(Gene.IRON_SULFUR_OXIDATION, Gene.PROTON_PUMP), 283.15, 305.15, 313.15, 8, Set.of(SampleSource.ORE, SampleSource.ACID));
    public static final MicrobeStrain PSYCHROBACTER = strain("psychrobacter_arcticus", "北极冷杆菌", "Psychrobacter arcticus",
            Metabolism.HETEROTROPH, Morphology.COCCUS, Set.of(Gene.ANTIFREEZE_PROTEIN), 263.15, 295.15, 301.15, 6,
            Set.of(SampleSource.ICE));
    public static final MicrobeStrain ALIIVIBRIO = strain("aliivibrio_fischeri", "费氏弧菌", "Aliivibrio fischeri",
            Metabolism.HETEROTROPH, Morphology.BACILLUS, Set.of(Gene.LUCIFERASE), 277.15, 299.15, 305.15, 1, Set.of(SampleSource.WATER));
    /** 跟着人和垃圾到了每一颗星球（{@link Microbiome} 把它放进每颗星球的垃圾来源里）。 */
    public static final MicrobeStrain IDEONELLA = strain("ideonella_sakaiensis", "大阪堺菌", "Ideonella sakaiensis",
            Metabolism.HETEROTROPH, Morphology.BACILLUS, Set.of(Gene.PETASE), 288.15, 306.15, 315.15, 12, Set.of(SampleSource.JUNK));

    /** 母星上的全部，按表里的顺序。 */
    public static final List<MicrobeStrain> ALL = List.of(RHIZOBIUM, SYNECHOCOCCUS, METHANOSARCINA, ACIDITHIOBACILLUS, PSYCHROBACTER,
            ALIIVIBRIO, IDEONELLA);

    private EarthMicrobes() {
    }

    private static MicrobeStrain strain(String id, String zh, String en, Metabolism metabolism, Morphology morphology, Set<Gene> genes,
            double min, double opt, double max, double doublingHours, Set<SampleSource> sources) {
        return new MicrobeStrain(KEY_PREFIX + id, zh, en, metabolism, morphology, genes, min, opt, max, doublingHours, 1.0, sources);
    }
}
