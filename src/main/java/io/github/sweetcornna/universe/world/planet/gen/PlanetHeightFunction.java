package io.github.sweetcornna.universe.world.planet.gen;

import com.mojang.serialization.MapCodec;

import io.github.sweetcornna.universe.core.terrain.TerrainModel;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** 二维密度函数：星球地形模型给出的地表高度（方块 Y）。 */
public record PlanetHeightFunction(PlanetGenSpec spec) implements DensityFunction {
    public static final MapCodec<PlanetHeightFunction> CODEC = PlanetGenSpec.CODEC.fieldOf("planet")
            .xmap(PlanetHeightFunction::new, PlanetHeightFunction::spec);

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
        return Interval.of(TerrainModel.MIN_Y, TerrainModel.MAX_Y);
    }

    @Override
    public @Axes int domainAxes() {
        return AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<PlanetHeightFunction> codec() {
        return CODEC;
    }

    private record Sampler(TerrainModel model) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            // 只随水平坐标变化：每列算一次，整列填同一个值
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    float value = this.sampleValue(context, volume.blockX(x), 0, blockZ);
                    outputBuffer.setRange(volume.indexUnchecked(x, 0, z), volume.sizeY(), value);
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) this.model.sample(blockX, blockZ).height();
        }
    }
}
