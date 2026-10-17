package io.github.sweetcornna.universe.core.galaxy;

import java.util.List;
import java.util.Optional;

/**
 * 一个世界的星团：全部恒星系和天体。是世界种子的纯函数，按需计算并缓存（技术架构 §3.1）。
 *
 * @param seed 星团种子（由世界种子派生）
 */
public record Galaxy(long seed, List<StarSystem> systems) {
    public Galaxy {
        systems = List.copyOf(systems);
    }

    public StarSystem home() {
        return this.systems.get(GalaxyGenerator.HOME_SYSTEM);
    }

    public Planet homePlanet() {
        return this.home().planets().get(GalaxyGenerator.HOME_PLANET);
    }

    public Optional<StarSystem> system(int index) {
        return index >= 0 && index < this.systems.size() ? Optional.of(this.systems.get(index)) : Optional.empty();
    }

    public Optional<Planet> body(BodyId id) {
        Optional<StarSystem> system = this.system(id.system());
        if (system.isEmpty() || id.planet() >= system.get().planets().size()) {
            return Optional.empty();
        }
        Planet planet = system.get().planets().get(id.planet());
        if (!id.isMoon()) {
            return Optional.of(planet);
        }
        return id.moon() < planet.moons().size() ? Optional.of(planet.moons().get(id.moon())) : Optional.empty();
    }

    public int bodyCount() {
        return this.systems.stream().mapToInt(system -> system.bodies().size()).sum();
    }
}
