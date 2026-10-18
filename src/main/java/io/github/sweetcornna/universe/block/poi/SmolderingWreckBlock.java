package io.github.sweetcornna.universe.block.poi;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 坠毁飞船上还在冒烟的焦黑残骸（ADR-0014）：上面是空气时往上冒烟，远处就能看见；挖掉能拆出船体板。
 */
public class SmolderingWreckBlock extends Block {
    public SmolderingWreckBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos.above()).isAir()) {
            return;
        }
        if (random.nextInt(3) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.0,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.05, 0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + random.nextDouble(), pos.getY() + 1.02,
                    pos.getZ() + random.nextDouble(), 0, 0.01, 0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.4F + random.nextFloat() * 0.3F, random.nextFloat() * 0.6F + 0.5F, false);
        }
    }
}
