package io.github.sweetcornna.universe.client;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Liquid;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.terrain.Strata;
import io.github.sweetcornna.universe.network.GalaxyInfoPayload;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端的星团：登录时收到星团种子，自己算出全部星球参数（技术架构 §3.1）。
 *
 * <p>只在客户端主线程（渲染线程）上读写。区块网格在后台线程构建时也会读当前星球的配色，
 * 所以当前星球缓存在 volatile 字段里，读到的总是一个完整的对象。
 */
public final class ClientGalaxy {
    private static volatile @Nullable Galaxy galaxy;
    private static volatile @Nullable Planet currentPlanet;
    private static final int[] NEUTRAL_STRATA = Strata.colors(Palette.NEUTRAL, 0);
    private static volatile int[] currentStrata = NEUTRAL_STRATA;
    private static volatile Flora currentFlora = Flora.NEUTRAL;
    private static volatile Fauna currentFauna = Fauna.FALLBACK;
    private static @Nullable ResourceKey<Level> currentDimension;
    /** 按天体路径查过的动物（驯服的动物按出生的星球算物种），换星团时清掉。 */
    private static final Map<String, Fauna> HOME_FAUNA = new ConcurrentHashMap<>();

    private ClientGalaxy() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class,
                event -> event.register(GalaxyInfoPayload.TYPE, ClientGalaxy::handle));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            galaxy = null;
            currentPlanet = null;
            currentDimension = null;
            HOME_FAUNA.clear();
        });
        // 换维度时客户端会新建世界对象，在它开始渲染区块之前就换好配色
        gameBus.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ClientLevel level) {
                update(level.dimension());
            }
        });
        gameBus.addListener(ClientTickEvent.Post.class, event -> refresh());
    }

    private static void handle(GalaxyInfoPayload payload, IPayloadContext context) {
        long start = System.nanoTime();
        galaxy = GalaxyGenerator.fromGalaxySeed(payload.galaxySeed(), payload.systemCount());
        currentDimension = null;
        HOME_FAUNA.clear();
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

    /** 玩家所在的恒星系：太空和星球维度按编号，主世界是母星系；下界、末地这些不在星团里的维度为空。 */
    public static OptionalInt currentSystem() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return OptionalInt.empty();
        }
        ResourceKey<Level> dimension = level.dimension();
        OptionalInt space = SpaceDimensions.system(dimension);
        if (space.isPresent()) {
            return space;
        }
        Optional<BodyId> body = Planets.bodyId(dimension);
        if (body.isPresent()) {
            return OptionalInt.of(body.get().system());
        }
        return dimension == Level.OVERWORLD ? OptionalInt.of(GalaxyGenerator.HOME_SYSTEM) : OptionalInt.empty();
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
        if (dimension != currentDimension) {
            update(dimension);
        }
    }

    private static void update(@Nullable ResourceKey<Level> dimension) {
        currentDimension = dimension;
        Galaxy known = galaxy;
        Planet planet = dimension == null || known == null ? null : Planets.generatedPlanet(known, dimension).orElse(null);
        currentStrata = planet == null ? NEUTRAL_STRATA : Strata.colors(planet.palette(), planet.seed());
        currentFlora = planet == null ? Flora.NEUTRAL : Flora.of(planet);
        Fauna fauna = planet == null ? Fauna.FALLBACK : Fauna.of(planet);
        currentFauna = fauna.lifeless() ? Fauna.FALLBACK : fauna;
        currentPlanet = planet;
    }

    /**
     * 这个维度里水的颜色（不透明的 ARGB）：星球的海是水、酸液或冰时用星球的液体色；不是星球、
     * 或者星球的液体是熔岩（玩家倒的水）时返回 0，用生物群系的颜色。可以在任意线程调用。
     */
    public static int waterColor(ResourceKey<Level> dimension) {
        Galaxy known = galaxy;
        if (known == null) {
            return 0;
        }
        Planet planet = Planets.generatedPlanet(known, dimension).orElse(null);
        if (planet == null || planet.liquid() == Liquid.LAVA || planet.liquid() == Liquid.NONE) {
            return 0;
        }
        // 方块染色是带透明度的 ARGB，透明度为 0 的水会整个看不见
        return 0xFF000000 | planet.palette().liquid();
    }

    /** 当前星球的岩层层序（下标 0 是世界底部）。可以在任意线程调用。 */
    public static int[] currentStrata() {
        refresh();
        return currentStrata;
    }

    /** 当前星球的植物（不在星球上时是中性配色、没有物种）。可以在任意线程调用。 */
    public static Flora currentFlora() {
        refresh();
        return currentFlora;
    }

    /** 这个天体上的动物（还不知道星团、或者那里没有动物时是 {@link Fauna#FALLBACK}）。可以在任意线程调用。 */
    public static Fauna fauna(String path) {
        Galaxy known = galaxy;
        return known == null ? Fauna.FALLBACK : HOME_FAUNA.computeIfAbsent(path, p -> Planets.faunaOf(known, p));
    }

    /** 当前星球的动物（不在星球上、或者星球上没有动物时是 {@link Fauna#FALLBACK}）。可以在任意线程调用。 */
    public static Fauna currentFauna() {
        refresh();
        return currentFauna;
    }
}
