package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.poi.RandomModuleFunction;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.ClamBlock;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
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
        this.dropSelf(UniverseBlocks.DAMAGED_HELM.get());
        this.dropSelf(UniverseBlocks.SHIP_THRUSTER.get());
        this.dropSelf(UniverseBlocks.FUEL_TANK.get());
        this.dropSelf(UniverseBlocks.IMPULSE_ENGINE.get());
        this.dropSelf(UniverseBlocks.WARP_DRIVE.get());
        this.dropSelf(UniverseBlocks.SHIP_SEAT.get());
        for (Block block : List.of(UniverseBlocks.PHOTON_CANNON.get(), UniverseBlocks.MINING_BEAM.get(), UniverseBlocks.SHIELD_GENERATOR.get(),
                UniverseBlocks.SHIP_THRUSTER_MK2.get(), UniverseBlocks.FUEL_TANK_MK2.get(), UniverseBlocks.IMPULSE_ENGINE_MK2.get(),
                UniverseBlocks.WARP_DRIVE_MK2.get(), UniverseBlocks.ALLOY_HULL_PLATING.get())) {
            this.dropSelf(block);
        }
        UniverseBlocks.HULL_PLATING.values().forEach(block -> this.dropSelf(block.get()));
        // 工业设备：两格高的只认下半截；蓄电器拆下来带着电量
        for (Block block : List.of(UniverseBlocks.TESLA_TOWER.get(), UniverseBlocks.WIND_TURBINE.get(), UniverseBlocks.THERMAL_PLANT.get(),
                UniverseBlocks.OIL_EXTRACTOR.get(), UniverseBlocks.OIL_REFINERY.get(), UniverseBlocks.VERTICAL_LAUNCHING_SILO.get(),
                UniverseBlocks.ARTIFICIAL_STAR.get(), UniverseBlocks.WIRELESS_POWER_TOWER.get(), UniverseBlocks.SATELLITE_SUBSTATION.get())) {
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
        this.dropSelf(UniverseBlocks.CONVEYOR_BELT.get());
        this.dropSelf(UniverseBlocks.SORTER.get());
        this.dropSelf(UniverseBlocks.MATRIX_LAB.get());
        this.dropSelf(UniverseBlocks.STORAGE_DEPOT.get());
        // 物流（ADR-0015）：储液罐拆下来带着里面的流体
        this.dropSelf(UniverseBlocks.CONVEYOR_BELT_MK2.get());
        this.dropSelf(UniverseBlocks.CONVEYOR_BELT_MK3.get());
        this.dropSelf(UniverseBlocks.SORTER_MK2.get());
        this.dropSelf(UniverseBlocks.SORTER_MK3.get());
        this.dropSelf(UniverseBlocks.SPLITTER.get());
        this.dropSelf(UniverseBlocks.ASSEMBLER_MK2.get());
        this.dropSelf(UniverseBlocks.ASSEMBLER_MK3.get());
        this.dropSelf(UniverseBlocks.PLANE_SMELTER.get());
        this.dropSelf(UniverseBlocks.CHEMICAL_PLANT.get());
        this.dropSelf(UniverseBlocks.FRACTIONATOR.get());
        this.dropSelf(UniverseBlocks.SOLAR_PANEL.get());
        this.dropSelf(UniverseBlocks.ADVANCED_MINING_MACHINE.get());
        this.dropSelf(UniverseBlocks.FACTORY_ANCHOR.get());
        // 戴森球（ADR-0016）
        this.dropSelf(UniverseBlocks.EM_RAIL_EJECTOR.get());
        this.dropSelf(UniverseBlocks.RAY_RECEIVER.get());
        this.dropSelf(UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.get());
        this.dropSelf(UniverseBlocks.MINI_FUSION_POWER_PLANT.get());
        // 工厂的补全（ADR-0022）
        for (Block block : List.of(UniverseBlocks.SPRAY_COATER.get(), UniverseBlocks.AUTOMATIC_PILER.get(), UniverseBlocks.TRAFFIC_MONITOR.get(),
                UniverseBlocks.PILE_SORTER.get(), UniverseBlocks.GEOTHERMAL_POWER_STATION.get(), UniverseBlocks.ENERGY_EXCHANGER.get(),
                UniverseBlocks.NEGENTROPY_SMELTER.get(), UniverseBlocks.RECOMPOSING_ASSEMBLER.get(), UniverseBlocks.QUANTUM_CHEMICAL_PLANT.get(),
                UniverseBlocks.SELF_EVOLUTION_LAB.get(), UniverseBlocks.FOUNDATION.get())) {
            this.dropSelf(block);
        }
        // 配送（ADR-0023）
        this.dropSelf(UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        this.add(UniverseBlocks.STORAGE_TANK.get(), block -> LootTable.lootTable().withPool(this.applyExplosionCondition(block,
                LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                                .include(UniverseDataComponents.TANK_CONTENTS.get()))))));
        // 物流站：拆下来带着物品、载具和设置（像潜影盒一样）
        this.station(UniverseBlocks.PLANETARY_LOGISTICS_STATION.get(), true);
        this.station(UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get(), true);
        this.station(UniverseBlocks.ORBITAL_COLLECTOR.get(), false);
        // 兴趣点（ADR-0014）：掉落舱、石碑挖不掉；科技储藏箱掉一个随机模块，有时多一份勘探数据；冒烟的残骸拆出船体板
        this.dropSelf(UniverseBlocks.DATA_TERMINAL.get());
        this.dropSelf(UniverseBlocks.SIGNAL_BOOSTER.get());
        // 基地和传送（ADR-0017）：空间站传送器挖不掉
        this.dropSelf(UniverseBlocks.BASE_BEACON.get());
        this.dropSelf(UniverseBlocks.TELEPORTER.get());
        this.dropSelf(UniverseBlocks.LANDING_PAD.get());
        this.dropSelf(UniverseBlocks.SUPPLY_CRATE.get());
        this.add(UniverseBlocks.SMOLDERING_WRECK.get(), this.createSingleItemTable(UniverseBlocks.HULL_PLATING.get(DyeColor.GRAY).get()));
        this.add(UniverseBlocks.TECH_CACHE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                                .apply(RandomModuleFunction.randomModule(0))))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.SURVEY_DATA.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.35F))));
        // 资源植物（ADR-0026）：长熟的打掉也掉产物，收过的什么也不掉；植物本身拿不走
        UniverseBlocks.RESOURCE_PLANTS.forEach((plant, block) -> this.add(block.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.RESOURCES.get(plant.product()).get())
                                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(plant.wildMin(), plant.wildMax()))))
                        .when(MatchBlock.blockMatches(this.blocks, block.get(),
                                StatePropertiesPredicate.Builder.properties().hasProperty(ResourcePlantBlock.RIPE, true))))));
        this.dropSelf(UniverseBlocks.HYDROPONIC_TRAY.get());
        this.dropSelf(UniverseBlocks.NUTRIENT_PROCESSOR.get());
        this.dropSelf(UniverseBlocks.VEHICLE_SUMMONER.get());
        // 水下（ADR-0026）：没撬开的蚌掉里面的活珍珠，精准采集整个拿走（放下还是没撬开的）；撬开的空壳什么也不掉。
        // 海底密封箱和补给箱一样能拆走
        Block clam = UniverseBlocks.CLAM.get();
        this.add(clam, LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                .when(MatchBlock.blockMatches(this.blocks, clam, StatePropertiesPredicate.Builder.properties().hasProperty(ClamBlock.OPEN, false)))
                .add(LootItem.lootTableItem(clam).when(this.hasSilkTouch()).otherwise(LootItem.lootTableItem(UniverseItems.LIVING_PEARL.get())))));
        this.dropSelf(UniverseBlocks.SEALED_CRATE.get());
        // 野外的地标（ADR-0026）：化石土挖出一块化石（普通七成、少见两成半、稀有半成），精准采集拿走化石土本身；
        // 化石土还有四成的机会多出一块骨头，类群按星球抽（ADR-0034 §三）；
        // 打捞舱挖的时候先打开（东西在打开时倒出来），舱壳掉两块灰色船体板；雷击石掉自己
        Block fossilDirt = UniverseBlocks.FOSSIL_DIRT.get();
        this.add(fossilDirt, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(fossilDirt).when(this.hasSilkTouch())))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).when(this.doesNotHaveSilkTouch())
                        .add(LootItem.lootTableItem(UniverseItems.SHELL_FOSSIL.get()).setWeight(14))
                        .add(LootItem.lootTableItem(UniverseItems.ARTHROPOD_FOSSIL.get()).setWeight(5))
                        .add(LootItem.lootTableItem(UniverseItems.SKULL_FOSSIL.get()).setWeight(1)))
                .withPool(FossilData.dirtBones(LootPool.lootPool().when(this.doesNotHaveSilkTouch()))));
        this.add(UniverseBlocks.SALVAGE_CAPSULE.get(), LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                .add(LootItem.lootTableItem(UniverseBlocks.HULL_PLATING.get(DyeColor.GRAY).get())
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.exactly(2))))));
        this.dropSelf(UniverseBlocks.FULGURITE.get());
        // 便携精炼机：拆下来带着里面的东西和选好的配方（ADR-0026）
        this.add(UniverseBlocks.PORTABLE_REFINER.get(), block -> LootTable.lootTable().withPool(this.applyExplosionCondition(block,
                LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                                .include(DataComponents.CONTAINER).include(DataComponents.CUSTOM_DATA))))));
        // 生物卵：挖下来还是那个卵（ADR-0026 §九）
        this.add(UniverseBlocks.CREATURE_EGG.get(), block -> LootTable.lootTable().withPool(this.applyExplosionCondition(block,
                LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                                .include(UniverseDataComponents.CREATURE_EGG.get()))))));
        this.dropSelf(UniverseBlocks.EGG_SEQUENCER.get());
        // 星球矿石：和原版矿石一样，精准采集掉本体，时运加成掉落
        UniverseBlocks.ORES.forEach((kind, block) -> this.add(block.get(), this.createOreDrops(block.get(), kind)));
        this.darkFogBases();
        this.darkFogFacilities();
        this.darkFogTurrets();
        this.darkFogSupport();
        this.darkFogPlasma();
        this.darkFogShield();
        this.wrapUpAcid();
        this.wrapUpInterop();
        // M18 舰队和战斗（A）的战利品表加在这行上面
        // M18 物流调度（B）的战利品表加在这行上面
        // M18 统计和研究（C）的战利品表加在这行上面
        this.dropSelf(UniverseBlocks.HOLO_BEACON.get());
        // M18 信标、透镜、矿脉、蓝图（D）的战利品表加在这行上面
        this.dropSelf(UniverseBlocks.FISH_TRAP.get());
        // M19 钓鱼（A）的战利品表加在这行上面
        // M19 聚落（B）的战利品表加在这行上面
        FossilData.blockLoot(this::dropSelf);
        // M19 化石（C）的战利品表加在这行上面
        DeepSpaceDatagen.blockLoot(this::add);
        // M19 深空和重力枪（D）的战利品表加在这行上面
        // 间歇泉（ADR-0034 §六）：喷口只有精准采集拿得走；结壳精准采集掉本体，不然掉矿物，时运加成
        UniverseBlocks.GEYSER_VENTS.values().forEach(block -> this.add(block.get(), this.createSilkTouchOnlyTable(block.get())));
        UniverseBlocks.GEYSER_CRUSTS.forEach((kind, block) -> this.add(block.get(), this.createSilkTouchDispatchTable(block.get(),
                (LootPoolEntryContainer.Builder<?>) this.applyExplosionDecay(block.get(), LootItem.lootTableItem(
                                BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(kind.crustDrop())))
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(kind.crustMin(), kind.crustMax())))
                        .apply(ApplyBonusCount.addOreBonusCount(this.enchantments.getOrThrow(Enchantments.FORTUNE)))))));
        // M19 科技布局和局部危害（E）的战利品表加在这行上面
        ChemistryDatagen.blockLoot(this::dropSelf);
        // M20 化学（A）的战利品表加在这行上面
        this.dropSelf(UniverseBlocks.CHEMICAL_REACTOR.get());
        // M20 反应釜（B）的战利品表加在这行上面
        MicrobeDatagen.blockLoot(this::dropSelf);
        // M20 微生物和基因（C）的战利品表加在这行上面
        this.dropSelf(UniverseBlocks.BIOREACTOR.get());
        // M20 生物反应器（D）的战利品表加在这行上面
        TerraformDatagen.blockLoot(this::dropSelf);
        // M20 星球改造（E）的战利品表加在这行上面
    }

    /** 黑雾基地的方块（子代理 P）。 */
    private void darkFogBases() {
    }

    /**
     * M17 收尾的战利品表：酸液（子代理 A，ADR-0030）。
     *
     * <p>酸液方块和原版的水一样注册成 {@code noLootTable()}，没有战利品表，这里不用写；拿桶装走才是取酸液的办法。
     */
    private void wrapUpAcid() {
    }

    /** M17 收尾的战利品表：互通（子代理 C，ADR-0030）。 */
    private void wrapUpInterop() {
        this.dropSelf(UniverseBlocks.GRID_INTERFACE.get());
    }

    /** 残影、战场分析基地（子代理 Q）。 */
    private void darkFogFacilities() {
        this.dropSelf(UniverseBlocks.BATTLEFIELD_ANALYSIS_BASE.get());
    }

    /** 炮塔（子代理 R1）。 */
    private void darkFogTurrets() {
        this.dropSelf(UniverseBlocks.GAUSS_TURRET.get());
        this.dropSelf(UniverseBlocks.LASER_TURRET.get());
        this.dropSelf(UniverseBlocks.IMPLOSION_CANNON.get());
        this.dropSelf(UniverseBlocks.MISSILE_TURRET.get());
    }

    /** 等离子炮塔（ADR-0028 §八，子代理 T）。 */
    private void darkFogPlasma() {
        this.dropSelf(UniverseBlocks.PLASMA_TURRET.get());
        this.dropSelf(UniverseBlocks.SR_PLASMA_TURRET.get());
    }

    /** 行星护盾发生器（子代理 S）。 */
    private void darkFogShield() {
        // 两格高，只认下半截
        Block block = UniverseBlocks.PLANETARY_SHIELD_GENERATOR.get();
        this.add(block, this.createSinglePropConditionTable(block, TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
    }

    /** 信号塔、干扰塔（子代理 R2）。 */
    private void darkFogSupport() {
        // 两格高，只认下半截
        for (Block block : List.of(UniverseBlocks.SIGNAL_TOWER.get(), UniverseBlocks.JAMMER_TOWER.get())) {
            this.add(block, this.createSinglePropConditionTable(block, TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        }
    }

    private void station(Block block, boolean tall) {
        UniformContainerBase.Builder<?> item = LootItem.lootTableItem(block)
                .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                        .include(UniverseDataComponents.STATION_CONTENTS.get()));
        if (tall) {
            item.when(MatchBlock.blockMatches(this.blocks, block,
                    StatePropertiesPredicate.Builder.properties().hasProperty(TallMachineBlock.HALF, DoubleBlockHalf.LOWER)));
        }
        this.add(block, LootTable.lootTable().withPool(this.applyExplosionCondition(block,
                LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(item))));
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
