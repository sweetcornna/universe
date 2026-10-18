package io.github.sweetcornna.universe.core.galaxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StarMapCameraTest {
    private static final double CX = 200;
    private static final double CY = 100;
    private static final double H = 200;

    @Test
    void focusIsAtScreenCenter() {
        StarMapCamera camera = new StarMapCamera();
        camera.jumpTo(3, -1, 7);
        camera.rotate(57, -20);
        StarMapCamera.Projected p = camera.project(3, -1, 7, CX, CY, H);
        assertNotNull(p);
        assertEquals(CX, p.x(), 1e-9);
        assertEquals(CY, p.y(), 1e-9);
        assertEquals(camera.distance(), p.depth(), 1e-9);
    }

    @Test
    void screenAxesFollowYaw() {
        StarMapCamera camera = new StarMapCamera();
        camera.rotate(-camera.yaw(), -camera.pitch());
        // 偏航 0、俯仰 0：相机在 +z 看向 -z，+x 在右边，+y 在上面
        StarMapCamera.Projected right = camera.project(1, 0, 0, CX, CY, H);
        StarMapCamera.Projected up = camera.project(0, 1, 0, CX, CY, H);
        assertTrue(right.x() > CX && Math.abs(right.y() - CY) < 1e-9);
        assertTrue(up.y() < CY && Math.abs(up.x() - CX) < 1e-9);
        // 转 90°：相机在 +x 看向 -x，-z 在右边
        camera.rotate(90, 0);
        StarMapCamera.Projected minusZ = camera.project(0, 0, -1, CX, CY, H);
        assertTrue(minusZ.x() > CX);
    }

    @Test
    void pointsBehindTheCameraAreNotProjected() {
        StarMapCamera camera = new StarMapCamera();
        double[] eye = camera.eye();
        // 从焦点穿过相机再往后
        assertNull(camera.project(eye[0] * 2, eye[1] * 2, eye[2] * 2, CX, CY, H));
    }

    @Test
    void zoomAndPitchAreClamped() {
        StarMapCamera camera = new StarMapCamera();
        camera.zoom(1000);
        assertEquals(StarMapCamera.MIN_DISTANCE, camera.distance());
        camera.zoom(-1000);
        assertEquals(StarMapCamera.MAX_DISTANCE, camera.distance());
        camera.rotate(0, 500);
        assertEquals(StarMapCamera.MAX_PITCH, camera.pitch());
        camera.rotate(-400, 0);
        assertTrue(camera.yaw() >= 0 && camera.yaw() < 360);
    }

    @Test
    void panMovesTheWorldWithTheMouse() {
        StarMapCamera camera = new StarMapCamera();
        camera.jumpTo(0, 0, 0);
        StarMapCamera.Projected before = camera.project(0, 0, 0, CX, CY, H);
        camera.pan(30, 0, H);
        StarMapCamera.Projected after = camera.project(0, 0, 0, CX, CY, H);
        // 原点跟着鼠标往右移了 30（水平面上，俯仰不影响左右）
        assertEquals(before.x() + 30, after.x(), 1e-6);
    }

    @Test
    void focusMovesSmoothly() {
        StarMapCamera camera = new StarMapCamera();
        camera.jumpTo(0, 0, 0);
        camera.focus(10, 0, 0);
        camera.update(0.05);
        assertTrue(camera.focusX() > 0 && camera.focusX() < 10);
        for (int i = 0; i < 100; i++) {
            camera.update(0.05);
        }
        assertEquals(10, camera.focusX(), 1e-6);
    }
}
