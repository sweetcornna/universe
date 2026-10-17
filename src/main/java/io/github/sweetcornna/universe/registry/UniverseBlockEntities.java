package io.github.sweetcornna.universe.registry;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.ship.FuelTankBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块实体注册。 */
public final class UniverseBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Universe.MOD_ID);

    public static final Supplier<BlockEntityType<FuelTankBlockEntity>> FUEL_TANK = BLOCK_ENTITIES.register("fuel_tank",
            () -> new BlockEntityType<>(FuelTankBlockEntity::new, UniverseBlocks.FUEL_TANK.get()));

    private UniverseBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
