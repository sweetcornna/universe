package io.github.sweetcornna.universe.core.microbe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 一管菌种里的菌（ADR-0036 §五）：原来的菌，加上编辑进去的基因和驯化攒下的产率。
 * 菌种物品、生物反应器、生态播种站都按它算。
 *
 * @param strain       原来的菌
 * @param added        编辑进去的基因，按加的先后，最多 {@link Gene#MAX_ADDED} 个，和天生的不重复
 * @param domestication 驯化攒下的产率加成，0 到 {@link Isolation#DOMESTICATION_MAX}
 */
public record Organism(MicrobeStrain strain, List<Gene> added, double domestication) {
    public Organism {
        added = List.copyOf(added);
        if (added.size() > Gene.MAX_ADDED) {
            throw new IllegalArgumentException("too many added genes: " + added);
        }
        if (Set.copyOf(added).size() != added.size()) {
            throw new IllegalArgumentException("duplicate added genes: " + added);
        }
        for (Gene gene : added) {
            if (strain.has(gene)) {
                throw new IllegalArgumentException("gene already native: " + gene);
            }
        }
        domestication = Math.clamp(domestication, 0, Isolation.DOMESTICATION_MAX);
    }

    /** 没改过、没驯化过的菌。 */
    public static Organism wild(MicrobeStrain strain) {
        return new Organism(strain, List.of(), 0);
    }

    /** 天生的加编辑进去的。 */
    public Set<Gene> genes() {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        set.addAll(this.strain.genes());
        set.addAll(this.added);
        return Collections.unmodifiableSet(set);
    }

    public boolean has(Gene gene) {
        return this.strain.has(gene) || this.added.contains(gene);
    }

    /** 带来本事的基因（决定生物反应器里能跑哪些工艺）。 */
    public Set<Gene> functions() {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        for (Gene gene : this.genes()) {
            if (gene.role() == Gene.Role.FUNCTION) {
                set.add(gene);
            }
        }
        return set;
    }

    /** 能不能再加这个基因。 */
    public GeneEditing.Result canAdd(Gene gene) {
        return GeneEditing.check(this.strain.genes(), this.added, gene);
    }

    /** 加一个基因；加不了时抛异常，先用 {@link #canAdd} 查。 */
    public Organism withGene(Gene gene) {
        if (this.canAdd(gene) != GeneEditing.Result.OK) {
            throw new IllegalStateException("cannot add " + gene + ": " + this.canAdd(gene));
        }
        List<Gene> list = new ArrayList<>(this.added);
        list.add(gene);
        return new Organism(this.strain, list, this.domestication);
    }

    public Organism withDomestication(double domestication) {
        return new Organism(this.strain, this.added, domestication);
    }

    /** 生长的倍数：每个外来基因乘 {@link Gene#BURDEN}。 */
    public double growthMultiplier() {
        return GeneEditing.burden(this.added.size());
    }

    /** 产率：原来的乘上驯化的加成。 */
    public double productivity() {
        return this.strain.productivity() * (1 + this.domestication);
    }

    public Tolerance tolerance() {
        return Tolerance.of(this.strain, this.genes(), Set.copyOf(this.added));
    }

    /** 这个温度下长得多快，0–1，已经算上外来基因放宽的范围。 */
    public double temperatureFactor(double temperatureK) {
        return this.tolerance().growthFactor(temperatureK);
    }

    /** 最适条件下翻一倍要几刻，算上代谢负担。 */
    public double doublingTicks() {
        return this.strain.doublingTicks() / this.growthMultiplier();
    }
}
