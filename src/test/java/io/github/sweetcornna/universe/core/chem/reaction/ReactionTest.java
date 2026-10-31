package io.github.sweetcornna.universe.core.chem.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sweetcornna.universe.core.chem.Formula;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/** 反应釜的反应（ADR-0036 §四、ADR-0037）：配平、认反应、平衡移动的方向、产量最高的温度、催化剂和用电。 */
class ReactionTest {
    /** 母星的平均气温。 */
    private static final double AMBIENT = 288;

    /** 反应式按化学式查原子守恒（ADR-0037 §二），物品数就是系数。 */
    @Test
    void reactionsAreBalancedByElement() {
        for (Reaction reaction : Reaction.values()) {
            Map<String, Double> left = new TreeMap<>();
            Map<String, Double> right = new TreeMap<>();
            reaction.reactants().forEach(term -> Formula.add(left, term.species().formula(), term.moles()));
            reaction.products().forEach(term -> Formula.add(right, term.species().formula(), term.moles()));
            assertEquals(left, right, reaction.name());
            for (int i = 0; i < reaction.reactants().size(); i++) {
                Term term = reaction.reactants().get(i);
                assertEquals(new ItemCount(term.species().item(), (int) term.moles()), reaction.inputs().get(i), reaction.name());
                assertEquals(term.moles(), Math.rint(term.moles()), reaction.name());
            }
            assertEquals(reaction.products().size(), reaction.outputs().size(), reaction.name());
        }
    }

    /** 反应式由系数生成，系数 1 不写。 */
    @Test
    void equationsComeFromTerms() {
        assertEquals("N₂ + 3H₂ ⇄ 2NH₃", Reaction.HABER.equation());
        assertEquals("2SO₂ + O₂ ⇄ 2SO₃", Reaction.CONTACT.equation());
        assertEquals("2CO + 4H₂ → (CH₂)₂ + 2H₂O", Reaction.FISCHER_TROPSCH.equation());
        assertEquals("3NO₂ + H₂O → 2HNO₃ + NO", Reaction.NO2_ABSORPTION.equation());
    }

    /** 每条反应的（反应物种类，催化剂）组合唯一，原料格、产物格放得下（各 3 格）。 */
    @Test
    void reactantSetAndCatalystAreUnique() {
        Set<String> keys = new HashSet<>();
        for (Reaction reaction : Reaction.values()) {
            assertTrue(keys.add(reaction.reactantItems() + "/" + reaction.catalyst()), reaction.name());
            assertTrue(reaction.reactants().size() <= 3, reaction.name());
            assertTrue(reaction.products().size() <= 3, reaction.name());
            assertEquals(reaction.reactants().size(), reaction.reactantItems().size(), reaction.name());
        }
    }

    @Test
    void matcherRecognisesReactionsByItemsAndCatalyst() {
        for (Reaction reaction : Reaction.values()) {
            ReactionMatcher.Match match = ReactionMatcher.match(reaction.reactantItems(), reaction.catalyst());
            assertEquals(reaction, match.reaction(), reaction.name());
        }
        // 只有一条的，不放催化剂也认得，只是慢
        assertEquals(Reaction.HABER, ReactionMatcher.match(List.of("universe:hydrogen", "universe:nitrogen", "universe:hydrogen"), null).reaction());
        assertFalse(ReactionMatcher.catalyzed(Reaction.HABER, null));
        assertFalse(ReactionMatcher.catalyzed(Reaction.HABER, Catalyst.NICKEL));
        assertTrue(ReactionMatcher.catalyzed(Reaction.HYDROGEN_COMBUSTION, null));
        // 一氧化碳加氢：铁上费托合成、镍上甲烷化、铜锌上甲醇
        List<String> syngas = List.of("universe:carbon_monoxide", "universe:hydrogen");
        assertEquals(Reaction.FISCHER_TROPSCH, ReactionMatcher.match(syngas, Catalyst.IRON).reaction());
        assertEquals(Reaction.CO_METHANATION, ReactionMatcher.match(syngas, Catalyst.NICKEL).reaction());
        assertEquals(Reaction.METHANOL_SYNTHESIS, ReactionMatcher.match(syngas, Catalyst.COPPER_ZINC).reaction());
        ReactionMatcher.Match none = ReactionMatcher.match(syngas, null);
        assertTrue(none.ambiguous());
        assertEquals(3, none.candidates());
        assertFalse(ReactionMatcher.match(syngas, Catalyst.ZEOLITE).found());
        // 认不出
        ReactionMatcher.Match unknown = ReactionMatcher.match(List.of("universe:nitrogen"), null);
        assertFalse(unknown.found());
        assertFalse(unknown.ambiguous());
        assertEquals(0, unknown.candidates());
        assertFalse(ReactionMatcher.match(List.of(), null).found());
    }

    @Test
    void speciesHaveThermoDataAndItems() {
        for (Species species : Species.values()) {
            assertTrue(SpeciesThermo.covers(species), species.name());
            assertEquals(species, Species.byItem(species.item()), species.name());
            Formula.atoms(species.formula());
        }
        assertNull(Species.byItem("minecraft:stone"));
        assertEquals(Catalyst.NICKEL, Catalyst.byItem("universe:nickel_catalyst"));
        assertNull(Catalyst.byItem("minecraft:stone"));
    }

    @Test
    void formulaCountsAtoms() {
        assertEquals(Map.of("C", 1.0, "H", 4.0, "N", 2.0, "O", 1.0), Formula.atoms("CO(NH₂)₂"));
        assertEquals(Map.of("C", 2.0, "H", 4.0), Formula.atoms("(C₂H₄)ₙ"));
        assertEquals(Map.of("Ca", 5.0, "H", 1.0, "O", 13.0, "P", 3.0), Formula.atoms("Ca₅(PO₄)₃(OH)"));
        assertEquals(Map.of("C", 1.0, "H", 15.5, "O", 5.75), Formula.atoms("CH₄·5.75H₂O"));
        assertEquals(Map.of("Be", 3.0, "Al", 2.0, "Si", 6.0, "O", 18.0), Formula.atoms("Be₃Al₂Si₆O₁₈"));
    }

    @Test
    void pressureShiftsEquilibriumByGasMoles() {
        assertTrue(Thermo.equilibriumConversion(Reaction.HABER, 723, 200) > Thermo.equilibriumConversion(Reaction.HABER, 723, 20));
        assertTrue(Thermo.equilibriumConversion(Reaction.CONTACT, 850, 10) > Thermo.equilibriumConversion(Reaction.CONTACT, 850, 1));
        assertTrue(Thermo.equilibriumConversion(Reaction.SABATIER, 800, 20) > Thermo.equilibriumConversion(Reaction.SABATIER, 800, 1));
        assertTrue(Thermo.equilibriumConversion(Reaction.STEAM_REFORMING, 1123, 5)
                > Thermo.equilibriumConversion(Reaction.STEAM_REFORMING, 1123, 50));
    }

    @Test
    void temperatureShiftsEquilibriumByEnthalpy() {
        for (Reaction reaction : new Reaction[] {Reaction.HABER, Reaction.CONTACT, Reaction.SABATIER}) {
            double p = reaction.defaults().pressure();
            assertTrue(Thermo.equilibriumConversion(reaction, 600, p) > Thermo.equilibriumConversion(reaction, 900, p), reaction.name());
        }
        assertTrue(Thermo.equilibriumConversion(Reaction.STEAM_REFORMING, 1100, 25)
                > Thermo.equilibriumConversion(Reaction.STEAM_REFORMING, 800, 25));
        assertEquals(1, Thermo.equilibriumConversion(Reaction.FISCHER_TROPSCH, 500, 25));
    }

    /**
     * 手册（Larson 和 Dodge 1923 测的平衡氨含量）：450 °C 时 100 atm 16.4%、300 atm 35.5%，500 °C、300 atm 26.4%。
     * 按理想气体算，高压下少几个百分点。
     */
    @Test
    void equilibriumMatchesHandbooks() {
        double atm = 1.01325;
        assertEquals(0.164, Thermo.equilibriumFraction(Reaction.HABER, Species.AMMONIA, 723.15, 100 * atm), 0.01);
        assertEquals(0.355, Thermo.equilibriumFraction(Reaction.HABER, Species.AMMONIA, 723.15, 300 * atm), 0.045);
        assertEquals(0.264, Thermo.equilibriumFraction(Reaction.HABER, Species.AMMONIA, 773.15, 300 * atm), 0.04);
        // 450 °C 的 Kp（½N₂ + 3/2H₂ ⇌ NH₃）手册约 6.5 × 10⁻³ atm⁻¹
        double kp = Math.sqrt(Thermo.equilibriumConstant(Reaction.HABER, 723.15)) * atm;
        assertEquals(6.5e-3, kp, 1.0e-3);
        // 常压下几乎不出氨
        assertTrue(Thermo.equilibriumFraction(Reaction.HABER, Species.AMMONIA, 723.15, 1) < 0.01);
        // 接触法 430 °C、1.5 bar 时九成以上的二氧化硫转化
        assertTrue(Thermo.equilibriumConversion(Reaction.CONTACT, 703, 1.5) > 0.9);
        // 萨巴蒂尔 350 °C 常压九成上下
        assertTrue(Thermo.equilibriumConversion(Reaction.SABATIER, 623, 1) > 0.85);
        // 常温下重整几乎不走
        assertTrue(Thermo.equilibriumConversion(Reaction.STEAM_REFORMING, 500, 25) < 0.01);
    }

    @Test
    void bestTemperatureFallsInIndustrialWindow() {
        for (Reaction reaction : Reaction.values()) {
            double p = reaction.defaults().pressure();
            double best = ReactorModel.bestTemperature(reaction, p);
            assertTrue(best >= reaction.industrialMin() && best <= reaction.industrialMax(),
                    reaction.name() + " best at " + best + " K");
            // 教科书的工况也在这一段里，产量不比最好的差太多
            double t = reaction.defaults().temperature();
            assertTrue(t >= reaction.industrialMin() && t <= reaction.industrialMax(), reaction.name());
            assertTrue(rate(reaction, t, p) > 0.7 * rate(reaction, best, p), reaction.name());
        }
    }

    /**
     * 放热反应太冷太热都慢：最佳温度往下 150 K 慢得多（反应走不动），往上 150 K 也少（平衡变差、费托出甲烷）。
     * 接触法和萨巴蒂尔的平衡常数大，往上降得慢。
     */
    @Test
    void exothermicReactionsHaveACompromise() {
        for (Reaction reaction : new Reaction[] {Reaction.HABER, Reaction.CONTACT, Reaction.SABATIER, Reaction.FISCHER_TROPSCH}) {
            double p = reaction.defaults().pressure();
            double best = ReactorModel.bestTemperature(reaction, p);
            double peak = rate(reaction, best, p);
            assertTrue(rate(reaction, best - 150, p) < 0.5 * peak, reaction.name() + " cold");
            assertTrue(rate(reaction, best + 150, p) < 0.95 * peak, reaction.name() + " hot");
        }
        // 合成氨和费托合成往上掉一半上下
        assertTrue(rate(Reaction.HABER, 873, 200) < 0.7 * rate(Reaction.HABER, 723, 200));
        assertTrue(rate(Reaction.FISCHER_TROPSCH, 700, 25) < 0.7 * rate(Reaction.FISCHER_TROPSCH, 548, 25));
    }

    @Test
    void withoutCatalystReactionsBarelyRun() {
        for (Reaction reaction : Reaction.values()) {
            ReactorSettings settings = reaction.defaults();
            if (reaction.catalyst() == null) {
                // 燃烧、吸收、中和、热解不用催化剂
                ReactorResult bare = ReactorModel.evaluate(reaction, settings, false, AMBIENT);
                assertTrue(bare.catalyzed(), reaction.name());
                assertTrue(bare.productPerMinute() > 2, reaction.name());
                continue;
            }
            ReactorResult with = ReactorModel.evaluate(reaction, settings, true, AMBIENT);
            ReactorResult without = ReactorModel.evaluate(reaction, settings, false, AMBIENT);
            assertTrue(without.productPerMinute() < 0.15 * with.productPerMinute(), reaction.name());
            assertTrue(with.productPerMinute() > 2, reaction.name() + " " + with.productPerMinute());
            assertTrue(with.catalyzed());
            assertFalse(without.catalyzed());
        }
    }

    /** 费托合成高于 350 °C 积碳、出甲烷，长链烃的产量掉下来；尿素 210 °C 往上出缩二脲。 */
    @Test
    void limitTemperatureSlowsReactions() {
        assertTrue(rate(Reaction.FISCHER_TROPSCH, 723, 25) < 0.2 * rate(Reaction.FISCHER_TROPSCH, 600, 25));
        assertEquals(1, Reaction.HABER.limitFactor(1500));
        assertEquals(1, Reaction.UREA_SYNTHESIS.limitFactor(483));
        assertEquals(Math.exp(-1), Reaction.UREA_SYNTHESIS.limitFactor(483 + Reaction.LIMIT_WIDTH), 1e-12);
    }

    @Test
    void powerRisesWithPressureAndTemperature() {
        for (Reaction reaction : Reaction.values()) {
            double low = ReactorModel.power(reaction, new ReactorSettings(600, 2), 10, AMBIENT);
            assertTrue(ReactorModel.power(reaction, new ReactorSettings(600, 200), 10, AMBIENT) > low);
            assertTrue(ReactorModel.power(reaction, new ReactorSettings(900, 2), 10, AMBIENT) > low);
        }
        // 寒冷星球上保温更费电
        ReactorSettings haber = Reaction.HABER.defaults();
        assertTrue(ReactorModel.power(Reaction.HABER, haber, 10, 120) > ReactorModel.power(Reaction.HABER, haber, 10, AMBIENT));
        assertEquals(ReactorModel.BASE_POWER, ReactorModel.power(Reaction.HABER, new ReactorSettings(300, 1), 10, 300), 1e-9);
    }

    /** 重整每批至少要它的反应热；放热反应不发电。 */
    @Test
    void endothermicReformingPaysItsReactionHeat() {
        Reaction smr = Reaction.STEAM_REFORMING;
        assertEquals(206.1 * ReactorModel.JOULES_PER_KJ_MOL, ReactorModel.reactionHeat(smr), 50);
        ReactorSettings settings = smr.defaults();
        // 850 °C 下的反应焓按热容修正，比 298 K 的 206 kJ 多一成
        assertEquals(226.8, smr.enthalpyAt(settings.temperature()) / 1000, 0.5);
        double idle = ReactorModel.power(smr, settings, 0, AMBIENT);
        double busy = ReactorModel.power(smr, settings, 12, AMBIENT);
        assertEquals(12 * ReactorModel.reactionHeat(smr, settings.temperature()) / 1200, busy - idle, 1e-6);
        Reaction haber = Reaction.HABER;
        assertEquals(ReactorModel.power(haber, haber.defaults(), 0, AMBIENT), ReactorModel.power(haber, haber.defaults(), 30, AMBIENT));
        assertTrue(ReactorModel.reactionHeat(haber) < 0);
    }

    /** 重整做出的氢和一氧化碳烧掉拿回的电，不多过甲烷的燃料值加上反应釜的用电（低热值 802、283、242 kJ/mol）。 */
    @Test
    void reformingCreatesNoEnergy() {
        double k = ReactorModel.JOULES_PER_KJ_MOL;
        double methane = 802 * k;
        double products = 283 * k + 3 * 242 * k;
        ReactorResult result = ReactorModel.evaluate(Reaction.STEAM_REFORMING, Reaction.STEAM_REFORMING.defaults(), true, AMBIENT);
        double perBatch = result.power() * 1200 / result.batchesPerMinute();
        assertTrue(products <= methane + perBatch, products + " > " + (methane + perBatch));
        // 一个氢在火力发电厂里烧 4700 刻 × 15 J，和它的低热值对得上
        assertEquals(4700 * 15, 242 * k, 100);
    }

    /** 298 K 的反应焓、反应熵和教科书（NIST 生成焓、标准熵）对得上，单位 kJ/mol、J/(mol·K)。 */
    @Test
    void standardValuesMatchTextbooks() {
        assertEquals(-91.8, Reaction.HABER.enthalpy() / 1000, 0.3);
        assertEquals(-198.1, Reaction.HABER.entropy(), 1.0);
        assertEquals(-197.8, Reaction.CONTACT.enthalpy() / 1000, 0.5);
        assertEquals(-165.0, Reaction.SABATIER.enthalpy() / 1000, 0.5);
        assertEquals(206.1, Reaction.STEAM_REFORMING.enthalpy() / 1000, 0.5);
        assertEquals(-41.2, Reaction.WATER_GAS_SHIFT.enthalpy() / 1000, 0.5);
        assertEquals(-90.5, Reaction.METHANOL_SYNTHESIS.enthalpy() / 1000, 0.5);
        assertEquals(-906.2, Reaction.AMMONIA_OXIDATION.enthalpy() / 1000, 1.0);
        assertEquals(-114.1, Reaction.NO_OXIDATION.enthalpy() / 1000, 0.5);
        assertEquals(172.5, -Reaction.BOUDOUARD.enthalpy() / 1000, 0.5);
        // 没有 Shomate 拟合的按常数热容外推，500 K 时差不到 15 kJ
        assertEquals(-313.8, Reaction.FISCHER_TROPSCH.enthalpy() / 1000, 0.5);
        assertTrue(Math.abs(Reaction.FISCHER_TROPSCH.enthalpyAt(500) - Reaction.FISCHER_TROPSCH.enthalpy()) < 15_000);
    }

    @Test
    void catalystsMatchReactionsAndSinter() {
        assertTrue(Catalyst.IRON.accepts(Reaction.HABER));
        assertTrue(Catalyst.IRON.accepts(Reaction.FISCHER_TROPSCH));
        assertTrue(Catalyst.VANADIUM_PENTOXIDE.accepts(Reaction.CONTACT));
        assertTrue(Catalyst.NICKEL.accepts(Reaction.SABATIER));
        assertTrue(Catalyst.NICKEL.accepts(Reaction.STEAM_REFORMING));
        assertFalse(Catalyst.NICKEL.accepts(Reaction.HABER));
        assertTrue(Catalyst.PLATINUM_RHODIUM.accepts(Reaction.AMMONIA_OXIDATION));
        assertNull(Reaction.NEUTRALIZATION.catalyst());
        assertEquals(1, Catalyst.NICKEL.activity(1000));
        assertTrue(Catalyst.NICKEL.activity(1200) < 0.1);
        for (Catalyst catalyst : Catalyst.values()) {
            assertTrue(catalyst.batches() > 0);
            assertTrue(catalyst.item().startsWith("universe:"));
        }
    }

    @Test
    void settingsAreClamped() {
        ReactorSettings settings = new ReactorSettings(50, 1000);
        assertEquals(ReactorSettings.MIN_TEMPERATURE, settings.temperature());
        assertEquals(ReactorSettings.MAX_PRESSURE, settings.pressure());
        assertEquals(ReactorSettings.MIN_TEMPERATURE, new ReactorSettings(Double.NaN, 5).temperature());
        assertEquals(450, new ReactorSettings(723.15, 200).celsius(), 1e-9);
    }

    @Test
    void gridMapsSettingsToCells() {
        assertEquals(new YieldGrid.Cell(0, 0), YieldGrid.cell(new ReactorSettings(300, 1)));
        assertEquals(new YieldGrid.Cell(13, 7), YieldGrid.cell(new ReactorSettings(1560, 300)));
        // 每格 90 K，原来 10 列的编号不变
        assertEquals(new YieldGrid.Cell(9, 7), YieldGrid.cell(new ReactorSettings(1199, 300)));
        for (int i = 0; i < YieldGrid.CELLS; i++) {
            YieldGrid.Cell cell = YieldGrid.byIndex(i);
            assertEquals(i, cell.index());
            assertEquals(cell, YieldGrid.cell(YieldGrid.center(cell)));
        }
        // 450 °C、200 bar 在第 4 列、第 7 行
        assertEquals(new YieldGrid.Cell(4, 7), YieldGrid.cell(Reaction.HABER.defaults()));
    }

    @Test
    void namesRoundTrip() {
        for (Reaction reaction : Reaction.values()) {
            assertEquals(reaction, Reaction.byName(reaction.name()));
        }
        assertNull(Reaction.byName("NOPE"));
        for (Species species : Species.values()) {
            assertTrue(species.item().startsWith("universe:") || species == Species.COAL, species.name());
        }
    }

    private static double rate(Reaction reaction, double temperature, double pressure) {
        return ReactorModel.singlePass(reaction, temperature, pressure, true);
    }
}
