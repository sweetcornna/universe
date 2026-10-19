package io.github.sweetcornna.universe.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe.Kind;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
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
        // 烤肉：熔炉、烟熏炉、营火，时间和原版的牛肉一样
        this.cookMeat("smelting", SmeltingRecipe::new, 200);
        this.cookMeat("smoking", SmokingRecipe::new, 100);
        this.cookMeat("campfire_cooking", CampfireCookingRecipe::new, 600);
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
        // ---- 冶炼、对撞
        this.machine(Kind.SMELTING, "titanium_glass", 100, "quantum_printing",
                List.of(SizedIngredient.of(Items.GLASS, 2), SizedIngredient.of(titanium, 2), SizedIngredient.of(water, 2)), out(titaniumGlass, 2));
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
        // 星球上的资源：可燃冰直接做石墨烯（顺带出氢），硫磺加水做硫酸
        this.machine(Kind.CHEMICAL, "graphene_from_fire_ice", 40, "high_strength_materials", in(UniverseItems.oreDrop(OreKind.FIRE_ICE), 2),
                List.of(new ItemStackTemplate(graphene, 2), new ItemStackTemplate(UniverseItems.HYDROGEN.get(), 1)));
        this.machine(Kind.CHEMICAL, "sulfuric_acid_from_sulfur", 60, "basic_chemical", in(UniverseItems.oreDrop(OreKind.SULFUR), 2, water, 2),
                out(acid, 2));

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
        modules.forEach((type, ingredients) -> this.machine(Kind.ASSEMBLING, type.itemName(), 100, "tech_modules", ingredients,
                out(UniverseItems.MODULES.get(type).get(), 1)));
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
    /** 信号增强器和导航数据（ADR-0014）：电路板做出来就能做，早期探索用得上。 */
    private void pois() {
        Item board = UniverseItems.CIRCUIT_BOARD.get();
        this.shaped(RecipeCategory.TOOLS, UniverseItems.SIGNAL_BOOSTER.get())
                .pattern(" R ").pattern("CBC").pattern("III")
                .define('R', Items.LIGHTNING_ROD.weathering().unaffected()).define('C', Items.COPPER_INGOT).define('B', board).define('I', Items.IRON_INGOT)
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
    private <T extends AbstractCookingRecipe> void cookMeat(String source, AbstractCookingRecipe.Factory<T> factory, int time) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(UniverseItems.ALIEN_MEAT.get()), RecipeCategory.FOOD, CookingBookCategory.FOOD,
                        UniverseItems.COOKED_ALIEN_MEAT.get(), 0.35F, time, factory)
                .unlockedBy(getHasName(UniverseItems.ALIEN_MEAT.get()), this.has(UniverseItems.ALIEN_MEAT.get()))
                .save(this.output, "universe:cooked_alien_meat_from_" + source);
    }
}
