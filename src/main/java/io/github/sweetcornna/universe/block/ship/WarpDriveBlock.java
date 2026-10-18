package io.github.sweetcornna.universe.block.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * 跃迁引擎（ADR-0012）：装在飞船上就能在恒星系之间跃迁，每多一台射程多 5 光年。正面是发光的场线圈，放下时朝着玩家。
 */
public class WarpDriveBlock extends HorizontalDirectionalBlock {
    public WarpDriveBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 线圈周围偶尔飘出一点紫色的微粒。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
        level.addParticle(ParticleTypes.PORTAL, x, y, z, facing.getStepX() * 0.3, 0.05, facing.getStepZ() * 0.3);
    }
}
