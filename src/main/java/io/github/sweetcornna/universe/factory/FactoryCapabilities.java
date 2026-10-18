package io.github.sweetcornna.universe.factory;

import java.util.List;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/** 设备对外提供的能力：有物品栏的设备都提供物品能力，漏斗、分拣器和其他模组的管道都能接（ADR-0011）。 */
public final class FactoryCapabilities {
    private FactoryCapabilities() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, FactoryCapabilities::register);
    }

    private static List<Supplier<? extends BlockEntityType<? extends MachineBlockEntity>>> containers() {
        return List.of(UniverseBlockEntities.THERMAL_PLANT, UniverseBlockEntities.ARC_SMELTER, UniverseBlockEntities.ASSEMBLER,
                UniverseBlockEntities.OIL_REFINERY, UniverseBlockEntities.MINING_MACHINE, UniverseBlockEntities.WATER_PUMP,
                UniverseBlockEntities.OIL_EXTRACTOR, UniverseBlockEntities.MATRIX_LAB, UniverseBlockEntities.ASSEMBLER_MK2,
                UniverseBlockEntities.ASSEMBLER_MK3, UniverseBlockEntities.PLANE_SMELTER, UniverseBlockEntities.CHEMICAL_PLANT,
                UniverseBlockEntities.FRACTIONATOR, UniverseBlockEntities.ADVANCED_MINING_MACHINE, UniverseBlockEntities.EM_RAIL_EJECTOR,
                UniverseBlockEntities.VERTICAL_LAUNCHING_SILO, UniverseBlockEntities.RAY_RECEIVER, UniverseBlockEntities.MINIATURE_PARTICLE_COLLIDER);
    }

    private static void register(RegisterCapabilitiesEvent event) {
        for (Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type : containers()) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), WorldlyContainerWrapper::new);
        }
        // 传送带只能往上放
        event.registerBlockEntity(Capabilities.Item.BLOCK, UniverseBlockEntities.CONVEYOR_BELT.get(), (belt, side) -> belt.insertHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, UniverseBlockEntities.STORAGE_DEPOT.get(), (depot, side) -> VanillaContainerWrapper.of(depot));
        // 储液罐：流体是物品，只收流体（ADR-0015）
        event.registerBlockEntity(Capabilities.Item.BLOCK, UniverseBlockEntities.STORAGE_TANK.get(), (tank, side) -> tank.handler());
        // 物流站：直接读写账本里的物品格
        for (Supplier<BlockEntityType<StationBlockEntity>> type : List.of(UniverseBlockEntities.PLANETARY_LOGISTICS_STATION,
                UniverseBlockEntities.INTERSTELLAR_LOGISTICS_STATION, UniverseBlockEntities.ORBITAL_COLLECTOR)) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), (station, side) -> station.handler());
        }
    }
}
