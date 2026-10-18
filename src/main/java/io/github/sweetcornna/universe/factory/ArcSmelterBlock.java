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

/** 电弧熔炉：工作时坩埚冒热气、溅火星，观察窗里闪电弧。 */
public class ArcSmelterBlock extends MachineBlock {
    public ArcSmelterBlock(Properties properties) {
        super(properties, UniverseBlockEntities.ARC_SMELTER);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 1.02,
                    z + (random.nextDouble() - 0.5) * 0.4, 0, 0.03, 0);
        }
        if (random.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.LAVA, x, pos.getY() + 1.0, z, 0, 0, 0);
        }
        // 观察窗外的电火花
        Direction facing = state.getValue(FACING);
        if (random.nextInt(4) == 0) {
            double side = (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x + facing.getStepX() * 0.53 + facing.getStepZ() * side, pos.getY() + 0.35 + random.nextDouble() * 0.25,
                    z + facing.getStepZ() * 0.53 + facing.getStepX() * side, facing.getStepX() * 0.05, 0.02, facing.getStepZ() * 0.05);
        }
        if (random.nextInt(30) == 0) {
            level.playLocalSound(x, pos.getY() + 0.5, z, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.2F, false);
        }
    }
}
