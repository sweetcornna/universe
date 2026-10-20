package io.github.sweetcornna.universe.factory.power;

import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 人造恒星：两格高，下半截是底座和磁约束线圈，上半截四根立柱围着一颗小恒星（由方块实体渲染器画，发电时转动、脉动）。
 */
public class ArtificialStarBlock extends TallMachineBlock {
    private static final VoxelShape LOWER = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(2, 4, 2, 14, 16, 14));
    private static final VoxelShape UPPER = Shapes.or(Block.box(0, 0, 0, 2.5, 16, 2.5), Block.box(13.5, 0, 0, 16, 16, 2.5),
            Block.box(0, 0, 13.5, 2.5, 16, 16), Block.box(13.5, 0, 13.5, 16, 16, 16), Block.box(0, 14, 0, 16, 16, 16),
            Block.box(4, 3, 4, 12, 11, 12));

    public ArtificialStarBlock(Properties properties) {
        super(properties, UniverseBlockEntities.ARTIFICIAL_STAR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING) || state.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return;
        }
        // 从恒星表面飘出来的光点
        if (random.nextInt(2) == 0) {
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitch = (random.nextDouble() - 0.5) * Math.PI;
            double dx = Math.cos(yaw) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(yaw) * Math.cos(pitch);
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + dx * 0.3, pos.getY() + 0.45 + dy * 0.3, pos.getZ() + 0.5 + dz * 0.3,
                    dx * 0.03, dy * 0.03, dz * 0.03);
        }
        if (random.nextInt(50) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                    0.7F, 0.5F, false);
        }
    }
}
