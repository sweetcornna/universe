package io.github.sweetcornna.universe.block.encounter;

import io.github.sweetcornna.universe.core.space.Encounters;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 废弃货船天花板下漏电的线管（ADR-0024）：一直往下冒火花，时不时噼啪一声。走到底下（{@link Encounters#HAZARD_RANGE} 格以内）会挨电，
 * 伤害由 {@code SpaceEncounters} 按格子算。打不坏。
 */
public class LiveConduitBlock extends Block {
    public LiveConduitBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() - 0.05, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.2, -0.15 - random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.2);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() - 0.1, pos.getZ() + 0.5, 0, -0.05, 0);
        }
        if (random.nextInt(6) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WorldSounds.CONDUIT_HISS.get(), SoundSource.BLOCKS,
                    0.5F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }
}
