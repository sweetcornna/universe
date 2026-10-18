package io.github.sweetcornna.universe.world.planet.gen;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.poi.PoiStructures;
import io.github.sweetcornna.universe.poi.Pois;
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
 * 兴趣点（ADR-0014）：中心落在这个区块里的兴趣点在这里放下建筑。分布由核心层的 {@code PoiLayout} 按星球种子算，
 * 和扫描、信号增强器看到的是同一份。放在地面植物和树之后（最后一个装饰阶段），建筑会清掉占地范围里的植物。
 */
public record PlanetPoiFeature() implements Feature {
    public static final MapCodec<PlanetPoiFeature> CODEC = MapCodec.unit(PlanetPoiFeature::new);
    public static final ResourceKey<Feature> KEY = ResourceKey.create(Registries.FEATURE, Universe.id("planet_poi"));
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Universe.id("planet_poi"));

    @Override
    public MapCodec<PlanetPoiFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        return Planets.planet(level.getLevel())
                .flatMap(Pois::layout)
                .flatMap(layout -> layout.inChunk(origin.getX() >> 4, origin.getZ() >> 4))
                .map(poi -> PoiStructures.build(level, poi))
                .orElse(false);
    }
}
