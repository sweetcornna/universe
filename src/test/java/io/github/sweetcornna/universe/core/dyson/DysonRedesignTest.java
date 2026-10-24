package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.dyson.DysonGrid.Template;
import io.github.sweetcornna.universe.core.dyson.DysonLayer.Element;
import io.github.sweetcornna.universe.core.dyson.DysonSphere.EditResult;

class DysonRedesignTest {
    private static DysonSphere sphere() {
        DysonSphere sphere = new DysonSphere(1, 0.5);
        sphere.addLayer(0.2, 0, 0, Template.GEODESIC, 1);
        return sphere;
    }

    /** 把第一个能建的节点建满。 */
    private static int buildFirstNode(DysonSphere sphere) {
        return buildNode(sphere, sphere.layer(1).nextTarget(90).index());
    }

    private static int buildNode(DysonSphere sphere, int node) {
        DysonLayer layer = sphere.layer(1);
        DysonLayer.Target target = new DysonLayer.Target(true, node);
        for (int k = 0; k < DysonLayer.NODE_SP; k++) {
            layer.reserve(target, 1);
            layer.build(target);
        }
        assertTrue(layer.nodeBuilt(target.index()));
        return target.index();
    }

    @Test
    void unbuiltPartsCanBeDeletedFreely() {
        DysonSphere sphere = sphere();
        DysonDesign design = DysonDesign.of(sphere.layer(1));
        design.removeNodes(0, 5);
        assertNull(design.validate());
        assertEquals(EditResult.OK, sphere.redesign(1, design, false));
        DysonLayer layer = sphere.layer(1);
        assertTrue(layer.grid().custom());
        assertEquals(10, layer.grid().nodeCount());
        assertTrue(layer.empty());
    }

    @Test
    void builtPartsMustBeDemolishedFirst() {
        DysonSphere sphere = sphere();
        int built = buildFirstNode(sphere);
        long revision = sphere.revision();
        DysonDesign design = DysonDesign.of(sphere.layer(1));
        design.removeNodes(built);
        assertEquals(EditResult.BUILT, sphere.redesign(1, design, false));
        assertEquals(revision, sphere.revision());
        // 拆了再删：结构点不退
        assertEquals(EditResult.NOT_BUILT, sphere.demolish(1, Element.NODE, new int[] {(built + 1) % 12}));
        assertEquals(EditResult.OK, sphere.demolish(1, Element.NODE, new int[] {built}));
        assertEquals(0, sphere.layer(1).structurePoints());
        assertEquals(EditResult.OK, sphere.redesign(1, design, false));
        assertEquals(11, sphere.layer(1).grid().nodeCount());
    }

    @Test
    void progressFollowsTheElementsWhenOthersChange() {
        DysonSphere sphere = sphere();
        int built = buildNode(sphere, 5);
        double[] position = sphere.layer(1).grid().node(built).clone();
        DysonDesign design = DysonDesign.of(sphere.layer(1));
        // 删一个没建的、编号比它小的节点，建了的那个下标会变
        design.removeNodes(0);
        design.paintShell(0, 0xFF48D8E0);
        assertEquals(EditResult.OK, sphere.redesign(1, design, false));
        DysonLayer layer = sphere.layer(1);
        int now = -1;
        for (int i = 0; i < layer.grid().nodeCount(); i++) {
            if (SphereGeometry.angle(layer.grid().node(i), position) < 1e-12) {
                now = i;
            }
        }
        assertEquals(4, now);
        assertTrue(layer.nodeBuilt(now));
        assertEquals(DysonLayer.NODE_SP, layer.structurePoints());
        assertEquals(0xFF48D8E0, layer.shellColor(0));
    }

    @Test
    void rocketsInFlightAreRetargetedOrCancelled() {
        DysonSphere sphere = sphere();
        int target = 5;
        sphere.restoreRocket(new DysonSphere.Rocket(400, 1, true, target));
        DysonDesign design = DysonDesign.of(sphere.layer(1));
        design.removeNodes(target);
        // 有火箭在路上也算开始建了
        assertEquals(EditResult.BUILT, sphere.redesign(1, design, false));
        DysonDesign keep = DysonDesign.of(sphere.layer(1));
        keep.removeNodes(0);
        assertEquals(EditResult.OK, sphere.redesign(1, keep, false));
        DysonSphere.Rocket moved = sphere.rockets().getFirst();
        assertEquals(4, moved.index());
        assertTrue(SphereGeometry.angle(sphere.layer(1).grid().node(moved.index()),
                DysonGrid.of(Template.GEODESIC, 1).node(target)) < 1e-12);
        assertEquals(EditResult.OK, sphere.demolish(1, Element.NODE, new int[] {moved.index()}));
        assertTrue(sphere.rockets().isEmpty());
        assertTrue(sphere.layer(1).empty());
    }

    @Test
    void importOnlyIntoAnEmptyLayer() {
        DysonSphere sphere = sphere();
        DysonDesign blueprint = DysonDesign.of(new DysonLayer(9, 0.1, 0, 0, Template.HEXAGONAL, 2));
        assertEquals(EditResult.OK, sphere.redesign(1, blueprint, true));
        assertEquals(80, sphere.layer(1).grid().nodeCount());
        buildFirstNode(sphere);
        assertEquals(EditResult.NOT_EMPTY, sphere.redesign(1, blueprint, true));
        assertEquals(EditResult.NO_LAYER, sphere.redesign(4, blueprint, true));
    }

    @Test
    void radiusChangesKeepACustomGrid() {
        DysonSphere sphere = sphere();
        DysonDesign design = DysonDesign.of(sphere.layer(1));
        design.removeNodes(0);
        design.paintNode(0, 0xFF9C6CF0);
        sphere.redesign(1, design, false);
        assertTrue(sphere.updateLayer(1, 0.3, 10, 20, Template.GEODESIC, 1));
        DysonLayer layer = sphere.layer(1);
        assertTrue(layer.grid().custom());
        assertEquals(0.3, layer.radius(), 1e-12);
        assertEquals(0xFF9C6CF0, layer.nodeColor(0));
        // 换模板就回到模板
        assertTrue(sphere.updateLayer(1, 0.3, 10, 20, Template.LATITUDE, 1));
        assertFalse(sphere.layer(1).grid().custom());
        assertEquals(0, sphere.layer(1).nodeColor(0));
    }

    @Test
    void paintChecksIndices() {
        DysonSphere sphere = sphere();
        assertEquals(EditResult.STALE, sphere.paint(1, Element.FRAME, new int[] {999}, 0x123456));
        assertEquals(EditResult.OK, sphere.paint(1, Element.FRAME, new int[] {2, 3}, 0x123456));
        assertEquals(0xFF123456, sphere.layer(1).frameColor(3));
        assertEquals(EditResult.OK, sphere.paint(1, Element.FRAME, new int[] {3}, 0));
        assertEquals(0, sphere.layer(1).frameColor(3));
    }

    @Test
    void demolishingAFrameEmptiesItsShells() {
        DysonSphere sphere = sphere();
        sphere.complete(1, 90);
        DysonLayer layer = sphere.layer(1);
        assertTrue(layer.fullShells() > 0);
        int[] frames = layer.grid().shellFrames(0);
        assertEquals(EditResult.OK, sphere.demolish(1, Element.FRAME, new int[] {frames[0]}));
        assertEquals(0, layer.cells(0));
        assertEquals(0, layer.frameSp(frames[0]));
        assertTrue(layer.nodeBuilt(layer.grid().frame(frames[0])[0]));
    }
}
