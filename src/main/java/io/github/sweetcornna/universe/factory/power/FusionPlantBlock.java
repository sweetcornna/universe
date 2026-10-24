package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 微型聚变发电站：正面一圈约束环，发电时亮起青白色的光，偶尔迸出电火花。 */
public class FusionPlantBlock extends MachineBlock {
    public FusionPlantBlock(Properties properties) {
        super(properties, UniverseBlockEntities.MINI_FUSION_POWER_PLANT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        if (random.nextInt(3) == 0) {
            // 环在正面，半径约 5 像素
            double angle = random.nextDouble() * Math.PI * 2;
            double along = Math.cos(angle) * 0.32;
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.52 + (facing.getAxis() == Direction.Axis.Z ? along : 0);
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52 + (facing.getAxis() == Direction.Axis.X ? along : 0);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 0.5 + Math.sin(angle) * 0.32, z, 0, 0, 0);
        }
    }
}
