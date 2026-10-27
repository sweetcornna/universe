package io.github.sweetcornna.universe.microbe;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 装着样本的采样管（ADR-0036 §五）：名字带来源（组织样本带生物名），说明里写采自哪颗星球、采样处多少度。
 * 土、水、岩石这些样本放进培养箱分离菌种，组织样本放进测序仪测基因。
 */
public class SampleItem extends Item {
    public SampleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(Sample sample) {
        ItemStack stack = new ItemStack(UniverseItems.SAMPLE.get());
        stack.set(UniverseDataComponents.SAMPLE.get(), sample);
        return stack;
    }

    public static @Nullable Sample sample(ItemStack stack) {
        return stack.get(UniverseDataComponents.SAMPLE.get());
    }

    /** "热泉"、"克拉尔跳蛛的组织"。 */
    public static Component describe(Sample sample) {
        if (sample.tissue() && !sample.nameZh().isEmpty()) {
            return Component.translatable("item.universe.sample.tissue_of", MicrobeText.name(sample.nameZh(), sample.nameEn()));
        }
        return MicrobeText.source(sample.source());
    }

    @Override
    public Component getName(ItemStack stack) {
        Sample sample = sample(stack);
        return sample == null ? super.getName(stack) : Component.translatable("item.universe.sample.of", describe(sample));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Sample sample = sample(stack);
        if (sample == null) {
            return;
        }
        builder.accept(Component.translatable("tooltip.universe.sample.where", MicrobeText.planet(sample.body()),
                MicrobeText.temperature(sample.temperature())).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(sample.tissue() ? "tooltip.universe.sample.sequence" : "tooltip.universe.sample.culture")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
