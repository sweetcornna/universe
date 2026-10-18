package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.factory.FluidItems;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

/** 物品标签：哪些物品算流体（能装进储液罐，ADR-0015）。 */
final class UniverseItemTagsProvider extends ItemTagsProvider {
    UniverseItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(FluidItems.TAG).add(
                UniverseItems.WATER.key(),
                UniverseItems.CRUDE_OIL.key(),
                UniverseItems.REFINED_OIL.key(),
                UniverseItems.HYDROGEN.key(),
                UniverseItems.DEUTERIUM.key(),
                UniverseItems.SULFURIC_ACID.key());
    }
}
