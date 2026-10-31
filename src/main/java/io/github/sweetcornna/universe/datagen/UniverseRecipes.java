package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.survival.Dish;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/** 配方（26.3 里配方是数据包注册表的条目，走 {@code GatherDataRegistryEntriesEvent}）。 */
final class UniverseRecipes extends RecipeProvider {
    UniverseRecipes(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
        super(recipes, advancements);
    }

    @Override
    protected void buildRecipes() {
        // 烤肉、烤鱼：熔炉、烟熏炉、营火，时间和原版的牛肉、鳕鱼一样
        this.cook(UniverseItems.ALIEN_MEAT.get(), UniverseItems.COOKED_ALIEN_MEAT.get(), "smelting", SmeltingRecipe::new, 200);
        this.cook(UniverseItems.ALIEN_MEAT.get(), UniverseItems.COOKED_ALIEN_MEAT.get(), "smoking", SmokingRecipe::new, 100);
        this.cook(UniverseItems.ALIEN_MEAT.get(), UniverseItems.COOKED_ALIEN_MEAT.get(), "campfire_cooking", CampfireCookingRecipe::new, 600);
        this.cook(UniverseItems.ALIEN_FISH.get(), UniverseItems.COOKED_ALIEN_FISH.get(), "smelting", SmeltingRecipe::new, 200);
        this.cook(UniverseItems.ALIEN_FISH.get(), UniverseItems.COOKED_ALIEN_FISH.get(), "smoking", SmokingRecipe::new, 100);
        this.cook(UniverseItems.ALIEN_FISH.get(), UniverseItems.COOKED_ALIEN_FISH.get(), "campfire_cooking", CampfireCookingRecipe::new, 600);
        // 兽皮鞣成皮革
        this.shapeless(RecipeCategory.MISC, Items.LEATHER)
                .requires(UniverseItems.ALIEN_HIDE.get(), 2)
                .unlockedBy(getHasName(UniverseItems.ALIEN_HIDE.get()), this.has(UniverseItems.ALIEN_HIDE.get()))
                .save(this.output, "universe:leather_from_alien_hide");
        this.husbandry();
        this.ships();
        this.pois();
        this.ores();
        this.suit();
        this.multitool();
        this.factory();
        this.midgame();
        this.dyson();
        this.endgame();
        this.factoryDepth();
        this.darkFogBases();
        this.darkFogFacilities();
        this.darkFogTurrets();
        this.darkFogSupport();
        this.darkFogPlasma();
        this.darkFogShield();
        this.planetLife();
        this.wrapUpInterop();
        this.wrapUpFactory();
        this.m18Fleet();
        // M18 舰队和战斗（A）的配方加在这行上面
        // M18 物流调度（B）的配方加在这行上面
        // M18 统计和研究（C）的配方加在这行上面
        this.m18Beacons();
        // M18 信标、透镜、矿脉、蓝图（D）的配方加在这行上面
        FishingData.recipes(this.output, this.items);
        // M19 钓鱼（A）的配方加在这行上面
        // M19 聚落（B）的配方加在这行上面
        // 化石底座、骨架展台（ADR-0034 §三）：石头凿的，展台正面嵌一块铜牌；要研究"古生物学"
        this.researchShaped("paleontology", RecipeCategory.DECORATIONS, UniverseItems.FOSSIL_PEDESTAL.get(), 1, List.of("SSS", " S ", "SSS"),
                Map.of('S', Items.STONE));
        this.researchShaped("paleontology", RecipeCategory.DECORATIONS, UniverseItems.SKELETON_DISPLAY.get(), 1, List.of("SCS", "SSS"),
                Map.of('S', Items.STONE, 'C', Items.COPPER_INGOT));
        // M19 化石（C）的配方加在这行上面
        DeepSpaceDatagen.recipes(this.output);
        // M19 深空和重力枪（D）的配方加在这行上面
        // M19 科技布局和局部危害（E）的配方加在这行上面
        ChemistryDatagen.recipes(this.output);
        // M20 化学（A）的配方加在这行上面
        ReactorDatagen.recipes(this.output);
        // M20 反应釜（B）的配方加在这行上面
        MicrobeDatagen.recipes(this.output);
        // M20 微生物和基因（C）的配方加在这行上面
        BioreactorDatagen.recipes(this.output);
        // M20 生物反应器（D）的配方加在这行上面
        TerraformDatagen.recipes(this.output);
        // M20 星球改造（E）的配方加在这行上面
    }

    /**
     * M18 的舰队单位（子代理 A，ADR-0033 §一）：都在装配机里做，一级一级拿上一种当底子。原型机是一块电路板加推进器，
     * 精准无人机加处理器和晶体硅的瞄准镜、钛合金的机身，攻击无人机加电浆激发器的炮；炮艇是钛合金的船身配加力推进器，驱逐舰拿炮艇加钛合金、量子芯片和黑雾的物质重组器。
     */
    private void m18Fleet() {
        Item steel = UniverseItems.STEEL.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item exciter = UniverseItems.PLASMA_EXCITER.get();
        Item titanium = UniverseItems.TITANIUM_ALLOY.get();
        Item reinforced = UniverseItems.REINFORCED_THRUSTER.get();
        this.machine(Kind.ASSEMBLING, "prototype", 80, "ground_squadron", in(steel, 3, board, 2, coil, 2, UniverseItems.THRUSTER.get(), 1),
                out(UniverseItems.PROTOTYPE.get(), 1));
        this.machine(Kind.ASSEMBLING, "precision_drone", 120, "precision_drones",
                in(UniverseItems.PROTOTYPE.get(), 1, processor, 2, UniverseItems.CRYSTAL_SILICON.get(), 2, titanium, 2),
                out(UniverseItems.PRECISION_DRONE.get(), 1));
        this.machine(Kind.ASSEMBLING, "attack_drone", 160, "attack_drones",
                in(UniverseItems.PRECISION_DRONE.get(), 1, exciter, 2, titanium, 4, UniverseItems.SUPER_MAGNETIC_RING.get(), 1),
                out(UniverseItems.ATTACK_DRONE.get(), 1));
        // 装配机最多 4 种原料
        this.machine(Kind.ASSEMBLING, "corvette", 400, "space_fleet", in(titanium, 20, processor, 6, reinforced, 4, exciter, 4),
                out(UniverseItems.CORVETTE.get(), 1));
        this.machine(Kind.ASSEMBLING, "destroyer", 800, "destroyers", in(UniverseItems.CORVETTE.get(), 1, titanium, 30,
                UniverseItems.QUANTUM_CHIP.get(), 4, UniverseItems.MATTER_RECOMBINATOR.get(), 4), out(UniverseItems.DESTROYER.get(), 1));
    }

    /** M18 的配方：黑雾透镜、全息信标（子代理 D，ADR-0033 §六、§七）。 */
    private void m18Beacons() {
        // 黑雾透镜：引力透镜 1、核心素 1、能量碎片 4，装配机里 8 秒
        this.machine(Kind.ASSEMBLING, "dark_fog_lens", 160, "dark_fog_lens",
                in(UniverseItems.GRAVITON_LENS.get(), 1, UniverseItems.CORE_ELEMENT.get(), 1, UniverseItems.ENERGY_SHARD.get(), 4),
                out(UniverseItems.DARK_FOG_LENS.get(), 1));
        // 全息信标：铁做底座，电路板和磁线圈做投影，玻璃做发光盘；装配机里做或者手工合成
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        this.machine(Kind.ASSEMBLING, "holo_beacon", 60, "holo_beacon",
                in(Items.IRON_INGOT, 4, board, 2, coil, 2, Items.GLASS, 1), out(UniverseItems.HOLO_BEACON.get(), 1));
        this.researchShaped("holo_beacon", RecipeCategory.DECORATIONS, UniverseItems.HOLO_BEACON.get(), 1, List.of(" G ", "MBM", "III"),
                Map.of('G', Items.GLASS, 'M', coil, 'B', board, 'I', Items.IRON_INGOT));
    }

    /**
     * 星球生活（ADR-0026）：营养处理器和便携精炼机开局在工作台上就能做，烧燃料、不要电。营养处理器一样原料的 2.5 秒一份，
     * 组合菜 5 秒；精炼机按 ADR 的表。
     */
    private void planetLife() {
        this.shaped(RecipeCategory.FOOD, UniverseItems.NUTRIENT_PROCESSOR.get())
                .pattern("III").pattern("GFG").pattern("ICI")
                .define('I', Items.IRON_INGOT).define('G', Items.GLASS_PANE).define('F', Items.FURNACE).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.FURNACE), this.has(Items.FURNACE))
                .save(this.output);
        this.shaped(RecipeCategory.MISC, UniverseItems.PORTABLE_REFINER.get())
                .pattern("I I").pattern("IFI").pattern("CCC")
                .define('I', Items.IRON_INGOT).define('F', Items.FURNACE).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.FURNACE), this.has(Items.FURNACE))
                .save(this.output);

        Item water = UniverseItems.WATER.get();
        Item sugar = Items.SUGAR;
        Item milk = Items.MILK_BUCKET;
        Item egg = UniverseItems.ALIEN_EGG.get();
        // 半成品
        this.dish(Dish.FLOUR, 50, in(Items.WHEAT, 1));
        this.dish(Dish.DOUGH, 50, in(dish(Dish.FLOUR), 1, water, 1));
        this.dish(Dish.BUTTER, 50, in(milk, 1));
        this.dish(Dish.PASTRY, 50, in(dish(Dish.BUTTER), 1, dish(Dish.FLOUR), 1));
        this.dish(Dish.CAKE_BASE, 50, in(egg, 1, dish(Dish.FLOUR), 1, sugar, 1));
        // 一样原料做的
        this.dish(Dish.STAR_JAM, 50, in(resource("star_bulb"), 2));
        this.dish(Dish.SOLAR_SAUCE, 50, in(resource("solanium"), 2));
        this.dish(Dish.MOULD_PASTE, 50, in(resource("fungal_mould"), 2));
        this.dish(Dish.CACTUS_JUICE, 50, in(resource("cactus_flesh"), 2));
        this.dish(Dish.FROST_ICE_CREAM, 50, in(resource("frost_crystal"), 1, sugar, 1, milk, 1));
        this.dish(Dish.ROOT_SOUP, 50, in(resource("gamma_root"), 2, water, 1));
        this.dish(Dish.ROASTED_MARROW, 50, in(resource("marrow_bulb"), 2));
        this.dish(Dish.GRAVITINO_JELLY, 50, in(resource("gravitino_ball"), 1, sugar, 1));
        this.dish(Dish.CUSTARD, 50, in(egg, 1, milk, 1, sugar, 1));
        this.dish(Dish.FLUFF_CANDY, 50, in(UniverseItems.ALIEN_FLUFF.get(), 2, sugar, 1));
        // 组合菜
        this.dish(Dish.STAR_PIE, 100, in(dish(Dish.STAR_JAM), 1, dish(Dish.DOUGH), 1));
        this.dish(Dish.MEAT_PIE, 100, in(UniverseItems.ALIEN_MEAT.get(), 1, dish(Dish.DOUGH), 1));
        this.dish(Dish.SPICY_ROAST, 100, in(dish(Dish.SOLAR_SAUCE), 1, UniverseItems.COOKED_ALIEN_MEAT.get(), 1));
        this.dish(Dish.BRAISED_MEAT, 100, in(dish(Dish.MOULD_PASTE), 1, UniverseItems.ALIEN_MEAT.get(), 1));
        this.dish(Dish.ALIEN_CAKE, 100, in(dish(Dish.CAKE_BASE), 1, dish(Dish.CUSTARD), 1));
        this.dish(Dish.STAR_CUSTARD, 100, in(dish(Dish.CUSTARD), 1, dish(Dish.STAR_JAM), 1));
        this.dish(Dish.EGG_TART, 100, in(dish(Dish.CUSTARD), 1, dish(Dish.PASTRY), 1));
        this.dish(Dish.FROST_TART, 100, in(dish(Dish.FROST_ICE_CREAM), 1, dish(Dish.PASTRY), 1));
        this.dish(Dish.SOLAR_PIE, 100, in(dish(Dish.SOLAR_SAUCE), 1, dish(Dish.PASTRY), 1, UniverseItems.COOKED_ALIEN_MEAT.get(), 1));
        this.dish(Dish.CACTUS_JELLY, 100, in(dish(Dish.CACTUS_JUICE), 1, sugar, 1));
        this.dish(Dish.GRAVITINO_PASTRY, 100, in(dish(Dish.GRAVITINO_JELLY), 1, dish(Dish.PASTRY), 1));
        this.dish(Dish.MARROW_SOUP, 100, in(dish(Dish.ROASTED_MARROW), 1, dish(Dish.BUTTER), 1, water, 1));
        // 鱼派：原版的鱼和异星生鱼都行
        this.dish(Dish.FISH_PIE, 100, List.of(new SizedIngredient(Ingredient.of(Items.COD, Items.SALMON, UniverseItems.ALIEN_FISH.get()), 1),
                SizedIngredient.of(dish(Dish.PASTRY), 1)));
        this.dish(Dish.STAR_CAKE, 100, in(dish(Dish.ALIEN_CAKE), 1, dish(Dish.STAR_JAM), 1));
        // 水下的几道：海带囊烤成脆片，生鱼裹上脆片是海味卷；烤鱼和熔炉烤出来的是同一样东西
        this.dish(Dish.KELP_CRISPS, 50, in(resource("kelp_pod"), 2));
        this.dish(Dish.SEAFOOD_ROLL, 100, in(UniverseItems.ALIEN_FISH.get(), 1, dish(Dish.KELP_CRISPS), 1));
        this.machine(Kind.NUTRIENT, "cooked_alien_fish", 50, "", in(UniverseItems.ALIEN_FISH.get(), 1),
                out(UniverseItems.COOKED_ALIEN_FISH.get(), 1));

        // 便携精炼机
        Kind refining = Kind.PORTABLE_REFINING;
        // 岩石和月壤里的氧（月壤制氧是把矿物里的氧还原出来）
        this.machine(refining, "oxygen", 160, "", List.of(new SizedIngredient(Ingredient.of(UniverseItems.PLANET_ROCK.get(),
                UniverseItems.PLANET_COBBLESTONE.get(), UniverseItems.PLANET_REGOLITH.get(), Items.STONE, Items.COBBLESTONE), 4)),
                out(resource("oxygen"), 1));
        this.machine(refining, "water", 80, "", List.of(new SizedIngredient(Ingredient.of(Items.ICE, Items.SNOW_BLOCK,
                UniverseItems.PLANET_ICE.get(), UniverseItems.PLANET_SNOW.get()), 1)), out(water, 1));
        this.machine(refining, "water_from_packed_ice", 80, "", in(Items.PACKED_ICE, 1), out(water, 4));
        this.machine(refining, "water_from_cactus", 80, "", in(resource("cactus_flesh"), 2), out(water, 1));
        this.machine(refining, "water_from_bucket", 80, "", in(Items.WATER_BUCKET, 1), out(water, 4));
        this.machine(refining, "charcoal", 80, "", List.of(new SizedIngredient(this.tag(ItemTags.LOGS_THAT_BURN), 1)), out(Items.CHARCOAL, 1));
        this.machine(refining, "glass", 80, "", List.of(new SizedIngredient(Ingredient.of(Items.SAND, Items.RED_SAND,
                UniverseItems.PLANET_SAND.get()), 1)), out(Items.GLASS, 1));
        this.machine(refining, "iron_ingot", 120, "", in(Items.RAW_IRON, 1), out(Items.IRON_INGOT, 1));
        this.machine(refining, "copper_ingot", 120, "", in(Items.RAW_COPPER, 1), out(Items.COPPER_INGOT, 1));
        this.machine(refining, "gold_ingot", 120, "", in(Items.RAW_GOLD, 1), out(Items.GOLD_INGOT, 1));
        this.machine(refining, "oxygen_capsule", 80, "", in(resource("oxygen"), 4), out(UniverseItems.OXYGEN_CAPSULE.get(), 1));
        this.machine(refining, "hazard_cell", 80, "", in(resource("sodium"), 4), out(UniverseItems.HAZARD_CELL.get(), 1));
        // 防护凝胶：霜晶防寒、日焰果隔热、伽马根的黑色素挡辐射、霉菌解毒
        this.machine(refining, "cold_gel", 120, "", in(resource("frost_crystal"), 2), out(UniverseItems.GELS.get(Hazard.COLD).get(), 1));
        this.machine(refining, "heat_gel", 120, "", in(resource("solanium"), 2), out(UniverseItems.GELS.get(Hazard.HEAT).get(), 1));
        this.machine(refining, "radiation_gel", 120, "", in(resource("gamma_root"), 2),
                out(UniverseItems.GELS.get(Hazard.RADIATION).get(), 1));
        this.machine(refining, "toxin_gel", 120, "", in(resource("fungal_mould"), 2), out(UniverseItems.GELS.get(Hazard.TOXIC).get(), 1));
    }

    /** M17 收尾的配方：互通（子代理 C，ADR-0030）。 */
    private void wrapUpInterop() {
        // 电网接口：蓄电器加电路板、磁线圈、铜（铜线），装配机里做或者手工合成
        Item accumulator = UniverseItems.ACCUMULATOR.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        this.machine(Kind.ASSEMBLING, "grid_interface", 60, "grid_interface",
                in(accumulator, 1, board, 4, coil, 6, Items.COPPER_INGOT, 8), out(UniverseItems.GRID_INTERFACE.get(), 1));
        this.researchShaped("grid_interface", RecipeCategory.REDSTONE, UniverseItems.GRID_INTERFACE.get(), 1, List.of("CBC", "MAM", "CBC"),
                Map.of('C', Items.COPPER_INGOT, 'B', board, 'M', coil, 'A', accumulator));
    }

    /** M17 收尾的配方：工厂物流（子代理 E，ADR-0030）。 */
    private void wrapUpFactory() {
        // 高级设备的黑雾配方：打黑雾攒下的零件顶掉奇异物质、引力透镜和量子芯片，比正常配方便宜、做得也快；
        // 跟正常配方一样由各自的科技解锁
        Item singularity = UniverseItems.NEGENTROPY_SINGULARITY.get();
        Item recombinator = UniverseItems.MATTER_RECOMBINATOR.get();
        Item neuron = UniverseItems.SILICON_NEURON.get();
        Item core = UniverseItems.CORE_ELEMENT.get();
        this.machine(Kind.ASSEMBLING, "negentropy_smelter_from_dark_fog", 240, "negentropy_smelting",
                in(UniverseItems.PLANE_SMELTER.get(), 1, singularity, 2, recombinator, 4), out(UniverseItems.NEGENTROPY_SMELTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "recomposing_assembler_from_dark_fog", 240, "recomposing_assembly",
                in(UniverseItems.ASSEMBLER_MK3.get(), 1, recombinator, 4, neuron, 4), out(UniverseItems.RECOMPOSING_ASSEMBLER.get(), 1));
        this.machine(Kind.ASSEMBLING, "quantum_chemical_plant_from_dark_fog", 240, "quantum_chemistry",
                in(UniverseItems.CHEMICAL_PLANT.get(), 1, singularity, 2, recombinator, 2), out(UniverseItems.QUANTUM_CHEMICAL_PLANT.get(), 1));
        this.machine(Kind.ASSEMBLING, "self_evolution_lab_from_dark_fog", 240, "self_evolution",
                in(UniverseItems.MATRIX_LAB.get(), 1, neuron, 4, core, 1), out(UniverseItems.SELF_EVOLUTION_LAB.get(), 1));
    }

    private void dish(Dish dish, int time, List<SizedIngredient> ingredients) {
        this.machine(Kind.NUTRIENT, dish.id(), time, "", ingredients, out(dish(dish), 1));
    }

    private static Item dish(Dish dish) {
        return UniverseItems.DISHES.get(dish).get();
    }

    private static Item resource(String name) {
        return UniverseItems.RESOURCES.get(name).get();
    }

    /**
     * 戴森球阶段（ADR-0016）：配方照戴森球计划，时间是戴森球计划的秒数 × 20；设备只有 4 个原料格，原料多的去掉一种。
     * 组装件和设备也能手工合成，化工、对撞机的产物和矩阵只能在设备里做。
     */
    private void dyson() {
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item gear = UniverseItems.GEAR.get();
        Item steel = UniverseItems.STEEL.get();
        Item prism = UniverseItems.PRISM.get();
        Item silicon = UniverseItems.ORE_SMELTED.get(OreKind.SILICON).get();
        Item titanium = UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get();
        Item water = UniverseItems.WATER.get();
        Item hydrogen = UniverseItems.HYDROGEN.get();
        Item deuterium = UniverseItems.DEUTERIUM.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item diamond = UniverseItems.SYNTHETIC_DIAMOND.get();
        Item graphene = UniverseItems.GRAPHENE.get();
        Item nanotube = UniverseItems.CARBON_NANOTUBE.get();
        Item titaniumCrystal = UniverseItems.TITANIUM_CRYSTAL.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item container = UniverseItems.PARTICLE_CONTAINER.get();
        Item combiner = UniverseItems.PHOTON_COMBINER.get();
        Item sail = UniverseItems.SOLAR_SAIL.get();
        Item frame = UniverseItems.FRAME_MATERIAL.get();
        Item component = UniverseItems.DYSON_SPHERE_COMPONENT.get();
        Item rocket = UniverseItems.SMALL_CARRIER_ROCKET.get();
        Item rod = UniverseItems.DEUTERON_FUEL_ROD.get();
        Item titaniumGlass = UniverseItems.TITANIUM_GLASS.get();
        Item casimir = UniverseItems.CASIMIR_CRYSTAL.get();
        Item filter = UniverseItems.PLANE_FILTER.get();
        Item quantum = UniverseItems.QUANTUM_CHIP.get();
        Item strange = UniverseItems.STRANGE_MATTER.get();
        Item lens = UniverseItems.GRAVITON_LENS.get();

        // ---- 组装件
        this.machine(Kind.ASSEMBLING, "photon_combiner", 60, "solar_sail_orbit", in(prism, 2, board, 1), out(combiner, 1));
        this.machine(Kind.ASSEMBLING, "solar_sail", 80, "solar_sail_orbit", in(graphene, 1, combiner, 1), out(sail, 2));
        this.machine(Kind.ASSEMBLING, "frame_material", 120, "frame_material", in(nanotube, 4, alloy, 1, silicon, 1), out(frame, 1));
        this.machine(Kind.ASSEMBLING, "dyson_sphere_component", 160, "vertical_launching_silo", in(frame, 3, sail, 3, processor, 3),
                out(component, 1));
        this.machine(Kind.ASSEMBLING, "small_carrier_rocket", 120, "vertical_launching_silo", in(component, 2, rod, 2, quantum, 2),
                out(rocket, 1));
        this.machine(Kind.ASSEMBLING, "deuteron_fuel_rod", 240, "vertical_launching_silo", in(alloy, 1, deuterium, 20, ring, 1), out(rod, 2));
        this.machine(Kind.ASSEMBLING, "plane_filter", 240, "quantum_printing", in(casimir, 1, titaniumGlass, 2), out(filter, 1));
        this.machine(Kind.ASSEMBLING, "quantum_chip", 120, "quantum_printing", in(processor, 2, filter, 2), out(quantum, 1));
        this.machine(Kind.ASSEMBLING, "graviton_lens", 120, "gravitational_wave_refraction", in(diamond, 4, strange, 1), out(lens, 1));
        this.machine(Kind.ASSEMBLING, "gravity_matrix", 480, "gravity_matrix", in(lens, 1, quantum, 1),
                out(UniverseItems.GRAVITY_MATRIX.get(), 2));
        this.machine(Kind.ASSEMBLING, "casimir_crystal", 80, "quantum_printing", in(titaniumCrystal, 1, graphene, 2, hydrogen, 12),
                out(casimir, 1));
        // 钛化玻璃照戴森球计划在制造台里做
        this.machine(Kind.ASSEMBLING, "titanium_glass", 100, "quantum_printing", in(Items.GLASS, 2, titanium, 2, water, 2), out(titaniumGlass, 2));
        // ---- 对撞
        this.machine(Kind.PARTICLE, "strange_matter", 160, "miniature_collider", in(container, 2, Items.IRON_INGOT, 2, deuterium, 10),
                out(strange, 1));

        // ---- 设备
        this.machine(Kind.ASSEMBLING, "em_rail_ejector", 120, "solar_sail_orbit", in(steel, 20, gear, 20, processor, 5, ring, 10),
                out(UniverseItems.EM_RAIL_EJECTOR.get(), 1));
        // 戴森球计划还要 20 高纯硅，设备只有 4 个原料格，去掉
        this.machine(Kind.ASSEMBLING, "ray_receiver", 160, "ray_receiver", in(steel, 20, combiner, 10, processor, 5, ring, 20),
                out(UniverseItems.RAY_RECEIVER.get(), 1));
        // 垂直发射井的原料减半
        this.machine(Kind.ASSEMBLING, "vertical_launching_silo", 600, "vertical_launching_silo", in(alloy, 40, frame, 15, lens, 10, quantum, 5),
                out(UniverseItems.VERTICAL_LAUNCHING_SILO.get(), 1));
        // 戴森球计划还要 10 石墨烯，去掉
        this.machine(Kind.ASSEMBLING, "miniature_particle_collider", 300, "miniature_collider", in(alloy, 20, frame, 20, ring, 50, processor, 8),
                out(UniverseItems.MINIATURE_PARTICLE_COLLIDER.get(), 1));

        // ---- 手工合成
        this.researchShaped("solar_sail_orbit", RecipeCategory.MISC, combiner, 1, List.of("PBP"), Map.of('P', prism, 'B', board));
        this.researchShaped("solar_sail_orbit", RecipeCategory.MISC, sail, 2, List.of("G", "C"), Map.of('G', graphene, 'C', combiner));
        this.researchShaped("frame_material", RecipeCategory.MISC, frame, 1, List.of("NNA", "NNS"), Map.of('N', nanotube, 'A', alloy, 'S', silicon));
        this.researchShaped("vertical_launching_silo", RecipeCategory.MISC, component, 1, List.of("FFF", "SSS", "PPP"),
                Map.of('F', frame, 'S', sail, 'P', processor));
        this.researchShaped("vertical_launching_silo", RecipeCategory.MISC, rocket, 1, List.of("CRQ", "CRQ"),
                Map.of('C', component, 'R', rod, 'Q', quantum));
        this.researchShaped("quantum_printing", RecipeCategory.MISC, filter, 1, List.of("GCG"), Map.of('C', casimir, 'G', titaniumGlass));
        this.researchShaped("quantum_printing", RecipeCategory.MISC, quantum, 1, List.of("PF", "FP"), Map.of('P', processor, 'F', filter));
        this.researchShaped("gravitational_wave_refraction", RecipeCategory.MISC, lens, 1, List.of(" D ", "DSD", " D "),
                Map.of('D', diamond, 'S', strange));
        this.researchShaped("solar_sail_orbit", RecipeCategory.REDSTONE, UniverseItems.EM_RAIL_EJECTOR.get(), 1, List.of("SRS", "GPG", "SGS"),
                Map.of('S', steel, 'R', ring, 'G', gear, 'P', processor));
        this.researchShaped("ray_receiver", RecipeCategory.REDSTONE, UniverseItems.RAY_RECEIVER.get(), 1, List.of("CCC", "RPR", "SRS"),
                Map.of('C', combiner, 'R', ring, 'P', processor, 'S', steel));
        this.researchShaped("vertical_launching_silo", RecipeCategory.REDSTONE, UniverseItems.VERTICAL_LAUNCHING_SILO.get(), 1,
                List.of("AFA", "LQL", "AFA"), Map.of('A', alloy, 'F', frame, 'L', lens, 'Q', quantum));
        this.researchShaped("miniature_collider", RecipeCategory.REDSTONE, UniverseItems.MINIATURE_PARTICLE_COLLIDER.get(), 1,
                List.of("ARA", "FPF", "ARA"), Map.of('A', alloy, 'R', ring, 'F', frame, 'P', processor));
    }

    /**
     * 工厂的补全（ADR-0022）：配方照戴森球计划，时间是戴森球计划的秒数 × 20；原料太多的按比例缩了。设备也能手工合成，
     * 增产剂、燃烧单元和燃料棒只能在制造台里做。地基手工合成一次出 8 个，当建筑方块用。
     */
    private void factoryDepth() {
        Item steel = UniverseItems.STEEL.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item gear = UniverseItems.GEAR.get();
        Item exciter = UniverseItems.PLASMA_EXCITER.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item frame = UniverseItems.FRAME_MATERIAL.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item container = UniverseItems.PARTICLE_CONTAINER.get();
        Item strange = UniverseItems.STRANGE_MATTER.get();
        Item lens = UniverseItems.GRAVITON_LENS.get();
        Item quantum = UniverseItems.QUANTUM_CHIP.get();
        Item titanium = UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get();
        Item mk1 = UniverseItems.PROLIFERATOR_MK1.get();
        Item mk2 = UniverseItems.PROLIFERATOR_MK2.get();

        // ---- 制造台：物品
        this.machine(Kind.ASSEMBLING, "proliferator_mk1", 10, "proliferator_mk1", in(Items.COAL, 1), out(mk1, 1));
        this.machine(Kind.ASSEMBLING, "proliferator_mk2", 20, "proliferator_mk2", in(mk1, 2, UniverseItems.SYNTHETIC_DIAMOND.get(), 1),
                out(mk2, 1));
        this.machine(Kind.ASSEMBLING, "proliferator_mk3", 40, "proliferator_mk3", in(mk2, 2, UniverseItems.CARBON_NANOTUBE.get(), 1),
                out(UniverseItems.PROLIFERATOR_MK3.get(), 1));
        this.machine(Kind.ASSEMBLING, "combustible_unit", 60, "thermal_power", in(Items.COAL, 3), out(UniverseItems.COMBUSTIBLE_UNIT.get(), 1));
        this.machine(Kind.ASSEMBLING, "hydrogen_fuel_rod", 120, "deuterium_fractionation", in(titanium, 1, UniverseItems.HYDROGEN.get(), 10),
                out(UniverseItems.HYDROGEN_FUEL_ROD.get(), 2));
        this.machine(Kind.ASSEMBLING, "foundation", 20, "steel_smelting", in(Items.STONE, 3, steel, 1), out(UniverseItems.FOUNDATION.get(), 1));

        // ---- 制造台：设备
        this.machine(Kind.ASSEMBLING, "spray_coater", 60, "proliferator_mk1", in(steel, 4, exciter, 2, board, 2),
                out(UniverseItems.SPRAY_COATER.get(), 1));
        this.machine(Kind.ASSEMBLING, "automatic_piler", 60, "integrated_logistics", in(steel, 3, gear, 4, ring, 1, processor, 2),
                out(UniverseItems.AUTOMATIC_PILER.get(), 1));
        this.machine(Kind.ASSEMBLING, "traffic_monitor", 40, "integrated_logistics", in(Items.IRON_INGOT, 3, gear, 2, Items.GLASS, 1, board, 2),
                out(UniverseItems.TRAFFIC_MONITOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "pile_sorter", 20, "pile_sorting", in(UniverseItems.SORTER_MK3.get(), 2, ring, 1, processor, 1),
                out(UniverseItems.PILE_SORTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "wireless_power_tower", 60, "wireless_power", in(UniverseItems.TESLA_TOWER.get(), 1, exciter, 3),
                out(UniverseItems.WIRELESS_POWER_TOWER.get(), 1));
        this.machine(Kind.ASSEMBLING, "satellite_substation", 90, "satellite_power",
                in(UniverseItems.WIRELESS_POWER_TOWER.get(), 1, ring, 10, frame, 2), out(UniverseItems.SATELLITE_SUBSTATION.get(), 1));
        this.machine(Kind.ASSEMBLING, "geothermal_power_station", 120, "geothermal_power", in(steel, 15, Items.COPPER_INGOT, 20, processor, 4, ring, 1),
                out(UniverseItems.GEOTHERMAL_POWER_STATION.get(), 1));
        this.machine(Kind.ASSEMBLING, "energy_exchanger", 300, "energy_exchange", in(alloy, 20, steel, 20, processor, 10, container, 4),
                out(UniverseItems.ENERGY_EXCHANGER.get(), 1));
        // 高级设备：低一级的设备加奇异物质、引力透镜、量子芯片
        this.machine(Kind.ASSEMBLING, "negentropy_smelter", 300, "negentropy_smelting",
                in(UniverseItems.PLANE_SMELTER.get(), 1, strange, 4, lens, 4, quantum, 4), out(UniverseItems.NEGENTROPY_SMELTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "recomposing_assembler", 300, "recomposing_assembly",
                in(UniverseItems.ASSEMBLER_MK3.get(), 1, strange, 4, lens, 4, quantum, 4), out(UniverseItems.RECOMPOSING_ASSEMBLER.get(), 1));
        this.machine(Kind.ASSEMBLING, "quantum_chemical_plant", 300, "quantum_chemistry",
                in(UniverseItems.CHEMICAL_PLANT.get(), 1, strange, 4, lens, 4, quantum, 4), out(UniverseItems.QUANTUM_CHEMICAL_PLANT.get(), 1));
        this.machine(Kind.ASSEMBLING, "self_evolution_lab", 300, "self_evolution",
                in(UniverseItems.MATRIX_LAB.get(), 1, strange, 4, lens, 4, quantum, 4), out(UniverseItems.SELF_EVOLUTION_LAB.get(), 1));

        // ---- 手工合成
        this.researchShaped("steel_smelting", RecipeCategory.BUILDING_BLOCKS, UniverseItems.FOUNDATION.get(), 8, List.of("CCC", "CSC", "CCC"),
                Map.of('C', Items.STONE, 'S', steel));
        this.researchShaped("proliferator_mk1", RecipeCategory.REDSTONE, UniverseItems.SPRAY_COATER.get(), 1, List.of("SPS", "BSB"),
                Map.of('S', steel, 'P', exciter, 'B', board));
        this.researchShaped("integrated_logistics", RecipeCategory.REDSTONE, UniverseItems.AUTOMATIC_PILER.get(), 1, List.of("SGS", "GRG", "SPS"),
                Map.of('S', steel, 'G', gear, 'R', ring, 'P', processor));
        this.researchShaped("integrated_logistics", RecipeCategory.REDSTONE, UniverseItems.TRAFFIC_MONITOR.get(), 1, List.of("IGI", "BLB"),
                Map.of('I', Items.IRON_INGOT, 'G', gear, 'L', Items.GLASS, 'B', board));
        this.researchShaped("pile_sorting", RecipeCategory.REDSTONE, UniverseItems.PILE_SORTER.get(), 1, List.of("SRS", " P "),
                Map.of('S', UniverseItems.SORTER_MK3.get(), 'R', ring, 'P', processor));
        this.researchShaped("wireless_power", RecipeCategory.REDSTONE, UniverseItems.WIRELESS_POWER_TOWER.get(), 1, List.of("PTP", " P "),
                Map.of('P', exciter, 'T', UniverseItems.TESLA_TOWER.get()));
        this.researchShaped("satellite_power", RecipeCategory.REDSTONE, UniverseItems.SATELLITE_SUBSTATION.get(), 1, List.of("RFR", "RWR", "RFR"),
                Map.of('R', ring, 'F', frame, 'W', UniverseItems.WIRELESS_POWER_TOWER.get()));
        this.researchShaped("geothermal_power", RecipeCategory.REDSTONE, UniverseItems.GEOTHERMAL_POWER_STATION.get(), 1, List.of("SPS", "CRC", "SCS"),
                Map.of('S', steel, 'P', processor, 'C', Items.COPPER_INGOT, 'R', ring));
        this.researchShaped("energy_exchange", RecipeCategory.REDSTONE, UniverseItems.ENERGY_EXCHANGER.get(), 1, List.of("ASA", "PCP", "ASA"),
                Map.of('A', alloy, 'S', steel, 'P', processor, 'C', container));
        for (Map.Entry<String, List<Item>> advanced : Map.<String, List<Item>>of(
                "negentropy_smelting", List.of(UniverseItems.PLANE_SMELTER.get(), UniverseItems.NEGENTROPY_SMELTER.get()),
                "recomposing_assembly", List.of(UniverseItems.ASSEMBLER_MK3.get(), UniverseItems.RECOMPOSING_ASSEMBLER.get()),
                "quantum_chemistry", List.of(UniverseItems.CHEMICAL_PLANT.get(), UniverseItems.QUANTUM_CHEMICAL_PLANT.get()),
                "self_evolution", List.of(UniverseItems.MATRIX_LAB.get(), UniverseItems.SELF_EVOLUTION_LAB.get())).entrySet()) {
            this.researchShaped(advanced.getKey(), RecipeCategory.REDSTONE, advanced.getValue().get(1), 1, List.of("LSL", "QMQ", "LSL"),
                    Map.of('L', lens, 'S', strange, 'Q', quantum, 'M', advanced.getValue().get(0)));
        }
    }

    /**
     * 终局（ADR-0018）：配方照戴森球计划。宇宙矩阵要五种矩阵加反物质，制造台只有 4 个原料格，先把蓝红黄合成复合矩阵。
     * 两种发电设备也能手工合成。
     */
    private void endgame() {
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item frame = UniverseItems.FRAME_MATERIAL.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item nanotube = UniverseItems.CARBON_NANOTUBE.get();
        Item quantum = UniverseItems.QUANTUM_CHIP.get();
        Item hydrogen = UniverseItems.HYDROGEN.get();
        Item antimatter = UniverseItems.ANTIMATTER.get();
        Item sphere = UniverseItems.ANNIHILATION_CONSTRAINT_SPHERE.get();
        Item composite = UniverseItems.COMPOSITE_MATRIX.get();
        this.machine(Kind.PARTICLE, "antimatter", 40, "dirac_inversion", in(UniverseItems.CRITICAL_PHOTON.get(), 2),
                List.of(new ItemStackTemplate(antimatter, 2), new ItemStackTemplate(hydrogen, 2)));
        this.machine(Kind.ASSEMBLING, "annihilation_constraint_sphere", 400, "controlled_annihilation",
                in(UniverseItems.PARTICLE_CONTAINER.get(), 1, processor, 1), out(sphere, 1));
        this.machine(Kind.ASSEMBLING, "antimatter_fuel_rod", 480, "controlled_annihilation", in(antimatter, 12, hydrogen, 12, sphere, 1, alloy, 1),
                out(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 2));
        this.machine(Kind.ASSEMBLING, "composite_matrix", 100, "universe_matrix", in(UniverseItems.ELECTROMAGNETIC_MATRIX.get(), 1,
                UniverseItems.ENERGY_MATRIX.get(), 1, UniverseItems.STRUCTURE_MATRIX.get(), 1), out(composite, 1));
        this.machine(Kind.ASSEMBLING, "universe_matrix", 300, "universe_matrix", in(composite, 1, UniverseItems.INFORMATION_MATRIX.get(), 1,
                UniverseItems.GRAVITY_MATRIX.get(), 1, antimatter, 1), out(UniverseItems.UNIVERSE_MATRIX.get(), 1));
        this.machine(Kind.ASSEMBLING, "mini_fusion_power_plant", 200, "mini_fusion_power", in(alloy, 12, ring, 10, nanotube, 8, processor, 4),
                out(UniverseItems.MINI_FUSION_POWER_PLANT.get(), 1));
        this.machine(Kind.ASSEMBLING, "artificial_star", 600, "artificial_star", in(alloy, 20, frame, 20, sphere, 10, quantum, 10),
                out(UniverseItems.ARTIFICIAL_STAR.get(), 1));
        this.researchShaped("mini_fusion_power", RecipeCategory.REDSTONE, UniverseItems.MINI_FUSION_POWER_PLANT.get(), 1,
                List.of("ARA", "NPN", "ARA"), Map.of('A', alloy, 'R', ring, 'N', nanotube, 'P', processor));
        this.researchShaped("artificial_star", RecipeCategory.REDSTONE, UniverseItems.ARTIFICIAL_STAR.get(), 1,
                List.of("FSF", "AQA", "FSF"), Map.of('F', frame, 'S', sphere, 'A', alloy, 'Q', quantum));
    }

    /**
     * 中期工业（ADR-0015）：配方照戴森球计划，原料多于 4 种的合并掉；时间是戴森球计划的秒数 × 20。
     * 设备和组装件也能手工合成（9 格放不下的按比例缩了），化工、冶炼的产物和矩阵只能在设备里做。
     */
    private void midgame() {
        Item magnet = UniverseItems.ORE_SMELTED.get(OreKind.MAGNETIC).get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item gear = UniverseItems.GEAR.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item motor = UniverseItems.ELECTRIC_MOTOR.get();
        Item steel = UniverseItems.STEEL.get();
        Item graphite = UniverseItems.ENERGETIC_GRAPHITE.get();
        Item silicon = UniverseItems.ORE_SMELTED.get(OreKind.SILICON).get();
        Item titanium = UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get();
        Item water = UniverseItems.WATER.get();
        Item refined = UniverseItems.REFINED_OIL.get();
        Item crystalSilicon = UniverseItems.CRYSTAL_SILICON.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item diamond = UniverseItems.SYNTHETIC_DIAMOND.get();
        Item plastic = UniverseItems.PLASTIC.get();
        Item acid = UniverseItems.SULFURIC_ACID.get();
        Item organic = UniverseItems.oreDrop(OreKind.ORGANIC_CRYSTAL);
        Item graphene = UniverseItems.GRAPHENE.get();
        Item nanotube = UniverseItems.CARBON_NANOTUBE.get();
        Item titaniumCrystal = UniverseItems.TITANIUM_CRYSTAL.get();
        Item micro = UniverseItems.MICROCRYSTALLINE_COMPONENT.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item broadband = UniverseItems.PARTICLE_BROADBAND.get();
        Item thruster = UniverseItems.THRUSTER.get();
        Item reinforced = UniverseItems.REINFORCED_THRUSTER.get();
        Item turbine = UniverseItems.ELECTROMAGNETIC_TURBINE.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item container = UniverseItems.PARTICLE_CONTAINER.get();

        // ---- 冶炼
        this.machine(Kind.SMELTING, "crystal_silicon", 40, "semiconductor", in(silicon, 1), out(crystalSilicon, 1));
        this.machine(Kind.SMELTING, "titanium_alloy", 240, "titanium_alloy", in(titanium, 4, steel, 4, acid, 8), out(alloy, 4));
        this.machine(Kind.SMELTING, "synthetic_diamond", 40, "structure_matrix", in(graphite, 1), out(diamond, 1));

        // ---- 化工
        Ingredient stone = Ingredient.of(Items.COBBLESTONE, Items.STONE, UniverseBlocks.PLANET_COBBLESTONE.get(), UniverseBlocks.PLANET_ROCK.get());
        this.machine(Kind.CHEMICAL, "plastic", 60, "basic_chemical", in(refined, 2, graphite, 1), out(plastic, 1));
        this.machine(Kind.CHEMICAL, "sulfuric_acid", 120, "basic_chemical",
                List.of(SizedIngredient.of(refined, 6), new SizedIngredient(stone, 8), SizedIngredient.of(water, 4)), out(acid, 4));
        this.machine(Kind.CHEMICAL, "organic_crystal", 120, "basic_chemical", in(plastic, 2, refined, 1, water, 1), out(organic, 1));
        // 喂养动物得到的生物胶质也能做有机晶体（ADR-0017）
        this.machine(Kind.CHEMICAL, "organic_crystal_from_bio_gel", 120, "basic_chemical", in(UniverseItems.BIO_GEL.get(), 3, water, 1),
                out(organic, 1));
        this.machine(Kind.CHEMICAL, "graphene", 60, "high_strength_materials", in(acid, 3, graphite, 2), out(graphene, 2));
        this.machine(Kind.CHEMICAL, "carbon_nanotube", 80, "high_strength_materials", in(graphene, 3, titanium, 1), out(nanotube, 2));
        // 星球上的资源：可燃冰直接做石墨烯，甲烷裂解 CH₄ → C + 2H₂，水合物里的水排掉（RecipeAtomBalanceTest 里写了）；
        // 硫磺烧成二氧化硫、转化、吸收，总的是 2S + 3O₂ + 2H₂O → 2H₂SO₄（ADR-0037 §二）
        this.machine(Kind.CHEMICAL, "graphene_from_fire_ice", 40, "high_strength_materials", in(UniverseItems.oreDrop(OreKind.FIRE_ICE), 2),
                List.of(new ItemStackTemplate(graphene, 2), new ItemStackTemplate(UniverseItems.HYDROGEN.get(), 4)));
        this.machine(Kind.CHEMICAL, "sulfuric_acid_from_sulfur", 60, "basic_chemical",
                in(UniverseItems.oreDrop(OreKind.SULFUR), 2, resource("oxygen"), 3, water, 2), out(acid, 2));

        // ---- 制造台：组装件和矩阵
        this.machine(Kind.ASSEMBLING, "microcrystalline_component", 40, "semiconductor", in(silicon, 2, Items.COPPER_INGOT, 1), out(micro, 1));
        this.machine(Kind.ASSEMBLING, "processor", 60, "processor", in(board, 2, micro, 2), out(processor, 1));
        this.machine(Kind.ASSEMBLING, "particle_broadband", 160, "information_matrix", in(nanotube, 2, crystalSilicon, 2, plastic, 1),
                out(broadband, 1));
        this.machine(Kind.ASSEMBLING, "thruster", 80, "thruster", in(steel, 2, Items.COPPER_INGOT, 3), out(thruster, 1));
        this.machine(Kind.ASSEMBLING, "reinforced_thruster", 120, "interstellar_logistics", in(alloy, 5, turbine, 5), out(reinforced, 1));
        this.machine(Kind.ASSEMBLING, "electromagnetic_turbine", 40, "magnetic_levitation", in(motor, 2, coil, 2), out(turbine, 1));
        this.machine(Kind.ASSEMBLING, "super_magnetic_ring", 60, "magnetic_levitation", in(turbine, 2, magnet, 3, graphite, 1), out(ring, 1));
        this.machine(Kind.ASSEMBLING, "particle_container", 80, "planetary_logistics", in(turbine, 2, Items.COPPER_INGOT, 2, graphene, 2),
                out(container, 1));
        this.machine(Kind.ASSEMBLING, "titanium_crystal", 80, "structure_matrix", in(organic, 1, titanium, 3), out(titaniumCrystal, 1));
        this.machine(Kind.ASSEMBLING, "logistics_drone", 80, "planetary_logistics", in(Items.IRON_INGOT, 5, processor, 2, thruster, 2),
                out(UniverseItems.LOGISTICS_DRONE.get(), 1));
        this.machine(Kind.ASSEMBLING, "logistics_vessel", 120, "interstellar_logistics", in(alloy, 10, processor, 10, reinforced, 2),
                out(UniverseItems.LOGISTICS_VESSEL.get(), 1));
        // 配送（ADR-0023）：照戴森球计划的原料
        this.machine(Kind.ASSEMBLING, "logistics_distributor", 160, "distribution_logistics",
                in(Items.IRON_INGOT, 8, UniverseItems.PLASMA_EXCITER.get(), 4, processor, 4), out(UniverseItems.LOGISTICS_DISTRIBUTOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "logistics_bot", 40, "distribution_logistics", in(Items.IRON_INGOT, 2, processor, 1, thruster, 1),
                out(UniverseItems.LOGISTICS_BOT.get(), 1));
        this.machine(Kind.ASSEMBLING, "structure_matrix", 160, "structure_matrix", in(diamond, 1, titaniumCrystal, 1),
                out(UniverseItems.STRUCTURE_MATRIX.get(), 1));
        this.machine(Kind.ASSEMBLING, "information_matrix", 200, "information_matrix", in(processor, 2, broadband, 1),
                out(UniverseItems.INFORMATION_MATRIX.get(), 1));

        // ---- 制造台：设备
        this.machine(Kind.ASSEMBLING, "conveyor_belt_mk2", 20, "improved_logistics", in(UniverseItems.CONVEYOR_BELT.get(), 3, turbine, 1),
                out(UniverseItems.CONVEYOR_BELT_MK2.get(), 3));
        this.machine(Kind.ASSEMBLING, "conveyor_belt_mk3", 20, "efficient_logistics",
                in(UniverseItems.CONVEYOR_BELT_MK2.get(), 3, ring, 1, graphene, 1), out(UniverseItems.CONVEYOR_BELT_MK3.get(), 3));
        this.machine(Kind.ASSEMBLING, "sorter_mk2", 20, "improved_logistics", in(UniverseItems.SORTER.get(), 2, motor, 1),
                out(UniverseItems.SORTER_MK2.get(), 2));
        this.machine(Kind.ASSEMBLING, "sorter_mk3", 20, "efficient_logistics", in(UniverseItems.SORTER_MK2.get(), 2, turbine, 1),
                out(UniverseItems.SORTER_MK3.get(), 2));
        this.machine(Kind.ASSEMBLING, "splitter", 40, "improved_logistics", in(Items.IRON_INGOT, 3, gear, 2, board, 1),
                out(UniverseItems.SPLITTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "storage_tank", 40, "improved_logistics", in(Items.IRON_INGOT, 8, Items.STONE_BRICKS, 4, Items.GLASS, 4),
                out(UniverseItems.STORAGE_TANK.get(), 1));
        this.machine(Kind.ASSEMBLING, "assembler_mk2", 20, "improved_assembling", in(UniverseItems.ASSEMBLER.get(), 1, graphene, 8, processor, 4),
                out(UniverseItems.ASSEMBLER_MK2.get(), 1));
        this.machine(Kind.ASSEMBLING, "assembler_mk3", 20, "high_speed_assembling",
                in(UniverseItems.ASSEMBLER_MK2.get(), 1, broadband, 8, UniverseItems.QUANTUM_CHIP.get(), 2), out(UniverseItems.ASSEMBLER_MK3.get(), 1));
        // 戴森球计划还要 15 单极磁石，本模组没有，去掉
        this.machine(Kind.ASSEMBLING, "plane_smelter", 100, "plane_smelting",
                in(UniverseItems.ARC_SMELTER.get(), 1, UniverseItems.FRAME_MATERIAL.get(), 5, UniverseItems.PLANE_FILTER.get(), 4),
                out(UniverseItems.PLANE_SMELTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "chemical_plant", 100, "basic_chemical", in(steel, 8, Items.STONE_BRICKS, 20, Items.GLASS, 8, board, 2),
                out(UniverseItems.CHEMICAL_PLANT.get(), 1));
        this.machine(Kind.ASSEMBLING, "fractionator", 60, "deuterium_fractionation", in(steel, 8, Items.STONE_BRICKS, 4, Items.GLASS, 4, processor, 1),
                out(UniverseItems.FRACTIONATOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "solar_panel", 120, "solar_power", in(Items.COPPER_INGOT, 10, silicon, 10, board, 15),
                out(UniverseItems.SOLAR_PANEL.get(), 1));
        this.machine(Kind.ASSEMBLING, "advanced_mining_machine", 400, "advanced_mining",
                in(alloy, 20, UniverseItems.FRAME_MATERIAL.get(), 20, ring, 10, UniverseItems.QUANTUM_CHIP.get(), 10),
                out(UniverseItems.ADVANCED_MINING_MACHINE.get(), 1));
        this.machine(Kind.ASSEMBLING, "factory_anchor", 200, "interstellar_logistics", in(alloy, 8, processor, 8, ring, 4, container, 2),
                out(UniverseItems.FACTORY_ANCHOR.get(), 1));
        // 物流站：戴森球计划的原料比例缩到四分之一
        this.machine(Kind.ASSEMBLING, "planetary_logistics_station", 400, "planetary_logistics",
                in(steel, 10, titanium, 10, processor, 10, container, 5), out(UniverseItems.PLANETARY_LOGISTICS_STATION.get(), 1));
        this.machine(Kind.ASSEMBLING, "interstellar_logistics_station", 600, "interstellar_logistics",
                in(UniverseItems.PLANETARY_LOGISTICS_STATION.get(), 1, alloy, 10, container, 5),
                out(UniverseItems.INTERSTELLAR_LOGISTICS_STATION.get(), 1));
        this.machine(Kind.ASSEMBLING, "orbital_collector", 600, "orbital_collection",
                in(UniverseItems.INTERSTELLAR_LOGISTICS_STATION.get(), 1, ring, 12, reinforced, 5, UniverseItems.ACCUMULATOR.get(), 5),
                out(UniverseItems.ORBITAL_COLLECTOR.get(), 1));

        // ---- 手工合成
        this.researchShaped("magnetic_levitation", RecipeCategory.MISC, turbine, 1, List.of("MC", "CM"), Map.of('M', motor, 'C', coil));
        this.researchShaped("magnetic_levitation", RecipeCategory.MISC, ring, 1, List.of("NTN", "TGN"),
                Map.of('N', magnet, 'T', turbine, 'G', graphite));
        this.researchShaped("semiconductor", RecipeCategory.MISC, micro, 1, List.of("SCS"), Map.of('S', silicon, 'C', Items.COPPER_INGOT));
        this.researchShaped("processor", RecipeCategory.MISC, processor, 1, List.of("BM", "MB"), Map.of('B', board, 'M', micro));
        this.researchShaped("thruster", RecipeCategory.MISC, thruster, 1, List.of("SCS", "CC "), Map.of('S', steel, 'C', Items.COPPER_INGOT));
        this.researchShaped("planetary_logistics", RecipeCategory.MISC, container, 1, List.of("TCT", "GCG"),
                Map.of('T', turbine, 'C', Items.COPPER_INGOT, 'G', graphene));
        this.researchShaped("structure_matrix", RecipeCategory.MISC, titaniumCrystal, 1, List.of("TOT", " T "), Map.of('T', titanium, 'O', organic));
        this.researchShaped("planetary_logistics", RecipeCategory.MISC, UniverseItems.LOGISTICS_DRONE.get(), 1, List.of("IPI", "TIT", "IPI"),
                Map.of('I', Items.IRON_INGOT, 'P', processor, 'T', thruster));
        this.researchShaped("improved_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.CONVEYOR_BELT_MK2.get(), 3, List.of("BBB", " T "),
                Map.of('B', UniverseItems.CONVEYOR_BELT.get(), 'T', turbine));
        this.researchShaped("efficient_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.CONVEYOR_BELT_MK3.get(), 3, List.of("BBB", "RG "),
                Map.of('B', UniverseItems.CONVEYOR_BELT_MK2.get(), 'R', ring, 'G', graphene));
        this.researchShaped("improved_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.SORTER_MK2.get(), 2, List.of("SMS"),
                Map.of('S', UniverseItems.SORTER.get(), 'M', motor));
        this.researchShaped("efficient_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.SORTER_MK3.get(), 2, List.of("STS"),
                Map.of('S', UniverseItems.SORTER_MK2.get(), 'T', turbine));
        this.researchShaped("improved_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.SPLITTER.get(), 1, List.of("IGI", "GBI"),
                Map.of('I', Items.IRON_INGOT, 'G', gear, 'B', board));
        this.researchShaped("improved_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.STORAGE_TANK.get(), 1, List.of("IGI", "BIB", "IGI"),
                Map.of('I', Items.IRON_INGOT, 'G', Items.GLASS, 'B', Items.STONE_BRICKS));
        this.researchShaped("improved_assembling", RecipeCategory.REDSTONE, UniverseItems.ASSEMBLER_MK2.get(), 1, List.of("GPG", "PAP", "GPG"),
                Map.of('G', graphene, 'P', processor, 'A', UniverseItems.ASSEMBLER.get()));
        this.researchShaped("high_speed_assembling", RecipeCategory.REDSTONE, UniverseItems.ASSEMBLER_MK3.get(), 1, List.of("BQB", "BAB", "BQB"),
                Map.of('B', broadband, 'Q', UniverseItems.QUANTUM_CHIP.get(), 'A', UniverseItems.ASSEMBLER_MK2.get()));
        this.researchShaped("plane_smelting", RecipeCategory.REDSTONE, UniverseItems.PLANE_SMELTER.get(), 1, List.of("FPF", "FAF", "PFP"),
                Map.of('F', UniverseItems.FRAME_MATERIAL.get(), 'P', UniverseItems.PLANE_FILTER.get(), 'A', UniverseItems.ARC_SMELTER.get()));
        this.researchShaped("basic_chemical", RecipeCategory.REDSTONE, UniverseItems.CHEMICAL_PLANT.get(), 1, List.of("SGS", "KBK", "SGS"),
                Map.of('S', steel, 'G', Items.GLASS, 'K', Items.STONE_BRICKS, 'B', board));
        this.researchShaped("deuterium_fractionation", RecipeCategory.REDSTONE, UniverseItems.FRACTIONATOR.get(), 1, List.of("SGS", "KPK", "SGS"),
                Map.of('S', steel, 'G', Items.GLASS, 'K', Items.STONE_BRICKS, 'P', processor));
        this.researchShaped("solar_power", RecipeCategory.REDSTONE, UniverseItems.SOLAR_PANEL.get(), 1, List.of("SSS", "BCB", "CIC"),
                Map.of('S', silicon, 'B', board, 'C', Items.COPPER_INGOT, 'I', Items.IRON_INGOT));
        this.researchShaped("advanced_mining", RecipeCategory.REDSTONE, UniverseItems.ADVANCED_MINING_MACHINE.get(), 1, List.of("TFT", "RQR", "TFT"),
                Map.of('T', alloy, 'F', UniverseItems.FRAME_MATERIAL.get(), 'R', ring, 'Q', UniverseItems.QUANTUM_CHIP.get()));
        this.researchShaped("interstellar_logistics", RecipeCategory.REDSTONE, UniverseItems.FACTORY_ANCHOR.get(), 1, List.of("TPT", "RCR", "TPT"),
                Map.of('T', alloy, 'P', processor, 'R', ring, 'C', container));
        this.researchShaped("planetary_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.PLANETARY_LOGISTICS_STATION.get(), 1,
                List.of("SPS", "TCT", "SPS"), Map.of('S', steel, 'P', processor, 'T', titanium, 'C', container));
        this.researchShaped("interstellar_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.INTERSTELLAR_LOGISTICS_STATION.get(), 1,
                List.of("ACA", "CLC", "ACA"), Map.of('A', alloy, 'C', container, 'L', UniverseItems.PLANETARY_LOGISTICS_STATION.get()));
        this.researchShaped("orbital_collection", RecipeCategory.TRANSPORTATION, UniverseItems.ORBITAL_COLLECTOR.get(), 1,
                List.of("RTR", "AIA", "RTR"), Map.of('R', ring, 'T', reinforced, 'A', UniverseItems.ACCUMULATOR.get(),
                        'I', UniverseItems.INTERSTELLAR_LOGISTICS_STATION.get()));
        this.researchShaped("distribution_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.LOGISTICS_DISTRIBUTOR.get(), 1,
                List.of("IEI", "PIP", "IEI"), Map.of('I', Items.IRON_INGOT, 'E', UniverseItems.PLASMA_EXCITER.get(), 'P', processor));
        this.researchShaped("distribution_logistics", RecipeCategory.MISC, UniverseItems.LOGISTICS_BOT.get(), 1, List.of(" P ", "ITI"),
                Map.of('I', Items.IRON_INGOT, 'P', processor, 'T', thruster));
    }

    /**
     * 工业（ADR-0011）：中间产品一开始就能手工合成（戴森球计划的电磁阶段），设备要研究解锁；设备配方按戴森球计划的
     * 原料比例和时间来，单位时间是刻（戴森球计划的秒数 × 20）。
     */
    private void factory() {
        Item magnet = UniverseItems.ORE_SMELTED.get(OreKind.MAGNETIC).get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item gear = UniverseItems.GEAR.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item prism = UniverseItems.PRISM.get();
        Item graphite = UniverseItems.ENERGETIC_GRAPHITE.get();
        Item hydrogen = UniverseItems.HYDROGEN.get();
        Item silicon = UniverseItems.ORE_SMELTED.get(OreKind.SILICON).get();

        // ---- 手工合成：中间产品
        // 早期的磁铁：铁锭加红石；电弧熔炉直接把粗铁、粗磁铁烧成磁铁
        this.shapeless(RecipeCategory.MISC, magnet)
                .requires(Items.IRON_INGOT).requires(Items.REDSTONE)
                .unlockedBy(getHasName(Items.REDSTONE), this.has(Items.REDSTONE))
                .save(this.output, "universe:magnet_from_redstone");
        this.shapeless(RecipeCategory.MISC, gear)
                .requires(Items.IRON_INGOT)
                .unlockedBy(getHasName(Items.IRON_INGOT), this.has(Items.IRON_INGOT))
                .save(this.output);
        this.shaped(RecipeCategory.MISC, coil, 2)
                .pattern("M").pattern("C").pattern("M")
                .define('M', magnet).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(magnet), this.has(magnet))
                .save(this.output);
        this.shaped(RecipeCategory.MISC, board, 2)
                .pattern("ICI")
                .define('I', Items.IRON_INGOT).define('C', Items.COPPER_INGOT)
                .unlockedBy(getHasName(Items.COPPER_INGOT), this.has(Items.COPPER_INGOT))
                .save(this.output);
        // 要研究的中间产品
        this.researchShaped("electromagnetic_drive", RecipeCategory.MISC, UniverseItems.ELECTRIC_MOTOR.get(), 1,
                List.of("ICI", " G "), Map.of('I', Items.IRON_INGOT, 'C', coil, 'G', gear));
        this.researchShaped("plasma_refining", RecipeCategory.MISC, prism, 2,
                List.of("GG", "G "), Map.of('G', Items.GLASS));
        this.researchShaped("plasma_refining", RecipeCategory.MISC, UniverseItems.PLASMA_EXCITER.get(), 1,
                List.of("CPC", "CPC"), Map.of('C', coil, 'P', prism));

        // ---- 手工合成：物流
        this.researchShaped("basic_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.CONVEYOR_BELT.get(), 3,
                List.of("IGI"), Map.of('I', Items.IRON_INGOT, 'G', gear));
        // 跃迁引擎：钛合金的壳体、两组等离子激发器、钢框，中间一台蓄电器给场线圈供能
        this.researchShaped("warp_drive", RecipeCategory.TRANSPORTATION, UniverseItems.WARP_DRIVE.get(), 1,
                List.of("TPT", "SAS", "TPT"),
                Map.of('T', UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 'P', UniverseItems.PLASMA_EXCITER.get(), 'S', UniverseItems.STEEL.get(),
                        'A', UniverseItems.ACCUMULATOR.get()));
        this.researchShaped("electromagnetic_matrix", RecipeCategory.REDSTONE, UniverseItems.MATRIX_LAB.get(), 1,
                List.of("IGI", "CMC", "IGI"), Map.of('I', Items.IRON_INGOT, 'G', Items.GLASS, 'C', board, 'M', coil));
        this.researchShaped("basic_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.SORTER.get(), 1,
                List.of("I", "B"), Map.of('I', Items.IRON_INGOT, 'B', board));
        this.researchShaped("basic_logistics", RecipeCategory.TRANSPORTATION, UniverseItems.STORAGE_DEPOT.get(), 1,
                List.of("ISI", "S S", "ISI"), Map.of('I', Items.IRON_INGOT, 'S', Items.STONE));

        // ---- 手工合成：设备
        this.researchShaped("electromagnetism", RecipeCategory.REDSTONE, UniverseItems.TESLA_TOWER.get(), 1,
                List.of("C", "I", "I"), Map.of('C', coil, 'I', Items.IRON_INGOT));
        this.researchShaped("electromagnetism", RecipeCategory.REDSTONE, UniverseItems.WIND_TURBINE.get(), 1,
                List.of("IGI", "CIC", "IGI"), Map.of('I', Items.IRON_INGOT, 'G', gear, 'C', coil));
        this.researchShaped("automatic_metallurgy", RecipeCategory.REDSTONE, UniverseItems.ARC_SMELTER.get(), 1,
                List.of("IMI", "CFC", "BBB"),
                Map.of('I', Items.IRON_INGOT, 'M', coil, 'C', board, 'F', Items.BLAST_FURNACE, 'B', Items.STONE_BRICKS));
        this.researchShaped("basic_assembling", RecipeCategory.REDSTONE, UniverseItems.ASSEMBLER.get(), 1,
                List.of("IGI", "CTC", "IGI"), Map.of('I', Items.IRON_INGOT, 'G', gear, 'C', board, 'T', Items.CRAFTING_TABLE));
        this.researchShaped("thermal_power", RecipeCategory.REDSTONE, UniverseItems.THERMAL_PLANT.get(), 1,
                List.of("BCB", "GFG", "BIB"),
                Map.of('B', Items.STONE_BRICKS, 'C', coil, 'G', gear, 'F', Items.FURNACE, 'I', Items.IRON_INGOT));
        this.researchShaped("electromagnetism", RecipeCategory.REDSTONE, UniverseItems.MINING_MACHINE.get(), 1,
                List.of("IGI", "BPB", "IMI"),
                Map.of('I', Items.IRON_INGOT, 'G', gear, 'B', board, 'P', Items.IRON_PICKAXE, 'M', coil));
        this.researchShaped("fluid_storage", RecipeCategory.REDSTONE, UniverseItems.WATER_PUMP.get(), 1,
                List.of("IMI", "BUB", "IMI"),
                Map.of('I', Items.IRON_INGOT, 'M', UniverseItems.ELECTRIC_MOTOR.get(), 'B', board, 'U', Items.BUCKET));
        this.researchShaped("plasma_refining", RecipeCategory.REDSTONE, UniverseItems.OIL_EXTRACTOR.get(), 1,
                List.of("SES", "BPB", "KKK"),
                Map.of('S', UniverseItems.STEEL.get(), 'E', UniverseItems.PLASMA_EXCITER.get(), 'B', board, 'P', Items.PISTON,
                        'K', Items.STONE_BRICKS));
        this.researchShaped("plasma_refining", RecipeCategory.REDSTONE, UniverseItems.OIL_REFINERY.get(), 1,
                List.of("SES", "BFB", "KKK"),
                Map.of('S', UniverseItems.STEEL.get(), 'E', UniverseItems.PLASMA_EXCITER.get(), 'B', board, 'F', Items.BLAST_FURNACE,
                        'K', Items.STONE_BRICKS));
        this.researchShaped("energy_storage", RecipeCategory.REDSTONE, UniverseItems.ACCUMULATOR.get(), 1,
                List.of("IRI", "CSC", "IRI"),
                Map.of('I', Items.IRON_INGOT, 'R', Items.REDSTONE, 'C', coil, 'S', silicon));

        // ---- 电弧熔炉
        Ingredient cobblestone = Ingredient.of(Items.COBBLESTONE, UniverseBlocks.PLANET_COBBLESTONE.get());
        this.machine(Kind.SMELTING, "iron_ingot", 20, "", in(Items.RAW_IRON, 1), out(Items.IRON_INGOT, 1));
        this.machine(Kind.SMELTING, "copper_ingot", 20, "", in(Items.RAW_COPPER, 1), out(Items.COPPER_INGOT, 1));
        this.machine(Kind.SMELTING, "gold_ingot", 20, "", in(Items.RAW_GOLD, 1), out(Items.GOLD_INGOT, 1));
        this.machine(Kind.SMELTING, "magnet", 30, "", in(Items.RAW_IRON, 1), out(magnet, 1));
        this.machine(Kind.SMELTING, "magnet_from_magnetite", 30, "",
                in(UniverseItems.oreDrop(OreKind.MAGNETIC), 1), out(magnet, 2));
        this.machine(Kind.SMELTING, "stone_bricks", 20, "", List.of(new SizedIngredient(cobblestone, 1)), out(Items.STONE_BRICKS, 1));
        this.machine(Kind.SMELTING, "glass", 40, "", List.of(new SizedIngredient(Ingredient.of(Items.SAND, Items.RED_SAND), 1)),
                out(Items.GLASS, 1));
        this.machine(Kind.SMELTING, "energetic_graphite", 40, "", in(Items.COAL, 2), out(graphite, 1));
        this.machine(Kind.SMELTING, "silicon", 40, "", in(UniverseItems.oreDrop(OreKind.SILICON), 1), out(silicon, 1));
        this.machine(Kind.SMELTING, "titanium_ingot", 40, "", in(UniverseItems.oreDrop(OreKind.TITANIUM), 1),
                out(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 1));
        this.machine(Kind.SMELTING, "uranium_ingot", 40, "", in(UniverseItems.oreDrop(OreKind.URANIUM), 1),
                out(UniverseItems.ORE_SMELTED.get(OreKind.URANIUM).get(), 1));
        this.machine(Kind.SMELTING, "steel", 60, "steel_smelting", in(Items.IRON_INGOT, 3), out(UniverseItems.STEEL.get(), 1));

        // ---- 制造台
        this.machine(Kind.ASSEMBLING, "gear", 20, "", in(Items.IRON_INGOT, 1), out(gear, 1));
        this.machine(Kind.ASSEMBLING, "magnetic_coil", 20, "", in(magnet, 2, Items.COPPER_INGOT, 1), out(coil, 2));
        this.machine(Kind.ASSEMBLING, "circuit_board", 20, "", in(Items.IRON_INGOT, 2, Items.COPPER_INGOT, 1), out(board, 2));
        this.machine(Kind.ASSEMBLING, "electric_motor", 40, "electromagnetic_drive",
                in(Items.IRON_INGOT, 2, gear, 1, coil, 1), out(UniverseItems.ELECTRIC_MOTOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "prism", 40, "plasma_refining", in(Items.GLASS, 3), out(prism, 2));
        this.machine(Kind.ASSEMBLING, "plasma_exciter", 40, "plasma_refining", in(coil, 4, prism, 2),
                out(UniverseItems.PLASMA_EXCITER.get(), 1));
        this.machine(Kind.ASSEMBLING, "electromagnetic_matrix", 60, "electromagnetic_matrix", in(coil, 1, board, 1),
                out(UniverseItems.ELECTROMAGNETIC_MATRIX.get(), 1));
        this.machine(Kind.ASSEMBLING, "energy_matrix", 120, "energy_matrix", in(graphite, 2, hydrogen, 2),
                out(UniverseItems.ENERGY_MATRIX.get(), 1));
        // 设备也能在制造台里做，方便自动化
        this.machine(Kind.ASSEMBLING, "conveyor_belt", 20, "basic_logistics", in(Items.IRON_INGOT, 2, gear, 1),
                out(UniverseItems.CONVEYOR_BELT.get(), 3));
        // 空间翘曲器：一份能量矩阵把钛和棱镜做成能撕开空间的透镜（戴森球计划里是引力透镜，那要到后面的矩阵）
        Item titanium = UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get();
        this.machine(Kind.ASSEMBLING, "space_warper", 200, "warp_drive",
                in(titanium, 2, prism, 1, UniverseItems.ENERGY_MATRIX.get(), 1), out(UniverseItems.SPACE_WARPER.get(), 1));
        this.machine(Kind.ASSEMBLING, "space_warper_calibrated", 200, "warp_calibration",
                in(titanium, 2, prism, 1, UniverseItems.ENERGY_MATRIX.get(), 1), out(UniverseItems.SPACE_WARPER.get(), 2));
        this.machine(Kind.ASSEMBLING, "warp_drive", 120, "warp_drive",
                in(titanium, 4, UniverseItems.PLASMA_EXCITER.get(), 2, UniverseItems.STEEL.get(), 2, UniverseItems.ACCUMULATOR.get(), 1),
                out(UniverseItems.WARP_DRIVE.get(), 1));
        // 飞船武器和护盾（ADR-0017）也能在制造台里做
        this.machine(Kind.ASSEMBLING, "photon_cannon", 160, "ship_weapons",
                in(UniverseItems.STEEL.get(), 4, UniverseItems.PLASMA_EXCITER.get(), 1, UniverseItems.TITANIUM_ALLOY.get(), 2,
                        UniverseItems.PROCESSOR.get(), 1),
                out(UniverseItems.PHOTON_CANNON.get(), 1));
        this.machine(Kind.ASSEMBLING, "mining_beam", 160, "ship_weapons",
                in(UniverseItems.STEEL.get(), 4, UniverseItems.SYNTHETIC_DIAMOND.get(), 1, UniverseItems.TITANIUM_ALLOY.get(), 2,
                        UniverseItems.PLASMA_EXCITER.get(), 1),
                out(UniverseItems.MINING_BEAM.get(), 1));
        // 探索车（ADR-0017）：钢的车架、四台电动机、一块蓄电器，等离子激发器做采矿激光
        this.machine(Kind.ASSEMBLING, "exocraft", 240, "exocraft",
                in(UniverseItems.STEEL.get(), 6, UniverseItems.ELECTRIC_MOTOR.get(), 4, UniverseItems.ACCUMULATOR.get(), 1,
                        UniverseItems.PLASMA_EXCITER.get(), 1),
                out(UniverseItems.EXOCRAFT.get(), 1));
        // 潜艇（ADR-0026）：钛的耐压壳、一个玻璃观察罩、一块蓄电器、一台电动机带螺旋桨
        this.machine(Kind.ASSEMBLING, "submarine", 240, "submarine",
                in(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 4, Items.GLASS, 1, UniverseItems.ACCUMULATOR.get(), 1,
                        UniverseItems.ELECTRIC_MOTOR.get(), 1),
                out(UniverseItems.SUBMARINE.get(), 1));
        // 悬浮车、重型车、机甲、载具召唤台（ADR-0026）：悬浮车靠超级磁场环托着；重型车车尾背一台便携精炼机；
        // 机甲的腿用四足机的伺服器，步行机主脑管平衡；召唤台是一块处理器加等离子激发器做的定位信标
        this.machine(Kind.ASSEMBLING, "hover_car", 240, "all_terrain_vehicles",
                in(UniverseItems.TITANIUM_ALLOY.get(), 4, UniverseItems.SUPER_MAGNETIC_RING.get(), 2, UniverseItems.ACCUMULATOR.get(), 1,
                        UniverseItems.PLASMA_EXCITER.get(), 1),
                out(UniverseItems.HOVER_CAR.get(), 1));
        this.machine(Kind.ASSEMBLING, "heavy_rover", 360, "all_terrain_vehicles",
                in(UniverseItems.STEEL.get(), 10, UniverseItems.ELECTRIC_MOTOR.get(), 8, UniverseItems.ACCUMULATOR.get(), 2,
                        UniverseItems.PORTABLE_REFINER.get(), 1),
                out(UniverseItems.HEAVY_ROVER.get(), 1));
        this.machine(Kind.ASSEMBLING, "exo_mech", 360, "exo_mech",
                in(UniverseItems.TITANIUM_ALLOY.get(), 8, UniverseItems.QUADRUPED_SERVO.get(), 2, UniverseItems.WALKER_BRAIN.get(), 1,
                        UniverseItems.ACCUMULATOR.get(), 2),
                out(UniverseItems.EXO_MECH.get(), 1));
        this.machine(Kind.ASSEMBLING, "vehicle_summoner", 160, "all_terrain_vehicles",
                in(UniverseItems.STEEL.get(), 4, UniverseItems.PROCESSOR.get(), 1, UniverseItems.PLASMA_EXCITER.get(), 1,
                        UniverseItems.CIRCUIT_BOARD.get(), 2),
                out(UniverseItems.VEHICLE_SUMMONER.get(), 1));
        // 传送器（ADR-0017）：钛的机壳、等离子激发器，核心是一个空间翘曲器
        this.machine(Kind.ASSEMBLING, "teleporter", 240, "teleport_network",
                in(UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 4, UniverseItems.PLASMA_EXCITER.get(), 1, UniverseItems.STEEL.get(), 2,
                        UniverseItems.SPACE_WARPER.get(), 1),
                out(UniverseItems.TELEPORTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "shield_generator", 240, "ship_shields",
                in(UniverseItems.TITANIUM_ALLOY.get(), 4, UniverseItems.SUPER_MAGNETIC_RING.get(), 2, UniverseItems.PARTICLE_BROADBAND.get(), 2,
                        UniverseItems.ACCUMULATOR.get(), 1),
                out(UniverseItems.SHIELD_GENERATOR.get(), 1));
        // 技术模块（ADR-0014）：都要守卫机残骸和勘探数据，再加各自的材料；做出来是 C 级，第一次进背包时定下数值
        Item survey = UniverseItems.SURVEY_DATA.get();
        Item scrap = UniverseItems.SENTINEL_SCRAP.get();
        Map<ModuleType, List<SizedIngredient>> modules = new LinkedHashMap<>();
        modules.put(ModuleType.LIFE_SUPPORT, in(scrap, 2, survey, 2, UniverseItems.OXYGEN_CAPSULE.get(), 2, board, 1));
        modules.put(ModuleType.COLD_LINING, in(scrap, 2, survey, 2, Items.WOOL.white(), 2, UniverseItems.HAZARD_CELL.get(), 1));
        modules.put(ModuleType.HEAT_LINING, in(scrap, 2, survey, 2, Items.MAGMA_CREAM, 1, UniverseItems.HAZARD_CELL.get(), 1));
        modules.put(ModuleType.TOXIN_FILTER, in(scrap, 2, survey, 2, Items.CHARCOAL, 2, UniverseItems.HAZARD_CELL.get(), 1));
        modules.put(ModuleType.RADIATION_SHIELD, in(scrap, 2, survey, 2, UniverseItems.STEEL.get(), 1, UniverseItems.HAZARD_CELL.get(), 1));
        modules.put(ModuleType.JET_BOOSTER, in(scrap, 2, survey, 2, UniverseItems.LAUNCH_FUEL.get(), 2, gear, 2));
        modules.put(ModuleType.SERVO, in(scrap, 2, survey, 2, UniverseItems.ELECTRIC_MOTOR.get(), 1, gear, 1));
        modules.put(ModuleType.SHIELD, in(scrap, 3, survey, 3, coil, 2, prism, 1));
        modules.put(ModuleType.BEAM_FOCUS, in(scrap, 2, survey, 2, Items.GLASS, 2, coil, 1));
        modules.put(ModuleType.HEAT_SINK, in(scrap, 2, survey, 2, Items.COPPER_INGOT, 3, board, 1));
        modules.put(ModuleType.SCANNER_ARRAY, in(scrap, 2, survey, 2, board, 2, coil, 1));
        modules.put(ModuleType.ANALYZER, in(scrap, 2, survey, 3, Items.SPYGLASS, 1, board, 2));
        modules.put(ModuleType.WEAPON_CALIBRATOR, in(scrap, 3, survey, 2, gear, 2, coil, 1));
        // 飞船技术（ADR-0024）
        modules.put(ModuleType.THRUST_EFFICIENCY, in(scrap, 2, survey, 2, UniverseItems.SHIP_THRUSTER.get(), 1, UniverseItems.LAUNCH_FUEL.get(), 2));
        modules.put(ModuleType.PULSE_TUNING, in(scrap, 2, survey, 2, coil, 2, UniverseItems.LAUNCH_FUEL.get(), 1));
        modules.put(ModuleType.HYPERDRIVE_BOOSTER, in(scrap, 3, survey, 3, UniverseItems.NAVIGATION_DATA.get(), 2, prism, 1));
        modules.put(ModuleType.DEFLECTOR, in(scrap, 3, survey, 2, coil, 2, prism, 1));
        modules.put(ModuleType.HULL_REINFORCEMENT, in(scrap, 2, survey, 2, UniverseItems.STEEL.get(), 3, gear, 1));
        modules.put(ModuleType.FIRE_CONTROL, in(scrap, 3, survey, 2, board, 2, prism, 1));
        modules.forEach((type, ingredients) -> this.machine(Kind.ASSEMBLING, type.itemName(), 100, "tech_modules", ingredients,
                out(UniverseItems.MODULES.get(type).get(), 1)));
        // ---- M14：多功能工具的武器（ADR-0026 §六），和模块一样在制造台里做，都要守卫机残骸
        Item steel = UniverseItems.STEEL.get();
        this.machine(Kind.ASSEMBLING, ToolWeapon.SCATTER_BLASTER.id(), 200, "plasma_weapons",
                in(scrap, 4, coil, 4, board, 2, Items.COPPER_INGOT, 4), out(UniverseItems.WEAPONS.get(ToolWeapon.SCATTER_BLASTER).get(), 1));
        this.machine(Kind.ASSEMBLING, ToolWeapon.PULSE_SPITTER.id(), 200, "plasma_weapons",
                in(scrap, 4, coil, 6, board, 2, gear, 4), out(UniverseItems.WEAPONS.get(ToolWeapon.PULSE_SPITTER).get(), 1));
        this.machine(Kind.ASSEMBLING, ToolWeapon.BLAZE_JAVELIN.id(), 300, "heavy_plasma_weapons",
                in(scrap, 6, prism, 2, coil, 4, board, 4), out(UniverseItems.WEAPONS.get(ToolWeapon.BLAZE_JAVELIN).get(), 1));
        this.machine(Kind.ASSEMBLING, ToolWeapon.PLASMA_LAUNCHER.id(), 300, "heavy_plasma_weapons",
                in(scrap, 6, steel, 4, coil, 4, board, 4), out(UniverseItems.WEAPONS.get(ToolWeapon.PLASMA_LAUNCHER).get(), 1));
        // ---- M13：着陆台和三档驱动（ADR-0024）。驱动一档套一档：做高一档的要用掉低一档的
        this.researchShaped("landing_pad", RecipeCategory.TRANSPORTATION, UniverseItems.LANDING_PAD.get(), 1,
                List.of("SYS", "CBC", "III"), Map.of('S', Items.SMOOTH_STONE_SLAB, 'Y', Items.DYE.yellow(), 'C', coil, 'B', board,
                        'I', Items.IRON_INGOT));
        // ---- M14：水培槽（ADR-0026）：玻璃罩、补光灯、一桶营养液
        this.researchShaped("hydroponics", RecipeCategory.MISC, UniverseItems.HYDROPONIC_TRAY.get(), 1,
                List.of("GLG", "IWI", "IBI"), Map.of('G', Items.GLASS_PANE, 'L', Items.GLOWSTONE_DUST, 'I', Items.IRON_INGOT,
                        'W', Items.WATER_BUCKET, 'B', board));
        // 卵序列仪（ADR-0026 §九）：玻璃罩下面两个加热线圈和一块电路板
        this.researchShaped("gene_editing", RecipeCategory.MISC, UniverseItems.EGG_SEQUENCER.get(), 1,
                List.of("GGG", "CBC", "III"), Map.of('G', Items.GLASS_PANE, 'C', coil, 'B', board, 'I', Items.IRON_INGOT));
        this.researchShaped("cadmium_drive", RecipeCategory.TRANSPORTATION, UniverseItems.DRIVES.get(DriveTier.CADMIUM).get(), 1,
                List.of("TST", "PWP", "TST"), Map.of('T', UniverseItems.TITANIUM_ALLOY.get(), 'S', UniverseItems.SUPER_MAGNETIC_RING.get(),
                        'P', UniverseItems.PROCESSOR.get(), 'W', UniverseItems.WARP_DRIVE.get()));
        this.researchShaped("emeril_drive", RecipeCategory.TRANSPORTATION, UniverseItems.DRIVES.get(DriveTier.EMERIL).get(), 1,
                List.of("GCG", "PDP", "GCG"), Map.of('G', UniverseItems.TITANIUM_GLASS.get(), 'C', UniverseItems.CASIMIR_CRYSTAL.get(),
                        'P', UniverseItems.PARTICLE_BROADBAND.get(), 'D', UniverseItems.DRIVES.get(DriveTier.CADMIUM).get()));
        this.researchShaped("indium_drive", RecipeCategory.TRANSPORTATION, UniverseItems.DRIVES.get(DriveTier.INDIUM).get(), 1,
                List.of("LQL", "FDF", "LQL"), Map.of('L', UniverseItems.GRAVITON_LENS.get(), 'Q', UniverseItems.QUANTUM_CHIP.get(),
                        'F', UniverseItems.FRAME_MATERIAL.get(), 'D', UniverseItems.DRIVES.get(DriveTier.EMERIL).get()));
        this.machine(Kind.ASSEMBLING, "matrix_lab", 60, "electromagnetic_matrix",
                in(Items.IRON_INGOT, 4, Items.GLASS, 2, board, 2, coil, 1), out(UniverseItems.MATRIX_LAB.get(), 1));
        this.machine(Kind.ASSEMBLING, "sorter", 20, "basic_logistics", in(Items.IRON_INGOT, 1, board, 1),
                out(UniverseItems.SORTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "storage_depot", 40, "basic_logistics", in(Items.IRON_INGOT, 4, Items.STONE, 4),
                out(UniverseItems.STORAGE_DEPOT.get(), 1));
        this.machine(Kind.ASSEMBLING, "tesla_tower", 20, "electromagnetism", in(Items.IRON_INGOT, 2, coil, 1),
                out(UniverseItems.TESLA_TOWER.get(), 1));
        this.machine(Kind.ASSEMBLING, "wind_turbine", 80, "electromagnetism", in(Items.IRON_INGOT, 6, gear, 1, coil, 3),
                out(UniverseItems.WIND_TURBINE.get(), 1));
        this.machine(Kind.ASSEMBLING, "arc_smelter", 60, "automatic_metallurgy",
                in(Items.IRON_INGOT, 4, Items.STONE_BRICKS, 2, board, 4, coil, 2), out(UniverseItems.ARC_SMELTER.get(), 1));
        this.machine(Kind.ASSEMBLING, "assembler", 60, "basic_assembling", in(Items.IRON_INGOT, 4, gear, 8, board, 4),
                out(UniverseItems.ASSEMBLER.get(), 1));
        this.machine(Kind.ASSEMBLING, "thermal_plant", 100, "thermal_power",
                in(Items.IRON_INGOT, 10, Items.STONE_BRICKS, 4, gear, 4, coil, 4), out(UniverseItems.THERMAL_PLANT.get(), 1));
        this.machine(Kind.ASSEMBLING, "accumulator", 100, "energy_storage", in(Items.IRON_INGOT, 6, coil, 4, silicon, 2),
                out(UniverseItems.ACCUMULATOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "mining_machine", 60, "electromagnetism", in(Items.IRON_INGOT, 4, board, 2, coil, 2, gear, 2),
                out(UniverseItems.MINING_MACHINE.get(), 1));
        this.machine(Kind.ASSEMBLING, "water_pump", 80, "fluid_storage",
                in(Items.IRON_INGOT, 3, Items.STONE_BRICKS, 2, UniverseItems.ELECTRIC_MOTOR.get(), 4, board, 2),
                out(UniverseItems.WATER_PUMP.get(), 1));
        this.machine(Kind.ASSEMBLING, "oil_extractor", 160, "plasma_refining",
                in(UniverseItems.STEEL.get(), 12, Items.STONE_BRICKS, 12, board, 6, UniverseItems.PLASMA_EXCITER.get(), 4),
                out(UniverseItems.OIL_EXTRACTOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "oil_refinery", 120, "plasma_refining",
                in(UniverseItems.STEEL.get(), 10, Items.STONE_BRICKS, 10, board, 6, UniverseItems.PLASMA_EXCITER.get(), 6),
                out(UniverseItems.OIL_REFINERY.get(), 1));

        // ---- 原油精炼厂
        Item crude = UniverseItems.CRUDE_OIL.get();
        Item refined = UniverseItems.REFINED_OIL.get();
        this.machine(Kind.REFINING, "plasma_refining", 80, "plasma_refining", in(crude, 2),
                List.of(new ItemStackTemplate(refined, 2), new ItemStackTemplate(hydrogen, 1)));
        this.machine(Kind.REFINING, "x_ray_cracking", 80, "x_ray_cracking", in(refined, 1, hydrogen, 2),
                List.of(new ItemStackTemplate(hydrogen, 3), new ItemStackTemplate(graphite, 1)));
    }

    /** 要研究解锁的有序合成，配方 ID 和原版一样用产物的名字。 */
    private void researchShaped(String research, RecipeCategory category, ItemLike result, int count, List<String> pattern,
            Map<Character, ? extends ItemLike> key) {
        // 按字母排，生成的文件每次一样
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(category, null), ShapedRecipePattern.of(ingredients, pattern),
                new ItemStackTemplate(result.asItem(), count), research);
        this.output.accept(RecipeBuilder.getDefaultRecipeId(recipe.result()), recipe, null);
    }

    /** 设备配方，ID 是 {@code universe:<类型>/<名字>}。 */
    private void machine(Kind kind, String name, int time, String research, List<SizedIngredient> ingredients,
            List<ItemStackTemplate> results) {
        String type = kind.name().toLowerCase(Locale.ROOT);
        this.output.accept(ResourceKey.create(Registries.RECIPE, Universe.id(type + "/" + name)),
                new MachineRecipe(kind, ingredients, results, time, research), null);
    }

    /** 原料：物品、数量交替排列。 */
    private static List<SizedIngredient> in(Object... itemsAndCounts) {
        List<SizedIngredient> list = new ArrayList<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            list.add(SizedIngredient.of((ItemLike) itemsAndCounts[i], (Integer) itemsAndCounts[i + 1]));
        }
        return list;
    }

    private static List<ItemStackTemplate> out(ItemLike item, int count) {
        return List.of(new ItemStackTemplate(item.asItem(), count));
    }

    /** 多功能工具：修好坠毁点找到的那把，或者用钻石从头造一把（多人游戏里别的玩家用）。 */
    private void multitool() {
        // 修好的和新做的多功能工具都是 C 档手枪（ADR-0026）；没有类型的旧工具才按 A 档算
        ItemStackTemplate starter = new ItemStackTemplate(UniverseItems.MULTITOOL.get(),
                DataComponentPatch.builder().set(UniverseDataComponents.TOOL_SPEC.get(), MultitoolSpec.STARTER).build());
        this.shapeless(RecipeCategory.TOOLS, starter)
                .requires(UniverseItems.DAMAGED_MULTITOOL.get())
                .requires(Items.COPPER_INGOT).requires(Items.COPPER_INGOT)
                .requires(Items.REDSTONE).requires(Items.REDSTONE).requires(Items.IRON_INGOT)
                .unlockedBy(getHasName(UniverseItems.DAMAGED_MULTITOOL.get()), this.has(UniverseItems.DAMAGED_MULTITOOL.get()))
                .save(this.output, "universe:multitool_from_repair");
        // 手册：坠毁点的箱子里有一本，多人游戏里别的玩家自己做
        this.shapeless(RecipeCategory.MISC, UniverseItems.GUIDE.get())
                .requires(Items.BOOK).requires(UniverseItems.CIRCUIT_BOARD.get())
                .unlockedBy(getHasName(UniverseItems.CIRCUIT_BOARD.get()), this.has(UniverseItems.CIRCUIT_BOARD.get()))
                .save(this.output);
        this.shaped(RecipeCategory.TOOLS, starter)
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
    /** 信号增强器和导航数据（ADR-0014）：电路板做出来就能做，早期探索用得上。 */
    private void pois() {
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        this.shaped(RecipeCategory.TOOLS, UniverseItems.SIGNAL_BOOSTER.get())
                .pattern(" R ").pattern("CBC").pattern("III")
                .define('R', Items.LIGHTNING_ROD.weathering().unaffected()).define('C', Items.COPPER_INGOT).define('B', board).define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(board), this.has(board))
                .save(this.output);
        // 基地信标（ADR-0017）：和信号增强器一样，有电路板就能做
        this.shaped(RecipeCategory.TOOLS, UniverseItems.BASE_BEACON.get())
                .pattern(" R ").pattern("CBC").pattern("III")
                .define('R', Items.REDSTONE_TORCH).define('C', Items.COPPER_INGOT).define('B', board).define('I', Items.IRON_INGOT)
                .unlockedBy(getHasName(board), this.has(board))
                .save(this.output);
        this.shapeless(RecipeCategory.MISC, UniverseItems.NAVIGATION_DATA.get(), 2)
                .requires(board).requires(Items.PAPER, 2).requires(Items.REDSTONE)
                .unlockedBy(getHasName(board), this.has(board))
                .save(this.output);
    }

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

        // 飞船武器和护盾（ADR-0017）：光子炮是钢壳里两组等离子激发器，处理器管瞄准；采矿光束把激发器换成合成钻石的聚焦镜；
        // 护盾发生器用超级磁场环绕着蓄电器，粒子宽带控制场强
        Item steel = UniverseItems.STEEL.get();
        Item exciter = UniverseItems.PLASMA_EXCITER.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        this.researchShaped("ship_weapons", RecipeCategory.COMBAT, UniverseItems.PHOTON_CANNON.get(), 1,
                List.of("SPS", "TCT", "S S"), Map.of('S', steel, 'P', exciter, 'T', alloy, 'C', UniverseItems.PROCESSOR.get()));
        this.researchShaped("ship_weapons", RecipeCategory.TOOLS, UniverseItems.MINING_BEAM.get(), 1,
                List.of("SDS", "TPT", "S S"), Map.of('S', steel, 'D', UniverseItems.SYNTHETIC_DIAMOND.get(), 'T', alloy, 'P', exciter));
        this.researchShaped("exocraft", RecipeCategory.TRANSPORTATION, UniverseItems.EXOCRAFT.get(), 1,
                List.of("SXS", "CAC", "MSM"), Map.of('S', steel, 'X', exciter, 'C', UniverseItems.CIRCUIT_BOARD.get(),
                        'A', UniverseItems.ACCUMULATOR.get(), 'M', UniverseItems.ELECTRIC_MOTOR.get()));
        // 潜艇的配方 ID 是 universe:submarine（手册里引用）
        this.researchShaped("submarine", RecipeCategory.TRANSPORTATION, UniverseItems.SUBMARINE.get(), 1,
                List.of("TGT", "CAC", "TMT"), Map.of('T', UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 'G', Items.GLASS,
                        'C', UniverseItems.CIRCUIT_BOARD.get(), 'A', UniverseItems.ACCUMULATOR.get(), 'M', UniverseItems.ELECTRIC_MOTOR.get()));
        // 悬浮车、重型车、机甲、载具召唤台（ADR-0026）：配方 ID 就是物品名（召唤台的手册里引用）
        this.researchShaped("all_terrain_vehicles", RecipeCategory.TRANSPORTATION, UniverseItems.HOVER_CAR.get(), 1,
                List.of("TXT", "RAR", "T T"), Map.of('T', alloy, 'X', exciter, 'R', UniverseItems.SUPER_MAGNETIC_RING.get(),
                        'A', UniverseItems.ACCUMULATOR.get()));
        this.researchShaped("all_terrain_vehicles", RecipeCategory.TRANSPORTATION, UniverseItems.HEAVY_ROVER.get(), 1,
                List.of("SXS", "MRM", "MAM"), Map.of('S', steel, 'X', exciter, 'M', UniverseItems.ELECTRIC_MOTOR.get(),
                        'R', UniverseItems.PORTABLE_REFINER.get(), 'A', UniverseItems.ACCUMULATOR.get()));
        this.researchShaped("exo_mech", RecipeCategory.TRANSPORTATION, UniverseItems.EXO_MECH.get(), 1,
                List.of("TXT", "QBQ", "TAT"), Map.of('T', alloy, 'X', exciter, 'Q', UniverseItems.QUADRUPED_SERVO.get(),
                        'B', UniverseItems.WALKER_BRAIN.get(), 'A', UniverseItems.ACCUMULATOR.get()));
        this.researchShaped("all_terrain_vehicles", RecipeCategory.TRANSPORTATION, UniverseItems.VEHICLE_SUMMONER.get(), 1,
                List.of("SPS", "CXC", "SSS"), Map.of('S', steel, 'P', UniverseItems.PROCESSOR.get(), 'C', UniverseItems.CIRCUIT_BOARD.get(),
                        'X', exciter));
        // 蓝图（ADR-0018）：四张纸围着一块电路板，一次出两张
        this.researchShaped("blueprint", RecipeCategory.TOOLS, UniverseItems.BLUEPRINT.get(), 2, List.of(" P ", "PCP", " P "),
                Map.of('P', Items.PAPER, 'C', UniverseItems.CIRCUIT_BOARD.get()));
        this.researchShaped("teleport_network", RecipeCategory.TRANSPORTATION, UniverseItems.TELEPORTER.get(), 1,
                List.of("TPT", "SWS", "TCT"), Map.of('T', UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get(), 'P', exciter, 'S', steel,
                        'W', UniverseItems.SPACE_WARPER.get(), 'C', UniverseItems.CIRCUIT_BOARD.get()));
        this.researchShaped("ship_shields", RecipeCategory.COMBAT, UniverseItems.SHIELD_GENERATOR.get(), 1,
                List.of("TMT", "BAB", "TMT"), Map.of('T', alloy, 'M', UniverseItems.SUPER_MAGNETIC_RING.get(),
                        'B', UniverseItems.PARTICLE_BROADBAND.get(), 'A', UniverseItems.ACCUMULATOR.get()));

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

    /**
     * 喂养（ADR-0017）：星球植物压成草食饲料，生肉拌骨粉做肉食饲料；异星蛋烤着吃，绒毛搓线、压羊毛，生物胶质当黏液球用。
     * 生物胶质在化工厂里做有机晶体的配方放在化工那一段。
     */
    /** 黑雾基地的方块（子代理 P）。 */
    private void darkFogBases() {
    }

    /** 战场分析基地（子代理 Q）。 */
    private void darkFogFacilities() {
        // 战场分析基地（ADR-0028 §五）：钢和钛合金做外壳，处理器管修理和无人机，电磁涡轮驱动
        this.shaped(RecipeCategory.MISC, UniverseItems.BATTLEFIELD_ANALYSIS_BASE.get())
                .pattern("SPS").pattern("TFT").pattern("SCS")
                .define('S', UniverseItems.STEEL.get())
                .define('P', UniverseItems.PROCESSOR.get())
                .define('T', UniverseItems.ELECTROMAGNETIC_TURBINE.get())
                .define('F', UniverseItems.TITANIUM_ALLOY.get())
                .define('C', UniverseItems.CIRCUIT_BOARD.get())
                .unlockedBy(getHasName(UniverseItems.PROCESSOR.get()), this.has(UniverseItems.PROCESSOR.get()))
                .save(this.output);
    }

    /** 炮塔和弹药（子代理 R1）。 */
    private void darkFogTurrets() {
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item gear = UniverseItems.GEAR.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item steel = UniverseItems.STEEL.get();
        Item motor = UniverseItems.ELECTRIC_MOTOR.get();
        Item exciter = UniverseItems.PLASMA_EXCITER.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item titanium = UniverseItems.ORE_SMELTED.get(OreKind.TITANIUM).get();
        Item combustible = UniverseItems.COMBUSTIBLE_UNIT.get();
        Item explosive = UniverseItems.EXPLOSIVE_UNIT.get();
        Item crystalExplosive = UniverseItems.CRYSTAL_EXPLOSIVE_UNIT.get();
        Item magnum = UniverseItems.MAGNUM_AMMO_BOX.get();
        Item titaniumAmmo = UniverseItems.TITANIUM_AMMO_BOX.get();
        Item shell = UniverseItems.SHELL_SET.get();
        Item highExplosive = UniverseItems.HIGH_EXPLOSIVE_SHELL_SET.get();
        Item missile = UniverseItems.MISSILE_SET.get();
        Item supersonic = UniverseItems.SUPERSONIC_MISSILE_SET.get();

        // ---- 炮塔（ADR-0028 §五）：手工合成，也能在制造台里做
        this.researchShaped("gauss_turret", RecipeCategory.COMBAT, UniverseItems.GAUSS_TURRET.get(), 1,
                List.of(" B ", "CGC", "III"), Map.of('B', board, 'C', coil, 'G', gear, 'I', Items.IRON_INGOT));
        this.researchShaped("laser_turret", RecipeCategory.COMBAT, UniverseItems.LASER_TURRET.get(), 1,
                List.of(" P ", "SES", "SBS"), Map.of('P', UniverseItems.PRISM.get(), 'S', steel, 'E', exciter, 'B', board));
        this.researchShaped("implosion_cannon", RecipeCategory.COMBAT, UniverseItems.IMPLOSION_CANNON.get(), 1,
                List.of("SES", "MBM", "SSS"), Map.of('S', steel, 'E', exciter, 'M', motor, 'B', board));
        this.researchShaped("missile_turret", RecipeCategory.COMBAT, UniverseItems.MISSILE_TURRET.get(), 1,
                List.of("PTP", "HMH", "SSS"), Map.of('P', UniverseItems.PROCESSOR.get(), 'T', UniverseItems.THRUSTER.get(), 'H', alloy,
                        'M', motor, 'S', steel));
        this.machine(Kind.ASSEMBLING, "gauss_turret", 60, "gauss_turret", in(Items.IRON_INGOT, 4, gear, 2, coil, 2, board, 1),
                out(UniverseItems.GAUSS_TURRET.get(), 1));
        this.machine(Kind.ASSEMBLING, "laser_turret", 80, "laser_turret", in(steel, 4, exciter, 1, UniverseItems.PRISM.get(), 1, board, 1),
                out(UniverseItems.LASER_TURRET.get(), 1));
        this.machine(Kind.ASSEMBLING, "implosion_cannon", 100, "implosion_cannon", in(steel, 6, exciter, 1, motor, 2, board, 1),
                out(UniverseItems.IMPLOSION_CANNON.get(), 1));
        this.machine(Kind.ASSEMBLING, "missile_turret", 120, "missile_turret",
                in(steel, 3, alloy, 2, UniverseItems.THRUSTER.get(), 1, UniverseItems.PROCESSOR.get(), 2), out(UniverseItems.MISSILE_TURRET.get(), 1));

        // ---- 弹药：机枪弹箱一开始就能手搓，别的在制造台里做（手工也行）
        this.researchShaped("gauss_turret", RecipeCategory.COMBAT, magnum, 1,
                List.of("CIC"), Map.of('C', Items.COPPER_INGOT, 'I', Items.IRON_INGOT));
        this.researchShaped("titanium_ammo", RecipeCategory.COMBAT, titaniumAmmo, 1,
                List.of("T", "M", "T"), Map.of('T', titanium, 'M', magnum));
        this.researchShaped("superalloy_ammo", RecipeCategory.COMBAT, UniverseItems.SUPERALLOY_AMMO_BOX.get(), 1,
                List.of("H", "M", "H"), Map.of('H', alloy, 'M', titaniumAmmo));
        this.researchShaped("implosion_cannon", RecipeCategory.COMBAT, shell, 1,
                List.of("CSC"), Map.of('C', combustible, 'S', steel));
        this.researchShaped("high_explosive_shells", RecipeCategory.COMBAT, highExplosive, 1,
                List.of("X", "S", "X"), Map.of('X', explosive, 'S', shell));
        this.researchShaped("crystal_shells", RecipeCategory.COMBAT, UniverseItems.CRYSTAL_SHELL_SET.get(), 1,
                List.of("X", "S", "X"), Map.of('X', crystalExplosive, 'S', highExplosive));
        this.researchShaped("missile_turret", RecipeCategory.COMBAT, missile, 1,
                List.of("CBC", " S "), Map.of('C', combustible, 'B', board, 'S', steel));
        this.researchShaped("supersonic_missiles", RecipeCategory.COMBAT, supersonic, 1,
                List.of(" T ", "XMX"), Map.of('T', UniverseItems.THRUSTER.get(), 'X', explosive, 'M', missile));
        this.researchShaped("gravity_missiles", RecipeCategory.COMBAT, UniverseItems.GRAVITY_MISSILE_SET.get(), 1,
                List.of(" L ", "XMX"), Map.of('L', UniverseItems.GRAVITON_LENS.get(), 'X', crystalExplosive, 'M', supersonic));
        this.machine(Kind.ASSEMBLING, "magnum_ammo_box", 20, "gauss_turret", in(Items.COPPER_INGOT, 2, Items.IRON_INGOT, 1), out(magnum, 1));
        this.machine(Kind.ASSEMBLING, "titanium_ammo_box", 40, "titanium_ammo", in(magnum, 1, titanium, 2), out(titaniumAmmo, 1));
        this.machine(Kind.ASSEMBLING, "superalloy_ammo_box", 60, "superalloy_ammo", in(titaniumAmmo, 1, alloy, 2),
                out(UniverseItems.SUPERALLOY_AMMO_BOX.get(), 1));
        this.machine(Kind.ASSEMBLING, "shell_set", 40, "implosion_cannon", in(combustible, 2, steel, 1), out(shell, 1));
        this.machine(Kind.ASSEMBLING, "high_explosive_shell_set", 60, "high_explosive_shells", in(shell, 1, explosive, 2), out(highExplosive, 1));
        this.machine(Kind.ASSEMBLING, "crystal_shell_set", 80, "crystal_shells", in(highExplosive, 1, crystalExplosive, 2),
                out(UniverseItems.CRYSTAL_SHELL_SET.get(), 1));
        this.machine(Kind.ASSEMBLING, "missile_set", 60, "missile_turret", in(combustible, 2, board, 1, steel, 1), out(missile, 1));
        this.machine(Kind.ASSEMBLING, "supersonic_missile_set", 80, "supersonic_missiles",
                in(missile, 1, UniverseItems.THRUSTER.get(), 1, explosive, 2), out(supersonic, 1));
        this.machine(Kind.ASSEMBLING, "gravity_missile_set", 100, "gravity_missiles",
                in(supersonic, 1, UniverseItems.GRAVITON_LENS.get(), 1, crystalExplosive, 2), out(UniverseItems.GRAVITY_MISSILE_SET.get(), 1));
    }

    /** 等离子炮塔、近程等离子炮塔、两种胶囊（ADR-0028 §八，子代理 T）。 */
    private void darkFogPlasma() {
        Item steel = UniverseItems.STEEL.get();
        Item alloy = UniverseItems.TITANIUM_ALLOY.get();
        Item glass = UniverseItems.TITANIUM_GLASS.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item exciter = UniverseItems.PLASMA_EXCITER.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item graphene = UniverseItems.GRAPHENE.get();
        Item magnet = UniverseItems.ORE_SMELTED.get(OreKind.MAGNETIC).get();
        Item deuterium = UniverseItems.DEUTERIUM.get();
        Item plasma = UniverseItems.PLASMA_CAPSULE.get();
        // 等离子炮塔：钛合金的壳子、钛化玻璃的约束腔，超级磁场环和电浆激发器把氘激发成等离子团（照戴森球计划的配方）
        // （制造台的配方最多四种原料，手工合成和它用一样的料）
        this.researchShaped("plasma_turret", RecipeCategory.COMBAT, UniverseItems.PLASMA_TURRET.get(), 1, List.of("GEG", "RAR", "AAA"),
                Map.of('G', glass, 'E', exciter, 'R', ring, 'A', alloy));
        this.machine(Kind.ASSEMBLING, "plasma_turret", 200, "plasma_turret", in(alloy, 4, glass, 2, ring, 2, exciter, 1),
                out(UniverseItems.PLASMA_TURRET.get(), 1));
        // 近程等离子炮塔：钢的底座，石墨烯做散热，电浆激发器直接打
        this.researchShaped("sr_plasma_turret", RecipeCategory.COMBAT, UniverseItems.SR_PLASMA_TURRET.get(), 1, List.of(" E ", "GPG", "SSS"),
                Map.of('E', exciter, 'G', graphene, 'P', processor, 'S', steel));
        this.machine(Kind.ASSEMBLING, "sr_plasma_turret", 160, "sr_plasma_turret", in(steel, 4, graphene, 2, exciter, 2, processor, 1),
                out(UniverseItems.SR_PLASMA_TURRET.get(), 1));
        // 胶囊（只在制造台里做，一个要十份氘）：等离子胶囊装氘；反物质胶囊在等离子胶囊外面再套一个粒子容器，装反物质和氢
        this.machine(Kind.ASSEMBLING, "plasma_capsule", 40, "plasma_turret", in(graphene, 1, magnet, 2, deuterium, 10), out(plasma, 1));
        this.machine(Kind.ASSEMBLING, "antimatter_capsule", 40, "antimatter_capsule",
                in(plasma, 1, UniverseItems.PARTICLE_CONTAINER.get(), 1, UniverseItems.HYDROGEN.get(), 10, UniverseItems.ANTIMATTER.get(), 10),
                out(UniverseItems.ANTIMATTER_CAPSULE.get(), 1));
    }

    /** 行星护盾发生器（子代理 S）。 */
    private void darkFogShield() {
        // 原作：钢材、电磁涡轮、超级磁场环、粒子容器 20:20:5:5；这里和别的防御设施一样按手工合成的量缩小
        Item steel = UniverseItems.STEEL.get();
        Item turbine = UniverseItems.ELECTROMAGNETIC_TURBINE.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item container = UniverseItems.PARTICLE_CONTAINER.get();
        this.researchShaped("planetary_shield", RecipeCategory.COMBAT, UniverseItems.PLANETARY_SHIELD_GENERATOR.get(), 1,
                List.of("PRP", "TST", "SSS"), Map.of('P', container, 'R', ring, 'T', turbine, 'S', steel));
        this.machine(Kind.ASSEMBLING, "planetary_shield_generator", 200, "planetary_shield", in(steel, 4, turbine, 2, ring, 1, container, 2),
                out(UniverseItems.PLANETARY_SHIELD_GENERATOR.get(), 1));
    }

    /** 信号塔、干扰塔、投掷物、黑雾矩阵、替代配方（子代理 R2）。 */
    private void darkFogSupport() {
        Item steel = UniverseItems.STEEL.get();
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        Item coil = UniverseItems.MAGNETIC_COIL.get();
        Item exciter = UniverseItems.PLASMA_EXCITER.get();
        Item processor = UniverseItems.PROCESSOR.get();
        Item ring = UniverseItems.SUPER_MAGNETIC_RING.get();
        Item crystalSilicon = UniverseItems.CRYSTAL_SILICON.get();
        Item plastic = UniverseItems.PLASTIC.get();
        Item combustible = UniverseItems.COMBUSTIBLE_UNIT.get();
        Item explosive = UniverseItems.EXPLOSIVE_UNIT.get();
        // 信号塔：无线输电塔上架一圈天线；干扰塔：磁环绕着电浆激发器
        this.researchShaped("signal_tower", RecipeCategory.REDSTONE, UniverseItems.SIGNAL_TOWER.get(), 1, List.of(" E ", "SWS", "SBS"),
                Map.of('E', exciter, 'S', steel, 'W', UniverseItems.WIRELESS_POWER_TOWER.get(), 'B', board));
        this.machine(Kind.ASSEMBLING, "signal_tower", 200, "signal_tower",
                in(UniverseItems.WIRELESS_POWER_TOWER.get(), 1, steel, 4, exciter, 1, board, 2), out(UniverseItems.SIGNAL_TOWER.get(), 1));
        this.researchShaped("jammer_tower", RecipeCategory.REDSTONE, UniverseItems.JAMMER_TOWER.get(), 1, List.of("RER", "PSP", "SBS"),
                Map.of('R', ring, 'E', exciter, 'P', processor, 'S', steel, 'B', board));
        this.machine(Kind.ASSEMBLING, "jammer_tower", 200, "jammer_tower", in(ring, 2, exciter, 1, processor, 2, steel, 4),
                out(UniverseItems.JAMMER_TOWER.get(), 1));
        this.machine(Kind.ASSEMBLING, "jamming_capsule", 120, "jamming_capsules", in(ring, 1, crystalSilicon, 2, plastic, 1),
                out(UniverseItems.JAMMING_CAPSULE.get(), 2));
        // 投掷物：燃烧单元加塑料、硫酸做成爆破单元；爆破单元嵌上钛晶石是晶石爆破单元
        this.machine(Kind.CHEMICAL, "explosive_unit", 120, "explosives", in(combustible, 2, plastic, 2, UniverseItems.SULFURIC_ACID.get(), 1),
                out(explosive, 2));
        this.machine(Kind.ASSEMBLING, "crystal_explosive_unit", 160, "crystal_explosives",
                in(explosive, 4, UniverseItems.TITANIUM_CRYSTAL.get(), 1, crystalSilicon, 2), out(UniverseItems.CRYSTAL_EXPLOSIVE_UNIT.get(), 4));
        // 替代配方（ADR-0028 §六）：黑雾的零件换中间品，给不想开采的人一条刷怪的路；跟着各自产物的科技解锁
        this.machine(Kind.CHEMICAL, "graphene_from_matter_recombinator", 60, "high_strength_materials",
                in(UniverseItems.MATTER_RECOMBINATOR.get(), 1, UniverseItems.ENERGETIC_GRAPHITE.get(), 2), out(UniverseItems.GRAPHENE.get(), 3));
        this.machine(Kind.ASSEMBLING, "plasma_exciter_from_energy_shard", 40, "plasma_refining",
                in(UniverseItems.ENERGY_SHARD.get(), 2, coil, 1), out(exciter, 2));
        this.machine(Kind.ASSEMBLING, "accumulator_from_energy_shard", 80, "energy_storage",
                in(UniverseItems.ENERGY_SHARD.get(), 4, Items.IRON_INGOT, 2), out(UniverseItems.ACCUMULATOR.get(), 1));
        this.machine(Kind.ASSEMBLING, "processor_from_silicon_neuron", 60, "processor",
                in(UniverseItems.SILICON_NEURON.get(), 1, board, 2), out(processor, 2));
        this.machine(Kind.ASSEMBLING, "casimir_crystal_from_negentropy_singularity", 80, "quantum_printing",
                in(UniverseItems.NEGENTROPY_SINGULARITY.get(), 1, UniverseItems.TITANIUM_CRYSTAL.get(), 1), out(UniverseItems.CASIMIR_CRYSTAL.get(), 2));
        this.machine(Kind.ASSEMBLING, "graviton_lens_from_core_element", 120, "gravitational_wave_refraction",
                in(UniverseItems.CORE_ELEMENT.get(), 1, UniverseItems.SYNTHETIC_DIAMOND.get(), 2), out(UniverseItems.GRAVITON_LENS.get(), 1));
    }

    private void husbandry() {
        List<Item> plants = new ArrayList<>();
        UniverseItems.PLANTS.values().forEach(item -> plants.add(item.get()));
        plants.add(UniverseItems.PLANET_LEAVES.get());
        var herbivore = this.shapeless(RecipeCategory.FOOD, UniverseItems.HERBIVORE_FEED.get(), 2)
                .requires(Ingredient.of(plants.stream()), 3);
        for (Item plant : plants) {
            herbivore.unlockedBy(getHasName(plant), this.has(plant));
        }
        herbivore.save(this.output);
        this.shapeless(RecipeCategory.FOOD, UniverseItems.CARNIVORE_FEED.get(), 3)
                .requires(Ingredient.of(UniverseItems.ALIEN_MEAT.get(), Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT), 2)
                .requires(Items.BONE_MEAL)
                .unlockedBy(getHasName(UniverseItems.ALIEN_MEAT.get()), this.has(UniverseItems.ALIEN_MEAT.get()))
                .save(this.output);
        this.cook("smelting", SmeltingRecipe::new, 200, UniverseItems.ALIEN_EGG.get(), UniverseItems.COOKED_ALIEN_EGG.get());
        this.cook("smoking", SmokingRecipe::new, 100, UniverseItems.ALIEN_EGG.get(), UniverseItems.COOKED_ALIEN_EGG.get());
        this.cook("campfire_cooking", CampfireCookingRecipe::new, 600, UniverseItems.ALIEN_EGG.get(), UniverseItems.COOKED_ALIEN_EGG.get());
        this.shapeless(RecipeCategory.MISC, Items.STRING, 2)
                .requires(UniverseItems.ALIEN_FLUFF.get())
                .unlockedBy(getHasName(UniverseItems.ALIEN_FLUFF.get()), this.has(UniverseItems.ALIEN_FLUFF.get()))
                .save(this.output, "universe:string_from_alien_fluff");
        this.shaped(RecipeCategory.BUILDING_BLOCKS, Items.WOOL.white())
                .pattern("FF").pattern("FF")
                .define('F', UniverseItems.ALIEN_FLUFF.get())
                .unlockedBy(getHasName(UniverseItems.ALIEN_FLUFF.get()), this.has(UniverseItems.ALIEN_FLUFF.get()))
                .save(this.output, "universe:white_wool_from_alien_fluff");
        this.shapeless(RecipeCategory.MISC, Items.SLIME_BALL)
                .requires(UniverseItems.BIO_GEL.get())
                .unlockedBy(getHasName(UniverseItems.BIO_GEL.get()), this.has(UniverseItems.BIO_GEL.get()))
                .save(this.output, "universe:slime_ball_from_bio_gel");
    }

    private <T extends AbstractCookingRecipe> void cook(String source, AbstractCookingRecipe.Factory<T> factory, int time, Item input, Item result) {
        String name = BuiltInRegistries.ITEM.getKey(result).getPath();
        SimpleCookingRecipeBuilder.generic(Ingredient.of(input), RecipeCategory.FOOD, CookingBookCategory.FOOD, result, 0.35F, time, factory)
                .unlockedBy(getHasName(input), this.has(input))
                .save(this.output, "universe:" + name + "_from_" + source);
    }

    /** 原版的 simpleCookingRecipe 会把配方存到 minecraft 命名空间下，这里自己指定 ID。 */
    private <T extends AbstractCookingRecipe> void cook(Item raw, Item cooked, String source, AbstractCookingRecipe.Factory<T> factory,
            int time) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(raw), RecipeCategory.FOOD, CookingBookCategory.FOOD, cooked, 0.35F, time, factory)
                .unlockedBy(getHasName(raw), this.has(raw))
                .save(this.output, "universe:" + getItemName(cooked) + "_from_" + source);
    }
}
