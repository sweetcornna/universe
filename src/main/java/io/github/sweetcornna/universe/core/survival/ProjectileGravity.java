package io.github.sweetcornna.universe.core.survival;

/**
 * 不是生物的实体（掉落物、箭、雪球、经验球……）在星球上的重力（ADR-0030 §六）。生物的重力是属性，由
 * {@link GravityModel} 算；这些实体每刻自己减一个固定的重力，这里只按星球重力等比缩放。
 *
 * <p>低重力的星球上箭飞得远、掉落物飘得久，高重力反过来。重力为 0（太空里浮着的实体、不受重力的实体）不动。
 */
public final class ProjectileGravity {
    private ProjectileGravity() {
    }

    /** 实体原本每刻的重力换算成这颗星球上的值。 */
    public static double scale(double gravity, double planetGravity) {
        if (gravity == 0) {
            return gravity;
        }
        return gravity * planetGravity;
    }

    /**
     * 平射出去的东西落到地面要飞多远（格），给测试和手册用：初速 {@code speed} 格/刻，从 {@code height} 格高平射，
     * 不算空气阻力。射程和 1/√g 成正比，所以半重力的星球上远 √2 倍。
     */
    public static double flatRange(double speed, double height, double planetGravity) {
        if (planetGravity <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        // h = ½·a·t²，a 是每刻的重力
        double ticks = Math.sqrt(2 * height / (0.05 * planetGravity));
        return speed * ticks;
    }
}
