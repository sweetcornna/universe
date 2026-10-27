package io.github.sweetcornna.universe.factory;

import java.util.List;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.factory.fluid.FluidBridge;
import io.github.sweetcornna.universe.factory.fluid.OutputFluidHandler;
import io.github.sweetcornna.universe.factory.resource.OilExtractorBlockEntity;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 设备对外提供的能力：有物品栏的设备都提供物品能力，漏斗、分拣器和其他模组的管道都能接（ADR-0011）；喷过增产剂的原料
 * 进设备时点数记进池子（{@link MachineItemHandler}，ADR-0022）。
 *
 * <p>和别的模组的电、流体接上（ADR-0030 §三）：电网接口提供能量能力；储液罐、抽水站、原油萃取站提供流体能力，
 * 流体物品按 {@link FluidBridge} 换成真正的流体，一个算 1000 mB。
 */
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
                UniverseBlockEntities.VERTICAL_LAUNCHING_SILO, UniverseBlockEntities.RAY_RECEIVER, UniverseBlockEntities.MINIATURE_PARTICLE_COLLIDER,
                UniverseBlockEntities.MINI_FUSION_POWER_PLANT, UniverseBlockEntities.ARTIFICIAL_STAR, UniverseBlockEntities.ENERGY_EXCHANGER,
                UniverseBlockEntities.NEGENTROPY_SMELTER, UniverseBlockEntities.RECOMPOSING_ASSEMBLER, UniverseBlockEntities.QUANTUM_CHEMICAL_PLANT,
                UniverseBlockEntities.LOGISTICS_DISTRIBUTOR,
                // M20 化学（ADR-0036 §三）
                UniverseBlockEntities.ATMOSPHERIC_EXTRACTOR, UniverseBlockEntities.ELECTROLYZER
                // M20 反应釜（ADR-0036 §四）
                , UniverseBlockEntities.CHEMICAL_REACTOR
                // M20 生物反应器（ADR-0036 §五）
                , UniverseBlockEntities.BIOREACTOR
                // M20 微生物和基因（ADR-0036 §五）
                , UniverseBlockEntities.INCUBATOR, UniverseBlockEntities.SEQUENCER, UniverseBlockEntities.GENE_EDITOR
                // M20 反应釜（B）、微生物和基因（C）、生物反应器（D）、星球改造（E）的设备接在这一行前面，前面加逗号
                );
    }

    private static void register(RegisterCapabilitiesEvent event) {
        for (Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type : containers()) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), MachineItemHandler::new);
        }
        // 传送带只能往上放
        event.registerBlockEntity(Capabilities.Item.BLOCK, UniverseBlockEntities.CONVEYOR_BELT.get(), (belt, side) -> belt.insertHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, UniverseBlockEntities.STORAGE_DEPOT.get(), (depot, side) -> depot.handler());
        // 储液罐：流体是物品，只收流体（ADR-0015）
        event.registerBlockEntity(Capabilities.Item.BLOCK, UniverseBlockEntities.STORAGE_TANK.get(), (tank, side) -> tank.handler());
        // 物流站：直接读写账本里的物品格
        for (Supplier<BlockEntityType<StationBlockEntity>> type : List.of(UniverseBlockEntities.PLANETARY_LOGISTICS_STATION,
                UniverseBlockEntities.INTERSTELLAR_LOGISTICS_STATION, UniverseBlockEntities.ORBITAL_COLLECTOR)) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), (station, side) -> station.handler());
        }
        // ---- 和别的模组互通（ADR-0030 §三）
        event.registerBlockEntity(Capabilities.Energy.BLOCK, UniverseBlockEntities.GRID_INTERFACE.get(), (device, side) -> device.energyHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, UniverseBlockEntities.STORAGE_TANK.get(), (tank, side) -> tank.fluidHandler());
        // 抽水站、萃取站：产物格里的流体只出不进
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, UniverseBlockEntities.WATER_PUMP.get(),
                (pump, side) -> new OutputFluidHandler(pump, WaterPumpBlockEntity.OUTPUT));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, UniverseBlockEntities.OIL_EXTRACTOR.get(),
                (extractor, side) -> new OutputFluidHandler(extractor, OilExtractorBlockEntity.OUTPUT));
    }
}
