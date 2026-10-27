package io.github.sweetcornna.universe.core.chem.reaction;

/**
 * 反应式里的一项：物质和它的化学计量数（摩尔）。
 */
public record Term(Species species, double moles) {
    public Term {
        if (!(moles > 0)) {
            throw new IllegalArgumentException("moles must be positive: " + moles);
        }
    }
}
