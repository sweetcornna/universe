package io.github.sweetcornna.universe;

import io.github.sweetcornna.universe.client.ClientDimensions;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.datagen.UniverseDataGen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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
        PlanetTints.registerEvents(modBus);
        // 数据生成用的是客户端运行配置（clientData），只在客户端入口登记
        UniverseDataGen.registerEvents(modBus);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        Universe.LOGGER.info("Universe client setup done");
    }
}
