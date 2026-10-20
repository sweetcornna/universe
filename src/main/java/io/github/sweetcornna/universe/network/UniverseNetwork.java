package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.entity.ship.ShipWeapons;
import io.github.sweetcornna.universe.exocraft.Exocrafts;
import io.github.sweetcornna.universe.mission.Missions;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.survival.Multitool;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.ship.ShipWarp;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.survival.PlayerSurvival;
import io.github.sweetcornna.universe.tech.SuitTech;
import io.github.sweetcornna.universe.teleport.Teleporters;
import io.github.sweetcornna.universe.trade.Trading;
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
    private static final String PROTOCOL_VERSION = "19";

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
                .playToClient(PoiTextPayload.TYPE, PoiTextPayload.STREAM_CODEC)
                .playToClient(OpenSignalBoosterPayload.TYPE, OpenSignalBoosterPayload.STREAM_CODEC)
                .playToClient(LogisticsFlightsPayload.TYPE, LogisticsFlightsPayload.STREAM_CODEC)
                .playToClient(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.STREAM_CODEC)
                .playToClient(DysonSyncPayload.TYPE, DysonSyncPayload.STREAM_CODEC)
                .playToClient(TradeStatePayload.TYPE, TradeStatePayload.STREAM_CODEC)
                .playToClient(OpenTeleporterPayload.TYPE, OpenTeleporterPayload.STREAM_CODEC)
                .playToClient(OpenMissionBoardPayload.TYPE, OpenMissionBoardPayload.STREAM_CODEC)
                .playToClient(OpenBaseBeaconPayload.TYPE, OpenBaseBeaconPayload.STREAM_CODEC)
                .playToServer(DysonEditPayload.TYPE, DysonEditPayload.STREAM_CODEC, Dyson::handleEdit)
                .playToServer(JetpackPayload.TYPE, JetpackPayload.STREAM_CODEC, PlayerSurvival::handleJetpack)
                .playToServer(ResearchActionPayload.TYPE, ResearchActionPayload.STREAM_CODEC, Research::handleAction)
                .playToServer(WarpRequestPayload.TYPE, WarpRequestPayload.STREAM_CODEC, ShipWarp::handleRequest)
                .playToServer(AnalyzePayload.TYPE, AnalyzePayload.STREAM_CODEC, Discoveries::handleAnalyze)
                .playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, Discoveries::handleRename)
                .playToServer(OpenTechPayload.TYPE, OpenTechPayload.STREAM_CODEC, SuitTech::handleOpen)
                .playToServer(LocatePoiPayload.TYPE, LocatePoiPayload.STREAM_CODEC, LocatePoiPayload::handle)
                .playToServer(ToolModePayload.TYPE, ToolModePayload.STREAM_CODEC, Multitool::handleToggleMode)
                .playToServer(StatsRequestPayload.TYPE, StatsRequestPayload.STREAM_CODEC, ProductionLog::handleRequest)
                .playToServer(TradeActionPayload.TYPE, TradeActionPayload.STREAM_CODEC, Trading::handleAction)
                .playToServer(ShipControlPayload.TYPE, ShipControlPayload.STREAM_CODEC, ShipWeapons::handleControl)
                .playToServer(ExocraftControlPayload.TYPE, ExocraftControlPayload.STREAM_CODEC, Exocrafts::handleControl)
                .playToServer(TeleportRequestPayload.TYPE, TeleportRequestPayload.STREAM_CODEC, Teleporters::handleRequest)
                .playToServer(RenameBasePayload.TYPE, RenameBasePayload.STREAM_CODEC, Teleporters::handleRename)
                .playToServer(MissionActionPayload.TYPE, MissionActionPayload.STREAM_CODEC, Missions::handleAction);
    }
}
