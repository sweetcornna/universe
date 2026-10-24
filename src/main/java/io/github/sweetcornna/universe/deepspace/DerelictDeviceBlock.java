package io.github.sweetcornna.universe.deepspace;

import io.github.sweetcornna.universe.core.deepspace.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * 前哨站里能拆的设备（ADR-0034 §四）：断了电的控制柜，屏幕黑着，偶尔冒一点火花。拆下来是电子元件。
 */
public class DerelictDeviceBlock extends HorizontalDirectionalBlock {
    public DerelictDeviceBlock(Properties properties) {
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

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0) {
            Direction facing = state.getValue(FACING);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + facing.getStepX() * 0.52, pos.getY() + 0.3 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.5 + facing.getStepZ() * 0.52, facing.getStepX() * 0.05, 0, facing.getStepZ() * 0.05);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Hulks.removed(level, pos, Part.DEVICE);
    }
}
