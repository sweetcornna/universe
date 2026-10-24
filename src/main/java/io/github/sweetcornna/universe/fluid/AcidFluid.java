package io.github.sweetcornna.universe.fluid;

import io.github.sweetcornna.universe.registry.UniverseFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 酸液（ADR-0030 §一）：毒性星球的海和含水层。照浓硫酸做，比水重、稍稠、流得慢一点（{@link UniverseFluids}）。
 * 表面偶尔冒一缕烟、嘶一声。灼伤和腐蚀见 {@link AcidEffects}。
 */
public abstract class AcidFluid extends BaseFlowingFluid {
    protected AcidFluid(Properties properties) {
        super(properties);
    }

    /** 这格是酸液（源头或流动的）。 */
    public static boolean is(FluidState state) {
        return state.getFluidType() == UniverseFluids.ACID_TYPE.get();
    }

    /** 当海用的液体：水或酸液（潜艇、海底的地标都认）。 */
    public static boolean sea(FluidState state) {
        return state.is(net.minecraft.tags.FluidTags.WATER) || is(state);
    }

    public static FluidType type() {
        return UniverseFluids.ACID_TYPE.get();
    }

    @Override
    protected void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (state.isSource() && random.nextInt(160) == 0 && level.getBlockState(pos.above()).isAir()) {
            double x = pos.getX() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.0, z, 0, 0.02, 0);
            if (random.nextInt(6) == 0) {
                level.playLocalSound(x, pos.getY() + 1.0, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.08F,
                        1.6F + random.nextFloat() * 0.3F, false);
            }
        }
    }

    public static class Source extends AcidFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    public static class Flowing extends AcidFluid {
        public Flowing(Properties properties) {
            super(properties);
            this.registerDefaultState(this.getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.material.Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
