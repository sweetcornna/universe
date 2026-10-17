package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ship.ShipFlight.Controls;
import io.github.sweetcornna.universe.core.ship.ShipFlight.Environment;

class ShipTest {
    /** 40 个方块的小船：4 个推进器、1 个燃料舱、1 个驾驶座。 */
    private static final ShipStats SMALL = ShipStats.of(ShipPart.HULL, 34, ShipPart.HELM, 1, ShipPart.THRUSTER, 4, ShipPart.FUEL_TANK, 1);
    /** 推重比 1.35 的船。 */
    private static final ShipStats LIFTER = ShipStats.of(ShipPart.HULL, 34, ShipPart.HELM, 1, ShipPart.THRUSTER, 5, ShipPart.FUEL_TANK, 1);
    private static final Controls UP = new Controls(false, false, false, false, true, false, false);
    private static final Controls FORWARD = new Controls(true, false, false, false, false, false, false);
    private static final Controls BOOST = new Controls(true, false, false, false, false, false, true);

    @Test
    void statsAddUp() {
        assertEquals(40, SMALL.blocks());
        assertEquals(34 + 1 + 8 + 1.5, SMALL.mass(), 1e-9);
        assertEquals(48, SMALL.thrust(), 1e-9);
        assertEquals(ShipStats.FUEL_PER_TANK, SMALL.fuelCapacity(), 1e-9);
        assertTrue(SMALL.canLift(1));
        assertFalse(SMALL.canLift(2));
        assertTrue(SMALL.thrustToWeight(0) > 1000);
    }

    @Test
    void takesOffWhenThrustIsEnough() {
        ShipFlight flight = new ShipFlight(0);
        double y = 0;
        for (int i = 0; i < 40; i++) {
            flight.step(LIFTER, UP, Environment.HOMEWORLD, true, i == 0);
            y += flight.vy();
        }
        assertTrue(y > 2, "climbed " + y);
    }

    @Test
    void cannotTakeOffWhenTooHeavy() {
        ShipStats heavy = ShipStats.of(ShipPart.HULL, 200, ShipPart.HELM, 1, ShipPart.THRUSTER, 4);
        ShipFlight flight = new ShipFlight(0);
        for (int i = 0; i < 40; i++) {
            flight.step(heavy, UP, Environment.HOMEWORLD, true, true);
            assertEquals(0, flight.vy(), 1e-9);
        }
    }

    @Test
    void hoversWithoutInputAndFallsWithoutFuel() {
        ShipFlight flight = new ShipFlight(0);
        for (int i = 0; i < 60; i++) {
            flight.step(SMALL, Controls.NONE, Environment.HOMEWORLD, true, false);
        }
        assertEquals(0, flight.vy(), 1e-6);
        for (int i = 0; i < 20; i++) {
            flight.step(SMALL, Controls.NONE, Environment.HOMEWORLD, false, false);
        }
        assertTrue(flight.vy() < -0.5, "falling at " + flight.vy());
    }

    @Test
    void acceleratesForwardAndSlowsDown() {
        ShipFlight flight = new ShipFlight(0);
        for (int i = 0; i < 100; i++) {
            flight.step(SMALL, FORWARD, Environment.HOMEWORLD, true, false);
        }
        // 偏航 0 朝南（+z）
        assertTrue(flight.vz() > 0.8, "vz " + flight.vz());
        assertEquals(0, flight.vx(), 1e-9);
        double cruise = flight.vz();
        for (int i = 0; i < 100; i++) {
            flight.step(SMALL, BOOST, Environment.HOMEWORLD, true, false);
        }
        assertTrue(flight.vz() > cruise * 1.4, "boost " + flight.vz());
        for (int i = 0; i < 200; i++) {
            flight.step(SMALL, Controls.NONE, Environment.HOMEWORLD, true, false);
        }
        assertEquals(0, flight.speed(), 1e-6);
    }

    @Test
    void impulseEngineIsMuchFasterInSpace() {
        ShipStats withImpulse = ShipStats.of(ShipPart.HULL, 30, ShipPart.HELM, 1, ShipPart.THRUSTER, 4, ShipPart.IMPULSE_ENGINE, 1);
        ShipFlight plain = new ShipFlight(0);
        ShipFlight boosted = new ShipFlight(0);
        double fuel = 0;
        for (int i = 0; i < 200; i++) {
            plain.step(withImpulse, FORWARD, Environment.SPACE, true, false);
            fuel += boosted.step(withImpulse, BOOST, Environment.SPACE, true, false);
        }
        assertTrue(boosted.speed() > plain.speed() * 5, plain.speed() + " vs " + boosted.speed());
        assertTrue(boosted.speed() <= ShipFlight.MAX_IMPULSE_SPEED + 1e-9);
        assertTrue(fuel > 200 * ShipFlight.FUEL_PER_IMPULSE * 0.9);
        // 关掉脉冲引擎后几秒内降回常规速度
        for (int i = 0; i < 60; i++) {
            boosted.step(withImpulse, Controls.NONE, Environment.SPACE, true, false);
        }
        assertTrue(boosted.speed() < plain.speed() * 1.5, "still at " + boosted.speed());
    }

    @Test
    void turnsRightWhenPressingRight() {
        ShipFlight flight = new ShipFlight(0);
        Controls right = new Controls(false, false, false, true, false, false, false);
        for (int i = 0; i < 10; i++) {
            flight.step(SMALL, right, Environment.HOMEWORLD, true, false);
        }
        assertTrue(flight.yaw() > 5 && flight.yaw() < 90, "yaw " + flight.yaw());
    }

    @Test
    void consumesFuelWhileHovering() {
        ShipFlight flight = new ShipFlight(0);
        double fuel = flight.step(SMALL, Controls.NONE, Environment.HOMEWORLD, true, false);
        assertTrue(fuel > 0);
        assertEquals(0, flight.step(SMALL, Controls.NONE, Environment.HOMEWORLD, true, true), 1e-9);
    }

    @Test
    void assemblesConnectedBlocksOnly() {
        Set<Long> blocks = new HashSet<>();
        // 5×1×3 的甲板，加一根桅杆
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(ShipAssembler.pack(x, 64, z));
            }
        }
        blocks.add(ShipAssembler.pack(2, 65, 1));
        blocks.add(ShipAssembler.pack(2, 66, 1));
        // 不相连的一块
        blocks.add(ShipAssembler.pack(10, 64, 10));
        ShipAssembler.Result result = ShipAssembler.assemble((x, y, z) -> blocks.contains(ShipAssembler.pack(x, y, z)), 2, 65, 1, 100);
        assertFalse(result.tooLarge());
        assertEquals(17, result.positions().size());
        assertEquals(ShipAssembler.pack(2, 65, 1), result.positions().get(0));
        assertTrue(ShipAssembler.assemble((x, y, z) -> blocks.contains(ShipAssembler.pack(x, y, z)), 2, 65, 1, 10).tooLarge());
        // 连到地形上就会无穷无尽
        assertTrue(ShipAssembler.assemble((x, y, z) -> y <= 64, 0, 64, 0, ShipAssembler.MAX_BLOCKS).tooLarge());
    }

    @Test
    void packRoundTripsNegativeCoordinates() {
        int[][] samples = {{0, 0, 0}, {-1, -64, -1}, {-3000, 319, 2900}, {33554431, 2047, -33554432}};
        for (int[] s : samples) {
            long p = ShipAssembler.pack(s[0], s[1], s[2]);
            assertEquals(s[0], ShipAssembler.unpackX(p));
            assertEquals(s[1], ShipAssembler.unpackY(p));
            assertEquals(s[2], ShipAssembler.unpackZ(p));
        }
    }

    @Test
    void shapeIsCentredAndRotatesOnTheGrid() {
        List<Long> cube = new ArrayList<>();
        for (int x = 10; x < 13; x++) {
            for (int y = 70; y < 73; y++) {
                for (int z = -5; z < -2; z++) {
                    cube.add(ShipAssembler.pack(x, y, z));
                }
            }
        }
        ShipShape.Placed placed = ShipShape.fromWorld(cube);
        assertEquals(11, placed.originX());
        assertEquals(70, placed.originY());
        assertEquals(-4, placed.originZ());
        ShipShape shape = placed.shape();
        assertEquals(-1, shape.minX());
        assertEquals(1, shape.maxX());
        assertEquals(3, shape.height());
        int hidden = 0;
        for (int i = 0; i < shape.size(); i++) {
            if (!shape.exposed(i)) {
                hidden++;
                assertEquals(0, shape.x(i));
                assertEquals(1, shape.y(i));
                assertEquals(0, shape.z(i));
            }
        }
        assertEquals(1, hidden);
        // 连续旋转和四分之一圈旋转一致
        for (int turns = 0; turns < 4; turns++) {
            int[] q = ShipShape.rotateQuarter(2, 1, turns);
            double[] r = ShipShape.rotate(2, 1, turns * 90f);
            assertEquals(q[0], r[0], 1e-9);
            assertEquals(q[1], r[1], 1e-9);
        }
        assertEquals(1, ShipShape.quarterTurns(100));
        assertEquals(0, ShipShape.quarterTurns(-30));
        assertEquals(3, ShipShape.quarterTurns(-80));
    }
}
