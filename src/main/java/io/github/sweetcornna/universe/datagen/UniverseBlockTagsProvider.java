package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/** 方块标签：挖掘工具等。 */
final class UniverseBlockTagsProvider extends BlockTagsProvider {
    UniverseBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                UniverseBlocks.PLANET_ROCK.key(),
                UniverseBlocks.PLANET_COBBLESTONE.key(),
                UniverseBlocks.PLANET_ICE.key(),
                UniverseBlocks.PLANET_CRYSTAL.key());
        this.tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key(),
                UniverseBlocks.PLANET_REGOLITH.key(),
                UniverseBlocks.PLANET_SAND.key(),
                UniverseBlocks.PLANET_SNOW.key(),
                UniverseBlocks.PLANET_ASH.key());
        // 草皮、星壤能种树、长植物
        this.tag(BlockTags.DIRT).add(
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key());
        this.tag(BlockTags.SAND).add(UniverseBlocks.PLANET_SAND.key());
        this.tag(BlockTags.ICE).add(UniverseBlocks.PLANET_ICE.key());
        this.tag(BlockTags.BASE_STONE_OVERWORLD).add(UniverseBlocks.PLANET_ROCK.key());
    }
}
