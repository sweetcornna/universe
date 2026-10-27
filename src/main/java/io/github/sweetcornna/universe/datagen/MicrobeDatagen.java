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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * M20 微生物和基因的数据生成（子代理 C，ADR-0036 §五）：三台设备的掉落、标签和配方，采样管，琼脂、培养基、测序芯片、编辑试剂。
 * 设备的模型在 {@code UniverseModelProvider} 里（用那边的 {@code recipeMachine}）。
 */
final class MicrobeDatagen {
    private MicrobeDatagen() {
    }

    private static List<Block> machines() {
        return List.of(UniverseBlocks.INCUBATOR.get(), UniverseBlocks.SEQUENCER.get(), UniverseBlocks.GENE_EDITOR.get());
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

    // ---- 配方

    static void recipes(RecipeOutput output) {
        Item water = UniverseItems.WATER.get();
        Item agar = UniverseItems.AGAR.get();
        Item medium = UniverseItems.CULTURE_MEDIUM.get();
        Item plastic = UniverseItems.PLASTIC.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();

        // 采样管是带盖的玻璃管，一开始就能做：微生物学这门科技要交样本
        shaped(output, "", UniverseItems.SAMPLE_TUBE.get(), 3, List.of("N", "G", "G"),
                Map.of('N', Items.IRON_NUGGET, 'G', Items.GLASS));

        // 琼脂是红藻、海带熬出来的胶；培养基是琼脂加糖（碳源）和盐，营养处理器里熬
        machine(output, Kind.NUTRIENT, "agar", 100, "microbiology", in(Items.KELP, 4, water, 1), out(agar, 2));
        machine(output, Kind.NUTRIENT, "culture_medium", 60, "microbiology", in(agar, 1, Items.SUGAR, 1, UniverseItems.oreDrop(OreKind.SALT), 1),
                out(medium, 4));
        // 测序芯片：玻璃的流动槽贴在读出电路上，封进塑料壳
        machine(output, Kind.ASSEMBLING, "sequencing_chip", 120, "genomics", in(Items.GLASS_PANE, 1, board, 1, plastic, 1),
                out(UniverseItems.SEQUENCING_CHIP.get(), 2));
        // 编辑试剂：Cas9 蛋白和向导 RNA 都是菌培养出来再提纯的，分装进塑料管
        machine(output, Kind.CHEMICAL, "editing_kit", 160, "synthetic_biology", in(medium, 2, plastic, 1),
                out(UniverseItems.EDITING_KIT.get(), 2));

        // 设备：培养箱是保温的玻璃门柜子，铜的加热丝；测序仪是光学读出加电路；编辑台是带处理器的移液工作站
        Item steel = UniverseItems.STEEL.get();
        shaped(output, "microbiology", UniverseItems.INCUBATOR.get(), 1, List.of("IGI", "IBI", "ICI"),
                Map.of('I', Items.IRON_INGOT, 'G', Items.GLASS, 'B', board, 'C', Items.COPPER_INGOT));
        shaped(output, "genomics", UniverseItems.SEQUENCER.get(), 1, List.of("PGP", "BRB", "SSS"),
                Map.of('P', plastic, 'G', Items.GLASS, 'B', board, 'R', UniverseItems.PRISM.get(), 'S', steel));
        shaped(output, "synthetic_biology", UniverseItems.GENE_EDITOR.get(), 1, List.of("PGP", "MXM", "SSS"),
                Map.of('P', plastic, 'G', Items.GLASS, 'M', UniverseItems.ELECTRIC_MOTOR.get(), 'X', UniverseItems.PROCESSOR.get(), 'S', steel));
    }

    /** 设备配方，ID 是 {@code universe:<类型>/<名字>}。 */
    private static void machine(RecipeOutput output, Kind kind, String name, int time, String research, List<SizedIngredient> ingredients,
            List<ItemStackTemplate> results) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(type + "/" + name)),
                new MachineRecipe(kind, ingredients, results, time, research), null);
    }

    /** 要研究解锁的有序合成，配方 ID 用产物的名字。 */
    private static void shaped(RecipeOutput output, String research, ItemLike result, int count, List<String> pattern,
            Map<Character, ? extends ItemLike> key) {
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new java.util.TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(RecipeCategory.REDSTONE, null), ShapedRecipePattern.of(ingredients, pattern),
                new ItemStackTemplate(result.asItem(), count), research);
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
}
