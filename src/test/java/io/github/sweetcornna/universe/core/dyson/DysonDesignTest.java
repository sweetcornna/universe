package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.dyson.DysonDesign.Problem;
import io.github.sweetcornna.universe.core.dyson.DysonGrid.Template;

class DysonDesignTest {
    private static double[] at(double lat, double lon) {
        return SphereGeometry.fromLatLon(lat, lon);
    }

    /** 赤道上一个 20° 见方的四边形，顶点 0..3 逆时针。 */
    private static DysonDesign square() {
        DysonDesign design = new DysonDesign(Template.GEODESIC, 1);
        assertNull(design.addNode(at(-10, 0)));
        assertNull(design.addNode(at(-10, 20)));
        assertNull(design.addNode(at(10, 20)));
        assertNull(design.addNode(at(10, 0)));
        for (int k = 0; k < 4; k++) {
            assertNull(design.addFrame(k, (k + 1) % 4));
        }
        return design;
    }

    @Test
    void everyTemplateIsAValidDesign() {
        for (Template template : Template.values()) {
            for (int d = 1; d <= template.maxDensity(); d++) {
                DysonDesign design = DysonDesign.of(new DysonLayer(1, 0.1, 0, 0, template, d));
                assertNull(design.validate(), template + " " + d);
            }
        }
    }

    @Test
    void customGridOfATemplateKeepsOrderCostsAndArea() {
        DysonGrid grid = DysonGrid.of(Template.HEXAGONAL, 2);
        DysonGrid custom = DysonDesign.of(new DysonLayer(1, 0.1, 0, 0, Template.HEXAGONAL, 2)).toGrid();
        assertTrue(custom.custom());
        assertEquals(grid.nodeCount(), custom.nodeCount());
        for (int f = 0; f < grid.frameCount(); f++) {
            assertEquals(DysonLayer.frameCost(grid.frameArc(f), 0.2), DysonLayer.frameCost(custom.frameArc(f), 0.2));
        }
        for (int s = 0; s < grid.shellCount(); s++) {
            assertEquals(grid.shellArea(s), custom.shellArea(s), 1e-9);
        }
    }

    @Test
    void fillingFacesRebuildsTheTemplateShells() {
        for (Template template : Template.values()) {
            DysonDesign design = DysonDesign.of(new DysonLayer(1, 0.1, 0, 0, template, 2));
            int shells = design.shellCount();
            int[] all = new int[shells];
            for (int i = 0; i < shells; i++) {
                all[i] = i;
            }
            design.removeShells(all);
            assertEquals(0, design.shellCount());
            assertEquals(shells, design.fillFaces(), template.name());
            assertNull(design.validate());
        }
    }

    @Test
    void nodesKeepTheirDistance() {
        DysonDesign design = square();
        assertEquals(Problem.NODE_TOO_CLOSE, design.addNode(at(-10.5, 0.5)));
        // 擦着框架
        assertEquals(Problem.NODE_ON_FRAME, design.addNode(at(-10, 10)));
        assertNull(design.addShell(new int[] {0, 1, 2, 3}));
        assertEquals(Problem.NODE_IN_SHELL, design.addNode(at(0, 10)));
        assertNull(design.addNode(at(0, 30)));
    }

    @Test
    void framesDoNotCrossOrRunTooLong() {
        DysonDesign design = square();
        assertEquals(Problem.FRAME_SAME_NODE, design.addFrame(1, 1));
        assertEquals(Problem.FRAME_EXISTS, design.addFrame(1, 0));
        assertNull(design.addFrame(0, 2));
        assertEquals(Problem.FRAME_CROSSES, design.addFrame(1, 3));
        assertNull(design.addNode(at(0, 100)));
        assertEquals(Problem.FRAME_TOO_LONG, design.addFrame(2, 4));
    }

    @Test
    void shellsNeedAClosedEmptyLoop() {
        DysonDesign design = square();
        assertEquals(Problem.SHELL_OPEN, design.addShell(new int[] {0, 1, 3}));
        assertEquals(Problem.SHELL_SIDES, design.addShell(new int[] {0, 1}));
        assertNull(design.addNode(at(0, 8)));
        assertEquals(Problem.SHELL_HAS_NODE, design.addShell(new int[] {0, 1, 2, 3}));
        design.removeNodes(4);
        assertNull(design.addFrame(0, 2));
        assertEquals(Problem.SHELL_HAS_FRAME, design.addShell(new int[] {0, 1, 2, 3}));
        // 对角线把方格分成两片三角形
        assertNull(design.addShell(new int[] {2, 1, 0}));
        assertEquals(Problem.SHELL_EXISTS, design.addShell(new int[] {0, 1, 2}));
        assertNull(design.addShell(new int[] {0, 2, 3}));
        assertEquals(2, design.shellCount());
        assertNull(design.validate());
        // 顶点转成了逆时针
        DysonGrid grid = design.toGrid();
        for (int s = 0; s < grid.shellCount(); s++) {
            double[][] points = new double[3][];
            for (int k = 0; k < 3; k++) {
                points[k] = grid.node(grid.shell(s)[k]);
            }
            assertTrue(SphereGeometry.signedArea(points) > 0);
        }
    }

    @Test
    void concaveShellsAreFineAndTriangulated() {
        // 一个 L 形：六个顶点
        DysonDesign design = new DysonDesign(Template.GEODESIC, 1);
        double[][] corners = {at(0, 0), at(0, 20), at(10, 20), at(10, 10), at(20, 10), at(20, 0)};
        for (double[] corner : corners) {
            assertNull(design.addNode(corner));
        }
        for (int k = 0; k < 6; k++) {
            assertNull(design.addFrame(k, (k + 1) % 6));
        }
        assertNull(design.addShellAt(at(15, 5)));
        DysonGrid grid = design.toGrid();
        assertFalse(grid.shellConvex(0));
        assertEquals(12, grid.shellTriangles(0).length);
        double expected = 0;
        int[] tris = grid.shellTriangles(0);
        for (int t = 0; t < tris.length; t += 3) {
            expected += SphereGeometry.signedArea(new double[][] {grid.node(tris[t]), grid.node(tris[t + 1]), grid.node(tris[t + 2])});
        }
        assertEquals(expected, grid.shellArea(0), 1e-9);
        // 凹进去的那一块不在球壳里
        assertEquals(-1, design.shellAt(at(15, 15)));
        assertEquals(0, design.shellAt(at(5, 5)));
    }

    @Test
    void faceAtPicksTheSmallestLoop() {
        DysonDesign design = square();
        assertNull(design.addFrame(0, 2));
        int[] face = design.faceAt(at(-5, 15));
        assertNotNull(face);
        assertEquals(3, face.length);
        assertNull(design.faceAt(at(0, 60)));
        assertEquals(Problem.NO_FACE, design.addShellAt(at(40, 60)));
    }

    @Test
    void removingANodeTakesItsFramesAndShells() {
        DysonDesign design = square();
        assertNull(design.addShell(new int[] {0, 1, 2, 3}));
        assertNull(design.addNode(at(0, 40)));
        assertNull(design.addFrame(1, 4));
        design.removeNodes(2);
        assertEquals(4, design.nodeCount());
        assertEquals(0, design.shellCount());
        assertEquals(3, design.frameCount());
        // 后面的下标往前挪了
        assertTrue(design.frameBetween(1, 3) >= 0);
        assertNull(design.validate());
    }

    @Test
    void validateCatchesWhatTheEditsWouldRefuse() {
        DysonDesign design = new DysonDesign(Template.GEODESIC, 1);
        design.rawNode(at(-10, 0), 0);
        design.rawNode(at(10, 20), 0);
        design.rawNode(at(-10, 20), 0);
        design.rawNode(at(10, 0), 0);
        design.rawFrame(0, 1, 0);
        design.rawFrame(2, 3, 0);
        assertEquals(Problem.FRAME_CROSSES, design.validate());
        DysonDesign broken = new DysonDesign(Template.GEODESIC, 1);
        broken.rawNode(at(0, 0), 0);
        broken.rawFrame(0, 3, 0);
        assertEquals(Problem.BROKEN, broken.validate());
    }

    @Test
    void movingANodeChecksTheWholeDesign() {
        DysonDesign design = square();
        assertNull(design.moveNode(0, at(-12, -2)));
        // 框架 3-0 会穿过框架 1-2
        assertEquals(Problem.FRAME_CROSSES, design.moveNode(0, at(0, 30)));
        assertTrue(SphereGeometry.angle(design.position(0), at(-12, -2)) < 1e-9);
    }
}
