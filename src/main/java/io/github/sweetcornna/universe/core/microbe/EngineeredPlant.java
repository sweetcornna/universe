package io.github.sweetcornna.universe.core.microbe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.galaxy.Planet;

/**
 * 一株改造种苗（ADR-0036 §五）：一种资源植物加编辑进去的基因。
 *
 * <ul>
 *   <li>耐受的基因让它种得到原来种不了的星球上（{@link PlantTolerance}），组织里天生的也算。</li>
 *   <li>碳浓缩：水培槽里长得快一半。</li>
 *   <li>固氮：不用施肥，水培槽里总当施过肥；收得多四分之一。</li>
 *   <li>这两样只算编辑进去的：天生的本事（氧囊草的碳浓缩、星莓丛的固氮）已经算在原来的生长时间里。</li>
 *   <li>每个外来基因长得慢一成（{@link Gene#BURDEN}）。</li>
 *   <li>水培槽收获时有 {@link #SEEDLING_CHANCE} 的机会多给一株同样的种苗。</li>
 * </ul>
 *
 * @param plant 原来的植物，不能是矿物（盐晶簇）
 * @param added 编辑进去的基因，最多 {@link Gene#MAX_ADDED} 个，和组织里天生的不重复
 */
public record EngineeredPlant(ResourcePlant plant, List<Gene> added) {
    /** 施肥后下一茬长得快多少。 */
    public static final double FERTILIZER_GROWTH = 1.5;
    /** 碳浓缩让水培槽里长得快多少。 */
    public static final double CARBON_CONCENTRATING_GROWTH = 1.5;
    /** 固氮让一茬多收多少。 */
    public static final double NITROGENASE_YIELD = 1.25;
    /** 水培槽收获时多给一株种苗的机会。 */
    public static final double SEEDLING_CHANCE = 0.25;

    public EngineeredPlant {
        if (plant.habitat() == ResourcePlant.Habitat.ROCK) {
            throw new IllegalArgumentException("minerals cannot be edited: " + plant);
        }
        added = List.copyOf(added);
        if (added.size() > Gene.MAX_ADDED || Set.copyOf(added).size() != added.size()) {
            throw new IllegalArgumentException("bad added genes: " + added);
        }
        Set<Gene> nativeGenes = TissueGenes.plant(plant);
        for (Gene gene : added) {
            if (nativeGenes.contains(gene)) {
                throw new IllegalArgumentException("gene already native: " + gene);
            }
        }
    }

    /** 能不能编辑这种植物（矿物不行）。 */
    public static boolean editable(ResourcePlant plant) {
        return plant.habitat() != ResourcePlant.Habitat.ROCK;
    }

    /** 组织里天生的加编辑进去的。 */
    public Set<Gene> genes() {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        set.addAll(TissueGenes.plant(this.plant));
        set.addAll(this.added);
        return Collections.unmodifiableSet(set);
    }

    public GeneEditing.Result canAdd(Gene gene) {
        return GeneEditing.check(TissueGenes.plant(this.plant), this.added, gene);
    }

    public EngineeredPlant withGene(Gene gene) {
        if (this.canAdd(gene) != GeneEditing.Result.OK) {
            throw new IllegalStateException("cannot add " + gene + ": " + this.canAdd(gene));
        }
        List<Gene> list = new ArrayList<>(this.added);
        list.add(gene);
        return new EngineeredPlant(this.plant, list);
    }

    public boolean engineered() {
        return !this.added.isEmpty();
    }

    /** 不用施肥：编辑进了固氮酶。 */
    public boolean selfFertilizing() {
        return this.added.contains(Gene.NITROGENASE);
    }

    /**
     * 水培槽里的生长倍数。
     *
     * @param fertilized 这一茬施了肥
     */
    public double trayGrowth(boolean fertilized) {
        double growth = GeneEditing.burden(this.added.size());
        if (this.added.contains(Gene.CARBON_CONCENTRATING)) {
            growth *= CARBON_CONCENTRATING_GROWTH;
        }
        if (fertilized || this.selfFertilizing()) {
            growth *= FERTILIZER_GROWTH;
        }
        return growth;
    }

    /** 一茬收获的倍数。 */
    public double trayYield() {
        return this.selfFertilizing() ? NITROGENASE_YIELD : 1;
    }

    public boolean canPlant(Planet planet) {
        return PlantTolerance.canPlant(this.plant, this.genes(), this.engineered(), planet);
    }
}
