package io.github.sweetcornna.universe.factory.belt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.perf.ServerPerf;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 分流器（ADR-0015）：四面接传送带的小方块，进出由传送带的朝向决定。空手右键某一面把它设成优先出口（再点一下取消），
 * 拿着物品右键设成过滤物品，潜行空手右键清掉过滤（ADR-0022）。
 */
public class SplitterBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 8, 15);

    public SplitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SplitterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != UniverseBlockEntities.SPLITTER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            long start = ServerPerf.begin();
            ((SplitterBlockEntity) blockEntity).serverTick();
            ServerPerf.end("block/splitter", start);
        };
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
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SplitterBlockEntity splitter) {
            splitter.setFilter(stack.getItem());
            player.sendOverlayMessage(Component.translatable("message.universe.splitter.filter", stack.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SplitterBlockEntity splitter) {
            if (player.isShiftKeyDown()) {
                splitter.setFilter(null);
                player.sendOverlayMessage(Component.translatable("message.universe.splitter.no_filter"));
            } else {
                // 点顶面时按玩家朝着的方向算
                Direction side = hitResult.getDirection().getAxis().isHorizontal() ? hitResult.getDirection() : player.getDirection();
                Direction priority = splitter.togglePriority(side);
                player.sendOverlayMessage(priority == null ? Component.translatable("message.universe.splitter.no_priority")
                        : Component.translatable("message.universe.splitter.priority",
                                Component.translatable("message.universe.direction." + priority.getSerializedName())));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
