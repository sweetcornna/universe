package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Part;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 残骸导管（ADR-0034 §四）：主梁里的管线束，顺着一根轴。还有一点残电，时不时冒火花。拆下来是电子元件，
 * 但结构完整度 −8。
 */
public class DerelictConduitBlock extends RotatedPillarBlock {
    public DerelictConduitBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) {
            return;
        }
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.1;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2,
                (random.nextDouble() - 0.5) * 0.2);
        if (random.nextInt(30) == 0) {
            level.playLocalSound(pos, WorldSounds.CONDUIT_HISS.get(), SoundSource.BLOCKS, 0.25F, 0.8F + random.nextFloat() * 0.4F, false);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Hulks.removed(level, pos, Part.CONDUIT);
    }
}
