package io.github.sweetcornna.universe.hazard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.hazard.HazardKind;
import io.github.sweetcornna.universe.core.hazard.HazardOdds;
import io.github.sweetcornna.universe.core.hazard.HazardSites;
import io.github.sweetcornna.universe.core.hazard.LocalExposure;
import io.github.sweetcornna.universe.core.hazard.SporeDrift;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseGameRules;
import io.github.sweetcornna.universe.teleport.BaseData;
import io.github.sweetcornna.universe.world.planet.PlanetWeather;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 局部危害（ADR-0034 §六）的服务端调度：每个玩家每隔一会儿掷一次，在合适的星球和群系里放出火山喷发、孢子云、
 * 尘卷风；间歇泉是世界生成的喷口自己按节奏喷。
 *
 * <p>被认领的聚落、基地信标 {@value #BASE_RADIUS} 格内什么都不发生（{@link #exempt}）；世界设置里的频率（游戏规则
 * {@code local_hazards}，百分比）为 0 时全部停掉，包括间歇泉。
 *
 * <p>玩家在水汽柱、孢子云里时，喷口和云每刻给他记一笔（{@link #steam}、{@link #spores}），生命维持算环境时读
 * {@link #exposure}，所以多个来源叠在一起也只算一次。
 */
public final class Hazards {
    /** 基地信标多远内不发生局部危害。 */
    public static final double BASE_RADIUS = 32;
    /** 水汽柱里每刻加的危害强度：大约十几秒就扣光一条满的防护。 */
    public static final double STEAM_SEVERITY = 20;
    /** 记一笔管几刻：喷口、云和玩家谁先动都接得上。 */
    private static final int MARK_TICKS = 3;
    /** 玩家附近这么远内已经有一处同类的危害，就不再放新的。 */
    private static final double NEARBY = 96;
    /**
     * 别的不该发生局部危害的地方（聚落）：之后接进来的玩法往这里加，不用改这个类。
     */
    public static final List<BiPredicate<ServerLevel, BlockPos>> EXEMPTIONS = new CopyOnWriteArrayList<>();

    private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
    /** 每个玩家上一次身边出危害的时间。 */
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<VolcanicEruption>> ERUPTIONS = new HashMap<>();

    private Hazards() {
    }

    /** 一个玩家此刻身上记着的局部危害。 */
    private record Mark(Hazard steam, long steamUntil, long sporesUntil, long sporesSince) {
        static final Mark NONE = new Mark(null, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                long start = ServerPerf.begin();
                tickPlayer(player);
                ServerPerf.end("player/hazards", start);
            }
        });
        gameBus.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                tickLevel(level);
            }
        });
        gameBus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            MARKS.remove(event.getEntity().getUUID());
            LAST.remove(event.getEntity().getUUID());
        });
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            MARKS.clear();
            LAST.clear();
            synchronized (ERUPTIONS) {
                ERUPTIONS.clear();
            }
        });
        gameBus.addListener(RegisterCommandsEvent.class, event -> HazardCommand.register(event.getDispatcher()));
    }

    /** 这里不发生局部危害：基地信标 {@value #BASE_RADIUS} 格内，或者别的玩法登记的地方（聚落）。 */
    public static boolean exempt(ServerLevel level, BlockPos pos) {
        if (BaseData.get(level.getServer()).near(level.dimension(), pos, BASE_RADIUS)) {
            return true;
        }
        for (BiPredicate<ServerLevel, BlockPos> exemption : EXEMPTIONS) {
            if (exemption.test(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** 世界设置里的频率（百分比）。 */
    public static int frequency(MinecraftServer server) {
        return server.getGameRules().get(UniverseGameRules.LOCAL_HAZARDS.get());
    }

    /** 频率不是 0。 */
    public static boolean enabled(ServerLevel level) {
        return frequency(level.getServer()) > 0;
    }

    // ---- 身上记的一笔

    /** 玩家在水汽柱里：这一刻起按 {@code hazard} 加强度。 */
    public static void steam(ServerPlayer player, Hazard hazard, long time) {
        MARKS.compute(player.getUUID(), (id, mark) -> {
            Mark old = mark == null ? Mark.NONE : mark;
            if (old.steamUntil() < time - 20) {
                hint(player, "hud.universe.hazard.steam", ChatFormatting.GOLD,
                        Component.translatable("hazard.universe." + hazard.name().toLowerCase(Locale.ROOT)));
            }
            return new Mark(hazard, time + MARK_TICKS, old.sporesUntil(), old.sporesSince());
        });
    }

    /** 玩家在孢子云里。 */
    public static void spores(ServerPlayer player, long time) {
        MARKS.compute(player.getUUID(), (id, mark) -> {
            Mark old = mark == null ? Mark.NONE : mark;
            boolean fresh = old.sporesUntil() < time - 40;
            if (fresh) {
                hint(player, "hud.universe.hazard.spores", ChatFormatting.GREEN, Math.round(SporeDrift.TOXIC_FACTOR));
            }
            return new Mark(old.steam(), old.steamUntil(), time + MARK_TICKS, fresh ? time : old.sporesSince());
        });
    }

    /** 生命维持算环境时用：这一刻身边的局部危害。 */
    public static LocalExposure exposure(ServerPlayer player) {
        Mark mark = MARKS.get(player.getUUID());
        if (mark == null) {
            return LocalExposure.NONE;
        }
        long time = player.level().getGameTime();
        LocalExposure exposure = LocalExposure.NONE;
        if (mark.steam() != null && mark.steamUntil() >= time) {
            exposure = exposure.plus(mark.steam(), STEAM_SEVERITY);
        }
        if (mark.sporesUntil() >= time) {
            exposure = exposure.times(Hazard.TOXIC, SporeDrift.TOXIC_FACTOR);
        }
        if (exposure.isEmpty() && mark.steamUntil() < time - 200 && mark.sporesUntil() < time - 200) {
            MARKS.remove(player.getUUID(), mark);
        }
        return exposure;
    }

    /** HUD 上方一行提示。 */
    static void hint(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args).withStyle(color));
    }

    /** 镜头晃一晃（附近的玩家）。 */
    static void shake(ServerLevel level, Vec3 at, double radius, float strength, int ticks) {
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(at);
            if (distance <= radius) {
                float falloff = (float) (1 - 0.6 * distance / radius);
                PacketDistributor.sendToPlayer(player, new HazardPayloads.Shake(strength * falloff, ticks));
            }
        }
    }

    // ---- 调度

    private static void tickPlayer(ServerPlayer player) {
        // 错开每个玩家掷的时刻
        long time = player.level().getGameTime();
        if ((time + (player.getUUID().hashCode() & 0xFFFF)) % HazardOdds.CHECK_INTERVAL != 0) {
            return;
        }
        if (player.isSpectator() || !player.isAlive() || player.getVehicle() instanceof ShipEntity) {
            return;
        }
        ServerLevel level = player.level();
        int frequency = frequency(level.getServer());
        if (frequency <= 0) {
            return;
        }
        Long last = LAST.get(player.getUUID());
        if (last != null && time - last < HazardOdds.QUIET_AFTER) {
            return;
        }
        Optional<Planet> planet = Planets.planet(level);
        if (planet.isEmpty() || exempt(level, player.blockPosition())) {
            return;
        }
        BiomeKind biome = PlanetBiomes.kind(level.getBiome(player.blockPosition()));
        if (biome == null) {
            return;
        }
        Planet p = planet.get();
        int storm = PlanetWeather.severity(level);
        double wind = p.weather().wind();
        for (HazardKind kind : List.of(HazardKind.ERUPTION, HazardKind.SPORE_CLOUD, HazardKind.DUST_DEVIL)) {
            if (!HazardSites.allows(kind, p.type(), p.atmosphere().present(), wind, p.liquid(), biome)) {
                continue;
            }
            if (player.getRandom().nextDouble() >= HazardOdds.chance(kind, frequency, storm, wind) || nearby(level, kind, player.position())) {
                continue;
            }
            if (start(level, kind, player, Rng.of(player.getRandom().nextLong()))) {
                LAST.put(player.getUUID(), time);
                return;
            }
        }
    }

    /** 附近已经有一处同类的危害。 */
    private static boolean nearby(ServerLevel level, HazardKind kind, Vec3 at) {
        return switch (kind) {
            case ERUPTION -> eruptions(level).stream().anyMatch(eruption -> eruption.center().distanceTo(at) < NEARBY);
            case SPORE_CLOUD -> !level.getEntitiesOfClass(SporeCloud.class, new net.minecraft.world.phys.AABB(at, at).inflate(NEARBY)).isEmpty();
            case DUST_DEVIL -> !level.getEntitiesOfClass(DustDevil.class, new net.minecraft.world.phys.AABB(at, at).inflate(NEARBY)).isEmpty();
            case GEYSER -> false;
        };
    }

    /**
     * 在玩家附近放一处危害（调度和调试命令共用）。间歇泉不在这里放，见 {@link HazardCommand}。
     *
     * @return 放成了
     */
    public static boolean start(ServerLevel level, HazardKind kind, ServerPlayer player, Rng rng) {
        Planet planet = Planets.planet(level).orElse(null);
        long planetSeed = planet == null ? level.getSeed() : planet.seed();
        double wind = planet == null ? 0.5 : planet.weather().wind();
        return switch (kind) {
            case ERUPTION -> {
                VolcanicEruption eruption = VolcanicEruption.start(level, player.position(), rng);
                if (eruption == null) {
                    yield false;
                }
                synchronized (ERUPTIONS) {
                    ERUPTIONS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(eruption);
                }
                yield true;
            }
            case SPORE_CLOUD -> {
                double[] offset = SporeDrift.upwind(planetSeed, rng);
                Vec3 at = surface(level, player.getX() + offset[0], player.getZ() + offset[1]);
                yield at != null && SporeCloud.spawn(level, at, planetSeed, wind, rng) != null;
            }
            case DUST_DEVIL -> {
                double[] offset = io.github.sweetcornna.universe.core.hazard.Vortex.spawn(rng);
                Vec3 at = surface(level, player.getX() + offset[0], player.getZ() + offset[1]);
                yield at != null && DustDevil.spawn(level, at, io.github.sweetcornna.universe.core.hazard.Vortex.heading(offset[0], offset[1], rng),
                        rng) != null;
            }
            case GEYSER -> false;
        };
    }

    /** 这一点的地面（最上面一个挡路的方块的顶上）；在不该发生危害的地方、或者地面是液体时为 {@code null}。 */
    static Vec3 surface(ServerLevel level, double x, double z) {
        BlockPos column = BlockPos.containing(x, 0, z);
        if (!level.hasChunkAt(column)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos top = new BlockPos(column.getX(), y, column.getZ());
        if (!level.getFluidState(top.below()).isEmpty() || exempt(level, top)) {
            return null;
        }
        return new Vec3(x, y, z);
    }

    /** 这个维度里正在进行的火山喷发。 */
    public static List<VolcanicEruption> eruptions(ServerLevel level) {
        synchronized (ERUPTIONS) {
            List<VolcanicEruption> list = ERUPTIONS.get(level.dimension());
            return list == null ? List.of() : List.copyOf(list);
        }
    }

    /** 把这个维度里的火山喷发都停掉（调试命令）。 */
    public static int stopEruptions(ServerLevel level) {
        synchronized (ERUPTIONS) {
            List<VolcanicEruption> list = ERUPTIONS.remove(level.dimension());
            return list == null ? 0 : list.size();
        }
    }

    private static void tickLevel(ServerLevel level) {
        List<VolcanicEruption> list;
        synchronized (ERUPTIONS) {
            list = ERUPTIONS.get(level.dimension());
            if (list == null || list.isEmpty()) {
                return;
            }
            list = List.copyOf(list);
        }
        for (VolcanicEruption eruption : list) {
            if (!eruption.tick(level)) {
                synchronized (ERUPTIONS) {
                    List<VolcanicEruption> current = ERUPTIONS.get(level.dimension());
                    if (current != null) {
                        current.remove(eruption);
                    }
                }
            }
        }
    }
}
