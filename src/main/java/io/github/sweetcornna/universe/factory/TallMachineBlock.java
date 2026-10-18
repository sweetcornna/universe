package io.github.sweetcornna.universe.factory;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 两格高的设备（电力感应塔、风力涡轮机）：和门一样分上下两半，方块实体只在下半截，拆哪一半都整个拆掉。
 */
public class TallMachineBlock extends MachineBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public TallMachineBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties, type);
        this.registerDefaultState(this.defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        // 另一半没了，这一半也拆掉
        if (directionToNeighbour.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (directionToNeighbour == Direction.UP)) {
            if (!neighbourState.is(this) || neighbourState.getValue(HALF) == half) {
                return Blocks.AIR.defaultBlockState();
            }
            // 上半截跟着下半截的朝向和工作状态，模型才对得上
            return state.setValue(FACING, neighbourState.getValue(FACING)).setValue(WORKING, neighbourState.getValue(WORKING));
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 创造模式拆上半截时不让下半截掉落
        if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ItemStack destroyedWith) {
        // 上半截不掉东西（掉落表只认下半截），下半截被连带拆掉时自己掉
        super.playerDestroy(level, player, pos, state.getValue(HALF) == DoubleBlockHalf.UPPER ? Blocks.AIR.defaultBlockState() : state,
                blockEntity, destroyedWith);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.newBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.getTicker(level, state, type) : null;
    }

    @Override
    protected BlockPos machinePos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        // 两半用同一个随机种子
        BlockPos lower = this.machinePos(state, pos);
        return Mth.getSeed(lower.getX(), lower.getY(), lower.getZ());
    }
}
