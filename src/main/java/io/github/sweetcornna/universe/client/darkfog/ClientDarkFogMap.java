package io.github.sweetcornna.universe.client.darkfog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.network.DarkFogMapPayload;
import net.minecraft.util.Util;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 星图上的黑雾（ADR-0028 §八）：收服务端发来的巢穴概况和路上的种子。种子走到哪按收到的时间往后推，退出世界清掉。
 */
public final class ClientDarkFogMap {
    private static Map<Integer, DarkFogMapPayload.Hive> hives = Map.of();
    private static List<DarkFogMapPayload.Seed> seeds = List.of();
    private static long receivedAt;

    private ClientDarkFogMap() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DarkFogMapPayload.TYPE, ClientDarkFogMap::handle));
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            hives = Map.of();
            seeds = List.of();
        });
    }

    private static void handle(DarkFogMapPayload payload, IPayloadContext context) {
        Map<Integer, DarkFogMapPayload.Hive> map = new HashMap<>();
        for (DarkFogMapPayload.Hive hive : payload.hives()) {
            map.put(hive.system(), hive);
        }
        hives = Map.copyOf(map);
        seeds = List.copyOf(payload.seeds());
        receivedAt = Util.getMillis();
    }

    /** 这个恒星系的巢穴；没有是空。 */
    public static Optional<DarkFogMapPayload.Hive> hive(int system) {
        return Optional.ofNullable(hives.get(system));
    }

    public static Map<Integer, DarkFogMapPayload.Hive> hives() {
        return hives;
    }

    public static List<DarkFogMapPayload.Seed> seeds() {
        return seeds;
    }

    /** 种子现在走了多少，0–1：收到时走的刻数加上收到以后过的时间。 */
    public static double progress(DarkFogMapPayload.Seed seed) {
        double ticks = seed.elapsed() + (Util.getMillis() - receivedAt) / 50.0;
        return Math.max(0, Math.min(1, ticks / Math.max(1, seed.total())));
    }

    /** 种子还要多少刻到。 */
    public static int remaining(DarkFogMapPayload.Seed seed) {
        return (int) Math.max(0, Math.round(seed.total() * (1 - progress(seed))));
    }
}
