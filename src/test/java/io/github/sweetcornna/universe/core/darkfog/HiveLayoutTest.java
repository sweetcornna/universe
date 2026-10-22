package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HiveLayoutTest {
    @Test
    void coreAndFirstReceiverComeFree() {
        assertEquals(HiveBuildingKind.CORE, HiveLayout.slot(HiveLayout.CORE).kind());
        assertEquals(HiveBuildingKind.PHOTON_RECEIVER, HiveLayout.slot(HiveLayout.FIRST_RECEIVER).kind());
        assertEquals(0, HiveLayout.slot(HiveLayout.CORE).cost());
        assertEquals(0, HiveLayout.slot(HiveLayout.FIRST_RECEIVER).cost());
        for (int i = 2; i < HiveLayout.size(); i++) {
            assertTrue(HiveLayout.slot(i).cost() > 0, "slot " + i + " is free");
        }
    }

    @Test
    void slotsFitInsideTheHiveAndKeepApart() {
        for (int i = 0; i < HiveLayout.size(); i++) {
            HiveLayout.Slot a = HiveLayout.slot(i);
            double r = Math.sqrt(a.dx() * a.dx() + a.dy() * a.dy() + a.dz() * a.dz());
            assertTrue(r <= HiveLayout.RADIUS, "slot " + i + " sticks out");
            for (int j = i + 1; j < HiveLayout.size(); j++) {
                HiveLayout.Slot b = HiveLayout.slot(j);
                double dx = a.dx() - b.dx();
                double dy = a.dy() - b.dy();
                double dz = a.dz() - b.dz();
                assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) >= 14, "slots " + i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void everyPortAndTowerAppearsEarly() {
        for (HiveBuildingKind kind : HiveBuildingKind.values()) {
            int first = Integer.MAX_VALUE;
            for (HiveLayout.Slot slot : HiveLayout.SLOTS) {
                if (slot.kind() == kind) {
                    first = Math.min(first, slot.minLevel());
                }
            }
            assertTrue(first <= 3, kind + " first appears at level " + first);
        }
        for (SpaceUnitKind kind : SpaceUnitKind.values()) {
            assertTrue(HiveLayout.portCapacity(kind) > 0);
        }
    }

    @Test
    void levelsNeverGoDownAlongTheOrder() {
        for (int i = 2; i < HiveLayout.size(); i++) {
            assertTrue(HiveLayout.slot(i).minLevel() >= HiveLayout.slot(i - 1).minLevel(), "slot " + i + " comes before a cheaper level");
        }
    }
}
