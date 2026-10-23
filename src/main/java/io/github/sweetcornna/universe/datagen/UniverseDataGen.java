package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.entity.PlasmaBolt;
import io.github.sweetcornna.universe.world.planet.gen.PlanetExoticFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetLandmarkFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetPoiFeature;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.survival.SurvivalDamage;
import io.github.sweetcornna.universe.world.OverworldVeinsFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetFloraFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetOreFeature;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

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
        event.createProvider(UniverseItemTagsProvider::new);
        event.createProvider(UniverseDamageTypeTagsProvider::new);
        event.createProvider(PlanetEnvironmentProvider::new);
        // M17 收尾：酸液（A）的数据生成加在这行上面
        event.createProvider(UniverseFluidTagsProvider::new);
        // M17 收尾：互通（C）的数据生成加在这行上面
    }

    private static void gatherRegistries(GatherDataRegistryEntriesEvent event) {
        event.add(Registries.LOOT_TABLE, new LootTableProvider(Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(UniverseBlockLoot::new, LootContextParamSets.BLOCK),
                        new LootTableProvider.SubProviderEntry(UniverseEntityLoot::new, LootContextParamSets.ENTITY),
                        new LootTableProvider.SubProviderEntry(UniverseChestLoot::new, LootContextParamSets.CHEST))));
        event.recipe(UniverseRecipes::new);
        event.add(Registries.ADVANCEMENT, new AdvancementProvider(List.of(UniverseAdvancements::new)));
        event.add(Registries.FEATURE, context -> {
            context.register(PlanetFloraFeature.KEY, new PlanetFloraFeature());
            context.register(PlanetOreFeature.KEY, new PlanetOreFeature());
            context.register(OverworldVeinsFeature.KEY, new OverworldVeinsFeature());
            context.register(PlanetPoiFeature.KEY, new PlanetPoiFeature());
            context.register(PlanetLandmarkFeature.KEY, new PlanetLandmarkFeature());
            context.register(PlanetExoticFeature.KEY, new PlanetExoticFeature());
            // M17 收尾：奇异星球（B）的地物加在这行上面
        });
        event.add(Registries.PLACED_FEATURE, context -> {
            context.register(PlanetFloraFeature.PLACED,
                    new PlacedFeature(context.lookup(Registries.FEATURE).getOrThrow(PlanetFloraFeature.KEY), List.of(BiomeFilter.biome())));
            context.register(PlanetOreFeature.PLACED,
                    new PlacedFeature(context.lookup(Registries.FEATURE).getOrThrow(PlanetOreFeature.KEY), List.of(BiomeFilter.biome())));
            context.register(OverworldVeinsFeature.PLACED,
                    new PlacedFeature(context.lookup(Registries.FEATURE).getOrThrow(OverworldVeinsFeature.KEY), List.of(BiomeFilter.biome())));
            context.register(PlanetPoiFeature.PLACED,
                    new PlacedFeature(context.lookup(Registries.FEATURE).getOrThrow(PlanetPoiFeature.KEY), List.of(BiomeFilter.biome())));
            context.register(PlanetLandmarkFeature.PLACED,
                    new PlacedFeature(context.lookup(Registries.FEATURE).getOrThrow(PlanetLandmarkFeature.KEY), List.of(BiomeFilter.biome())));
            context.register(PlanetExoticFeature.PLACED,
                    new PlacedFeature(context.lookup(Registries.FEATURE).getOrThrow(PlanetExoticFeature.KEY), List.of(BiomeFilter.biome())));
            // M17 收尾：奇异星球（B）的放置的地物加在这行上面
        });
        // 主世界的矿脉露头和原油涌泉在最后一步放，压在树和草之后
        event.add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, context -> context.register(
                ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Universe.id("overworld_veins")),
                new BiomeModifiers.AddFeaturesBiomeModifier(context.lookup(Registries.BIOME).getOrThrow(BiomeTags.IS_OVERWORLD),
                        HolderSet.direct(context.lookup(Registries.PLACED_FEATURE).getOrThrow(OverworldVeinsFeature.PLACED)),
                        GenerationStep.Decoration.TOP_LAYER_MODIFICATION)));
        event.add(Registries.BIOME, UniverseBiomes::bootstrap);
        event.add(Registries.DAMAGE_TYPE, context -> {
            SurvivalDamage.bootstrap(context);
            PlasmaBolt.bootstrapDamage(context);
            io.github.sweetcornna.universe.darkfog.entity.DarkFogDamage.bootstrap(context);
        });
    }
}
