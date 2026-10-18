package io.github.sweetcornna.universe.block.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 护盾发生器（ADR-0017）：装在飞船上，每台给船 40 点护盾。中间的线圈发着蓝光，偶尔飘出一点光尘。
 */
public class ShieldGeneratorBlock extends Block {
    private static final DustParticleOptions DUST = new DustParticleOptions(0x6FD8FF, 0.6F);

    public ShieldGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.2;
        level.addParticle(DUST, x, y, z, 0, 0.02, 0);
    }
}
