package io.github.sweetcornna.universe.world.planet.gen;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetcornna.universe.core.terrain.BiomeKind;
import io.github.sweetcornna.universe.core.terrain.BiomeLayout;
import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * 星球群系源：不用原版的多噪声气候表，直接拿地形模型的采样交给 {@link BiomeLayout} 挑群系，
 * 群系和地形用的是同一份高度，海岸、河谷、山峰都能对得上。
 */
public final class PlanetBiomeSource extends BiomeSource implements BiomeResolver {
    public static final MapCodec<PlanetBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PlanetGenSpec.CODEC.fieldOf("planet").forGetter(source -> source.spec),
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, PlanetBiomeSource::new));

    private final PlanetGenSpec spec;
    private final TerrainModel model;
    private final BiomeLayout layout;
    private final Map<BiomeKind, Holder<Biome>> biomes = new EnumMap<>(BiomeKind.class);
    private final Holder<Biome> fallback;

    public PlanetBiomeSource(PlanetGenSpec spec, HolderGetter<Biome> biomeGetter) {
        this.spec = spec;
        this.model = spec.model();
        this.layout = spec.layout();
        for (BiomeKind kind : this.layout.possible()) {
            this.biomes.put(kind, biomeGetter.getOrThrow(PlanetBiomes.key(kind)));
        }
        this.fallback = this.biomes.values().iterator().next();
    }

    @Override
    protected MapCodec<PlanetBiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return List.copyOf(this.biomes.values()).stream();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        // 群系按 4×4×4 的格子存，取格子中心那一列
        TerrainModel.Sample sample = this.model.sample((quartX << 2) + 2, (quartZ << 2) + 2);
        return this.biomes.getOrDefault(this.layout.pick(sample), this.fallback);
    }

    @Override
    public void addDebugInfo(List<String> result, BlockPos feetPos, Climate.Sampler sampler) {
        TerrainModel.Sample s = this.model.sample(feetPos.getX(), feetPos.getZ());
        result.add(String.format("Planet terrain H: %.1f E: %.1f C: %.2f Er: %.2f M: %.1f T: %.2f Hu: %.2f R: %.2f",
                s.height(), s.elevation(), s.continentalness(), s.erosion(), s.mountains(), s.temperature(),
                s.humidity(), s.river()));
    }
}
