package io.github.sweetcornna.universe.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 聚落防御炮台的炮塔（ADR-0034 §二）：一圈底座上一个炮壳，两根炮管朝着 {@link #FACING}。海盗来袭时由
 * {@link SettlementRaids} 转向、开火。挖不掉，跟着炮台建筑一起放、一起拆。
 */
public class SettlementTurretBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 11, 14);

    public SettlementTurretBlock(Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
