package io.github.sweetcornna.universe.microbe;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Microbiome;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.server.MinecraftServer;

/**
 * 星球的微生物组（{@link Microbiome}），按星球缓存：派生一次要生成几十个菌，采样、培养时反复查。
 * 用的是星球本来的数据，不看改造（菌是星球原生的）。
 */
public final class Microbiomes {
    private static final Map<String, Microbiome> CACHE = new ConcurrentHashMap<>();

    private Microbiomes() {
    }

    public static Microbiome of(Planet planet) {
        return CACHE.computeIfAbsent(planet.id().path() + "@" + planet.seed(), key -> Microbiome.of(planet));
    }

    public static Optional<Planet> planet(Galaxy galaxy, String path) {
        return BodyId.parse(path).flatMap(galaxy::body);
    }

    /** 这管样本里分离得出来的菌：那颗星球上这种来源的菌。组织样本没有。 */
    public static List<MicrobeStrain> candidates(MinecraftServer server, Sample sample) {
        if (sample.tissue()) {
            return List.of();
        }
        return planet(Planets.galaxy(server), sample.body()).map(planet -> of(planet).in(sample.source())).orElse(List.of());
    }
}
