package io.github.sweetcornna.universe.factory.belt;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.perf.ServerPerf;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 储液罐（ADR-0015）：拿着流体右键把整组灌进去，空手右键看装了什么，潜行空手右键取出一组。叠起来的罐子之间不画顶盖和
 * 底板，看起来是一根长罐；比较器读满了多少。
 */
public class StorageTankBlock extends BaseEntityBlock {
    public StorageTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != UniverseBlockEntities.STORAGE_TANK.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            long start = ServerPerf.begin();
            ((StorageTankBlockEntity) blockEntity).serverTick();
            ServerPerf.end("block/storage_tank", start);
        };
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return direction.getAxis() == Direction.Axis.Y && neighborState.is(this) || super.skipRendering(state, neighborState, direction);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof StorageTankBlockEntity tank)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (tank.room(stack.getItem()) <= 0) {
            // 不是流体（或者装不下）就照常放方块、用物品
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            int added = tank.fill(stack.getItem(), stack.getCount());
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(added);
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.0F);
            showContents(player, tank);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof StorageTankBlockEntity tank)) {
            return InteractionResult.SUCCESS;
        }
        Item fluid = tank.fluid();
        if (player.isSecondaryUseActive() && fluid != null) {
            int taken = tank.drain(fluid.getDefaultMaxStackSize());
            ItemStack out = new ItemStack(fluid, taken);
            player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        showContents(player, tank);
        return InteractionResult.SUCCESS;
    }

    private static void showContents(Player player, StorageTankBlockEntity tank) {
        Item fluid = tank.fluid();
        player.sendOverlayMessage(fluid == null
                ? Component.translatable("message.universe.storage_tank.empty", StorageTankBlockEntity.CAPACITY)
                : Component.translatable("message.universe.storage_tank.contents", Component.translatable(fluid.getDescriptionId()), tank.amount(),
                        StorageTankBlockEntity.CAPACITY));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof StorageTankBlockEntity tank) || tank.amount() == 0) {
            return 0;
        }
        return Mth.clamp(1 + Mth.floor(tank.fillRatio() * 14), 1, 15);
    }
}
