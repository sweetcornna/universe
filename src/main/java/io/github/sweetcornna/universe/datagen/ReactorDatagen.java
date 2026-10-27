package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 化学反应釜（ADR-0036 §四）的数据生成：五条反应的设备配方、反应釜本身的配方和方块标签。模型在 {@code UniverseModelProvider}
 * 里按配方设备的做法生成，战利品表是掉自己。
 */
final class ReactorDatagen {
    private ReactorDatagen() {
    }

    static void recipes(RecipeOutput output) {
        // 每条反应一个配方，按单程转化率 1 写：数量按摩尔比，时间是一批最快要多久。费托合成的甲烷是副产物，不进配方
        for (Reaction reaction : Reaction.values()) {
            List<SizedIngredient> ingredients = new ArrayList<>();
            for (ItemCount input : reaction.inputs()) {
                ingredients.add(SizedIngredient.of(ReactorRecipes.item(input), input.count()));
            }
            List<ItemStackTemplate> results = new ArrayList<>();
            for (ItemCount result : reaction.outputs()) {
                results.add(new ItemStackTemplate(ReactorRecipes.item(result), result.count()));
            }
            output.accept(ReactorRecipes.key(reaction), new MachineRecipe(Kind.REACTOR, ingredients, results, ReactorRecipes.time(reaction),
                    ReactorRecipes.research(reaction)), null);
        }
        // 反应釜：钢的压力容器，钛合金内衬耐腐蚀，处理器管温度和压强，里面是一台化工厂的管路
        Item steel = UniverseItems.STEEL.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item plant = UniverseItems.CHEMICAL_PLANT.get();
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id("assembling/chemical_reactor")),
                new MachineRecipe(Kind.ASSEMBLING, List.of(SizedIngredient.of(plant, 1), SizedIngredient.of(steel, 8),
                        SizedIngredient.of(alloy, 4), SizedIngredient.of(processor, 2)),
                        List.of(new ItemStackTemplate(UniverseItems.CHEMICAL_REACTOR.get(), 1)), 120, "catalysis"), null);
        shaped(output, "catalysis", UniverseItems.CHEMICAL_REACTOR.get(), List.of("TST", "PCP", "TST"),
                Map.of('T', alloy, 'S', steel, 'P', processor, 'C', plant));
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
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.CHEMICAL_REACTOR.key());
        tag.apply(ShipParts.EXCLUDED).add(UniverseBlocks.CHEMICAL_REACTOR.key());
        tag.apply(Facilities.TAG).add(UniverseBlocks.CHEMICAL_REACTOR.key());
    }
}
