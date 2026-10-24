package io.github.sweetcornna.universe.mecha.fleet;

import java.util.Locale;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 舰队的单位（ADR-0033 §一）：平时是物品，放进机甲面板"舰队"页的编队格子里，出动时变成 {@link FleetUnit}。
 * 掉的血记在物品上（{@link UniverseDataComponents#FLEET_DAMAGE}），满血的不带这个组件，能叠在一起。物品栏里显示血条。
 */
public class FleetUnitItem extends Item {
    private final FleetUnitKind kind;

    public FleetUnitItem(FleetUnitKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public FleetUnitKind kind() {
        return this.kind;
    }

    /** 这件物品是哪种单位；不是单位时为空。 */
    public static FleetUnitKind kindOf(ItemStack stack) {
        return stack.getItem() instanceof FleetUnitItem item ? item.kind : null;
    }

    /** 还剩多少生命。 */
    public static int health(ItemStack stack) {
        FleetUnitKind kind = kindOf(stack);
        if (kind == null) {
            return 0;
        }
        return Math.max(1, kind.maxHealth() - stack.getOrDefault(UniverseDataComponents.FLEET_DAMAGE.get(), 0));
    }

    /** 记下生命：满血时去掉组件，好和别的满血单位叠在一起。 */
    public static void setHealth(ItemStack stack, int health) {
        FleetUnitKind kind = kindOf(stack);
        if (kind == null) {
            return;
        }
        int damage = Math.clamp(kind.maxHealth() - health, 0, kind.maxHealth() - 1);
        if (damage <= 0) {
            stack.remove(UniverseDataComponents.FLEET_DAMAGE.get());
        } else {
            stack.set(UniverseDataComponents.FLEET_DAMAGE.get(), damage);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.getOrDefault(UniverseDataComponents.FLEET_DAMAGE.get(), 0) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * health(stack) / this.kind.maxHealth());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = Math.max(0, (float) health(stack) / this.kind.maxHealth());
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FleetUnitKind kind = this.kind;
        builder.accept(Component.translatable(kind.space() ? "tooltip.universe.fleet_unit.space" : "tooltip.universe.fleet_unit.ground")
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.universe.fleet_unit.health", health(stack), kind.maxHealth())
                .withStyle(isBarVisible(stack) ? ChatFormatting.YELLOW : ChatFormatting.BLUE));
        String weapon = "tooltip.universe.fleet_unit.weapon." + kind.weapon().name().toLowerCase(Locale.ROOT);
        builder.accept(Component.translatable(weapon, number(kind.damage()), number(20.0 / kind.interval()), number(kind.range()))
                .withStyle(ChatFormatting.BLUE));
        if (kind.splash() > 0) {
            builder.accept(Component.translatable("tooltip.universe.ammo.radius", number(kind.splash())).withStyle(ChatFormatting.BLUE));
        }
        if (kind == FleetUnitKind.DESTROYER) {
            builder.accept(Component.translatable("tooltip.universe.fleet_unit.point_defense").withStyle(ChatFormatting.BLUE));
        }
        builder.accept(Component.translatable("tooltip.universe.fleet_unit.power", Upgrades.watts(kind.idleJoules()),
                Upgrades.energy(kind.shotJoules())).withStyle(ChatFormatting.GRAY));
    }

    static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
