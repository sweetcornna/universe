package io.github.sweetcornna.universe.world.space;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.space.StationLayout;
import io.github.sweetcornna.universe.core.space.StationLayout.Site;
import io.github.sweetcornna.universe.station.Stations;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/**
 * 太空维度的区块生成器（ADR-0017）：和以前一样是没有地层的平坦生成器（虚空），只是生成区块时把落在区块里的
 * 那部分空间站放上去。空间站的位置在建维度时由恒星系算好，存在生成器里。
 */
public class SpaceChunkGenerator extends FlatLevelSource {
    private static final Codec<Site> SITE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Site::x),
            Codec.INT.fieldOf("y").forGetter(Site::y),
            Codec.INT.fieldOf("z").forGetter(Site::z),
            Codec.STRING.xmap(name -> StationLayout.Dir.valueOf(name.toUpperCase(Locale.ROOT)), dir -> dir.name().toLowerCase(Locale.ROOT))
                    .fieldOf("hangar").forGetter(Site::hangar)
    ).apply(i, Site::new));
    public static final MapCodec<SpaceChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings),
            SITE_CODEC.optionalFieldOf("station").forGetter(SpaceChunkGenerator::station)
    ).apply(i, SpaceChunkGenerator::new));

    private final Optional<Site> station;

    public SpaceChunkGenerator(FlatLevelGeneratorSettings settings, Optional<Site> station) {
        super(settings);
        this.station = station;
    }

    public Optional<Site> station() {
        return this.station;
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
                    this.station.ifPresent(site -> Stations.place(site, built));
                    return built;
                });
    }
}
