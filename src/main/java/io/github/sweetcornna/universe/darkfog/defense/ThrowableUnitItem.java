package io.github.sweetcornna.universe.darkfog.defense;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.darkfog.Support;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** 能扔的单元（ADR-0028 §五）：右键扔出去，落地炸开。燃烧单元照样是燃料。 */
public class ThrowableUnitItem extends Item {
    public ThrowableUnitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F,
                0.5F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(ThrownUnit.throwFrom(serverLevel, player, stack));
        }
        player.getCooldowns().addCooldown(stack, Support.THROW_COOLDOWN);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
            TooltipFlag flag) {
        Support.Charge charge = ThrownUnit.chargeOf(stack);
        tooltip.accept(Component.translatable("item.universe.throwable_unit.tooltip", Math.round(charge.damage(0)),
                Math.round(charge.radius())).withStyle(ChatFormatting.GRAY));
    }
}
