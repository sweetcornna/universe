package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.block.poi.SalvageCapsuleBlock;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.encounter.SpaceEncounters;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.poi.LandmarkStructures;
import io.github.sweetcornna.universe.poi.OutpostStructures;
import io.github.sweetcornna.universe.poi.PoiStructures;
import io.github.sweetcornna.universe.poi.RandomModuleFunction;
import io.github.sweetcornna.universe.poi.RandomReactionFunction;
import io.github.sweetcornna.universe.poi.WildStructures;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * 兴趣点补给箱的战利品（ADR-0014）：坠毁飞船里多是飞船的物资，据点里多是生存物资；都有机会出导航数据和模块，
 * 坠毁飞船的模块品级高一档。太空里废弃货船的货箱和保险箱（ADR-0024）、海底的沉船和遗迹（ADR-0026）、
 * 伙伴挖出来的东西（ADR-0026 §九）也在这里。废弃货船、深空前哨站、残骸货舱里有机会出实验记录（ADR-0037 §五）。
 */
final class UniverseChestLoot implements LootTableSubProvider {
    private final LootTableSubProvider.Context context;

    UniverseChestLoot(LootTableSubProvider.Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        this.context.accept(PoiStructures.CRASHED_SHIP_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 6))
                        .add(item(Items.IRON_INGOT, 2, 6, 10))
                        .add(item(Items.COPPER_INGOT, 2, 6, 8))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 6))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 3, 6))
                        .add(item(UniverseItems.MAGNETIC_COIL.get(), 1, 2, 4))
                        .add(item(UniverseItems.GEAR.get(), 2, 4, 4))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 2, 5))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 2, 4))
                        .add(item(UniverseBlocks.HULL_PLATING.get(DyeColor.GRAY).get(), 2, 6, 4))
                        .add(item(UniverseItems.SPACE_WARPER.get(), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 2, 1), 0.75F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(1)), 0.4F))
                // 偶尔能拆到一件 Mk.II 部件（ADR-0017）
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.15F))
                        .add(item(UniverseItems.SHIP_THRUSTER_MK2.get(), 1, 2, 4))
                        .add(item(UniverseItems.FUEL_TANK_MK2.get(), 1, 1, 4))
                        .add(item(UniverseItems.ALLOY_HULL_PLATING.get(), 2, 6, 3))
                        .add(item(UniverseItems.IMPULSE_ENGINE_MK2.get(), 1, 1, 1))
                        .add(item(UniverseItems.WARP_DRIVE_MK2.get(), 1, 1, 1))));
        // 废弃货船（ADR-0024）：货箱里多是材料和飞船的物资，还有信用点芯片；船长的保险箱里是芯片、导航数据、飞船模块，
        // 偶尔有 Mk.II 部件和下一台应急信号接收器
        this.context.accept(SpaceEncounters.DERELICT_CARGO, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(Items.IRON_INGOT, 3, 8, 8))
                        .add(item(Items.COPPER_INGOT, 3, 8, 6))
                        .add(item(Items.GOLD_INGOT, 1, 3, 3))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 4, 6))
                        .add(item(UniverseItems.MAGNETIC_COIL.get(), 1, 3, 5))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 5))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 3, 4))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 2, 3))
                        .add(item(UniverseItems.ALLOY_HULL_PLATING.get(), 2, 6, 3))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 3, 6))
                        .add(item(UniverseItems.PIRATE_SCRAP.get(), 1, 2, 2)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 2, 1), 0.3F))
                .withPool(labNotes(0.15F)));
        this.context.accept(SpaceEncounters.DERELICT_SAFE, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(item(UniverseItems.CREDIT_CHIP.get(), 4, 10, 1)))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(item(UniverseItems.NAVIGATION_DATA.get(), 1, 3, 1)))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.FIRE_CONTROL).get())
                        .apply(RandomModuleFunction.randomShipModule(1)), 0.8F))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.3F))
                        .add(item(UniverseItems.SHIP_THRUSTER_MK2.get(), 1, 2, 4))
                        .add(item(UniverseItems.FUEL_TANK_MK2.get(), 1, 1, 4))
                        .add(item(UniverseItems.IMPULSE_ENGINE_MK2.get(), 1, 1, 2))
                        .add(item(UniverseItems.WARP_DRIVE_MK2.get(), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.EMERGENCY_RECEIVER.get(), 1, 1, 1), 0.25F))
                .withPool(labNotes(0.3F)));
        // 沉船（ADR-0026）：泡了很久的飞船，货舱里多是金属和飞船的物资，有信用点芯片；偶尔有一颗活珍珠、一个飞船模块、一件 Mk.II 部件
        this.context.accept(LandmarkStructures.SHIPWRECK_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 2, 5, 8))
                        .add(item(Items.IRON_INGOT, 2, 6, 8))
                        .add(item(Items.COPPER_INGOT, 2, 6, 6))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 3, 6))
                        .add(item(UniverseItems.MAGNETIC_COIL.get(), 1, 2, 4))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 5))
                        .add(item(UniverseBlocks.HULL_PLATING.get(DyeColor.GRAY).get(), 2, 6, 5))
                        .add(item(UniverseItems.ALLOY_HULL_PLATING.get(), 1, 3, 3))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 3, 4))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 2, 3)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 2, 1), 0.6F))
                .withPool(chance(item(UniverseItems.LIVING_PEARL.get(), 1, 1, 1), 0.2F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.FIRE_CONTROL).get())
                        .apply(RandomModuleFunction.randomShipModule(1)), 0.35F))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.2F))
                        .add(item(UniverseItems.SHIP_THRUSTER_MK2.get(), 1, 2, 4))
                        .add(item(UniverseItems.FUEL_TANK_MK2.get(), 1, 1, 4))
                        .add(item(UniverseItems.IMPULSE_ENGINE_MK2.get(), 1, 1, 2))
                        .add(item(UniverseItems.WARP_DRIVE_MK2.get(), 1, 1, 1))));
        // 海底遗迹（ADR-0026）：一处有三个密封箱，每个装得少一点：钛、金、信用点芯片、生存物资，常有活珍珠，有时有导航数据和外骨骼模块
        this.context.accept(LandmarkStructures.SEABED_RUIN_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 4))
                        .add(item(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 1, 4, 6))
                        .add(item(Items.GOLD_INGOT, 1, 3, 4))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 4, 6))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 3, 5))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 2, 4))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 2, 3))
                        .add(item(Items.PRISMARINE_SHARD, 2, 5, 3)))
                .withPool(chance(item(UniverseItems.LIVING_PEARL.get(), 1, 2, 1), 0.45F))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 1, 1), 0.5F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(1)), 0.4F)));
        // 打捞舱（ADR-0026）：1–5 块打捞的废料（越多越少见），一些零件和材料，有时一件 Mk.II 部件或一个飞船模块
        this.context.accept(SalvageCapsuleBlock.LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(item(UniverseItems.SALVAGED_SCRAP.get(), 1, 1, 5))
                        .add(item(UniverseItems.SALVAGED_SCRAP.get(), 2, 2, 4))
                        .add(item(UniverseItems.SALVAGED_SCRAP.get(), 3, 3, 3))
                        .add(item(UniverseItems.SALVAGED_SCRAP.get(), 4, 4, 2))
                        .add(item(UniverseItems.SALVAGED_SCRAP.get(), 5, 5, 1)))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 4))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 3, 6))
                        .add(item(UniverseItems.MAGNETIC_COIL.get(), 1, 3, 5))
                        .add(item(UniverseItems.GEAR.get(), 2, 4, 5))
                        .add(item(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 2, 4, 5))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 4))
                        .add(item(UniverseItems.ALLOY_HULL_PLATING.get(), 1, 3, 3))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 2, 3)))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.FIRE_CONTROL).get())
                        .apply(RandomModuleFunction.randomShipModule(1)), 0.3F))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.15F))
                        .add(item(UniverseItems.SHIP_THRUSTER_MK2.get(), 1, 1, 4))
                        .add(item(UniverseItems.FUEL_TANK_MK2.get(), 1, 1, 4))
                        .add(item(UniverseItems.IMPULSE_ENGINE_MK2.get(), 1, 1, 1))));
        // 废弃建筑（ADR-0026）：和据点差不多的生存物资，导航数据更常见
        this.context.accept(WildStructures.ABANDONED_BUILDING_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 3, 8))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 3, 8))
                        .add(item(UniverseItems.COOKED_ALIEN_MEAT.get(), 1, 3, 5))
                        .add(item(Items.IRON_INGOT, 1, 4, 6))
                        .add(item(Items.COPPER_INGOT, 2, 5, 5))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 2, 4))
                        .add(item(UniverseItems.MAGNETIC_COIL.get(), 1, 2, 3))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 2, 3))
                        .add(item(Items.PAPER, 2, 5, 3)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 2, 1), 0.6F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(0)), 0.25F)));
        // 交易站的补给箱（ADR-0026）：做买卖的地方，多是燃料、氧气、防护电池和信用点芯片，偶尔有导航数据和模块
        this.context.accept(OutpostStructures.TRADING_POST_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 8))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 3, 8))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 3, 7))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 3, 6))
                        .add(item(UniverseItems.COOKED_ALIEN_MEAT.get(), 1, 3, 5))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 2, 4))
                        .add(item(Items.IRON_INGOT, 2, 5, 4))
                        .add(item(UniverseItems.SPACE_WARPER.get(), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 1, 1), 0.4F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(0)), 0.2F)));
        this.context.accept(PoiStructures.OUTPOST_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(Items.BREAD, 2, 4, 6))
                        .add(item(UniverseItems.COOKED_ALIEN_MEAT.get(), 1, 3, 5))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 3, 8))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 3, 8))
                        .add(item(Items.IRON_INGOT, 1, 4, 6))
                        .add(item(Items.COPPER_INGOT, 2, 5, 6))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 2, 5))
                        .add(item(UniverseItems.GEAR.get(), 1, 3, 4))
                        .add(item(Items.REDSTONE, 2, 6, 4))
                        .add(item(Items.PAPER, 2, 5, 3)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 1, 1), 0.5F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(0)), 0.25F)));
        this.companionDig();
        // M19 钓鱼（A）的箱子战利品（调自己的方法）加在这行上面
        // M19 聚落（B）的箱子战利品（调自己的方法）加在这行上面
        FossilData.chestLoot(this.context::accept);
        // M19 化石（C）的箱子战利品（调自己的方法）加在这行上面
        DeepSpaceDatagen.chestLoot(this.context::accept);
        // M19 深空和重力枪（D）的箱子战利品（调自己的方法）加在这行上面
        // M19 科技布局和局部危害（E）的箱子战利品（调自己的方法）加在这行上面
    }

    /** 伙伴挖出来的（ADR-0026 §九）：一份矿石，心情好的（幸运 1）多一份；常有勘测数据，偶尔有技术模块。 */
    private void companionDig() {
        this.context.accept(AlienCreature.DIG_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).setBonusRolls(ContextFloatProviders.exactly(1))
                        .add(item(Items.RAW_IRON, 1, 3, 8))
                        .add(item(Items.RAW_COPPER, 2, 5, 8))
                        .add(item(Items.COAL, 1, 4, 6))
                        .add(item(UniverseItems.oreDrop(OreKind.TITANIUM), 1, 2, 5))
                        .add(item(UniverseItems.oreDrop(OreKind.SILICON), 1, 3, 5))
                        .add(item(UniverseItems.oreDrop(OreKind.SULFUR), 1, 3, 4))
                        .add(item(UniverseItems.oreDrop(OreKind.SALT), 2, 4, 4))
                        .add(item(UniverseItems.oreDrop(OreKind.ORGANIC_CRYSTAL), 1, 2, 2))
                        .add(item(UniverseItems.oreDrop(OreKind.OPTICAL_CRYSTAL), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.SURVEY_DATA.get(), 1, 2, 1), 0.35F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(0)), 0.05F)));
    }

    private static UniformContainerBase.Builder<?> item(ItemLike item, int min, int max, int weight) {
        return LootItem.lootTableItem(item).setWeight(weight)
                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)));
    }

    /** 一张写着随机一条反应的实验记录（ADR-0037 §五），按机会出。 */
    static LootPool.Builder labNotes(float probability) {
        return chance(LootItem.lootTableItem(UniverseItems.LAB_NOTES.get()).apply(RandomReactionFunction.randomReaction()), probability);
    }

    private static LootPool.Builder chance(UniformContainerBase.Builder<?> entry, float probability) {
        return LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(entry)
                .when(LootItemRandomChanceCondition.randomChance(probability));
    }
}
