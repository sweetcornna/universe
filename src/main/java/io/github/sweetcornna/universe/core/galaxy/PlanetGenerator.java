package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Atmosphere.Kind;
import io.github.sweetcornna.universe.core.galaxy.WeatherProfile.Precipitation;
import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 按位置和种子生成一颗行星或卫星的全部参数。
 *
 * <p>每个方面（物理、类型、大气、天气、地形……）用自己的随机流（{@code Seeds.derive(seed, "方面")}），
 * 以后调整某一方面的抽样不会让其他方面跟着变。
 */
final class PlanetGenerator {
    /** 1 AU、光度为 1、反照率为 0 时的平衡温度（K）。 */
    private static final double EQUILIBRIUM_AT_1_AU = 278.6;

    private PlanetGenerator() {
    }

    /**
     * 生成参数。
     *
     * @param distance       到恒星的距离（AU）；卫星就是所属行星的轨道半径
     * @param forcedType     指定类型（母星系用），{@code null} 表示按环境决定
     * @param parentGasGiant 卫星所属的行星是不是气态巨行星
     * @param airless        强制没有大气（母星的月球）
     */
    record Spec(BodyId id, long seed, Star star, Orbit orbit, double distance, boolean gasGiant,
            PlanetType forcedType, boolean parentGasGiant, boolean airless) {
    }

    static Planet generate(Spec spec) {
        long seed = spec.seed();
        boolean moon = spec.id().isMoon();
        Star star = spec.star();

        // —— 物理参数 ——
        Rng physical = Rng.of(Seeds.derive(seed, "physical"));
        double radius;
        double gravity;
        boolean rings = false;
        if (spec.gasGiant()) {
            radius = physical.range(3.5, 12);
            gravity = physical.range(1.0, 2.6);
            rings = physical.chance(0.45);
        } else if (moon) {
            radius = physical.logRange(0.08, 0.45);
            gravity = clamp(radius * physical.range(0.55, 1.0), 0.04, 0.4);
        } else {
            radius = physical.logRange(0.35, 1.8);
            gravity = clamp(radius * physical.range(0.7, 1.15), 0.15, 2.4);
            // 少数岩石行星也带环，站在地上看得到环横过天空（ADR-0021）。单开一条流，别的取值不变；母星系照旧
            rings = spec.forcedType() == null && Rng.of(Seeds.derive(seed, "rings")).chance(0.12);
        }
        double albedo = physical.range(0.1, 0.45);
        double atmosphereChance = spec.gasGiant() ? 1
                : moon ? (gravity > 0.2 ? 0.3 : 0.08)
                : clamp((gravity - 0.2) / 0.5, 0.08, 0.92);
        // 每个数都照抽不误，再按条件取舍，保证这条流后面的取值不受前面分支的影响
        boolean hasAtmosphere = physical.chance(atmosphereChance) && !spec.airless();
        double rolledPressure = moon ? physical.logRange(0.02, 1.5) : physical.logRange(0.05, 6);
        double pressure = hasAtmosphere ? rolledPressure : 0;
        double rolledHeat = physical.range(30, 90);
        double internalHeat = star.starClass().isCompact() ? rolledHeat : 0;

        double equilibrium = equilibriumTemperature(star, spec.distance(), albedo) + internalHeat;
        double temperature = equilibrium * greenhouse(pressure);

        // —— 类型 ——
        Rng typeRng = Rng.of(Seeds.derive(seed, "type"));
        PlanetType type = spec.forcedType() != null ? spec.forcedType()
                : decideType(typeRng, spec, temperature, hasAtmosphere, pressure);

        // —— 大气，按类型修正气压，再重新算温度 ——
        Atmosphere atmosphere = decideAtmosphere(Rng.of(Seeds.derive(seed, "atmosphere")), type, hasAtmosphere, pressure);
        temperature = clampTemperature(type, equilibrium * greenhouse(atmosphere.pressure()));

        Liquid liquid = decideLiquid(Rng.of(Seeds.derive(seed, "liquid")), type, atmosphere, moon);
        List<Hazard> hazards = decideHazards(type, star, spec.distance(), temperature, atmosphere, liquid);
        DayCycle dayCycle = decideDayCycle(Rng.of(Seeds.derive(seed, "day")), spec, moon);
        double swing = decideTemperatureSwing(Rng.of(Seeds.derive(seed, "swing")), atmosphere, dayCycle);
        WeatherProfile weather = decideWeather(Rng.of(Seeds.derive(seed, "weather")), type, atmosphere, temperature);
        TerrainProfile terrain = decideTerrain(Rng.of(Seeds.derive(seed, "terrain")), type, liquid, moon)
                .withLandforms(decideLandforms(Rng.of(Seeds.derive(seed, "landforms")), type, liquid, moon));
        Ecology ecology = decideEcology(Rng.of(Seeds.derive(seed, "ecology")), type, moon, Seeds.derive(seed, "species"));
        Palette palette = PlanetPalettes.generate(Rng.of(Seeds.derive(seed, "palette")), type, atmosphere, liquid, star, temperature);
        List<Resource.Deposit> resources = decideResources(Rng.of(Seeds.derive(seed, "resources")), type, star, moon);
        int worldSize = worldSize(radius, moon, spec.gasGiant());
        NameGenerator.Name name = NameGenerator.generate(Rng.of(Seeds.derive(seed, "name")), 2, 3);

        List<Planet> moons = moon ? List.of() : generateMoons(spec, radius);

        return new Planet(spec.id(), seed, name, type, spec.orbit(), radius, gravity, atmosphere, temperature, swing,
                hazards, liquid, dayCycle, weather, terrain, ecology, palette, resources, worldSize, rings, moons);
    }

    /** 黑体平衡温度：T = 278.6 K × L^¼ / √a × (1 − 反照率)^¼。 */
    static double equilibriumTemperature(Star star, double distanceAu, double albedo) {
        return EQUILIBRIUM_AT_1_AU * Math.pow(star.luminosity(), 0.25) / Math.sqrt(distanceAu) * Math.pow(1 - albedo, 0.25);
    }

    /** 温室效应：1 个标准大气压时升温约 13%，和母星相符（255 K → 288 K）。 */
    static double greenhouse(double pressure) {
        return 1 + 0.13 * Math.log1p(pressure) / Math.log(2);
    }

    private static PlanetType decideType(Rng rng, Spec spec, double temperature, boolean hasAtmosphere, double pressure) {
        Star star = spec.star();
        if (spec.gasGiant()) {
            return PlanetType.GAS_GIANT;
        }
        if (star.starClass().isCompact()) {
            double exotic = star.starClass() == StarClass.BLACK_HOLE ? 4 : 1.5;
            return choose(rng, List.of(PlanetType.IRRADIATED, PlanetType.FROZEN, PlanetType.BARREN, PlanetType.EXOTIC),
                    3, 3, 2, exotic);
        }
        if (spec.id().isMoon() && spec.parentGasGiant() && rng.chance(0.15)) {
            // 气态巨行星的潮汐把卫星内部搓热，表面火山遍布
            return PlanetType.SCORCHED;
        }
        if (temperature > 420) {
            return PlanetType.SCORCHED;
        }
        if (temperature < 200) {
            return hasAtmosphere
                    ? choose(rng, List.of(PlanetType.FROZEN, PlanetType.EXOTIC), 4, 0.3)
                    : choose(rng, List.of(PlanetType.FROZEN, PlanetType.BARREN), 2, 2);
        }
        if (!hasAtmosphere) {
            // 没有大气挡着，星风和宇宙射线直接打在地面上
            if (rng.chance(star.starClass().isHighRadiation() ? 0.5 : 0.12)) {
                return PlanetType.IRRADIATED;
            }
            return rng.chance(0.05) ? PlanetType.EXOTIC : PlanetType.BARREN;
        }
        if (rng.chance(star.starClass().isHighRadiation() ? 0.35 : 0.04)) {
            return PlanetType.IRRADIATED;
        }
        if (temperature >= 250 && temperature <= 330 && pressure >= 0.4 && pressure <= 4) {
            return choose(rng, List.of(PlanetType.HABITABLE, PlanetType.OCEAN, PlanetType.TOXIC, PlanetType.EXOTIC),
                    4, 2.5, 2, 0.8);
        }
        if (temperature < 255) {
            return choose(rng, List.of(PlanetType.FROZEN, PlanetType.TOXIC, PlanetType.BARREN, PlanetType.EXOTIC),
                    3, 1, 1, 0.4);
        }
        return choose(rng, List.of(PlanetType.TOXIC, PlanetType.SCORCHED, PlanetType.BARREN, PlanetType.EXOTIC),
                3, 2, 1, 0.5);
    }

    private static Atmosphere decideAtmosphere(Rng rng, PlanetType type, boolean hasAtmosphere, double pressure) {
        return switch (type) {
            case HABITABLE -> new Atmosphere(Kind.BREATHABLE, hasAtmosphere ? clamp(pressure, 0.6, 2.0) : rng.range(0.8, 1.4));
            case OCEAN -> rng.chance(0.45)
                    ? new Atmosphere(Kind.BREATHABLE, hasAtmosphere ? clamp(pressure, 0.6, 2.5) : rng.range(0.8, 1.6))
                    : new Atmosphere(Kind.TOXIC, hasAtmosphere ? clamp(pressure, 0.8, 3) : rng.range(1, 2.5));
            case TOXIC -> new Atmosphere(Kind.TOXIC, hasAtmosphere ? clamp(pressure, 0.6, 6) : rng.range(1, 4));
            case SCORCHED -> !hasAtmosphere ? Atmosphere.NONE
                    : rng.chance(0.5) ? new Atmosphere(Kind.DENSE, clamp(pressure * 2, 2, 10))
                    : new Atmosphere(Kind.THIN, clamp(pressure, 0.05, 0.3));
            case FROZEN -> !hasAtmosphere ? Atmosphere.NONE
                    : rng.chance(0.2) ? new Atmosphere(Kind.TOXIC, clamp(pressure, 0.5, 2))
                    : new Atmosphere(Kind.THIN, clamp(pressure, 0.05, 0.5));
            case BARREN, IRRADIATED -> hasAtmosphere ? new Atmosphere(Kind.THIN, clamp(pressure, 0.01, 0.3)) : Atmosphere.NONE;
            case EXOTIC -> !hasAtmosphere ? Atmosphere.NONE
                    : new Atmosphere(choose(rng, List.of(Kind.THIN, Kind.TOXIC, Kind.BREATHABLE, Kind.DENSE), 2, 2, 1, 1),
                            clamp(pressure, 0.1, 5));
            case GAS_GIANT -> new Atmosphere(Kind.DENSE, 50);
        };
    }

    /** 让温度和类型相符，例如宜居星球不会是 −40 °C。 */
    private static double clampTemperature(PlanetType type, double temperature) {
        double t = switch (type) {
            case HABITABLE -> clamp(temperature, 270, 305);
            case OCEAN -> clamp(temperature, 268, 315);
            case FROZEN -> Math.min(temperature, 250);
            case SCORCHED -> Math.max(temperature, 400);
            default -> temperature;
        };
        return Math.max(t, 20);
    }

    private static Liquid decideLiquid(Rng rng, PlanetType type, Atmosphere atmosphere, boolean moon) {
        return switch (type) {
            case HABITABLE, OCEAN -> Liquid.WATER;
            case SCORCHED -> rng.chance(moon ? 0.9 : 0.8) ? Liquid.LAVA : Liquid.NONE;
            case TOXIC -> rng.chance(0.8) ? Liquid.ACID : Liquid.WATER;
            case FROZEN -> atmosphere.present() && rng.chance(0.5) ? Liquid.ICE : Liquid.NONE;
            case EXOTIC -> choose(rng, List.of(Liquid.WATER, Liquid.ACID, Liquid.LAVA, Liquid.NONE), 2, 1, 1, 2);
            case BARREN, IRRADIATED, GAS_GIANT -> Liquid.NONE;
        };
    }

    private static List<Hazard> decideHazards(PlanetType type, Star star, double distance, double temperature,
            Atmosphere atmosphere, Liquid liquid) {
        List<Hazard> candidates = new ArrayList<>();
        // 和星球类型对应的危害排在前面，超过两种时优先保留
        switch (type) {
            case FROZEN -> candidates.add(Hazard.COLD);
            case SCORCHED -> candidates.add(Hazard.HEAT);
            case TOXIC -> candidates.add(Hazard.TOXIC);
            case IRRADIATED -> candidates.add(Hazard.RADIATION);
            default -> {
            }
        }
        if (type != PlanetType.HABITABLE && temperature < 245) {
            candidates.add(Hazard.COLD);
        }
        if (temperature > 335) {
            candidates.add(Hazard.HEAT);
        }
        if (atmosphere.kind() == Kind.TOXIC || liquid == Liquid.ACID) {
            candidates.add(Hazard.TOXIC);
        }
        boolean nearHotStar = star.starClass().isHighRadiation() && distance < star.habitableZone() * 0.5;
        if (type == PlanetType.IRRADIATED || nearHotStar || star.starClass().isCompact()) {
            candidates.add(Hazard.RADIATION);
        }
        return candidates.stream().distinct().limit(2).toList();
    }

    private static DayCycle decideDayCycle(Rng rng, Spec spec, boolean moon) {
        if (spec.gasGiant()) {
            return DayCycle.SHORT;
        }
        if (moon) {
            return choose(rng, List.of(DayCycle.STANDARD, DayCycle.LONG, DayCycle.VERY_LONG,
                    DayCycle.LOCKED_DAY, DayCycle.LOCKED_TWILIGHT, DayCycle.LOCKED_NIGHT), 1, 3, 3, 0.5, 0.7, 0.5);
        }
        if (spec.distance() < 0.25 * spec.star().habitableZone() && rng.chance(0.7)) {
            // 离恒星太近的行星被潮汐锁定
            return choose(rng, List.of(DayCycle.LOCKED_DAY, DayCycle.LOCKED_TWILIGHT, DayCycle.LOCKED_NIGHT), 1, 2, 1);
        }
        return choose(rng, List.of(DayCycle.SHORT, DayCycle.STANDARD, DayCycle.LONG, DayCycle.VERY_LONG), 2, 4, 2.5, 1);
    }

    private static double decideTemperatureSwing(Rng rng, Atmosphere atmosphere, DayCycle dayCycle) {
        if (dayCycle.locked()) {
            return 0;
        }
        double lengthFactor = Math.sqrt(dayCycle.lengthTicks() / 24000.0);
        return switch (atmosphere.kind()) {
            case NONE -> rng.range(80, 200) * lengthFactor;
            case THIN -> rng.range(30, 80) * lengthFactor;
            case BREATHABLE, TOXIC -> rng.range(5, 20) * lengthFactor;
            case DENSE -> rng.range(1, 6);
        };
    }

    private static WeatherProfile decideWeather(Rng rng, PlanetType type, Atmosphere atmosphere, double temperature) {
        if (!atmosphere.present() || type == PlanetType.GAS_GIANT) {
            return WeatherProfile.CALM;
        }
        Precipitation precipitation = switch (type) {
            case HABITABLE -> temperature < 268 ? Precipitation.SNOW : Precipitation.RAIN;
            case OCEAN -> Precipitation.RAIN;
            case TOXIC -> Precipitation.ACID_RAIN;
            case FROZEN -> atmosphere.pressure() > 0.3 || atmosphere.kind() == Kind.TOXIC ? Precipitation.SNOW : Precipitation.DUST;
            case SCORCHED -> Precipitation.ASH;
            case BARREN, IRRADIATED -> Precipitation.DUST;
            case EXOTIC -> choose(rng, List.of(Precipitation.RAIN, Precipitation.SNOW, Precipitation.DUST,
                    Precipitation.CRYSTAL, Precipitation.ACID_RAIN), 1, 1, 1, 2, 1);
            case GAS_GIANT -> Precipitation.NONE;
        };
        double frequency = rng.range(0.1, 0.45) * (atmosphere.kind() == Kind.THIN ? 0.6 : 1)
                + (type == PlanetType.OCEAN ? 0.15 : 0);
        double stormChance = rng.range(0.05, 0.45) + (atmosphere.kind() == Kind.DENSE || atmosphere.kind() == Kind.TOXIC ? 0.15 : 0);
        double wind = clamp(Math.sqrt(atmosphere.pressure()) * rng.range(0.25, 0.9), 0.02, 1);
        boolean lightning = (precipitation == Precipitation.RAIN || precipitation == Precipitation.ACID_RAIN
                || precipitation == Precipitation.ASH) && rng.chance(0.65);
        return new WeatherProfile(precipitation, clamp(frequency, 0, 0.7), clamp(stormChance, 0, 0.7), wind, lightning);
    }

    private static TerrainProfile decideTerrain(Rng rng, PlanetType type, Liquid liquid, boolean moon) {
        int sea = 63;
        TerrainProfile t = switch (type) {
            case HABITABLE -> new TerrainProfile(sea, sea + rng.range(4, 14), rng.range(700, 1500), rng.range(30, 70),
                    rng.range(10, 28), rng.range(0.3, 0.6), rng.range(0.3, 0.8), rng.range(0, 0.15), 0,
                    rng.range(0.3, 0.7), rng.range(0, 0.25), 0, false, List.of());
            case OCEAN -> new TerrainProfile(sea, sea - rng.range(4, 16), rng.range(500, 1100), rng.range(30, 60),
                    rng.range(6, 16), rng.range(0.2, 0.5), rng.range(0.2, 0.6), 0, 0,
                    rng.range(0.2, 0.5), rng.range(0, 0.15), 0, false, List.of());
            case BARREN -> new TerrainProfile(sea, rng.range(70, 90), rng.range(500, 1500), rng.range(20, 60),
                    rng.range(6, 18), rng.range(0.3, 0.8), rng.range(0.1, 0.6), rng.range(0, 0.3), rng.range(0.5, 1.0),
                    rng.range(0.2, 0.6), rng.range(0, 0.1), rng.range(0, 0.1), false, List.of());
            case FROZEN -> new TerrainProfile(sea, rng.range(66, 90), rng.range(600, 1500), rng.range(25, 80),
                    rng.range(8, 22), rng.range(0.2, 0.5), rng.range(0.4, 0.9), rng.range(0, 0.2), rng.range(0, 0.5),
                    rng.range(0.3, 0.7), rng.range(0.1, 0.35), rng.range(0, 0.3), false, List.of());
            case SCORCHED -> new TerrainProfile(rng.range(50, 63), rng.range(66, 85), rng.range(400, 1100), rng.range(25, 70),
                    rng.range(8, 24), rng.range(0.4, 0.9), rng.range(0.3, 0.8), rng.range(0, 0.25), rng.range(0, 0.3),
                    rng.range(0.4, 0.9), rng.range(0.1, 0.4), rng.range(0.2, 0.7), false, List.of());
            case TOXIC -> new TerrainProfile(sea, rng.range(64, 72), rng.range(400, 1000), rng.range(15, 45),
                    rng.range(6, 16), rng.range(0.3, 0.6), rng.range(0.1, 0.5), rng.range(0, 0.2), 0,
                    rng.range(0.3, 0.6), rng.range(0, 0.3), rng.range(0, 0.2), false, List.of());
            case IRRADIATED -> new TerrainProfile(sea, rng.range(68, 85), rng.range(500, 1200), rng.range(20, 55),
                    rng.range(6, 20), rng.range(0.4, 0.8), rng.range(0.2, 0.6), rng.range(0.1, 0.5), rng.range(0.1, 0.5),
                    rng.range(0.2, 0.6), rng.range(0, 0.2), rng.range(0.3, 0.8), false, List.of());
            case EXOTIC -> new TerrainProfile(sea, rng.range(66, 90), rng.range(400, 1400), rng.range(30, 90),
                    rng.range(8, 30), rng.range(0.3, 0.9), rng.range(0.2, 0.9), rng.range(0, 0.6), rng.range(0, 0.3),
                    rng.range(0.3, 0.8), rng.range(0.3, 0.8), rng.range(0, 0.8), rng.chance(0.5), List.of());
            case GAS_GIANT -> TerrainProfile.HOME;
        };
        if (moon) {
            // 卫星小、没有风化：陨坑更多，起伏更缓
            t = new TerrainProfile(t.seaLevel(), t.baseHeight(), t.continentScale() * 0.7, t.heightVariation() * 0.8,
                    t.hilliness(), t.roughness(), t.ridges() * 0.6, t.terraces(), Math.min(1, t.craters() + 0.35),
                    t.caves(), t.overhangs(), t.spikes(), t.floatingIslands(), List.of());
        }
        if (liquid == Liquid.NONE && t.baseHeight() <= t.seaLevel()) {
            t = new TerrainProfile(t.seaLevel(), t.seaLevel() + 6, t.continentScale(), t.heightVariation(), t.hilliness(),
                    t.roughness(), t.ridges(), t.terraces(), t.craters(), t.caves(), t.overhangs(), t.spikes(),
                    t.floatingIslands(), List.of());
        }
        return t;
    }

    /**
     * 标志性地貌：按星球类型加权抽一到三种，各自带一个强度。用单独的随机流，不影响其他地形参数。
     */
    static List<Landform.Feature> decideLandforms(Rng rng, PlanetType type, Liquid liquid, boolean moon) {
        boolean wet = liquid != Liquid.NONE;
        Map<Landform, Double> weights = new EnumMap<>(Landform.class);
        switch (type) {
            case HABITABLE -> {
                weights.put(Landform.MOUNTAIN_RANGES, 3.0);
                weights.put(Landform.KARST, 1.2);
                weights.put(Landform.PLATEAUS, 1.2);
                weights.put(Landform.CANYONS, 1.0);
                weights.put(Landform.MESAS, 0.8);
                weights.put(Landform.ARCHIPELAGO, 0.8);
                weights.put(Landform.RIFTS, 0.5);
                weights.put(Landform.VOLCANOES, 0.5);
                weights.put(Landform.SINKHOLES, 0.4);
                weights.put(Landform.DUNES, 0.4);
            }
            case OCEAN -> {
                weights.put(Landform.ARCHIPELAGO, 4.0);
                weights.put(Landform.VOLCANOES, 2.0);
                weights.put(Landform.KARST, 2.0);
                weights.put(Landform.MOUNTAIN_RANGES, 1.0);
            }
            case BARREN -> {
                weights.put(Landform.PLATEAUS, 1.5);
                weights.put(Landform.RIFTS, 1.2);
                weights.put(Landform.MOUNTAIN_RANGES, 1.0);
                weights.put(Landform.DUNES, 1.0);
                weights.put(Landform.MESAS, 0.6);
                weights.put(Landform.SINKHOLES, 0.5);
            }
            case FROZEN -> {
                weights.put(Landform.MOUNTAIN_RANGES, 3.0);
                weights.put(Landform.RIFTS, 1.5);
                weights.put(Landform.CANYONS, 1.0);
                weights.put(Landform.PLATEAUS, 1.0);
                weights.put(Landform.SINKHOLES, 0.4);
            }
            case SCORCHED -> {
                weights.put(Landform.VOLCANOES, 4.0);
                weights.put(Landform.RIFTS, 2.0);
                weights.put(Landform.MESAS, 1.0);
                weights.put(Landform.CANYONS, 1.0);
                weights.put(Landform.DUNES, wet ? 0.3 : 1.5);
                weights.put(Landform.PLATEAUS, 1.0);
            }
            case TOXIC -> {
                weights.put(Landform.SINKHOLES, 2.0);
                weights.put(Landform.KARST, 1.5);
                weights.put(Landform.MESAS, 1.0);
                weights.put(Landform.ARCHIPELAGO, 1.0);
                weights.put(Landform.VOLCANOES, 1.0);
            }
            case IRRADIATED -> {
                weights.put(Landform.MESAS, 2.0);
                weights.put(Landform.CANYONS, 2.0);
                weights.put(Landform.DUNES, 2.0);
                weights.put(Landform.RIFTS, 1.0);
                weights.put(Landform.KARST, 1.0);
            }
            case EXOTIC -> {
                weights.put(Landform.KARST, 2.0);
                weights.put(Landform.MESAS, 1.5);
                weights.put(Landform.MOUNTAIN_RANGES, 1.5);
                weights.put(Landform.CANYONS, 1.0);
                weights.put(Landform.VOLCANOES, 1.0);
                weights.put(Landform.SINKHOLES, 1.0);
                weights.put(Landform.ARCHIPELAGO, 1.0);
            }
            case GAS_GIANT -> {
                return List.of();
            }
        }
        if (!wet) {
            weights.remove(Landform.ARCHIPELAGO);
        }
        int count = moon ? 1 : 1 + (rng.chance(0.6) ? 1 : 0) + (rng.chance(0.25) ? 1 : 0);
        List<Landform.Feature> features = new ArrayList<>();
        for (int i = 0; i < count && !weights.isEmpty(); i++) {
            Landform landform = rng.weighted(new ArrayList<>(weights.keySet()), weights::get);
            weights.remove(landform);
            // 第一种是主地貌，最强
            double strength = i == 0 ? rng.range(0.7, 1.0) : rng.range(0.4, 0.8);
            features.add(new Landform.Feature(landform, strength));
        }
        return features;
    }

    private static Ecology decideEcology(Rng rng, PlanetType type, boolean moon, long speciesSeed) {
        if (moon && type != PlanetType.HABITABLE && type != PlanetType.OCEAN && !rng.chance(0.15)) {
            return Ecology.barren(speciesSeed);
        }
        return switch (type) {
            case HABITABLE -> new Ecology(rng.range(0.6, 1), rng.range(0.3, 0.9), rng.range(0.5, 1),
                    rng.range(6, 12), rng.range(4, 9), speciesSeed);
            case OCEAN -> new Ecology(rng.range(0.4, 0.8), rng.range(0.1, 0.5), rng.range(0.4, 0.9),
                    rng.range(4, 9), rng.range(4, 8), speciesSeed);
            case TOXIC -> new Ecology(rng.range(0.3, 0.8), rng.range(0.1, 0.6), rng.range(0.2, 0.6),
                    rng.range(4, 9), rng.range(2, 6), speciesSeed);
            case EXOTIC -> new Ecology(rng.range(0.3, 0.9), rng.range(0.2, 0.8), rng.range(0.2, 0.7),
                    rng.range(4, 10), rng.range(2, 6), speciesSeed);
            case FROZEN -> rng.chance(0.5) ? Ecology.barren(speciesSeed)
                    : new Ecology(rng.range(0.05, 0.3), rng.range(0, 0.15), rng.range(0, 0.3),
                    rng.range(1, 4), rng.range(0, 3), speciesSeed);
            case IRRADIATED -> new Ecology(rng.range(0.1, 0.5), rng.range(0, 0.3), rng.range(0, 0.3),
                    rng.range(2, 5), rng.range(0, 3), speciesSeed);
            case SCORCHED -> rng.chance(0.7) ? Ecology.barren(speciesSeed)
                    : new Ecology(rng.range(0.02, 0.15), 0, rng.range(0, 0.15), rng.range(1, 3), rng.range(0, 2), speciesSeed);
            case BARREN -> rng.chance(0.9) ? Ecology.barren(speciesSeed)
                    : new Ecology(rng.range(0.01, 0.05), 0, 0, rng.range(1, 2), 0, speciesSeed);
            case GAS_GIANT -> Ecology.barren(speciesSeed);
        };
    }

    private static List<Resource.Deposit> decideResources(Rng rng, PlanetType type, Star star, boolean moon) {
        List<Resource.Deposit> deposits = new ArrayList<>();
        switch (type) {
            case HABITABLE -> {
                add(deposits, rng, Resource.IRON, 1, 0.4, 0.8);
                add(deposits, rng, Resource.COPPER, 1, 0.3, 0.7);
                add(deposits, rng, Resource.COAL, 0.9, 0.3, 0.8);
                add(deposits, rng, Resource.CRUDE_OIL, 0.4, 0.1, 0.4);
                add(deposits, rng, Resource.SILICON, 0.3, 0.1, 0.4);
            }
            case OCEAN -> {
                add(deposits, rng, Resource.SALT, 1, 0.5, 1);
                add(deposits, rng, Resource.IRON, 0.8, 0.2, 0.5);
                add(deposits, rng, Resource.ORGANIC_CRYSTAL, 0.5, 0.2, 0.6);
                add(deposits, rng, Resource.DEUTERIUM, 0.3, 0.1, 0.4);
            }
            case BARREN -> {
                add(deposits, rng, Resource.IRON, 1, 0.5, 1);
                add(deposits, rng, Resource.TITANIUM, 0.85, 0.4, 0.9);
                add(deposits, rng, Resource.SILICON, 0.6, 0.2, 0.6);
                add(deposits, rng, Resource.COPPER, 0.5, 0.2, 0.5);
            }
            case FROZEN -> {
                add(deposits, rng, Resource.WATER_ICE, 1, 0.6, 1);
                add(deposits, rng, Resource.FIRE_ICE, 0.8, 0.4, 0.9);
                add(deposits, rng, Resource.TITANIUM, 0.4, 0.1, 0.5);
                add(deposits, rng, Resource.IRON, 0.5, 0.1, 0.4);
            }
            case SCORCHED -> {
                add(deposits, rng, Resource.SULFUR, 1, 0.5, 1);
                add(deposits, rng, Resource.SILICON, 0.9, 0.4, 0.9);
                add(deposits, rng, Resource.OPTICAL_CRYSTAL, 0.4, 0.1, 0.5);
                add(deposits, rng, Resource.IRON, 0.6, 0.2, 0.6);
            }
            case TOXIC -> {
                add(deposits, rng, Resource.CRUDE_OIL, 1, 0.5, 1);
                add(deposits, rng, Resource.ORGANIC_CRYSTAL, 0.7, 0.3, 0.8);
                add(deposits, rng, Resource.SULFUR, 0.5, 0.2, 0.5);
                add(deposits, rng, Resource.COAL, 0.4, 0.2, 0.5);
            }
            case IRRADIATED -> {
                add(deposits, rng, Resource.URANIUM, 1, 0.5, 1);
                add(deposits, rng, Resource.TITANIUM, 0.5, 0.2, 0.6);
                add(deposits, rng, Resource.OPTICAL_CRYSTAL, 0.4, 0.1, 0.5);
            }
            case EXOTIC -> {
                add(deposits, rng, Resource.OPTICAL_CRYSTAL, 1, 0.5, 1);
                add(deposits, rng, Resource.ORGANIC_CRYSTAL, 0.5, 0.2, 0.7);
                add(deposits, rng, Resource.TITANIUM, 0.4, 0.2, 0.6);
                add(deposits, rng, Resource.SILICON, 0.4, 0.2, 0.6);
            }
            case GAS_GIANT -> {
                add(deposits, rng, Resource.HYDROGEN, 1, 0.6, 1);
                add(deposits, rng, Resource.DEUTERIUM, 0.8, 0.2, 0.7);
            }
        }
        // 特殊恒星带来的稀有资源（玩法设计 §1.3）
        if (type.landable()) {
            switch (star.starClass()) {
                case NEUTRON_STAR -> add(deposits, rng, Resource.MAGNETIC_ORE, 0.9, 0.3, 0.9);
                case BLACK_HOLE -> add(deposits, rng, Resource.STRANGE_MATTER, 0.8, 0.2, 0.8);
                case WHITE_DWARF -> add(deposits, rng, Resource.OPTICAL_CRYSTAL, 0.6, 0.3, 0.8);
                default -> {
                }
            }
        }
        if (moon) {
            // 卫星小，储量打个折
            deposits.replaceAll(d -> new Resource.Deposit(d.resource(), d.abundance() * 0.75));
        }
        // 同一种资源重复出现时保留丰度高的那条
        List<Resource.Deposit> merged = new ArrayList<>();
        for (Resource.Deposit deposit : deposits) {
            int existing = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i).resource() == deposit.resource()) {
                    existing = i;
                }
            }
            if (existing < 0) {
                merged.add(deposit);
            } else if (merged.get(existing).abundance() < deposit.abundance()) {
                merged.set(existing, deposit);
            }
        }
        return merged;
    }

    private static void add(List<Resource.Deposit> deposits, Rng rng, Resource resource, double chance, double min, double max) {
        // 不论是否中选都抽两次，免得一种资源的去留影响后面资源的丰度
        boolean present = rng.chance(chance);
        double abundance = rng.range(min, max);
        if (present) {
            deposits.add(new Resource.Deposit(resource, abundance));
        }
    }

    /** 世界边界：卫星 2k–5k 格，行星 8k–30k 格（玩法设计 §2.1），取 512 的整数倍。 */
    static int worldSize(double radius, boolean moon, boolean gasGiant) {
        if (gasGiant) {
            return 0;
        }
        double size = moon
                ? 2000 + (radius - 0.08) / (0.45 - 0.08) * 3000
                : 8000 + (radius - 0.35) / (1.8 - 0.35) * 22000;
        double clamped = moon ? clamp(size, 2048, 5120) : clamp(size, 8192, 30208);
        return (int) Math.round(clamped / 512) * 512;
    }

    private static List<Planet> generateMoons(Spec spec, double planetRadius) {
        Rng rng = Rng.of(Seeds.derive(spec.seed(), "moons"));
        int count;
        if (spec.gasGiant()) {
            count = rng.range(1, 4);
        } else {
            double big = clamp((planetRadius - 0.35) / 1.45, 0, 1);
            count = choose(rng, List.of(0, 1, 2), 5 - 2 * big, 3.5, 1.5 + big);
        }
        List<Planet> moons = new ArrayList<>();
        double moonOrbit = (spec.gasGiant() ? 0.004 : 0.0015) * rng.range(0.8, 1.25);
        for (int k = 0; k < count; k++) {
            Orbit orbit = new Orbit(moonOrbit, rng.range(5, 35), rng.nextDouble(), rng.range(-8, 8));
            moonOrbit *= rng.range(1.5, 2.2);
            moons.add(generate(new Spec(spec.id().moon(k), Seeds.derive(spec.seed(), k), spec.star(), orbit,
                    spec.distance(), false, null, spec.gasGiant(), false)));
        }
        return moons;
    }

    /** 按权重挑选，权重和选项一一对应。 */
    static <T> T choose(Rng rng, List<T> options, double... weights) {
        if (options.size() != weights.length) {
            throw new IllegalArgumentException("options and weights differ in length");
        }
        double total = 0;
        for (double weight : weights) {
            total += Math.max(0, weight);
        }
        double target = rng.nextDouble() * total;
        for (int i = 0; i < options.size(); i++) {
            target -= Math.max(0, weights[i]);
            if (target < 0) {
                return options.get(i);
            }
        }
        return options.getLast();
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
