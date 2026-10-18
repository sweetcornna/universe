package io.github.sweetcornna.universe.world;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 主世界（母星）地表的矿脉露头和原油涌泉（ADR-0011）：铁、铜、煤比较常见，金少一些，像戴森球计划的出生星球一样
 * 开局附近就有能放采矿机的地方。由 NeoForge 的生物群系修改器加进主世界的所有生物群系。
 */
public record OverworldVeinsFeature() implements Feature {
    public static final MapCodec<OverworldVeinsFeature> CODEC = MapCodec.unit(OverworldVeinsFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("overworld_veins"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Universe.id("overworld_veins"));

    @Override
    public MapCodec<OverworldVeinsFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        int x = origin.getX() & ~15;
        int z = origin.getZ() & ~15;
        boolean placed = false;
        placed |= outcrop(level, random, x, z, 8, Blocks.IRON_ORE.defaultBlockState());
        placed |= outcrop(level, random, x, z, 10, Blocks.COPPER_ORE.defaultBlockState());
        placed |= outcrop(level, random, x, z, 8, Blocks.COAL_ORE.defaultBlockState());
        placed |= outcrop(level, random, x, z, 48, Blocks.GOLD_ORE.defaultBlockState());
        if (random.nextInt(40) == 0) {
            placed |= Outcrops.single(level, random, x, z, UniverseBlocks.CRUDE_OIL_SEEP.get().defaultBlockState(), OverworldVeinsFeature::ground);
        }
        return placed;
    }

    /** 平均每 {@code oneIn} 个区块一片。 */
    private static boolean outcrop(WorldGenLevel level, RandomSource random, int x, int z, int oneIn, BlockState ore) {
        return random.nextInt(oneIn) == 0 && Outcrops.patch(level, random, x, z, 2 + random.nextInt(2), ore, OverworldVeinsFeature::ground) > 0;
    }

    /** 地表的草、土、沙、石头。26.x 的 {@code #dirt} 不再包括草方块，要用 {@code #substrate_overworld}。 */
    private static boolean ground(BlockState state) {
        return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL) || state.is(BlockTags.TERRACOTTA);
    }
}
