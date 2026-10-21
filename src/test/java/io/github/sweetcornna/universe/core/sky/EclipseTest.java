package io.github.sweetcornna.universe.core.sky;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EclipseTest {
    @Test
    void apartDiscsDoNotDim() {
        assertEquals(1, Eclipse.visible(1, 0.5, 1.6), 1e-12);
        assertEquals(1, Eclipse.visible(1, 3, 4.01), 1e-12);
    }

    @Test
    void biggerBodyInFrontCoversTheStar() {
        assertEquals(0, Eclipse.visible(1, 3, 0.5), 1e-12);
        assertEquals(0, Eclipse.visible(1, 1, 0), 1e-12);
    }

    @Test
    void smallBodyCoversItsOwnArea() {
        // 金星凌日那样：小圆面整个在恒星里面
        assertEquals(1 - 0.04, Eclipse.visible(1, 0.2, 0.3), 1e-12);
    }

    @Test
    void halfwayCoverageMatchesTheLensFormula() {
        // 两个单位圆，圆心相距 1：公共部分是 2π/3 − √3/2
        double expected = 1 - (2 * Math.PI / 3 - Math.sqrt(3) / 2) / Math.PI;
        assertEquals(expected, Eclipse.visible(1, 1, 1), 1e-9);
    }

    @Test
    void coverageGrowsAsTheBodyMovesIn() {
        double previous = 1;
        for (double d = 2.5; d >= 0; d -= 0.05) {
            double v = Eclipse.visible(1, 1.5, d);
            assertTrue(v <= previous + 1e-12, "d=" + d);
            previous = v;
        }
        assertEquals(0, previous, 1e-12);
    }
}
