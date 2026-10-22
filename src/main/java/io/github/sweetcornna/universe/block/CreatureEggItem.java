package io.github.sweetcornna.universe.block;

import java.util.Locale;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.entity.CreatureEggs;
import io.github.sweetcornna.universe.entity.CreatureEggs.Egg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** 生物卵的物品（ADR-0026 §九）：名字带物种，说明里列出体型、颜色、性情。放下去就是卵方块。 */
public class CreatureEggItem extends BlockItem {
    public CreatureEggItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Egg egg = CreatureEggs.egg(stack);
        return egg == null ? super.getName(stack) : Component.translatable("item.universe.creature_egg.of", egg.speciesName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Egg egg = CreatureEggs.egg(stack);
        if (egg == null) {
            builder.accept(Component.translatable("tooltip.universe.creature_egg.infertile").withStyle(ChatFormatting.GRAY));
            return;
        }
        traits(egg.traits(), builder);
    }

    /** 性状的三行：体型、颜色（不变色的不写）、性情。卵序列仪的界面也用。 */
    public static void traits(CreatureTraits traits, Consumer<Component> builder) {
        builder.accept(Component.translatable("tooltip.universe.creature_egg.size", size(traits.size())).withStyle(ChatFormatting.GRAY));
        if (traits.tint() != CreatureTraits.NO_TINT) {
            builder.accept(Component.translatable("tooltip.universe.creature_egg.color", swatch(traits.tint())).withStyle(ChatFormatting.GRAY));
        }
        builder.accept(Component.translatable("tooltip.universe.creature_egg.temperament", temperament(traits.temperament()))
                .withStyle(ChatFormatting.GRAY));
    }

    public static String size(double size) {
        return String.format(Locale.ROOT, "%.2f", size);
    }

    /** 一小块那种颜色。 */
    public static Component swatch(int tint) {
        return Component.literal("■■").withColor(tint);
    }

    public static Component temperament(Temperament temperament) {
        return Component.translatable("discovery.universe.temperament." + temperament.name().toLowerCase(Locale.ROOT));
    }
}
