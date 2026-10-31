package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.deepspace.DebrisChunk;
import io.github.sweetcornna.universe.deepspace.DerelictLightBlock;
import io.github.sweetcornna.universe.deepspace.Structures;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.poi.RandomModuleFunction;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * M19 深空和引力抓取器的数据生成（子代理 D，ADR-0034 §四、§七）：残骸方块的模型和掉落、打捞物的标签和价钱分类、
 * 补给箱和碎片的战利品、牵引光束模块和引力抓取器的配方、打捞物回炉。
 */
final class DeepSpaceDatagen {
    /** 残骸货舱开出来的东西（拆开残骸货舱方块掉的也是这一份）。 */
    static final ResourceKey<LootTable> HULK_CARGO = ResourceKey.create(Registries.LOOT_TABLE, Universe.id("chests/derelict_hulk"));

    private DeepSpaceDatagen() {
    }

    // ---- 模型

    static void models(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        for (Block block : List.of(UniverseBlocks.DERELICT_PLATING.get(), UniverseBlocks.DERELICT_FRAME.get(), UniverseBlocks.DERELICT_RADIATOR.get(),
                UniverseBlocks.REACTOR_SHIELDING.get(), UniverseBlocks.REACTOR_CORE.get(), UniverseBlocks.DERELICT_WINDOW.get(),
                UniverseBlocks.DERELICT_DECK.get(), UniverseBlocks.OUTPOST_ANTENNA.get())) {
            blockModels.createTrivialCube(block);
        }
        // 罐子、货舱、发动机、太阳能板：侧面和两头不一样
        for (Block block : List.of(UniverseBlocks.DERELICT_TANK.get(), UniverseBlocks.DERELICT_CARGO.get(), UniverseBlocks.DERELICT_ENGINE.get(),
                UniverseBlocks.OUTPOST_SOLAR.get())) {
            blockModels.createTrivialBlock(block, TexturedModel.COLUMN);
        }
        blockModels.createAxisAlignedPillarBlock(UniverseBlocks.DERELICT_CONDUIT.get(), TexturedModel.COLUMN);
        // 舱灯：灭着、亮着、报警（闪红光）
        Block light = UniverseBlocks.DERELICT_LIGHT.get();
        Identifier off = ModelTemplates.CUBE_ALL.create(light, TextureMapping.cube(texture("derelict_light")), blockModels.modelOutput);
        Identifier on = ModelTemplates.CUBE_ALL.createWithSuffix(light, "_on", TextureMapping.cube(texture("derelict_light_on")), blockModels.modelOutput);
        Identifier alarm = ModelTemplates.CUBE_ALL.createWithSuffix(light, "_alarm", TextureMapping.cube(texture("derelict_light_alarm")),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(light)
                .with(PropertyDispatch.initial(DerelictLightBlock.LIT, DerelictLightBlock.ALARM)
                        .generate((lit, alarming) -> BlockModelGenerators.plainVariant(alarming ? alarm : lit ? on : off))));
        // 前哨站的设备：正面是面板，朝向跟着放的方向转
        Block device = UniverseBlocks.OUTPOST_DEVICE.get();
        Identifier deviceModel = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(device, new TextureMapping()
                .put(TextureSlot.FRONT, texture("outpost_device_front"))
                .put(TextureSlot.SIDE, texture("outpost_device_side"))
                .put(TextureSlot.TOP, texture("outpost_device_top"))
                .put(TextureSlot.BOTTOM, texture("outpost_device_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(device, BlockModelGenerators.plainVariant(deviceModel))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        for (ItemLike item : List.of(UniverseItems.SCRAP_METAL.get(), UniverseItems.ALLOY_PLATE.get(), UniverseItems.SALVAGED_ELECTRONICS.get(),
                UniverseItems.SALVAGED_REACTOR_CORE.get())) {
            itemModels.generateFlatItem(item.asItem(), ModelTemplates.FLAT_ITEM);
        }
    }

    private static Material texture(String name) {
        return new Material(Universe.id("block/" + name));
    }

    // ---- 标签

    /** 残骸、前哨站的方块：用镐（多功能工具）拆，不能当成飞船的零件组装走。 */
    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        for (Block block : derelictBlocks()) {
            tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(block.builtInRegistryHolder().key());
            tag.apply(ShipParts.EXCLUDED).add(block.builtInRegistryHolder().key());
        }
        tag.apply(BlockTags.NEEDS_IRON_TOOL).add(UniverseBlocks.REACTOR_SHIELDING.key(), UniverseBlocks.REACTOR_CORE.key());
    }

    static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        tag.apply(ItemValues.tag(TradeCategory.RAW)).add(UniverseItems.SCRAP_METAL.key());
        tag.apply(ItemValues.tag(TradeCategory.REFINED)).add(UniverseItems.ALLOY_PLATE.key());
        tag.apply(ItemValues.tag(TradeCategory.COMPONENT)).add(UniverseItems.SALVAGED_ELECTRONICS.key());
        tag.apply(ItemValues.tag(TradeCategory.ENERGY)).add(UniverseItems.SALVAGED_REACTOR_CORE.key());
    }

    private static List<Block> derelictBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (var holder : UniverseBlocks.BLOCKS.getEntries()) {
            if (Structures.derelict(holder.get().defaultBlockState())) {
                blocks.add(holder.get());
            }
        }
        return blocks;
    }

    // ---- 战利品

    /**
     * 拆下来的残骸方块掉什么（ADR-0034 §四）：舱板、甲板、桁架是废金属，屏蔽层、散热板、发动机出合金板，导管和设备是电子元件，
     * 堆芯是打捞的反应堆芯；舷窗碎了什么都不剩。
     */
    static void blockLoot(BiConsumer<Block, LootTable.Builder> add) {
        add.accept(UniverseBlocks.DERELICT_PLATING.get(), drops(UniverseItems.SCRAP_METAL.get(), 1, 2));
        add.accept(UniverseBlocks.DERELICT_DECK.get(), drops(UniverseItems.SCRAP_METAL.get(), 1, 2));
        add.accept(UniverseBlocks.DERELICT_FRAME.get(), drops(UniverseItems.SCRAP_METAL.get(), 1, 1));
        add.accept(UniverseBlocks.DERELICT_TANK.get(), drops(UniverseItems.SCRAP_METAL.get(), 1, 3));
        add.accept(UniverseBlocks.DERELICT_RADIATOR.get(), LootTable.lootTable()
                .withPool(single(item(UniverseItems.SCRAP_METAL.get(), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.ALLOY_PLATE.get(), 1, 1, 1), 0.35F)));
        add.accept(UniverseBlocks.REACTOR_SHIELDING.get(), drops(UniverseItems.ALLOY_PLATE.get(), 1, 2));
        add.accept(UniverseBlocks.DERELICT_ENGINE.get(), LootTable.lootTable()
                .withPool(single(item(UniverseItems.ALLOY_PLATE.get(), 1, 2, 1)))
                .withPool(chance(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 1, 1), 0.3F)));
        add.accept(UniverseBlocks.DERELICT_CONDUIT.get(), LootTable.lootTable()
                .withPool(single(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.MAGNETIC_COIL.get(), 1, 2, 1), 0.4F)));
        add.accept(UniverseBlocks.DERELICT_LIGHT.get(), LootTable.lootTable()
                .withPool(chance(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 1, 1), 0.4F)));
        add.accept(UniverseBlocks.REACTOR_CORE.get(), drops(UniverseItems.SALVAGED_REACTOR_CORE.get(), 1, 1));
        add.accept(UniverseBlocks.DERELICT_CARGO.get(), hulkCargo());
        add.accept(UniverseBlocks.DERELICT_WINDOW.get(), LootTable.lootTable());
        add.accept(UniverseBlocks.OUTPOST_SOLAR.get(), LootTable.lootTable()
                .withPool(single(item(UniverseItems.SCRAP_METAL.get(), 1, 1, 1)))
                .withPool(chance(item(Items.QUARTZ, 1, 2, 1), 0.3F)));
        add.accept(UniverseBlocks.OUTPOST_ANTENNA.get(), LootTable.lootTable()
                .withPool(single(item(UniverseItems.SCRAP_METAL.get(), 1, 1, 1)))
                .withPool(chance(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 1, 1), 0.5F)));
        add.accept(UniverseBlocks.OUTPOST_DEVICE.get(), LootTable.lootTable()
                .withPool(single(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 2, 1)))
                .withPool(chance(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 2, 1), 0.5F))
                .withPool(chance(item(UniverseItems.PROCESSOR.get(), 1, 1, 1), 0.1F)));
    }

    /** 补给箱：残骸货舱、前哨站仓库。 */
    static void chestLoot(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> add) {
        add.accept(HULK_CARGO, hulkCargo());
        // 前哨站仓库：没人管的储备物资，常有导航数据，偶尔有一块模块
        add.accept(Structures.OUTPOST_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 3, 8))
                        .add(item(UniverseItems.HAZARD_CELL.get(), 1, 2, 6))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 6))
                        .add(item(UniverseItems.CIRCUIT_BOARD.get(), 1, 3, 5))
                        .add(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 2, 5))
                        .add(item(UniverseItems.ALLOY_PLATE.get(), 1, 3, 4))
                        .add(item(UniverseItems.SCRAP_METAL.get(), 2, 6, 6))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 2, 3)))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 2, 1), 0.6F))
                .withPool(chance(item(UniverseItems.SURVEY_DATA.get(), 1, 2, 1), 0.4F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.LIFE_SUPPORT).get())
                        .apply(RandomModuleFunction.randomModule(0)), 0.2F))
                // 留守的人做过的实验（ADR-0037 §五）
                .withPool(UniverseChestLoot.labNotes(0.35F)));
    }

    /** 残骸货舱：货船的货，多是材料，有打捞物，偶尔有一块飞船模块。 */
    private static LootTable.Builder hulkCargo() {
        return LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(2, 4))
                        .add(item(UniverseItems.SCRAP_METAL.get(), 2, 6, 8))
                        .add(item(UniverseItems.ALLOY_PLATE.get(), 1, 3, 6))
                        .add(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 2, 5))
                        .add(item(UniverseItems.STEEL.get(), 2, 5, 4))
                        .add(item(UniverseItems.TITANIUM_ALLOY.get(), 1, 3, 3))
                        .add(item(UniverseItems.LAUNCH_FUEL.get(), 1, 3, 4))
                        .add(item(UniverseItems.CREDIT_CHIP.get(), 1, 2, 3)))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get())
                        .apply(RandomModuleFunction.randomShipModule(0)), 0.1F))
                .withPool(UniverseChestLoot.labNotes(0.1F));
    }

    /**
     * 碎片：小块是废金属和一点合金，中块多出电子元件，大块有机会出打捞的反应堆芯和飞船模块。
     *
     * @param small 实体类型的默认表（小块）
     * @param add   另外两张，按 {@link DebrisChunk} 里的键
     */
    static void entityLoot(Consumer<LootTable.Builder> small, BiConsumer<ResourceKey<LootTable>, LootTable.Builder> add) {
        small.accept(LootTable.lootTable()
                .withPool(single(item(UniverseItems.SCRAP_METAL.get(), 1, 3, 1)))
                .withPool(chance(item(UniverseItems.ALLOY_PLATE.get(), 1, 1, 1), 0.25F)));
        add.accept(DebrisChunk.MEDIUM_LOOT, LootTable.lootTable()
                .withPool(single(item(UniverseItems.SCRAP_METAL.get(), 3, 6, 1)))
                .withPool(single(item(UniverseItems.ALLOY_PLATE.get(), 1, 2, 1)))
                .withPool(chance(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 2, 1), 0.5F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get())
                        .apply(RandomModuleFunction.randomShipModule(0)), 0.04F)));
        add.accept(DebrisChunk.LARGE_LOOT, LootTable.lootTable()
                .withPool(single(item(UniverseItems.SCRAP_METAL.get(), 6, 12, 1)))
                .withPool(single(item(UniverseItems.ALLOY_PLATE.get(), 2, 4, 1)))
                .withPool(single(item(UniverseItems.SALVAGED_ELECTRONICS.get(), 1, 3, 1)))
                .withPool(chance(item(UniverseItems.SALVAGED_REACTOR_CORE.get(), 1, 1, 1), 0.08F))
                .withPool(chance(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get())
                        .apply(RandomModuleFunction.randomShipModule(1)), 0.15F)));
    }

    private static LootTable.Builder drops(ItemLike item, int min, int max) {
        return LootTable.lootTable().withPool(single(item(item, min, max, 1)));
    }

    private static UniformContainerBase.Builder<?> item(ItemLike item, int min, int max, int weight) {
        return LootItem.lootTableItem(item).setWeight(weight)
                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)));
    }

    private static LootPool.Builder single(UniformContainerBase.Builder<?> entry) {
        return LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(entry);
    }

    private static LootPool.Builder chance(UniformContainerBase.Builder<?> entry, float probability) {
        return single(entry).when(LootItemRandomChanceCondition.randomChance(probability));
    }

    // ---- 配方

    static void recipes(RecipeOutput output) {
        // 牵引光束模块：和别的飞船模块一样在制造台里做，多一圈线圈
        machine(output, Kind.ASSEMBLING, ModuleType.TRACTOR_BEAM.itemName(), 100, "tractor_beam",
                in(UniverseItems.SENTINEL_SCRAP.get(), 2, UniverseItems.SURVEY_DATA.get(), 2, UniverseItems.MAGNETIC_COIL.get(), 4,
                        UniverseItems.CIRCUIT_BOARD.get(), 2),
                UniverseItems.MODULES.get(ModuleType.TRACTOR_BEAM).get(), 1);
        // 引力抓取器：线圈、电机，加上从残骸里拆出来的电子元件
        machine(output, Kind.ASSEMBLING, ToolWeapon.GRAVITY_COIL.id(), 240, "gravity_coil",
                in(UniverseItems.SENTINEL_SCRAP.get(), 4, UniverseItems.MAGNETIC_COIL.get(), 6, UniverseItems.ELECTRIC_MOTOR.get(), 2,
                        UniverseItems.SALVAGED_ELECTRONICS.get(), 2),
                UniverseItems.WEAPONS.get(ToolWeapon.GRAVITY_COIL).get(), 1);
        // 打捞物回炉、拆件：一件打捞物在进度模型里按一个矿算（Progression），回炉出来的东西不能比开矿做的便宜，
        // 不然会把矩阵、火箭的成本拉低。所以这几条都只是"不亏"，打捞物主要是卖钱；反应堆芯不回炉，只卖
        machine(output, Kind.SMELTING, "iron_from_scrap_metal", 40, "automatic_metallurgy", in(UniverseItems.SCRAP_METAL.get(), 4), Items.IRON_INGOT, 2);
        machine(output, Kind.SMELTING, "steel_from_scrap_metal", 120, "steel_smelting", in(UniverseItems.SCRAP_METAL.get(), 6),
                UniverseItems.STEEL.get(), 1);
        // 合金板是钛合金的，熔回去是钛锭：没去过产钛的星球也能先攒一点
        machine(output, Kind.SMELTING, "titanium_ingot_from_alloy_plate", 80, "", in(UniverseItems.ALLOY_PLATE.get(), 1),
                UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 1);
        machine(output, Kind.ASSEMBLING, "circuit_board_from_salvaged_electronics", 60, "basic_assembling",
                in(UniverseItems.SALVAGED_ELECTRONICS.get(), 3), UniverseItems.CIRCUIT_BOARD.get(), 2);
    }

    /** 设备配方，ID 是 {@code universe:<类型>/<名字>}，和 {@code UniverseRecipes} 的一样。 */
    private static void machine(RecipeOutput output, Kind kind, String name, int time, String research, List<SizedIngredient> ingredients,
            ItemLike result, int count) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(type + "/" + name)),
                new MachineRecipe(kind, ingredients, List.of(new ItemStackTemplate(result.asItem(), count)), time, research), null);
    }

    /** 原料：物品、数量交替排列。 */
    private static List<SizedIngredient> in(Object... itemsAndCounts) {
        List<SizedIngredient> list = new ArrayList<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            list.add(SizedIngredient.of((ItemLike) itemsAndCounts[i], (Integer) itemsAndCounts[i + 1]));
        }
        return list;
    }
}
