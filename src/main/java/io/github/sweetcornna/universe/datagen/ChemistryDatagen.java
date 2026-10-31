package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.chem.Electrolysis;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import io.github.sweetcornna.universe.factory.FluidItems;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * M20 化学的数据生成（子代理 A，ADR-0036 §三、§四）：大气提取器和电解槽的掉落、标签、配方，电解槽的五条配方，
 * 化工厂里按真实反应式的几条配方；ADR-0037 新催化剂的配方和化学手册。两台设备的模型在 {@code UniverseModelProvider} 里（用那边的 {@code recipeMachine}）。
 */
final class ChemistryDatagen {
    /**
     * 电积铜做一次几刻。核心层按 100 J/刻 × 60 刻定的电，可电解槽的功率是固定的 500 J/刻，照用电不变折算只有 12 刻，
     * 一台电解槽一秒出 8 个铜锭，比电弧熔炉快八倍。这里拉长到 40 刻（一秒一个，和电弧熔炉一样），一次 20 000 J。
     */
    static final int ELECTROWINNING_TICKS = 40;

    private ChemistryDatagen() {
    }

    private static List<Block> machines() {
        return List.of(UniverseBlocks.ATMOSPHERIC_EXTRACTOR.get(), UniverseBlocks.ELECTROLYZER.get());
    }

    // ---- 掉落、标签

    static void blockLoot(Consumer<Block> dropSelf) {
        machines().forEach(dropSelf);
    }

    /** 和别的工业设备一样：镐子挖，实心，不会被组装进飞船。 */
    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        for (Block block : machines()) {
            ResourceKey<Block> key = block.builtInRegistryHolder().key();
            tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(key);
            tag.apply(ShipParts.EXCLUDED).add(key);
            tag.apply(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(key);
        }
    }

    /** 气体和溶液都能装进储液罐。 */
    static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        TagAppender<Item> fluids = tag.apply(FluidItems.TAG);
        for (Gas gas : Gas.values()) {
            // 氢以前就在里面
            if (gas != Gas.HYDROGEN) {
                fluids.add(key(item(gas)));
            }
        }
        for (Item item : List.of(UniverseItems.SODIUM_HYDROXIDE.get(), UniverseItems.NITRIC_ACID.get(), UniverseItems.TITANIUM_TETRACHLORIDE.get(),
                UniverseItems.COPPER_LEACH_SOLUTION.get(), UniverseItems.GOLD_LEACH_SOLUTION.get(),
                // ADR-0037 反应釜的气体和甲醇
                UniverseItems.SULFUR_TRIOXIDE.get(), UniverseItems.NITRIC_OXIDE.get(), UniverseItems.NITROGEN_DIOXIDE.get(),
                UniverseItems.HYDROGEN_CHLORIDE.get(), UniverseItems.METHANOL.get(), UniverseItems.ETHYLENE.get())) {
            fluids.add(key(item));
        }
    }

    // ---- 配方

    static void recipes(RecipeOutput output) {
        Item water = UniverseItems.WATER.get();
        Item hydrogen = UniverseItems.HYDROGEN.get();
        Item oxygen = item(Gas.OXYGEN);
        Item salt = UniverseItems.oreDrop(OreKind.SALT);
        Item sodium = UniverseItems.RESOURCES.get("sodium").get();
        Item chlorine = UniverseItems.CHLORINE.get();
        Item caustic = UniverseItems.SODIUM_HYDROXIDE.get();

        // 电解槽（§三）：用电照 core.chem.Electrolysis，每出一个氢至少用掉它能换回的电的 1.4 倍
        electrolysis(output, Electrolysis.Recipe.WATER, "atmospheric_chemistry", in(water, 2), out(hydrogen, 2, oxygen, 1));
        electrolysis(output, Electrolysis.Recipe.MOXIE, "atmospheric_chemistry", in(UniverseItems.CARBON_DIOXIDE.get(), 2),
                out(UniverseItems.CARBON_MONOXIDE.get(), 2, oxygen, 1));
        electrolysis(output, Electrolysis.Recipe.BRINE, "electrochemistry", in(salt, 2, water, 2), out(chlorine, 1, hydrogen, 1, caustic, 2));
        electrolysis(output, Electrolysis.Recipe.MOLTEN_SALT, "electrochemistry", in(salt, 2), out(sodium, 2, chlorine, 1));
        machine(output, Kind.ELECTROLYSIS, Electrolysis.Recipe.ELECTROWINNING.id(), ELECTROWINNING_TICKS, "electrochemistry",
                in(UniverseItems.COPPER_LEACH_SOLUTION.get(), 2), out(Items.COPPER_INGOT, 2, UniverseItems.SULFURIC_ACID.get(), 1));
        // 金从浸出液里电积出来（现实里是从金氰络离子的溶液里）
        machine(output, Kind.ELECTROLYSIS, "electrowinning_gold", ELECTROWINNING_TICKS, "electrochemistry",
                in(UniverseItems.GOLD_LEACH_SOLUTION.get(), 2), out(Items.GOLD_INGOT, 2));

        // 亨特法（§四）：钛铁矿和氯气、焦炭在流化床里氯化成四氯化钛，混在里面的三氯氧钒提纯时分出来成钒渣；
        // 四氯化钛再用钠还原。一块粗钛按两摩尔二氧化钛算，直接冶炼只出一个钛锭，氯化法出两个。
        // 还原出来的盐拿去熔盐电解，正好还回四个钠、两个氯气，所以这条链只吃电。还原一炉现实里要好几天，这里 48 秒：
        // 出钛多，可是慢，按价钱算（TradeValuesTest）也不比冶炼的钛锭便宜
        Item tetrachloride = UniverseItems.TITANIUM_TETRACHLORIDE.get();
        machine(output, Kind.CHEMICAL, "titanium_tetrachloride", 160, "electrochemistry",
                in(UniverseItems.oreDrop(OreKind.TITANIUM), 2, chlorine, 8, Items.COAL, 4), out(tetrachloride, 4, UniverseItems.VANADIUM_SLAG.get(), 1));
        machine(output, Kind.CHEMICAL, "hunter_titanium", 960, "electrochemistry", in(tetrachloride, 1, sodium, 4),
                out(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 1, salt, 4));

        // 奥斯特瓦尔德法的总反应 NH₃ + 2O₂ → HNO₃ + H₂O；硝酸铵 NH₃ + HNO₃ → NH₄NO₃
        Item ammonia = UniverseItems.AMMONIA.get();
        Item nitric = UniverseItems.NITRIC_ACID.get();
        machine(output, Kind.CHEMICAL, "nitric_acid", 80, "catalysis", in(ammonia, 1, oxygen, 2), out(nitric, 1, water, 1));
        machine(output, Kind.CHEMICAL, "ammonium_nitrate", 40, "catalysis", in(ammonia, 1, nitric, 1), out(UniverseItems.AMMONIUM_NITRATE.get(), 1));
        // 安德卢梭法：甲烷、氨和氧在 1100 °C 上下的铂铑网上反应（2CH₄ + 2NH₃ + 3O₂ → 2HCN + 6H₂O）。
        // 现实里氰化氢是做尼龙、有机玻璃的原料；这里只用来往星球的大气里放（大气释放塔）
        machine(output, Kind.CHEMICAL, "hydrogen_cyanide", 120, "catalysis", in(UniverseItems.METHANE.get(), 2, ammonia, 2, oxygen, 3),
                out(UniverseItems.HYDROGEN_CYANIDE.get(), 2, water, 6));

        // 催化剂：合成氨的铁催化剂是磁铁矿用氢还原的（Fe₃O₄ + 4H₂ → 3Fe + 4H₂O）；钒渣用碱液浸出钒酸钠，再烧成五氧化二钒；
        // 镍溶进硝酸，浸到载体上烧成氧化镍，再用氢还原
        machine(output, Kind.CHEMICAL, "iron_catalyst", 120, "catalysis", in(UniverseItems.oreDrop(OreKind.MAGNETIC), 1, hydrogen, 4),
                out(UniverseItems.IRON_CATALYST.get(), 1, water, 4));
        machine(output, Kind.CHEMICAL, "vanadium_catalyst", 120, "catalysis", in(UniverseItems.VANADIUM_SLAG.get(), 2, caustic, 1),
                out(UniverseItems.VANADIUM_CATALYST.get(), 1));
        machine(output, Kind.CHEMICAL, "nickel_catalyst", 120, "synthetic_fuels", in(UniverseItems.NICKEL_INGOT.get(), 1, nitric, 2, hydrogen, 1),
                out(UniverseItems.NICKEL_CATALYST.get(), 1));

        // ADR-0037 的催化剂。铜锌：粗铜矿（黄铜矿常和闪锌矿共生）溶进硝酸，和高岭土里的铝一起共沉淀成氧化物，
        // 装进甲醇塔以后用合成气就地还原。铂铑网：铂铑合金拉成细丝织成网（铂锭里带着一成铑）。
        // ZSM-5：石英砂、高岭土和碱液在釜里水热结晶。齐格勒–纳塔：四氯化钛用氢还原成紫色的三氯化钛（2TiCl₄ + H₂ → 2TiCl₃ + 2HCl）。
        // 氯化铜：铜在氯气里烧（Cu + Cl₂ → CuCl₂），浸到浮石上
        machine(output, Kind.CHEMICAL, "copper_zinc_catalyst", 120, "synthetic_fuels", in(Items.RAW_COPPER, 2, nitric, 2, Items.CLAY_BALL, 1),
                out(UniverseItems.COPPER_ZINC_CATALYST.get(), 1));
        machine(output, Kind.ASSEMBLING, "platinum_rhodium_gauze", 120, "catalysis", in(UniverseItems.PLATINUM_INGOT.get(), 2),
                out(UniverseItems.PLATINUM_RHODIUM_GAUZE.get(), 1));
        machine(output, Kind.CHEMICAL, "zeolite_catalyst", 160, "synthetic_fuels",
                in(UniverseItems.oreDrop(OreKind.SILICON), 2, Items.CLAY_BALL, 1, caustic, 1), out(UniverseItems.ZEOLITE_CATALYST.get(), 1));
        machine(output, Kind.CHEMICAL, "ziegler_natta_catalyst", 120, "synthetic_fuels", in(tetrachloride, 2, hydrogen, 1),
                out(UniverseItems.ZIEGLER_NATTA_CATALYST.get(), 2, UniverseItems.HYDROGEN_CHLORIDE.get(), 2));
        machine(output, Kind.CHEMICAL, "copper_chloride_catalyst", 80, "electrochemistry", in(Items.COPPER_INGOT, 1, chlorine, 1),
                out(UniverseItems.COPPER_CHLORIDE_CATALYST.get(), 1));

        // 甲烷液氧：1 甲烷 + 2 氧灌成一罐（CH₄ + 2O₂ 正好烧完）
        machine(output, Kind.CHEMICAL, "methalox_fuel", 40, "synthetic_fuels", in(UniverseItems.METHANE.get(), 1, oxygen, 2),
                out(UniverseItems.METHALOX_FUEL.get(), 1));

        // 设备：大气提取器是压缩机（电机）加冷箱，顶上一圈进气格栅；电解槽是玻璃的电解池、铜的电极和整流用的电路板
        Item steel = UniverseItems.STEEL.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        shaped(output, "atmospheric_chemistry", UniverseItems.ATMOSPHERIC_EXTRACTOR.get(), List.of("SGS", "MBM", "SSS"),
                Map.of('S', steel, 'G', Items.IRON_BARS, 'M', UniverseItems.ELECTRIC_MOTOR.get(), 'B', board));
        shaped(output, "atmospheric_chemistry", UniverseItems.ELECTROLYZER.get(), List.of("CGC", "SBS", "CGC"),
                Map.of('C', Items.COPPER_INGOT, 'G', Items.GLASS, 'S', steel, 'B', board));
        // 化学手册（ADR-0037 §四）：一本书加一个空瓶
        shaped(output, "atmospheric_chemistry", UniverseItems.CHEMISTRY_HANDBOOK.get(), List.of("BG"),
                Map.of('B', Items.BOOK, 'G', Items.GLASS_BOTTLE));
    }

    private static void electrolysis(RecipeOutput output, Electrolysis.Recipe recipe, String research, List<SizedIngredient> ingredients,
            List<ItemStackTemplate> results) {
        machine(output, Kind.ELECTROLYSIS, recipe.id(), recipe.ticksAt(Electrolysis.POWER), research, ingredients, results);
    }

    /** 设备配方，ID 是 {@code universe:<类型>/<名字>}。 */
    private static void machine(RecipeOutput output, Kind kind, String name, int time, String research, List<SizedIngredient> ingredients,
            List<ItemStackTemplate> results) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(type + "/" + name)),
                new MachineRecipe(kind, ingredients, results, time, research), null);
    }

    /** 要研究解锁的有序合成，配方 ID 用产物的名字。 */
    private static void shaped(RecipeOutput output, String research, ItemLike result, List<String> pattern, Map<Character, ? extends ItemLike> key) {
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new java.util.TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(RecipeCategory.REDSTONE, null), ShapedRecipePattern.of(ingredients, pattern),
                new ItemStackTemplate(result.asItem(), 1), research);
        output.accept(RecipeBuilder.getDefaultRecipeId(recipe.result()), recipe, null);
    }

    /** 原料：物品、数量交替排列。 */
    private static List<SizedIngredient> in(Object... itemsAndCounts) {
        List<SizedIngredient> list = new ArrayList<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            list.add(SizedIngredient.of((ItemLike) itemsAndCounts[i], (Integer) itemsAndCounts[i + 1]));
        }
        return list;
    }

    /** 产物：物品、数量交替排列。 */
    private static List<ItemStackTemplate> out(Object... itemsAndCounts) {
        List<ItemStackTemplate> list = new ArrayList<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            list.add(new ItemStackTemplate(((ItemLike) itemsAndCounts[i]).asItem(), (Integer) itemsAndCounts[i + 1]));
        }
        return list;
    }

    private static Item item(Gas gas) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(io.github.sweetcornna.universe.core.chem.Substances.item(gas)));
    }

    private static ResourceKey<Item> key(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
    }
}
