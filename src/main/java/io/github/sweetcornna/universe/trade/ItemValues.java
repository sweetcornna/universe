package io.github.sweetcornna.universe.trade;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.trade.RawValues;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.core.trade.ValueGraph;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * 服务端的物品价钱表（ADR-0017）：从 {@link RawValues} 顺着配方管理器里的工作台、熔炉、切石机配方和本模组的设备配方
 * 推出每样东西的基准价。第一次用到时算，重新加载数据包后重算。
 *
 * <p>只有"干净"的东西能卖：没有附魔、改名、内容物这些数据，耐久和模块的数值除外。耐久磨损按比例折价，
 * 模块按品级翻倍（C 1、B 2、A 4、S 8）。
 */
public final class ItemValues {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 手工合成、切石算一秒。 */
    private static final double CRAFT_SECONDS = 1;
    private static final double[] GRADE_FACTOR = {1, 2, 4, 8};
    private static final Map<TradeCategory, TagKey<Item>> TAGS = new EnumMap<>(TradeCategory.class);

    static {
        for (TradeCategory category : TradeCategory.values()) {
            TAGS.put(category, TagKey.create(Registries.ITEM, Universe.id("trade/" + category.key())));
        }
    }

    private static @Nullable Map<Item, Double> values;

    private ItemValues() {
    }

    public static TagKey<Item> tag(TradeCategory category) {
        return TAGS.get(category);
    }

    /** 配方或原料表变了（重新加载数据包、换存档）：下次用到时重算。 */
    static synchronized void invalidate() {
        values = null;
    }

    private static synchronized Map<Item, Double> values(MinecraftServer server) {
        if (values == null) {
            values = compute(server);
        }
        return values;
    }

    /** 一样东西（不管数据组件）的基准价；没有配方又不在原料表里的没有价钱。 */
    public static OptionalDouble value(MinecraftServer server, Item item) {
        Double value = values(server).get(item);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /** 物品标签 {@code universe:trade/<类>} 决定的大类，没打标签的按价钱估。 */
    public static TradeCategory category(Item item, double value) {
        Holder<Item> holder = item.builtInRegistryHolder();
        for (TradeCategory category : TradeCategory.values()) {
            if (holder.is(TAGS.get(category))) {
                return category;
            }
        }
        return TradeCategory.byValue(value);
    }

    /** 模块的品级；还没随机过的按 C 级。 */
    public static @Nullable Grade grade(ItemStack stack) {
        if (stack.getItem() instanceof TechModuleItem item) {
            return item.module(stack).grade();
        }
        return null;
    }

    public static double gradeFactor(Grade grade) {
        return GRADE_FACTOR[grade.ordinal()];
    }

    /**
     * 行情里区分货物的键：物品 ID，模块再加上品级（{@code universe:module_x#B}），不同品级各压各的价。
     */
    public static String key(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Grade grade = grade(stack);
        return grade == null ? id : id + "#" + grade.letter();
    }

    /** 能不能卖：有价钱，而且除了耐久、修理费、模块数值以外没有别的数据。 */
    public static boolean sellable(MinecraftServer server, ItemStack stack) {
        if (stack.isEmpty() || value(server, stack.getItem()).isEmpty()) {
            return false;
        }
        if (stack.isComponentsPatchEmpty()) {
            return true;
        }
        Set<DataComponentType<?>> allowed = Set.of(DataComponents.DAMAGE, DataComponents.REPAIR_COST, UniverseDataComponents.TECH_MODULE.get());
        for (DataComponentType<?> type : stack.getComponentsPatch().keySet()) {
            if (!allowed.contains(type)) {
                return false;
            }
        }
        return true;
    }

    /** 一件的基准价：磨损按剩余耐久折价，模块按品级翻倍。不能卖返回 0。 */
    public static double unitValue(MinecraftServer server, ItemStack stack) {
        if (!sellable(server, stack)) {
            return 0;
        }
        double value = value(server, stack.getItem()).orElse(0);
        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            value *= Math.max(0, 1 - stack.getDamageValue() / (double) stack.getMaxDamage());
        }
        Grade grade = grade(stack);
        if (grade != null) {
            value *= gradeFactor(grade);
        }
        return value;
    }

    // ---- 计算

    private static Map<Item, Double> compute(MinecraftServer server) {
        long start = System.nanoTime();
        Map<String, Double> raw = new HashMap<>();
        // 先展开标签，再放单独列出的东西，单独列出的优先
        RawValues.DEFAULT.forEach((key, value) -> {
            if (key.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1)));
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    raw.putIfAbsent(id(holder.value()), value);
                }
            }
        });
        RawValues.DEFAULT.forEach((key, value) -> {
            if (!key.startsWith("#")) {
                if (BuiltInRegistries.ITEM.containsKey(Identifier.parse(key))) {
                    raw.put(key, value);
                } else {
                    LOGGER.warn("原料价表里的物品不存在：{}", key);
                }
            }
        });
        List<ValueGraph.Recipe> recipes = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                ValueGraph.Recipe recipe = convert(holder.value());
                if (recipe != null) {
                    recipes.add(recipe);
                }
            } catch (RuntimeException e) {
                // 特殊配方（烟花、修理……）没有固定的原料和产物，跳过
                LOGGER.debug("跳过配方 {}：{}", holder.id(), e.toString());
            }
        }
        Map<String, Double> computed = ValueGraph.compute(raw, recipes);
        Map<Item, Double> out = new IdentityHashMap<>();
        computed.forEach((id, value) -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).ifPresent(item -> out.put(item, value)));
        LOGGER.info("算出 {} 样东西的价钱（{} 个配方，{} ms）", out.size(), recipes.size(), (System.nanoTime() - start) / 1_000_000);
        return out;
    }

    private static ValueGraph.@Nullable Recipe convert(Recipe<?> recipe) {
        if (recipe instanceof MachineRecipe machine) {
            List<ValueGraph.Input> inputs = new ArrayList<>();
            machine.ingredients().forEach(sized -> inputs.add(new ValueGraph.Input(choices(sized.ingredient()), sized.count())));
            List<ValueGraph.Output> outputs = new ArrayList<>();
            for (ItemStackTemplate result : machine.results()) {
                outputs.add(new ValueGraph.Output(id(result.item().value()), result.count()));
            }
            return new ValueGraph.Recipe(inputs, outputs, machine.time() / 20.0);
        }
        if (recipe instanceof NormalCraftingRecipe crafting) {
            return single(crafting.placementInfo().ingredients(), crafting.assemble(CraftingInput.EMPTY), CRAFT_SECONDS);
        }
        if (recipe instanceof SingleItemRecipe single) {
            double seconds = single instanceof AbstractCookingRecipe cooking ? cooking.cookingTime() / 20.0 : CRAFT_SECONDS;
            return single(List.of(single.input()), single.assemble(new SingleRecipeInput(ItemStack.EMPTY)), seconds);
        }
        return null;
    }

    private static ValueGraph.@Nullable Recipe single(List<Ingredient> ingredients, ItemStack result, double seconds) {
        if (ingredients.isEmpty() || result.isEmpty()) {
            return null;
        }
        List<ValueGraph.Input> inputs = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            inputs.add(new ValueGraph.Input(choices(ingredient), 1));
        }
        return new ValueGraph.Recipe(inputs, List.of(new ValueGraph.Output(id(result.getItem()), result.getCount())), seconds);
    }

    private static List<String> choices(Ingredient ingredient) {
        return ingredient.items().map(holder -> id(holder.value())).distinct().toList();
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
