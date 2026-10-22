package io.github.sweetcornna.universe.survival;

import java.util.Locale;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.survival.Dish;
import io.github.sweetcornna.universe.core.survival.LifeSupport;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 营养处理器做的半成品和菜（ADR-0026）。能吃的按原版回饥饿和饱和度（物品上的食物组件），吃完再按 {@link Dish#effect()}
 * 补氧气、补危害防护或者保暖一段时间。
 */
public class DishItem extends Item {
    private final Dish dish;

    public DishItem(Dish dish, Properties properties) {
        super(properties);
        this.dish = dish;
    }

    public Dish dish() {
        return this.dish;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && this.dish.effect() != Dish.Effect.NONE) {
            Component name = stack.getHoverName();
            LifeSupport support = player.getData(UniverseAttachments.LIFE_SUPPORT);
            switch (this.dish.effect()) {
                case OXYGEN -> support.addOxygen(this.dish.amount());
                case HAZARD -> support.rechargeHazards(this.dish.amount());
                case WARMTH -> support.warm(this.dish.amount() * 20);
                case NONE -> {
                }
            }
            player.sendOverlayMessage(Component.translatable("message.universe.dish." + this.effectKey(), name, this.dish.amount()));
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        if (this.dish.effect() != Dish.Effect.NONE) {
            builder.accept(Component.translatable("tooltip.universe.dish." + this.effectKey(), this.dish.amount()).withStyle(ChatFormatting.BLUE));
        }
    }

    private String effectKey() {
        return this.dish.effect().name().toLowerCase(Locale.ROOT);
    }
}
