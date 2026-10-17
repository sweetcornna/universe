package io.github.sweetcornna.universe.core.galaxy;

/**
 * 生态（玩法设计 §8）。物种的具体形态由 {@code core.ecology} 按 {@link #seed()} 生成。
 *
 * @param flora         植被覆盖 0–1
 * @param trees         树木密度 0–1
 * @param fauna         动物丰富程度 0–1
 * @param plantSpecies  植物物种数
 * @param animalSpecies 动物物种数
 * @param seed          物种生成的种子
 */
public record Ecology(double flora, double trees, double fauna, int plantSpecies, int animalSpecies, long seed) {
    public static Ecology barren(long seed) {
        return new Ecology(0, 0, 0, 0, 0, seed);
    }

    public boolean lifeless() {
        return this.plantSpecies == 0 && this.animalSpecies == 0;
    }
}
