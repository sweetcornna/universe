package io.github.sweetcornna.universe.factory;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 三档增产剂的物品（ADR-0022）。 */
public final class Proliferators {
    private Proliferators() {
    }

    /** 这个物品是哪一档增产剂；不是增产剂返回空。 */
    public static Proliferation.@Nullable Tier tier(Item item) {
        if (item == UniverseItems.PROLIFERATOR_MK1.get()) {
            return Proliferation.Tier.MK1;
        }
        if (item == UniverseItems.PROLIFERATOR_MK2.get()) {
            return Proliferation.Tier.MK2;
        }
        if (item == UniverseItems.PROLIFERATOR_MK3.get()) {
            return Proliferation.Tier.MK3;
        }
        return null;
    }

    public static Proliferation.@Nullable Tier tier(ItemStack stack) {
        return stack.isEmpty() ? null : tier(stack.getItem());
    }

    public static Item item(Proliferation.Tier tier) {
        return switch (tier) {
            case MK1 -> UniverseItems.PROLIFERATOR_MK1.get();
            case MK2 -> UniverseItems.PROLIFERATOR_MK2.get();
            case MK3 -> UniverseItems.PROLIFERATOR_MK3.get();
        };
    }
}
