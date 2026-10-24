package io.github.sweetcornna.universe.fossil;

import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.CollectorTrade;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 化石展品（ADR-0034 §三）：骨架展台上的骨架封起来，记着五个部件，名字跟着骨架走（"化石展品：长颈掠食兽"）。
 * 一样的展品能叠起来；拿着对空的骨架展台右键就摆回去（{@link SkeletonDisplayBlock}），收藏家按骨架的完整程度收。
 */
public class FossilExhibitItem extends Item {
    public FossilExhibitItem(Properties properties) {
        super(properties);
    }

    /** 展品里的骨架；没有组件（创造模式拿出来的空展品）是空骨架。 */
    public static Skeleton skeleton(ItemStack stack) {
        Skeleton skeleton = stack.get(UniverseDataComponents.FOSSIL_EXHIBIT.get());
        return skeleton == null ? Skeleton.EMPTY : skeleton;
    }

    /** 把骨架封成一件展品。 */
    public static ItemStack of(Skeleton skeleton) {
        ItemStack stack = new ItemStack(io.github.sweetcornna.universe.registry.UniverseItems.FOSSIL_EXHIBIT.get());
        stack.set(UniverseDataComponents.FOSSIL_EXHIBIT.get(), skeleton);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Skeleton skeleton = skeleton(stack);
        return skeleton.isEmpty() ? super.getName(stack) : Component.translatable("item.universe.fossil_exhibit.named", FossilNames.skeleton(skeleton));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Skeleton skeleton = skeleton(stack);
        if (skeleton.isEmpty()) {
            builder.accept(Component.translatable("tooltip.universe.fossil_exhibit.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (BonePart part : BonePart.values()) {
            Bone bone = skeleton.bone(part);
            Component value = bone == null ? Component.translatable("tooltip.universe.fossil_exhibit.missing").withStyle(ChatFormatting.DARK_GRAY)
                    : FossilNames.family(bone.family()).withStyle(ChatFormatting.WHITE);
            builder.accept(Component.translatable("tooltip.universe.fossil_exhibit.part", FossilNames.part(part), value).withStyle(ChatFormatting.GRAY));
        }
        if (skeleton.pure()) {
            builder.accept(Component.translatable("tooltip.universe.fossil_exhibit.pure").withStyle(ChatFormatting.GOLD));
        }
        builder.accept(Component.translatable("tooltip.universe.fossil_exhibit.price", Credits.format(CollectorTrade.exhibitPrice(skeleton)))
                .withStyle(ChatFormatting.YELLOW));
    }
}
