package io.github.sweetcornna.universe.block.ship;

import io.github.sweetcornna.universe.core.ship.ShipCombat;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 飞船武器（ADR-0017）：光子炮和采矿光束。朝向是炮口的方向，放下时朝着玩家面对的方向（站在船尾朝船头放）。
 *
 * <p>开火的方向跟着驾驶员的视线走，但炮口左右只能偏离方块的朝向 {@link ShipCombat#AIM_YAW}°，上下 {@link ShipCombat#AIM_PITCH}°：
 * 朝前装的炮管船头前方，朝两侧、朝后装的炮守住别的方向。
 */
public class ShipWeaponBlock extends HorizontalDirectionalBlock {
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 6, 16, 16, 16),
            Block.box(5, 5, 0, 11, 11, 6)));

    public ShipWeaponBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
