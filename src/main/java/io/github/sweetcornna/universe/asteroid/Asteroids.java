package io.github.sweetcornna.universe.asteroid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.space.AsteroidField;
import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.station.Stations;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 小行星的生成（ADR-0017，分布见 {@link AsteroidField}）：每半秒看一遍太空里的玩家，把身边 {@link #SPAWN_RADIUS} 格内该有、
 * 还没生成、也不在冷却里的小行星生成出来。小行星自己在离所有玩家都远了以后消失；打碎的记下冷却时间（只在内存里，重启就清空）。
 * 天体的大气球、恒星附近和空间站周围不放。
 */
public final class Asteroids {
    /** 玩家身边多远以内的小行星要生成出来（格）。 */
    static final double SPAWN_RADIUS = 160;
    /** 空间站周围多远以内不放（格）。 */
    private static final double STATION_CLEARANCE = StationLayout.EXTENT + 60;

    /** 一个恒星系的太空：小行星带、天体和空间站的位置。 */
    record Field(StarSystem system, List<AsteroidField.Belt> belts, List<SpaceLayout.Body> bodies, double starRadius,
            Optional<StationLayout.Site> station) {
        boolean clear(double x, double y, double z) {
            if (!AsteroidField.clear(this.bodies, this.starRadius, x, y, z)) {
                return false;
            }
            return this.station.map(site -> {
                double dx = x - site.x();
                double dy = y - site.y();
                double dz = z - site.z();
                return dx * dx + dy * dy + dz * dz > STATION_CLEARANCE * STATION_CLEARANCE;
            }).orElse(true);
        }
    }

    private static final Map<ResourceKey<Level>, Field> FIELDS = new HashMap<>();
    /** 已经生成出来的小行星（按身份）。 */
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Asteroid>> LIVE = new HashMap<>();
    /** 打碎的小行星什么时候（游戏刻）才能重新长出来。 */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> REGROW = new HashMap<>();

    private Asteroids() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("asteroids", () -> tick(event.getServer())));
        gameBus.addListener(ServerStoppedEvent.class, event -> clear());
    }

    private static void clear() {
        FIELDS.clear();
        LIVE.clear();
        REGROW.clear();
    }

    /** 这个太空维度的小行星分布；不是太空维度时返回 null。 */
    static @Nullable Field field(ServerLevel level) {
        OptionalInt index = SpaceDimensions.system(level.dimension());
        if (index.isEmpty()) {
            return null;
        }
        return FIELDS.computeIfAbsent(level.dimension(), key -> Planets.galaxy(level.getServer()).system(index.getAsInt())
                .map(system -> new Field(system, AsteroidField.belts(system), SpaceLayout.of(system), SpaceLayout.starRadius(system),
                        Stations.site(Planets.galaxy(level.getServer()), index.getAsInt())))
                .orElse(null));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        if (server.getTickCount() % 40 == 0) {
            sweep(server);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !(player.level() instanceof ServerLevel level) || !SpaceDimensions.isSpace(level.dimension())) {
                continue;
            }
            Field field = field(level);
            if (field != null) {
                spawnAround(level, field, player.position());
            }
        }
    }

    private static void spawnAround(ServerLevel level, Field field, Vec3 at) {
        Long2ObjectOpenHashMap<Asteroid> live = LIVE.computeIfAbsent(level.dimension(), key -> new Long2ObjectOpenHashMap<>());
        live.values().removeIf(Entity::isRemoved);
        Long2LongOpenHashMap regrow = REGROW.computeIfAbsent(level.dimension(), key -> new Long2LongOpenHashMap());
        long now = level.getGameTime();
        regrow.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        int cell = AsteroidField.CELL;
        int minY = level.getMinY() + 16;
        int maxY = level.getMaxY() - 16;
        int x0 = (int) Math.floor((at.x - SPAWN_RADIUS) / cell);
        int x1 = (int) Math.floor((at.x + SPAWN_RADIUS) / cell);
        int y0 = (int) Math.floor((Math.max(minY, at.y - SPAWN_RADIUS)) / cell);
        int y1 = (int) Math.floor((Math.min(maxY, at.y + SPAWN_RADIUS)) / cell);
        int z0 = (int) Math.floor((at.z - SPAWN_RADIUS) / cell);
        int z1 = (int) Math.floor((at.z + SPAWN_RADIUS) / cell);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    for (AsteroidField.Slot slot : AsteroidField.cell(field.system().seed(), field.belts(), cx, cy, cz)) {
                        double dx = slot.x() - at.x;
                        double dy = slot.y() - at.y;
                        double dz = slot.z() - at.z;
                        if (dx * dx + dy * dy + dz * dz > SPAWN_RADIUS * SPAWN_RADIUS || slot.y() < minY || slot.y() > maxY) {
                            continue;
                        }
                        long key = slot.key();
                        if (live.containsKey(key) || regrow.containsKey(key) || !field.clear(slot.x(), slot.y(), slot.z())) {
                            continue;
                        }
                        Asteroid asteroid = Asteroid.create(level, slot.seed(), key, new Vec3(slot.x(), slot.y(), slot.z()));
                        if (level.addFreshEntity(asteroid)) {
                            live.put(key, asteroid);
                        }
                    }
                }
            }
        }
    }

    /** 小行星碎了：记下冷却，过 {@link AsteroidField#REGROW_TICKS} 才重新长出来。 */
    static void onShattered(ServerLevel level, Asteroid asteroid) {
        LIVE.computeIfAbsent(level.dimension(), key -> new Long2ObjectOpenHashMap<>()).remove(asteroid.slot());
        REGROW.computeIfAbsent(level.dimension(), key -> new Long2LongOpenHashMap()).put(asteroid.slot(),
                level.getGameTime() + AsteroidField.REGROW_TICKS);
    }

    /** 调试命令：维度里已经生成的小行星。 */
    /**
     * 离所有玩家都远了的小行星删掉。区块卸载时不存档的实体不会被移出世界（只是不再更新），小行星自己的检查就跑不到了：
     * 高速飞过小行星带会在身后留下一串，所以这里按表统一清。
     */
    private static void sweep(MinecraftServer server) {
        LIVE.forEach((dimension, live) -> {
            ServerLevel level = server.getLevel(dimension);
            live.values().removeIf(asteroid -> {
                if (asteroid.isRemoved()) {
                    return true;
                }
                if (level == null || level.getNearestPlayer(asteroid.getX(), asteroid.getY(), asteroid.getZ(), Asteroid.DESPAWN_RADIUS, false) == null) {
                    asteroid.discard();
                    return true;
                }
                return false;
            });
        });
    }

    static int live(ServerLevel level) {
        Long2ObjectOpenHashMap<Asteroid> live = LIVE.get(level.dimension());
        if (live == null) {
            return 0;
        }
        live.values().removeIf(Entity::isRemoved);
        return live.size();
    }

    /** 调试命令：删掉维度里所有小行星，清空冷却（下次检查时按种子重新生成）。 */
    static int reset(ServerLevel level) {
        Long2ObjectOpenHashMap<Asteroid> live = LIVE.remove(level.dimension());
        REGROW.remove(level.dimension());
        int count = 0;
        if (live != null) {
            for (Asteroid asteroid : live.values()) {
                if (!asteroid.isRemoved()) {
                    asteroid.discard();
                    count++;
                }
            }
        }
        return count;
    }
}
