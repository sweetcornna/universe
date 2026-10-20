package io.github.sweetcornna.universe.client.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.factory.ClientResearch;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** 手册里画的配方（ADR-0018）：工作台的 3×3，或者设备的原料一排、产物一排。 */
public final class GuideRecipes {
    /** 一格里可能放的物品（标签原料有好几种，画的时候轮着换）。 */
    public record Crafting(List<List<ItemStack>> grid, ItemStack result) implements View {
    }

    public record Machine(Component machine, List<List<ItemStack>> inputs, List<ItemStack> outputs, float seconds) implements View {
    }

    public sealed interface View permits Crafting, Machine {
    }

    private GuideRecipes() {
    }

    /** 先当配方 ID 找，找不到再当物品注册名找产出它的第一个配方（设备配方优先）。 */
    public static @Nullable View find(Level level, String id) {
        if (id.isEmpty()) {
            return null;
        }
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return null;
        }
        RecipeMap map = ClientResearch.recipes();
        RecipeHolder<?> holder = map.byKey(ResourceKey.create(Registries.RECIPE, key));
        if (holder != null) {
            return view(holder.value());
        }
        Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(key);
        if (item.isEmpty()) {
            return null;
        }
        for (MachineRecipe.Kind kind : MachineRecipe.Kind.values()) {
            for (RecipeHolder<MachineRecipe> recipe : MachineRecipes.list(level, kind)) {
                if (recipe.value().results().stream().anyMatch(result -> result.item().value() == item.get().value())) {
                    return view(recipe.value());
                }
            }
        }
        for (RecipeHolder<CraftingRecipe> recipe : map.byType(RecipeType.CRAFTING)) {
            View view = view(recipe.value());
            if (view instanceof Crafting crafting && crafting.result().is(item.get().value())) {
                return view;
            }
        }
        return null;
    }

    private static @Nullable View view(Recipe<?> recipe) {
        if (recipe instanceof MachineRecipe machine) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            machine.ingredients().forEach(sized -> inputs.add(stacks(sized.ingredient(), sized.count())));
            List<ItemStack> outputs = machine.results().stream().map(ItemStackTemplate::create).toList();
            return new Machine(Component.translatable("gui.universe.guide.machine." + machine.kind().name().toLowerCase(java.util.Locale.ROOT)),
                    inputs, outputs, machine.time() / 20.0F);
        }
        if (recipe instanceof ResearchShapedRecipe shaped) {
            return new Crafting(grid(shaped.pattern().width(), shaped.pattern().ingredients()), shaped.result().create());
        }
        if (recipe instanceof ShapedRecipe shaped) {
            return new Crafting(grid(shaped.getWidth(), shaped.getIngredients()), result(shaped));
        }
        if (recipe instanceof CraftingRecipe crafting && !crafting.isSpecial()) {
            List<Optional<Ingredient>> list = crafting.placementInfo().ingredients().stream().map(Optional::of).toList();
            return list.isEmpty() || list.size() > 9 ? null : new Crafting(grid(3, list), result(crafting));
        }
        return null;
    }

    private static ItemStack result(CraftingRecipe recipe) {
        try {
            return recipe.assemble(CraftingInput.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** 按配方的宽度摆进 3×3。 */
    private static List<List<ItemStack>> grid(int width, List<Optional<Ingredient>> ingredients) {
        List<List<ItemStack>> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(List.of());
        }
        for (int i = 0; i < ingredients.size(); i++) {
            int x = i % width;
            int y = i / width;
            if (x < 3 && y < 3) {
                grid.set(y * 3 + x, ingredients.get(i).map(ingredient -> stacks(ingredient, 1)).orElse(List.of()));
            }
        }
        return grid;
    }

    private static List<ItemStack> stacks(Ingredient ingredient, int count) {
        return ingredient.items().map(item -> new ItemStack(item, count)).toList();
    }
}
