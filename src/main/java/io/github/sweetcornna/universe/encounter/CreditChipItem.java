package io.github.sweetcornna.universe.encounter;

import io.github.sweetcornna.universe.core.space.Encounters;
import io.github.sweetcornna.universe.registry.sound.WorldSounds;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 信用点芯片（ADR-0024）：废弃货船的货箱和保险箱里有。右键把手里这一叠都兑成信用点，一枚 {@link Encounters#CREDIT_CHIP}。 */
public class CreditChipItem extends Item {
    public CreditChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        long amount = (long) Encounters.CREDIT_CHIP * stack.getCount();
        Credits.add(player, amount);
        stack.consume(stack.getCount(), player);
        player.sendOverlayMessage(Component.translatable("message.universe.encounter.chips_cashed", Credits.format(amount))
                .withStyle(ChatFormatting.GOLD));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), WorldSounds.CREDITS, SoundSource.PLAYERS, 0.8F, 1.0F);
        return InteractionResult.SUCCESS;
    }
}
