package io.github.sweetcornna.universe.world.planet.gen;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.poi.LandmarkStructures;
import io.github.sweetcornna.universe.poi.Landmarks;
import io.github.sweetcornna.universe.world.planet.Planets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * 地标（ADR-0026）：中心落在这个区块里的地标在这里放下建筑。分布由核心层的 {@code LandmarkLayout} 按星球种子算，
 * 和潜艇的声呐看到的是同一份。排在兴趣点后面，地标给兴趣点让路是选址时就算好的。
 */
public record PlanetLandmarkFeature() implements Feature {
    public static final MapCodec<PlanetLandmarkFeature> CODEC = MapCodec.unit(PlanetLandmarkFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("planet_landmark"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Universe.id("planet_landmark"));

    @Override
    public MapCodec<PlanetLandmarkFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        return Planets.planet(level.getLevel()).flatMap(Landmarks::layout).map(layout -> {
            boolean placed = false;
            for (Landmark landmark : layout.inChunk(origin.getX() >> 4, origin.getZ() >> 4)) {
                placed |= LandmarkStructures.build(level, landmark);
            }
            return placed;
        }).orElse(false);
    }
}
