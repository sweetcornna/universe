package io.github.sweetcornna.universe.client.factory;

import io.github.sweetcornna.universe.client.logistics.LogisticsClient;
import io.github.sweetcornna.universe.client.logistics.DistributorScreen;
import io.github.sweetcornna.universe.client.logistics.StationScreen;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachines;
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
            event.registerBlockEntityRenderer(UniverseBlockEntities.SOLAR_PANEL.get(), SolarPanelRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.CONVEYOR_BELT.get(), BeltRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.SORTER.get(), SorterRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.SPLITTER.get(), SplitterRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.MATRIX_LAB.get(), MatrixLabRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.STORAGE_TANK.get(), StorageTankRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.ARTIFICIAL_STAR.get(), ArtificialStarRenderer::new);
            event.registerBlockEntityRenderer(UniverseBlockEntities.HYDROPONIC_TRAY.get(), HydroponicTrayRenderer::new);
        });
        modBus.addListener(ModelEvent.RegisterStandalone.class, WindTurbineRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, SolarPanelRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, SorterRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, SplitterRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, MatrixLabRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterStandalone.class, ArtificialStarRenderer::registerModels);
        ClientResearch.registerEvents(modBus, gameBus);
        LogisticsClient.registerEvents(modBus, gameBus);
        BeltClient.registerEvents(modBus);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(UniverseMenus.TESLA_TOWER.menuType(), PowerScreens.TeslaTower::new);
        event.register(UniverseMenus.WIND_TURBINE.menuType(), PowerScreens.WindTurbine::new);
        event.register(UniverseMenus.SOLAR_PANEL.menuType(), PowerScreens.SolarPanel::new);
        event.register(UniverseMenus.THERMAL_PLANT.menuType(), PowerScreens.ThermalPlant::new);
        event.register(UniverseMenus.ACCUMULATOR.menuType(), PowerScreens.Accumulator::new);
        event.register(UniverseMenus.MINI_FUSION_POWER_PLANT.menuType(), PowerScreens.FuelGenerator::new);
        event.register(UniverseMenus.ARTIFICIAL_STAR.menuType(), PowerScreens.FuelGenerator::new);
        event.register(UniverseMenus.GEOTHERMAL_POWER_STATION.menuType(), PowerScreens.Geothermal::new);
        event.register(UniverseMenus.ENERGY_EXCHANGER.menuType(), PowerScreens.EnergyExchanger::new);
        // 电网接口（ADR-0030 §三）
        event.register(UniverseMenus.GRID_INTERFACE.menuType(), GridInterfaceScreen::new);
        recipeMachine(event, RecipeMachines.ARC_SMELTER);
        event.<MachineMenu, RecipeMachineScreen>register(RecipeMachines.NUTRIENT_PROCESSOR.menu().menuType(),
                (menu, inventory, title) -> new FuelRecipeMachineScreen(menu, inventory, title, RecipeMachines.NUTRIENT_PROCESSOR));
        event.<MachineMenu, RecipeMachineScreen>register(RecipeMachines.PORTABLE_REFINER.menu().menuType(),
                (menu, inventory, title) -> new FuelRecipeMachineScreen(menu, inventory, title, RecipeMachines.PORTABLE_REFINER));
        recipeMachine(event, RecipeMachines.ASSEMBLER);
        recipeMachine(event, RecipeMachines.OIL_REFINERY);
        recipeMachine(event, RecipeMachines.ASSEMBLER_MK2);
        recipeMachine(event, RecipeMachines.ASSEMBLER_MK3);
        recipeMachine(event, RecipeMachines.PLANE_SMELTER);
        recipeMachine(event, RecipeMachines.CHEMICAL_PLANT);
        recipeMachine(event, RecipeMachines.MINIATURE_PARTICLE_COLLIDER);
        recipeMachine(event, RecipeMachines.NEGENTROPY_SMELTER);
        recipeMachine(event, RecipeMachines.RECOMPOSING_ASSEMBLER);
        recipeMachine(event, RecipeMachines.QUANTUM_CHEMICAL_PLANT);
        event.register(UniverseMenus.FRACTIONATOR.menuType(), ResourceScreens.Fractionator::new);
        event.register(UniverseMenus.MINING_MACHINE.menuType(), ResourceScreens.MiningMachine::new);
        event.register(UniverseMenus.WATER_PUMP.menuType(), ResourceScreens.WaterPump::new);
        event.register(UniverseMenus.OIL_EXTRACTOR.menuType(), ResourceScreens.OilExtractor::new);
        event.register(UniverseMenus.STORAGE_DEPOT.menuType(), StorageDepotScreen::new);
        event.register(UniverseMenus.PLANETARY_LOGISTICS_STATION.menuType(), StationScreen::new);
        event.register(UniverseMenus.INTERSTELLAR_LOGISTICS_STATION.menuType(), StationScreen::new);
        event.register(UniverseMenus.ORBITAL_COLLECTOR.menuType(), StationScreen::new);
        event.register(UniverseMenus.LOGISTICS_DISTRIBUTOR.menuType(), DistributorScreen::new);
        event.register(UniverseMenus.MATRIX_LAB.menuType(), MatrixLabScreen::new);
        event.register(UniverseMenus.TRAFFIC_MONITOR.menuType(), TrafficMonitorScreen::new);
    }

    private static void recipeMachine(RegisterMenuScreensEvent event, RecipeMachineBlockEntity.Spec spec) {
        event.<MachineMenu, RecipeMachineScreen>register(spec.menu().menuType(),
                (menu, inventory, title) -> new RecipeMachineScreen(menu, inventory, title, spec));
    }
}
