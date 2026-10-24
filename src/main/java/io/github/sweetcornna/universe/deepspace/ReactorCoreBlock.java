package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.registry.sound.DeepSpaceSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 反应堆芯（ADR-0034 §四）：残骸上最值钱的东西，包在屏蔽层里，还在发蓝光、嗡嗡响。拆下来结构完整度 −30，
 * 而且 20 秒后残骸熔毁。
 */
public class ReactorCoreBlock extends DerelictBlock {
    private static final DustParticleOptions GLOW = new DustParticleOptions(0x5FE8FF, 0.7F);

    public ReactorCoreBlock(Properties properties) {
        super(Part.REACTOR, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.4, pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.4,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.4, 0, 0, 0);
        }
        if (random.nextInt(40) == 0) {
            level.playLocalSound(pos, DeepSpaceSounds.REACTOR_HUM.get(), SoundSource.BLOCKS, 0.5F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }
}
