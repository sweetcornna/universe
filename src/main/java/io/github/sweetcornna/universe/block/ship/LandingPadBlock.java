package io.github.sweetcornna.universe.block.ship;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 着陆台（ADR-0024）：右键看状态，没有界面。放下的人是主人，召唤时自己的船停在自己的台子上。 */
public class LandingPadBlock extends MachineBlock {
    public LandingPadBlock(Properties properties) {
        super(properties, UniverseBlockEntities.LANDING_PAD);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof LandingPadBlockEntity pad) {
            pad.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LandingPadBlockEntity pad) {
            ShipEntity ship = pad.shipAbove();
            Component message;
            if (!pad.connected()) {
                message = Component.translatable("message.universe.landing_pad.no_power");
            } else if (ship == null) {
                message = Component.translatable("message.universe.landing_pad.empty");
            } else {
                message = Component.translatable("message.universe.landing_pad.fuel", Math.round(ship.fuelServer()), Math.round(ship.fuelCapacity()));
            }
            player.sendOverlayMessage(message);
        }
        return InteractionResult.SUCCESS;
    }
}
