package io.github.sweetcornna.universe.microbe;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.microbe.BioTime;
import io.github.sweetcornna.universe.core.microbe.Gene;
import io.github.sweetcornna.universe.core.microbe.MicrobeStrain;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.core.microbe.SampleSource;
import io.github.sweetcornna.universe.core.microbe.Tolerance;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 一管菌种（ADR-0036 §五），带着 {@link UniverseDataComponents#ORGANISM}。测过序的写菌名、代谢、温度范围、翻倍时间和基因；
 * 没测过的只写"未鉴定的菌株"和采自哪里。生物反应器拿它接种，生态播种站拿它当先锋。
 */
public class StrainTubeItem extends Item {
    public StrainTubeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(Organism organism, int count) {
        ItemStack stack = new ItemStack(UniverseItems.STRAIN_TUBE.get(), count);
        stack.set(UniverseDataComponents.ORGANISM.get(), organism);
        return stack;
    }

    public static @Nullable Organism organism(ItemStack stack) {
        return stack.is(UniverseItems.STRAIN_TUBE.get()) ? stack.get(UniverseDataComponents.ORGANISM.get()) : null;
    }

    /** 测过序的写菌名，没测过的写"未鉴定的菌株"。 */
    public static Component strainName(Organism organism) {
        return GeneLibraries.sequenced(organism.strain().key()) ? MicrobeText.strain(organism.strain())
                : Component.translatable("microbe.universe.unidentified");
    }

    @Override
    public Component getName(ItemStack stack) {
        Organism organism = organism(stack);
        if (organism == null) {
            return super.getName(stack);
        }
        Component name = strainName(organism);
        return Component.translatable(organism.added().isEmpty() ? "item.universe.strain_tube.of" : "item.universe.strain_tube.edited", name);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Organism organism = organism(stack);
        if (organism == null) {
            return;
        }
        MicrobeStrain strain = organism.strain();
        SampleSource source = strain.sources().stream().findFirst().orElse(null);
        if (!GeneLibraries.sequenced(strain.key())) {
            if (source != null) {
                builder.accept(Component.translatable("tooltip.universe.strain_tube.from", MicrobeText.source(source)).withStyle(ChatFormatting.GRAY));
            }
            builder.accept(Component.translatable("tooltip.universe.strain_tube.unidentified").withStyle(ChatFormatting.DARK_GRAY));
            addedLines(organism, builder);
            return;
        }
        builder.accept(Component.translatable("tooltip.universe.strain_tube.kind", MicrobeText.metabolism(strain.metabolism()),
                MicrobeText.morphology(strain.morphology())).withStyle(ChatFormatting.GRAY));
        Tolerance tolerance = organism.tolerance();
        builder.accept(Component.translatable("tooltip.universe.strain_tube.temperature", Math.round(tolerance.minK()), Math.round(tolerance.maxK()),
                MicrobeText.temperature(tolerance.optK())).withStyle(ChatFormatting.GRAY));
        double ticks = organism.doublingTicks();
        builder.accept(Component.translatable("tooltip.universe.strain_tube.doubling", MicrobeText.hours(BioTime.hours(ticks)),
                Math.max(1, Math.round(ticks / 20))).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.strain_tube.genes", MicrobeText.genes(strain.genes())).withStyle(ChatFormatting.GRAY));
        addedLines(organism, builder);
    }

    /** 编辑进去的基因和驯化的加成。 */
    private static void addedLines(Organism organism, Consumer<Component> builder) {
        if (!organism.added().isEmpty()) {
            builder.accept(Component.translatable("tooltip.universe.strain_tube.added", organism.added().size(), Gene.MAX_ADDED,
                    MicrobeText.genes(organism.added())).withStyle(ChatFormatting.AQUA));
        }
        if (organism.domestication() > 0) {
            builder.accept(Component.translatable("tooltip.universe.strain_tube.domesticated", MicrobeText.bonus(organism.domestication()))
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
