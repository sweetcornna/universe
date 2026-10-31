package io.github.sweetcornna.universe.terraform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.survival.Environment;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.core.terraform.TerraformState;

/** 星球改造适配层里不碰世界的那几样：危害的辐射系数、阶段提示挑哪一句、门槛的写法。 */
class TerraformAdapterTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(43, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    private static List<Planet> bodies() {
        List<Planet> list = new ArrayList<>();
        for (StarSystem system : GALAXY.systems()) {
            list.addAll(system.bodies());
        }
        return list;
    }

    /** 一颗有这些危害、能改造的星球（危害照它本来的，换成给的这几种）。 */
    private static Planet planet(List<Hazard> hazards) {
        Planet p = bodies().stream().filter(Terraform::terraformable).findFirst().orElseThrow();
        return new Planet(p.id(), p.seed(), p.name(), p.type(), p.orbit(), p.radius(), p.gravity(), p.atmosphere(), p.temperature(),
                p.temperatureSwing(), hazards, p.liquid(), p.dayCycle(), p.weather(), p.terrain(), p.ecology(), p.palette(), p.resources(),
                p.worldSize(), p.rings(), p.moons());
    }

    private static EffectiveEnvironment environment(Planet planet, double radiationScale) {
        return new EffectiveEnvironment(planet, 1000, Map.of(), AtmosphereMix.EMPTY, 0,
                planet.temperature(), radiationScale, false, 0);
    }

    @Test
    void untouchedHazardsAreTheSameMap() {
        for (Planet planet : bodies()) {
            if (!planet.landable()) {
                continue;
            }
            EffectiveEnvironment environment = Terraform.effective(GALAXY, planet, TerraformState.EMPTY);
            assertSame(planet, environment.planet(), planet.id().path());
            assertEquals(Environment.hazardsOf(planet), TerraformRules.hazards(environment), planet.id().path());
        }
    }

    @Test
    void compactStarHalvesRadiation() {
        Planet planet = planet(List.of(Hazard.RADIATION, Hazard.COLD));
        Map<Hazard, Double> full = Environment.hazardsOf(planet);
        Map<Hazard, Double> halved = TerraformRules.hazards(environment(planet, 0.5));
        assertEquals(full.get(Hazard.RADIATION) * 0.5, halved.get(Hazard.RADIATION), 1e-12);
        assertEquals(full.get(Hazard.COLD), halved.get(Hazard.COLD));
        assertEquals(full, TerraformRules.hazards(environment(planet, 1)));
    }

    @Test
    void stageLinesNameTheHazardThatWentAway() {
        Planet cold = planet(List.of(Hazard.COLD, Hazard.RADIATION, Hazard.TOXIC));
        Planet hot = planet(List.of(Hazard.HEAT));
        Planet mild = planet(List.of());
        assertEquals("cold_gone", TerraformRules.stageKey(cold, Terraform.Stage.TEMPERATE, false));
        assertEquals("heat_gone", TerraformRules.stageKey(hot, Terraform.Stage.TEMPERATE, false));
        assertEquals("temperate", TerraformRules.stageKey(mild, Terraform.Stage.TEMPERATE, false));
        assertEquals("toxic_gone", TerraformRules.stageKey(cold, Terraform.Stage.CLEAN_AIR, false));
        assertEquals("clean_air", TerraformRules.stageKey(mild, Terraform.Stage.CLEAN_AIR, false));
        assertEquals("radiation_gone", TerraformRules.stageKey(cold, Terraform.Stage.SHIELDED, false));
        assertEquals("radiation_halved", TerraformRules.stageKey(cold, Terraform.Stage.SHIELDED, true));
        assertEquals("shielded", TerraformRules.stageKey(mild, Terraform.Stage.SHIELDED, true));
        assertEquals("breathable", TerraformRules.stageKey(mild, Terraform.Stage.BREATHABLE, false));
    }

    @Test
    void thresholdText() {
        assertEquals("253–313 K", ThresholdText.range(threshold(Terraform.Threshold.Kind.TEMPERATURE, 210.4, Terraform.TEMPERATE_MIN,
                Terraform.TEMPERATE_MAX)));
        assertEquals("210 K", ThresholdText.value(threshold(Terraform.Threshold.Kind.TEMPERATURE, 210.4, 0, 0)));
        assertEquals("≤ 1", ThresholdText.range(threshold(Terraform.Threshold.Kind.TOXIC_GASES, 0.32, 0, Terraform.TOXIC_LIMIT)));
        assertEquals("0.32", ThresholdText.value(threshold(Terraform.Threshold.Kind.TOXIC_GASES, 0.32, 0, Terraform.TOXIC_LIMIT)));
        assertEquals("≥ 1.0×", ThresholdText.range(threshold(Terraform.Threshold.Kind.PRESSURE, 0.25, Terraform.SHIELD_COLUMN,
                Double.POSITIVE_INFINITY)));
        assertEquals("1.38×", ThresholdText.value(threshold(Terraform.Threshold.Kind.PRESSURE, 1.375, 0, 0)));
        assertEquals("0.5–3 atm", ThresholdText.range(threshold(Terraform.Threshold.Kind.BREATHING_PRESSURE, 0.1,
                Terraform.BREATHE_MIN_PRESSURE, Terraform.BREATHE_MAX_PRESSURE)));
        assertEquals("0.0060 atm", ThresholdText.value(threshold(Terraform.Threshold.Kind.BREATHING_PRESSURE, 0.006, 0, 0)));
        assertEquals("16–50 kPa", ThresholdText.range(threshold(Terraform.Threshold.Kind.OXYGEN, 0, Terraform.OXYGEN_MIN_KPA,
                Terraform.OXYGEN_MAX_KPA)));
        assertEquals("≤ 5000 ppm", ThresholdText.range(threshold(Terraform.Threshold.Kind.CARBON_DIOXIDE, 0, 0,
                Terraform.CARBON_DIOXIDE_LIMIT)));
        assertEquals("95,000 ppm", ThresholdText.value(threshold(Terraform.Threshold.Kind.CARBON_DIOXIDE, 95_000, 0, 0)));
    }

    private static Terraform.Threshold threshold(Terraform.Threshold.Kind kind, double value, double min, double max) {
        return new Terraform.Threshold(kind, value, min, max, false);
    }

    @Test
    void irrigationAndGreeningRadius() {
        assertEquals(0, TerraformRules.irrigation(0));
        assertEquals(0.25, TerraformRules.irrigation(1), 1e-12);
        assertEquals(1, TerraformRules.irrigation(4), 1e-12);
        assertEquals(1, TerraformRules.irrigation(9), 1e-12);
        assertEquals(0, TerraformRules.greeningRadius(0));
        assertEquals(TerraformRules.GREENING_MIN_RADIUS, TerraformRules.greeningRadius(1e-9));
        assertEquals(5, TerraformRules.greeningRadius(0.001));
        assertEquals(18, TerraformRules.greeningRadius(0.0625));
        assertEquals(TerraformRules.GREENING_MAX_RADIUS, TerraformRules.greeningRadius(0.25));
        assertEquals(TerraformRules.GREENING_MAX_RADIUS, TerraformRules.greeningRadius(1));
    }
}
