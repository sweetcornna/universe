package io.github.sweetcornna.universe.block.ship;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.entity.ship.ShipAssembly;
import io.github.sweetcornna.universe.entity.ship.ShipPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 驾驶台：一艘船的核心。朝向就是船头，右键把和它相连的方块组装成飞船并坐上去；空手潜行右键打开飞船面板，
 * 看评级、装技术（ADR-0024，技术存在 {@link HelmBlockEntity} 里）。
 */
public class ShipHelmBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** 座面的高度（格），驾驶员坐在这里，停船后也站在这里。 */
    public static final double SEAT_HEIGHT = 7 / 16.0;
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(1, 0, 1, 15, 2, 15),
            // 控制台在船头一侧
            Block.box(2, 2, 0, 14, 12, 5),
            Block.box(3, 2, 7, 13, 7, 15),
            Block.box(3, 7, 13, 13, 16, 15)));

    public ShipHelmBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放的时候面朝哪边，船头就朝哪边
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HelmBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                ShipPanel.openParked(serverLevel, pos, serverPlayer);
            } else {
                ShipAssembly.assembleAndBoard(serverLevel, pos, serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
