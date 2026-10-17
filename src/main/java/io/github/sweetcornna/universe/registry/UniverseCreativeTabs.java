package io.github.sweetcornna.universe.registry;

import java.util.function.Supplier;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 创造模式物品栏。 */
public final class UniverseCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Universe.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.universe"))
            .icon(() -> new ItemStack(UniverseItems.PLANET_CRYSTAL.get()))
            .displayItems((parameters, output) -> UniverseItems.CREATIVE_ORDER.forEach(item -> output.accept(item.get())))
            .build());

    private UniverseCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
