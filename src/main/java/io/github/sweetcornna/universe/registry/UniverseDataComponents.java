package io.github.sweetcornna.universe.registry;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品的数据组件。 */
public final class UniverseDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Universe.MOD_ID);

    /** 蓄电器拆下来时带着的电量（焦耳）。 */
    public static final Supplier<DataComponentType<Long>> STORED_ENERGY = COMPONENTS.registerComponentType("stored_energy",
            builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    private UniverseDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
