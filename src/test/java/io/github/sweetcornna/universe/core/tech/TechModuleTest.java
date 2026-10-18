package io.github.sweetcornna.universe.core.tech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.procgen.Rng;

class TechModuleTest {
    @Test
    void rolledValuesStayInsideTheGradeRange() {
        Rng rng = Rng.of(42);
        for (ModuleType type : ModuleType.values()) {
            for (Grade grade : Grade.values()) {
                for (int i = 0; i < 20; i++) {
                    TechModule module = TechModule.roll(type, grade, rng);
                    for (Stat stat : type.stats()) {
                        double value = module.value(stat);
                        assertTrue(value >= stat.min(grade) - 1e-9 && value <= stat.max(grade) + 1e-9,
                                type + " " + grade + " " + stat + " = " + value);
                    }
                    assertTrue(module.quality() >= 0 && module.quality() <= 1);
                }
            }
        }
    }

    @Test
    void rollsAreDeterministic() {
        assertEquals(TechModule.roll(ModuleType.JET_BOOSTER, Grade.A, Rng.of(7)), TechModule.roll(ModuleType.JET_BOOSTER, Grade.A, Rng.of(7)));
    }

    @Test
    void higherGradesAreStronger() {
        for (Stat stat : Stat.values()) {
            for (int i = 0; i < Grade.values().length - 1; i++) {
                Grade low = Grade.values()[i];
                Grade high = Grade.values()[i + 1];
                assertEquals(stat.max(low), stat.min(high), 1e-9, stat + " ranges should meet");
                assertTrue(stat.max(high) > stat.max(low));
            }
        }
    }

    @Test
    void onlyThreeOfATypeCountAndTheBestOnesWin() {
        TechModule weak = new TechModule(ModuleType.BEAM_FOCUS, Grade.C, List.of(0.1));
        TechModule strong = new TechModule(ModuleType.BEAM_FOCUS, Grade.S, List.of(0.6));
        TechBonuses bonuses = TechBonuses.of(List.of(weak, strong, strong, strong));
        assertEquals(1.8, bonuses.get(Stat.MINING), 1e-9);
        assertEquals(2.8, bonuses.boost(Stat.MINING), 1e-9);
        // 不同种类的同一项不受这个限制
        TechModule cooling = new TechModule(ModuleType.HEAT_SINK, Grade.B, List.of(0.25));
        assertEquals(0.25, TechBonuses.of(List.of(cooling, weak)).get(Stat.COOLING), 1e-9);
        assertEquals(1 / 1.25, TechBonuses.of(List.of(cooling)).reduction(Stat.COOLING), 1e-9);
    }

    @Test
    void cooldownAndAnalysisHaveFloors() {
        TechModule scanner = new TechModule(ModuleType.SCANNER_ARRAY, Grade.S, List.of(0.8, 0.4));
        TechBonuses bonuses = TechBonuses.of(List.of(scanner, scanner, scanner));
        assertEquals(TechBonuses.MIN_COOLDOWN, bonuses.scanCooldown(), 1e-9);
        TechModule analyzer = new TechModule(ModuleType.ANALYZER, Grade.S, List.of(0.5));
        assertEquals(TechBonuses.MIN_ANALYSIS, TechBonuses.of(List.of(analyzer, analyzer)).analysisTime(), 1e-9);
        assertEquals(1, TechBonuses.NONE.analysisTime(), 1e-9);
    }

    @Test
    void podsGrowSlotsUpToTheLimits() {
        assertEquals(SuitSlots.TECH_START, SuitSlots.tech(0));
        assertEquals(SuitSlots.CARGO_START, SuitSlots.cargo(0));
        assertEquals(SuitSlots.CARGO_MAX, SuitSlots.cargo(5));
        assertEquals(SuitSlots.TECH_MAX, SuitSlots.tech(8));
        assertEquals(SuitSlots.TECH_MAX, SuitSlots.tech(30));
        assertTrue(SuitSlots.canGrow(7));
        assertFalse(SuitSlots.canGrow(8));
        assertEquals(2, SuitSlots.podCost(0).size());
        assertEquals("universe:plasma_exciter", SuitSlots.podCost(6).get(1).item());
    }

    @Test
    void rarerStarsGiveBetterGrades() {
        assertEquals(0, ModuleLoot.rarity(StarClass.G));
        assertEquals(1, ModuleLoot.rarity(StarClass.WHITE_DWARF));
        assertEquals(2, ModuleLoot.rarity(StarClass.BLACK_HOLE));
        double common = averageGrade(0, 0);
        double exotic = averageGrade(2, 0);
        double boosted = averageGrade(0, 1);
        assertTrue(exotic > common + 0.4, common + " vs " + exotic);
        assertTrue(boosted > common + 0.2, common + " vs " + boosted);
    }

    @Test
    void lootPicksTypesFromTheGivenHost() {
        Rng rng = Rng.of(3);
        Map<ModuleType, Integer> seen = new EnumMap<>(ModuleType.class);
        for (int i = 0; i < 400; i++) {
            TechModule module = ModuleLoot.roll(rng, ModuleType.of(ModuleHost.MULTITOOL), 0, 0);
            assertEquals(ModuleHost.MULTITOOL, module.type().host());
            seen.merge(module.type(), 1, Integer::sum);
        }
        assertEquals(ModuleType.of(ModuleHost.MULTITOOL).size(), seen.size());
    }

    private static double averageGrade(int rarity, int bonus) {
        Rng rng = Rng.of(11);
        double total = 0;
        int n = 4000;
        for (int i = 0; i < n; i++) {
            total += ModuleLoot.grade(rng, rarity, bonus).ordinal();
        }
        return total / n;
    }
}
