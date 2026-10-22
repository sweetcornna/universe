package io.github.sweetcornna.universe.encounter;

import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 应急信号接收器（ADR-0024）：在太空里开着飞船时用，收到一艘废弃货船的信号，在船头前方 1.5–2.5k 格。用掉一个。
 * 空间站的科技商人有卖，废弃货船的保险箱里偶尔也有。
 */
public class EmergencyReceiverItem extends Item {
    public EmergencyReceiverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        if (!(player.getVehicle() instanceof ShipEntity ship) || ship.pilot() != player || !SpaceDimensions.isSpace(level.dimension())) {
            player.sendOverlayMessage(Component.translatable("message.universe.encounter.receiver_space"));
            return InteractionResult.FAIL;
        }
        if (SpaceEncounters.busy(server)) {
            player.sendOverlayMessage(Component.translatable("message.universe.encounter.receiver_busy"));
            return InteractionResult.FAIL;
        }
        if (!SpaceEncounters.derelict(server, ship)) {
            player.sendOverlayMessage(Component.translatable("message.universe.encounter.receiver_nothing"));
            return InteractionResult.FAIL;
        }
        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
