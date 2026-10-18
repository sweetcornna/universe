package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** 火力发电厂：两格高，下面是锅炉，上面是汽轮机和烟囱；发电时烟囱冒烟、炉门透火光。 */
public class ThermalPlantBlock extends TallMachineBlock {
    public ThermalPlantBlock(Properties properties) {
        super(properties, UniverseBlockEntities.THERMAL_PLANT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            // 烟囱在背面靠右（模型里 x 10–14、z 10–14，朝北时）
            double[] offset = rotate(facing, 12 / 16.0 - 0.5, 12 / 16.0 - 0.5);
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    pos.getX() + 0.5 + offset[0] + (random.nextDouble() - 0.5) * 0.1, pos.getY() + 1.05,
                    pos.getZ() + 0.5 + offset[1] + (random.nextDouble() - 0.5) * 0.1, 0, 0.06, 0);
        } else {
            // 炉门的火星
            if (random.nextInt(3) == 0) {
                double[] offset = rotate(facing, (random.nextDouble() - 0.5) * 0.4, -0.52);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + offset[0], pos.getY() + 0.25 + random.nextDouble() * 0.15,
                        pos.getZ() + 0.5 + offset[1], 0, 0, 0);
            }
            if (random.nextInt(40) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                        1.0F, 0.8F, false);
            }
        }
    }

    /** 把朝北时的水平偏移（x 向东、z 向南）转到实际朝向。 */
    private static double[] rotate(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> new double[] {-x, -z};
            case WEST -> new double[] {z, -x};
            case EAST -> new double[] {-z, x};
            default -> new double[] {x, z};
        };
    }
}
