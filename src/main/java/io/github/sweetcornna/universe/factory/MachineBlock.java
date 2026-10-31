package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.perf.ServerPerf;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 工业设备的方块（ADR-0011）：水平朝向（正面朝着放置的玩家），"工作中"时换成发光的模型，右键打开设备界面。
 * 具体的逻辑都在方块实体里。
 */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type;

    public MachineBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type) {
        super(properties);
        this.type = type;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return this.type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != this.type.get()) {
            return null;
        }
        return ServerPerf.timed(type, (tickLevel, pos, tickState, blockEntity) -> ((MachineBlockEntity) blockEntity).serverTick());
    }

    /** 设备的方块实体所在的位置；高的设备从上半截转到下半截。 */
    protected BlockPos machinePos(BlockState state, BlockPos pos) {
        return pos;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockPos machine = this.machinePos(state, pos);
            if (level.getBlockEntity(machine) instanceof MachineBlockEntity blockEntity) {
                player.openMenu(blockEntity, machine);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(this.machinePos(state, pos)));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
