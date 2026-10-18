package io.github.sweetcornna.universe.client.factory;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 工业设备的客户端部分：界面、方块实体渲染器、单独加载的模型。 */
public final class FactoryClient {
    private FactoryClient() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterMenuScreensEvent.class, FactoryClient::registerScreens);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(UniverseBlockEntities.WIND_TURBINE.get(), WindTurbineRenderer::new);
        });
        modBus.addListener(ModelEvent.RegisterStandalone.class, WindTurbineRenderer::registerModels);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(UniverseMenus.TESLA_TOWER.menuType(), PowerScreens.TeslaTower::new);
        event.register(UniverseMenus.WIND_TURBINE.menuType(), PowerScreens.WindTurbine::new);
        event.register(UniverseMenus.THERMAL_PLANT.menuType(), PowerScreens.ThermalPlant::new);
        event.register(UniverseMenus.ACCUMULATOR.menuType(), PowerScreens.Accumulator::new);
    }
}
