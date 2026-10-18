package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DysonSkyTest {
    private static final double EPS = 1e-9;

    private static double angle(double[] a, double[] b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, DysonSky.dot(a, b)))));
    }

    @Test
    void eclipticMappingIsARotation() {
        // y（法线）变成 z；x × y = z 在两边都成立
        assertArrayEquals(new double[] {0, 0, 1}, DysonSky.ecliptic(new double[] {0, 1, 0}), EPS);
        assertArrayEquals(new double[] {1, 0, 0}, DysonSky.ecliptic(new double[] {1, 0, 0}), EPS);
        assertArrayEquals(new double[] {0, -1, 0}, DysonSky.ecliptic(new double[] {0, 0, 1}), EPS);
    }

    @Test
    void theStarItselfIsWhereTheSunIs() {
        double[] here = {1, 0, 0};
        for (double sun : new double[] {0, 0.7, -2.1}) {
            assertArrayEquals(DysonSky.starDirection(sun), DysonSky.skyDirection(here, new double[] {0, 0, 0}, sun), 1e-9);
        }
        // 正午恒星在头顶
        assertArrayEquals(new double[] {0, 1, 0}, DysonSky.starDirection(0), EPS);
    }

    @Test
    void offsetsMatchTheRealAngle() {
        double[] here = {1, 0, 0};
        // 黄道面里偏开的点：和恒星的夹角就是从观察者看过去的真实夹角
        double[] point = {0, 0.2, 0};
        double expected = Math.toDegrees(Math.atan2(0.2, 1));
        double[] direction = DysonSky.skyDirection(here, point, 0.3);
        assertEquals(expected, angle(direction, DysonSky.starDirection(0.3)), 1e-9);
        // 在黄道面上：世界的 z 分量是 0
        assertEquals(0, direction[2], 1e-12);
        // 黄道面外的点偏到 z 方向
        double[] above = DysonSky.skyDirection(here, new double[] {0, 0, 0.2}, 0.3);
        assertTrue(Math.abs(above[2]) > 0.1);
        assertEquals(expected, angle(above, DysonSky.starDirection(0.3)), 1e-9);
    }

    @Test
    void remapPushesEverythingOutsideTheStarDisc() {
        double[] star = DysonSky.starDirection(0.4);
        double inner = Math.toRadians(6.5);
        double[] near = DysonSky.skyDirection(new double[] {1, 0, 0}, new double[] {0, 0.01, 0.005}, 0.4);
        double[] mapped = DysonSky.remap(star, near, inner);
        double theta = Math.toRadians(angle(near, star));
        assertEquals(Math.toDegrees(inner + theta * (Math.PI / 2 - inner) / (Math.PI / 2)), angle(mapped, star), 1e-6);
        // 方位不变：映射前后和恒星在同一个大圆上
        double[] normalBefore = cross(star, near);
        double[] normalAfter = cross(star, mapped);
        assertEquals(0, angle(normalize(normalBefore), normalize(normalAfter)), 1e-6);
        // 90° 不动，正对恒星的点落在 inner 上
        double[] side = {1, 0, 0};
        double[] up = {0, 1, 0};
        assertEquals(90, angle(DysonSky.remap(up, side, inner), up), 1e-9);
        assertEquals(6.5, angle(DysonSky.remap(up, up, inner), up), 1e-9);
        assertEquals(1, Math.sqrt(DysonSky.dot(mapped, mapped)), 1e-12);
    }

    @Test
    void facingAndSlerp() {
        assertTrue(DysonSky.facing(new double[] {0.1, 0, 0}, new double[] {1, 0, 0}));
        assertFalse(DysonSky.facing(new double[] {-0.1, 0, 0}, new double[] {1, 0, 0}));
        double[] half = DysonSky.slerp(new double[] {1, 0, 0}, new double[] {0, 1, 0}, 0.5);
        assertArrayEquals(new double[] {Math.sqrt(0.5), Math.sqrt(0.5), 0}, half, 1e-12);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] normalize(double[] v) {
        double length = Math.sqrt(DysonSky.dot(v, v));
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }
}
