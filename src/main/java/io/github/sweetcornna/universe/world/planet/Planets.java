package io.github.sweetcornna.universe.world.planet;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 星团和星球维度的对应关系，服务端和客户端通用。
 *
 * <p>星球维度的 ID 是 {@code universe:<天体路径>}，例如 {@code universe:s3/p2}；母星就是主世界。
 */
public final class Planets {
    /** 星团里的恒星系数量。暂时固定，以后做成世界生成时的配置项。 */
    public static final int SYSTEM_COUNT = GalaxyGenerator.DEFAULT_SYSTEM_COUNT;

    /** 星球维度对应的星球，每个世界查一次。 */
    private static final Map<ServerLevel, Optional<Planet>> PLANETS = new WeakHashMap<>();

    private Planets() {
    }

    /** 这个世界是本模组生成的星球维度时返回它的星球。可以在任意线程调用（世界生成在后台线程）。 */
    public static Optional<Planet> planet(ServerLevel level) {
        synchronized (PLANETS) {
            return PLANETS.computeIfAbsent(level, key -> generatedPlanet(galaxy(key.getServer()), key.dimension()));
        }
    }

    /** 服务端的星团。 */
    public static Galaxy galaxy(MinecraftServer server) {
        return GalaxyGenerator.fromGalaxySeed(galaxySeed(server), SYSTEM_COUNT);
    }

    /** 发给客户端的星团种子，由世界种子派生，反推不出世界种子。 */
    public static long galaxySeed(MinecraftServer server) {
        return GalaxyGenerator.galaxySeed(server.getWorldGenSettings().options().seed());
    }

    public static ResourceKey<Level> dimension(BodyId id) {
        if (id.equals(GalaxyGenerator.HOME_PLANET_ID)) {
            return Level.OVERWORLD;
        }
        return ResourceKey.create(Registries.DIMENSION, Universe.id(id.path()));
    }

    /** 维度对应的天体；主世界是母星，其他不是星球的维度返回空。 */
    public static Optional<BodyId> bodyId(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return Optional.of(GalaxyGenerator.HOME_PLANET_ID);
        }
        if (!dimension.identifier().getNamespace().equals(Universe.MOD_ID)) {
            return Optional.empty();
        }
        return BodyId.parse(dimension.identifier().getPath());
    }

    /** 本模组生成的星球维度（不含主世界）对应的星球。 */
    public static Optional<Planet> generatedPlanet(Galaxy galaxy, ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) {
            return Optional.empty();
        }
        return bodyId(dimension).flatMap(galaxy::body).filter(Planet::landable);
    }
}
