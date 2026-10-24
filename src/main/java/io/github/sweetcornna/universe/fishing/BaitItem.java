package io.github.sweetcornna.universe.fishing;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.fishing.Bait;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 专门的鱼饵（ADR-0034 §一），营养处理器里做；放进钓鱼器的饵格或者捕鱼笼的饵格。 */
public class BaitItem extends Item {
    private final Bait bait;

    public BaitItem(Bait bait, Properties properties) {
        super(properties);
        this.bait = bait;
    }

    public Bait bait() {
        return this.bait;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.universe.bait." + this.bait.id()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.bait.hint").withStyle(ChatFormatting.DARK_AQUA));
    }
}
