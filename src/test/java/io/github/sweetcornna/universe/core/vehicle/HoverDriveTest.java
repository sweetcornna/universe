package io.github.sweetcornna.universe.core.vehicle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HoverDriveTest {
    /** 从 {@code start} 格高放下，按 {@code gravity} 的重力悬 {@code ticks} 刻，返回最后离地多高。地面在 0。 */
    private static double settle(double start, double gravity, int ticks) {
        double height = start;
        double vy = 0;
        for (int i = 0; i < ticks; i++) {
            vy = HoverDrive.lift(height, vy, gravity);
            height = Math.max(0, height + vy);
        }
        return height;
    }

    @Test
    void settlesAtHoverHeight() {
        assertEquals(HoverDrive.HEIGHT, settle(0, 1, 60), 0.02, "从地上升到悬浮高度");
        assertEquals(HoverDrive.HEIGHT, settle(3, 1, 60), 0.02, "从高处落到悬浮高度");
    }

    @Test
    void settlesWithinASecond() {
        assertEquals(HoverDrive.HEIGHT, settle(0, 1, 20), 0.1, "一秒内稳住");
    }

    @Test
    void heightDoesNotDependOnGravity() {
        assertEquals(settle(0, 0.3, 80), settle(0, 2.5, 80), 1e-9, "轻重力和重重力的星球上悬得一样高");
    }

    @Test
    void waterCountsAsGround() {
        // 实体把水面当成地面探：水面以上和地面以上是同一个数，悬浮的结果一样
        assertTrue(HoverDrive.hovering(HoverDrive.HEIGHT), "悬在水面上算着地，油门有抓地力");
        assertEquals(HoverDrive.HEIGHT, settle(HoverDrive.HEIGHT, 1, 40), 1e-6, "停在水面上不沉");
    }

    @Test
    void fallsBeyondSenseRange() {
        assertFalse(HoverDrive.hovering(HoverDrive.SENSE + 0.5), "推力垫够不着地面");
        double vy = HoverDrive.lift(HoverDrive.SENSE + 2, 0, 1);
        assertEquals(-HoverDrive.GRAVITY * 0.98, vy, 1e-9, "冲下悬崖只剩重力");
        assertTrue(HoverDrive.lift(HoverDrive.SENSE + 2, 0, 0.3) > vy, "轻重力的星球上掉得慢");
    }

    @Test
    void liftIsBounded() {
        assertTrue(HoverDrive.lift(0, 0, 1) <= HoverDrive.MAX_LIFT);
        assertTrue(HoverDrive.lift(HoverDrive.SENSE, -10, 1) >= -HoverDrive.MAX_LIFT);
    }
}
