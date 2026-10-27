package io.github.sweetcornna.universe.core.microbe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * 全服共享的基因库（ADR-0036 §五）：测序仪测出来的基因，和测过的菌。基因编辑台只能从库里选基因。
 * 适配层存成两串名字，读到不认识的名字跳过。
 */
public final class GeneLibrary {
    private final EnumSet<Gene> genes = EnumSet.noneOf(Gene.class);
    private final TreeSet<String> strains = new TreeSet<>();

    /** 测一次的结果。 */
    public record SequenceResult(Set<Gene> newGenes, boolean newStrain) {
        public SequenceResult {
            newGenes = Collections.unmodifiableSet(newGenes.isEmpty() ? EnumSet.noneOf(Gene.class) : EnumSet.copyOf(newGenes));
        }

        /** 测出了库里没有的东西。 */
        public boolean anythingNew() {
            return this.newStrain || !this.newGenes.isEmpty();
        }
    }

    /** 从存档读回来。 */
    public static GeneLibrary load(Collection<String> geneNames, Collection<String> strainKeys) {
        GeneLibrary library = new GeneLibrary();
        for (String name : geneNames) {
            Gene gene = Gene.byName(name);
            if (gene != null) {
                library.genes.add(gene);
            }
        }
        library.strains.addAll(strainKeys);
        return library;
    }

    /**
     * 记下测出的东西。
     *
     * @param strainKey 测的是菌种时它的键（{@link MicrobeStrain#key()}）；测的是组织为 {@code null}
     * @param found     测出的基因
     */
    public SequenceResult sequence(@Nullable String strainKey, Collection<Gene> found) {
        EnumSet<Gene> fresh = EnumSet.noneOf(Gene.class);
        for (Gene gene : found) {
            if (this.genes.add(gene)) {
                fresh.add(gene);
            }
        }
        boolean newStrain = strainKey != null && this.strains.add(strainKey);
        return new SequenceResult(fresh, newStrain);
    }

    public boolean has(Gene gene) {
        return this.genes.contains(gene);
    }

    public boolean sequenced(String strainKey) {
        return this.strains.contains(strainKey);
    }

    public Set<Gene> genes() {
        return Collections.unmodifiableSet(this.genes);
    }

    public Set<String> strains() {
        return Collections.unmodifiableSet(this.strains);
    }

    /** 存档用：基因的名字，按枚举的顺序。 */
    public List<String> geneNames() {
        List<String> names = new ArrayList<>();
        for (Gene gene : this.genes) {
            names.add(gene.name());
        }
        return names;
    }
}
