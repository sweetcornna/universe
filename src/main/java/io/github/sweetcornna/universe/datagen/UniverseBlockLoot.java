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
        this.dropSelf(UniverseBlocks.PLANET_STRATA.get());
        // 植物：用剪刀或精准采集才掉本体（和原版的草一样）
        UniverseBlocks.PLANTS.values().forEach(block -> this.add(block.get(), this.createShearsOrSilkTouchOnlyDrop(block.get())));
        this.dropSelf(UniverseBlocks.PLANET_LOG.get());
        this.add(UniverseBlocks.PLANET_LEAVES.get(), this.createShearsOrSilkTouchOnlyDrop(UniverseBlocks.PLANET_LEAVES.get()));
        this.dropSelf(UniverseBlocks.PLANET_CAP.get());
        this.dropSelf(UniverseBlocks.PLANET_BLOOM.get());
        // 飞船部件拆下来原样掉落（燃料舱里的燃料不保留）
        this.dropSelf(UniverseBlocks.SHIP_HELM.get());
        this.dropSelf(UniverseBlocks.SHIP_THRUSTER.get());
        this.dropSelf(UniverseBlocks.FUEL_TANK.get());
        this.dropSelf(UniverseBlocks.IMPULSE_ENGINE.get());
        this.dropSelf(UniverseBlocks.SHIP_SEAT.get());
        UniverseBlocks.HULL_PLATING.values().forEach(block -> this.dropSelf(block.get()));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Universe.MOD_ID))
                .toList();
    }
}
