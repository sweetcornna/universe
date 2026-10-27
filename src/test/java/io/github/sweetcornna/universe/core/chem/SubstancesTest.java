package io.github.sweetcornna.universe.core.chem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 物品是什么做的（ADR-0036 §一）。 */
class SubstancesTest {
    /** 星球矿石掉的和冶炼出来的（{@code OreKind}）。 */
    private static final List<String> ORES = List.of("minecraft:raw_iron", "minecraft:raw_copper", "minecraft:coal",
            "universe:raw_titanium", "universe:raw_silicon", "universe:sulfur", "universe:fire_ice", "universe:optical_crystal",
            "universe:organic_crystal", "universe:raw_uranium", "universe:salt", "universe:raw_magnetite", "universe:strange_matter",
            "universe:titanium_ingot", "universe:silicon", "universe:uranium_ingot", "universe:magnet");
    /** 现有的流体和化工品。 */
    private static final List<String> CHEMICALS = List.of("universe:water", "universe:crude_oil", "universe:refined_oil",
            "universe:hydrogen", "universe:deuterium", "universe:sulfuric_acid", "universe:oxygen", "universe:sodium", "universe:plastic",
            "universe:energetic_graphite", "universe:graphene", "universe:carbon_nanotube", "universe:crystal_silicon", "minecraft:glass",
            "universe:steel", "universe:titanium_alloy", "universe:synthetic_diamond", "universe:titanium_glass");
    /** M20 新加的。 */
    private static final List<String> M20 = List.of("universe:nitrogen", "universe:carbon_dioxide", "universe:carbon_monoxide",
            "universe:methane", "universe:argon", "universe:helium", "universe:sulfur_dioxide", "universe:ammonia", "universe:chlorine",
            "universe:sodium_hydroxide", "universe:nitric_acid", "universe:ammonium_nitrate", "universe:titanium_tetrachloride",
            "universe:vanadium_slag", "universe:copper_leach_solution", "universe:gold_leach_solution", "universe:nickel_ingot",
            "universe:methalox_fuel", "universe:iron_catalyst", "universe:vanadium_catalyst", "universe:nickel_catalyst", "universe:agar",
            "universe:culture_medium", "universe:editing_kit", "universe:algal_biomass");

    @Test
    void everyListedItemHasAFormula() {
        for (List<String> group : List.of(ORES, CHEMICALS, M20)) {
            for (String item : group) {
                Substance substance = Substances.of(item).orElseThrow(() -> new AssertionError("缺 " + item));
                assertFalse(substance.formula().isBlank(), item);
                assertFalse(substance.nameZh().isBlank(), item);
                assertFalse(substance.nameEn().isBlank(), item);
            }
        }
    }

    @Test
    void oresFollowTheMineralTable() {
        assertEquals("FeTiO₃", Substances.of("universe:raw_titanium").orElseThrow().formula());
        assertEquals("钛铁矿", Substances.of("universe:raw_titanium").orElseThrow().nameZh());
        assertEquals("Fe₂O₃", Substances.of("minecraft:raw_iron").orElseThrow().formula());
        assertEquals("CuFeS₂", Substances.of("minecraft:raw_copper").orElseThrow().formula());
        assertEquals("C₂₄H₁₂", Substances.of("universe:organic_crystal").orElseThrow().formula());
        assertEquals("CH₄·5.75H₂O", Substances.of("universe:fire_ice").orElseThrow().formula());
        assertEquals("NaCl", Substances.of("universe:salt").orElseThrow().formula());
    }

    @Test
    void fictionalThingsAreQuestionMarks() {
        for (String item : List.of("universe:strange_matter", "universe:casimir_crystal", "universe:titanium_crystal")) {
            Substance substance = Substances.of(item).orElseThrow();
            assertEquals(Substance.UNKNOWN_FORMULA, substance.formula());
            assertFalse(substance.known());
        }
        for (Substance substance : Substances.all()) {
            assertEquals(substance.kind() == Substance.Kind.UNKNOWN, substance.formula().equals(Substance.UNKNOWN_FORMULA),
                    substance.item());
        }
    }

    @Test
    void gasesComeFromTheEnum() {
        for (Gas gas : Gas.values()) {
            Substance substance = Substances.of(gas);
            assertEquals(gas.formula(), substance.formula());
            assertEquals("universe:" + gas.item(), substance.item());
            assertTrue(substance.known());
        }
    }

    @Test
    void componentsAreNotSubstances() {
        assertTrue(Substances.of("universe:circuit_board").isEmpty());
        assertTrue(Substances.of("universe:sequencing_chip").isEmpty());
    }

    @Test
    void formulasUseSubscriptDigits() {
        // 化学式里的数字都写成下标；水合物的系数、合金牌号、配平的系数和蛋白质名例外
        List<String> exceptions = List.of("universe:fire_ice", "universe:titanium_alloy", "universe:methalox_fuel", "universe:editing_kit");
        for (Substance substance : Substances.all()) {
            if (!exceptions.contains(substance.item())) {
                assertFalse(substance.formula().chars().anyMatch(c -> c >= '0' && c <= '9'), substance.item() + " " + substance.formula());
            }
        }
    }
}
