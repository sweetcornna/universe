package io.github.sweetcornna.universe.core.survival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 不是生物的实体按星球重力下落（ADR-0030 §六）。 */
class ProjectileGravityTest {
    /** 原版的掉落物每刻的重力。 */
    private static final double ITEM_GRAVITY = 0.04;

    @Test
    void scalesWithPlanetGravity() {
        assertEquals(ITEM_GRAVITY, ProjectileGravity.scale(ITEM_GRAVITY, 1), 1e-12, "主世界重力不变");
        assertEquals(ITEM_GRAVITY * 0.4, ProjectileGravity.scale(ITEM_GRAVITY, 0.4), 1e-12, "低重力星球上落得慢");
        assertEquals(ITEM_GRAVITY * 2.2, ProjectileGravity.scale(ITEM_GRAVITY, 2.2), 1e-12, "高重力星球上落得快");
    }

    /** 不受重力的实体（太空里浮着的）不动：0 乘出来还是 0，别的系数也不给它加重力。 */
    @Test
    void keepsWeightless() {
        assertEquals(0, ProjectileGravity.scale(0, 1), 0);
        assertEquals(0, ProjectileGravity.scale(0, 0.3), 0);
        assertEquals(0, ProjectileGravity.scale(0, 3), 0);
    }

    /** 箭的射程和 1/√g 成正比：半重力的星球上远 √2 倍，两倍重力近 √2 倍。 */
    @Test
    void arrowsFlyFartherInLowGravity() {
        double home = ProjectileGravity.flatRange(3, 2, 1);
        assertEquals(home * Math.sqrt(2), ProjectileGravity.flatRange(3, 2, 0.5), 1e-9, "半重力远 √2 倍");
        assertEquals(home / Math.sqrt(2), ProjectileGravity.flatRange(3, 2, 2), 1e-9, "两倍重力近 √2 倍");
        assertTrue(ProjectileGravity.flatRange(3, 2, 0.2) > home, "低重力飞得远");
    }

    /** 重力为 0 的地方平射出去不落地。 */
    @Test
    void neverFallsWithoutGravity() {
        assertEquals(Double.POSITIVE_INFINITY, ProjectileGravity.flatRange(3, 2, 0));
    }
}
