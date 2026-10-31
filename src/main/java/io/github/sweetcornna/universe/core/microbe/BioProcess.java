package io.github.sweetcornna.universe.core.microbe;

import java.util.ArrayList;
import java.util.List;

/**
 * 生物反应器里的工艺（ADR-0036 §五），每种要菌带一个基因。配方（进什么、出什么）在数据生成里。
 * 顺序和名字是存档格式的一部分，只往后加。
 */
public enum BioProcess {
    /** 生物浸出：粗铜（或粗金）、硫酸、水 → 浸出液，偶尔出镍。世界上约两成的铜靠它。 */
    BIOLEACHING(Gene.IRON_SULFUR_OXIDATION, false, "生物浸出", "Bioleaching"),
    /** 藻类培养：二氧化碳、水 → 氧、藻类生物质；要光。 */
    ALGAE(Gene.OXYGENIC_PHOTOSYNTHESIS, true, "藻类培养", "Algae Culture"),
    /** 生物甲烷化：二氧化碳、氢 → 甲烷、水，常温的萨巴蒂尔反应。 */
    BIOMETHANATION(Gene.METHANOGENESIS, false, "生物甲烷化", "Biomethanation"),
    /** 生物固氮：氮、藻类生物质 → 氨。 */
    NITROGEN_FIXATION(Gene.NITROGENASE, false, "生物固氮", "Nitrogen Fixation"),
    /** 塑料回收：杂物 → 塑料。 */
    PLASTIC_RECYCLING(Gene.PETASE, false, "塑料回收", "Plastic Recycling");

    private final Gene gene;
    private final boolean needsLight;
    private final String nameZh;
    private final String nameEn;

    BioProcess(Gene gene, boolean needsLight, String nameZh, String nameEn) {
        this.gene = gene;
        this.needsLight = needsLight;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
    }

    /** 菌要带的基因。 */
    public Gene gene() {
        return this.gene;
    }

    /** 要光：看得到天，或者接补光的电。 */
    public boolean needsLight() {
        return this.needsLight;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public String id() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 这管菌能跑的工艺。 */
    public static List<BioProcess> available(Organism organism) {
        List<BioProcess> list = new ArrayList<>();
        for (BioProcess process : values()) {
            if (organism.has(process.gene)) {
                list.add(process);
            }
        }
        return list;
    }

    /** 按名字找，找不到为 {@code null}。 */
    public static BioProcess byName(String name) {
        for (BioProcess process : values()) {
            if (process.name().equals(name)) {
                return process;
            }
        }
        return null;
    }
}
