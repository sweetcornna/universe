package io.github.sweetcornna.universe.factory;

import java.util.List;
import java.util.function.Supplier;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

/** 设备对外提供的能力：有物品栏的设备都提供物品能力，漏斗、分拣器和其他模组的管道都能接（ADR-0011）。 */
public final class FactoryCapabilities {
    private FactoryCapabilities() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(RegisterCapabilitiesEvent.class, FactoryCapabilities::register);
    }

    private static List<Supplier<? extends BlockEntityType<? extends MachineBlockEntity>>> containers() {
        return List.of(UniverseBlockEntities.THERMAL_PLANT, UniverseBlockEntities.ARC_SMELTER, UniverseBlockEntities.ASSEMBLER);
    }

    private static void register(RegisterCapabilitiesEvent event) {
        for (Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type : containers()) {
            event.registerBlockEntity(Capabilities.Item.BLOCK, type.get(), WorldlyContainerWrapper::new);
        }
    }
}
