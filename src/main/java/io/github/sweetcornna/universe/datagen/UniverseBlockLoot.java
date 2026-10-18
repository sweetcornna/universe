package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

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
        // 工业设备：两格高的只认下半截；蓄电器拆下来带着电量
        for (Block block : List.of(UniverseBlocks.TESLA_TOWER.get(), UniverseBlocks.WIND_TURBINE.get(), UniverseBlocks.THERMAL_PLANT.get(),
                UniverseBlocks.OIL_EXTRACTOR.get(), UniverseBlocks.OIL_REFINERY.get())) {
            this.add(block, this.createSinglePropConditionTable(block, TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        }
        this.add(UniverseBlocks.ACCUMULATOR.get(), block -> LootTable.lootTable().withPool(this.applyExplosionCondition(block,
                LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                                .include(UniverseDataComponents.STORED_ENERGY.get()))))));
        this.dropSelf(UniverseBlocks.ARC_SMELTER.get());
        this.dropSelf(UniverseBlocks.ASSEMBLER.get());
        this.dropSelf(UniverseBlocks.MINING_MACHINE.get());
        this.dropSelf(UniverseBlocks.WATER_PUMP.get());
        // 星球矿石：和原版矿石一样，精准采集掉本体，时运加成掉落
        UniverseBlocks.ORES.forEach((kind, block) -> this.add(block.get(), this.createOreDrops(block.get(), kind)));
    }

    private LootTable.Builder createOreDrops(Block block, OreKind kind) {
        Item drop = UniverseItems.oreDrop(kind);
        if (kind.minDrops() == 1 && kind.maxDrops() == 1) {
            return this.createOreDrop(block, drop);
        }
        return this.createSilkTouchDispatchTable(block, (LootPoolEntryContainer.Builder<?>) this.applyExplosionDecay(block,
                LootItem.lootTableItem(drop)
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(kind.minDrops(), kind.maxDrops())))
                        .apply(ApplyBonusCount.addOreBonusCount(this.enchantments.getOrThrow(Enchantments.FORTUNE)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return BuiltInRegistries.BLOCK.stream()
                .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Universe.MOD_ID))
                .toList();
    }
}
