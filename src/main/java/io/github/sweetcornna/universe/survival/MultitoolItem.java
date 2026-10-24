package io.github.sweetcornna.universe.survival;

import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.survival.ToolMode;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import io.github.sweetcornna.universe.exocraft.Exocrafts;
import java.util.function.Consumer;
import java.util.Locale;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * 多功能工具：按住使用键开采矿激光（战斗模式下连发等离子弹），潜行时按使用键扫描。左键也能像镐、铲、斧一样挖（工具组件）。
 * 逻辑在 {@link Multitool}。
 *
 * <p>类型、档次和坏了几格存在 {@link UniverseDataComponents#TOOL_SPEC} 里（ADR-0026）；没有的是做出来的旧工具，按 A 档手枪算。
 * 名字按档次上色。
 */
public final class MultitoolItem extends Item {
    public MultitoolItem(Properties properties) {
        super(properties);
    }

    /** 这把工具的类型、档次和坏格。 */
    public static MultitoolSpec spec(ItemStack tool) {
        return tool.getOrDefault(UniverseDataComponents.TOOL_SPEC.get(), MultitoolSpec.LEGACY);
    }

    /** 一把新的、指定类型和档次的工具。 */
    public static ItemStack create(MultitoolSpec spec) {
        ItemStack stack = new ItemStack(UniverseItems.MULTITOOL.get());
        stack.set(UniverseDataComponents.TOOL_SPEC.get(), spec);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withColor(spec(stack).grade().color());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // 开着探索车：右键是车载扫描（潜行会下车）
        InteractionResult vehicle = Exocrafts.multitoolUse(player);
        if (vehicle != null) {
            return vehicle;
        }
        if (player.isSecondaryUseActive()) {
            // 钓鱼模式下潜行右键打开饵格（ADR-0034 §一）
            if (io.github.sweetcornna.universe.fishing.Fishing.fishing(player.getItemInHand(hand))) {
                if (player instanceof ServerPlayer serverPlayer) {
                    io.github.sweetcornna.universe.fishing.Fishing.openBait(serverPlayer);
                }
                return InteractionResult.SUCCESS;
            }
            // M19 钓鱼（A）的潜行右键加在这行上面
            // M19 深空和重力枪（D）的潜行右键加在这行上面
            if (player instanceof ServerPlayer serverPlayer) {
                Multitool.scan(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        // 钓鱼模式下右键：甩竿、收回、钩住，按住收线（ADR-0034 §一）
        if (io.github.sweetcornna.universe.fishing.Fishing.fishing(player.getItemInHand(hand))) {
            return io.github.sweetcornna.universe.fishing.Fishing.use(level, player, hand);
        }
        // M19 钓鱼（A）的右键加在这行上面
        // M19 深空和重力枪（D）的右键加在这行上面
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        // M19 钓鱼（A）的松开右键加在这行上面
        // 引力抓取器松开右键：轻轻放下抓着的东西（ADR-0034 §七）
        if (entity instanceof net.minecraft.server.level.ServerPlayer player && io.github.sweetcornna.universe.gravity.GravityCoils.active(player)) {
            io.github.sweetcornna.universe.gravity.GravityCoils.release(player, true);
            return true;
        }
        // M19 深空和重力枪（D）的松开右键加在这行上面
        return false;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (entity instanceof ServerPlayer player) {
            Multitool.trigger(player);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        MultitoolSpec spec = spec(stack);
        builder.accept(Component.translatable("item.universe.multitool.spec", spec.grade().letter(), className(spec),
                spec.slots()).withColor(spec.grade().color()));
        spec.specialty().forEach((stat, value) -> builder.accept(Component.translatable("item.universe.multitool.specialty",
                TechModuleItem.statLine(stat, value)).withStyle(ChatFormatting.GRAY)));
        if (spec.broken() > 0) {
            builder.accept(Component.translatable("item.universe.multitool.broken", spec.broken(), MultitoolSpec.REPAIR_SCRAP)
                    .withStyle(ChatFormatting.RED));
        }
        // 实际生效的模式：选中的武器被拆下来了就是爆能枪
        ToolMode mode = Multitool.activeMode(stack);
        builder.accept(Component.translatable("item.universe.multitool.mode",
                Component.translatable("hud.universe.multitool.mode." + mode.name().toLowerCase(Locale.ROOT)),
                Component.keybind("key.universe.tool_mode")).withStyle(mode.combat() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    /** 类型的名字，比如"手枪"。 */
    public static Component className(MultitoolSpec spec) {
        return Component.translatable("multitool.universe.class." + spec.type().key());
    }
}
