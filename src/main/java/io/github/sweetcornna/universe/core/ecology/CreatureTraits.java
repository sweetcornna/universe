package io.github.sweetcornna.universe.core.ecology;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 一只动物自己的性状（ADR-0026 §九）：体型系数、颜色、性情。野生的出生时按种子定体型（0.85–1.15）和一点色差，
 * 性情随物种；卵序列仪能改（体型 0.5–1.5、染成一种颜色、改性情）。
 *
 * @param size        体型系数，乘在物种的体型上
 * @param tint        颜色：RGB，乘在贴图上，{@link #NO_TINT} 是不变
 * @param temperament 性情
 */
public record CreatureTraits(double size, int tint, Temperament temperament) {
    /** 野生的体型系数范围。 */
    public static final double NATURAL_MIN = 0.85;
    public static final double NATURAL_MAX = 1.15;
    /** 卵序列仪能改到的范围。 */
    public static final double MIN_SIZE = 0.5;
    public static final double MAX_SIZE = 1.5;
    public static final int NO_TINT = 0xFFFFFF;
    /** 野生的色差：每个通道最多暗这么多。 */
    private static final int NATURAL_SHADE = 24;

    public CreatureTraits {
        size = Math.clamp(size, MIN_SIZE, MAX_SIZE);
        tint &= 0xFFFFFF;
    }

    /** 物种的默认性状：体型 1，不变色。 */
    public static CreatureTraits of(CreatureSpecies species) {
        return new CreatureTraits(1, NO_TINT, species.temperament());
    }

    /** 野生的出生时：按种子定体型系数和一点色差，性情随物种。 */
    public static CreatureTraits natural(CreatureSpecies species, long seed) {
        Rng rng = Rng.of(seed);
        double size = rng.range(NATURAL_MIN, NATURAL_MAX);
        int r = 255 - rng.nextInt(NATURAL_SHADE + 1);
        int g = 255 - rng.nextInt(NATURAL_SHADE + 1);
        int b = 255 - rng.nextInt(NATURAL_SHADE + 1);
        return new CreatureTraits(size, r << 16 | g << 8 | b, species.temperament());
    }

    public CreatureTraits withSize(double size) {
        return new CreatureTraits(size, this.tint, this.temperament);
    }

    public CreatureTraits withTint(int tint) {
        return new CreatureTraits(this.size, tint, this.temperament);
    }

    public CreatureTraits withTemperament(Temperament temperament) {
        return new CreatureTraits(this.size, this.tint, temperament);
    }
}
