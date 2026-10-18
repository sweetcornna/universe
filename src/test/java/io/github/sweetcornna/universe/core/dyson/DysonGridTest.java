package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.dyson.DysonGrid.Template;

class DysonGridTest {
    @Test
    void geodesicCountsFollowTheIcosahedron() {
        for (int f = 1; f <= 4; f++) {
            DysonGrid grid = DysonGrid.of(Template.GEODESIC, f);
            assertEquals(10 * f * f + 2, grid.nodeCount(), "nodes f=" + f);
            assertEquals(30 * f * f, grid.frameCount(), "frames f=" + f);
            assertEquals(20 * f * f, grid.shellCount(), "shells f=" + f);
        }
    }

    @Test
    void hexagonalIsTheDual() {
        DysonGrid dodecahedron = DysonGrid.of(Template.HEXAGONAL, 1);
        assertEquals(20, dodecahedron.nodeCount());
        assertEquals(30, dodecahedron.frameCount());
        assertEquals(12, dodecahedron.shellCount());
        DysonGrid two = DysonGrid.of(Template.HEXAGONAL, 2);
        assertEquals(80, two.nodeCount());
        assertEquals(120, two.frameCount());
        assertEquals(42, two.shellCount());
        int pentagons = 0;
        for (int s = 0; s < two.shellCount(); s++) {
            int sides = two.shell(s).length;
            assertTrue(sides == 5 || sides == 6);
            pentagons += sides == 5 ? 1 : 0;
        }
        assertEquals(12, pentagons);
    }

    @Test
    void latitudeGridHasRingsMeridiansAndPoles() {
        DysonGrid grid = DysonGrid.of(Template.LATITUDE, 2);
        // 5 圈纬线 × 12 条经线 + 两极
        assertEquals(62, grid.nodeCount());
        // 每条经线：两个极三角形 + 4 个方格
        assertEquals(12 * 6, grid.shellCount());
        // 纬线 5 × 12 + 经线段 4 × 12 + 极点连线 2 × 12
        assertEquals(60 + 48 + 24, grid.frameCount());
    }

    @Test
    void everyTemplateTilesTheWholeSphere() {
        for (Template template : Template.values()) {
            for (int d = 1; d <= template.maxDensity(); d++) {
                DysonGrid grid = DysonGrid.of(template, d);
                double area = 0;
                for (int s = 0; s < grid.shellCount(); s++) {
                    assertTrue(grid.shellArea(s) > 0);
                    area += grid.shellArea(s);
                }
                assertEquals(4 * Math.PI, area, 1e-6, template + " " + d);
                // 欧拉公式：V − E + F = 2
                assertEquals(2, grid.nodeCount() - grid.frameCount() + grid.shellCount(), template + " " + d);
            }
        }
    }

    @Test
    void everyFrameBordersTwoShellsAndShellEdgesAreFrames() {
        for (Template template : Template.values()) {
            DysonGrid grid = DysonGrid.of(template, 2);
            Map<Integer, Integer> borders = new HashMap<>();
            for (int s = 0; s < grid.shellCount(); s++) {
                int[] shell = grid.shell(s);
                int[] frames = grid.shellFrames(s);
                for (int k = 0; k < shell.length; k++) {
                    int[] ends = grid.frame(frames[k]);
                    int a = shell[k];
                    int b = shell[(k + 1) % shell.length];
                    assertTrue(ends[0] == Math.min(a, b) && ends[1] == Math.max(a, b));
                    borders.merge(frames[k], 1, Integer::sum);
                }
            }
            assertEquals(grid.frameCount(), borders.size());
            borders.values().forEach(count -> assertEquals(2, count));
        }
    }

    @Test
    void shellsFaceOutwards() {
        for (Template template : Template.values()) {
            DysonGrid grid = DysonGrid.of(template, 2);
            for (int s = 0; s < grid.shellCount(); s++) {
                int[] shell = grid.shell(s);
                double[] a = grid.node(shell[0]);
                double[] b = grid.node(shell[1]);
                double[] c = grid.node(shell[2]);
                double[] normal = DysonGrid.cross(new double[] {b[0] - a[0], b[1] - a[1], b[2] - a[2]},
                        new double[] {c[0] - b[0], c[1] - b[1], c[2] - b[2]});
                assertTrue(DysonGrid.dot(normal, a) > 0, template + " shell " + s);
            }
        }
    }

    @Test
    void nodesAreOrderedFromTheEquator() {
        DysonGrid grid = DysonGrid.of(Template.GEODESIC, 3);
        for (int i = 1; i < grid.nodeCount(); i++) {
            assertTrue(Math.abs(grid.latitude(i)) >= Math.abs(grid.latitude(i - 1)) - 1e-6);
        }
        assertEquals(90, Math.abs(grid.latitude(grid.nodeCount() - 1)), 1e-6);
    }

    @Test
    void thirtyDegreesAllowsTheEquatorialBandOfTheIcosahedron() {
        DysonGrid grid = DysonGrid.of(Template.GEODESIC, 1);
        int nodes = 0;
        int frames = 0;
        int shells = 0;
        for (int i = 0; i < grid.nodeCount(); i++) {
            nodes += grid.nodeWithin(i, DysonSphere.LATITUDE_BASE) ? 1 : 0;
        }
        for (int i = 0; i < grid.frameCount(); i++) {
            frames += grid.frameWithin(i, DysonSphere.LATITUDE_BASE) ? 1 : 0;
        }
        for (int i = 0; i < grid.shellCount(); i++) {
            shells += grid.shellWithin(i, DysonSphere.LATITUDE_BASE) ? 1 : 0;
        }
        assertEquals(10, nodes);
        assertEquals(20, frames);
        assertEquals(10, shells);
    }

    @Test
    void gridsAreCachedAndDensityIsClamped() {
        assertSame(DysonGrid.of(Template.GEODESIC, 2), DysonGrid.of(Template.GEODESIC, 2));
        assertEquals(4, DysonGrid.of(Template.GEODESIC, 9).density());
        assertEquals(1, DysonGrid.of(Template.HEXAGONAL, 0).density());
    }
}
