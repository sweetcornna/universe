package io.github.sweetcornna.universe.tech;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 多功能工具的武器（ADR-0026 §六）：装进工具的技术槽里，一种只能装一个。装上以后按切换键切到它，
 * 数值在 {@link ToolWeapon}，开火在 {@code Multitool}。
 */
public class ToolWeaponItem extends Item {
    private final ToolWeapon weapon;

    public ToolWeaponItem(ToolWeapon weapon, Properties properties) {
        super(properties);
        this.weapon = weapon;
    }

    public ToolWeapon weapon() {
        return this.weapon;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.universe.tool_weapon." + this.weapon.id()).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.tool_weapon.hint", Component.keybind("key.universe.tool_mode"))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
