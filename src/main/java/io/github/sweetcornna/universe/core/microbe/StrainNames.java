package io.github.sweetcornna.universe.core.microbe;

import java.util.Locale;
import java.util.Set;

/**
 * 外星菌的名字照双名法起（ADR-0036 §五）：属名是特点加形态，种名用星球名，
 * 中文是"星球名 + 特点 + 形态"（克拉尔嗜热球菌），拉丁名是"Thermococcus kelarensis"。
 *
 * <p>属名里的特点只取一个，按产甲烷、耐温、耐酸、耐盐、耐辐射、铁氧化、固氮、石生的先后挑；
 * 都没有的叫"异"（Exo-）。光合菌的中文词尾叫"藻"（蓝藻的习惯），拉丁词尾照真实的蓝藻属名。
 */
public final class StrainNames {
    private StrainNames() {
    }

    /** 属名的前半截。 */
    enum Trait {
        METHANO("产甲烷", "Methano"),
        THERMO("嗜热", "Thermo"),
        PSYCHRO("嗜冷", "Psychro"),
        ACIDI("嗜酸", "Acidi"),
        HALO("嗜盐", "Halo"),
        RADIO("耐辐射", "Radio"),
        FERRO("嗜铁", "Ferro"),
        AZOTO("固氮", "Azoto"),
        LITHO("石生", "Litho"),
        /** 光合菌没有别的特点时叫"蓝"。 */
        CYANO("蓝", "Cyano"),
        EXO("异", "Exo");

        final String zh;
        final String latin;

        Trait(String zh, String latin) {
            this.zh = zh;
            this.latin = latin;
        }
    }

    /** 两种语言的名字。 */
    public record Name(String zh, String en) {
    }

    /** 起名字。{@code planetZh}、{@code planetEn} 是星球的名字。 */
    public static Name name(Metabolism metabolism, Morphology morphology, Set<Gene> genes, String planetZh, String planetEn) {
        boolean alga = metabolism == Metabolism.OXYGENIC_PHOTOTROPH;
        Trait trait = trait(metabolism, genes, alga);
        String zh = planetZh + trait.zh + (alga ? morphology.algaZh() : morphology.nameZh());
        String genus = trait.latin + (alga ? algaLatin(morphology) : morphology.latin());
        return new Name(zh, genus + " " + epithet(planetEn));
    }

    static Trait trait(Metabolism metabolism, Set<Gene> genes, boolean alga) {
        if (metabolism == Metabolism.METHANOGEN) {
            return Trait.METHANO;
        }
        if (genes.contains(Gene.THERMOSTABLE_ENZYMES)) {
            return Trait.THERMO;
        }
        if (genes.contains(Gene.ANTIFREEZE_PROTEIN)) {
            return Trait.PSYCHRO;
        }
        if (genes.contains(Gene.PROTON_PUMP)) {
            return Trait.ACIDI;
        }
        if (genes.contains(Gene.COMPATIBLE_SOLUTES) && !genes.contains(Gene.UV_SHIELD)) {
            return Trait.HALO;
        }
        if (genes.contains(Gene.DNA_REPAIR)) {
            return Trait.RADIO;
        }
        if (genes.contains(Gene.IRON_SULFUR_OXIDATION)) {
            return Trait.FERRO;
        }
        if (genes.contains(Gene.NITROGENASE)) {
            return Trait.AZOTO;
        }
        if (genes.contains(Gene.UV_SHIELD)) {
            return Trait.LITHO;
        }
        return alga ? Trait.CYANO : Trait.EXO;
    }

    /** 光合菌的拉丁词尾，照真实的蓝藻属名：微囊藻（-cystis）、微鞘藻（-coleus）、节旋藻（-spira）、丝状的（-nema）。 */
    static String algaLatin(Morphology morphology) {
        return switch (morphology) {
            case COCCUS, ARCHAEON -> "cystis";
            case BACILLUS -> "coleus";
            case SPIRILLUM -> "spira";
            case FILAMENT -> "nema";
        };
    }

    /** 种名：星球名去掉末尾的元音加 -ensis（Kelar → kelarensis，Mubei → mubensis）。 */
    static String epithet(String planetEn) {
        StringBuilder letters = new StringBuilder();
        for (char c : planetEn.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c >= 'a' && c <= 'z') {
                letters.append(c);
            }
        }
        String root = letters.toString();
        int end = root.length();
        while (end > 2 && "aeiouy".indexOf(root.charAt(end - 1)) >= 0) {
            end--;
        }
        root = root.substring(0, end);
        return (root.isEmpty() ? "ignot" : root) + "ensis";
    }
}
