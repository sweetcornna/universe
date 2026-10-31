package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.bio.BioRecipes;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.core.chem.Substances;
import io.github.sweetcornna.universe.core.fishing.Junk;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
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
 * 生物反应器（ADR-0036 §五）的数据生成：五种工艺的六条配方、反应器本身的配方和方块标签。模型在 {@code UniverseModelProvider}
 * 里按配方设备的做法生成，战利品表是掉自己。
 *
 * <p>配方照真实的反应配平，一个物品按一摩尔；固体按好用的数配：
 * <ul>
 *   <li>生物浸出：粗铜按辉铜矿 Cu₂S 算，一个粗铜两摩尔铜。嗜酸的铁硫氧化菌把 Fe²⁺ 氧化成 Fe³⁺，Fe³⁺ 去溶矿，
 *       矿里的硫被氧化成硫酸（Cu₂S + 2.5 O₂ + H₂SO₄ → 2 CuSO₄ + H₂O，氧从空气里来）。放进去的硫酸是把 pH 压到 2 以下的起始酸，
 *       电积时还回来的比放进去的多，和现实的堆浸—电积一样是产酸的。直接冶炼一个粗铜出一个铜锭，这里出两个。
 *       粗金照难处理金矿的生物氧化预处理（南非的 BIOX 工艺），同样的数。</li>
 *   <li>藻类培养：CO₂ + H₂O → (CH₂O) + O₂，一个藻类生物质按两摩尔 CH₂O 算（约 60 克干重）。</li>
 *   <li>生物甲烷化：CO₂ + 4H₂ → CH₄ + 2H₂O，产甲烷古菌在常温做萨巴蒂尔反应。</li>
 *   <li>生物固氮：固氮菌靠呼吸作用给固氮酶供能，每个氮分子要 8 个电子、16 个 ATP，约烧掉 5–6 个 CH₂O。
 *       这里按 6 个（三个藻类生物质）：N₂ + 6 CH₂O + 4 O₂ → 2 NH₃ + 6 CO₂ + 2 H₂O + H₂，水和少量的氢留在培养液里。
 *       现实的根瘤菌每固定一克氮要烧 6–12 克碳，比这里费，这里取接近理论下限的数。</li>
 *   <li>塑料回收：PET 水解酶把聚酯水解成对苯二甲酸和乙二醇（PET + H₂O → TPA + EG），再缩聚回塑料。一个破损的储罐出一块塑料。</li>
 * </ul>
 * 时间按满罐、产率 1、最适温度写；罐没长满、温度不对时按比例慢。
 */
final class BioreactorDatagen {
    private BioreactorDatagen() {
    }

    static void recipes(RecipeOutput output) {
        Item water = UniverseItems.WATER.get();
        Item acid = UniverseItems.SULFURIC_ACID.get();
        Item carbonDioxide = UniverseItems.CARBON_DIOXIDE.get();
        Item oxygen = item(Gas.OXYGEN);
        Item biomass = UniverseItems.ALGAL_BIOMASS.get();

        // 生物浸出比直接冶炼多出一半：现实里细菌浸出的是品位太低、选不出精矿的矿，同一座矿能多采出的就是这部分。
        // 难处理的金矿现实里先用细菌氧化掉包着金的硫化物（BIOX 法），再用氰化物浸出，这里并成一步
        bio(output, "bioleaching_copper", 600, in(Items.RAW_COPPER, 2, acid, 1, water, 2), out(UniverseItems.COPPER_LEACH_SOLUTION.get(), 3));
        bio(output, "bioleaching_gold", 600, in(Items.RAW_GOLD, 2, acid, 1, water, 2), out(UniverseItems.GOLD_LEACH_SOLUTION.get(), 3));
        bio(output, "algae", 200, in(carbonDioxide, 2, water, 2), out(biomass, 1, oxygen, 2));
        bio(output, "biomethanation", 120, in(carbonDioxide, 1, UniverseItems.HYDROGEN.get(), 4), out(UniverseItems.METHANE.get(), 1, water, 2));
        bio(output, "nitrogen_fixation", 300, in(UniverseItems.NITROGEN.get(), 1, biomass, 3, oxygen, 4),
                out(UniverseItems.AMMONIA.get(), 2, carbonDioxide, 6));
        bio(output, "plastic_recycling", 400, in(UniverseItems.JUNK.get(Junk.BROKEN_CANISTER).get(), 1, water, 1),
                out(UniverseItems.PLASTIC.get(), 1));

        // 反应器：不锈钢的罐，两扇视镜，搅拌桨的电机，处理器管温度和溶氧
        Item steel = UniverseItems.STEEL.get();
        Item motor = UniverseItems.ELECTRIC_MOTOR.get();
        Item processor = UniverseItems.PROCESSOR.get();
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id("assembling/bioreactor")),
                new MachineRecipe(Kind.ASSEMBLING, in(steel, 4, Items.GLASS, 2, motor, 2, processor, 1),
                        List.of(new ItemStackTemplate(UniverseItems.BIOREACTOR.get(), 1)), 120, BioRecipes.RESEARCH), null);
        shaped(output, BioRecipes.RESEARCH, UniverseItems.BIOREACTOR.get(), List.of("SGS", "MPM", "SGS"),
                Map.of('S', steel, 'G', Items.GLASS, 'M', motor, 'P', processor));
    }

    /** 一条生物反应器的配方，ID 是 {@code universe:bioreactor/<名字>}，名字以工艺开头（{@link BioRecipes#process}）。 */
    private static void bio(RecipeOutput output, String name, int time, List<SizedIngredient> ingredients, List<ItemStackTemplate> results) {
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(BioRecipes.DIR + "/" + name)),
                new MachineRecipe(Kind.BIOREACTOR, ingredients, results, time, BioRecipes.RESEARCH), null);
    }

    /** 要研究解锁的有序合成，配方 ID 用产物的名字。 */
    private static void shaped(RecipeOutput output, String research, ItemLike result, List<String> pattern, Map<Character, ? extends ItemLike> key) {
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(RecipeCategory.REDSTONE, null), ShapedRecipePattern.of(ingredients, pattern),
                new ItemStackTemplate(result.asItem(), 1), research);
        output.accept(RecipeBuilder.getDefaultRecipeId(recipe.result()), recipe, null);
    }

    /** 和别的工业设备一样：镐子挖，不跟着飞船走，黑雾会打。 */
    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.BIOREACTOR.key());
        tag.apply(ShipParts.EXCLUDED).add(UniverseBlocks.BIOREACTOR.key());
        tag.apply(Facilities.TAG).add(UniverseBlocks.BIOREACTOR.key());
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
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(Substances.item(gas)));
    }
}
