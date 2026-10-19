package io.github.sweetcornna.universe.client;

import java.util.List;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 物品说明的"按住 Shift 查看说明"：语言文件里有 {@code tooltip.universe.<物品>.summary} 的物品，平时只显示一行提示，
 * 按住 Shift 显示说明（按换行拆成多行）。操作方法写在这里，不塞进聊天栏和动作栏的提示。
 */
public final class ItemSummaries {
    private ItemSummaries() {
    }

    public static void registerEvents(IEventBus gameBus) {
        gameBus.addListener(ItemTooltipEvent.class, ItemSummaries::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        append(event.getItemStack(), event.getToolTip(), Minecraft.getInstance().hasShiftDown());
    }

    /** 把说明（{@code details}）或"按住 Shift"那一行接到 {@code tooltip} 后面；没有说明的物品不动。 */
    public static void append(ItemStack stack, List<Component> tooltip, boolean details) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(Universe.MOD_ID)) {
            return;
        }
        String key = "tooltip.universe." + id.getPath() + ".summary";
        Language language = Language.getInstance();
        if (!language.has(key)) {
            return;
        }
        if (details) {
            for (String line : language.getOrDefault(key).split("\n")) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.universe.hold_shift", Component.literal("Shift").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
