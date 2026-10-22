package io.github.sweetcornna.universe.core.ecology;

import java.util.Optional;

/**
 * 卵序列仪的一次改造（ADR-0026 §九）：一份材料改卵上的一项性状。生物胶质让体型大一级，骨粉小一级（一级 0.1，
 * 改到 {@link CreatureTraits#MIN_SIZE}–{@link CreatureTraits#MAX_SIZE} 为止）；染料把颜色换成那种染料的；
 * 草食饲料让性情变温顺，生肉变得有领地意识。改不动的（体型到头了、颜色或性情已经是那样）不收材料。
 */
public sealed interface GeneEdit {
    /** 体型一级。 */
    double SIZE_STEP = 0.1;
    /** 改一次要几刻：5 秒。 */
    int TICKS = 100;

    /** 改完的性状；改不动时为空。 */
    Optional<CreatureTraits> apply(CreatureTraits traits);

    /** 体型改 {@code steps} 级，正的变大。 */
    record Resize(int steps) implements GeneEdit {
        @Override
        public Optional<CreatureTraits> apply(CreatureTraits traits) {
            // 按百分之一取整，免得一级一级加出 1.5000000000000004 这样的数
            double size = Math.clamp(Math.round((traits.size() + this.steps * SIZE_STEP) * 100) / 100.0,
                    CreatureTraits.MIN_SIZE, CreatureTraits.MAX_SIZE);
            return size == traits.size() ? Optional.empty() : Optional.of(traits.withSize(size));
        }
    }

    /** 换成这种颜色（RGB）。 */
    record Recolor(int tint) implements GeneEdit {
        public Recolor {
            tint &= 0xFFFFFF;
        }

        @Override
        public Optional<CreatureTraits> apply(CreatureTraits traits) {
            return traits.tint() == this.tint ? Optional.empty() : Optional.of(traits.withTint(this.tint));
        }
    }

    /** 换成这种性情。 */
    record Temper(Temperament temperament) implements GeneEdit {
        @Override
        public Optional<CreatureTraits> apply(CreatureTraits traits) {
            return traits.temperament() == this.temperament ? Optional.empty() : Optional.of(traits.withTemperament(this.temperament));
        }
    }

    GeneEdit GROW = new Resize(1);
    GeneEdit SHRINK = new Resize(-1);
    GeneEdit CALM = new Temper(Temperament.DOCILE);
    GeneEdit ROUSE = new Temper(Temperament.TERRITORIAL);
}
