package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.core.fishing.Rarity;
import io.github.sweetcornna.universe.core.survival.Dish;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * M19 钓鱼的数据生成（子代理 A，ADR-0034 §一）：配方、模型、物品和方块标签。
 *
 * <ul>
 *   <li>钓鱼器在装配机里做（钛合金 2、磁线圈 2、电路板 1），捕鱼笼手工合成，都要研究"钓鱼"。</li>
 *   <li>六种饵和七道海鲜菜在营养处理器里做。菜的原料用鱼的标签：哪种鱼都行，传说鱼宴要一条传说的鱼。</li>
 *   <li>钓上来的鱼也能放进熔炉、烟熏炉、营火烤成熟的异星鱼。</li>
 * </ul>
 */
final class FishingData {
    /** 所有鱼。 */
    static final TagKey<Item> FISH = TagKey.create(Registries.ITEM, Universe.id("fish"));

    private FishingData() {
    }

    /** 按档次的鱼：{@code universe:fish/common} 这样。 */
    static TagKey<Item> fish(Rarity rarity) {
        return TagKey.create(Registries.ITEM, Universe.id("fish/" + rarity.key()));
    }

    // ---- 模型

    static void models(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        List<ItemLike> flat = new ArrayList<>();
        UniverseItems.FISH.values().forEach(item -> flat.add(item.get()));
        UniverseItems.BAITS.values().forEach(item -> flat.add(item.get()));
        UniverseItems.JUNK.values().forEach(item -> flat.add(item.get()));
        flat.add(UniverseItems.MESSAGE_BOTTLE.get());
        flat.add(UniverseItems.SEALED_CACHE.get());
        for (ItemLike item : flat) {
            itemModels.generateFlatItem(item.asItem(), ModelTemplates.FLAT_ITEM);
        }
        // 捕鱼笼：手写的笼子模型 models/block/fish_trap.json（外面一层网，里面再贴一层朝里的网，从网眼看得见对面）
        Block trap = UniverseBlocks.FISH_TRAP.get();
        Identifier model = ModelLocationUtils.getModelLocation(trap);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(trap, BlockModelGenerators.plainVariant(model)));
        blockModels.registerSimpleItemModel(trap, model);
    }

    // ---- 标签

    static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        UniverseItems.FISH.forEach((species, item) -> {
            ResourceKey<Item> key = item.key();
            tag.apply(FISH).add(key);
            tag.apply(fish(species.rarity())).add(key);
            tag.apply(Tags.Items.FOODS_RAW_FISH).add(key);
            tag.apply(ItemTags.FISHES).add(key);
            tag.apply(ItemValues.tag(TradeCategory.BIO)).add(key);
        });
    }

    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.FISH_TRAP.key());
    }

    // ---- 配方

    static void recipes(RecipeOutput output, HolderGetter<Item> items) {
        Ingredient anyFish = Ingredient.of(items.getOrThrow(FISH));
        Item titanium = UniverseItems.TITANIUM_ALLOY.get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        machine(output, Kind.ASSEMBLING, ToolWeapon.FISHING_RIG.id(), 100, "fishing", in(titanium, 2, coil, 2, board, 1),
                UniverseItems.WEAPONS.get(ToolWeapon.FISHING_RIG).get(), 1);
        // 捕鱼笼：铁栏杆围一圈，底下一个箱子装鱼，上面一块铁锭压着
        shaped(output, "fishing", UniverseItems.FISH_TRAP.get(), 1, List.of("BIB", "B B", "BCB"),
                Map.of('B', Items.IRON_BARS, 'I', Items.IRON_INGOT, 'C', Items.CHEST));

        // 饵：腥饵是鱼肉拌异星肉；亮片拟饵是生物凝胶的软身子挂金片；深水坠饵坠铁；夜光饵用伽马根的荧光须；
        // 静电拟饵里夹着雷击石；星核饵要稀有的鱼、能量碎片和活珍珠
        Item meat = UniverseItems.ALIEN_MEAT.get();
        Item gel = UniverseItems.BIO_GEL.get();
        bait(output, Bait.STINK, 4, in(UniverseItems.ALIEN_FISH.get(), 1, meat, 1));
        bait(output, Bait.SPINNER, 2, in(gel, 1, Items.GOLD_NUGGET, 2));
        bait(output, Bait.SINKER, 2, in(meat, 1, Items.IRON_NUGGET, 3));
        bait(output, Bait.GLOW, 2, in(meat, 1, resource("gamma_root"), 1));
        bait(output, Bait.STATIC, 2, in(gel, 1, UniverseItems.FULGURITE.get(), 1));
        bait(output, Bait.STAR_CORE, 1, List.of(new SizedIngredient(Ingredient.of(items.getOrThrow(fish(Rarity.RARE))), 1),
                SizedIngredient.of(UniverseItems.ENERGY_SHARD.get(), 2), SizedIngredient.of(UniverseItems.LIVING_PEARL.get(), 1)));

        // 海鲜菜：营养处理器最多 3 样原料
        Item water = UniverseItems.WATER.get();
        SizedIngredient fish = new SizedIngredient(anyFish, 1);
        dish(output, Dish.GRILLED_FILLET, 50, List.of(fish));
        dish(output, Dish.FISH_SOUP, 100, List.of(fish, SizedIngredient.of(resource("gamma_root"), 1), SizedIngredient.of(water, 1)));
        dish(output, Dish.SASHIMI_PLATTER, 100, List.of(new SizedIngredient(anyFish, 2), SizedIngredient.of(dishItem(Dish.KELP_CRISPS), 1)));
        dish(output, Dish.SMOKED_FISH, 100, List.of(fish, SizedIngredient.of(Items.CHARCOAL, 1)));
        // 深海炖菜：海带囊里的氧气炖进去
        dish(output, Dish.DEEP_SEA_STEW, 100, List.of(new SizedIngredient(anyFish, 2), SizedIngredient.of(resource("kelp_pod"), 2),
                SizedIngredient.of(water, 1)));
        // 冰鱼汤：日焰辣酱煮的热汤
        dish(output, Dish.ICE_FISH_SOUP, 100, List.of(fish, SizedIngredient.of(dishItem(Dish.SOLAR_SAUCE), 1), SizedIngredient.of(water, 1)));
        dish(output, Dish.LEGENDARY_FEAST, 200, List.of(new SizedIngredient(Ingredient.of(items.getOrThrow(fish(Rarity.LEGENDARY))), 1),
                new SizedIngredient(Ingredient.of(items.getOrThrow(fish(Rarity.RARE))), 2), SizedIngredient.of(dishItem(Dish.SOLAR_PIE), 1)));

        // 鱼烤成熟的异星鱼
        Criterion<InventoryChangeTrigger.TriggerInstance> hasFish = CriteriaTriggers.INVENTORY_CHANGED.createCriterion(
                new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY,
                        List.of(ItemPredicate.Builder.item().of(items, FISH).build())));
        Item cooked = UniverseItems.COOKED_ALIEN_FISH.get();
        SimpleCookingRecipeBuilder.smelting(anyFish, RecipeCategory.FOOD, net.minecraft.world.item.crafting.CookingBookCategory.FOOD, cooked, 0.35F, 200)
                .unlockedBy("has_fish", hasFish).save(output, "universe:cooked_alien_fish_from_fish_smelting");
        SimpleCookingRecipeBuilder.smoking(anyFish, RecipeCategory.FOOD, cooked, 0.35F, 100)
                .unlockedBy("has_fish", hasFish).save(output, "universe:cooked_alien_fish_from_fish_smoking");
        SimpleCookingRecipeBuilder.campfireCooking(anyFish, RecipeCategory.FOOD, cooked, 0.35F, 600)
                .unlockedBy("has_fish", hasFish).save(output, "universe:cooked_alien_fish_from_fish_campfire_cooking");
    }

    private static void bait(RecipeOutput output, Bait bait, int count, List<SizedIngredient> ingredients) {
        machine(output, Kind.NUTRIENT, bait.id(), 50, "fishing", ingredients, UniverseItems.BAITS.get(bait).get(), count);
    }

    private static void dish(RecipeOutput output, Dish dish, int time, List<SizedIngredient> ingredients) {
        machine(output, Kind.NUTRIENT, dish.id(), time, "", ingredients, dishItem(dish), 1);
    }

    private static Item dishItem(Dish dish) {
        return UniverseItems.DISHES.get(dish).get();
    }

    private static Item resource(String name) {
        return UniverseItems.RESOURCES.get(name).get();
    }

    /** 设备配方，ID 是 {@code universe:<类型>/<名字>}，和 {@code UniverseRecipes} 的一样。 */
    private static void machine(RecipeOutput output, Kind kind, String name, int time, String research, List<SizedIngredient> ingredients,
            ItemLike result, int count) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(type + "/" + name)),
                new MachineRecipe(kind, ingredients, List.of(new ItemStackTemplate(result.asItem(), count)), time, research), null);
    }

    /** 要研究解锁的有序合成，配方 ID 用产物的名字。 */
    private static void shaped(RecipeOutput output, String research, ItemLike result, int count, List<String> pattern,
            Map<Character, ? extends ItemLike> key) {
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new java.util.TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(RecipeCategory.MISC, null), ShapedRecipePattern.of(ingredients, pattern),
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
}
