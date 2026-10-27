package io.github.sweetcornna.universe.core.chem.reaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** 反应釜的五条反应（ADR-0036 §四）：配平、平衡移动的方向、产量最高的温度、催化剂和用电。 */
class ReactionTest {
    /** 母星的平均气温。 */
    private static final double AMBIENT = 288;

    @Test
    void reactionsAreBalancedByElement() {
        for (Reaction reaction : Reaction.values()) {
            if (reaction == Reaction.FISCHER_TROPSCH) {
                continue;
            }
            Map<Character, Double> left = new HashMap<>();
            Map<Character, Double> right = new HashMap<>();
            reaction.reactants().forEach(term -> parse(term.species().formula(), term.moles(), left));
            reaction.products().forEach(term -> parse(term.species().formula(), term.moles(), right));
            assertEquals(left, right, reaction.name());
        }
    }

    /** 物品配方按摩尔比：同样按元素配平（费托合成一个精炼油算两节 CH₂，接触法连吸收一起算）。 */
    @Test
    void itemRecipesAreBalanced() {
        Map<String, String> formula = new HashMap<>();
        formula.put("universe:nitrogen", "N2");
        formula.put("universe:hydrogen", "H2");
        formula.put("universe:oxygen", "O2");
        formula.put("universe:ammonia", "NH3");
        formula.put("universe:sulfur_dioxide", "SO2");
        formula.put("universe:carbon_dioxide", "CO2");
        formula.put("universe:carbon_monoxide", "CO");
        formula.put("universe:methane", "CH4");
        formula.put("universe:water", "H2O");
        formula.put("universe:sulfuric_acid", "H2SO4");
        formula.put("universe:refined_oil", "C2H4");
        for (Reaction reaction : Reaction.values()) {
            Map<Character, Double> left = new HashMap<>();
            Map<Character, Double> right = new HashMap<>();
            reaction.inputs().forEach(item -> parse(formula.get(item.item()), item.count(), left));
            reaction.outputs().forEach(item -> parse(formula.get(item.item()), item.count(), right));
            assertEquals(left, right, reaction.name());
            // 副产物的碳和主产物的碳一样多：选择性只是在两者之间分碳
            if (reaction.byproduct() != null) {
                Map<Character, Double> main = new HashMap<>();
                Map<Character, Double> side = new HashMap<>();
                parse(formula.get(reaction.mainProduct().item()), reaction.mainProduct().count(), main);
                parse(formula.get(reaction.byproduct().item()), reaction.byproduct().count(), side);
                assertEquals(main.get('C'), side.get('C'), reaction.name());
            }
        }
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
            double best = ReactorModel.bestTemperature(reaction, reaction.defaults().pressure());
            assertTrue(best >= reaction.industrialMin() && best <= reaction.industrialMax(),
                    reaction.name() + " best at " + best + " K");
            // 教科书的工况也在这一段里
            double t = reaction.defaults().temperature();
            assertTrue(t >= reaction.industrialMin() && t <= reaction.industrialMax(), reaction.name());
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
            ReactorResult with = ReactorModel.evaluate(reaction, settings, true, AMBIENT);
            ReactorResult without = ReactorModel.evaluate(reaction, settings, false, AMBIENT);
            assertTrue(without.productPerMinute() < 0.15 * with.productPerMinute(), reaction.name());
            assertTrue(with.productPerMinute() > 2, reaction.name() + " " + with.productPerMinute());
            assertTrue(with.catalyzed());
            assertFalse(without.catalyzed());
        }
    }

    @Test
    void fischerTropschMakesMoreMethaneWhenHot() {
        Reaction ft = Reaction.FISCHER_TROPSCH;
        assertTrue(ft.methaneSelectivity(500) < 0.06);
        assertTrue(ft.methaneSelectivity(700) > 0.45);
        ReactorResult cool = ReactorModel.evaluate(ft, new ReactorSettings(548, 25), true, AMBIENT);
        ReactorResult hot = ReactorModel.evaluate(ft, new ReactorSettings(700, 25), true, AMBIENT);
        assertTrue(hot.byproductPerMinute() > cool.byproductPerMinute());
        assertTrue(hot.productPerMinute() < cool.productPerMinute());
        assertNotNull(ft.byproduct());
        assertNull(Reaction.HABER.byproduct());
        assertEquals(0, ReactorModel.evaluate(Reaction.HABER, Reaction.HABER.defaults(), true, AMBIENT).byproductPerMinute());
        assertEquals(0, Reaction.HABER.methaneSelectivity(900));
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
        assertEquals(60_037, ReactorModel.reactionHeat(smr), 1);
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

    /** Shomate 方程在 298.15 K 还原出标准反应焓和反应熵。 */
    @Test
    void shomateReproducesStandardValues() {
        for (Reaction reaction : List.of(Reaction.HABER, Reaction.CONTACT, Reaction.SABATIER, Reaction.STEAM_REFORMING)) {
            assertEquals(reaction.enthalpy() / 1000, reaction.enthalpyAt(298.15) / 1000, 0.6, reaction.name());
            assertEquals(reaction.entropy(), reaction.entropyAt(298.15), 0.7, reaction.name());
        }
        // 费托合成没有烃链的数据，用 298 K 的值
        assertEquals(Reaction.FISCHER_TROPSCH.enthalpy(), Reaction.FISCHER_TROPSCH.enthalpyAt(500));
    }

    @Test
    void catalystsMatchReactionsAndSinter() {
        assertTrue(Catalyst.IRON.accepts(Reaction.HABER));
        assertTrue(Catalyst.IRON.accepts(Reaction.FISCHER_TROPSCH));
        assertTrue(Catalyst.VANADIUM_PENTOXIDE.accepts(Reaction.CONTACT));
        assertTrue(Catalyst.NICKEL.accepts(Reaction.SABATIER));
        assertTrue(Catalyst.NICKEL.accepts(Reaction.STEAM_REFORMING));
        assertFalse(Catalyst.NICKEL.accepts(Reaction.HABER));
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
        assertEquals(new YieldGrid.Cell(9, 7), YieldGrid.cell(new ReactorSettings(1200, 300)));
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
            assertTrue(species.item() == null ? species == Species.SULFUR_TRIOXIDE : species.item().startsWith("universe:"));
        }
    }

    private static double rate(Reaction reaction, double temperature, double pressure) {
        return ReactorModel.singlePass(reaction, temperature, pressure, true) * (1 - reaction.methaneSelectivity(temperature));
    }

    /** 数元素，只认这里用到的写法：单字母元素加下标数字（Unicode 或 ASCII），括号和下标 n 当成一节。 */
    private static void parse(String formula, double moles, Map<Character, Double> atoms) {
        String f = formula.replace("₂", "2").replace("₃", "3").replace("₄", "4");
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (!Character.isUpperCase(c)) {
                continue;
            }
            int k = i + 1;
            while (k < f.length() && Character.isDigit(f.charAt(k))) {
                k++;
            }
            int n = k > i + 1 ? Integer.parseInt(f.substring(i + 1, k)) : 1;
            atoms.merge(c, moles * n, Double::sum);
        }
    }
}
