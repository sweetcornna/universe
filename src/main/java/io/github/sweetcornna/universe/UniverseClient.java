package io.github.sweetcornna.universe;

import io.github.sweetcornna.universe.client.ClientDimensions;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.ClientWeather;
import io.github.sweetcornna.universe.client.MultitoolClient;
import io.github.sweetcornna.universe.client.SuitClient;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.client.creature.AlienCreatureRenderer;
import io.github.sweetcornna.universe.client.factory.FactoryClient;
import io.github.sweetcornna.universe.client.ship.ShipClient;
import io.github.sweetcornna.universe.client.sky.PlanetSkyRenderer;
import io.github.sweetcornna.universe.datagen.UniverseDataGen;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.world.planet.PlanetEnvironment;
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
        modBus.addListener(this::clientSetup);
        ClientDimensions.registerEvents(modBus, NeoForge.EVENT_BUS);
        ClientGalaxy.registerEvents(modBus, NeoForge.EVENT_BUS);
        ClientWeather.registerEvents(modBus, NeoForge.EVENT_BUS);
        PlanetTints.registerEvents(modBus);
        PlanetSkyRenderer.registerEvents(modBus, NeoForge.EVENT_BUS);
        ShipClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        SuitClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        MultitoolClient.registerEvents(modBus);
        FactoryClient.registerEvents(modBus, NeoForge.EVENT_BUS);
        // 客户端世界建立环境属性时，从客户端的星团里查星球
        PlanetEnvironment.setClientGalaxy(dimension -> ClientGalaxy.galaxy());
        // 客户端的动物按当前星球的物种表画
        AlienCreature.setClientFauna(ClientGalaxy::currentFauna);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(UniverseEntities.ALIEN_CREATURE.get(), AlienCreatureRenderer::new);
            event.registerEntityRenderer(UniverseEntities.ALIEN_FLYER.get(), AlienCreatureRenderer::new);
        });
        // 数据生成用的是客户端运行配置（clientData），只在客户端入口登记
        UniverseDataGen.registerEvents(modBus);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        Universe.LOGGER.info("Universe client setup done");
    }
}
