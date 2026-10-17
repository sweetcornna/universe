package io.github.sweetcornna.universe.block.ship;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.ship.ShipStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 燃料舱：拿着燃料右键加注，空手右键看存量。组装飞船时燃料从各个燃料舱汇总到船上，停船时再按比例分回去。
 */
public class FuelTankBlock extends Block implements EntityBlock {
    public FuelTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelTankBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        double value = ShipFuel.value(stack);
        if (value <= 0) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof FuelTankBlockEntity tank)) {
            return InteractionResult.PASS;
        }
        if (tank.fuel() >= ShipStats.FUEL_PER_TANK - 1e-6) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.universe.fuel_tank.full"));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            tank.setFuel(tank.fuel() + value);
            ShipFuel.consume(stack, player, hand);
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.4F);
            showLevel(player, tank);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FuelTankBlockEntity tank) {
            showLevel(player, tank);
        }
        return InteractionResult.SUCCESS;
    }

    private static void showLevel(Player player, FuelTankBlockEntity tank) {
        player.sendOverlayMessage(Component.translatable("message.universe.fuel_tank.level",
                Math.round(tank.fuel()), Math.round(ShipStats.FUEL_PER_TANK)));
    }
}
