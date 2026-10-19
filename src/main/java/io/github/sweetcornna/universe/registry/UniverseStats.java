package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 统计信息里本模组的条目（统计界面的"通用"一页）。 */
public final class UniverseStats {
    public static final DeferredRegister<Identifier> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, Universe.MOD_ID);

    /** 打下的海盗战机、打退的海盗袭击（ADR-0017）。 */
    public static final DeferredHolder<Identifier, Identifier> PIRATES_DESTROYED = stat("pirates_destroyed");
    public static final DeferredHolder<Identifier, Identifier> PIRATE_RAIDS_REPELLED = stat("pirate_raids_repelled");

    private UniverseStats() {
    }

    private static DeferredHolder<Identifier, Identifier> stat(String name) {
        return STATS.register(name, () -> Universe.id(name));
    }

    public static void register(IEventBus modBus) {
        STATS.register(modBus);
        // 统计项要在注册表冻结以后建出来（带上显示格式），统计界面才列得出来
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> STATS.getEntries()
                .forEach(holder -> Stats.CUSTOM.get(holder.value(), StatFormatter.DEFAULT))));
    }
}
