package io.github.sweetcornna.universe.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.stats.RecipeGraph;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;

/**
 * 服务端的配方图（ADR-0033 §四）：本模组各种设备的配方，加上本模组的工作台配方。配方表换了（重载数据包）就重建。
 * 26.x 的配方不全发给客户端，统计面板的上下游都从这里查好了再发过去。只在服务端线程用。
 */
public final class RecipeIndex {
    private static @Nullable RecipeMap cached;
    private static RecipeGraph graph = RecipeGraph.EMPTY;

    private RecipeIndex() {
    }

    public static RecipeGraph graph(MinecraftServer server) {
        RecipeMap map = server.getRecipeManager().recipeMap();
        if (map != cached) {
            graph = build(map);
            cached = map;
        }
        return graph;
    }

    public static void clear() {
        cached = null;
        graph = RecipeGraph.EMPTY;
    }

    /** 配方的 ID，参考产量按它对上配方图。 */
    public static String id(RecipeHolder<?> holder) {
        return holder.id().identifier().toString();
    }

    private static RecipeGraph build(RecipeMap map) {
        List<RecipeGraph.Recipe> recipes = new ArrayList<>();
        for (MachineRecipe.Kind kind : MachineRecipe.Kind.values()) {
            String machine = item(machine(kind).asItem());
            for (RecipeHolder<MachineRecipe> holder : map.byType(kind.type())) {
                MachineRecipe recipe = holder.value();
                List<RecipeGraph.Input> inputs = new ArrayList<>();
                recipe.ingredients().forEach(sized -> inputs.add(new RecipeGraph.Input(items(sized.ingredient()), sized.count())));
                List<RecipeGraph.Output> outputs = new ArrayList<>();
                for (ItemStackTemplate result : recipe.results()) {
                    outputs.add(new RecipeGraph.Output(item(result.item().value()), result.count()));
                }
                recipes.add(new RecipeGraph.Recipe(id(holder), machine, inputs, outputs, recipe.time()));
            }
        }
        // 工作台只算本模组的：原版的配方太多，物品选中原版东西时上下游只列设备配方
        for (RecipeHolder<CraftingRecipe> holder : map.byType(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (!holder.id().identifier().getNamespace().equals(Universe.MOD_ID) || recipe.isSpecial()) {
                continue;
            }
            ItemStack result = result(recipe);
            if (result.isEmpty()) {
                continue;
            }
            Map<List<String>, Integer> counts = new LinkedHashMap<>();
            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                List<String> items = items(ingredient);
                if (!items.isEmpty()) {
                    counts.merge(items, 1, Integer::sum);
                }
            }
            List<RecipeGraph.Input> inputs = new ArrayList<>();
            counts.forEach((items, count) -> inputs.add(new RecipeGraph.Input(items, count)));
            recipes.add(new RecipeGraph.Recipe(id(holder), "minecraft:crafting_table", inputs,
                    List.of(new RecipeGraph.Output(item(result.getItem()), result.getCount())), 0));
        }
        return new RecipeGraph(recipes);
    }

    /** 每种配方在统计面板上画哪台设备（每种取第一台，和 JEI 里一样）。 */
    private static ItemLike machine(MachineRecipe.Kind kind) {
        return switch (kind) {
            case SMELTING -> UniverseBlocks.ARC_SMELTER.get();
            case ASSEMBLING -> UniverseBlocks.ASSEMBLER.get();
            case REFINING -> UniverseBlocks.OIL_REFINERY.get();
            case CHEMICAL -> UniverseBlocks.CHEMICAL_PLANT.get();
            case PARTICLE -> UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.get();
            case NUTRIENT -> UniverseBlocks.NUTRIENT_PROCESSOR.get();
            case PORTABLE_REFINING -> UniverseBlocks.PORTABLE_REFINER.get();
            case ELECTROLYSIS -> UniverseBlocks.ELECTROLYZER.get();
            // M20 化学（A）的设备加在这行上面
            case REACTOR -> UniverseBlocks.CHEMICAL_REACTOR.get();
            // M20 反应釜（B）的设备加在这行上面
            // M20 微生物和基因（C）的设备加在这行上面
            case BIOREACTOR -> UniverseBlocks.BIOREACTOR.get();
            // M20 生物反应器（D）的设备加在这行上面
            // M20 星球改造（E）的设备加在这行上面
        };
    }

    private static ItemStack result(CraftingRecipe recipe) {
        try {
            return recipe.assemble(CraftingInput.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static List<String> items(Ingredient ingredient) {
        return ingredient.items().map(Holder::value).map(RecipeIndex::item).toList();
    }

    private static String item(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
