package io.github.sweetcornna.universe.settlement;

import java.util.List;

import io.github.sweetcornna.universe.core.settlement.Cost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 施工、升级、修理、接管要交的材料（{@link Cost}）和背包对上：数有几个、收走几个、写出名字。客户端界面也用。 */
public final class Materials {
    private Materials() {
    }

    public static TagKey<Item> tag(Cost cost) {
        return TagKey.create(Registries.ITEM, Identifier.parse(cost.id()));
    }

    public static boolean matches(ItemStack stack, Cost cost) {
        if (stack.isEmpty()) {
            return false;
        }
        if (cost.tag()) {
            return stack.is(tag(cost));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(cost.id());
    }

    /** 背包里有几个。 */
    public static int count(Player player, Cost cost) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(stack, cost)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 从背包里收走最多 {@code max} 个，返回收了几个。创造模式不收，直接算够。 */
    public static int take(Player player, Cost cost, int max) {
        if (max <= 0) {
            return 0;
        }
        if (player.hasInfiniteMaterials()) {
            return max;
        }
        Inventory inventory = player.getInventory();
        int taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < max; i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(stack, cost)) {
                int n = Math.min(max - taken, stack.getCount());
                stack.shrink(n);
                taken += n;
            }
        }
        if (taken > 0) {
            inventory.setChanged();
        }
        return taken;
    }

    /** 背包里够不够交这一组。 */
    public static boolean enough(Player player, List<Cost> costs) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        for (Cost cost : costs) {
            if (count(player, cost) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    /** 一样东西的名字：物品的名字，或者标签的名字（{@code tag.item.universe.settlement.stone}）。 */
    public static Component name(Cost cost) {
        if (cost.tag()) {
            return Component.translatable("tag.item." + cost.id().replace(':', '.').replace('/', '.'));
        }
        return icon(cost).getHoverName();
    }

    /** 界面上画的图标：标签取里面的第一样。 */
    public static ItemStack icon(Cost cost) {
        if (cost.tag()) {
            return BuiltInRegistries.ITEM.get(tag(cost)).flatMap(set -> set.stream().findFirst())
                    .map(holder -> new ItemStack(holder.value())).orElse(new ItemStack(Items.COBBLESTONE));
        }
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(cost.id())).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
