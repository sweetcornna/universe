package io.github.sweetcornna.universe.core.terraform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.microbe.Gene;

/**
 * 先锋生物能不能在一颗星球上活下来（ADR-0036 §六）。照现实里火星改造的设想：先放耐极端环境的蓝藻和地衣，
 * 每一样恶劣的条件都要一个对应的基因扛住。
 */
public final class Pioneer {
    /** 冷过这个温度（K）要抗冻蛋白；现实里嗜冷菌在 −20 °C 上下还能长。 */
    public static final double COLD_LIMIT = 253;
    /** 冷过这个温度（K）什么都长不了。 */
    public static final double FROZEN_LIMIT = 200;
    /** 热过这个温度（K）要嗜热酶。 */
    public static final double HEAT_LIMIT = 330;
    /** 热过这个温度（K）什么都长不了：甲烷嗜高热菌的纪录是 122 °C。 */
    public static final double BOILED_LIMIT = 395;
    /** 气压低于这个（标准大气压）紫外太强，要紫外防护色素；也是人的阿姆斯特朗极限（6.3 千帕）。 */
    public static final double UV_PRESSURE = 0.06;

    /** 一样条件，和扛住它要的基因；扛不住的条件没有基因。 */
    public enum Condition {
        /** 要能产氧光合，才算改造星球的先锋。 */
        PHOTOSYNTHESIS(Gene.OXYGENIC_PHOTOSYNTHESIS),
        COLD(Gene.ANTIFREEZE_PROTEIN),
        TOO_COLD(null),
        HEAT(Gene.THERMOSTABLE_ENZYMES),
        TOO_HOT(null),
        RADIATION(Gene.DNA_REPAIR),
        /**
         * 酸：大气把水变酸（{@link Terraform#acidic}），或者星球本来就有毒。按微生物算，不按人的限值：
         * 蓝细菌、地衣在几百 ppm 的一氧化碳里照样长，有的细菌还吃一氧化碳。
         */
        TOXIC(Gene.PROTON_PUMP),
        UV(Gene.UV_SHIELD),
        /** 没有天然水源，只靠播种站浇水：要扛得住干。 */
        DRY(Gene.COMPATIBLE_SOLUTES),
        /** 没有水，也没人浇。 */
        NO_WATER(null);

        private final Gene gene;

        Condition(Gene gene) {
            this.gene = gene;
        }

        /** 扛住这个条件要的基因；扛不住的为 {@code null}。 */
        public Gene gene() {
            return this.gene;
        }
    }

    /** 一行条件：对不对得上。 */
    public record Requirement(Condition condition, boolean met) {
    }

    private Pioneer() {
    }

    /** 这个环境给先锋生物出的条件，每条写着这组基因对不对得上。产氧光合那条总在第一行。 */
    public static List<Requirement> requirements(Set<Gene> genes, EffectiveEnvironment environment, double irrigation) {
        List<Requirement> list = new ArrayList<>();
        list.add(row(Condition.PHOTOSYNTHESIS, genes));
        double temperature = environment.temperature();
        if (temperature < FROZEN_LIMIT) {
            list.add(new Requirement(Condition.TOO_COLD, false));
        } else if (temperature < COLD_LIMIT) {
            list.add(row(Condition.COLD, genes));
        }
        if (temperature > BOILED_LIMIT) {
            list.add(new Requirement(Condition.TOO_HOT, false));
        } else if (temperature > HEAT_LIMIT) {
            list.add(row(Condition.HEAT, genes));
        }
        if (environment.radiationScale() > 0) {
            list.add(row(Condition.RADIATION, genes));
        }
        if (environment.hasHazard(Hazard.TOXIC) || Terraform.acidic(environment.mix(), environment.pressure())) {
            list.add(row(Condition.TOXIC, genes));
        }
        if (environment.pressure() < UV_PRESSURE) {
            list.add(row(Condition.UV, genes));
        }
        if (!environment.naturalWater()) {
            list.add(irrigation > 0 ? row(Condition.DRY, genes) : new Requirement(Condition.NO_WATER, false));
        }
        return list;
    }

    private static Requirement row(Condition condition, Set<Gene> genes) {
        return new Requirement(condition, genes.contains(condition.gene()));
    }

    public static boolean viable(List<Requirement> requirements) {
        for (Requirement requirement : requirements) {
            if (!requirement.met()) {
                return false;
            }
        }
        return true;
    }

    /** 这组基因在这个环境里能不能活。 */
    public static boolean viable(Set<Gene> genes, EffectiveEnvironment environment, double irrigation) {
        return viable(requirements(genes, environment, irrigation));
    }
}
