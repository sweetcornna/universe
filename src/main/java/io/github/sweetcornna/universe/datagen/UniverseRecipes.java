package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** 配方（26.3 里配方是数据包注册表的条目，走 {@code GatherDataRegistryEntriesEvent}）。 */
final class UniverseRecipes extends RecipeProvider {
    UniverseRecipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
        super(recipes, advancements);
    }

    @Override
    protected void buildRecipes() {
        // 烤肉：熔炉、烟熏炉、营火，时间和原版的牛肉一样
        this.cookMeat("smelting", SmeltingRecipe::new, 200);
        this.cookMeat("smoking", SmokingRecipe::new, 100);
        this.cookMeat("campfire_cooking", CampfireCookingRecipe::new, 600);
        // 兽皮鞣成皮革
        this.shapeless(RecipeCategory.MISC, Items.LEATHER)
                .requires(UniverseItems.ALIEN_HIDE.get(), 2)
                .unlockedBy(getHasName(UniverseItems.ALIEN_HIDE.get()), this.has(UniverseItems.ALIEN_HIDE.get()))
                .save(this.output, "universe:leather_from_alien_hide");
        this.ships();
        this.ores();
        this.suit();
        this.multitool();
        this.factory();
    }

    /**
     * 工业（ADR-0011）：中间产品一开始就能手工合成（戴森球计划的电磁阶段），设备要研究解锁；设备配方按戴森球计划的
     * 原料比例和时间来，单位时间是刻（戴森球计划的秒数 × 20）。
     */
    private void factory() {
        Item magnet = UniverseItems.ORE_SMELTED.get(OreKind.MAGNETIC).get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item gear = UniverseItems.GEAR.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item prism = UniverseItems.PRISM.get();
        Item graphite = UniverseItems.ENERGETIC_GRAPHITE.get();
        Item hydrogen = UniverseItems.HYDROGEN.get();
        Item silicon = UniverseItems.ORE_SMELTED.get(OreKind.SILICON).get();

        // ---- 手工合成：中间产品
        // 早期的磁铁：铁锭加红石；电弧熔炉直接把粗铁、粗磁铁烧成磁铁
        this.shapeless(RecipeCategory.MISC, magnet)
                .requires(Items.IRON_INGOT).requires(Items.REDSTONE)
                .unlockedBy(getHasName(Items.REDSTONE), this.has(Items.REDSTONE))
                .save(this.output, "universe:magnet_from_redstone");
        this.shapeless(RecipeCategory.MISC, gear)
                .requires(Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.IRON_INGOT), this.has(Items.IRON_INGOT))
                .save(this.output);
        this.shaped(RecipeCategory.MISC, coil, 2)
                .pattern("M").pattern("C").pattern("M")
                .define('M', magnet).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(magnet), this.has(magnet))
                .save(this.output);
        this.shaped(RecipeCategory.MISC, board, 2)
                .pattern("ICI")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output);
        // 要研究的中间产品
        this.researchShaped("electromagnetic_drive", RecipeCategory.MISC, UniverseItems.ELECTRIC_MOTOR.get(), 1,
                List.of("ICI", " G "), Map.of('I', Items.IRON_INGOT, 'C', coil, 'G', gear));
        this.researchShaped("plasma_refining", RecipeCategory.MISC, prism, 2,
                List.of("GG", "G "), Map.of('G', Items.GLASS));
        this.researchShaped("plasma_refining", RecipeCategory.MISC, UniverseItems.PLASMA_EXCITER.get(), 1,
                List.of("CPC", "CPC"), Map.of('C', coil, 'P', prism));

        // ---- 手工合成：物流
        this.researchShaped("basic_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.CONVEYOR_BELT.get(), 3,
                List.of("IGI"), Map.of('I', Items.IRON_INGOT, 'G', gear));
        this.researchShaped("electromagnetic_matrix", RecipeCategory.REDSTONE, UniverseItems.MATRIX_LAB.get(), 1,
                List.of("IGI", "CMC", "IGI"), Map.of('I', Items.IRON_INGOT, 'G', Items.GLASS, 'C', board, 'M', coil));
        this.researchShaped("basic_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.SORTER.get(), 1,
                List.of("I", "B"), Map.of('I', Items.IRON_INGOT, 'B', board));
        this.researchShaped("basic_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.STORAGE_DEPOT.get(), 1,
                List.of("ISI", "S S", "ISI"), Map.of('I', Items.IRON_INGOT, 'S', Items.STONE));

        // ---- 手工合成：设备
        this.researchShaped("electromagnetism", RecipeCategory.REDSTONE, UniverseItems.TESLA_TOWER.get(), 1,
                List.of("C", "I", "I"), Map.of('C', coil, 'I', Items.IRON_INGOT));
        this.researchShaped("electromagnetism", RecipeCategory.REDSTONE, UniverseItems.WIND_TURBINE.get(), 1,
                List.of("IGI", "CIC", "IGI"), Map.of('I', Items.IRON_INGOT, 'G', gear, 'C', coil));
        this.researchShaped("automatic_metallurgy", RecipeCategory.REDSTONE, UniverseItems.ARC_SMELTER.get(), 1,
                List.of("IMI", "CFC", "BBB"),
                Map.of('I', Items.IRON_INGOT, 'M', coil, 'C', board, 'F', Items.BLAST_FURNACE, 'B', Items.STONE_BRICKS));
        this.researchShaped("basic_assembling", RecipeCategory.REDSTONE, UniverseItems.ASSEMBLER.get(), 1,
                List.of("IGI", "CTC", "IGI"), Map.of('I', Items.IRON_INGOT, 'G', gear, 'C', board, 'T', Items.CRAFTING_TABLE));
        this.researchShaped("thermal_power", RecipeCategory.REDSTONE, UniverseItems.THERMAL_PLANT.get(), 1,
                List.of("BCB", "GFG", "BIB"),
                Map.of('B', Items.STONE_BRICKS, 'C', coil, 'G', gear, 'F', Items.FURNACE, 'I', Items.IRON_INGOT));
        this.researchShaped("electromagnetism", RecipeCategory.REDSTONE, UniverseItems.MINING_MACHINE.get(), 1,
                List.of("IGI", "BPB", "IMI"),
                Map.of('I', Items.IRON_INGOT, 'G', gear, 'B', board, 'P', Items.IRON_PICKAXE, 'M', coil));
        this.researchShaped("fluid_storage", RecipeCategory.REDSTONE, UniverseItems.WATER_PUMP.get(), 1,
                List.of("IMI", "BUB", "IMI"),
                Map.of('I', Items.IRON_INGOT, 'M', UniverseItems.ELECTRIC_MOTOR.get(), 'B', board, 'U', Items.BUCKET));
        this.researchShaped("plasma_refining", RecipeCategory.REDSTONE, UniverseItems.OIL_EXTRACTOR.get(), 1,
                List.of("SES", "BPB", "KKK"),
                Map.of('S', UniverseItems.STEEL.get(), 'E', UniverseItems.PLASMA_EXCITER.get(), 'B', board, 'P', Items.PISTON,
                        'K', Items.STONE_BRICKS));
        this.researchShaped("plasma_refining", RecipeCategory.REDSTONE, UniverseItems.OIL_REFINERY.get(), 1,
                List.of("SES", "BFB", "KKK"),
                Map.of('S', UniverseItems.STEEL.get(), 'E', UniverseItems.PLASMA_EXCITER.get(), 'B', board, 'F', Items.BLAST_FURNACE,
                        'K', Items.STONE_BRICKS));
        this.researchShaped("energy_storage", RecipeCategory.REDSTONE, UniverseItems.ACCUMULATOR.get(), 1,
                List.of("IRI", "CSC", "IRI"),
                Map.of('I', Items.IRON_INGOT, 'R', Items.REDSTONE, 'C', coil, 'S', silicon));

        // ---- 电弧熔炉
        Ingredient cobblestone = Ingredient.of(Items.COBBLESTONE, UniverseBlocks.PLANET_COBBLESTONE.get());
        this.machine(Kind.SMELTING, "iron_ingot", 20, "", in(Items.RAW_IRON, 1), out(Items.IRON_INGOT, 1));
        this.machine(Kind.SMELTING, "copper_ingot", 20, "", in(Items.RAW_COPPER, 1), out(Items.COPPER_INGOT, 1));
        this.machine(Kind.SMELTING, "gold_ingot", 20, "", in(Items.RAW_GOLD, 1), out(Items.GOLD_INGOT, 1));
        this.machine(Kind.SMELTING, "magnet", 30, "", in(Items.RAW_IRON, 1), out(magnet, 1));
        this.machine(Kind.SMELTING, "magnet_from_magnetite", 30, "",
                in(UniverseItems.oreDrop(OreKind.MAGNETIC), 1), out(magnet, 2));
        this.machine(Kind.SMELTING, "stone_bricks", 20, "", List.of(new SizedIngredient(cobblestone, 1)), out(Items.STONE_BRICKS, 1));
        this.machine(Kind.SMELTING, "glass", 40, "", List.of(new SizedIngredient(Ingredient.of(Items.SAND, Items.RED_SAND), 1)),
                out(Items.GLASS, 1));
        this.machine(Kind.SMELTING, "energetic_graphite", 40, "", in(Items.COAL, 2), out(graphite, 1));
        this.machine(Kind.SMELTING, "silicon", 40, "", in(UniverseItems.oreDrop(OreKind.SILICON), 1), out(silicon, 1));
        this.machine(Kind.SMELTING, "titanium_ingot", 40, "", in(UniverseItems.oreDrop(OreKind.TITANIUM), 1),
                out(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 1));
        this.machine(Kind.SMELTING, "uranium_ingot", 40, "", in(UniverseItems.oreDrop(OreKind.URANIUM), 1),
                out(UniverseItems.ORE_SMELTED.get(OreKind.URANIUM).get(), 1));
        this.machine(Kind.SMELTING, "steel", 60, "steel_smelting", in(Items.IRON_INGOT, 3), out(UniverseItems.STEEL.get(), 1));

        // ---- 制造台
        this.machine(Kind.ASSEMBLING, "gear", 20, "", in(Items.IRON_INGOT, 1), out(gear, 1));
        this.machine(Kind.ASSEMBLING, "magnetic_coil", 20, "", in(magnet, 2, Items.COPPER_INGOT, 1), out(coil, 2));
        this.machine(Kind.ASSEMBLING, "circuit_board", 20, "", in(Items.IRON_INGOT, 2, Items.COPPER_INGOT, 1), out(board, 2));
        this.machine(Kind.ASSEMBLING, "electric_motor", 40, "electromagnetic_drive",
                in(Items.IRON_INGOT, 2, gear, 1, coil, 1), out(UniverseItems.ELECTRIC_MOTOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "prism", 40, "plasma_refining", in(Items.GLASS, 3), out(prism, 2));
        this.machine(Kind.ASSEMBLING, "plasma_exciter", 40, "plasma_refining", in(coil, 4, prism, 2),
                out(UniverseItems.PLASMA_EXCITER.get(), 1));
        this.machine(Kind.ASSEMBLING, "electromagnetic_matrix", 60, "electromagnetic_matrix", in(coil, 1, board, 1),
                out(UniverseItems.ELECTROMAGNETIC_MATRIX.get(), 1));
        this.machine(Kind.ASSEMBLING, "energy_matrix", 120, "energy_matrix", in(graphite, 2, hydrogen, 2),
                out(UniverseItems.ENERGY_MATRIX.get(), 1));
        // 设备也能在制造台里做，方便自动化
        this.machine(Kind.ASSEMBLING, "conveyor_belt", 20, "basic_logistics", in(Items.IRON_INGOT, 2, gear, 1),
                out(UniverseItems.CONVEYOR_BELT.get(), 3));
        this.machine(Kind.ASSEMBLING, "matrix_lab", 60, "electromagnetic_matrix",
                in(Items.IRON_INGOT, 4, Items.GLASS, 2, board, 2, coil, 1), out(UniverseItems.MATRIX_LAB.get(), 1));
        this.machine(Kind.ASSEMBLING, "sorter", 20, "basic_logistics", in(Items.IRON_INGOT, 1, board, 1),
                out(UniverseItems.SORTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "storage_depot", 40, "basic_logistics", in(Items.IRON_INGOT, 4, Items.STONE, 4),
                out(UniverseItems.STORAGE_DEPOT.get(), 1));
        this.machine(Kind.ASSEMBLING, "tesla_tower", 20, "electromagnetism", in(Items.IRON_INGOT, 2, coil, 1),
                out(UniverseItems.TESLA_TOWER.get(), 1));
        this.machine(Kind.ASSEMBLING, "wind_turbine", 80, "electromagnetism", in(Items.IRON_INGOT, 6, gear, 1, coil, 3),
                out(UniverseItems.WIND_TURBINE.get(), 1));
        this.machine(Kind.ASSEMBLING, "arc_smelter", 60, "automatic_metallurgy",
                in(Items.IRON_INGOT, 4, Items.STONE_BRICKS, 2, board, 4, coil, 2), out(UniverseItems.ARC_SMELTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "assembler", 60, "basic_assembling", in(Items.IRON_INGOT, 4, gear, 8, board, 4),
                out(UniverseItems.ASSEMBLER.get(), 1));
        this.machine(Kind.ASSEMBLING, "thermal_plant", 100, "thermal_power",
                in(Items.IRON_INGOT, 10, Items.STONE_BRICKS, 4, gear, 4, coil, 4), out(UniverseItems.THERMAL_PLANT.get(), 1));
        this.machine(Kind.ASSEMBLING, "accumulator", 100, "energy_storage", in(Items.IRON_INGOT, 6, coil, 4, silicon, 2),
                out(UniverseItems.ACCUMULATOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "mining_machine", 60, "electromagnetism", in(Items.IRON_INGOT, 4, board, 2, coil, 2, gear, 2),
                out(UniverseItems.MINING_MACHINE.get(), 1));
        this.machine(Kind.ASSEMBLING, "water_pump", 80, "fluid_storage",
                in(Items.IRON_INGOT, 3, Items.STONE_BRICKS, 2, UniverseItems.ELECTRIC_MOTOR.get(), 4, board, 2),
                out(UniverseItems.WATER_PUMP.get(), 1));
        this.machine(Kind.ASSEMBLING, "oil_extractor", 160, "plasma_refining",
                in(UniverseItems.STEEL.get(), 12, Items.STONE_BRICKS, 12, board, 6, UniverseItems.PLASMA_EXCITER.get(), 4),
                out(UniverseItems.OIL_EXTRACTOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "oil_refinery", 120, "plasma_refining",
                in(UniverseItems.STEEL.get(), 10, Items.STONE_BRICKS, 10, board, 6, UniverseItems.PLASMA_EXCITER.get(), 6),
                out(UniverseItems.OIL_REFINERY.get(), 1));

        // ---- 原油精炼厂
        Item crude = UniverseItems.CRUDE_OIL.get();
        Item refined = UniverseItems.REFINED_OIL.get();
        this.machine(Kind.REFINING, "plasma_refining", 80, "plasma_refining", in(crude, 2),
                List.of(new ItemStackTemplate(refined, 2), new ItemStackTemplate(hydrogen, 1)));
        this.machine(Kind.REFINING, "x_ray_cracking", 80, "x_ray_cracking", in(refined, 1, hydrogen, 2),
                List.of(new ItemStackTemplate(hydrogen, 3), new ItemStackTemplate(graphite, 1)));
    }

    /** 要研究解锁的有序合成，配方 ID 和原版一样用产物的名字。 */
    private void researchShaped(String research, RecipeCategory category, ItemLike result, int count, List<String> pattern,
            Map<Character, ? extends ItemLike> key) {
        // 按字母排，生成的文件每次一样
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(category, null), ShapedRecipePattern.of(ingredients, pattern),
                new ItemStackTemplate(result.asItem(), count), research);
        this.output.accept(RecipeBuilder.getDefaultRecipeId(recipe.result()), recipe, null);
    }

    /** 设备配方，ID 是 {@code universe:<类型>/<名字>}。 */
    private void machine(Kind kind, String name, int time, String research, List<SizedIngredient> ingredients,
            List<ItemStackTemplate> results) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        this.output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(type + "/" + name)),
                new MachineRecipe(kind, ingredients, results, time, research), null);
    }

    /** 原料：物品、数量交替排列。 */
    private static List<SizedIngredient> in(Object... itemsAndCounts) {
        List<SizedIngredient> list = new ArrayList<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            list.add(SizedIngredient.of((ItemLike) itemsAndCounts[i], (Integer) itemsAndCounts[i + 1]));
        }
        return list;
    }

    private static List<ItemStackTemplate> out(ItemLike item, int count) {
        return List.of(new ItemStackTemplate(item.asItem(), count));
    }

    /** 多功能工具：修好坠毁点找到的那把，或者用钻石从头造一把（多人游戏里别的玩家用）。 */
    private void multitool() {
        this.shapeless(RecipeCategory.TOOLS, UniverseItems.MULTITOOL.get())
                .requires(UniverseItems.DAMAGED_MULTITOOL.get())
                .requires(Items.COPPER_INGOT).requires(Items.COPPER_INGOT)
                .requires(Items.REDSTONE).requires(Items.REDSTONE).requires(Items.IRON_INGOT)
                .unlockedBy(getHasName(UniverseItems.DAMAGED_MULTITOOL.get()), this.has(UniverseItems.DAMAGED_MULTITOOL.get()))
                .save(this.output, "universe:multitool_from_repair");
        this.shaped(RecipeCategory.TOOLS, UniverseItems.MULTITOOL.get())
                .pattern(" GD").pattern("CRC").pattern("I  ")
                .define('G', Items.GLASS_PANE).define('D', Items.DIAMOND).define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE_BLOCK).define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.DIAMOND), this.has(Items.DIAMOND))
                .save(this.output);
    }

    /** 外骨骼：主世界的铁、铜、玻璃、红石就能做，第一次登月前要备好。 */
    private void suit() {
        this.shaped(RecipeCategory.COMBAT, UniverseItems.EXOSUIT_HELMET.get())
                .pattern("ICI").pattern("IGI")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT).define('G', Items.GLASS_PANE)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output);
        this.shaped(RecipeCategory.COMBAT, UniverseItems.EXOSUIT_CHESTPLATE.get())
                .pattern("I I").pattern("CRC").pattern("IBI")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT).define('R', Items.REDSTONE_BLOCK).define('B', Items.BUCKET)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output);
        this.shaped(RecipeCategory.COMBAT, UniverseItems.EXOSUIT_LEGGINGS.get())
                .pattern("ICI").pattern("I I").pattern("I I")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output);
        this.shaped(RecipeCategory.COMBAT, UniverseItems.EXOSUIT_BOOTS.get())
                .pattern("C C").pattern("I I")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output);
        // 氧气罐：树叶里的氧压进瓶子；防护电池：铜和红石
        this.shapeless(RecipeCategory.MISC, UniverseItems.OXYGEN_CAPSULE.get(), 2)
                .requires(Items.GLASS_BOTTLE).requires(Items.GLASS_BOTTLE).requires(Items.COPPER_INGOT)
                .requires(ItemTags.LEAVES).requires(ItemTags.LEAVES)
                .unlockedBy(getHasName(Items.GLASS_BOTTLE), this.has(Items.GLASS_BOTTLE))
                .save(this.output);
        this.shapeless(RecipeCategory.MISC, UniverseItems.HAZARD_CELL.get(), 2)
                .requires(Items.COPPER_INGOT).requires(Items.REDSTONE).requires(Items.REDSTONE).requires(ItemTags.COALS)
                .unlockedBy(getHasName(Items.REDSTONE), this.has(Items.REDSTONE))
                .save(this.output);
    }

    /** 冶炼：粗矿烧成锭，精准采集下来的矿石直接烧出产物；高炉快一倍。 */
    private void ores() {
        for (OreKind kind : OreKind.values()) {
            Item ore = UniverseItems.ORES.get(kind).get();
            Item drop = UniverseItems.oreDrop(kind);
            Item product = kind.smelted() != null ? UniverseItems.ORE_SMELTED.get(kind).get() : drop;
            float xp = kind.toolTier() >= 2 ? 1.0F : 0.7F;
            this.oreCooking(ore, product, xp, kind.blockName());
            if (kind.smelted() != null) {
                this.oreCooking(drop, product, xp, kind.drop());
            }
        }
    }

    private void oreCooking(Item input, Item result, float xp, String from) {
        String name = "universe:" + BuiltInRegistries.ITEM.getKey(result).getPath();
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(input), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 200)
                .unlockedBy(getHasName(input), this.has(input))
                .save(this.output, name + "_from_smelting_" + from);
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(input), RecipeCategory.MISC, CookingBookCategory.MISC, result, xp, 100)
                .unlockedBy(getHasName(input), this.has(input))
                .save(this.output, name + "_from_blasting_" + from);
    }

    /** 飞船部件：前期的铁、铜、熔炉就能造出能飞的船，脉冲引擎要钻石和末影珍珠。 */
    private void ships() {
        this.shaped(RecipeCategory.TRANSPORTATION, UniverseItems.SHIP_HELM.get())
                .pattern("GGG").pattern("CEC").pattern("III")
                .define('G', Items.GLASS_PANE).define('C', Items.COPPER_INGOT).define('E', Items.COMPASS).define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.COMPASS), this.has(Items.COMPASS))
                .save(this.output);
        this.shaped(RecipeCategory.TRANSPORTATION, UniverseItems.SHIP_THRUSTER.get())
                .pattern("IBI").pattern("IFI").pattern("C C")
                .define('I', Items.IRON_INGOT).define('B', Items.IRON_BARS).define('F', Items.FURNACE).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.FURNACE), this.has(Items.FURNACE))
                .save(this.output);
        this.shaped(RecipeCategory.TRANSPORTATION, UniverseItems.FUEL_TANK.get())
                .pattern("ICI").pattern("CBC").pattern("ICI")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT).define('B', Items.BUCKET)
                .unlockedBy(getHasName(Items.BUCKET), this.has(Items.BUCKET))
                .save(this.output);
        this.shaped(RecipeCategory.TRANSPORTATION, UniverseItems.IMPULSE_ENGINE.get())
                .pattern("IDI").pattern("EBE").pattern("IRI")
                .define('I', Items.IRON_INGOT).define('D', Items.DIAMOND).define('E', Items.ENDER_PEARL)
                .define('B', Items.BLAST_FURNACE).define('R', Items.REDSTONE_BLOCK)
                .unlockedBy(getHasName(Items.ENDER_PEARL), this.has(Items.ENDER_PEARL))
                .save(this.output);
        this.shaped(RecipeCategory.TRANSPORTATION, UniverseItems.SHIP_SEAT.get())
                .pattern("W  ").pattern("WWW").pattern("I I")
                .define('W', ItemTags.WOOL).define('I', Items.IRON_INGOT)
                .unlockedBy("has_wool", this.has(ItemTags.WOOL))
                .save(this.output);
        this.shapeless(RecipeCategory.MISC, UniverseItems.LAUNCH_FUEL.get())
                .requires(ItemTags.COALS).requires(ItemTags.COALS).requires(ItemTags.COALS)
                .requires(Items.GUNPOWDER).requires(Items.GLASS_BOTTLE)
                .unlockedBy("has_coal", this.has(ItemTags.COALS))
                .save(this.output);

        // 船体板：铁和铜压成淡灰色的板，八块围着一份染料换颜色
        Item lightGray = UniverseItems.HULL_PLATING.get(DyeColor.LIGHT_GRAY).get();
        this.shaped(RecipeCategory.BUILDING_BLOCKS, lightGray, 16)
                .pattern("ICI").pattern("CIC").pattern("ICI")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output, "universe:hull_plating");
        Ingredient anyPlating = Ingredient.of(UniverseItems.HULL_PLATING.values().stream().map(item -> (Item) item.get()));
        UniverseItems.HULL_PLATING.forEach((color, item) -> this.shaped(RecipeCategory.BUILDING_BLOCKS, item.get(), 8)
                .pattern("###").pattern("#D#").pattern("###")
                .define('#', anyPlating).define('D', color.getTag())
                .group("universe:hull_plating")
                .unlockedBy("has_hull_plating", this.has(lightGray))
                .save(this.output, "universe:hull_plating_" + color.getSerializedName() + "_from_dye"));
    }

    /** 原版的 simpleCookingRecipe 会把配方存到 minecraft 命名空间下，这里自己指定 ID。 */
    private <T extends AbstractCookingRecipe> void cookMeat(String source, AbstractCookingRecipe.Factory<T> factory, int time) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(UniverseItems.ALIEN_MEAT.get()), RecipeCategory.FOOD, CookingBookCategory.FOOD,
                        UniverseItems.COOKED_ALIEN_MEAT.get(), 0.35F, time, factory)
                .unlockedBy(getHasName(UniverseItems.ALIEN_MEAT.get()), this.has(UniverseItems.ALIEN_MEAT.get()))
                .save(this.output, "universe:cooked_alien_meat_from_" + source);
    }
}
