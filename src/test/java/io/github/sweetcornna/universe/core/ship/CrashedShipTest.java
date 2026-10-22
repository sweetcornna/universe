package io.github.sweetcornna.universe.core.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Role;
import io.github.sweetcornna.universe.core.tech.Grade;

class CrashedShipTest {
    @Test
    void sameSeedSameWreck() {
        assertEquals(CrashedShip.design(42), CrashedShip.design(42));
        assertEquals(CrashedShip.wreck(CrashedShip.design(42), 42), CrashedShip.wreck(CrashedShip.design(42), 42));
    }

    @Test
    void wreckKeepsEveryPartAndStillFlies() {
        for (long seed = 0; seed < 200; seed++) {
            ShipDesign design = CrashedShip.design(seed);
            List<Cell> wreck = CrashedShip.wreck(design, seed);
            assertTrue(wreck.size() < design.cells().size(), "nothing broke on " + seed);
            // 功能部件一个不少
            Map<Role, Integer> before = roles(design.cells());
            Map<Role, Integer> after = roles(wreck);
            for (Role role : Role.values()) {
                if (role != Role.HULL && role != Role.TRIM && role != Role.LAMP) {
                    assertEquals(before.getOrDefault(role, 0), after.getOrDefault(role, 0), role + " lost on " + seed);
                }
            }
            // 剩下的还连着驾驶台，修好以后照样飞得起来
            Map<Long, Cell> cells = new HashMap<>();
            Cell helm = null;
            for (Cell cell : wreck) {
                cells.put(((long) (cell.x() & 0xFFFFF) << 40) | ((long) (cell.y() & 0xFFFFF) << 20) | (cell.z() & 0xFFFFF), cell);
                if (cell.role() == Role.HELM) {
                    helm = cell;
                }
            }
            assertTrue(CrashedShip.connected(cells, helm), "wreck split apart on " + seed);
            ShipDesign repaired = new ShipDesign(design.type(), design.grade(), design.primary(), design.accent(), wreck);
            assertTrue(repaired.stats().thrustToWeight(1) >= ShipDesigner.MIN_TWR, "can't lift on " + seed);
        }
    }

    @Test
    void mostWrecksAreLowGrade() {
        Map<Grade, Integer> grades = new EnumMap<>(Grade.class);
        for (long seed = 0; seed < 400; seed++) {
            grades.merge(CrashedShip.design(seed).grade(), 1, Integer::sum);
        }
        assertTrue(grades.getOrDefault(Grade.C, 0) > grades.getOrDefault(Grade.A, 0) * 3);
        assertTrue(grades.getOrDefault(Grade.S, 0) > 0, "never an S: " + grades);
    }

    private static Map<Role, Integer> roles(List<Cell> cells) {
        Map<Role, Integer> out = new EnumMap<>(Role.class);
        for (Cell cell : cells) {
            out.merge(cell.role(), 1, Integer::sum);
        }
        return out;
    }
}
