package io.github.sweetcornna.universe.core.galaxy;

import java.util.List;

import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;

/**
 * 一颗行星或卫星的全部生成参数。由种子决定，不写进存档（技术架构 §3.1）。
 *
 * @param seed             这颗星球的种子，地形、物种等都从它派生
 * @param radius           半径（母星为 1）
 * @param gravity          表面重力（g）
 * @param temperature      平均地表温度（K）
 * @param temperatureSwing 昼夜温差（K），没有大气的星球温差很大
 * @param hazards          环境危害，0–2 种
 * @param worldSize        世界边界的边长（方块）
 * @param rings            是否有行星环（在天空里画出来）
 * @param moons            卫星；卫星自己的这一项为空
 */
public record Planet(
        BodyId id,
        long seed,
        Name name,
        PlanetType type,
        Orbit orbit,
        double radius,
        double gravity,
        Atmosphere atmosphere,
        double temperature,
        double temperatureSwing,
        List<Hazard> hazards,
        Liquid liquid,
        DayCycle dayCycle,
        WeatherProfile weather,
        TerrainProfile terrain,
        Ecology ecology,
        Palette palette,
        List<Resource.Deposit> resources,
        int worldSize,
        boolean rings,
        List<Planet> moons) {
    public Planet {
        hazards = List.copyOf(hazards);
        resources = List.copyOf(resources);
        moons = List.copyOf(moons);
    }

    public boolean moon() {
        return this.id.isMoon();
    }

    public boolean landable() {
        return this.type.landable();
    }

    /** 没有可呼吸的大气，需要外骨骼的生命维持。 */
    public boolean needsLifeSupport() {
        return !this.atmosphere.breathable();
    }

    public boolean hasHazard(Hazard hazard) {
        return this.hazards.contains(hazard);
    }

    public double abundance(Resource resource) {
        for (Resource.Deposit deposit : this.resources) {
            if (deposit.resource() == resource) {
                return deposit.abundance();
            }
        }
        return 0;
    }

    /** 换个名字，其余不变（母星系里有固定名字的天体用）。 */
    public Planet withName(Name newName) {
        return new Planet(this.id, this.seed, newName, this.type, this.orbit, this.radius, this.gravity, this.atmosphere,
                this.temperature, this.temperatureSwing, this.hazards, this.liquid, this.dayCycle, this.weather,
                this.terrain, this.ecology, this.palette, this.resources, this.worldSize, this.rings, this.moons);
    }

    /** 换一份资源表（母星的卫星保证有钛）。 */
    public Planet withResources(List<Resource.Deposit> newResources) {
        return new Planet(this.id, this.seed, this.name, this.type, this.orbit, this.radius, this.gravity, this.atmosphere,
                this.temperature, this.temperatureSwing, this.hazards, this.liquid, this.dayCycle, this.weather,
                this.terrain, this.ecology, this.palette, newResources, this.worldSize, this.rings, this.moons);
    }

    /** 摄氏度，界面上显示用。 */
    public double temperatureCelsius() {
        return this.temperature - 273.15;
    }
}
