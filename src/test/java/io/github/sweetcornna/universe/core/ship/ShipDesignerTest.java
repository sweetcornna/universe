package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Role;
import io.github.sweetcornna.universe.core.tech.Grade;

class ShipDesignerTest {
    private static final int SEEDS = 150;

    @Test
    void everyDesignIsAFlyableShipOfTheRequestedRating() {
        for (ShipType type : ShipType.values()) {
            for (Grade grade : Grade.values()) {
                for (long seed = 0; seed < SEEDS; seed++) {
                    ShipDesign design = ShipDesigner.design(seed * 7919 + type.ordinal() * 31 + grade.ordinal(), type, grade);
                    String what = type + " " + grade + " seed " + seed;
                    assertEquals(1, design.cells().stream().filter(c -> c.role() == Role.HELM).count(), what);
                    assertTrue(connected(design), what + " is in pieces");
                    ShipStats stats = design.stats();
                    assertTrue(stats.thrustToWeight(1) >= ShipDesigner.MIN_TWR, what + " TWR " + stats.thrustToWeight(1));
                    assertTrue(stats.impulse() > 0 && stats.warpDrives() > 0 && stats.fuelTanks() > 0, what);
                    ShipRating rating = design.rating();
                    assertEquals(type, rating.type(), what);
                    assertEquals(grade, rating.grade(), what + " quality " + rating.quality());
                    int[] size = design.size();
                    assertTrue(size[2] <= ShipDesigner.MAX_LENGTH && size[0] <= 13 && size[1] <= 7, what + " size " + size[0] + "x" + size[1] + "x" + size[2]);
                }
            }
        }
    }

    @Test
    void sameSeedSameShip() {
        ShipDesign a = ShipDesigner.design(42, ShipType.FIGHTER, Grade.A);
        ShipDesign b = ShipDesigner.design(42, ShipType.FIGHTER, Grade.A);
        assertEquals(a, b);
    }

    @Test
    void haulersCarryCargoAndFightersCarryGuns() {
        ShipDesign hauler = ShipDesigner.design(3, ShipType.HAULER, Grade.B);
        assertTrue(hauler.cargoSlots() >= 2 * ShipDesign.CARGO_SLOTS);
        ShipDesign fighter = ShipDesigner.design(3, ShipType.FIGHTER, Grade.B);
        assertTrue(fighter.stats().cannons() >= 2);
        ShipDesign explorer = ShipDesigner.design(3, ShipType.EXPLORER, Grade.B);
        assertTrue(explorer.stats().warpDrives() >= 2);
    }

    /** 从驾驶台按六个方向连得到全部格子。 */
    private static boolean connected(ShipDesign design) {
        Map<Long, Cell> cells = new HashMap<>();
        Cell start = null;
        for (Cell c : design.cells()) {
            cells.put(key(c.x(), c.y(), c.z()), c);
            if (c.role() == Role.HELM) {
                start = c;
            }
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        queue.add(start);
        seen.add(key(start.x(), start.y(), start.z()));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            for (int[] d : dirs) {
                long k = key(c.x() + d[0], c.y() + d[1], c.z() + d[2]);
                Cell n = cells.get(k);
                if (n != null && seen.add(k)) {
                    queue.add(n);
                }
            }
        }
        return seen.size() == cells.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }
}
