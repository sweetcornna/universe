package io.github.sweetcornna.universe.chem;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 化学这条线的设备方块（ADR-0036 §三）。逻辑都在方块实体里，这里只管粒子。 */
public final class ChemistryBlocks {
    private ChemistryBlocks() {
    }

    /** 大气提取器：工作时顶上的进气口吸进一缕缕空气。 */
    public static class AtmosphericExtractor extends MachineBlock {
        public AtmosphericExtractor(Properties properties) {
            super(properties, UniverseBlockEntities.ATMOSPHERIC_EXTRACTOR);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING) || random.nextInt(3) != 0) {
                return;
            }
            // 从上方一格往进气口落下来
            double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
            double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
            level.addParticle(ParticleTypes.WHITE_ASH, x, pos.getY() + 1.8, z, 0, -0.06, 0);
        }
    }

    /** 电解槽：两极冒出细小的气泡，排气口偶尔一缕白汽。 */
    public static class Electrolyzer extends MachineBlock {
        public Electrolyzer(Properties properties) {
            super(properties, UniverseBlockEntities.ELECTROLYZER);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (!state.getValue(WORKING)) {
                return;
            }
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02,
                        pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
            }
            if (random.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.75, pos.getY() + 1.05, pos.getZ() + 0.75, 0, 0.04, 0);
            }
        }
    }
}
