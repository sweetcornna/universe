package io.github.sweetcornna.universe.darkfog.turret;

import java.util.Locale;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** 炮塔的弹药（ADR-0028 §五）：说明里写给哪种炮塔用、一发多少伤害、一个能打几发、范围。 */
public class AmmoItem extends Item {
    private final AmmoKind ammo;

    public AmmoItem(AmmoKind ammo, Properties properties) {
        super(properties);
        this.ammo = ammo;
    }

    public AmmoKind ammo() {
        return this.ammo;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.universe.ammo.turret",
                Component.translatable("block.universe." + this.ammo.turret().id())).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.ammo.damage", number(this.ammo.damage()), this.ammo.rounds())
                .withStyle(ChatFormatting.BLUE));
        if (this.ammo.radius() > 0) {
            builder.accept(Component.translatable(this.ammo.push() < 0 ? "tooltip.universe.ammo.pull" : "tooltip.universe.ammo.radius",
                    number(this.ammo.radius())).withStyle(ChatFormatting.BLUE));
        }
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
