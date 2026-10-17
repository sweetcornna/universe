package io.github.sweetcornna.universe.datagen;

import java.util.Set;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/** 方块掉落。 */
final class UniverseBlockLoot extends BlockLootSubProvider {
    UniverseBlockLoot(LootTableSubProvider.Context context) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), context);
    }

    @Override
    protected void generate() {
        // 和原版石头、草方块一样：精准采集才掉本体
        this.add(UniverseBlocks.PLANET_ROCK.get(),
                block -> this.createSingleItemTableWithSilkTouch(block, UniverseBlocks.PLANET_COBBLESTONE.get()));
        this.add(UniverseBlocks.PLANET_TURF.get(),
                block -> this.createSingleItemTableWithSilkTouch(block, UniverseBlocks.PLANET_SOIL.get()));
        this.dropSelf(UniverseBlocks.PLANET_COBBLESTONE.get());
        this.dropSelf(UniverseBlocks.PLANET_SOIL.get());
        this.dropSelf(UniverseBlocks.PLANET_REGOLITH.get());
        this.dropSelf(UniverseBlocks.PLANET_SAND.get());
        this.dropSelf(UniverseBlocks.PLANET_SNOW.get());
        this.dropWhenSilkTouch(UniverseBlocks.PLANET_ICE.get());
        this.dropSelf(UniverseBlocks.PLANET_ASH.get());
        this.dropSelf(UniverseBlocks.PLANET_CRYSTAL.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Universe.MOD_ID))
                .toList();
    }
}
