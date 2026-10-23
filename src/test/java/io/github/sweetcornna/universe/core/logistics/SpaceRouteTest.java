package io.github.sweetcornna.universe.core.logistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.SpaceLayout;

class SpaceRouteTest {
    private static final Galaxy GALAXY = GalaxyGenerator.fromGalaxySeed(12345L, 40);

    @Test
    void planetEndsLeaveTimeForTheLift() {
        // 两颗星球之间：起飞后 200 刻钻出大气球，到达前 200 刻钻进去
        double[] w = SpaceRoute.window(SpaceRoute.SPHERE, SpaceRoute.SPHERE, 1000, 3000);
        assertEquals(1200, w[0], 1e-9);
        assertEquals(2800, w[1], 1e-9);
        // 太空里的站直接从停机坪起飞
        w = SpaceRoute.window(SpaceRoute.PAD, SpaceRoute.SPHERE, 1000, 3000);
        assertEquals(1000, w[0], 1e-9);
        assertEquals(2800, w[1], 1e-9);
        // 航程很短时起降各占四成，中间还剩一段
        w = SpaceRoute.window(SpaceRoute.SPHERE, SpaceRoute.SPHERE, 0, 400);
        assertEquals(160, w[0], 1e-9);
        assertEquals(240, w[1], 1e-9);
    }

    @Test
    void warpLegsShowOnlyTheNearEnd() {
        // 去别的恒星系：钻出大气球后飞 600 刻到边上跃迁
        double[] w = SpaceRoute.window(SpaceRoute.SPHERE, SpaceRoute.EDGE, 0, 10_000);
        assertEquals(200, w[0], 1e-9);
        assertEquals(800, w[1], 1e-9);
        // 从别处跃迁来：最后 600 刻从边上飞进来，再降落
        w = SpaceRoute.window(SpaceRoute.EDGE, SpaceRoute.SPHERE, 0, 10_000);
        assertEquals(9200, w[0], 1e-9);
        assertEquals(9800, w[1], 1e-9);
        // 航程短时最多占一半
        w = SpaceRoute.window(SpaceRoute.PAD, SpaceRoute.EDGE, 0, 800);
        assertEquals(0, w[0], 1e-9);
        assertEquals(400, w[1], 1e-9);
    }

    @Test
    void edgeIsOutsideEveryBodyAndPointsAtTheOtherStar() {
        StarSystem here = GALAXY.home();
        List<SpaceLayout.Body> bodies = SpaceLayout.of(here);
        double edge = SpaceRoute.edgeRadius(bodies);
        for (SpaceLayout.Body body : bodies) {
            double d = Math.sqrt(body.x() * body.x() + body.y() * body.y() + body.z() * body.z());
            assertTrue(d + body.radius() < edge, body.id() + " 在边外面");
        }
        StarSystem other = GALAXY.systems().stream().filter(s -> s.index() != here.index()).findFirst().orElseThrow();
        double[] p = SpaceRoute.edgePoint(here, other.x(), other.z(), edge);
        assertEquals(edge, Math.hypot(p[0], p[2]), 1e-6);
        assertEquals(0, p[1], 1e-9);
        // 方向和恒星连线在黄道面上的投影一致
        double dx = other.x() - here.x();
        double dz = other.z() - here.z();
        assertEquals(1, (p[0] * dx + p[2] * dz) / (edge * Math.hypot(dx, dz)), 1e-9);
        // 两颗恒星重合时也有个方向
        double[] same = SpaceRoute.edgePoint(here, here.x(), here.z(), edge);
        assertEquals(edge, same[0], 1e-9);
    }

    @Test
    void spherePointSitsJustOutsideTheAtmosphereFacingTheTarget() {
        SpaceLayout.Body body = SpaceLayout.of(GALAXY.home()).getFirst();
        double[] target = {body.x() + 50_000, body.y(), body.z()};
        double[] p = SpaceRoute.spherePoint(body, target);
        assertEquals(body.radius() + SpaceRoute.SPHERE_ALTITUDE, body.distanceTo(p[0], p[1], p[2]), 1e-6);
        assertTrue(p[0] > body.x(), "没朝着目标");
    }

    @Test
    void routeAcrossTheStarBendsAroundIt() {
        double starRadius = 3000;
        double[] from = {-40_000, 0, 500};
        double[] to = {40_000, 0, 500};
        double[] control = SpaceRoute.control(from, to, starRadius);
        double safe = starRadius * SpaceRoute.STAR_CLEARANCE;
        for (int i = 0; i <= 100; i++) {
            double[] p = SpaceRoute.point(from, control, to, i / 100.0);
            double d = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            assertTrue(d > starRadius * 2, "第 " + i + " 点离恒星只有 " + d);
        }
        double[] middle = SpaceRoute.point(from, control, to, 0.5);
        assertTrue(Math.sqrt(middle[0] * middle[0] + middle[1] * middle[1] + middle[2] * middle[2]) >= safe - 1e-6, "中点太近");
        // 不穿过恒星的航线就是直线：控制点在中点
        double[] a = {40_000, 0, 0};
        double[] b = {0, 0, 40_000};
        double[] straight = SpaceRoute.control(a, b, starRadius);
        assertEquals(20_000, straight[0], 1e-9);
        assertEquals(20_000, straight[2], 1e-9);
        // 两头正好隔着恒星对穿时往上绕
        double[] up = SpaceRoute.control(new double[] {-40_000, 0, 0}, new double[] {40_000, 0, 0}, starRadius);
        assertTrue(up[1] > 0, "没往上绕");
    }

    @Test
    void easingStartsAndEndsWhereItShould() {
        int[][] pairs = {{SpaceRoute.SPHERE, SpaceRoute.SPHERE}, {SpaceRoute.PAD, SpaceRoute.EDGE}, {SpaceRoute.EDGE, SpaceRoute.SPHERE}};
        for (int[] pair : pairs) {
            assertEquals(0, SpaceRoute.ease(pair[0], pair[1], 0), 1e-12);
            assertEquals(1, SpaceRoute.ease(pair[0], pair[1], 1), 1e-12);
            double previous = 0;
            for (int i = 1; i <= 50; i++) {
                double e = SpaceRoute.ease(pair[0], pair[1], i / 50.0);
                assertTrue(e >= previous, "倒退了");
                previous = e;
            }
        }
        // 飞向跃迁点时越飞越快：前一半只走一成多
        assertTrue(SpaceRoute.ease(SpaceRoute.SPHERE, SpaceRoute.EDGE, 0.5) < 0.2);
        // 跃迁进来时一开始最快
        assertTrue(SpaceRoute.ease(SpaceRoute.EDGE, SpaceRoute.SPHERE, 0.5) > 0.8);
    }

    @Test
    void shipStretchesOnlyRightAtTheWarp() {
        assertEquals(1, SpaceRoute.stretch(SpaceRoute.SPHERE, SpaceRoute.SPHERE, 0, 1000, 999), 1e-12);
        assertEquals(1, SpaceRoute.stretch(SpaceRoute.SPHERE, SpaceRoute.EDGE, 0, 1000, 900), 1e-12);
        assertEquals(6, SpaceRoute.stretch(SpaceRoute.SPHERE, SpaceRoute.EDGE, 0, 1000, 1000), 1e-12);
        assertTrue(SpaceRoute.stretch(SpaceRoute.SPHERE, SpaceRoute.EDGE, 0, 1000, 990) > 1);
        assertEquals(6, SpaceRoute.stretch(SpaceRoute.EDGE, SpaceRoute.SPHERE, 0, 1000, 0), 1e-12);
        assertEquals(1, SpaceRoute.stretch(SpaceRoute.EDGE, SpaceRoute.SPHERE, 0, 1000, 31), 1e-12);
    }
}
