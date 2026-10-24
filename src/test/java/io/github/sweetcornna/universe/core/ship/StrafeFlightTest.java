package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 聚落来袭的战机在星球上扫射（ADR-0034 §二）：有了地板，一轮轮俯冲开火，但不钻到地里。 */
class StrafeFlightTest {
    @Test
    void strafesGroundTargetWithoutHittingTheGround() {
        PirateFlight flight = new PirateFlight(5, 0, 3);
        // 地面在 y = 0，离地至少留 8 格
        flight.floor(8);
        double[] pos = {90, 40, 0};
        double[] vel = {0, 0, 0};
        double[] target = {0, 1, 0};
        double lowest = Double.MAX_VALUE;
        int shots = 0;
        int passes = 0;
        boolean close = false;
        for (int t = 0; t < 900; t++) {
            PirateFlight.Step step = flight.step(pos, vel, target, new double[] {0, 0, 0}, 3, 1, 1);
            vel = step.velocity();
            pos = PirateFlight.add(pos, vel);
            lowest = Math.min(lowest, pos[1]);
            if (step.fire()) {
                shots++;
            }
            double distance = ShipCombat.length(PirateFlight.sub(target, pos));
            if (!close && distance < 30) {
                close = true;
                passes++;
            } else if (close && distance > 50) {
                close = false;
            }
        }
        assertTrue(shots > 0, "没开火");
        assertTrue(passes >= 2, "只冲了 " + passes + " 轮");
        assertTrue(lowest > 2, "飞到了离地 " + lowest + " 格");
    }
}
