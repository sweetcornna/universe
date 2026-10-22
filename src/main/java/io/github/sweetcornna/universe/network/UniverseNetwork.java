package io.github.sweetcornna.universe.network;

import io.github.sweetcornna.universe.blueprint.Blueprints;
import io.github.sweetcornna.universe.dyson.Dyson;
import io.github.sweetcornna.universe.entity.CompanionMenu;
import io.github.sweetcornna.universe.entity.ship.ShipWeapons;
import io.github.sweetcornna.universe.exocraft.Exocrafts;
import io.github.sweetcornna.universe.exocraft.VehicleSummoning;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mission.Missions;
import io.github.sweetcornna.universe.stats.ProductionLog;
import io.github.sweetcornna.universe.story.Story;
import io.github.sweetcornna.universe.survival.Multitool;
import io.github.sweetcornna.universe.discovery.Discoveries;
import io.github.sweetcornna.universe.entity.ship.ShipDealer;
import io.github.sweetcornna.universe.station.MultitoolCabinet;
import io.github.sweetcornna.universe.fleet.FleetTerminal;
import io.github.sweetcornna.universe.fleet.FreighterConsole;
import io.github.sweetcornna.universe.portal.Portals;
import io.github.sweetcornna.universe.entity.ship.ShipSummon;
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
    private static final String PROTOCOL_VERSION = "26";

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
                .playToClient(FlyersPayload.TYPE, FlyersPayload.STREAM_CODEC)
                .playToClient(GhostsPayload.TYPE, GhostsPayload.STREAM_CODEC)
                .playToClient(StatsSnapshotPayload.TYPE, StatsSnapshotPayload.STREAM_CODEC)
                .playToClient(DysonSyncPayload.TYPE, DysonSyncPayload.STREAM_CODEC)
                .playToClient(TradeStatePayload.TYPE, TradeStatePayload.STREAM_CODEC)
                .playToClient(OpenTeleporterPayload.TYPE, OpenTeleporterPayload.STREAM_CODEC)
                .playToClient(OpenMissionBoardPayload.TYPE, OpenMissionBoardPayload.STREAM_CODEC)
                .playToClient(OpenBaseBeaconPayload.TYPE, OpenBaseBeaconPayload.STREAM_CODEC)
                .playToClient(StoryStatePayload.TYPE, StoryStatePayload.STREAM_CODEC)
                .playToClient(EndingPayload.TYPE, EndingPayload.STREAM_CODEC)
                .playToServer(EndingRequestPayload.TYPE, EndingRequestPayload.STREAM_CODEC, Story::handleEndingRequest)
                .playToClient(BlueprintPayloads.OpenSave.TYPE, BlueprintPayloads.OpenSave.STREAM_CODEC)
                .playToClient(BlueprintPayloads.Content.TYPE, BlueprintPayloads.Content.STREAM_CODEC)
                .playToServer(BlueprintPayloads.Save.TYPE, BlueprintPayloads.Save.STREAM_CODEC, Blueprints::handleSave)
                .playToServer(BlueprintPayloads.Rotate.TYPE, BlueprintPayloads.Rotate.STREAM_CODEC, Blueprints::handleRotate)
                .playToServer(BlueprintPayloads.Click.TYPE, BlueprintPayloads.Click.STREAM_CODEC, Blueprints::handleClick)
                .playToServer(BlueprintPayloads.Request.TYPE, BlueprintPayloads.Request.STREAM_CODEC, Blueprints::handleRequest)
                .playToServer(DysonEditPayload.TYPE, DysonEditPayload.STREAM_CODEC, Dyson::handleEdit)
                .playToServer(JetpackPayload.TYPE, JetpackPayload.STREAM_CODEC, PlayerSurvival::handleJetpack)
                .playToServer(MechaPayloads.Open.TYPE, MechaPayloads.Open.STREAM_CODEC, Mecha::handleOpen)
                .playToServer(MechaPayloads.Bounds.TYPE, MechaPayloads.Bounds.STREAM_CODEC, Mecha::handleBounds)
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
                .playToServer(MissionActionPayload.TYPE, MissionActionPayload.STREAM_CODEC, Missions::handleAction)
                // M13：飞船面板、召唤、自动驾驶（ADR-0024）
                .playToClient(ShipPayloads.Helm.TYPE, ShipPayloads.Helm.STREAM_CODEC)
                .playToClient(ShipPayloads.Destinations.TYPE, ShipPayloads.Destinations.STREAM_CODEC)
                .playToServer(ShipPayloads.Summon.TYPE, ShipPayloads.Summon.STREAM_CODEC, ShipSummon::handleSummon)
                .playToServer(ShipPayloads.Navigate.TYPE, ShipPayloads.Navigate.STREAM_CODEC, ShipSummon::handleNavigate)
                .playToClient(DealerPayloads.State.TYPE, DealerPayloads.State.STREAM_CODEC)
                .playToServer(DealerPayloads.Buy.TYPE, DealerPayloads.Buy.STREAM_CODEC, ShipDealer::handleBuy)
                .playToServer(DealerPayloads.Sell.TYPE, DealerPayloads.Sell.STREAM_CODEC, ShipDealer::handleSell)
                .playToServer(DealerPayloads.Hire.TYPE, DealerPayloads.Hire.STREAM_CODEC, ShipDealer::handleHire)
                .playToServer(DealerPayloads.BuyFreighter.TYPE, DealerPayloads.BuyFreighter.STREAM_CODEC, ShipDealer::handleBuyFreighter)
                // M14：多功能工具柜（ADR-0026）
                .playToClient(CabinetPayloads.State.TYPE, CabinetPayloads.State.STREAM_CODEC)
                .playToServer(CabinetPayloads.Buy.TYPE, CabinetPayloads.Buy.STREAM_CODEC, MultitoolCabinet::handleBuy)
                // M14：伙伴界面（ADR-0026 §九）
                .playToServer(CompanionPayloads.Rename.TYPE, CompanionPayloads.Rename.STREAM_CODEC, CompanionMenu::handleRename)
                // M14：载具召唤台（ADR-0026）
                .playToClient(VehiclePayloads.Listing.TYPE, VehiclePayloads.Listing.STREAM_CODEC)
                .playToServer(VehiclePayloads.Summon.TYPE, VehiclePayloads.Summon.STREAM_CODEC, VehicleSummoning::handleSummon)
                // M13：货船、舰队（ADR-0024）
                .playToClient(FleetPayloads.Location.TYPE, FleetPayloads.Location.STREAM_CODEC)
                .playToClient(FleetPayloads.Console.TYPE, FleetPayloads.Console.STREAM_CODEC)
                .playToServer(FleetPayloads.Warp.TYPE, FleetPayloads.Warp.STREAM_CODEC, FreighterConsole::handleWarp)
                .playToClient(FleetPayloads.Terminal.TYPE, FleetPayloads.Terminal.STREAM_CODEC)
                .playToServer(FleetPayloads.Action.TYPE, FleetPayloads.Action.STREAM_CODEC, FleetTerminal::handleAction)
                // M13：先驱者传送门（ADR-0024）
                .playToClient(PortalPayloads.Open.TYPE, PortalPayloads.Open.STREAM_CODEC)
                .playToServer(PortalPayloads.Dial.TYPE, PortalPayloads.Dial.STREAM_CODEC, Portals::handleDial)
                // M13：太空遭遇（ADR-0024）
                .playToClient(EncounterPayloads.Signal.TYPE, EncounterPayloads.Signal.STREAM_CODEC);
    }
}
