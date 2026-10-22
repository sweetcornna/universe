package io.github.sweetcornna.universe.registry;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.poi.RandomModuleFunction;
import io.github.sweetcornna.universe.world.OverworldVeinsFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetBiomeSource;
import io.github.sweetcornna.universe.world.planet.gen.PlanetCaveRoofFunction;
import io.github.sweetcornna.universe.world.planet.gen.PlanetFloraFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetHeightFunction;
import io.github.sweetcornna.universe.world.planet.gen.PlanetIslandFunction;
import io.github.sweetcornna.universe.world.planet.gen.PlanetLavaCondition;
import io.github.sweetcornna.universe.world.planet.gen.PlanetOreFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetPoiFeature;
import io.github.sweetcornna.universe.world.planet.gen.PlanetSteepCondition;
import io.github.sweetcornna.universe.world.freighter.FreighterChunkGenerator;
import io.github.sweetcornna.universe.world.space.SpaceChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 世界生成用的自定义类型：密度函数、群系源、地表条件、地物、太空的区块生成器，以及兴趣点用的战利品函数。 */
public final class UniverseWorldgen {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Universe.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Universe.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends MaterialCondition>> MATERIAL_CONDITION_TYPES =
            DeferredRegister.create(Registries.MATERIAL_CONDITION_TYPE, Universe.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES =
            DeferredRegister.create(Registries.FEATURE_TYPE, Universe.MOD_ID);
    /** 区块生成器：太空维度带空间站的虚空（ADR-0017），货船的船舱（ADR-0024）。 */
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Universe.MOD_ID);
    /** 战利品函数：兴趣点里随机生成模块（ADR-0014）。 */
    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> LOOT_FUNCTION_TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Universe.MOD_ID);

    static {
        DENSITY_FUNCTION_TYPES.register("planet_height", () -> PlanetHeightFunction.CODEC);
        DENSITY_FUNCTION_TYPES.register("planet_islands", () -> PlanetIslandFunction.CODEC);
        DENSITY_FUNCTION_TYPES.register("planet_cave_roof", () -> PlanetCaveRoofFunction.CODEC);
        BIOME_SOURCES.register("planet", () -> PlanetBiomeSource.CODEC);
        MATERIAL_CONDITION_TYPES.register("planet_steep", () -> PlanetSteepCondition.CODEC);
        MATERIAL_CONDITION_TYPES.register("planet_lava", () -> PlanetLavaCondition.CODEC);
        FEATURE_TYPES.register("planet_flora", () -> PlanetFloraFeature.CODEC);
        FEATURE_TYPES.register("planet_ores", () -> PlanetOreFeature.CODEC);
        FEATURE_TYPES.register("overworld_veins", () -> OverworldVeinsFeature.CODEC);
        FEATURE_TYPES.register("planet_poi", () -> PlanetPoiFeature.CODEC);
        LOOT_FUNCTION_TYPES.register("random_module", () -> RandomModuleFunction.MAP_CODEC);
        CHUNK_GENERATORS.register("space", () -> SpaceChunkGenerator.CODEC);
        CHUNK_GENERATORS.register("freighter", () -> FreighterChunkGenerator.CODEC);
    }

    private UniverseWorldgen() {
    }

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTION_TYPES.register(modBus);
        BIOME_SOURCES.register(modBus);
        MATERIAL_CONDITION_TYPES.register(modBus);
        FEATURE_TYPES.register(modBus);
        LOOT_FUNCTION_TYPES.register(modBus);
        CHUNK_GENERATORS.register(modBus);
    }
}
