package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.block.poi.DropPodBlock;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.client.tech.ModuleGradeTint;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlock;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.renderer.item.properties.select.DisplayContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.registries.DeferredItem;

/** 方块状态、方块模型和物品模型。 */
final class UniverseModelProvider extends ModelProvider {
    /** 六个面都带染色索引 0 的立方体（{@code assets/universe/models/block/tinted_cube_all.json}）。 */
    private static final ModelTemplate TINTED_CUBE_ALL =
            new ModelTemplate(Optional.of(Universe.id("block/tinted_cube_all")), Optional.empty(), TextureSlot.ALL);
    private static final TextureSlot OVERLAY = TextureSlot.create("overlay");
    /** 草皮：顶面和侧面上沿染索引 0（地表色），其余染索引 1（土壤色）。 */
    private static final ModelTemplate TINTED_TURF = new ModelTemplate(
            Optional.of(Universe.id("block/tinted_turf")), Optional.empty(), TextureSlot.TOP, TextureSlot.BOTTOM, OVERLAY);
    private static final TextureSlot BODY = TextureSlot.create("body");
    private static final TextureSlot DETAIL = TextureSlot.create("detail");
    /** 植物：主体染索引 0、点缀染索引 1 的交叉面片；发光物种的点缀层自发光。 */
    private static final ModelTemplate PLANT_CROSS = template("plant_cross", BODY, DETAIL);
    private static final ModelTemplate PLANT_CROSS_EMISSIVE = template("plant_cross_emissive", BODY, DETAIL);
    /** 地衣：贴地的一片。 */
    private static final ModelTemplate PLANT_PATCH = template("plant_patch", BODY, DETAIL);
    private static final ModelTemplate PLANT_PATCH_EMISSIVE = template("plant_patch_emissive", BODY, DETAIL);
    private static final ModelTemplate TINTED_COLUMN = template("tinted_column", TextureSlot.SIDE, TextureSlot.END);
    private static final ModelTemplate TINTED_CUBE_ALL_EMISSIVE = template("tinted_cube_all_emissive", TextureSlot.ALL);
    /** 矿石：底层是染成星岩色的岩石（索引 0），上面一层不染色的矿点；会发光的矿点层自发光。 */
    private static final ModelTemplate TINTED_ORE = template("tinted_ore", TextureSlot.ALL, OVERLAY);
    private static final ModelTemplate TINTED_ORE_EMISSIVE = template("tinted_ore_emissive", TextureSlot.ALL, OVERLAY);

    UniverseModelProvider(PackOutput output) {
        super(output, Universe.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        for (UniverseBlocks.Tinted tinted : UniverseBlocks.TINTED) {
            Block block = tinted.block().get();
            Identifier model;
            if (block == UniverseBlocks.PLANET_TURF.get()) {
                TextureMapping textures = new TextureMapping()
                        .put(TextureSlot.TOP, texture("planet_turf_top"))
                        .put(TextureSlot.BOTTOM, texture("planet_soil"))
                        .put(OVERLAY, texture("planet_turf_side_overlay"));
                model = TINTED_TURF.create(block, textures, blockModels.modelOutput);
            } else {
                model = TINTED_CUBE_ALL.create(block, new TextureMapping().put(TextureSlot.ALL, TextureMapping.getBlockTexture(block)),
                        blockModels.modelOutput);
            }
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
            // 物品只染第一层：草皮物品看起来就是地表色的方块
            itemModels.itemModelOutput.accept(block.asItem(),
                    ItemModelUtils.tintedModel(model, new PlanetTints.PaletteItemTint(tinted.primary()),
                            new PlanetTints.PaletteItemTint(tinted.secondary().orElse(tinted.primary()))));
        }
        UniverseBlocks.PLANTS.forEach((form, block) -> plant(blockModels, itemModels, form, block.get()));
        this.trees(blockModels, itemModels);
        for (DeferredItem<?> item : List.of(UniverseItems.ALIEN_MEAT, UniverseItems.COOKED_ALIEN_MEAT, UniverseItems.ALIEN_HIDE,
                UniverseItems.ALIEN_CREATURE_SPAWN_EGG, UniverseItems.ALIEN_FLYER_SPAWN_EGG, UniverseItems.LAUNCH_FUEL,
                UniverseItems.SPACE_WARPER, UniverseItems.SURVEY_DATA)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
        ships(blockModels);
        pois(blockModels, itemModels);
        ores(blockModels, itemModels);
        for (DeferredItem<?> item : List.of(UniverseItems.EXOSUIT_HELMET, UniverseItems.EXOSUIT_CHESTPLATE, UniverseItems.EXOSUIT_LEGGINGS,
                UniverseItems.EXOSUIT_BOOTS, UniverseItems.OXYGEN_CAPSULE, UniverseItems.HAZARD_CELL, UniverseItems.DAMAGED_MULTITOOL)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
        // 多功能工具拿在手里像工具一样斜着
        itemModels.generateFlatItem(UniverseItems.MULTITOOL.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
        // 技术模块：芯片加按品级染色的边框（ADR-0014）
        UniverseItems.MODULES.values().forEach(item -> itemModels.generateItemWithTintedOverlay(item.get(), ModuleGradeTint.INSTANCE));
        factory(blockModels, itemModels);
    }

    /** 工业设备：两格高的设备模型由 tools/factory_models.py 生成，这里按上下半截、工作状态、朝向挑模型。 */
    private static void factory(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        tall(blockModels, itemModels, UniverseBlocks.TESLA_TOWER.get(), "tesla_tower", false, true);
        tall(blockModels, itemModels, UniverseBlocks.WIND_TURBINE.get(), "wind_turbine", false, false);
        tall(blockModels, itemModels, UniverseBlocks.THERMAL_PLANT.get(), "thermal_plant", true, false);
        // 蓄电器：正面四格电量
        Block accumulator = UniverseBlocks.ACCUMULATOR.get();
        Identifier[] charges = new Identifier[AccumulatorBlock.MAX_CHARGE + 1];
        for (int i = 0; i < charges.length; i++) {
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.FRONT, texture("accumulator_front_" + i))
                    .put(TextureSlot.SIDE, texture("accumulator_side"))
                    .put(TextureSlot.TOP, texture("accumulator_top"));
            charges[i] = ModelTemplates.CUBE_ORIENTABLE.createWithSuffix(accumulator, "_" + i, textures, blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(accumulator)
                .with(PropertyDispatch.initial(AccumulatorBlock.CHARGE).generate(charge -> BlockModelGenerators.plainVariant(charges[charge])))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(accumulator, charges[0]);
        recipeMachine(blockModels, UniverseBlocks.ARC_SMELTER.get(), "arc_smelter", true, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.ASSEMBLER.get(), "assembler", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.MINING_MACHINE.get(), "mining_machine", false, "mining_machine_bottom");
        recipeMachine(blockModels, UniverseBlocks.WATER_PUMP.get(), "water_pump", false, "water_pump_bottom");
        // 中期工业（ADR-0015）
        recipeMachine(blockModels, UniverseBlocks.ASSEMBLER_MK2.get(), "assembler_mk2", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.ASSEMBLER_MK3.get(), "assembler_mk3", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.PLANE_SMELTER.get(), "plane_smelter", true, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.CHEMICAL_PLANT.get(), "chemical_plant", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.FRACTIONATOR.get(), "fractionator", false, "fractionator_top");
        recipeMachine(blockModels, UniverseBlocks.ADVANCED_MINING_MACHINE.get(), "advanced_mining_machine", false, "mining_machine_bottom");
        Block anchor = UniverseBlocks.FACTORY_ANCHOR.get();
        Identifier anchorModel = ModelTemplates.CUBE_BOTTOM_TOP.create(anchor, new TextureMapping()
                .put(TextureSlot.TOP, texture("factory_anchor_top"))
                .put(TextureSlot.SIDE, texture("factory_anchor_side"))
                .put(TextureSlot.BOTTOM, texture("machine_casing_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(anchor, BlockModelGenerators.plainVariant(anchorModel)));
        blockModels.registerSimpleItemModel(anchor, anchorModel);
        // 物流站（ADR-0015）：停机坪的指示灯工作时亮；采集器不分工作状态
        tall(blockModels, itemModels, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get(), "planetary_logistics_station", false, true);
        tall(blockModels, itemModels, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get(), "interstellar_logistics_station", false, true);
        Block collector = UniverseBlocks.ORBITAL_COLLECTOR.get();
        Identifier collectorModel = Universe.id("block/orbital_collector");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(collector)
                .with(PropertyDispatch.initial(MachineBlock.WORKING).generate(on -> BlockModelGenerators.plainVariant(collectorModel)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(collector, collectorModel);
        // 无人机和运输船：物品栏里是平面图标，拿在手里、掉在地上和飞行时是立体模型
        craft(itemModels, UniverseItems.LOGISTICS_DRONE.get(), "logistics_drone_model");
        craft(itemModels, UniverseItems.LOGISTICS_VESSEL.get(), "logistics_vessel_model");
        tall(blockModels, itemModels, UniverseBlocks.OIL_EXTRACTOR.get(), "oil_extractor", true, false);
        tall(blockModels, itemModels, UniverseBlocks.OIL_REFINERY.get(), "oil_refinery", true, false);
        // 原油涌泉：顶上是油面，四周是浸了油的土
        Block seep = UniverseBlocks.CRUDE_OIL_SEEP.get();
        Identifier seepModel = ModelTemplates.CUBE_BOTTOM_TOP.create(seep, new TextureMapping()
                .put(TextureSlot.TOP, texture("crude_oil_seep_top"))
                .put(TextureSlot.SIDE, texture("crude_oil_seep_side"))
                .put(TextureSlot.BOTTOM, texture("crude_oil_seep_side")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(seep, BlockModelGenerators.plainVariant(seepModel)));
        blockModels.registerSimpleItemModel(seep, seepModel);
        belt(blockModels, itemModels, UniverseBlocks.CONVEYOR_BELT.get(), "conveyor_belt");
        belt(blockModels, itemModels, UniverseBlocks.CONVEYOR_BELT_MK2.get(), "conveyor_belt_mk2");
        belt(blockModels, itemModels, UniverseBlocks.CONVEYOR_BELT_MK3.get(), "conveyor_belt_mk3");
        // 分拣器：底座和立柱是方块模型，手臂由方块实体渲染器画；物品模型里带一只朝后的手臂。三档只换颜色
        for (Block sorter : List.of(UniverseBlocks.SORTER.get(), UniverseBlocks.SORTER_MK2.get(), UniverseBlocks.SORTER_MK3.get())) {
            String name = BuiltInRegistries.BLOCK.getKey(sorter).getPath();
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(sorter, BlockModelGenerators.plainVariant(Universe.id("block/" + name)))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            itemModels.itemModelOutput.accept(sorter.asItem(), ItemModelUtils.plainModel(Universe.id("block/" + name + "_item")));
        }
        // 分流器、储液罐：模型是手写的
        for (Block block : List.of(UniverseBlocks.SPLITTER.get(), UniverseBlocks.STORAGE_TANK.get())) {
            Identifier model = ModelLocationUtils.getModelLocation(block);
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
            itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(model));
        }
        // 太阳能板：底座和立柱是方块模型，板子由方块实体渲染器画（追着太阳转）；物品模型里板子放平
        Block solar = UniverseBlocks.SOLAR_PANEL.get();
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(solar,
                BlockModelGenerators.plainVariant(Universe.id("block/solar_panel"))));
        itemModels.itemModelOutput.accept(solar.asItem(), ItemModelUtils.plainModel(Universe.id("block/solar_panel_item")));
        // 矩阵研究站：模型是手写的，核心由方块实体渲染器画；物品模型里带着核心
        Block lab = UniverseBlocks.MATRIX_LAB.get();
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(lab, BlockModelGenerators.plainVariant(Universe.id("block/matrix_lab")))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        itemModels.itemModelOutput.accept(lab.asItem(), ItemModelUtils.plainModel(Universe.id("block/matrix_lab_item")));
        Block depot = UniverseBlocks.STORAGE_DEPOT.get();
        Identifier depotModel = ModelTemplates.CUBE_BOTTOM_TOP.create(depot, new TextureMapping()
                .put(TextureSlot.TOP, texture("storage_depot_top"))
                .put(TextureSlot.SIDE, texture("storage_depot_side"))
                .put(TextureSlot.BOTTOM, texture("storage_depot_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(depot, BlockModelGenerators.plainVariant(depotModel)));
        blockModels.registerSimpleItemModel(depot, depotModel);
        for (DeferredItem<?> item : List.of(UniverseItems.GEAR, UniverseItems.MAGNETIC_COIL, UniverseItems.CIRCUIT_BOARD,
                UniverseItems.ELECTRIC_MOTOR, UniverseItems.STEEL, UniverseItems.PRISM, UniverseItems.PLASMA_EXCITER,
                UniverseItems.ENERGETIC_GRAPHITE, UniverseItems.WATER, UniverseItems.CRUDE_OIL, UniverseItems.REFINED_OIL,
                UniverseItems.HYDROGEN, UniverseItems.ELECTROMAGNETIC_MATRIX, UniverseItems.ENERGY_MATRIX,
                UniverseItems.DEUTERIUM, UniverseItems.CRYSTAL_SILICON, UniverseItems.TITANIUM_ALLOY, UniverseItems.SYNTHETIC_DIAMOND,
                UniverseItems.PLASTIC, UniverseItems.SULFURIC_ACID, UniverseItems.GRAPHENE, UniverseItems.CARBON_NANOTUBE,
                UniverseItems.TITANIUM_CRYSTAL, UniverseItems.MICROCRYSTALLINE_COMPONENT, UniverseItems.PROCESSOR, UniverseItems.PARTICLE_BROADBAND,
                UniverseItems.THRUSTER, UniverseItems.REINFORCED_THRUSTER, UniverseItems.ELECTROMAGNETIC_TURBINE, UniverseItems.SUPER_MAGNETIC_RING,
                UniverseItems.PARTICLE_CONTAINER, UniverseItems.STRUCTURE_MATRIX,
                UniverseItems.INFORMATION_MATRIX, UniverseItems.PHOTON_COMBINER, UniverseItems.SOLAR_SAIL, UniverseItems.FRAME_MATERIAL,
                UniverseItems.DYSON_SPHERE_COMPONENT, UniverseItems.SMALL_CARRIER_ROCKET, UniverseItems.DEUTERON_FUEL_ROD,
                UniverseItems.TITANIUM_GLASS, UniverseItems.CASIMIR_CRYSTAL, UniverseItems.PLANE_FILTER, UniverseItems.QUANTUM_CHIP,
                UniverseItems.GRAVITON_LENS, UniverseItems.CRITICAL_PHOTON, UniverseItems.GRAVITY_MATRIX)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
        // 戴森球的设备（ADR-0016）：会动的部分（炮管、碟面、火箭）由方块实体渲染器画，模型由 tools/dyson_models.py 生成
        for (Block block : List.of(UniverseBlocks.EM_RAIL_EJECTOR.get(), UniverseBlocks.RAY_RECEIVER.get())) {
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block,
                    BlockModelGenerators.plainVariant(Universe.id("block/" + name))));
            itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(Universe.id("block/" + name + "_item")));
        }
        tall(blockModels, itemModels, UniverseBlocks.VERTICAL_LAUNCHING_SILO.get(), "vertical_launching_silo", false, true);
        recipeMachine(blockModels, UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.get(), "miniature_particle_collider", false,
                "machine_casing_top");
    }

    /** 载具：界面里用平面图标，其余场合用 {@code models/block/} 下手写的立体模型。 */
    private static void craft(ItemModelGenerators itemModels, Item item, String model) {
        Identifier flat = itemModels.createFlatItemModel(item, ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.select(new DisplayContext(), ItemModelUtils.plainModel(Universe.id("block/" + model)),
                ItemModelUtils.when(ItemDisplayContext.GUI, ItemModelUtils.plainModel(flat))));
    }

    /** 一格的设备：正面（和电弧熔炉的顶面）工作时换成发光的贴图。 */
    private static void recipeMachine(BlockModelGenerators blockModels, Block block, String name, boolean topLit, String bottom) {
        Identifier[] models = new Identifier[2];
        for (int on = 0; on < 2; on++) {
            String suffix = on == 1 ? "_on" : "";
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.FRONT, texture(name + "_front" + suffix))
                    .put(TextureSlot.SIDE, texture(name + "_side"))
                    .put(TextureSlot.TOP, texture(name + "_top" + (topLit ? suffix : "")))
                    .put(TextureSlot.BOTTOM, texture(bottom));
            models[on] = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.createWithSuffix(block, suffix, textures, blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(MachineBlock.WORKING).generate(on -> BlockModelGenerators.plainVariant(models[on ? 1 : 0])))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(block, models[0]);
    }

    /** 传送带：模型是手写的（平的、左右弯道、上下坡），按坡度和弯道选，再按朝向转。 */
    private static void belt(BlockModelGenerators blockModels, ItemModelGenerators itemModels, Block block, String name) {
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(BeltBlock.SLOPE, BeltBlock.CURVE).generate((slope, curve) -> {
                    String suffix = switch (slope) {
                        case UP -> "_up";
                        case DOWN -> "_down";
                        case FLAT -> switch (curve) {
                            case LEFT -> "_curve_left";
                            case RIGHT -> "_curve_right";
                            case NONE -> "";
                        };
                    };
                    return BlockModelGenerators.plainVariant(Universe.id("block/" + name + suffix));
                }))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(Universe.id("block/" + name)));
    }

    /** 两格高的设备：{@code <名字>_lower/_upper}，工作时发光的那半截另有 {@code _on}；物品用两半叠起来的 {@code _item}。 */
    private static void tall(BlockModelGenerators blockModels, ItemModelGenerators itemModels, Block block, String name,
            boolean lowerLit, boolean upperLit) {
        Identifier lower = Universe.id("block/" + name + "_lower");
        Identifier upper = Universe.id("block/" + name + "_upper");
        Identifier lowerOn = lowerLit ? Universe.id("block/" + name + "_lower_on") : lower;
        Identifier upperOn = upperLit ? Universe.id("block/" + name + "_upper_on") : upper;
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(TallMachineBlock.HALF, MachineBlock.WORKING).generate((half, working) ->
                        BlockModelGenerators.plainVariant(half == DoubleBlockHalf.LOWER ? (working ? lowerOn : lower) : (working ? upperOn : upper))))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(Universe.id("block/" + name + "_item")));
    }

    private static void ores(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        UniverseBlocks.ORES.forEach((kind, holder) -> {
            Block block = holder.get();
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.ALL, texture("planet_rock"))
                    .put(OVERLAY, texture(kind.blockName() + "_overlay"));
            Identifier model = (kind.light() > 0 ? TINTED_ORE_EMISSIVE : TINTED_ORE).create(block, textures, blockModels.modelOutput);
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
            itemModels.itemModelOutput.accept(block.asItem(),
                    ItemModelUtils.tintedModel(model, new PlanetTints.PaletteItemTint(Palette.Channel.ROCK)));
        });
        UniverseItems.ORE_DROPS.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        UniverseItems.ORE_SMELTED.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
    }

    /**
     * 飞船部件：驾驶台、推进器、脉冲引擎、跃迁引擎、座椅、光子炮、采矿光束、护盾发生器的模型是手写的（{@code models/block/}），
     * Mk.II 部件的手写模型继承普通版、换贴图，这里只出方块状态。
     */
    private static void ships(BlockModelGenerators blockModels) {
        for (Block block : new Block[] {UniverseBlocks.SHIP_HELM.get(), UniverseBlocks.SHIP_SEAT.get(), UniverseBlocks.IMPULSE_ENGINE.get(),
                UniverseBlocks.WARP_DRIVE.get(), UniverseBlocks.PHOTON_CANNON.get(), UniverseBlocks.MINING_BEAM.get(),
                UniverseBlocks.IMPULSE_ENGINE_MK2.get(), UniverseBlocks.WARP_DRIVE_MK2.get()}) {
            Identifier model = ModelLocationUtils.getModelLocation(block);
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            blockModels.registerSimpleItemModel(block, model);
        }
        for (Block thruster : new Block[] {UniverseBlocks.SHIP_THRUSTER.get(), UniverseBlocks.SHIP_THRUSTER_MK2.get()}) {
            Identifier thrusterModel = ModelLocationUtils.getModelLocation(thruster);
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(thruster, BlockModelGenerators.plainVariant(thrusterModel))
                    .with(BlockModelGenerators.ROTATION_FACING));
            blockModels.registerSimpleItemModel(thruster, thrusterModel);
        }
        Block shield = UniverseBlocks.SHIELD_GENERATOR.get();
        Identifier shieldModel = ModelLocationUtils.getModelLocation(shield);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(shield, BlockModelGenerators.plainVariant(shieldModel)));
        blockModels.registerSimpleItemModel(shield, shieldModel);
        blockModels.createTrivialBlock(UniverseBlocks.FUEL_TANK.get(), TexturedModel.COLUMN);
        blockModels.createTrivialBlock(UniverseBlocks.FUEL_TANK_MK2.get(), TexturedModel.COLUMN);
        UniverseBlocks.HULL_PLATING.values().forEach(block -> blockModels.createTrivialCube(block.get()));
        blockModels.createTrivialCube(UniverseBlocks.ALLOY_HULL_PLATING.get());
    }

    /** 兴趣点的方块（ADR-0014）：掉落舱的正面按修没修好换贴图；信号增强器的模型是手写的。 */
    private static void pois(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Block pod = UniverseBlocks.DROP_POD.get();
        Identifier[] podModels = new Identifier[2];
        for (int repaired = 0; repaired < 2; repaired++) {
            String suffix = repaired == 1 ? "_repaired" : "";
            podModels[repaired] = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.createWithSuffix(pod, suffix, new TextureMapping()
                    .put(TextureSlot.FRONT, texture("drop_pod_front" + suffix))
                    .put(TextureSlot.SIDE, texture("drop_pod_side"))
                    .put(TextureSlot.TOP, texture("drop_pod_top"))
                    .put(TextureSlot.BOTTOM, texture("drop_pod_top")), blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(pod)
                .with(PropertyDispatch.initial(DropPodBlock.REPAIRED).generate(repaired -> BlockModelGenerators.plainVariant(podModels[repaired ? 1 : 0])))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(pod, podModels[0]);
        for (Block block : List.of(UniverseBlocks.DATA_TERMINAL.get(), UniverseBlocks.SUPPLY_CRATE.get(), UniverseBlocks.TRADE_TERMINAL.get(),
                UniverseBlocks.SHIP_REPAIR.get())) {
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            Identifier model = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(block, new TextureMapping()
                    .put(TextureSlot.FRONT, texture(name + "_front"))
                    .put(TextureSlot.SIDE, texture(name + "_side"))
                    .put(TextureSlot.TOP, texture(name + "_top"))
                    .put(TextureSlot.BOTTOM, texture(name + "_top")), blockModels.modelOutput);
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            blockModels.registerSimpleItemModel(block, model);
        }
        // 空间站的建材（ADR-0017）
        for (Block block : List.of(UniverseBlocks.STATION_HULL.get(), UniverseBlocks.STATION_FLOOR.get(), UniverseBlocks.STATION_GLASS.get(),
                UniverseBlocks.STATION_LIGHT.get(), UniverseBlocks.STATION_PAD.get(), UniverseBlocks.STATION_TRIM.get())) {
            blockModels.createTrivialCube(block);
        }
        for (Block block : List.of(UniverseBlocks.MONOLITH.get(), UniverseBlocks.TECH_CACHE.get(), UniverseBlocks.SMOLDERING_WRECK.get())) {
            blockModels.createTrivialBlock(block, TexturedModel.COLUMN);
        }
        Block booster = UniverseBlocks.SIGNAL_BOOSTER.get();
        Identifier boosterModel = ModelLocationUtils.getModelLocation(booster);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(booster, BlockModelGenerators.plainVariant(boosterModel)));
        blockModels.registerSimpleItemModel(booster, boosterModel);
        itemModels.generateFlatItem(UniverseItems.NAVIGATION_DATA.get(), ModelTemplates.FLAT_ITEM);
        // 守卫机（ADR-0014）
        itemModels.generateFlatItem(UniverseItems.SENTINEL_SCRAP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.PIRATE_SCRAP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.SENTINEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.HEAVY_SENTINEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
    }

    /** 地面植物：变体号单双数用两种外形，发光的物种换自发光模型。 */
    private static void plant(BlockModelGenerators blockModels, ItemModelGenerators itemModels, PlantForm form, Block block) {
        boolean flat = form == PlantForm.LICHEN;
        Identifier[][] models = new Identifier[2][2];
        for (int shape = 0; shape < 2; shape++) {
            String suffix = shape == 0 ? "_a" : "_b";
            TextureMapping textures = new TextureMapping()
                    .put(BODY, texture(form.blockName() + suffix))
                    .put(DETAIL, texture(form.blockName() + suffix + "_detail"));
            models[shape][0] = (flat ? PLANT_PATCH : PLANT_CROSS).createWithSuffix(block, suffix, textures, blockModels.modelOutput);
            models[shape][1] = (flat ? PLANT_PATCH_EMISSIVE : PLANT_CROSS_EMISSIVE)
                    .createWithSuffix(block, suffix + "_lit", textures, blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(
                PropertyDispatch.initial(FloraBlocks.VARIANT, FloraBlocks.LIT).generate((variant, lit) ->
                        BlockModelGenerators.plainVariant(models[variant % 2][lit ? 1 : 0]))));
        Identifier item = ModelTemplates.TWO_LAYERED_ITEM.create(block.asItem(),
                TextureMapping.layered(texture(form.blockName() + "_a"), texture(form.blockName() + "_a_detail")),
                itemModels.modelOutput);
        itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.tintedModel(item,
                new PlanetTints.PlantItemTint(form, 0), new PlanetTints.PlantItemTint(form, 1)));
    }

    private void trees(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Block log = UniverseBlocks.PLANET_LOG.get();
        Identifier logModel = TINTED_COLUMN.create(log, new TextureMapping()
                .put(TextureSlot.SIDE, texture("planet_log")).put(TextureSlot.END, texture("planet_log_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createAxisAlignedPillarBlock(log, BlockModelGenerators.plainVariant(logModel)));
        itemModels.itemModelOutput.accept(log.asItem(), ItemModelUtils.tintedModel(logModel, new PlanetTints.TreeItemTint(Flora.TreePart.WOOD)));

        for (Block block : new Block[] {UniverseBlocks.PLANET_LEAVES.get(), UniverseBlocks.PLANET_CAP.get()}) {
            Identifier model = TINTED_CUBE_ALL.create(block, new TextureMapping().put(TextureSlot.ALL, TextureMapping.getBlockTexture(block)),
                    blockModels.modelOutput);
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
            itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.tintedModel(model, new PlanetTints.TreeItemTint(Flora.TreePart.LEAVES)));
        }

        Block bloom = UniverseBlocks.PLANET_BLOOM.get();
        TextureMapping bloomTextures = new TextureMapping().put(TextureSlot.ALL, TextureMapping.getBlockTexture(bloom));
        Identifier bloomModel = TINTED_CUBE_ALL.create(bloom, bloomTextures, blockModels.modelOutput);
        Identifier bloomLit = TINTED_CUBE_ALL_EMISSIVE.createWithSuffix(bloom, "_lit", bloomTextures, blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(bloom).with(
                PropertyDispatch.initial(FloraBlocks.LIT).generate(lit -> BlockModelGenerators.plainVariant(lit ? bloomLit : bloomModel))));
        itemModels.itemModelOutput.accept(bloom.asItem(), ItemModelUtils.tintedModel(bloomModel, new PlanetTints.TreeItemTint(Flora.TreePart.BLOOM)));
    }

    private static ModelTemplate template(String name, TextureSlot... slots) {
        return new ModelTemplate(Optional.of(Universe.id("block/" + name)), Optional.empty(), slots);
    }

    private static Material texture(String name) {
        return new Material(Universe.id("block/" + name));
    }
}
