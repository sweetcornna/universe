package io.github.sweetcornna.universe.factory;

import io.github.sweetcornna.universe.Universe;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** 流体按物品处理（ADR-0011）；带 {@code universe:fluids} 标签的物品能装进储液罐（ADR-0015）。 */
public final class FluidItems {
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Universe.id("fluids"));

    private FluidItems() {
    }

    public static boolean isFluid(Item item) {
        return item.builtInRegistryHolder().is(TAG);
    }
}
