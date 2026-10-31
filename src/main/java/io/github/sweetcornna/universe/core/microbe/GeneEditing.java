package io.github.sweetcornna.universe.core.microbe;

import java.util.Collection;
import java.util.List;

/**
 * 基因编辑台（照 CRISPR-Cas9）的规矩（ADR-0036 §五）：一样东西最多加 {@link Gene#MAX_ADDED} 个外来基因，
 * 已经有的不能再加；每加一个长得慢一成（{@link Gene#BURDEN}，外源基因的代谢负担）。
 */
public final class GeneEditing {
    private GeneEditing() {
    }

    /** 能不能加。 */
    public enum Result {
        OK,
        /** 天生就有，或者已经加过。 */
        ALREADY_PRESENT,
        /** 外来基因满了。 */
        FULL
    }

    /** 在天生有 {@code nativeGenes}、已经加了 {@code added} 的东西上再加 {@code gene}。 */
    public static Result check(Collection<Gene> nativeGenes, List<Gene> added, Gene gene) {
        if (nativeGenes.contains(gene) || added.contains(gene)) {
            return Result.ALREADY_PRESENT;
        }
        return added.size() >= Gene.MAX_ADDED ? Result.FULL : Result.OK;
    }

    /** 加了 {@code added} 个外来基因以后的生长倍数。 */
    public static double burden(int added) {
        return Math.pow(Gene.BURDEN, added);
    }
}
