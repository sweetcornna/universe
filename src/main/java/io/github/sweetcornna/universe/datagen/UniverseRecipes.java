package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;

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
