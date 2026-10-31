package io.github.sweetcornna.universe;

import io.github.sweetcornna.universe.client.darkfog.TurretClient;
import io.github.sweetcornna.universe.client.ClientConfig;
import io.github.sweetcornna.universe.client.ItemSummaries;
import io.github.sweetcornna.universe.client.asteroid.AsteroidClient;
import io.github.sweetcornna.universe.client.blueprint.BlueprintClient;
import io.github.sweetcornna.universe.client.blueprint.GhostClient;
import io.github.sweetcornna.universe.client.civ.NpcClient;
import io.github.sweetcornna.universe.client.mecha.MechaClient;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.client.exocraft.ExoMechClient;
import io.github.sweetcornna.universe.client.exocraft.ExocraftClient;
import io.github.sweetcornna.universe.client.exocraft.SubmarineClient;
import io.github.sweetcornna.universe.client.guide.GuideScreen;
import io.github.sweetcornna.universe.client.pirate.PirateClient;
import io.github.sweetcornna.universe.client.sentinel.GroundSentinelRenderer;
import io.github.sweetcornna.universe.client.poi.OutpostClient;
import io.github.sweetcornna.universe.client.sentinel.SentinelClient;
import io.github.sweetcornna.universe.client.sentinel.SentinelRenderer;
import io.github.sweetcornna.universe.client.PlasmaBoltRenderer;
import io.github.sweetcornna.universe.client.PlasmaGrenadeRenderer;
import io.github.sweetcornna.universe.client.ClientDimensions;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.ClientWeather;
import io.github.sweetcornna.universe.client.MultitoolClient;
import io.github.sweetcornna.universe.client.SuitClient;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.client.creature.AlienCreatureRenderer;
import io.github.sweetcornna.universe.client.creature.CompanionScreen;
import io.github.sweetcornna.universe.client.creature.AquaticCreatureRenderer;
import io.github.sweetcornna.universe.client.creature.EggClient;
import io.github.sweetcornna.universe.client.creature.GiantFootsteps;
import io.github.sweetcornna.universe.client.discovery.AnalysisVisor;
import io.github.sweetcornna.universe.client.discovery.ClientDiscoveries;
import io.github.sweetcornna.universe.client.dyson.ClientDyson;
import io.github.sweetcornna.universe.client.factory.FactoryClient;
import io.github.sweetcornna.universe.client.galaxy.StarMapClient;
import io.github.sweetcornna.universe.client.galaxy.WarpClient;
import io.github.sweetcornna.universe.client.mission.MissionClient;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.client.ship.ShipNavClient;
import io.github.sweetcornna.universe.client.fleet.FleetClient;
import io.github.sweetcornna.universe.client.portal.PortalClient;
import io.github.sweetcornna.universe.client.encounter.EncounterClient;
import io.github.sweetcornna.universe.client.poi.PoiClient;
import io.github.sweetcornna.universe.client.sky.PlanetSkyRenderer;
import io.github.sweetcornna.universe.client.station.StationClient;
import io.github.sweetcornna.universe.client.stats.StatsClient;
import io.github.sweetcornna.universe.client.story.ClientStory;
import io.github.sweetcornna.universe.client.teleport.TeleportClient;
import io.github.sweetcornna.universe.client.tech.TechClient;
import io.github.sweetcornna.universe.client.trade.TradeClient;
import io.github.sweetcornna.universe.datagen.UniverseDataGen;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.guide.GuideItem;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.world.planet.PlanetEnvironment;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端入口：只在客户端加载，渲染、HUD、界面相关的注册放在这里。
 */
@Mod(value = Universe.MOD_ID, dist = Dist.CLIENT)
public final class UniverseClient {
    public UniverseClient(IEventBus modBus, ModContainer container) {
        ClientConfig.register(container);
        modBus.addListener(this::clientSetup);
        ClientDimensions.registerEvents(modBus, NeoForge.EVENT_BUS);
        ClientGalaxy.registerEvents(modBus, NeoForge.EVENT_BUS);
        ClientWeather.registerEvents(modBus, NeoForge.EVENT_BUS);
        PlanetTints.registerEvents(modBus);
        PlanetSkyRenderer.registerEvents(modBus, NeoForge.EVENT_BUS);
        ShipClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        ShipNavClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        FleetClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        PortalClient.registerEvents(modBus);
        EncounterClient.registerEvents(modBus);
        SuitClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        MultitoolClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        FactoryClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        ClientDyson.registerEvents(modBus, NeoForge.EVENT_BUS);
        UniverseKeys.registerEvents(modBus);
        StarMapClient.registerEvents(NeoForge.EVENT_BUS);
        WarpClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        ClientDiscoveries.registerEvents(modBus, NeoForge.EVENT_BUS);
        AnalysisVisor.registerEvents(modBus, NeoForge.EVENT_BUS);
        TechClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        StatsClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        TradeClient.registerEvents(modBus);
        StationClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        PirateClient.registerEvents(modBus);
        NpcClient.registerEvents(modBus);
        AsteroidClient.registerEvents(modBus);
        ExocraftClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        SubmarineClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        ExoMechClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        GiantFootsteps.registerEvents(NeoForge.EVENT_BUS);
        ItemSummaries.registerEvents(NeoForge.EVENT_BUS);
        PoiClient.registerEvents(modBus);
        SentinelClient.registerEvents(modBus);
        OutpostClient.registerEvents(modBus);
        CompanionScreen.registerEvents(modBus);
        TeleportClient.registerEvents(modBus);
        EggClient.registerEvents(modBus);
        MissionClient.registerEvents(modBus);
        io.github.sweetcornna.universe.client.darkfog.DarkFogClient.registerEvents(modBus);
        io.github.sweetcornna.universe.client.facility.FacilityClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        TurretClient.registerEvents(modBus);
        io.github.sweetcornna.universe.client.darkfog.DarkFogSupportClient.registerEvents(modBus);
        // M16 第二块（ADR-0028 §八）
        // M16 黑雾：巢穴（H）的注册加在这行上面
        io.github.sweetcornna.universe.client.darkfog.hive.HiveClient.registerEvents(modBus);
        // M16 黑雾：巢穴建筑（B）的注册加在这行上面
        io.github.sweetcornna.universe.client.darkfog.space.SpaceUnitClient.registerEvents(modBus);
        // M16 黑雾：太空单位（U）的注册加在这行上面
        io.github.sweetcornna.universe.client.darkfog.ClientDarkFogMap.registerEvents(modBus, NeoForge.EVENT_BUS);
        io.github.sweetcornna.universe.client.darkfog.flight.FlightClient.registerEvents(modBus);
        // M16 黑雾：中继站和种子（R）的注册加在这行上面
        // M16 黑雾：等离子炮塔（T）的注册加在这行上面
        io.github.sweetcornna.universe.client.darkfog.shield.ShieldClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M16 黑雾：行星护盾（S）的注册加在这行上面
        io.github.sweetcornna.universe.client.fluid.AcidClient.registerEvents(modBus);
        // M17 收尾：酸液（A）的注册加在这行上面
        // M17 收尾：奇异星球（B）的注册加在这行上面
        // M17 收尾：互通（C）的注册加在这行上面
        // M17 收尾：戴森球（D）的注册加在这行上面
        // M17 收尾：工厂物流（E）的注册加在这行上面
        io.github.sweetcornna.universe.client.ship.ShipCargoClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M17 收尾：飞船和星球（F）的注册加在这行上面
        ClientStory.registerEvents(modBus, NeoForge.EVENT_BUS);
        GuideItem.opener = () -> Minecraft.getInstance().gui.setScreen(new GuideScreen());
        BlueprintClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        GhostClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        MechaClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // 声音（ADR-0029）：机器的环境声、真空和水下的处理、给开发脚本记的日志
        io.github.sweetcornna.universe.client.audio.MachineSoundscape.registerEvents(NeoForge.EVENT_BUS);
        io.github.sweetcornna.universe.client.audio.AcousticsClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        io.github.sweetcornna.universe.client.audio.SoundLog.registerEvents(NeoForge.EVENT_BUS);
        // M17 音效：机器（M）的注册加在这行上面
        io.github.sweetcornna.universe.client.ship.ShipAudio.registerEvents(NeoForge.EVENT_BUS);
        // M17 音效：飞船和载具（S）的注册加在这行上面
        io.github.sweetcornna.universe.client.GearAudio.registerEvents(NeoForge.EVENT_BUS);
        // M17 音效：玩家的装备（G）的注册加在这行上面
        // M17 音效：敌人和炮塔（H）的注册加在这行上面
        io.github.sweetcornna.universe.client.mecha.CombatClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M18 舰队和战斗（A）的注册加在这行上面
        io.github.sweetcornna.universe.client.logistics.ControlPanelClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M18 物流调度（B）的注册加在这行上面
        // M18 统计和研究（C）的注册加在这行上面
        io.github.sweetcornna.universe.client.beacon.BeaconClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        io.github.sweetcornna.universe.client.factory.VeinAlertClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M18 信标、透镜、矿脉、蓝图（D）的注册加在这行上面
        io.github.sweetcornna.universe.client.fishing.FishingClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M19 钓鱼（A）的注册加在这行上面
        io.github.sweetcornna.universe.client.settlement.SettlementClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M19 聚落（B）的注册加在这行上面
        io.github.sweetcornna.universe.client.fossil.FossilClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M19 化石（C）的注册加在这行上面
        io.github.sweetcornna.universe.client.deepspace.DeepSpaceClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        io.github.sweetcornna.universe.client.gravity.GravityClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M19 深空和重力枪（D）的注册加在这行上面
        io.github.sweetcornna.universe.client.hazard.HazardClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M19 科技布局和局部危害（E）的注册加在这行上面
        io.github.sweetcornna.universe.client.chem.ChemistryClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M20 化学（A）的注册加在这行上面
        // M20 反应釜（B）的注册加在这行上面
        io.github.sweetcornna.universe.client.microbe.MicrobeClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M20 微生物和基因（C）的注册加在这行上面
        // M20 生物反应器（D）的注册加在这行上面
        io.github.sweetcornna.universe.client.terraform.TerraformClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // M20 星球改造（E）的注册加在这行上面
        // 开发用：带了 -Duniverse.devScript 才启用
        DevScript.registerEvents(NeoForge.EVENT_BUS);
        // 客户端世界建立环境属性时，从客户端的星团里查星球
        PlanetEnvironment.setClientGalaxy(dimension -> ClientGalaxy.galaxy());
        // 客户端的动物按当前星球的物种表画
        AlienCreature.setClientFauna(ClientGalaxy::currentFauna, ClientGalaxy::fauna);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.ALIEN_CREATURE.get(), AlienCreatureRenderer::new);
            event.registerEntityRenderer(UniverseEntities.ALIEN_FLYER.get(), AlienCreatureRenderer::new);
            event.registerEntityRenderer(UniverseEntities.AQUATIC_CREATURE.get(), AquaticCreatureRenderer::new);
            event.registerEntityRenderer(UniverseEntities.PLASMA_BOLT.get(), PlasmaBoltRenderer::new);
            event.registerEntityRenderer(UniverseEntities.PLASMA_GRENADE.get(), PlasmaGrenadeRenderer::new);
            event.registerEntityRenderer(UniverseEntities.SENTINEL.get(), SentinelRenderer::new);
            event.registerEntityRenderer(UniverseEntities.HEAVY_SENTINEL.get(), SentinelRenderer::new);
            event.registerEntityRenderer(UniverseEntities.REPAIR_SENTINEL.get(), SentinelRenderer::new);
            event.registerEntityRenderer(UniverseEntities.SUMMONER_SENTINEL.get(), SentinelRenderer::new);
            event.registerEntityRenderer(UniverseEntities.QUADRUPED_SENTINEL.get(), GroundSentinelRenderer::quadruped);
            event.registerEntityRenderer(UniverseEntities.WALKER_SENTINEL.get(), GroundSentinelRenderer::walker);
        });
        // 数据生成用的是客户端运行配置（clientData），只在客户端入口登记
        UniverseDataGen.registerEvents(modBus);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        Universe.LOGGER.info("Universe client setup done");
    }
}
