package io.github.sweetcornna.universe.core.terraform;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.core.chem.AtmosphereMix;
import io.github.sweetcornna.universe.core.chem.AtmosphereMixes;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere;
import io.github.sweetcornna.universe.core.galaxy.Atmosphere.Kind;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Resource;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile.Precipitation;
import io.github.sweetcornna.universe.core.microbe.Gene;

/**
 * 星球改造（ADR-0036 §六）：星球本来的环境加上 {@link TerraformState}，算出现用的环境。
 *
 * <p>气压照 P = M·g ÷ 面积：同样多的气体，星球越大、重力越小，气压升得越少。
 * 一个单位就是一个气体物品，一个标准大气压合 {@link #capacity} 个单位。
 *
 * <p>大气种类、危害、降水都是"越过门槛才变"：按同一套规则把星球本来的环境和现用的环境各归一类，
 * 两边是同一类就照星球本来的写，不同类才换成新的。这样没改到门槛的星球和以前完全一样，
 * 生成时另有讲究的地方（比如危害最多两种）也不会因为加了一点气体就变。
 */
public final class Terraform {
    /**
     * 一个标准大气压、每平方千米（世界边长按方块算，一格一米）、1 g 重力时的气体单位数。
     * 照"一座中期工厂十座释放塔、每座每秒一个、十到二十个小时把一颗冷的稀薄小星球改到能呼吸"定的，
     * 见 {@code TerraformTest#typicalColdPlanetTakesTenToTwentyHours}：
     * 边长 13312、重力 0.4 的星球一个大气压合约 142 万单位，0.1 个大气压的火星式大气改到能呼吸，
     * 释放塔要放进约 63 万单位的氮和氧（17 个多小时），二氧化碳由先锋生物换成氧。
     */
    public static final double UNITS_PER_KM2_PER_G = 3200;
    /** 重力再小也按这么大算，免得极小的卫星一点气体就涨到几十个大气压。 */
    public static final double MIN_GRAVITY = 0.05;

    /** 寒冷、高温危害消失的温度范围（K），−20 到 40 °C。 */
    public static final double TEMPERATE_MIN = 253;
    public static final double TEMPERATE_MAX = 313;
    /** 冷过这个温度出现寒冷危害、热过这个温度出现高温危害，和星球生成时一样（K）。 */
    public static final double FREEZING = 245;
    public static final double BAKING = 335;
    /**
     * 有毒气体的接触指数（{@link AtmosphereMix#exposureIndex}，各种毒气的分压折成 ppm 除以 NIOSH 的限值再加起来）
     * 不超过这个，毒性危害消失。按分压算：往大气里掺别的气体冲淡不了毒气。
     */
    public static final double TOXIC_LIMIT = 1.0;
    /**
     * 头顶的大气柱质量到地球的这么多倍，挡住宇宙线，辐射危害消失。柱质量 = 气压 ÷ 重力：地球海平面约 1033 g/cm²；
     * 火星那样 0.38 g 的星球，0.38 个大气压就有同样厚的一层。
     */
    public static final double SHIELD_COLUMN = 1.0;
    /** 能呼吸的总气压范围。 */
    public static final double BREATHE_MIN_PRESSURE = 0.5;
    public static final double BREATHE_MAX_PRESSURE = 3.0;
    /** 氧分压的范围（千帕）：16 千帕相当于海拔两千多米；超过 50 千帕长期吸会氧中毒。 */
    public static final double OXYGEN_MIN_KPA = 16;
    public static final double OXYGEN_MAX_KPA = 50;
    /** 二氧化碳的分压折成一个大气压下不超过 5000 ppm（NIOSH 的限值，再高人就头疼犯困）。 */
    public static final double CARBON_DIOXIDE_LIMIT = 5000;
    /** 气压低于这个（标准大气压）算真空。 */
    public static final double VACUUM = 0.001;
    /** 稀薄和浓密大气的分界。 */
    public static final double THIN_BELOW = 0.3;
    public static final double DENSE_ABOVE = 3.0;
    /** 水的三相点约 611 帕：气压更低时地上存不住液态水，不下雨雪。 */
    public static final double TRIPLE_POINT = 0.006;
    /** 低于这个温度（K）降水是雪，和星球生成时一样。 */
    public static final double SNOW_BELOW = 268;

    /** 每面轨道反射镜让平均温度升几度，每面遮阳板降几度。 */
    public static final double MIRROR_KELVIN = 3;
    /** 反射镜、遮阳板各自最多算几面。 */
    public static final int MAX_MIRRORS = 24;
    /** 温室气体的强弱：倍数 = 1 + α·ln(1 + Σ 强弱 × 分压 ÷ 参考分压)，按辐射强迫随浓度取对数的关系。 */
    public static final double GREENHOUSE_ALPHA = 0.03;
    /** 参考分压（标准大气压），约等于工业革命前地球的二氧化碳。 */
    public static final double GREENHOUSE_REFERENCE = 0.0003;
    public static final double MIN_TEMPERATURE = 20;
    public static final double MAX_TEMPERATURE = 1500;

    private Terraform() {
    }

    /** 能不能改造：能登陆、有世界边界，母星不改。 */
    public static boolean terraformable(Planet planet) {
        return planet.landable() && planet.worldSize() > 0 && !planet.id().equals(GalaxyGenerator.HOME_PLANET_ID);
    }

    /** 一个标准大气压合多少单位气体：和表面积成正比，和重力成反比。 */
    public static double capacity(Planet planet) {
        if (planet.worldSize() <= 0) {
            return 0;
        }
        double km = planet.worldSize() / 1000.0;
        return UNITS_PER_KM2_PER_G * km * km / Math.max(planet.gravity(), MIN_GRAVITY);
    }

    /** 星球本来有多少单位这种气体。 */
    public static double baseUnits(Planet planet, AtmosphereMix baseMix, Gas gas) {
        return baseMix.fraction(gas) * planet.atmosphere().pressure() * capacity(planet);
    }

    // ---- 现用的环境

    /** 现用的环境；在星系里找恒星，看是不是致密恒星。 */
    public static EffectiveEnvironment effective(Galaxy galaxy, Planet base, TerraformState state) {
        boolean compact = galaxy.system(base.id().system()).map(system -> system.star().starClass().isCompact()).orElse(false);
        return effective(base, compact, state);
    }

    /** 现用的环境，当它不在致密恒星旁边。 */
    public static EffectiveEnvironment effective(Planet base, TerraformState state) {
        return effective(base, false, state);
    }

    public static EffectiveEnvironment effective(Planet base, boolean compactStar, TerraformState state) {
        return effective(base, AtmosphereMixes.base(base), compactStar, state);
    }

    /**
     * 现用的环境。
     *
     * @param baseMix     星球本来的大气成分（一般是 {@link AtmosphereMixes#base}）
     * @param compactStar 在致密恒星（白矮星、中子星、黑洞）旁边：大气再厚辐射也只减半
     */
    public static EffectiveEnvironment effective(Planet base, AtmosphereMix baseMix, boolean compactStar, TerraformState state) {
        double capacity = capacity(base);
        double basePressure = base.atmosphere().pressure();
        Map<Gas, Double> baseAmounts = amounts(baseMix, basePressure, capacity);
        boolean water = naturalWater(base);
        if (state.isEmpty() || !terraformable(base) || capacity <= 0) {
            return new EffectiveEnvironment(base, capacity, baseAmounts, baseMix, basePressure, base.temperature(),
                    base.hasHazard(Hazard.RADIATION) ? 1 : 0, water, state.biomass());
        }

        EnumMap<Gas, Double> amounts = new EnumMap<>(Gas.class);
        amounts.putAll(baseAmounts);
        state.deltas().forEach((gas, delta) -> amounts.merge(gas, delta, Double::sum));
        amounts.replaceAll((gas, amount) -> Math.max(0, amount));
        amounts.values().removeIf(amount -> amount <= 0);
        double total = 0;
        for (double amount : amounts.values()) {
            total += amount;
        }
        double pressure = total / capacity;
        AtmosphereMix mix = AtmosphereMix.of(amounts);
        double temperature = temperature(base, baseMix, mix, pressure, state);

        // 大气种类
        AirClass baseAir = airClass(basePressure, baseMix);
        AirClass air = airClass(pressure, mix);
        Kind kind = air == baseAir ? base.atmosphere().kind() : kindOf(air, pressure);
        Atmosphere atmosphere = kind == Kind.NONE ? new Atmosphere(Kind.NONE, 0) : new Atmosphere(kind, pressure);

        // 危害：从星球本来的那几种出发，越过门槛的才加、才去
        List<Hazard> hazards = new ArrayList<>(base.hazards());
        Band baseCold = coldBand(base.temperature());
        Band cold = coldBand(temperature);
        if (cold != baseCold) {
            if (cold == Band.MILD) {
                hazards.remove(Hazard.COLD);
            } else if (cold == Band.EXTREME && !hazards.contains(Hazard.COLD)) {
                hazards.add(Hazard.COLD);
            }
        }
        Band baseHeat = heatBand(base.temperature());
        Band heat = heatBand(temperature);
        if (heat != baseHeat) {
            if (heat == Band.MILD) {
                hazards.remove(Hazard.HEAT);
            } else if (heat == Band.EXTREME && !hazards.contains(Hazard.HEAT)) {
                hazards.add(Hazard.HEAT);
            }
        }
        boolean baseToxic = baseMix.exposureIndex(basePressure) > TOXIC_LIMIT;
        boolean toxic = mix.exposureIndex(pressure) > TOXIC_LIMIT;
        if (toxic != baseToxic) {
            if (toxic) {
                if (!hazards.contains(Hazard.TOXIC)) {
                    hazards.add(Hazard.TOXIC);
                }
            } else {
                hazards.remove(Hazard.TOXIC);
            }
        }
        if (kind == Kind.BREATHABLE && !base.atmosphere().breathable()) {
            // 改到能呼吸了：大气里没有毒，本来算在大气头上的毒性一起去掉
            hazards.remove(Hazard.TOXIC);
        }
        double radiationScale = 0;
        if (hazards.contains(Hazard.RADIATION)) {
            boolean baseShielded = column(base, basePressure) >= SHIELD_COLUMN;
            boolean shielded = column(base, pressure) >= SHIELD_COLUMN;
            radiationScale = 1;
            if (shielded && !baseShielded) {
                if (compactStar) {
                    radiationScale = 0.5;
                } else {
                    hazards.remove(Hazard.RADIATION);
                    radiationScale = 0;
                }
            }
        }

        double swing = base.temperatureSwing() * swingFactor(pressure) / swingFactor(basePressure);
        WeatherProfile weather = weather(base, baseMix, mix, pressure, temperature, water);
        Planet planet = new Planet(base.id(), base.seed(), base.name(), base.type(), base.orbit(), base.radius(), base.gravity(),
                atmosphere, temperature, swing, hazards, base.liquid(), base.dayCycle(), weather, base.terrain(), base.ecology(),
                base.palette(), base.resources(), base.worldSize(), base.rings(), base.moons());
        return new EffectiveEnvironment(planet, capacity, amounts, mix, pressure, temperature, radiationScale, water,
                state.biomass());
    }

    /** 头顶的大气柱质量，地球海平面的倍数：气压 ÷ 重力（重力最小按 {@link #MIN_GRAVITY} 算）。 */
    public static double column(Planet planet, double pressure) {
        return pressure / Math.max(planet.gravity(), MIN_GRAVITY);
    }

    /** 换过大气、温度、危害、天气的星球，给原来读 {@link Planet} 的地方用。 */
    public static Planet withEffective(Planet base, boolean compactStar, TerraformState state) {
        return effective(base, compactStar, state).planet();
    }

    private static Map<Gas, Double> amounts(AtmosphereMix mix, double pressure, double capacity) {
        EnumMap<Gas, Double> amounts = new EnumMap<>(Gas.class);
        mix.fractions().forEach((gas, fraction) -> amounts.put(gas, fraction * pressure * capacity));
        return amounts;
    }

    // ---- 温度

    private static double temperature(Planet base, AtmosphereMix baseMix, AtmosphereMix mix, double pressure, TerraformState state) {
        double basePressure = base.atmosphere().pressure();
        double byPressure = greenhouse(pressure) / greenhouse(basePressure);
        double byGases = (1 + GREENHOUSE_ALPHA * forcing(mix, pressure)) / (1 + GREENHOUSE_ALPHA * forcing(baseMix, basePressure));
        int net = Math.min(state.mirrors(), MAX_MIRRORS) - Math.min(state.shades(), MAX_MIRRORS);
        return Math.clamp(base.temperature() * byPressure * byGases + MIRROR_KELVIN * net, MIN_TEMPERATURE, MAX_TEMPERATURE);
    }

    /** 气压本身的温室效应，和星球生成时一样：1 个标准大气压升温约 13%（{@code PlanetGenerator#greenhouse}）。 */
    static double greenhouse(double pressure) {
        return 1 + 0.13 * Math.log1p(Math.max(0, pressure)) / Math.log(2);
    }

    /** 温室气体的强迫：按二氧化碳当量的分压取对数。 */
    static double forcing(AtmosphereMix mix, double pressure) {
        double equivalent = 0;
        for (Map.Entry<Gas, Double> entry : mix.fractions().entrySet()) {
            equivalent += entry.getKey().greenhouse() * entry.getValue() * pressure;
        }
        return Math.log1p(equivalent / GREENHOUSE_REFERENCE);
    }

    /** 昼夜温差跟着大气变：大气越厚温差越小。 */
    private static double swingFactor(double pressure) {
        return 1 / (1 + 4 * Math.max(0, pressure));
    }

    // ---- 分类

    /** 大气按门槛归的类。 */
    enum AirClass {
        VACUUM,
        THIN,
        MODERATE,
        DENSE,
        TOXIC,
        BREATHABLE
    }

    static AirClass airClass(double pressure, AtmosphereMix mix) {
        if (pressure < VACUUM || mix.isEmpty()) {
            return AirClass.VACUUM;
        }
        if (breathing(pressure, mix)) {
            return AirClass.BREATHABLE;
        }
        if (mix.exposureIndex(pressure) > TOXIC_LIMIT) {
            return AirClass.TOXIC;
        }
        if (pressure > DENSE_ABOVE) {
            return AirClass.DENSE;
        }
        return pressure < THIN_BELOW ? AirClass.THIN : AirClass.MODERATE;
    }

    /** 人能不能直接呼吸。 */
    public static boolean breathing(double pressure, AtmosphereMix mix) {
        double oxygen = mix.partial(Gas.OXYGEN, pressure) * EffectiveEnvironment.KPA_PER_ATM;
        return pressure >= BREATHE_MIN_PRESSURE && pressure <= BREATHE_MAX_PRESSURE
                && oxygen >= OXYGEN_MIN_KPA && oxygen <= OXYGEN_MAX_KPA
                && mix.ppmEquivalent(Gas.CARBON_DIOXIDE, pressure) <= CARBON_DIOXIDE_LIMIT && mix.exposureIndex(pressure) <= TOXIC_LIMIT;
    }

    /** 换了类以后写成哪一种大气。不能呼吸、没有毒、气压适中的，一个大气压以下算稀薄，以上算浓密。 */
    private static Kind kindOf(AirClass air, double pressure) {
        return switch (air) {
            case VACUUM -> Kind.NONE;
            case THIN -> Kind.THIN;
            case MODERATE -> pressure < 1 ? Kind.THIN : Kind.DENSE;
            case DENSE -> Kind.DENSE;
            case TOXIC -> Kind.TOXIC;
            case BREATHABLE -> Kind.BREATHABLE;
        };
    }

    /** 温度的三档：温和、中间、极端。中间那一档不加也不去，免得在门槛上来回跳。 */
    enum Band {
        MILD,
        BETWEEN,
        EXTREME
    }

    static Band coldBand(double temperature) {
        return temperature < FREEZING ? Band.EXTREME : temperature < TEMPERATE_MIN ? Band.BETWEEN : Band.MILD;
    }

    static Band heatBand(double temperature) {
        return temperature > BAKING ? Band.EXTREME : temperature > TEMPERATE_MAX ? Band.BETWEEN : Band.MILD;
    }

    /** 星球上本来就有水：海、冰、酸液（硫酸溶液）、水冰矿，或者本来就下雨下雪。 */
    public static boolean naturalWater(Planet planet) {
        Liquid liquid = planet.liquid();
        return liquid == Liquid.WATER || liquid == Liquid.ICE || liquid == Liquid.ACID
                || planet.type() == PlanetType.HABITABLE || planet.type() == PlanetType.OCEAN
                || planet.weather().precipitation().falls() || planet.abundance(Resource.WATER_ICE) > 0;
    }

    /** 降水按门槛归的类。 */
    enum Rainfall {
        DRY,
        SNOW,
        ACID,
        RAIN
    }

    static Rainfall rainfall(double pressure, double temperature, AtmosphereMix mix, boolean water) {
        if (pressure < TRIPLE_POINT || mix.isEmpty() || !water) {
            return Rainfall.DRY;
        }
        if (temperature < SNOW_BELOW) {
            return Rainfall.SNOW;
        }
        return acidic(mix, pressure) ? Rainfall.ACID : Rainfall.RAIN;
    }

    /**
     * 大气会把水变酸：二氧化硫溶进水里成亚硫酸、硫酸，氯气成盐酸。分压折成 ppm 超过各自的限值就算，
     * 下的是酸雨，先锋生物要扛酸。
     */
    public static boolean acidic(AtmosphereMix mix, double pressure) {
        return mix.ppmEquivalent(Gas.SULFUR_DIOXIDE, pressure) > Gas.SULFUR_DIOXIDE.exposureLimit()
                || mix.ppmEquivalent(Gas.CHLORINE, pressure) > Gas.CHLORINE.exposureLimit();
    }

    private static WeatherProfile weather(Planet base, AtmosphereMix baseMix, AtmosphereMix mix, double pressure, double temperature,
            boolean water) {
        WeatherProfile old = base.weather();
        double basePressure = base.atmosphere().pressure();
        double wind = basePressure > 0 ? Math.clamp(old.wind() * Math.sqrt(pressure / basePressure), 0, 1)
                : Math.clamp(Math.sqrt(pressure) * 0.5, 0.02, 1);
        if (pressure < VACUUM) {
            return WeatherProfile.CALM;
        }
        Rainfall before = rainfall(basePressure, base.temperature(), baseMix, water);
        Rainfall now = rainfall(pressure, temperature, mix, water);
        if (now == before) {
            return old.frequency() > 0 ? new WeatherProfile(old.precipitation(), old.frequency(), old.stormChance(), wind, old.lightning())
                    : new WeatherProfile(Precipitation.DUST, 0.15, 0.1, wind, false);
        }
        Precipitation precipitation = switch (now) {
            case DRY -> old.precipitation().falls() || old.precipitation() == Precipitation.NONE ? Precipitation.DUST : old.precipitation();
            case SNOW -> Precipitation.SNOW;
            case ACID -> Precipitation.ACID_RAIN;
            case RAIN -> Precipitation.RAIN;
        };
        double frequency = old.frequency() > 0 ? old.frequency() : 0.2;
        double storm = old.stormChance() > 0 ? old.stormChance() : 0.1;
        boolean lightning = (precipitation == Precipitation.RAIN || precipitation == Precipitation.ACID_RAIN) && old.lightning();
        return new WeatherProfile(precipitation, frequency, storm, wind, lightning);
    }

    // ---- 改动

    /**
     * 加进（正）或取走（负）一种气体。取走的不会比现有的多：返回的状态里这种气体最少剩 0。
     */
    public static TerraformState addGas(Planet base, TerraformState state, Gas gas, double units) {
        return addGas(base, AtmosphereMixes.base(base), state, gas, units);
    }

    public static TerraformState addGas(Planet base, AtmosphereMix baseMix, TerraformState state, Gas gas, double units) {
        double floor = -baseUnits(base, baseMix, gas);
        double delta = Math.max(floor, state.delta(gas) + units);
        return state.withDelta(gas, delta);
    }

    /** 这种气体现在还有多少单位，大气提取器最多取走这么多。 */
    public static double available(Planet base, TerraformState state, Gas gas) {
        return available(base, AtmosphereMixes.base(base), state, gas);
    }

    public static double available(Planet base, AtmosphereMix baseMix, TerraformState state, Gas gas) {
        return Math.max(0, baseUnits(base, baseMix, gas) + state.delta(gas));
    }

    /** 播种的结果：种活了没有、每个条件对不对得上、播完的状态。 */
    public record Seeding(boolean viable, List<Pioneer.Requirement> requirements, TerraformState state) {
    }

    /**
     * 播一份先锋生物。环境对得上就记下这组基因，生物量加 {@link Biosphere#SEED_BIOMASS}；对不上状态不变。
     */
    public static Seeding seed(Planet base, boolean compactStar, TerraformState state, Set<Gene> genes) {
        return seed(base, AtmosphereMixes.base(base), compactStar, state, genes);
    }

    public static Seeding seed(Planet base, AtmosphereMix baseMix, boolean compactStar, TerraformState state, Set<Gene> genes) {
        EffectiveEnvironment environment = effective(base, baseMix, compactStar, state);
        List<Pioneer.Requirement> requirements = Pioneer.requirements(genes, environment, state.irrigation());
        boolean viable = terraformable(base) && Pioneer.viable(requirements);
        if (!viable) {
            return new Seeding(false, requirements, state);
        }
        TerraformState next = state.withPioneer(genes).withBiomass(state.biomass() + Biosphere.SEED_BIOMASS);
        return new Seeding(true, requirements, next);
    }

    /**
     * 推进一段时间：生物量长或死，光合作用把二氧化碳换成氧（{@link Biosphere}）；大气按条件把毒气去掉（{@link AtmosphericChemistry}）。
     */
    public static TerraformState advance(TerraformState state, Planet base, boolean compactStar, long ticks) {
        AtmosphereMix baseMix = AtmosphereMixes.base(base);
        TerraformState next = Biosphere.advance(state, base, baseMix, compactStar, ticks);
        return AtmosphericChemistry.advance(next, base, baseMix, compactStar, ticks);
    }

    // ---- 门槛和阶段

    /** 界面上的一行门槛。 */
    public record Threshold(Kind kind, double value, double min, double max, boolean met) {
        public enum Kind {
            /** 平均温度（K）。 */
            TEMPERATURE,
            /** 有毒气体的接触指数，不超过 1 才安全。 */
            TOXIC_GASES,
            /** 大气柱质量（地球海平面的倍数，气压 ÷ 重力），挡辐射的门槛。 */
            PRESSURE,
            /** 总气压，能呼吸的范围。 */
            BREATHING_PRESSURE,
            /** 氧分压（千帕）。 */
            OXYGEN,
            /** 二氧化碳的分压折成一个大气压下的 ppm。 */
            CARBON_DIOXIDE
        }
    }

    /** 界面上列的几项门槛，按 ADR 的表排。 */
    public static List<Threshold> thresholds(EffectiveEnvironment environment) {
        double temperature = environment.temperature();
        double toxic = environment.toxicIndex();
        double pressure = environment.pressure();
        double column = environment.shieldingColumn();
        double oxygen = environment.oxygenKpa();
        double carbon = environment.mix().ppmEquivalent(Gas.CARBON_DIOXIDE, pressure);
        return List.of(
                new Threshold(Threshold.Kind.TEMPERATURE, temperature, TEMPERATE_MIN, TEMPERATE_MAX,
                        temperature >= TEMPERATE_MIN && temperature <= TEMPERATE_MAX),
                new Threshold(Threshold.Kind.TOXIC_GASES, toxic, 0, TOXIC_LIMIT, toxic <= TOXIC_LIMIT),
                new Threshold(Threshold.Kind.PRESSURE, column, SHIELD_COLUMN, Double.POSITIVE_INFINITY, column >= SHIELD_COLUMN),
                new Threshold(Threshold.Kind.BREATHING_PRESSURE, pressure, BREATHE_MIN_PRESSURE, BREATHE_MAX_PRESSURE,
                        pressure >= BREATHE_MIN_PRESSURE && pressure <= BREATHE_MAX_PRESSURE),
                new Threshold(Threshold.Kind.OXYGEN, oxygen, OXYGEN_MIN_KPA, OXYGEN_MAX_KPA, oxygen >= OXYGEN_MIN_KPA && oxygen <= OXYGEN_MAX_KPA),
                new Threshold(Threshold.Kind.CARBON_DIOXIDE, carbon, 0, CARBON_DIOXIDE_LIMIT, carbon <= CARBON_DIOXIDE_LIMIT));
    }

    /** 改造的几个阶段，到了全服提示一次。 */
    public enum Stage {
        /** 平均温度在 253–313 K。 */
        TEMPERATE,
        /** 有大气，有毒气体的接触指数不超过 1。 */
        CLEAN_AIR,
        /** 大气柱和地球的一样厚，挡住宇宙线。 */
        SHIELDED,
        /** 能直接呼吸。 */
        BREATHABLE;

        public int bit() {
            return 1 << this.ordinal();
        }
    }

    /** 这个环境到了哪几个阶段。 */
    public static EnumSet<Stage> reached(EffectiveEnvironment environment) {
        EnumSet<Stage> stages = EnumSet.noneOf(Stage.class);
        double temperature = environment.temperature();
        if (temperature >= TEMPERATE_MIN && temperature <= TEMPERATE_MAX) {
            stages.add(Stage.TEMPERATE);
        }
        if (environment.pressure() >= VACUUM && !environment.mix().isEmpty() && environment.toxicIndex() <= TOXIC_LIMIT) {
            stages.add(Stage.CLEAN_AIR);
        }
        if (environment.shieldingColumn() >= SHIELD_COLUMN) {
            stages.add(Stage.SHIELDED);
        }
        if (environment.breathable()) {
            stages.add(Stage.BREATHABLE);
        }
        return stages;
    }

    /** 两个环境比，后一个新到的阶段。 */
    public static EnumSet<Stage> newlyReached(EffectiveEnvironment before, EffectiveEnvironment after) {
        EnumSet<Stage> stages = reached(after);
        stages.removeAll(reached(before));
        return stages;
    }

    /** 该提示的阶段和记下提示以后的状态。 */
    public record Announcement(EnumSet<Stage> stages, TerraformState state) {
    }

    /**
     * 该全服提示的阶段：现在到了、星球本来没到、以前也没提示过的。返回的状态记着这次提示过的。
     */
    public static Announcement announce(Planet base, boolean compactStar, TerraformState state) {
        return announce(base, AtmosphereMixes.base(base), compactStar, state);
    }

    public static Announcement announce(Planet base, AtmosphereMix baseMix, boolean compactStar, TerraformState state) {
        EnumSet<Stage> stages = reached(effective(base, baseMix, compactStar, state));
        stages.removeAll(reached(effective(base, baseMix, compactStar, TerraformState.EMPTY)));
        stages.removeIf(stage -> (state.announced() & stage.bit()) != 0);
        if (stages.isEmpty()) {
            return new Announcement(stages, state);
        }
        int bits = state.announced();
        for (Stage stage : stages) {
            bits |= stage.bit();
        }
        return new Announcement(stages, state.withAnnounced(bits));
    }
}
