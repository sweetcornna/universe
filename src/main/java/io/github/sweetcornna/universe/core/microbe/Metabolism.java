package io.github.sweetcornna.universe.core.microbe;

import org.jspecify.annotations.Nullable;

/**
 * 微生物靠什么活（ADR-0036 §五）。每种代谢带一个标志性的基因，菌天生就有。
 * 顺序和名字是存档格式的一部分，只往后加。
 */
public enum Metabolism {
    /** 产氧光合：蓝藻。 */
    OXYGENIC_PHOTOTROPH("产氧光合", "Oxygenic Phototroph", Gene.OXYGENIC_PHOTOSYNTHESIS),
    /** 化能自养：氧化亚铁和硫化物取能。 */
    CHEMOLITHOTROPH("化能自养", "Chemolithotroph", Gene.IRON_SULFUR_OXIDATION),
    /** 产甲烷：二氧化碳加氢还原成甲烷，只有古菌会。 */
    METHANOGEN("产甲烷", "Methanogen", Gene.METHANOGENESIS),
    /** 固氮：把空气里的氮还原成氨。 */
    DIAZOTROPH("固氮", "Diazotroph", Gene.NITROGENASE),
    /** 异养：吃现成的有机物。 */
    HETEROTROPH("异养", "Heterotroph", null);

    private final String nameZh;
    private final String nameEn;
    private final @Nullable Gene signature;

    Metabolism(String nameZh, String nameEn, @Nullable Gene signature) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.signature = signature;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    /** 这种代谢天生带的基因；异养菌没有。 */
    public @Nullable Gene signature() {
        return this.signature;
    }
}
