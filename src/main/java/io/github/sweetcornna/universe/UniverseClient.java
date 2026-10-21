package io.github.sweetcornna.universe;

import io.github.sweetcornna.universe.client.ClientConfig;
import io.github.sweetcornna.universe.client.ItemSummaries;
import io.github.sweetcornna.universe.client.asteroid.AsteroidClient;
import io.github.sweetcornna.universe.client.blueprint.BlueprintClient;
import io.github.sweetcornna.universe.client.blueprint.GhostClient;
import io.github.sweetcornna.universe.client.mecha.MechaClient;
import io.github.sweetcornna.universe.client.dev.DevScript;
import io.github.sweetcornna.universe.client.exocraft.ExocraftClient;
import io.github.sweetcornna.universe.client.guide.GuideScreen;
import io.github.sweetcornna.universe.client.pirate.PirateClient;
import io.github.sweetcornna.universe.client.sentinel.SentinelClient;
import io.github.sweetcornna.universe.client.sentinel.SentinelRenderer;
import io.github.sweetcornna.universe.client.PlasmaBoltRenderer;
import io.github.sweetcornna.universe.client.ClientDimensions;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.ClientWeather;
import io.github.sweetcornna.universe.client.MultitoolClient;
import io.github.sweetcornna.universe.client.SuitClient;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.client.creature.AlienCreatureRenderer;
import io.github.sweetcornna.universe.client.discovery.AnalysisVisor;
import io.github.sweetcornna.universe.client.discovery.ClientDiscoveries;
import io.github.sweetcornna.universe.client.dyson.ClientDyson;
import io.github.sweetcornna.universe.client.factory.FactoryClient;
import io.github.sweetcornna.universe.client.galaxy.StarMapClient;
import io.github.sweetcornna.universe.client.galaxy.WarpClient;
import io.github.sweetcornna.universe.client.mission.MissionClient;
import io.github.sweetcornna.universe.client.ship.ShipClient;
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
        AsteroidClient.registerEvents(modBus);
        ExocraftClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        ItemSummaries.registerEvents(NeoForge.EVENT_BUS);
        PoiClient.registerEvents(modBus);
        SentinelClient.registerEvents(modBus);
        TeleportClient.registerEvents(modBus);
        MissionClient.registerEvents(modBus);
        ClientStory.registerEvents(modBus, NeoForge.EVENT_BUS);
        GuideItem.opener = () -> Minecraft.getInstance().gui.setScreen(new GuideScreen());
        BlueprintClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        GhostClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        MechaClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // 开发用：带了 -Duniverse.devScript 才启用
        DevScript.registerEvents(NeoForge.EVENT_BUS);
        // 客户端世界建立环境属性时，从客户端的星团里查星球
        PlanetEnvironment.setClientGalaxy(dimension -> ClientGalaxy.galaxy());
        // 客户端的动物按当前星球的物种表画
        AlienCreature.setClientFauna(ClientGalaxy::currentFauna, ClientGalaxy::fauna);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.ALIEN_CREATURE.get(), AlienCreatureRenderer::new);
            event.registerEntityRenderer(UniverseEntities.ALIEN_FLYER.get(), AlienCreatureRenderer::new);
            event.registerEntityRenderer(UniverseEntities.PLASMA_BOLT.get(), PlasmaBoltRenderer::new);
            event.registerEntityRenderer(UniverseEntities.SENTINEL.get(), SentinelRenderer::new);
            event.registerEntityRenderer(UniverseEntities.HEAVY_SENTINEL.get(), SentinelRenderer::new);
        });
        // 数据生成用的是客户端运行配置（clientData），只在客户端入口登记
        UniverseDataGen.registerEvents(modBus);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        Universe.LOGGER.info("Universe client setup done");
    }
}
