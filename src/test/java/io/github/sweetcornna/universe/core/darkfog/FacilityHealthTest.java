package io.github.sweetcornna.universe.core.darkfog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FacilityHealthTest {
    @Test
    void maxFollowsTheAdr() {
        assertEquals(60, FacilityHealth.max("conveyor_belt"));
        assertEquals(60, FacilityHealth.max("sorter_mk3"));
        assertEquals(400, FacilityHealth.max("assembler"));
        assertEquals(400, FacilityHealth.max("something_new"));
        assertEquals(1200, FacilityHealth.max("interstellar_logistics_station"));
        assertEquals(1000, FacilityHealth.max("signal_tower"));
        for (String turret : List.of("gauss_turret", "laser_turret", "implosion_cannon", "missile_turret")) {
            float max = FacilityHealth.max(turret);
            assertTrue(max >= 300 && max <= 600, turret);
        }
        // 战场分析基地比别的都耐打，能当墙用
        assertTrue(FacilityHealth.max("battlefield_analysis_base") > FacilityHealth.LARGE);
    }

    @Test
    void damageAndRepairStayInBounds() {
        assertEquals(0, FacilityHealth.afterDamage(30, 100));
        assertEquals(20, FacilityHealth.afterDamage(30, 10));
        assertEquals(30, FacilityHealth.afterDamage(30, -5));
        assertEquals(400, FacilityHealth.afterRepair(350, 400, 120));
        assertEquals(170, FacilityHealth.afterRepair(50, 400, 120));
        assertEquals(0.5F, FacilityHealth.ratio(200, 400));
        assertTrue(FacilityHealth.full(399.7F, 400));
        assertFalse(FacilityHealth.full(399, 400));
    }

    @Test
    void repairQueueTakesTheWorstFirst() {
        List<RepairQueue.Candidate> candidates = List.of(
                new RepairQueue.Candidate(1, 4, 0.9F),
                new RepairQueue.Candidate(2, 100, 0.2F),
                new RepairQueue.Candidate(3, 1, 0.2F),
                new RepairQueue.Candidate(4, 2000, 0.1F));
        List<RepairQueue.Candidate> picked = RepairQueue.pick(candidates, 30 * 30, 2);
        assertEquals(List.of(3L, 2L), picked.stream().map(RepairQueue.Candidate::key).toList());
        // 范围外的不修
        assertEquals(3, RepairQueue.pick(candidates, 30 * 30, 10).size());
        assertTrue(RepairQueue.pick(candidates, 30 * 30, 0).isEmpty());
    }

    @Test
    void pickupFilterCycles() {
        assertTrue(PickupFilter.ALL.accepts(false, false));
        assertTrue(PickupFilter.DARK_FOG.accepts(true, false));
        assertFalse(PickupFilter.DARK_FOG.accepts(false, true));
        assertTrue(PickupFilter.STORED.accepts(false, true));
        assertFalse(PickupFilter.STORED.accepts(true, false));
        assertFalse(PickupFilter.OFF.accepts(true, true));
        PickupFilter filter = PickupFilter.ALL;
        for (int i = 0; i < PickupFilter.values().length; i++) {
            filter = filter.next();
        }
        assertEquals(PickupFilter.ALL, filter);
        assertEquals(PickupFilter.ALL, PickupFilter.byIndex(99));
    }
}
