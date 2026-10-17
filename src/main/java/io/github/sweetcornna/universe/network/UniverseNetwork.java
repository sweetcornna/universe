package io.github.sweetcornna.universe.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 网络包注册（技术架构 §3.10）。
 *
 * <p>发往客户端的包只在这里登记格式，处理器在 {@code UniverseClient} 里通过
 * {@code RegisterClientPayloadHandlersEvent} 注册，这样专用服务器不会加载客户端类。
 */
public final class UniverseNetwork {
    /** 协议版本：任何包的格式改动都要加一，版本不同的客户端和服务端会拒绝连接。 */
    private static final String PROTOCOL_VERSION = "3";

    private UniverseNetwork() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(UniverseNetwork::register);
        modBus.addListener(RegisterConfigurationTasksEvent.class, event ->
                event.register(new GalaxyInfoPayload.SyncTask(event.getListener(), ServerLifecycleHooks.getCurrentServer())));
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .configurationToClient(GalaxyInfoPayload.TYPE, GalaxyInfoPayload.STREAM_CODEC)
                .playToClient(DimensionListPayload.TYPE, DimensionListPayload.STREAM_CODEC)
                .playToClient(PlanetWeatherPayload.TYPE, PlanetWeatherPayload.STREAM_CODEC);
    }
}
