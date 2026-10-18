package io.github.sweetcornna.universe.factory.belt;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** 储液罐的物品：拆下来时带着里面的流体，提示里写着装了什么。 */
public class StorageTankItem extends BlockItem {
    public StorageTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        StorageTankBlockEntity.Contents contents = stack.get(UniverseDataComponents.TANK_CONTENTS.get());
        builder.accept(contents == null
                ? Component.translatable("tooltip.universe.storage_tank.empty", StorageTankBlockEntity.CAPACITY).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.universe.storage_tank.contents", Component.translatable(contents.fluid().getDescriptionId()), contents.amount(),
                        StorageTankBlockEntity.CAPACITY).withStyle(ChatFormatting.AQUA));
    }
}
