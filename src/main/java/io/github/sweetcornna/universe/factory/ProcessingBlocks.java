package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 中期的加工设备（ADR-0015）：方块本身只多了工作时的粒子和声音，干活的是方块实体。 */
public final class ProcessingBlocks {
    private ProcessingBlocks() {
    }

    /** 观察窗外的一个位置：正面中间往外一点，左右随机偏一些。 */
    private static double[] window(BlockPos pos, Direction facing, RandomSource random, double height) {
        double side = (random.nextDouble() - 0.5) * 0.5;
        return new double[] {
                pos.getX() + 0.5 + facing.getStepX() * 0.53 + facing.getStepZ() * side,
                pos.getY() + height + random.nextDouble() * 0.2,
                pos.getZ() + 0.5 + facing.getStepZ() * 0.53 + facing.getStepX() * side};
    }

    /** 位面熔炉：观察窗里翻着紫色的光，顶上飘起细小的光点。 */
    public static class PlaneSmelter extends MachineBlock {
        public PlaneSmelter(Properties properties) {
            super(properties, UniverseBlockEntities.PLANE_SMELTER);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            Direction facing = state.getValue(FACING);
            if (random.nextInt(2) == 0) {
                double[] at = window(pos, facing, random, 0.3);
                level.addParticle(ParticleTypes.REVERSE_PORTAL, at[0], at[1], at[2], facing.getStepX() * 0.02, 0.02, facing.getStepZ() * 0.02);
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
                        pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
            }
            if (random.nextInt(40) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                        0.4F, 1.6F, false);
            }
        }
    }

    /** 化工厂：反应釜咕嘟冒泡，排气口飘白烟。 */
    public static class ChemicalPlant extends MachineBlock {
        public ChemicalPlant(Properties properties) {
            super(properties, UniverseBlockEntities.CHEMICAL_PLANT);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.25 + random.nextDouble() * 0.15, pos.getY() + 1.05,
                        pos.getZ() + 0.25 + random.nextDouble() * 0.15, 0, 0.04, 0);
            }
            if (random.nextInt(2) == 0) {
                double[] at = window(pos, state.getValue(FACING), random, 0.25);
                level.addParticle(ParticleTypes.BUBBLE_POP, at[0], at[1], at[2], 0, 0.03, 0);
            }
            if (random.nextInt(30) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS,
                        0.5F, 0.8F, false);
            }
        }
    }

    /** 分馏塔：工作时塔顶冒出淡淡的蒸汽，偶尔一点青色的辉光（重氢）。 */
    public static class Fractionator extends MachineBlock {
        public Fractionator(Properties properties) {
            super(properties, UniverseBlockEntities.FRACTIONATOR);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.4 + random.nextDouble() * 0.2, pos.getY() + 1.02,
                        pos.getZ() + 0.4 + random.nextDouble() * 0.2, 0, 0.02, 0);
            }
            if (random.nextInt(10) == 0) {
                level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0, 0.03, 0);
            }
        }
    }
}
