package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

/**
 * 数据生成（{@code ./gradlew runData}），输出到 {@code src/generated/resources}。
 *
 * <p>运行配置用的是 {@code clientData()}，只触发 {@link GatherDataEvent.Client}，所以服务端数据（标签等）也在这里登记。
 * 战利品表、群系、维度类型这些数据包注册表的条目走 {@link GatherDataRegistryEntriesEvent}。
 */
public final class UniverseDataGen {
    private UniverseDataGen() {
    }

    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(GatherDataEvent.Client.class, UniverseDataGen::gather);
        modBus.addListener(GatherDataRegistryEntriesEvent.class, UniverseDataGen::gatherRegistries);
    }

    private static void gather(GatherDataEvent.Client event) {
        event.createProvider(UniverseModelProvider::new);
        event.createProvider(UniverseLanguageProvider.Chinese::new);
        event.createProvider(UniverseLanguageProvider.English::new);
        event.createProvider(UniverseBlockTagsProvider::new);
        event.createProvider(PlanetEnvironmentProvider::new);
    }

    private static void gatherRegistries(GatherDataRegistryEntriesEvent event) {
        event.add(Registries.LOOT_TABLE, new LootTableProvider(Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(UniverseBlockLoot::new, LootContextParamSets.BLOCK))));
        event.add(Registries.BIOME, UniverseBiomes::bootstrap);
    }
}
