package io.github.sweetcornna.universe.core.terraform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.AtmosphereMixes;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere.Kind;
import io.github.sweetcornna.universe.core.galaxy.DayCycle;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile.Precipitation;
import io.github.sweetcornna.universe.core.microbe.Gene;

class TerraformTest {
    private static final Galaxy GALAXY = GalaxyGenerator.generate(43, GalaxyGenerator.DEFAULT_SYSTEM_COUNT);

    /** 照火星的稀薄大气。 */
    private static final AtmosphereMix MARTIAN = mix(Gas.CARBON_DIOXIDE, 0.95, Gas.NITROGEN, 0.028, Gas.ARGON, 0.02,
            Gas.OXYGEN, 0.0015, Gas.CARBON_MONOXIDE, 0.0005);

    private static AtmosphereMix mix(Object... pairs) {
        Map<Gas, Double> map = new EnumMap<>(Gas.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Gas) pairs[i], (Double) pairs[i + 1]);
        }
        return AtmosphereMix.of(map);
    }

    private static List<Planet> bodies() {
        List<Planet> list = new ArrayList<>();
        for (StarSystem system : GALAXY.systems()) {
            list.addAll(system.bodies());
        }
        return list;
    }

    private static Planet anyBarren() {
        return bodies().stream().filter(p -> p.type() == PlanetType.BARREN && !p.id().equals(GalaxyGenerator.HOME_PLANET_ID))
                .findFirst().orElseThrow();
    }

    /** ADR 里那颗典型的冷星球：边长 13312、重力 0.4、0.1 个大气压的火星式大气、210 K，有水冰。 */
    private static Planet coldPlanet(List<Hazard> hazards, DayCycle day) {
        Planet p = anyBarren();
        return new Planet(p.id(), p.seed(), p.name(), PlanetType.BARREN, p.orbit(), 0.8, 0.4, new Atmosphere(Kind.THIN, 0.1), 210, 50,
                hazards, Liquid.NONE, day, new WeatherProfile(Precipitation.DUST, 0.2, 0.2, 0.2, false), p.terrain(), p.ecology(),
                p.palette(), List.of(new Resource.Deposit(Resource.WATER_ICE, 0.3)), 13312, false, List.of());
    }

    private static Planet coldPlanet() {
        return coldPlanet(List.of(Hazard.COLD, Hazard.RADIATION), DayCycle.STANDARD);
    }

    private static EffectiveEnvironment effective(Planet planet, TerraformState state) {
        return Terraform.effective(planet, MARTIAN, false, state);
    }

    // ---- 没改造的星球和原来一样

    @Test
    void emptyStateGivesTheSamePlanetAcrossTheGalaxy() {
        for (Planet planet : bodies()) {
            EffectiveEnvironment environment = Terraform.effective(GALAXY, planet, TerraformState.EMPTY);
            assertEquals(planet, environment.planet(), planet.id().toString());
            assertEquals(planet.temperature(), environment.temperature());
            assertEquals(planet.atmosphere().pressure(), environment.pressure());
        }
    }

    @Test
    void aSingleUnitOfGasDoesNotFlipAnythingAcrossTheGalaxy() {
        for (Planet planet : bodies()) {
            if (!Terraform.terraformable(planet)) {
                continue;
            }
            TerraformState state = Terraform.addGas(planet, TerraformState.EMPTY, Gas.NITROGEN, 1);
            Planet changed = Terraform.effective(GALAXY, planet, state).planet();
            String id = planet.id().toString();
            assertEquals(planet.atmosphere().kind(), changed.atmosphere().kind(), id);
            assertEquals(planet.hazards(), changed.hazards(), id);
            assertEquals(planet.weather().precipitation(), changed.weather().precipitation(), id);
            assertEquals(planet.temperature(), changed.temperature(), 0.01, id);
        }
    }

    @Test
    void homeAndGasGiantsAreNotTerraformed() {
        Planet home = GALAXY.homePlanet();
        assertFalse(Terraform.terraformable(home));
        TerraformState state = TerraformState.EMPTY.withDelta(Gas.SULFUR_DIOXIDE, 1e9).withMirrors(20);
        assertEquals(home, Terraform.effective(GALAXY, home, state).planet());
        Planet giant = bodies().stream().filter(p -> p.type() == PlanetType.GAS_GIANT).findFirst().orElseThrow();
        assertFalse(Terraform.terraformable(giant));
        assertEquals(giant, Terraform.effective(GALAXY, giant, state).planet());
    }

    // ---- 容量

    @Test
    void capacityScalesWithAreaAndInverseGravity() {
        Planet planet = coldPlanet();
        double capacity = Terraform.capacity(planet);
        assertEquals(Terraform.UNITS_PER_KM2_PER_G * 13.312 * 13.312 / 0.4, capacity, 1e-6);
        Planet bigger = new Planet(planet.id(), planet.seed(), planet.name(), planet.type(), planet.orbit(), planet.radius(), 0.4,
                planet.atmosphere(), planet.temperature(), planet.temperatureSwing(), planet.hazards(), planet.liquid(), planet.dayCycle(),
                planet.weather(), planet.terrain(), planet.ecology(), planet.palette(), planet.resources(), 26624, false, List.of());
        assertEquals(4 * capacity, Terraform.capacity(bigger), 1e-6);
        Planet heavier = new Planet(planet.id(), planet.seed(), planet.name(), planet.type(), planet.orbit(), planet.radius(), 0.8,
                planet.atmosphere(), planet.temperature(), planet.temperatureSwing(), planet.hazards(), planet.liquid(), planet.dayCycle(),
                planet.weather(), planet.terrain(), planet.ecology(), planet.palette(), planet.resources(), 13312, false, List.of());
        assertEquals(capacity / 2, Terraform.capacity(heavier), 1e-6);
    }

    @Test
    void addedGasRaisesPressureByUnitsOverCapacity() {
        Planet planet = coldPlanet();
        double capacity = Terraform.capacity(planet);
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.NITROGEN, 0.4 * capacity);
        assertEquals(0.5, effective(planet, state).pressure(), 1e-9);
    }

    @Test
    void cannotTakeMoreThanThereIs() {
        Planet planet = coldPlanet();
        double co2 = Terraform.baseUnits(planet, MARTIAN, Gas.CARBON_DIOXIDE);
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.CARBON_DIOXIDE, -10 * co2);
        assertEquals(-co2, state.delta(Gas.CARBON_DIOXIDE), 1e-6);
        assertEquals(0, Terraform.available(planet, MARTIAN, state, Gas.CARBON_DIOXIDE), 1e-6);
        assertEquals(0, effective(planet, state).available(Gas.CARBON_DIOXIDE), 1e-6);
    }

    // ---- 温度

    @Test
    void mirrorsWarmAndShadesCool() {
        Planet planet = coldPlanet();
        assertEquals(210 + 5 * Terraform.MIRROR_KELVIN, effective(planet, TerraformState.EMPTY.withMirrors(5)).temperature(), 1e-9);
        assertEquals(210 - 4 * Terraform.MIRROR_KELVIN, effective(planet, TerraformState.EMPTY.withShades(4)).temperature(), 1e-9);
        assertEquals(210 + Terraform.MAX_MIRRORS * Terraform.MIRROR_KELVIN,
                effective(planet, TerraformState.EMPTY.withMirrors(500)).temperature(), 1e-9);
    }

    @Test
    void greenhouseGasesAndPressureWarmMonotonically() {
        Planet planet = coldPlanet();
        double capacity = Terraform.capacity(planet);
        for (Gas gas : List.of(Gas.CARBON_DIOXIDE, Gas.METHANE, Gas.AMMONIA, Gas.NITROGEN)) {
            double last = planet.temperature();
            for (double atm = 0.01; atm <= 1; atm *= 2) {
                TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, gas, atm * capacity);
                double temperature = effective(planet, state).temperature();
                assertTrue(temperature > last, gas + " at " + atm);
                last = temperature;
            }
        }
        // 同样多，甲烷比二氧化碳暖
        TerraformState co2 = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.CARBON_DIOXIDE, 0.01 * capacity);
        TerraformState ch4 = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.METHANE, 0.01 * capacity);
        assertTrue(effective(planet, ch4).temperature() > effective(planet, co2).temperature());
    }

    @Test
    void removingCarbonDioxideCools() {
        Planet planet = coldPlanet();
        double co2 = Terraform.baseUnits(planet, MARTIAN, Gas.CARBON_DIOXIDE);
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.CARBON_DIOXIDE, -0.9 * co2);
        assertTrue(effective(planet, state).temperature() < planet.temperature());
    }

    // ---- 门槛

    @Test
    void thinPlanetCrossesThresholdsInOrder() {
        Planet planet = coldPlanet();
        double capacity = Terraform.capacity(planet);
        TerraformState state = TerraformState.EMPTY;

        // 一：加氮到一个大气压，挡住辐射，还不能呼吸
        state = Terraform.addGas(planet, MARTIAN, state, Gas.NITROGEN, 0.9 * capacity);
        EffectiveEnvironment shielded = effective(planet, state);
        assertFalse(shielded.hasHazard(Hazard.RADIATION));
        assertEquals(0, shielded.radiationScale());
        assertFalse(shielded.breathable());
        assertTrue(shielded.hasHazard(Hazard.COLD));
        assertTrue(Terraform.reached(shielded).contains(Terraform.Stage.SHIELDED));

        // 二：反射镜加热到温和，寒冷危害没了
        state = state.withMirrors(Terraform.MAX_MIRRORS);
        EffectiveEnvironment warm = effective(planet, state);
        assertTrue(warm.temperature() >= Terraform.TEMPERATE_MIN, "温度 " + warm.temperature());
        assertFalse(warm.hasHazard(Hazard.COLD));
        assertFalse(warm.breathable());
        assertEquals(EnumSet.of(Terraform.Stage.TEMPERATE), Terraform.newlyReached(shielded, warm));

        // 三：二氧化碳换成氧，还差氧分压
        double co2 = Terraform.baseUnits(planet, MARTIAN, Gas.CARBON_DIOXIDE);
        state = Terraform.addGas(planet, MARTIAN, state, Gas.CARBON_DIOXIDE, -0.97 * co2);
        state = Terraform.addGas(planet, MARTIAN, state, Gas.OXYGEN, 0.97 * co2);
        assertFalse(effective(planet, state).breathable());

        // 四：补氧到 21 千帕以上，就差毒气：0.05% 的一氧化碳本来只有 50 ppm 的分压，加气冲不淡
        state = Terraform.addGas(planet, MARTIAN, state, Gas.OXYGEN, 0.15 * capacity);
        EffectiveEnvironment almost = effective(planet, state);
        assertFalse(almost.breathable());
        assertEquals(List.of(Terraform.Threshold.Kind.TOXIC_GASES),
                Terraform.thresholds(almost).stream().filter(t -> !t.met()).map(Terraform.Threshold::kind).toList());

        // 五：有了氧、水和光，一氧化碳被氧化成二氧化碳，现实两个多月（游戏几个小时）就能呼吸了
        state = AtmosphericChemistry.advance(state, planet, MARTIAN, false, (long) (90 * AtmosphericChemistry.TICKS_PER_REAL_DAY));
        EffectiveEnvironment breathable = effective(planet, state);
        assertTrue(breathable.breathable(), Terraform.thresholds(breathable).toString());
        assertEquals(Kind.BREATHABLE, breathable.planet().atmosphere().kind());
        assertTrue(breathable.planet().hazards().isEmpty(), breathable.planet().hazards().toString());
        assertTrue(Terraform.thresholds(breathable).stream().allMatch(Terraform.Threshold::met));
    }

    @Test
    void radiationOnlyHalvesNearCompactStars() {
        Planet planet = coldPlanet();
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.NITROGEN, Terraform.capacity(planet));
        EffectiveEnvironment compact = Terraform.effective(planet, MARTIAN, true, state);
        assertTrue(compact.hasHazard(Hazard.RADIATION));
        assertEquals(0.5, compact.radiationScale());
    }

    @Test
    void sulfurDioxideMakesABreathablePlanetToxic() {
        Planet planet = bodies().stream().filter(p -> Terraform.terraformable(p) && p.atmosphere().breathable()
                && p.type() == PlanetType.HABITABLE).findFirst().orElseThrow();
        AtmosphereMix base = AtmosphereMixes.base(planet);
        double amount = 0.005 * planet.atmosphere().pressure() * Terraform.capacity(planet);
        TerraformState state = Terraform.addGas(planet, base, TerraformState.EMPTY, Gas.SULFUR_DIOXIDE, amount);
        EffectiveEnvironment polluted = Terraform.effective(planet, base, false, state);
        assertEquals(Kind.TOXIC, polluted.planet().atmosphere().kind());
        assertTrue(polluted.hasHazard(Hazard.TOXIC));
        assertFalse(polluted.breathable());
        if (polluted.temperature() >= Terraform.SNOW_BELOW) {
            assertEquals(Precipitation.ACID_RAIN, polluted.planet().weather().precipitation());
        }
        // 抽回去，和原来一样
        state = Terraform.addGas(planet, base, state, Gas.SULFUR_DIOXIDE, -amount);
        assertTrue(state.isEmpty());
        assertEquals(planet, Terraform.effective(planet, base, false, state).planet());
    }

    @Test
    void cleaningAToxicPlanetRemovesTheHazard() {
        Planet planet = bodies().stream().filter(p -> Terraform.terraformable(p) && p.type() == PlanetType.TOXIC
                && p.hasHazard(Hazard.TOXIC)).findFirst().orElseThrow();
        AtmosphereMix base = AtmosphereMixes.base(planet);
        assertTrue(base.exposureIndex(planet.atmosphere().pressure()) > Terraform.TOXIC_LIMIT);
        TerraformState state = TerraformState.EMPTY;
        for (Gas gas : Gas.values()) {
            if (gas.toxic()) {
                state = Terraform.addGas(planet, base, state, gas, -Terraform.baseUnits(planet, base, gas));
            }
        }
        EffectiveEnvironment clean = Terraform.effective(planet, base, false, state);
        assertFalse(clean.hasHazard(Hazard.TOXIC));
        assertTrue(Terraform.reached(clean).contains(Terraform.Stage.CLEAN_AIR));
        assertFalse(clean.planet().atmosphere().kind() == Kind.TOXIC);
    }

    @Test
    void airlessPlanetGetsAnAtmosphere() {
        Planet planet = bodies().stream().filter(p -> Terraform.terraformable(p) && !p.atmosphere().present()).findFirst().orElseThrow();
        AtmosphereMix base = AtmosphereMixes.base(planet);
        assertTrue(base.isEmpty());
        TerraformState state = Terraform.addGas(planet, base, TerraformState.EMPTY, Gas.NITROGEN, 0.2 * Terraform.capacity(planet));
        EffectiveEnvironment environment = Terraform.effective(planet, base, false, state);
        assertEquals(Kind.THIN, environment.planet().atmosphere().kind());
        assertEquals(0.2, environment.planet().atmosphere().pressure(), 1e-9);
        assertTrue(environment.planet().temperatureSwing() < planet.temperatureSwing() || planet.temperatureSwing() == 0);
        assertTrue(environment.planet().weather().frequency() > 0);
    }

    @Test
    void warmWetPlanetRainsInsteadOfSnowing() {
        Planet planet = coldPlanet();
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.NITROGEN, 0.5 * Terraform.capacity(planet))
                .withMirrors(Terraform.MAX_MIRRORS);
        EffectiveEnvironment environment = effective(planet, state);
        assertTrue(environment.temperature() >= Terraform.SNOW_BELOW, "温度 " + environment.temperature());
        assertEquals(Precipitation.RAIN, environment.planet().weather().precipitation());
        // 冷下来又回到星球本来那一类（有水冰、在下雪的温度），照本来的写沙尘
        TerraformState colder = state.withMirrors(0);
        assertEquals(Precipitation.DUST, effective(planet, colder).planet().weather().precipitation());
    }

    @Test
    void announcesEachStageOnce() {
        Planet planet = coldPlanet();
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.NITROGEN, Terraform.capacity(planet));
        Terraform.Announcement first = Terraform.announce(planet, MARTIAN, false, state);
        assertEquals(EnumSet.of(Terraform.Stage.SHIELDED), first.stages());
        Terraform.Announcement second = Terraform.announce(planet, MARTIAN, false, first.state());
        assertTrue(second.stages().isEmpty());
        assertEquals(first.state(), second.state());
    }

    // ---- 生物量

    private static final Set<Gene> CYANOBACTERIUM = EnumSet.of(Gene.OXYGENIC_PHOTOSYNTHESIS);
    private static final Set<Gene> MARS_LICHEN = EnumSet.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.ANTIFREEZE_PROTEIN, Gene.DNA_REPAIR,
            Gene.UV_SHIELD);

    @Test
    void pioneerNeedsAGeneForEveryHarshCondition() {
        Planet planet = coldPlanet();
        EffectiveEnvironment environment = effective(planet, TerraformState.EMPTY);
        List<Pioneer.Requirement> plain = Pioneer.requirements(CYANOBACTERIUM, environment, 0);
        assertFalse(Pioneer.viable(plain));
        List<Pioneer.Condition> conditions = plain.stream().map(Pioneer.Requirement::condition).toList();
        assertEquals(List.of(Pioneer.Condition.PHOTOSYNTHESIS, Pioneer.Condition.COLD, Pioneer.Condition.RADIATION), conditions);
        assertTrue(Pioneer.viable(EnumSet.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.ANTIFREEZE_PROTEIN, Gene.DNA_REPAIR), environment, 0));
        assertFalse(Pioneer.viable(EnumSet.of(Gene.ANTIFREEZE_PROTEIN, Gene.DNA_REPAIR), environment, 0));

        // 气压低于 0.06 个大气压要紫外防护
        Planet thinner = new Planet(planet.id(), planet.seed(), planet.name(), planet.type(), planet.orbit(), planet.radius(),
                planet.gravity(), new Atmosphere(Kind.THIN, 0.01), planet.temperature(), planet.temperatureSwing(), planet.hazards(),
                planet.liquid(), planet.dayCycle(), planet.weather(), planet.terrain(), planet.ecology(), planet.palette(),
                planet.resources(), planet.worldSize(), false, List.of());
        EffectiveEnvironment thin = Terraform.effective(thinner, MARTIAN, false, TerraformState.EMPTY);
        assertFalse(Pioneer.viable(EnumSet.of(Gene.OXYGENIC_PHOTOSYNTHESIS, Gene.ANTIFREEZE_PROTEIN, Gene.DNA_REPAIR), thin, 0));
        assertTrue(Pioneer.viable(MARS_LICHEN, thin, 0));
    }

    @Test
    void dryPlanetNeedsIrrigationAndCompatibleSolutes() {
        Planet wet = coldPlanet();
        Planet dry = new Planet(wet.id(), wet.seed(), wet.name(), wet.type(), wet.orbit(), wet.radius(), wet.gravity(), wet.atmosphere(),
                wet.temperature(), wet.temperatureSwing(), wet.hazards(), wet.liquid(), wet.dayCycle(), wet.weather(), wet.terrain(),
                wet.ecology(), wet.palette(), List.of(), wet.worldSize(), false, List.of());
        EffectiveEnvironment environment = Terraform.effective(dry, MARTIAN, false, TerraformState.EMPTY);
        assertFalse(environment.naturalWater());
        assertFalse(Pioneer.viable(MARS_LICHEN, environment, 0));
        assertFalse(Pioneer.viable(MARS_LICHEN, environment, 1));
        Set<Gene> desert = EnumSet.copyOf(MARS_LICHEN);
        desert.add(Gene.COMPATIBLE_SOLUTES);
        assertFalse(Pioneer.viable(desert, environment, 0));
        assertTrue(Pioneer.viable(desert, environment, 0.5));
    }

    @Test
    void tooColdAndTooHotCannotBeHelped() {
        Planet planet = coldPlanet();
        Set<Gene> everything = EnumSet.allOf(Gene.class);
        assertFalse(Pioneer.viable(everything, effective(planet, TerraformState.EMPTY.withShades(10)), 1));
        assertTrue(Pioneer.viable(everything, effective(planet, TerraformState.EMPTY), 1));
    }

    @Test
    void seedingOnlyTakesWhenViable() {
        Planet planet = coldPlanet();
        Terraform.Seeding failed = Terraform.seed(planet, MARTIAN, false, TerraformState.EMPTY, CYANOBACTERIUM);
        assertFalse(failed.viable());
        assertEquals(TerraformState.EMPTY, failed.state());
        Terraform.Seeding seeded = Terraform.seed(planet, MARTIAN, false, TerraformState.EMPTY, MARS_LICHEN);
        assertTrue(seeded.viable());
        assertEquals(List.of(MARS_LICHEN), seeded.state().pioneers());
        assertEquals(Biosphere.SEED_BIOMASS, seeded.state().biomass(), 1e-12);
        // 再播同一种只加生物量
        TerraformState again = Terraform.seed(planet, MARTIAN, false, seeded.state(), MARS_LICHEN).state();
        assertEquals(1, again.pioneers().size());
        assertEquals(2 * Biosphere.SEED_BIOMASS, again.biomass(), 1e-12);
    }

    @Test
    void biomassGrowsWhenViableAndTurnsCarbonDioxideIntoOxygen() {
        Planet planet = coldPlanet();
        TerraformState state = Terraform.seed(planet, MARTIAN, false, TerraformState.EMPTY.withMirrors(Terraform.MAX_MIRRORS),
                MARS_LICHEN).state();
        double co2Before = effective(planet, state).available(Gas.CARBON_DIOXIDE);
        double o2Before = effective(planet, state).available(Gas.OXYGEN);
        TerraformState later = Biosphere.advance(state, planet, MARTIAN, false, 6 * 72_000);
        assertTrue(later.biomass() > 10 * state.biomass(), "生物量 " + later.biomass());
        EffectiveEnvironment environment = effective(planet, later);
        double co2Taken = co2Before - environment.available(Gas.CARBON_DIOXIDE);
        double o2Made = environment.available(Gas.OXYGEN) - o2Before;
        assertTrue(co2Taken > 0);
        assertEquals(co2Taken, o2Made, 1e-6 * co2Taken);
        assertTrue(later.biomass() <= 1);
    }

    @Test
    void biomassDiesWhenTheEnvironmentTurns() {
        Planet planet = coldPlanet();
        TerraformState state = Terraform.seed(planet, MARTIAN, false, TerraformState.EMPTY, MARS_LICHEN).state().withBiomass(0.1);
        // 放进二氧化硫，没有质子泵的先锋活不了
        state = Terraform.addGas(planet, MARTIAN, state, Gas.SULFUR_DIOXIDE, 0.01 * Terraform.capacity(planet));
        TerraformState later = Biosphere.advance(state, planet, MARTIAN, false, 72_000);
        assertEquals(0.05, later.biomass(), 1e-3);
    }

    @Test
    void lockedNightPlanetsGrowLess() {
        Planet day = coldPlanet();
        Planet night = coldPlanet(List.of(Hazard.COLD, Hazard.RADIATION), DayCycle.LOCKED_NIGHT);
        EffectiveEnvironment environment = effective(day, TerraformState.EMPTY);
        assertEquals(0.1 * Biosphere.carryingCapacity(environment, day, 0),
                Biosphere.carryingCapacity(Terraform.effective(night, MARTIAN, false, TerraformState.EMPTY), night, 0), 1e-12);
    }

    // ---- 要多久

    /**
     * ADR 的目标：一座中期工厂十座释放塔、每座每秒一个，十到二十小时把一颗冷的稀薄小星球改到能呼吸。
     * 计划：先锋生物把二氧化碳换成氧，留 0.4%；释放塔补氧到 0.21 个大气压、补一成甲烷当温室气体、补氮到总共 0.55 个大气压；
     * 反射镜补足温度。
     */
    @Test
    void typicalColdPlanetTakesTenToTwentyHours() {
        Planet planet = coldPlanet();
        double capacity = Terraform.capacity(planet);
        assertEquals(1.418e6, capacity, 0.01e6);

        double co2 = Terraform.baseUnits(planet, MARTIAN, Gas.CARBON_DIOXIDE);
        double co2Left = 0.004 * capacity;
        double converted = co2 - co2Left;
        double oxygen = 0.21 * capacity - Terraform.baseUnits(planet, MARTIAN, Gas.OXYGEN) - converted;
        double methane = 0.0055 * capacity;
        double others = 0;
        for (Gas gas : List.of(Gas.NITROGEN, Gas.ARGON, Gas.CARBON_MONOXIDE)) {
            others += Terraform.baseUnits(planet, MARTIAN, gas);
        }
        double nitrogen = 0.55 * capacity - (0.21 * capacity + co2Left + methane + others);

        TerraformState state = TerraformState.EMPTY;
        state = Terraform.addGas(planet, MARTIAN, state, Gas.CARBON_DIOXIDE, -converted);
        state = Terraform.addGas(planet, MARTIAN, state, Gas.OXYGEN, converted + oxygen);
        state = Terraform.addGas(planet, MARTIAN, state, Gas.METHANE, methane);
        state = Terraform.addGas(planet, MARTIAN, state, Gas.NITROGEN, nitrogen);
        double unmirrored = effective(planet, state).temperature();
        int mirrors = (int) Math.ceil((260 - unmirrored) / Terraform.MIRROR_KELVIN);
        assertTrue(mirrors <= Terraform.MAX_MIRRORS, "反射镜 " + mirrors);
        state = state.withMirrors(mirrors);

        EffectiveEnvironment environment = effective(planet, state);
        assertNotNull(environment);
        assertEquals(0.55, environment.pressure(), 1e-6);
        // 一氧化碳在补氧的同时被氧化掉：氧分压过 1 千帕以后，现实两个月一个寿命，游戏里三个小时
        assertFalse(environment.breathable());
        state = AtmosphericChemistry.advance(state, planet, MARTIAN, false, (long) (90 * AtmosphericChemistry.TICKS_PER_REAL_DAY));
        environment = effective(planet, state);
        assertTrue(environment.breathable(), Terraform.thresholds(environment).toString());
        // 0.4 g 的星球，0.55 个大气压的大气柱比地球的厚，宇宙线也挡住了
        assertTrue(environment.planet().hazards().isEmpty(), environment.planet().hazards().toString());

        double towerUnits = oxygen + methane + nitrogen;
        double hours = towerUnits / (10 * 3600.0);
        assertTrue(hours >= 10 && hours <= 20, "要 " + hours + " 小时");

        // 光合作用换掉那些二氧化碳：长满（上限按改造后的温和环境）时一小时 0.03 个大气压，换完要几个小时，和释放塔同时进行
        double atmToConvert = converted / capacity;
        double hoursAtFullBiomass = atmToConvert / 0.03;
        assertTrue(hoursAtFullBiomass < hours, "光合作用要 " + hoursAtFullBiomass + " 小时");
    }

    // ---- 大气自己去掉毒气

    @Test
    void untouchedConditionsRemoveNothing() {
        Planet planet = coldPlanet();
        // 只加氮：没有氧，没有液态水，一氧化碳没有新的去处
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.NITROGEN, 0.2 * Terraform.capacity(planet));
        TerraformState later = AtmosphericChemistry.advance(state, planet, MARTIAN, false, 10_000_000);
        assertEquals(state, later);
        assertEquals(TerraformState.EMPTY, AtmosphericChemistry.advance(TerraformState.EMPTY, planet, MARTIAN, false, 10_000_000));
    }

    @Test
    void oxygenBurnsCarbonMonoxideAtItsRealLifetime() {
        Planet planet = coldPlanet();
        TerraformState state = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.OXYGEN, 0.2 * Terraform.capacity(planet));
        double co = Terraform.available(planet, MARTIAN, state, Gas.CARBON_MONOXIDE);
        double co2 = Terraform.available(planet, MARTIAN, state, Gas.CARBON_DIOXIDE);
        double o2 = Terraform.available(planet, MARTIAN, state, Gas.OXYGEN);
        // 一个寿命（现实 60 天）剩 1/e
        TerraformState later = AtmosphericChemistry.advance(state, planet, MARTIAN, false, (long) (60 * AtmosphericChemistry.TICKS_PER_REAL_DAY));
        double coLeft = Terraform.available(planet, MARTIAN, later, Gas.CARBON_MONOXIDE);
        assertEquals(co / Math.E, coLeft, 1e-6 * co);
        double burned = co - coLeft;
        // 2 CO + O₂ → 2 CO₂
        assertEquals(co2 + burned, Terraform.available(planet, MARTIAN, later, Gas.CARBON_DIOXIDE), 1e-6 * co2);
        assertEquals(o2 - burned / 2, Terraform.available(planet, MARTIAN, later, Gas.OXYGEN), 1e-6 * o2);
        // 永夜没有阳光，没有羟基
        Planet night = coldPlanet(List.of(Hazard.COLD, Hazard.RADIATION), DayCycle.LOCKED_NIGHT);
        assertEquals(state, AtmosphericChemistry.advance(state, night, MARTIAN, false, 1_000_000));
    }

    @Test
    void warmRainWashesOutSulfurDioxideOnlyAboveFreezing() {
        Planet planet = coldPlanet();
        double capacity = Terraform.capacity(planet);
        TerraformState cold = Terraform.addGas(planet, MARTIAN, TerraformState.EMPTY, Gas.NITROGEN, 0.5 * capacity);
        cold = Terraform.addGas(planet, MARTIAN, cold, Gas.SULFUR_DIOXIDE, 0.001 * capacity);
        // 冷的时候水冻着，洗不掉
        assertEquals(cold, AtmosphericChemistry.advance(cold, planet, MARTIAN, false, 100_000));
        TerraformState warm = cold.withMirrors(Terraform.MAX_MIRRORS);
        assertTrue(effective(planet, warm).temperature() > 273.15, "温度 " + effective(planet, warm).temperature());
        // 一个寿命（现实两天）剩 1/e
        TerraformState later = AtmosphericChemistry.advance(warm, planet, MARTIAN, false, (long) (2 * AtmosphericChemistry.TICKS_PER_REAL_DAY));
        assertEquals(0.001 * capacity / Math.E, Terraform.available(planet, MARTIAN, later, Gas.SULFUR_DIOXIDE), 1e-6 * capacity);
    }

    @Test
    void sinksThatAlreadyRunDoNotCount() {
        // 本来就下雨、本来就有毒的火山星球：雨本来就在洗二氧化硫，被火山补着，改造前后都不变
        int checked = 0;
        for (Planet planet : bodies()) {
            if (!Terraform.terraformable(planet) || planet.type() != PlanetType.TOXIC) {
                continue;
            }
            AtmosphereMix base = AtmosphereMixes.base(planet);
            if (base.fraction(Gas.SULFUR_DIOXIDE) == 0 || !AtmosphericChemistry.liquidWater(Terraform.effective(planet, base, false,
                    TerraformState.EMPTY))) {
                continue;
            }
            TerraformState state = Terraform.addGas(planet, base, TerraformState.EMPTY, Gas.ARGON, 1);
            TerraformState later = AtmosphericChemistry.advance(state, planet, base, false, 1_000_000);
            assertEquals(state.delta(Gas.SULFUR_DIOXIDE), later.delta(Gas.SULFUR_DIOXIDE), 1e-9, planet.id().toString());
            checked++;
        }
        assertTrue(checked > 0);
    }

    // ---- 存盘

    @Test
    void storedStateRoundTripsAndIgnoresUnknownNames() {
        TerraformState state = TerraformState.EMPTY.withDelta(Gas.NITROGEN, 1234.5).withDelta(Gas.CARBON_DIOXIDE, -20)
                .withMirrors(3).withShades(1).withPioneer(MARS_LICHEN).withBiomass(0.25).withIrrigation(0.5).withLastUpdate(99)
                .withAnnounced(5);
        Map<String, Double> gases = new java.util.LinkedHashMap<>(state.storedGases());
        gases.put("PHLOGISTON", 7.0);
        List<List<String>> pioneers = new ArrayList<>(state.storedPioneers());
        pioneers.add(List.of("MIDICHLORIANS"));
        TerraformState loaded = TerraformState.fromStored(gases, state.biomass(), state.mirrors(), state.shades(), pioneers,
                state.irrigation(), state.lastUpdate(), state.announced());
        assertEquals(state, loaded);
        assertTrue(TerraformState.EMPTY.withDelta(Gas.NITROGEN, 0).isEmpty());
        assertFalse(TerraformState.EMPTY.withBiomass(0.5).withPioneer(MARS_LICHEN).deltas().containsKey(Gas.NITROGEN));
    }
}
