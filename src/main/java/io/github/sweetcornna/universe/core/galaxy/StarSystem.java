package io.github.sweetcornna.universe.core.galaxy;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.NameGenerator.Name;

/**
 * 一个恒星系。
 *
 * @param x       在星团里的坐标（光年），母星系在原点
 * @param planets 行星，按轨道半径从内到外排列
 */
public record StarSystem(int index, long seed, Name name, double x, double y, double z, Star star, List<Planet> planets) {
    public StarSystem {
        planets = List.copyOf(planets);
    }

    /** 全部行星和卫星，先行星、后它的卫星。 */
    public List<Planet> bodies() {
        List<Planet> bodies = new ArrayList<>();
        for (Planet planet : this.planets) {
            bodies.add(planet);
            bodies.addAll(planet.moons());
        }
        return bodies;
    }

    public double distanceTo(StarSystem other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
