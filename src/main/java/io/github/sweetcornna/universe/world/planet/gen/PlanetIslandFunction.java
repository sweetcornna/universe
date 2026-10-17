package io.github.sweetcornna.universe.world.planet.gen;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** 三维密度函数：浮空岛，大于 0 为实心。 */
public record PlanetIslandFunction(PlanetGenSpec spec) implements DensityFunction {
    public static final MapCodec<PlanetIslandFunction> CODEC = PlanetGenSpec.CODEC.fieldOf("planet")
            .xmap(PlanetIslandFunction::new, PlanetIslandFunction::spec);
    private static final float SCALE = 1.5F;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(this.spec.model());
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.of(-2, 2);
    }

    @Override
    public @Axes int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public MapCodec<PlanetIslandFunction> codec() {
        return CODEC;
    }

    private record Sampler(TerrainModel model) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Mth.clamp((float) this.model.islandDensity(blockX, blockY, blockZ) * SCALE, -2, 2);
        }
    }
}
