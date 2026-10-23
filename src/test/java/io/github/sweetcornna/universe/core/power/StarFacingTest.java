package io.github.sweetcornna.universe.core.power;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StarFacingTest {
    /** 按渲染的顺序转板子的法线：先绕 z 转 tilt，再绕 y 转 yaw（右手系，逆时针为正）。 */
    private static double[] normal(double[] angles) {
        double yaw = Math.toRadians(angles[0]);
        double tilt = Math.toRadians(angles[1]);
        double x = -Math.sin(tilt);
        double y = Math.cos(tilt);
        return new double[] {x * Math.cos(yaw), y, -x * Math.sin(yaw)};
    }

    /** 板子的法线和指向恒星的方向夹角的余弦。 */
    private static double alignment(double x, double y, double z) {
        double[] n = normal(StarFacing.facing(x, y, z));
        double length = Math.sqrt(x * x + y * y + z * z);
        return -(n[0] * x + n[1] * y + n[2] * z) / length;
    }

    @Test
    void panelNormalPointsAtTheStar() {
        // 恒星在头顶以上的方向（板子在黄道面以下）：正对
        assertEquals(1, alignment(40_000, -64, 0), 1e-9);
        assertEquals(1, alignment(-40_000, -64, 0), 1e-9);
        assertEquals(1, alignment(0, -300, 40_000), 1e-9);
        assertEquals(1, alignment(25_000, -300, -31_000), 1e-9);
        assertEquals(1, alignment(10_000, -10_000, 0), 1e-9);
        // 恒星正上方：平放
        assertEquals(1, alignment(0, -20_000, 0), 1e-9);
        // 黄道面上方几百格（太空里的基地一般在这里）：竖着，差不到一度
        assertEquals(1, alignment(40_000, 300, 0), 1e-4);
        assertEquals(1, alignment(-12_000, 200, 35_000), 1e-3);
    }

    @Test
    void eastOfTheStarLeansWestLikeOnPlanets() {
        // 恒星在西边（板子在 +x）：和星球上下午的太阳一样朝 −x 倒，不用转方位
        double[] angles = StarFacing.facing(40_000, 0, 0);
        assertEquals(0, angles[0], 1e-9);
        assertEquals(90, angles[1], 1e-9);
    }

    @Test
    void starBelowStopsAtUpright() {
        // 恒星在下面：竖到 90° 为止
        assertEquals(90, StarFacing.facing(5_000, 5_000, 0)[1], 1e-9);
        assertEquals(90, StarFacing.facing(0, 100, 0)[1], 1e-9);
        // 正上方平放，就在恒星上时也平放
        assertEquals(0, StarFacing.facing(0, -100, 0)[1], 1e-9);
        assertEquals(0, StarFacing.facing(0, 0, 0)[1], 1e-9);
    }
}
