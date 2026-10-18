package io.github.sweetcornna.universe.datagen;

import java.util.List;

import io.github.sweetcornna.universe.world.planet.gen.PlanetPoiFeature;
import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomes;
import io.github.sweetcornna.universe.world.planet.gen.PlanetFloraFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetOreFeature;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.CavePlacements;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 星球群系（数据包注册表条目）。
 *
 * <p>所有群系按同一个顺序登记地物，只是各自跳过不需要的：原版要求同一个群系源里的群系地物顺序一致，否则报"地物顺序成环"。
 * 颜色不在这里定，客户端按星球调色板替换。植被由程序化植物地物负责，不用原版的花草树木。
 */
final class UniverseBiomes {
    private static final int DEFAULT_WATER = 0x3F76E4;

    private UniverseBiomes() {
    }

    static void bootstrap(BootstrapContext<Biome> context) {
        HolderGetter<PlacedFeature> placed = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<WorldCarver> carvers = context.lookup(Registries.CARVER);
        for (BiomeKind kind : BiomeKind.values()) {
            context.register(PlanetBiomes.key(kind), biome(kind, placed, carvers));
        }
    }

    private static Biome biome(BiomeKind kind, HolderGetter<PlacedFeature> placed, HolderGetter<WorldCarver> carvers) {
        BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(placed, carvers);
        generation.addFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND);
        if (volcanic(kind)) {
            generation.addFeature(GenerationStep.Decoration.LAKES, MiscOverworldPlacements.LAKE_LAVA_SURFACE);
        }
        generation.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, CavePlacements.AMETHYST_GEODE);
        if (kind == BiomeKind.FROZEN_OCEAN) {
            generation.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, MiscOverworldPlacements.ICEBERG_PACKED);
            generation.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, MiscOverworldPlacements.ICEBERG_BLUE);
        }
        for (ResourceKey<PlacedFeature> ore : ORES) {
            generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, ore);
        }
        // 铁、铜、煤和外星矿物按星球的资源表生成，换成星球矿石
        generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, PlanetOreFeature.PLACED);
        generation.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, PlanetFloraFeature.PLACED);
        // 兴趣点在植物之后放，建筑会清掉占地范围里的植物（ADR-0014）
        generation.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, PlanetPoiFeature.PLACED);
        generation.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, MiscOverworldPlacements.FREEZE_TOP_LAYER);
        return new Biome.BiomeBuilder()
                .hasPrecipitation(kind.precipitation())
                .temperature(kind.temperature())
                .downfall(kind.downfall())
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(DEFAULT_WATER).build())
                .generationSettings(generation.build())
                .build();
    }

    private static boolean volcanic(BiomeKind kind) {
        return kind == BiomeKind.ASH_WASTES || kind == BiomeKind.BASALT_FIELDS || kind == BiomeKind.LAVA_SEA;
    }

    /** 原版矿石：矿石替换规则认 {@code stone_ore_replaceables} 标签，星岩已加进去。以后换成按星球资源生成的星球矿石。 */
    private static final List<ResourceKey<PlacedFeature>> ORES = List.of(
            OrePlacements.ORE_GRAVEL,
            OrePlacements.ORE_GOLD,
            OrePlacements.ORE_GOLD_LOWER,
            OrePlacements.ORE_REDSTONE,
            OrePlacements.ORE_REDSTONE_LOWER,
            OrePlacements.ORE_DIAMOND,
            OrePlacements.ORE_DIAMOND_MEDIUM,
            OrePlacements.ORE_DIAMOND_LARGE,
            OrePlacements.ORE_DIAMOND_BURIED,
            OrePlacements.ORE_LAPIS,
            OrePlacements.ORE_LAPIS_BURIED);
}
