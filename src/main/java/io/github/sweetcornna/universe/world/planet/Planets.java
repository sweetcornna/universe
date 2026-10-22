package io.github.sweetcornna.universe.world.planet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.rules.UniverseRules;
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
    /** 星团里默认的恒星系数量。实际的数量建世界时定（游戏规则 {@code star_systems}，ADR-0028 §七），见 {@link #systemCount}。 */
    public static final int SYSTEM_COUNT = GalaxyGenerator.DEFAULT_SYSTEM_COUNT;

    /** 星球维度对应的星球，每个世界查一次。 */
    private static final Map<ServerLevel, Optional<Planet>> PLANETS = new WeakHashMap<>();
    /** 星球维度上的动物，每个世界算一次。 */
    private static final Map<ServerLevel, Optional<Fauna>> FAUNA = new WeakHashMap<>();
    /** 按天体路径查的动物（驯服的动物按出生的星球算物种），键是星团种子加天体路径。 */
    private static final Map<String, Fauna> HOME_FAUNA = new HashMap<>();

    private Planets() {
    }

    /** 这个世界是本模组生成的星球维度时返回它的星球。可以在任意线程调用（世界生成在后台线程）。 */
    public static Optional<Planet> planet(ServerLevel level) {
        synchronized (PLANETS) {
            return PLANETS.computeIfAbsent(level, key -> generatedPlanet(galaxy(key.getServer()), key.dimension()));
        }
    }

    /** 这个世界对应的天体，包括母星（主世界）；其他维度返回空。 */
    public static Optional<Planet> body(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return galaxy(level.getServer()).body(GalaxyGenerator.HOME_PLANET_ID);
        }
        return planet(level);
    }

    /** 星球维度上的动物；不是星球、或者星球上没有动物时返回空。 */
    public static Optional<Fauna> fauna(ServerLevel level) {
        synchronized (FAUNA) {
            return FAUNA.computeIfAbsent(level, key -> planet(key).map(Fauna::of).filter(fauna -> !fauna.lifeless()));
        }
    }

    /** 这个天体上的动物（驯服的动物按出生的星球算物种）；不是有动物的星球时是 {@link Fauna#FALLBACK}。 */
    public static Fauna fauna(MinecraftServer server, String path) {
        String key = galaxySeed(server) + "/" + path;
        synchronized (HOME_FAUNA) {
            Fauna fauna = HOME_FAUNA.get(key);
            if (fauna == null) {
                fauna = faunaOf(galaxy(server), path);
                HOME_FAUNA.put(key, fauna);
            }
            return fauna;
        }
    }

    /** 星团里这个天体上的动物，客户端和服务端共用；和 {@link #fauna(ServerLevel)} 算出来的物种表一样。 */
    public static Fauna faunaOf(Galaxy galaxy, String path) {
        return BodyId.parse(path).flatMap(galaxy::body).filter(Planet::landable).map(Fauna::of).filter(fauna -> !fauna.lifeless())
                .orElse(Fauna.FALLBACK);
    }

    /** 服务端的星团。 */
    public static Galaxy galaxy(MinecraftServer server) {
        return GalaxyGenerator.fromGalaxySeed(galaxySeed(server), systemCount(server));
    }

    /** 这个存档的恒星系数量：建世界时定下，以后不变。 */
    public static int systemCount(MinecraftServer server) {
        return UniverseRules.starSystems(server);
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
