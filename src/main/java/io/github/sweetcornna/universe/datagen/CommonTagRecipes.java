package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.registry.UniverseItemTags;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 设备配方写进注册表之前，把原料里本模组的材料换成对应的通用标签（{@link UniverseItemTags#COMMON}，ADR-0039）：
 * 别的模组的钛锭、钢、硫磺放进设备也认。只换单一物品的原料，已经是标签、几选一的不动。
 *
 * <p>反应釜和生物反应器不换：反应釜按投进去的物质认反应（ADR-0037），生物反应器按菌种认工艺，都按本模组的物品算。
 * 工作台的配方也不换，那些是开局手搓的，原料都是原版的或者本模组独有的。
 */
final class CommonTagRecipes implements BootstrapContext<Recipe<?>> {
    private static final Set<Kind> UNCHANGED = Set.of(Kind.REACTOR, Kind.BIOREACTOR);

    private final BootstrapContext<Recipe<?>> delegate;
    private final HolderGetter<Item> items;

    CommonTagRecipes(BootstrapContext<Recipe<?>> delegate) {
        this.delegate = delegate;
        this.items = delegate.lookup(Registries.ITEM);
    }

    @Override
    public Holder.Reference<Recipe<?>> register(ResourceKey<Recipe<?>> key, Recipe<?> value) {
        return this.delegate.register(key, this.rewrite(value));
    }

    @Override
    public Holder.Reference<Recipe<?>> register(ResourceKey<Recipe<?>> key, Recipe<?> value, ICondition... conditions) {
        return this.delegate.register(key, this.rewrite(value), conditions);
    }

    @Override
    public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> key) {
        return this.delegate.lookup(key);
    }

    @Deprecated
    @Override
    public <S> Stream<Holder.Reference<S>> listContextElements(ResourceKey<? extends Registry<? extends S>> key) {
        return this.delegate.listContextElements(key);
    }

    @Override
    public <S> Optional<HolderLookup<S>> holderLookup(ResourceKey<? extends Registry<? extends S>> registry) {
        return this.delegate.holderLookup(registry);
    }

    private Recipe<?> rewrite(Recipe<?> recipe) {
        if (!(recipe instanceof MachineRecipe machine) || UNCHANGED.contains(machine.kind())) {
            return recipe;
        }
        List<SizedIngredient> ingredients = new ArrayList<>(machine.ingredients().size());
        boolean changed = false;
        for (SizedIngredient sized : machine.ingredients()) {
            TagKey<Item> tag = common(sized.ingredient());
            if (tag == null) {
                ingredients.add(sized);
            } else {
                ingredients.add(new SizedIngredient(Ingredient.of(this.items.getOrThrow(tag)), sized.count()));
                changed = true;
            }
        }
        return changed ? new MachineRecipe(machine.kind(), ingredients, machine.results(), machine.time(), machine.research()) : recipe;
    }

    /** 只有一样物品、这样物品又有通用标签的原料，返回那个标签。 */
    private static @Nullable TagKey<Item> common(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return null;
        }
        HolderSet<Item> values = ingredient.getValues();
        if (values.unwrapKey().isPresent() || values.size() != 1) {
            return null;
        }
        return values.get(0).unwrapKey().map(key -> UniverseItemTags.COMMON.get(key.identifier().toString())).orElse(null);
    }
}
