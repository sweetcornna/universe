package io.github.sweetcornna.universe.tech;

import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 技术模块（ADR-0014）。数值存在 {@link UniverseDataComponents#TECH_MODULE} 里；制造台做出来的还没有数值，
 * 第一次进玩家背包（或者装进技术槽）时按 C 级随机定下。
 */
public class TechModuleItem extends Item {
    private final ModuleType type;

    public TechModuleItem(ModuleType type, Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public ModuleType type() {
        return this.type;
    }

    /** 模块的数值；还没随机过的按 C 级中间值显示。 */
    public TechModule module(ItemStack stack) {
        TechModule module = stack.get(UniverseDataComponents.TECH_MODULE.get());
        return module != null && module.type() == this.type ? module : TechModule.typical(this.type, Grade.C);
    }

    public static boolean calibrated(ItemStack stack) {
        return stack.has(UniverseDataComponents.TECH_MODULE.get());
    }

    /** 没有数值的模块按 C 级随机一份。 */
    public static void calibrate(ItemStack stack, long seed) {
        if (stack.getItem() instanceof TechModuleItem item && !calibrated(stack)) {
            stack.set(UniverseDataComponents.TECH_MODULE.get(), TechModule.roll(item.type, Grade.C, Rng.of(seed)));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        calibrate(stack, level.getRandom().nextLong());
    }

    @Override
    public Component getName(ItemStack stack) {
        Grade grade = this.module(stack).grade();
        return Component.translatable(this.getDescriptionId()).append(" · " + grade.letter())
                .withStyle(Style.EMPTY.withColor(grade.color()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        TechModule module = this.module(stack);
        for (Stat stat : this.type.stats()) {
            builder.accept(statLine(stat, module.value(stat)).withStyle(ChatFormatting.GRAY));
        }
        if (calibrated(stack)) {
            builder.accept(Component.translatable("tooltip.universe.module.quality", Math.round(module.quality() * 100))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.universe.module.uncalibrated").withStyle(ChatFormatting.DARK_GRAY));
        }
        builder.accept(Component.translatable("tooltip.universe.module.host." + this.type.host().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /** 一项加成的说明，比如"+23% 采矿速度"、"+6 护盾"。 */
    public static MutableComponent statLine(Stat stat, double value) {
        String amount = stat.absolute() ? "+" + format(value) : "+" + Math.round(value * 100) + "%";
        return Component.translatable("tech.universe.stat." + stat.name().toLowerCase(Locale.ROOT), amount);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
