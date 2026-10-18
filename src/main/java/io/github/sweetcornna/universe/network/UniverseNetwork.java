package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.ship.ShipWarp;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.survival.PlayerSurvival;
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
    private static final String PROTOCOL_VERSION = "9";

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
                .playToClient(PlanetWeatherPayload.TYPE, PlanetWeatherPayload.STREAM_CODEC)
                .playToClient(ScanResultPayload.TYPE, ScanResultPayload.STREAM_CODEC)
                .playToClient(ResearchStatePayload.TYPE, ResearchStatePayload.STREAM_CODEC)
                .playToClient(BeltLinesPayload.TYPE, BeltLinesPayload.STREAM_CODEC)
                .playToClient(WarpPayload.TYPE, WarpPayload.STREAM_CODEC)
                .playToClient(DiscoveryPayload.TYPE, DiscoveryPayload.STREAM_CODEC)
                .playToServer(JetpackPayload.TYPE, JetpackPayload.STREAM_CODEC, PlayerSurvival::handleJetpack)
                .playToServer(ResearchActionPayload.TYPE, ResearchActionPayload.STREAM_CODEC, Research::handleAction)
                .playToServer(WarpRequestPayload.TYPE, WarpRequestPayload.STREAM_CODEC, ShipWarp::handleRequest)
                .playToServer(AnalyzePayload.TYPE, AnalyzePayload.STREAM_CODEC, Discoveries::handleAnalyze)
                .playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, Discoveries::handleRename);
    }
}
