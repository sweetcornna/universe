package io.github.sweetcornna.universe.mecha;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.network.FlyersPayload;
import io.github.sweetcornna.universe.network.FlyersPayload.Flyer;
import io.github.sweetcornna.universe.perf.ServerPerf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 在飞的建造无人机和配送机器人（ADR-0023），服务端这边只记每架现在这一段航线，变了攒起来每刻发一次给这个维度的玩家。
 * 进入维度的玩家收到全量。
 */
public final class Flyers {
    private static final Map<ResourceKey<Level>, Map<Integer, Flyer>> ACTIVE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Integer, Flyer>> CHANGED = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<Integer>> LANDED = new HashMap<>();
    private static int nextId = 1;

    private Flyers() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Post.class, event -> ServerPerf.time("flyers", () -> flush(event.getServer())));
        gameBus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> snapshot(event.getEntity()));
        gameBus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> snapshot(event.getEntity()));
        gameBus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> snapshot(event.getEntity()));
        gameBus.addListener(ServerStoppedEvent.class, event -> {
            ACTIVE.clear();
            CHANGED.clear();
            LANDED.clear();
        });
    }

    public static synchronized int nextId() {
        return nextId++;
    }

    /** 一架开始一段新航线。 */
    public static void fly(ServerLevel level, Flyer flyer) {
        ResourceKey<Level> key = level.dimension();
        ACTIVE.computeIfAbsent(key, k -> new HashMap<>()).put(flyer.id(), flyer);
        CHANGED.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(flyer.id(), flyer);
        List<Integer> landed = LANDED.get(key);
        if (landed != null) {
            landed.remove((Integer) flyer.id());
        }
    }

    /** 一架落地了（回到玩家身上、回到配送器里）。 */
    public static void land(ResourceKey<Level> dimension, int id) {
        Map<Integer, Flyer> active = ACTIVE.get(dimension);
        if (active == null || active.remove(id) == null) {
            return;
        }
        Map<Integer, Flyer> changed = CHANGED.get(dimension);
        if (changed != null) {
            changed.remove(id);
        }
        LANDED.computeIfAbsent(dimension, k -> new ArrayList<>()).add(id);
    }

    private static void flush(MinecraftServer server) {
        if (CHANGED.isEmpty() && LANDED.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Map<Integer, Flyer> changed = CHANGED.remove(level.dimension());
            List<Integer> landed = LANDED.remove(level.dimension());
            if ((changed == null || changed.isEmpty()) && (landed == null || landed.isEmpty())) {
                continue;
            }
            FlyersPayload payload = new FlyersPayload(false, changed == null ? List.of() : List.copyOf(changed.values()),
                    landed == null ? List.of() : List.copyOf(landed));
            for (ServerPlayer player : level.players()) {
                // 测试里的假玩家没有协商过本模组的频道
                if (player.connection != null && player.connection.hasChannel(FlyersPayload.TYPE)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
        CHANGED.clear();
        LANDED.clear();
    }

    private static void snapshot(net.minecraft.world.entity.player.Player entity) {
        if (entity instanceof ServerPlayer player && player.connection != null && player.connection.hasChannel(FlyersPayload.TYPE)) {
            Map<Integer, Flyer> active = ACTIVE.get(player.level().dimension());
            PacketDistributor.sendToPlayer(player, new FlyersPayload(true, active == null ? List.of() : List.copyOf(active.values()), List.of()));
        }
    }
}
