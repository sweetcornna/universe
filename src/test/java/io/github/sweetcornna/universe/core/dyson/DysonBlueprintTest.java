package io.github.sweetcornna.universe.core.dyson;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.dyson.DysonBlueprint.Error;
import io.github.sweetcornna.universe.core.dyson.DysonGrid.Template;

class DysonBlueprintTest {
    private static DysonDesign painted() {
        DysonDesign design = DysonDesign.of(new DysonLayer(1, 0.1, 0, 0, Template.GEODESIC, 2));
        design.paintNode(3, 0xFF4C8CF0);
        design.paintFrame(7, 0xFFE84A3A);
        design.paintShell(11, 0xFF58D068);
        design.removeNodes(0);
        return design;
    }

    @Test
    void roundTripKeepsGridAndColors() {
        DysonDesign design = painted();
        String text = DysonBlueprint.export(design);
        assertTrue(text.startsWith("udyson1:"));
        DysonBlueprint.Parsed parsed = DysonBlueprint.parse("  " + text + "\n");
        assertNull(parsed.error());
        DysonDesign back = parsed.design();
        assertEquals(design.nodeCount(), back.nodeCount());
        assertEquals(design.frameCount(), back.frameCount());
        assertEquals(design.shellCount(), back.shellCount());
        for (int i = 0; i < design.nodeCount(); i++) {
            // 1e-5 度
            assertTrue(SphereGeometry.angle(design.position(i), back.position(i)) < Math.toRadians(1e-4));
        }
        assertArrayEquals(design.nodeColors(), back.nodeColors());
        assertArrayEquals(design.frameColors(), back.frameColors());
        assertArrayEquals(design.shellColors(), back.shellColors());
        assertEquals(Template.GEODESIC, back.base());
        assertEquals(2, back.density());
    }

    @Test
    void packKeepsExactPositions() {
        DysonDesign design = painted();
        DysonDesign back = DysonBlueprint.unpack(DysonBlueprint.pack(design));
        assertNotNull(back);
        assertTrue(design.sameAs(back));
    }

    @Test
    void badStringsSayWhy() {
        assertEquals(Error.EMPTY, DysonBlueprint.parse("   ").error());
        assertEquals(Error.NOT_BLUEPRINT, DysonBlueprint.parse("hello").error());
        assertEquals(Error.NOT_BLUEPRINT, DysonBlueprint.parse("udysonX:abc:0").error());
        DysonBlueprint.Parsed future = DysonBlueprint.parse("udyson7:abc:0");
        assertEquals(Error.WRONG_VERSION, future.error());
        assertEquals(7, future.version());
        String good = DysonBlueprint.export(painted());
        assertEquals(Error.CORRUPT, DysonBlueprint.parse(good.substring(0, good.lastIndexOf(':'))).error());
        // 改一个字
        int at = good.indexOf(':') + 5;
        String flipped = good.substring(0, at) + (good.charAt(at) == 'A' ? 'B' : 'A') + good.substring(at + 1);
        assertEquals(Error.CORRUPT, DysonBlueprint.parse(flipped).error());
        assertEquals(Error.CORRUPT, DysonBlueprint.parse("udyson1:!!!:0").error());
    }

    @Test
    void geometricallyBadBlueprintsAreInvalid() {
        DysonDesign design = new DysonDesign(Template.GEODESIC, 1);
        design.rawNode(SphereGeometry.fromLatLon(-10, 0), 0);
        design.rawNode(SphereGeometry.fromLatLon(10, 20), 0);
        design.rawNode(SphereGeometry.fromLatLon(-10, 20), 0);
        design.rawNode(SphereGeometry.fromLatLon(10, 0), 0);
        design.rawFrame(0, 1, 0);
        design.rawFrame(2, 3, 0);
        DysonBlueprint.Parsed parsed = DysonBlueprint.parse(DysonBlueprint.export(design));
        assertEquals(Error.INVALID, parsed.error());
        assertEquals(DysonDesign.Problem.FRAME_CROSSES, parsed.problem());
    }

    @Test
    void emptyDesignRoundTrips() {
        DysonDesign empty = new DysonDesign(Template.LATITUDE, 3);
        DysonBlueprint.Parsed parsed = DysonBlueprint.parse(DysonBlueprint.export(empty));
        assertNull(parsed.error());
        assertEquals(0, parsed.design().nodeCount());
        assertEquals(Template.LATITUDE, parsed.design().base());
    }
}
