package io.github.sweetcornna.universe.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 聚落的数（ADR-0034 §二）。 */
class SettlementRulesTest {
    @Test
    void gradesMultiply() {
        assertEquals(10, BuildingType.FARM.productivity(0), 1e-9);
        assertEquals(15, BuildingType.FARM.productivity(1), 1e-9);
        assertEquals(20, BuildingType.FARM.productivity(2), 1e-9);
        assertEquals(30, BuildingType.FARM.productivity(3), 1e-9);
        assertEquals("S", BuildingType.letter(3));
    }

    @Test
    void everyBuildingHasThreeStages() {
        for (BuildingType type : BuildingType.values()) {
            for (int stage = 0; stage < BuildingType.STAGES; stage++) {
                assertFalse(type.stageCost(stage).isEmpty(), type.id() + " 第 " + stage + " 段");
            }
        }
        for (int grade = 1; grade < BuildingType.GRADES; grade++) {
            assertFalse(BuildingType.upgradeCost(grade).isEmpty());
        }
    }

    @Test
    void footprintsFitThePlot() {
        for (BuildingType type : BuildingType.values()) {
            assertTrue(type.halfX() < 5 && type.halfZ() < 5, type.id());
        }
    }

    @Test
    void raidOdds() {
        assertEquals(0, SettlementRules.raidChance(40), 1e-9);
        assertEquals(0.2, SettlementRules.raidChance(80), 1e-9);
        assertEquals(1, SettlementRules.raidChance(100), 1e-9);
        assertEquals(3, SettlementRules.fighters(0, 10));
        assertEquals(6, SettlementRules.fighters(3, 100));
    }

    @Test
    void luckSwingsAQuarter() {
        assertTrue(SettlementRules.defended(100, 75, 0));
        assertFalse(SettlementRules.defended(100, 76, 0));
        assertTrue(SettlementRules.defended(100, 125, 1));
    }

    @Test
    void classThresholds() {
        assertEquals(0, SettlementRules.settlementClass(0));
        assertEquals(1, SettlementRules.settlementClass(8));
        assertEquals(2, SettlementRules.settlementClass(18));
        assertEquals(3, SettlementRules.settlementClass(30));
        assertEquals(60, SettlementRules.reserveCap(0));
        assertEquals(200, SettlementRules.reserveCap(3));
    }

    @Test
    void driftMovesAQuarterAtLeastOne() {
        assertEquals(10, SettlementRules.drift(50, 90), 1e-9);
        assertEquals(-1, SettlementRules.drift(50, 47), 1e-9);
        assertEquals(0.5, SettlementRules.drift(50, 50.5), 1e-9);
    }

    @Test
    void costsUseTagsOrIds() {
        Cost stone = BuildingType.OFFICE.stageCost(0).getFirst();
        assertTrue(stone.tag());
        assertEquals("universe:settlement/stone", stone.id());
        Cost steel = BuildingType.OFFICE.stageCost(1).get(1);
        assertFalse(steel.tag());
        assertEquals("universe:steel", steel.id());
    }
}
