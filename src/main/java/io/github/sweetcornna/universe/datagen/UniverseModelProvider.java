package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
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
import net.minecraft.world.level.block.Block;
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
                UniverseItems.ALIEN_CREATURE_SPAWN_EGG, UniverseItems.ALIEN_FLYER_SPAWN_EGG, UniverseItems.LAUNCH_FUEL)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
        ships(blockModels);
        ores(blockModels, itemModels);
        for (DeferredItem<?> item : List.of(UniverseItems.EXOSUIT_HELMET, UniverseItems.EXOSUIT_CHESTPLATE, UniverseItems.EXOSUIT_LEGGINGS,
                UniverseItems.EXOSUIT_BOOTS, UniverseItems.OXYGEN_CAPSULE, UniverseItems.HAZARD_CELL)) {
            itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
        }
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

    /** 飞船部件：驾驶台、推进器、脉冲引擎、座椅的模型是手写的（{@code models/block/}），这里只出方块状态。 */
    private static void ships(BlockModelGenerators blockModels) {
        for (Block block : new Block[] {UniverseBlocks.SHIP_HELM.get(), UniverseBlocks.SHIP_SEAT.get(), UniverseBlocks.IMPULSE_ENGINE.get()}) {
            Identifier model = ModelLocationUtils.getModelLocation(block);
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model))
                    .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
            blockModels.registerSimpleItemModel(block, model);
        }
        Block thruster = UniverseBlocks.SHIP_THRUSTER.get();
        Identifier thrusterModel = ModelLocationUtils.getModelLocation(thruster);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(thruster, BlockModelGenerators.plainVariant(thrusterModel))
                .with(BlockModelGenerators.ROTATION_FACING));
        blockModels.registerSimpleItemModel(thruster, thrusterModel);
        blockModels.createTrivialBlock(UniverseBlocks.FUEL_TANK.get(), TexturedModel.COLUMN);
        UniverseBlocks.HULL_PLATING.values().forEach(block -> blockModels.createTrivialCube(block.get()));
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
