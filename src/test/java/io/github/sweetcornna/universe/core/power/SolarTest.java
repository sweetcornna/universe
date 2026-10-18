package io.github.sweetcornna.universe.core.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SolarTest {
    @Test
    void homeIsOneAndBrightStarsAreCapped() {
        assertEquals(1, Solar.ratio(1, 1), 1e-9);
        // 戴森球计划的曲线：0.4 AU 处一倍七左右，5 AU 处不到四成
        assertEquals(1.73, Solar.ratio(1, 0.4), 0.01);
        assertTrue(Solar.ratio(1, 5) < 0.4);
        assertEquals(Solar.MAX_RATIO, Solar.ratio(50_000, 1));
        assertEquals(Solar.MAX_RATIO, Solar.ratio(1, 0));
        // 黑洞、中子星这种几乎不发光的
        assertTrue(Solar.ratio(0.0001, 1) < 0.1);
        assertEquals(0, Solar.ratio(0, 1));
    }

    @Test
    void elevationFollowsTheSun() {
        assertEquals(1, Solar.elevation(0), 1e-9);
        assertEquals(0.5, Solar.elevation(60), 1e-9);
        assertEquals(0.5, Solar.elevation(-60), 1e-9);
        assertEquals(0, Solar.elevation(90), 1e-9);
        assertEquals(0, Solar.elevation(180));
        assertEquals(0, Solar.elevation(250));
    }

    @Test
    void weatherDimsTheOutput() {
        assertEquals(1, Solar.weather(false, false));
        assertEquals(0.6, Solar.weather(true, false));
        assertEquals(0.3, Solar.weather(true, true));
    }

    @Test
    void trackingFacesTheSunAndRestsFlatAtNight() {
        assertEquals(0, Solar.trackingAngle(0), 1e-9);
        assertEquals(40, Solar.trackingAngle(40), 1e-9);
        assertEquals(-40, Solar.trackingAngle(320), 1e-9);
        assertEquals(Solar.MAX_TILT, Solar.trackingAngle(85), 1e-9);
        assertEquals(-Solar.MAX_TILT, Solar.trackingAngle(-85), 1e-9);
        assertEquals(0, Solar.trackingAngle(180), 1e-9);
        // 日落后连续地放平，不跳
        double previous = Solar.trackingAngle(90);
        for (double angle = 90; angle <= 130; angle += 0.5) {
            double tilt = Solar.trackingAngle(angle);
            assertTrue(Math.abs(tilt - previous) < 2, "跳变 @" + angle);
            previous = tilt;
        }
    }
}
