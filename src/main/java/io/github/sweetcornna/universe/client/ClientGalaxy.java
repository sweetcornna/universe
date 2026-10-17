package io.github.sweetcornna.universe.client;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.network.GalaxyInfoPayload;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端的星团：登录时收到星团种子，自己算出全部星球参数（技术架构 §3.1）。
 *
 * <p>只在客户端主线程（渲染线程）上读写。区块网格在后台线程构建时也会读当前星球的配色，
 * 所以当前星球缓存在 volatile 字段里，读到的总是一个完整的对象。
 */
public final class ClientGalaxy {
    private static @Nullable Galaxy galaxy;
    private static volatile @Nullable Planet currentPlanet;
    private static @Nullable ResourceKey<Level> currentDimension;

    private ClientGalaxy() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(GalaxyInfoPayload.TYPE, ClientGalaxy::handle));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            galaxy = null;
            currentPlanet = null;
            currentDimension = null;
        });
    }

    private static void handle(GalaxyInfoPayload payload, IPayloadContext context) {
        long start = System.nanoTime();
        galaxy = GalaxyGenerator.fromGalaxySeed(payload.galaxySeed(), payload.systemCount());
        currentDimension = null;
        Universe.LOGGER.debug("Client galaxy ready: {} systems, {} bodies, {} ms",
                payload.systemCount(), galaxy.bodyCount(), (System.nanoTime() - start) / 1_000_000);
    }

    public static Optional<Galaxy> galaxy() {
        return Optional.ofNullable(galaxy);
    }

    /** 玩家当前所在的星球（本模组生成的星球维度）；在主世界、下界或者还不知道星团时为空。 */
    public static Optional<Planet> currentPlanet() {
        refresh();
        return Optional.ofNullable(currentPlanet);
    }

    /** 当前星球的配色；不在星球上时是中性配色。可以在任意线程调用。 */
    public static Palette currentPalette() {
        refresh();
        Planet planet = currentPlanet;
        return planet != null ? planet.palette() : Palette.NEUTRAL;
    }

    /** 维度变了就重新查一次。只在主线程上真正更新，其他线程直接用已缓存的值。 */
    private static void refresh() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            return;
        }
        ClientLevel level = minecraft.level;
        ResourceKey<Level> dimension = level == null ? null : level.dimension();
        if (dimension == currentDimension) {
            return;
        }
        currentDimension = dimension;
        Galaxy known = galaxy;
        currentPlanet = dimension == null || known == null ? null : Planets.generatedPlanet(known, dimension).orElse(null);
    }
}
