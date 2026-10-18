package io.github.sweetcornna.universe.registry;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.ship.FuelTankBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachines;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlockEntity;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlockEntity;
import io.github.sweetcornna.universe.factory.power.WindTurbineBlockEntity;
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
            () -> new BlockEntityType<>(FuelTankBlockEntity::new, UniverseBlocks.FUEL_TANK.get()));

    // ---- 工业
    public static final Supplier<BlockEntityType<TeslaTowerBlockEntity>> TESLA_TOWER = BLOCK_ENTITIES.register("tesla_tower",
            () -> new BlockEntityType<>(TeslaTowerBlockEntity::new, UniverseBlocks.TESLA_TOWER.get()));
    public static final Supplier<BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE = BLOCK_ENTITIES.register("wind_turbine",
            () -> new BlockEntityType<>(WindTurbineBlockEntity::new, UniverseBlocks.WIND_TURBINE.get()));
    public static final Supplier<BlockEntityType<ThermalPlantBlockEntity>> THERMAL_PLANT = BLOCK_ENTITIES.register("thermal_plant",
            () -> new BlockEntityType<>(ThermalPlantBlockEntity::new, UniverseBlocks.THERMAL_PLANT.get()));
    public static final Supplier<BlockEntityType<AccumulatorBlockEntity>> ACCUMULATOR = BLOCK_ENTITIES.register("accumulator",
            () -> new BlockEntityType<>(AccumulatorBlockEntity::new, UniverseBlocks.ACCUMULATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> ARC_SMELTER = recipeMachine(
            "arc_smelter", UniverseBlocks.ARC_SMELTER, RecipeMachines.ARC_SMELTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeMachineBlockEntity>> ASSEMBLER = recipeMachine(
            "assembler", UniverseBlocks.ASSEMBLER, RecipeMachines.ASSEMBLER);

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
