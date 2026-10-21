package io.github.sweetcornna.universe.factory.belt;

import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * 流速监测器（ADR-0022）：右键打开界面设报警的下限；拿着物品右键只数这一种，潜行空手右键清掉。报警时亮红灯、
 * 四面发出满格的红石信号。
 */
public class TrafficMonitorBlock extends BeltPassBlock {
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");

    public TrafficMonitorBlock(Properties properties) {
        super(properties, UniverseBlockEntities.TRAFFIC_MONITOR, Shapes.or(Block.box(0, 0, 0, 16, 9, 16), Block.box(0, 9, 7, 2, 16, 9),
                Block.box(14, 9, 7, 16, 16, 9), Block.box(2, 14, 7, 14, 16, 9), Block.box(3, 9.5, 7.5, 13, 14, 8.5)));
        this.registerDefaultState(this.defaultBlockState().setValue(ALARM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ALARM);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // 拿着方块照常放
        if (stack.getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrafficMonitorBlockEntity monitor) {
            monitor.setFilter(stack.getItem());
            player.sendOverlayMessage(Component.translatable("message.universe.traffic_monitor.filter", stack.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrafficMonitorBlockEntity monitor) {
            if (player.isShiftKeyDown()) {
                monitor.setFilter(null);
                player.sendOverlayMessage(Component.translatable("message.universe.traffic_monitor.no_filter"));
            } else {
                player.openMenu(monitor, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ALARM) ? 15 : 0;
    }
}
