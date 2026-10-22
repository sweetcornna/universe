package io.github.sweetcornna.universe.world.freighter;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.fleet.FreighterInterior;
import io.github.sweetcornna.universe.fleet.FreighterBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/**
 * 货船船舱的区块生成器（ADR-0024）：没有地层的平坦生成器（虚空），生成区块时把落在区块里的那部分船舱
 * （{@link FreighterInterior}）放上去。每艘货船的船舱都一样，生成器不带参数。
 */
public class FreighterChunkGenerator extends FlatLevelSource {
    public static final MapCodec<FreighterChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings)
    ).apply(i, FreighterChunkGenerator::new));

    public FreighterChunkGenerator(FlatLevelGeneratorSettings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState, StructureManager structureManager,
            BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        return super.buildTerrain(chunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes)
                .thenApply(built -> {
                    place(built);
                    return built;
                });
    }

    private static void place(ChunkAccess chunk) {
        int[] bounds = FreighterInterior.BOUNDS;
        ChunkPos pos = chunk.getPos();
        int minX = Math.max(bounds[0], pos.getMinBlockX());
        int maxX = Math.min(bounds[3], pos.getMaxBlockX());
        int minZ = Math.max(bounds[2], pos.getMinBlockZ());
        int maxZ = Math.min(bounds[5], pos.getMaxBlockZ());
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = bounds[1]; y <= bounds[4]; y++) {
                    FreighterInterior.Part part = FreighterInterior.at(x, y, z);
                    if (part == null) {
                        continue;
                    }
                    int worldY = FreighterInterior.ORIGIN_Y + y;
                    BlockState state = FreighterBlocks.interior(part);
                    chunk.setBlockState(cursor.set(x, worldY, z), state);
                    oceanFloor.update(x & 15, worldY, z & 15, state);
                    worldSurface.update(x & 15, worldY, z & 15, state);
                }
            }
        }
    }
}
