package io.github.sweetcornna.universe.datagen;

import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.PlanetTints;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** 方块状态、方块模型和物品模型。 */
final class UniverseModelProvider extends ModelProvider {
    /** 六个面都带染色索引 0 的立方体（{@code assets/universe/models/block/tinted_cube_all.json}）。 */
    private static final ModelTemplate TINTED_CUBE_ALL =
            new ModelTemplate(Optional.of(Universe.id("block/tinted_cube_all")), Optional.empty(), TextureSlot.ALL);
    private static final TextureSlot OVERLAY = TextureSlot.create("overlay");
    /** 草皮：顶面和侧面上沿染索引 0（地表色），其余染索引 1（土壤色）。 */
    private static final ModelTemplate TINTED_TURF = new ModelTemplate(
            Optional.of(Universe.id("block/tinted_turf")), Optional.empty(), TextureSlot.TOP, TextureSlot.BOTTOM, OVERLAY);

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
    }

    private static Material texture(String name) {
        return new Material(Universe.id("block/" + name));
    }
}
