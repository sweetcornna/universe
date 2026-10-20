package io.github.sweetcornna.universe.registry;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.poi.SupplyCrateBlockEntity;
import io.github.sweetcornna.universe.block.ship.FuelTankBlockEntity;
import io.github.sweetcornna.universe.dyson.EmRailEjectorBlockEntity;
import io.github.sweetcornna.universe.dyson.LaunchSiloBlockEntity;
import io.github.sweetcornna.universe.dyson.RayReceiverBlockEntity;
import io.github.sweetcornna.universe.factory.FactoryAnchorBlockEntity;
import io.github.sweetcornna.universe.factory.FractionatorBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachines;
import io.github.sweetcornna.universe.factory.belt.BeltBlockEntity;
import io.github.sweetcornna.universe.factory.belt.SorterBlockEntity;
import io.github.sweetcornna.universe.factory.belt.SplitterBlockEntity;
import io.github.sweetcornna.universe.factory.belt.StorageDepotBlockEntity;
import io.github.sweetcornna.universe.factory.belt.StorageTankBlockEntity;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.SolarPanelBlockEntity;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.resource.MiningMachineBlockEntity;
import io.github.sweetcornna.universe.factory.resource.OilExtractorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.teleport.TeleporterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块实体注册。 */
public final class UniverseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Universe.MOD_ID);

    public static final Supplier<BlockEntityType<FuelTankBlockEntity>> FUEL_TANK = BLOCK_ENTITIES.register("fuel_tank",
            () -> new BlockEntityType<>(FuelTankBlockEntity::new, UniverseBlocks.FUEL_TANK.get(), UniverseBlocks.FUEL_TANK_MK2.get()));
    public static final Supplier<BlockEntityType<SupplyCrateBlockEntity>> SUPPLY_CRATE = BLOCK_ENTITIES.register("supply_crate",
            () -> new BlockEntityType<>(SupplyCrateBlockEntity::new, UniverseBlocks.SUPPLY_CRATE.get()));

    // ---- 工业
    public static final Supplier<BlockEntityType<TeslaTowerBlockEntity>> TESLA_TOWER = BLOCK_ENTITIES.register("tesla_tower",
            () -> new BlockEntityType<>(TeslaTowerBlockEntity::new, UniverseBlocks.TESLA_TOWER.get()));
    public static final Supplier<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE = BLOCK_ENTITIES.register("wind_turbine",
            () -> new BlockEntityType<>(WindTurbineBlockEntity::new, UniverseBlocks.WIND_TURBINE.get()));
    public static final Supplier<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL = BLOCK_ENTITIES.register("solar_panel",
            () -> new BlockEntityType<>(SolarPanelBlockEntity::new, UniverseBlocks.SOLAR_PANEL.get()));
    public static final Supplier<BlockEntityType<ThermalPlantBlockEntity>> THERMAL_PLANT = BLOCK_ENTITIES.register("thermal_plant",
            () -> new BlockEntityType<>(ThermalPlantBlockEntity::new, UniverseBlocks.THERMAL_PLANT.get()));
    public static final Supplier<BlockEntityType<TeleporterBlockEntity>> TELEPORTER = BLOCK_ENTITIES.register("teleporter",
            () -> new BlockEntityType<>(TeleporterBlockEntity::new, UniverseBlocks.TELEPORTER.get(), UniverseBlocks.STATION_TELEPORTER.get()));
    public static final Supplier<BlockEntityType<AccumulatorBlockEntity>> ACCUMULATOR = BLOCK_ENTITIES.register("accumulator",
            () -> new BlockEntityType<>(AccumulatorBlockEntity::new, UniverseBlocks.ACCUMULATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> ARC_SMELTER = recipeMachine(
            "arc_smelter", UniverseBlocks.ARC_SMELTER, RecipeMachines.ARC_SMELTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> ASSEMBLER = recipeMachine(
            "assembler", UniverseBlocks.ASSEMBLER, RecipeMachines.ASSEMBLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> OIL_REFINERY = recipeMachine(
            "oil_refinery", UniverseBlocks.OIL_REFINERY, RecipeMachines.OIL_REFINERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> ASSEMBLER_MK2 = recipeMachine(
            "assembler_mk2", UniverseBlocks.ASSEMBLER_MK2, RecipeMachines.ASSEMBLER_MK2);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> ASSEMBLER_MK3 = recipeMachine(
            "assembler_mk3", UniverseBlocks.ASSEMBLER_MK3, RecipeMachines.ASSEMBLER_MK3);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> PLANE_SMELTER = recipeMachine(
            "plane_smelter", UniverseBlocks.PLANE_SMELTER, RecipeMachines.PLANE_SMELTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> CHEMICAL_PLANT = recipeMachine(
            "chemical_plant", UniverseBlocks.CHEMICAL_PLANT, RecipeMachines.CHEMICAL_PLANT);
    public static final Supplier<BlockEntityType<FractionatorBlockEntity>> FRACTIONATOR = BLOCK_ENTITIES.register("fractionator",
            () -> new BlockEntityType<>(FractionatorBlockEntity::new, UniverseBlocks.FRACTIONATOR.get()));
    public static final Supplier<BlockEntityType<MiningMachineBlockEntity>> ADVANCED_MINING_MACHINE = BLOCK_ENTITIES.register(
            "advanced_mining_machine", () -> new BlockEntityType<>(MiningMachineBlockEntity::advanced, UniverseBlocks.ADVANCED_MINING_MACHINE.get()));
    public static final Supplier<BlockEntityType<FactoryAnchorBlockEntity>> FACTORY_ANCHOR = BLOCK_ENTITIES.register("factory_anchor",
            () -> new BlockEntityType<>(FactoryAnchorBlockEntity::new, UniverseBlocks.FACTORY_ANCHOR.get()));
    public static final Supplier<BlockEntityType<MiningMachineBlockEntity>> MINING_MACHINE = BLOCK_ENTITIES.register("mining_machine",
            () -> new BlockEntityType<>(MiningMachineBlockEntity::new, UniverseBlocks.MINING_MACHINE.get()));
    public static final Supplier<BlockEntityType<WaterPumpBlockEntity>> WATER_PUMP = BLOCK_ENTITIES.register("water_pump",
            () -> new BlockEntityType<>(WaterPumpBlockEntity::new, UniverseBlocks.WATER_PUMP.get()));
    public static final Supplier<BlockEntityType<OilExtractorBlockEntity>> OIL_EXTRACTOR = BLOCK_ENTITIES.register("oil_extractor",
            () -> new BlockEntityType<>(OilExtractorBlockEntity::new, UniverseBlocks.OIL_EXTRACTOR.get()));
    public static final Supplier<BlockEntityType<MatrixLabBlockEntity>> MATRIX_LAB = BLOCK_ENTITIES.register("matrix_lab",
            () -> new BlockEntityType<>(MatrixLabBlockEntity::new, UniverseBlocks.MATRIX_LAB.get()));

    public static final Supplier<BlockEntityType<BeltBlockEntity>> CONVEYOR_BELT = BLOCK_ENTITIES.register("conveyor_belt",
            () -> new BlockEntityType<>(BeltBlockEntity::new, UniverseBlocks.CONVEYOR_BELT.get(), UniverseBlocks.CONVEYOR_BELT_MK2.get(),
                    UniverseBlocks.CONVEYOR_BELT_MK3.get()));
    public static final Supplier<BlockEntityType<SorterBlockEntity>> SORTER = BLOCK_ENTITIES.register("sorter",
            () -> new BlockEntityType<>(SorterBlockEntity::new, UniverseBlocks.SORTER.get(), UniverseBlocks.SORTER_MK2.get(),
                    UniverseBlocks.SORTER_MK3.get()));
    public static final Supplier<BlockEntityType<SplitterBlockEntity>> SPLITTER = BLOCK_ENTITIES.register("splitter",
            () -> new BlockEntityType<>(SplitterBlockEntity::new, UniverseBlocks.SPLITTER.get()));
    public static final Supplier<BlockEntityType<StorageDepotBlockEntity>> STORAGE_DEPOT = BLOCK_ENTITIES.register("storage_depot",
            () -> new BlockEntityType<>(StorageDepotBlockEntity::new, UniverseBlocks.STORAGE_DEPOT.get()));
    public static final Supplier<BlockEntityType<StorageTankBlockEntity>> STORAGE_TANK = BLOCK_ENTITIES.register("storage_tank",
            () -> new BlockEntityType<>(StorageTankBlockEntity::new, UniverseBlocks.STORAGE_TANK.get()));
    // 物流站（ADR-0015）
    public static final Supplier<BlockEntityType<StationBlockEntity>> PLANETARY_LOGISTICS_STATION = BLOCK_ENTITIES.register(
            "planetary_logistics_station",
            () -> new BlockEntityType<>(StationBlockEntity::planetary, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get()));
    public static final Supplier<BlockEntityType<StationBlockEntity>> INTERSTELLAR_LOGISTICS_STATION = BLOCK_ENTITIES.register(
            "interstellar_logistics_station",
            () -> new BlockEntityType<>(StationBlockEntity::interstellar, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get()));
    public static final Supplier<BlockEntityType<StationBlockEntity>> ORBITAL_COLLECTOR = BLOCK_ENTITIES.register("orbital_collector",
            () -> new BlockEntityType<>(StationBlockEntity::collector, UniverseBlocks.ORBITAL_COLLECTOR.get()));
    // 戴森球（ADR-0016）
    public static final Supplier<BlockEntityType<EmRailEjectorBlockEntity>> EM_RAIL_EJECTOR = BLOCK_ENTITIES.register("em_rail_ejector",
            () -> new BlockEntityType<>(EmRailEjectorBlockEntity::new, UniverseBlocks.EM_RAIL_EJECTOR.get()));
    public static final Supplier<BlockEntityType<LaunchSiloBlockEntity>> VERTICAL_LAUNCHING_SILO = BLOCK_ENTITIES.register(
            "vertical_launching_silo", () -> new BlockEntityType<>(LaunchSiloBlockEntity::new, UniverseBlocks.VERTICAL_LAUNCHING_SILO.get()));
    public static final Supplier<BlockEntityType<RayReceiverBlockEntity>> RAY_RECEIVER = BLOCK_ENTITIES.register("ray_receiver",
            () -> new BlockEntityType<>(RayReceiverBlockEntity::new, UniverseBlocks.RAY_RECEIVER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> MINIATURE_PARTICLE_COLLIDER = recipeMachine(
            "miniature_particle_collider", UniverseBlocks.MINIATURE_PARTICLE_COLLIDER, RecipeMachines.MINIATURE_PARTICLE_COLLIDER);

    private UniverseBlockEntities() {
    }

    /** 配方设备共用一个方块实体类，各自的规格不同。 */
    private static DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> recipeMachine(String name,
            Supplier<? extends Block> block, RecipeMachineBlockEntity.Spec spec) {
        DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> self =
                DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, Universe.id(name));
        BLOCK_ENTITIES.register(name, () -> new BlockEntityType<>(
                (pos, state) -> new RecipeMachineBlockEntity(self.get(), pos, state, spec), block.get()));
        return self;
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
