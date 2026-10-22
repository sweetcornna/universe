package io.github.sweetcornna.universe.tech;

import java.util.Locale;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.space.DriveTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 镉、翠金、铟驱动（ADR-0024）：装进驾驶台的技术槽，跃迁引擎就能去更极端的恒星。装了高一档的，下面几档也都能去。
 */
public class DriveItem extends Item {
    private final DriveTier tier;

    public DriveItem(DriveTier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public DriveTier tier() {
        return this.tier;
    }

    /** 物品名，比如 cadmium_drive。 */
    public static String itemName(DriveTier tier) {
        return tier.name().toLowerCase(Locale.ROOT) + "_drive";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.universe.drive." + this.tier.name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.module.host.ship").withStyle(ChatFormatting.DARK_AQUA));
    }
}
