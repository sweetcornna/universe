package io.github.sweetcornna.universe.datagen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.block.landmark.KnowledgeStoneBlock;
import io.github.sweetcornna.universe.block.landmark.ReinforcedDoorBlock;
import io.github.sweetcornna.universe.block.poi.DropPodBlock;
import io.github.sweetcornna.universe.block.poi.SalvageCapsuleBlock;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.ClamBlock;
import io.github.sweetcornna.universe.block.CreatureEggBlock;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.client.fluid.AcidClient;
import io.github.sweetcornna.universe.client.creature.EggClient;
import io.github.sweetcornna.universe.client.tech.ModuleGradeTint;
import io.github.sweetcornna.universe.client.tech.MultitoolClassProperty;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.TrafficMonitorBlock;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlock;
import io.github.sweetcornna.universe.block.portal.PortalGlyphBlock;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
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
import net.minecraft.client.renderer.item.SelectItemModel;
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
    private static final ModelTemplate TRAY = template("hydroponic_tray_template", TextureSlot.FRONT, TextureSlot.SIDE, TextureSlot.TOP,
            TextureSlot.BOTTOM);
    /** 便携精炼机：十二格见方的小箱子。 */
    private static final ModelTemplate SMALL_BOX = template("portable_refiner_template", TextureSlot.FRONT, TextureSlot.SIDE, TextureSlot.TOP,
            TextureSlot.BOTTOM);
    private static final ModelTemplate PLANT_CROSS_EMISSIVE = template("plant_cross_emissive", BODY, DETAIL);
    /** 甲壳蚌（ADR-0026）：合着的和撬开的两个手写父模型，撬开的多一张壳里的贴图。 */
    private static final TextureSlot INNER = TextureSlot.create("inner");
    private static final ModelTemplate CLAM = template("clam_template", TextureSlot.TOP, TextureSlot.SIDE);
    private static final ModelTemplate CLAM_OPEN = template("clam_open_template", TextureSlot.TOP, TextureSlot.SIDE, INNER);
    /** 生物卵（ADR-0026 §九）：八格宽、十格高的卵，每个面都带染色索引 0。 */
    private static final ModelTemplate CREATURE_EGG = template("creature_egg_template", TextureSlot.SIDE, TextureSlot.TOP);
    /** 地衣：贴地的一片。 */
    private static final ModelTemplate PLANT_PATCH = template("plant_patch", BODY, DETAIL);
    private static final ModelTemplate PLANT_PATCH_EMISSIVE = template("plant_patch_emissive", BODY, DETAIL);
    private static final ModelTemplate TINTED_COLUMN = template("tinted_column", TextureSlot.SIDE, TextureSlot.END);
    private static final ModelTemplate TINTED_CUBE_ALL_EMISSIVE = template("tinted_cube_all_emissive", TextureSlot.ALL);
    /** 矿石：底层是染成星岩色的岩石（索引 0），上面一层不染色的矿点；会发光的矿点层自发光。 */
    private static final ModelTemplate TINTED_ORE = template("tinted_ore", TextureSlot.ALL, OVERLAY);
    private static final ModelTemplate TINTED_ORE_EMISSIVE = template("tinted_ore_emissive", TextureSlot.ALL, OVERLAY);
    /** 设备运转时正面叠一层和它共面的自发光贴图（只含灯、屏幕这些像素，ADR-0031 §三）。 */
    private static final TextureSlot GLOW = TextureSlot.create("glow");
    private static final ModelTemplate MACHINE_GLOW = template("machine_glow", TextureSlot.FRONT, TextureSlot.SIDE, TextureSlot.TOP,
            TextureSlot.BOTTOM, GLOW);
    /** 顶面也亮的设备（电弧熔炉这类）：正面和顶面各叠一层。 */
    private static final TextureSlot GLOW_TOP = TextureSlot.create("glow_top");
    private static final ModelTemplate MACHINE_GLOW_TOP = template("machine_glow_top", TextureSlot.FRONT, TextureSlot.SIDE,
            TextureSlot.TOP, TextureSlot.BOTTOM, GLOW, GLOW_TOP);

    /** 手画的贴图所在的目录：看某张发光层贴图在不在。 */
    private static Path textures;

    UniverseModelProvider(PackOutput output) {
        super(output, Universe.MOD_ID);
        textures = output.getOutputFolder().getParent().getParent().resolve("main/resources/assets/universe/textures");
    }

    private static boolean hasTexture(String name) {
        return Files.exists(textures.resolve("block/" + name + ".png"));
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
                UniverseItems.HERBIVORE_FEED, UniverseItems.CARNIVORE_FEED, UniverseItems.ALIEN_EGG, UniverseItems.COOKED_ALIEN_EGG,
                UniverseItems.ALIEN_FLUFF, UniverseItems.BIO_GEL, UniverseItems.ALIEN_CREATURE_SPAWN_EGG, UniverseItems.ALIEN_FLYER_SPAWN_EGG, UniverseItems.LAUNCH_FUEL,
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
        // 多功能工具拿在手里像工具一样斜着；按类型换贴图，手枪和没有类型的旧工具用原来那张（ADR-0026）
        multitool(itemModels);
        // 技术模块：芯片加按品级染色的边框（ADR-0014）
        UniverseItems.MODULES.values().forEach(item -> itemModels.generateItemWithTintedOverlay(item.get(), ModuleGradeTint.INSTANCE));
        // 镉、翠金、铟驱动（ADR-0024）
        UniverseItems.DRIVES.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        // 多功能工具的武器（ADR-0026 §六）：装进工具槽的枪管组件
        UniverseItems.WEAPONS.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        // 资源植物（ADR-0026）：长熟的和收过的两种样子，植物本身没有物品；产物是平面物品
        UniverseBlocks.RESOURCE_PLANTS.values().forEach(block -> resourcePlant(blockModels, block.get()));
        UniverseItems.RESOURCES.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        smallMachine(blockModels, UniverseBlocks.HYDROPONIC_TRAY.get(), TRAY, "hydroponic_tray", "machine_casing_top");
        smallMachine(blockModels, UniverseBlocks.PORTABLE_REFINER.get(), SMALL_BOX, "portable_refiner", "portable_refiner_bottom");
        recipeMachine(blockModels, UniverseBlocks.NUTRIENT_PROCESSOR.get(), "nutrient_processor", true, "nutrient_processor_side");
        eggs(blockModels, itemModels);
        UniverseItems.DISHES.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        UniverseItems.GELS.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        underwater(blockModels, itemModels);
        wildLandmarks(blockModels, itemModels);
        outpostLandmarks(blockModels);
        civLandmarks(blockModels);
        factory(blockModels, itemModels);
        darkFogItems(itemModels);
        darkFogBases(blockModels, itemModels);
        darkFogFacilities(blockModels, itemModels);
        darkFogTurrets(blockModels, itemModels);
        darkFogSupport(blockModels, itemModels);
        darkFogPlasma(blockModels, itemModels);
        darkFogShield(blockModels, itemModels);
        wrapUpAcid(blockModels, itemModels);
        wrapUpInterop(blockModels, itemModels);
        // M18 舰队单位（ADR-0033 §一）：平面物品
        for (var unit : List.of(UniverseItems.PROTOTYPE, UniverseItems.PRECISION_DRONE, UniverseItems.ATTACK_DRONE, UniverseItems.CORVETTE,
                UniverseItems.DESTROYER)) {
            itemModels.generateFlatItem(unit.get(), ModelTemplates.FLAT_ITEM);
        }
        // M18 舰队和战斗（A）的模型加在这行上面
        // M18 物流调度（B）的模型加在这行上面
        // M18 统计和研究（C）的模型加在这行上面
        m18Beacons(blockModels, itemModels);
        // M18 信标、透镜、矿脉、蓝图（D）的模型加在这行上面
        FishingData.models(blockModels, itemModels);
        // M19 钓鱼（A）的模型加在这行上面
        SettlementDatagen.models(blockModels);
        // M19 聚落（B）的模型加在这行上面
        FossilData.models(blockModels, itemModels);
        // M19 化石（C）的模型加在这行上面
        DeepSpaceDatagen.models(blockModels, itemModels);
        // M19 深空和重力枪（D）的模型加在这行上面
        HazardModels.add(blockModels);
        // M19 科技布局和局部危害（E）的模型加在这行上面
        UniverseItems.M20_SHARED.forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        UniverseItems.M20_REACTIONS.forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        itemModels.generateFlatItem(UniverseItems.LAB_NOTES.get(), ModelTemplates.FLAT_ITEM);
        // 大气提取器顶上是进气格栅，工作时亮；电解槽正面是电解池的观察窗
        recipeMachine(blockModels, UniverseBlocks.ATMOSPHERIC_EXTRACTOR.get(), "atmospheric_extractor", true, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.ELECTROLYZER.get(), "electrolyzer", false, "machine_casing_top");
        // M20 化学（A）的模型加在这行上面
        recipeMachine(blockModels, UniverseBlocks.CHEMICAL_REACTOR.get(), "chemical_reactor", false, "machine_casing_top");
        // M20 反应釜（B）的模型加在这行上面
        // 培养箱正面是玻璃门，工作时里面亮着；测序仪、编辑台正面是屏幕
        recipeMachine(blockModels, UniverseBlocks.INCUBATOR.get(), "incubator", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.SEQUENCER.get(), "sequencer", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.GENE_EDITOR.get(), "gene_editor", false, "machine_casing_top");
        itemModels.generateFlatItem(UniverseItems.SAMPLE_TUBE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.SAMPLE.get(), ModelTemplates.FLAT_ITEM);
        // M20 微生物和基因（C）的模型加在这行上面
        recipeMachine(blockModels, UniverseBlocks.BIOREACTOR.get(), "bioreactor", true, "machine_casing_top");
        // M20 生物反应器（D）的模型加在这行上面
        recipeMachine(blockModels, UniverseBlocks.ATMOSPHERE_RELEASE_TOWER.get(), "atmosphere_release_tower", false, "atmosphere_release_tower_bottom");
        recipeMachine(blockModels, UniverseBlocks.ECOLOGY_SEEDER.get(), "ecology_seeder", true, "ecology_seeder_bottom");
        TerraformDatagen.models(blockModels);
        // M20 星球改造（E）的模型加在这行上面
    }

    /** M18：黑雾透镜（平面物品）、全息信标（手写的投影台模型 models/block/holo_beacon.json，ADR-0033 §六、§七）。 */
    private static void m18Beacons(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        itemModels.generateFlatItem(UniverseItems.DARK_FOG_LENS.get(), ModelTemplates.FLAT_ITEM);
        Block beacon = UniverseBlocks.HOLO_BEACON.get();
        Identifier model = ModelLocationUtils.getModelLocation(beacon);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(beacon, BlockModelGenerators.plainVariant(model)));
        blockModels.registerSimpleItemModel(beacon, model);
    }

    /** 黑雾的掉落、弹药、投掷物（ADR-0028）：平面物品。 */
    private static void darkFogItems(ItemModelGenerators itemModels) {
        for (DeferredItem<?> item : List.of(UniverseItems.ENERGY_SHARD, UniverseItems.SILICON_NEURON, UniverseItems.MATTER_RECOMBINATOR,
                UniverseItems.NEGENTROPY_SINGULARITY, UniverseItems.CORE_ELEMENT, UniverseItems.DARK_FOG_MATRIX,
                UniverseItems.MAGNUM_AMMO_BOX, UniverseItems.TITANIUM_AMMO_BOX, UniverseItems.SUPERALLOY_AMMO_BOX,
                UniverseItems.SHELL_SET, UniverseItems.HIGH_EXPLOSIVE_SHELL_SET, UniverseItems.CRYSTAL_SHELL_SET,
                UniverseItems.MISSILE_SET, UniverseItems.SUPERSONIC_MISSILE_SET, UniverseItems.GRAVITY_MISSILE_SET,
                UniverseItems.JAMMING_CAPSULE, UniverseItems.EXPLOSIVE_UNIT, UniverseItems.CRYSTAL_EXPLOSIVE_UNIT)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
    }

    /**
     * M17 收尾的模型：酸液（子代理 A，ADR-0030）。贴图见 {@code tools/m17_acid_textures.py}。
     *
     * <p>液体方块和原版的水一样，方块模型只有一张粒子贴图（原版的 {@code block/water} 也是这样），真正的样子由
     * 流体渲染器按 {@code AcidClient} 注册的 FluidModel 画。桶分两层：桶壳不染色，里面的液面按当前星球的酸液色染。
     */
    private static void wrapUpAcid(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 没有这份模型，客户端加载资源时报 Missing block model
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(UniverseBlocks.ACID.get(),
                BlockModelGenerators.plainVariant(ModelTemplates.PARTICLE_ONLY.create(UniverseBlocks.ACID.get(),
                        TextureMapping.particle(texture("acid_still")), blockModels.modelOutput))));
        Item bucket = UniverseItems.ACID_BUCKET.get();
        Identifier model = ModelTemplates.TWO_LAYERED_ITEM.create(bucket,
                TextureMapping.layered(TextureMapping.getItemTexture(bucket), TextureMapping.getItemTexture(bucket, "_fluid")),
                itemModels.modelOutput);
        itemModels.itemModelOutput.accept(bucket, ItemModelUtils.tintedModel(model,
                ItemModelUtils.constantTint(-1), AcidClient.AcidItemTint.INSTANCE));
    }

    /** M17 收尾的模型：互通（子代理 C，ADR-0030）。 */
    private static void wrapUpInterop(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 贴图见 tools/m17_interop_textures.py：正面是接线排，工作时指示灯亮
        recipeMachine(blockModels, UniverseBlocks.GRID_INTERFACE.get(), "grid_interface", false, "machine_casing_top");
    }

    /** 黑雾基地的方块（子代理 P）。 */
    private static void darkFogBases(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        blockModels.createTrivialCube(UniverseBlocks.DARK_FOG_PLATING.get());
        blockModels.createTrivialCube(UniverseBlocks.DARK_FOG_CONDUIT.get());
        blockModels.createTrivialCube(UniverseBlocks.DARK_FOG_CORE_REMNANT.get());
    }

    /** 残影、战场分析基地（子代理 Q）。 */
    private static void darkFogFacilities(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 残影的样子由方块实体渲染器按原方块画，方块模型只给碎屑粒子用
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(UniverseBlocks.FACILITY_GHOST.get(),
                BlockModelGenerators.plainVariant(ModelTemplates.PARTICLE_ONLY.create(UniverseBlocks.FACILITY_GHOST.get(),
                        TextureMapping.particle(texture("facility_ghost")), blockModels.modelOutput))));
        recipeMachine(blockModels, UniverseBlocks.BATTLEFIELD_ANALYSIS_BASE.get(), "battlefield_analysis_base", true, "machine_casing_top");
    }

    /** 炮塔（子代理 R1）。 */
    private static void darkFogTurrets(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 模型由 tools/m16_turret_models.py 生成：底座是方块模型，转台和炮管由渲染器画，物品模型拼在一起
        for (Block block : List.of(UniverseBlocks.GAUSS_TURRET.get(), UniverseBlocks.LASER_TURRET.get(), UniverseBlocks.IMPLOSION_CANNON.get(),
                UniverseBlocks.MISSILE_TURRET.get())) {
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block,
                    BlockModelGenerators.plainVariant(Universe.id("block/" + name))));
            itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(Universe.id("block/" + name + "_item")));
        }
    }

    /** 等离子炮塔、近程等离子炮塔、两种胶囊（ADR-0028 §八，子代理 T）。 */
    private static void darkFogPlasma(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 模型由 tools/m16_plasma_models.py 生成，和别的炮塔一样分底座、转台、炮管
        for (Block block : List.of(UniverseBlocks.PLASMA_TURRET.get(), UniverseBlocks.SR_PLASMA_TURRET.get())) {
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block,
                    BlockModelGenerators.plainVariant(Universe.id("block/" + name))));
            itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(Universe.id("block/" + name + "_item")));
        }
        itemModels.generateFlatItem(UniverseItems.PLASMA_CAPSULE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.ANTIMATTER_CAPSULE.get(), ModelTemplates.FLAT_ITEM);
    }

    /** 行星护盾发生器（子代理 S）。 */
    private static void darkFogShield(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 手写的两截模型见 tools/m16_shield_models.py，穹顶展开时上半截的发射头亮
        tall(blockModels, itemModels, UniverseBlocks.PLANETARY_SHIELD_GENERATOR.get(), "planetary_shield_generator", false, true);
    }

    /** 信号塔、干扰塔（子代理 R2）。 */
    private static void darkFogSupport(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // 手写的两截模型见 tools/m16_support_models.py，工作时上半截亮
        tall(blockModels, itemModels, UniverseBlocks.SIGNAL_TOWER.get(), "signal_tower", false, true);
        tall(blockModels, itemModels, UniverseBlocks.JAMMER_TOWER.get(), "jammer_tower", false, true);
    }

    /**
     * 交易站、制造设施、观测站的方块（ADR-0026）：交易站的终端借空间站交易终端的贴图；加固门上下两半各一张贴图，
     * 四面一样；加固板是一张贴图的方块。
     */
    private static void outpostLandmarks(BlockModelGenerators blockModels) {
        Block trade = UniverseBlocks.OUTPOST_TRADE_TERMINAL.get();
        Identifier tradeModel = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(trade, new TextureMapping()
                .put(TextureSlot.FRONT, texture("trade_terminal_front"))
                .put(TextureSlot.SIDE, texture("trade_terminal_side"))
                .put(TextureSlot.TOP, texture("trade_terminal_top"))
                .put(TextureSlot.BOTTOM, texture("trade_terminal_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(trade, BlockModelGenerators.plainVariant(tradeModel))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(trade, tradeModel);
        for (Block block : List.of(UniverseBlocks.FACILITY_TERMINAL.get(), UniverseBlocks.OBSERVATORY_TERMINAL.get())) {
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
        blockModels.createTrivialCube(UniverseBlocks.REINFORCED_PLATING.get());
        Block door = UniverseBlocks.REINFORCED_DOOR.get();
        Identifier lower = ModelTemplates.CUBE_ALL.createWithSuffix(door, "_lower", TextureMapping.cube(texture("reinforced_door_lower")),
                blockModels.modelOutput);
        Identifier upper = ModelTemplates.CUBE_ALL.createWithSuffix(door, "_upper", TextureMapping.cube(texture("reinforced_door_upper")),
                blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(door).with(PropertyDispatch.initial(ReinforcedDoorBlock.HALF)
                .generate(half -> BlockModelGenerators.plainVariant(half == DoubleBlockHalf.LOWER ? lower : upper))));
        blockModels.registerSimpleItemModel(door, lower);
    }

    /** 知识石（ADR-0027 §五）：上下两半各一张侧面贴图，顶和底共用一张。 */
    private static void civLandmarks(BlockModelGenerators blockModels) {
        Block stone = UniverseBlocks.KNOWLEDGE_STONE.get();
        Identifier lower = ModelTemplates.CUBE_COLUMN.createWithSuffix(stone, "_lower",
                TextureMapping.column(texture("knowledge_stone_lower"), texture("knowledge_stone_top")), blockModels.modelOutput);
        Identifier upper = ModelTemplates.CUBE_COLUMN.createWithSuffix(stone, "_upper",
                TextureMapping.column(texture("knowledge_stone_upper"), texture("knowledge_stone_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(stone).with(PropertyDispatch.initial(KnowledgeStoneBlock.HALF)
                .generate(half -> BlockModelGenerators.plainVariant(half == DoubleBlockHalf.LOWER ? lower : upper))));
        blockModels.registerSimpleItemModel(stone, lower);
    }

    /** 野外的地标（ADR-0026）：化石土、雷击石是普通方块；打捞舱顶上是舱门，分关着和开着两种；化石和废料是平面物品。 */
    private static void wildLandmarks(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        blockModels.createTrivialCube(UniverseBlocks.FOSSIL_DIRT.get());
        blockModels.createTrivialCube(UniverseBlocks.FULGURITE.get());
        Block capsule = UniverseBlocks.SALVAGE_CAPSULE.get();
        Identifier[] capsules = new Identifier[2];
        for (int open = 0; open < 2; open++) {
            capsules[open] = ModelTemplates.CUBE_BOTTOM_TOP.createWithSuffix(capsule, open == 1 ? "_open" : "", new TextureMapping()
                    .put(TextureSlot.TOP, texture(open == 1 ? "salvage_capsule_top_open" : "salvage_capsule_top"))
                    .put(TextureSlot.BOTTOM, texture("salvage_capsule_bottom"))
                    .put(TextureSlot.SIDE, texture("salvage_capsule_side")), blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(capsule)
                .with(PropertyDispatch.initial(SalvageCapsuleBlock.OPEN).generate(opened -> BlockModelGenerators.plainVariant(capsules[opened ? 1 : 0]))));
        blockModels.registerSimpleItemModel(capsule, capsules[0]);
        for (DeferredItem<?> item : List.of(UniverseItems.SHELL_FOSSIL, UniverseItems.ARTHROPOD_FOSSIL, UniverseItems.SKULL_FOSSIL,
                UniverseItems.SALVAGED_SCRAP)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
    }

    /**
     * 生物卵和卵序列仪（ADR-0026 §九）：卵按孵化的进度换三张侧面贴图（完好、裂一道、裂两道），方块和物品都按卵的颜色染色；
     * 卵序列仪是一台配方设备的样子，干活时正面亮。
     */
    private static void eggs(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Block egg = UniverseBlocks.CREATURE_EGG.get();
        Identifier[] stages = new Identifier[CreatureEggBlock.HATCH.getPossibleValues().size()];
        for (int i = 0; i < stages.length; i++) {
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.SIDE, texture("creature_egg_side_" + i))
                    .put(TextureSlot.TOP, texture("creature_egg_top"));
            stages[i] = CREATURE_EGG.createWithSuffix(egg, "_" + i, textures, blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(egg)
                .with(PropertyDispatch.initial(CreatureEggBlock.HATCH).generate(stage -> BlockModelGenerators.plainVariant(stages[stage]))));
        itemModels.itemModelOutput.accept(UniverseItems.CREATURE_EGG.get(), ItemModelUtils.tintedModel(stages[0], EggClient.EggItemTint.INSTANCE));
        recipeMachine(blockModels, UniverseBlocks.EGG_SEQUENCER.get(), "egg_sequencer", false, "machine_casing_top");
    }

    /**
     * 水下（ADR-0026）：甲壳蚌有合着和撬开两种样子，嘴朝放的人；生鱼、熟鱼、活珍珠、刷怪蛋、潜艇是平面物品。
     * 烛光海带和海带囊跟着资源植物生成，两道菜跟着菜生成，海底密封箱和补给箱一起生成。
     */
    private static void underwater(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Block clam = UniverseBlocks.CLAM.get();
        TextureMapping textures = new TextureMapping()
                .put(TextureSlot.TOP, texture("clam_top"))
                .put(TextureSlot.SIDE, texture("clam_side"))
                .put(INNER, texture("clam_inner"));
        Identifier closed = CLAM.create(clam, textures, blockModels.modelOutput);
        Identifier open = CLAM_OPEN.createWithSuffix(clam, "_open", textures, blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(clam)
                .with(PropertyDispatch.initial(ClamBlock.OPEN).generate(opened -> BlockModelGenerators.plainVariant(opened ? open : closed)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(clam, closed);
        for (DeferredItem<?> item : List.of(UniverseItems.ALIEN_FISH, UniverseItems.COOKED_ALIEN_FISH, UniverseItems.LIVING_PEARL,
                UniverseItems.AQUATIC_CREATURE_SPAWN_EGG, UniverseItems.SUBMARINE)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
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
        for (Block sorter : List.of(UniverseBlocks.SORTER.get(), UniverseBlocks.SORTER_MK2.get(), UniverseBlocks.SORTER_MK3.get(),
                UniverseBlocks.PILE_SORTER.get())) {
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
        Block evolution = UniverseBlocks.SELF_EVOLUTION_LAB.get();
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(evolution,
                BlockModelGenerators.plainVariant(Universe.id("block/self_evolution_lab"))).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        itemModels.itemModelOutput.accept(evolution.asItem(), ItemModelUtils.plainModel(Universe.id("block/self_evolution_lab_item")));
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
        // 终局（ADR-0018）：人造恒星的模型由 tools/endgame_models.py 生成，上半截的恒星由方块实体渲染器画
        recipeMachine(blockModels, UniverseBlocks.MINI_FUSION_POWER_PLANT.get(), "mini_fusion_power_plant", false, "machine_casing_top");
        tall(blockModels, itemModels, UniverseBlocks.ARTIFICIAL_STAR.get(), "artificial_star", true, false);
        factoryDepth(blockModels, itemModels);
        mecha(blockModels, itemModels);
        for (DeferredItem<?> item : List.of(UniverseItems.ANTIMATTER, UniverseItems.ANNIHILATION_CONSTRAINT_SPHERE,
                UniverseItems.ANTIMATTER_FUEL_ROD, UniverseItems.COMPOSITE_MATRIX, UniverseItems.UNIVERSE_MATRIX, UniverseItems.BLUEPRINT,
                UniverseItems.GUIDE)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
    }

    /**
     * 工厂的补全（ADR-0022）：串在传送带中间的方块和两种塔的模型由 tools/m11_models.py 生成，一格的设备用和别的设备一样的模板。
     */
    private static void factoryDepth(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        for (Block block : List.of(UniverseBlocks.SPRAY_COATER.get(), UniverseBlocks.AUTOMATIC_PILER.get())) {
            Identifier model = ModelLocationUtils.getModelLocation(block);
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            itemModels.itemModelOutput.accept(block.asItem(), ItemModelUtils.plainModel(model));
        }
        // 流速监测器：报警时灯是红的
        Block monitor = UniverseBlocks.TRAFFIC_MONITOR.get();
        Identifier monitorModel = Universe.id("block/traffic_monitor");
        Identifier alarmModel = Universe.id("block/traffic_monitor_alarm");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(monitor)
                .with(PropertyDispatch.initial(TrafficMonitorBlock.ALARM).generate(alarm -> BlockModelGenerators.plainVariant(alarm ? alarmModel : monitorModel)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        itemModels.itemModelOutput.accept(monitor.asItem(), ItemModelUtils.plainModel(monitorModel));
        tall(blockModels, itemModels, UniverseBlocks.WIRELESS_POWER_TOWER.get(), "wireless_power_tower", false, true);
        tall(blockModels, itemModels, UniverseBlocks.SATELLITE_SUBSTATION.get(), "satellite_substation", false, true);
        recipeMachine(blockModels, UniverseBlocks.GEOTHERMAL_POWER_STATION.get(), "geothermal_power_station", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.ENERGY_EXCHANGER.get(), "energy_exchanger", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.NEGENTROPY_SMELTER.get(), "negentropy_smelter", true, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.RECOMPOSING_ASSEMBLER.get(), "recomposing_assembler", false, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.QUANTUM_CHEMICAL_PLANT.get(), "quantum_chemical_plant", false, "machine_casing_top");
        blockModels.createTrivialCube(UniverseBlocks.FOUNDATION.get());
        for (DeferredItem<?> item : List.of(UniverseItems.PROLIFERATOR_MK1, UniverseItems.PROLIFERATOR_MK2, UniverseItems.PROLIFERATOR_MK3,
                UniverseItems.COMBUSTIBLE_UNIT, UniverseItems.HYDROGEN_FUEL_ROD)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
    }

    /** 机甲和配送（ADR-0023）：模型由 tools/m12_models.py 生成；配送器上下左右对称，不按朝向转。 */
    private static void mecha(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Block distributor = UniverseBlocks.LOGISTICS_DISTRIBUTOR.get();
        Identifier off = Universe.id("block/logistics_distributor");
        Identifier on = Universe.id("block/logistics_distributor_on");
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(distributor)
                .with(PropertyDispatch.initial(MachineBlock.WORKING).generate(working -> BlockModelGenerators.plainVariant(working ? on : off))));
        itemModels.itemModelOutput.accept(distributor.asItem(), ItemModelUtils.plainModel(off));
        craft(itemModels, UniverseItems.LOGISTICS_BOT.get(), "logistics_bot_model");
        craft(itemModels, UniverseItems.CONSTRUCTION_DRONE.get(), "construction_drone_model");
    }

    /** 多功能工具：按类型选贴图（{@code item/multitool_<类型>}），手枪用 {@code item/multitool}。 */
    private static void multitool(ItemModelGenerators itemModels) {
        Item tool = UniverseItems.MULTITOOL.get();
        Identifier pistol = itemModels.createFlatItemModel(tool, ModelTemplates.FLAT_HANDHELD_ITEM);
        List<SelectItemModel.SwitchCase<MultitoolClass>> cases = new ArrayList<>();
        for (MultitoolClass type : MultitoolClass.values()) {
            if (type != MultitoolClass.PISTOL) {
                Identifier model = itemModels.createFlatItemModel(tool, "_" + type.key(), ModelTemplates.FLAT_HANDHELD_ITEM);
                cases.add(ItemModelUtils.when(type, ItemModelUtils.plainModel(model)));
            }
        }
        itemModels.itemModelOutput.accept(tool, ItemModelUtils.select(new MultitoolClassProperty(), ItemModelUtils.plainModel(pistol), cases));
    }

    /** 载具：界面里用平面图标，其余场合用 {@code models/block/} 下手写的立体模型。 */
    private static void craft(ItemModelGenerators itemModels, Item item, String model) {
        Identifier flat = itemModels.createFlatItemModel(item, ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.select(new DisplayContext(), ItemModelUtils.plainModel(Universe.id("block/" + model)),
                ItemModelUtils.when(ItemDisplayContext.GUI, ItemModelUtils.plainModel(flat))));
    }

    /**
     * 一格的设备：正面（和电弧熔炉的顶面）工作时换成发光的贴图；有 {@code <名字>_front_on_glow} 贴图的，
     * 正面再叠一层自发光，夜里灯和屏幕是亮的。顶面也亮、又有 {@code <名字>_top_on_glow} 的，顶面也叠一层。
     */
    private static void recipeMachine(BlockModelGenerators blockModels, Block block, String name, boolean topLit, String bottom) {
        Identifier[] models = new Identifier[2];
        for (int on = 0; on < 2; on++) {
            String suffix = on == 1 ? "_on" : "";
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.FRONT, texture(name + "_front" + suffix))
                    .put(TextureSlot.SIDE, texture(name + "_side"))
                    .put(TextureSlot.TOP, texture(name + "_top" + (topLit ? suffix : "")))
                    .put(TextureSlot.BOTTOM, texture(bottom));
            if (on == 1 && hasTexture(name + "_front_on_glow")) {
                textures.put(GLOW, texture(name + "_front_on_glow"));
                if (topLit && hasTexture(name + "_top_on_glow")) {
                    textures.put(GLOW_TOP, texture(name + "_top_on_glow"));
                    models[on] = MACHINE_GLOW_TOP.createWithSuffix(block, suffix, textures, blockModels.modelOutput);
                } else {
                    models[on] = MACHINE_GLOW.createWithSuffix(block, suffix, textures, blockModels.modelOutput);
                }
            } else {
                models[on] = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.createWithSuffix(block, suffix, textures, blockModels.modelOutput);
            }
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block)
                .with(PropertyDispatch.initial(MachineBlock.WORKING).generate(on -> BlockModelGenerators.plainVariant(models[on ? 1 : 0])))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(block, models[0]);
    }

    private static void resourcePlant(BlockModelGenerators blockModels, Block block) {
        Identifier ripe = ModelTemplates.CROSS.create(block, TextureMapping.cross(block), blockModels.modelOutput);
        Identifier bare = ModelTemplates.CROSS.createWithSuffix(block, "_harvested",
                TextureMapping.cross(TextureMapping.getBlockTexture(block, "_harvested")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(BlockModelGenerators.createBooleanModelDispatch(
                ResourcePlantBlock.RIPE, BlockModelGenerators.plainVariant(ripe), BlockModelGenerators.plainVariant(bare))));
    }

    /**
     * 不满一格的设备（手写的父模型）：水培槽是十格高的槽子，通电长着的时候正面补光灯亮，上面的植物由方块实体渲染器画；
     * 便携精炼机是十二格见方的箱子，干活时观察窗亮。
     */
    private static void smallMachine(BlockModelGenerators blockModels, Block block, ModelTemplate template, String name, String bottom) {
        Identifier[] models = new Identifier[2];
        for (int on = 0; on < 2; on++) {
            String suffix = on == 1 ? "_on" : "";
            TextureMapping textures = new TextureMapping()
                    .put(TextureSlot.FRONT, texture(name + "_front" + suffix))
                    .put(TextureSlot.SIDE, texture(name + "_side"))
                    .put(TextureSlot.TOP, texture(name + "_top"))
                    .put(TextureSlot.BOTTOM, texture(bottom));
            models[on] = template.createWithSuffix(block, suffix, textures, blockModels.modelOutput);
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
        for (Block block : new Block[] {UniverseBlocks.SHIP_HELM.get(), UniverseBlocks.DAMAGED_HELM.get(), UniverseBlocks.SHIP_SEAT.get(),
                UniverseBlocks.IMPULSE_ENGINE.get(),
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
        for (Block block : List.of(UniverseBlocks.DATA_TERMINAL.get(), UniverseBlocks.SUPPLY_CRATE.get(), UniverseBlocks.SEALED_CRATE.get(),
                UniverseBlocks.TRADE_TERMINAL.get(),
                UniverseBlocks.SHIP_REPAIR.get(), UniverseBlocks.MISSION_BOARD.get(), UniverseBlocks.SHIP_DEALER.get(),
                UniverseBlocks.MULTITOOL_CABINET.get(), UniverseBlocks.FREIGHTER_CONSOLE.get(), UniverseBlocks.FLEET_TERMINAL.get())) {
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
                UniverseBlocks.STATION_LIGHT.get(), UniverseBlocks.STATION_PAD.get(), UniverseBlocks.STATION_TRIM.get(),
                UniverseBlocks.STATION_SOLAR_PANEL.get(), UniverseBlocks.FREIGHTER_PLATE.get(), UniverseBlocks.FREIGHTER_ENGINE.get())) {
            blockModels.createTrivialCube(block);
        }
        for (Block block : List.of(UniverseBlocks.MONOLITH.get(), UniverseBlocks.TECH_CACHE.get(), UniverseBlocks.SMOLDERING_WRECK.get())) {
            blockModels.createTrivialBlock(block, TexturedModel.COLUMN);
        }
        // 守卫塔（ADR-0026）：外壳、灯带是整块；终端正面是屏幕
        blockModels.createTrivialCube(UniverseBlocks.SENTINEL_PILLAR_PLATING.get());
        blockModels.createTrivialCube(UniverseBlocks.SENTINEL_PILLAR_LIGHT.get());
        Block pillarTerminal = UniverseBlocks.SENTINEL_PILLAR_TERMINAL.get();
        Identifier pillarTerminalModel = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(pillarTerminal, new TextureMapping()
                .put(TextureSlot.FRONT, texture("sentinel_pillar_terminal_front"))
                .put(TextureSlot.SIDE, texture("sentinel_pillar_plating"))
                .put(TextureSlot.TOP, texture("sentinel_pillar_plating"))
                .put(TextureSlot.BOTTOM, texture("sentinel_pillar_plating")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(pillarTerminal, BlockModelGenerators.plainVariant(pillarTerminalModel))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(pillarTerminal, pillarTerminalModel);
        // 先驱者传送门（ADR-0024）：符文石按刻的是第几个符文换贴图；门开时环里那层光是半透明、会动的贴图
        blockModels.createTrivialCube(UniverseBlocks.PORTAL_STONE.get());
        blockModels.createTrivialBlock(UniverseBlocks.PORTAL_DIAL.get(), TexturedModel.COLUMN);
        Block glyphStone = UniverseBlocks.PORTAL_GLYPH.get();
        Identifier[] glyphModels = new Identifier[PortalAddress.GLYPHS];
        for (int i = 0; i < glyphModels.length; i++) {
            glyphModels[i] = ModelTemplates.CUBE_ALL.createWithSuffix(glyphStone, "_" + i, TextureMapping.cube(texture("portal_glyph_" + i)),
                    blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(glyphStone)
                .with(PropertyDispatch.initial(PortalGlyphBlock.GLYPH).generate(glyph -> BlockModelGenerators.plainVariant(glyphModels[glyph]))));
        blockModels.registerSimpleItemModel(glyphStone, glyphModels[0]);
        // 废弃货船里漏电的线管（ADR-0024）
        blockModels.createTrivialCube(UniverseBlocks.LIVE_CONDUIT.get());
        Block field = UniverseBlocks.PORTAL_FIELD.get();
        Identifier fieldModel = ModelTemplates.CUBE_ALL.create(field, TextureMapping.cube(texture("portal_field")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(field, BlockModelGenerators.plainVariant(fieldModel)));
        Block booster = UniverseBlocks.SIGNAL_BOOSTER.get();
        Identifier boosterModel = ModelLocationUtils.getModelLocation(booster);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(booster, BlockModelGenerators.plainVariant(boosterModel)));
        blockModels.registerSimpleItemModel(booster, boosterModel);
        // 基地和传送（ADR-0017）：传送器充满电时台面发亮；基地信标的模型是手写的
        recipeMachine(blockModels, UniverseBlocks.TELEPORTER.get(), "teleporter", true, "machine_casing_top");
        recipeMachine(blockModels, UniverseBlocks.STATION_TELEPORTER.get(), "station_teleporter", true, "station_hull");
        // 着陆台（ADR-0024）：加燃料时台面和侧面的灯亮
        recipeMachine(blockModels, UniverseBlocks.LANDING_PAD.get(), "landing_pad", true, "machine_casing_top");
        Block beacon = UniverseBlocks.BASE_BEACON.get();
        Identifier beaconModel = ModelLocationUtils.getModelLocation(beacon);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(beacon, BlockModelGenerators.plainVariant(beaconModel)));
        blockModels.registerSimpleItemModel(beacon, beaconModel);
        itemModels.generateFlatItem(UniverseItems.NAVIGATION_DATA.get(), ModelTemplates.FLAT_ITEM);
        // 守卫机（ADR-0014）
        itemModels.generateFlatItem(UniverseItems.SENTINEL_SCRAP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.PIRATE_SCRAP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.CREDIT_CHIP.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.EMERGENCY_RECEIVER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.EXOCRAFT.get(), ModelTemplates.FLAT_ITEM);
        // 悬浮车、重型车、机甲和载具召唤台（ADR-0026）：召唤台正面朝放的人，底面和顶面一样
        itemModels.generateFlatItem(UniverseItems.HOVER_CAR.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.HEAVY_ROVER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.EXO_MECH.get(), ModelTemplates.FLAT_ITEM);
        Block summoner = UniverseBlocks.VEHICLE_SUMMONER.get();
        Identifier summonerModel = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(summoner, new TextureMapping()
                .put(TextureSlot.FRONT, texture("vehicle_summoner_front"))
                .put(TextureSlot.SIDE, texture("vehicle_summoner_side"))
                .put(TextureSlot.TOP, texture("vehicle_summoner_top"))
                .put(TextureSlot.BOTTOM, texture("vehicle_summoner_top")), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(summoner, BlockModelGenerators.plainVariant(summonerModel))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(summoner, summonerModel);
        itemModels.generateFlatItem(UniverseItems.SENTINEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.HEAVY_SENTINEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        // 地面守卫机（ADR-0026）
        itemModels.generateFlatItem(UniverseItems.QUADRUPED_SERVO.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.WALKER_BRAIN.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.QUADRUPED_SENTINEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(UniverseItems.WALKER_SENTINEL_SPAWN_EGG.get(), ModelTemplates.FLAT_ITEM);
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
