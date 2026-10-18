package io.github.sweetcornna.universe.logistics;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** 物流站的物品：拆下来带着内容的，提示里列出设定的物品、存货和载具（ADR-0015）。 */
public class StationItem extends BlockItem {
    public StationItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        StationContents contents = stack.get(UniverseDataComponents.STATION_CONTENTS.get());
        if (contents == null) {
            return;
        }
        for (StationContents.Slot slot : contents.slots()) {
            if (!slot.item().isEmpty()) {
                builder.accept(Component.translatable("tooltip.universe.station.slot",
                        Component.translatable(StationBlockEntity.itemOf(slot.item()).getDescriptionId()), slot.count())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (contents.drones() > 0 || contents.vessels() > 0 || contents.warpers() > 0) {
            builder.accept(Component.translatable("tooltip.universe.station.vehicles", contents.drones(), contents.vessels(), contents.warpers())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
