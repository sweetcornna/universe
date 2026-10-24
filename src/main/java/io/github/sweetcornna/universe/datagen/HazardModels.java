package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.hazard.GeyserKind;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** M19 的模型：间歇泉的喷口和结壳（子代理 E，ADR-0034 §六）。 */
final class HazardModels {
    private HazardModels() {
    }

    static void add(BlockModelGenerators blockModels) {
        for (GeyserKind kind : GeyserKind.values()) {
            String name = kind.id() + "_geyser";
            Block crust = UniverseBlocks.GEYSER_CRUSTS.get(kind).get();
            blockModels.createTrivialCube(crust);
            // 喷口：顶上是泉眼，四面是一层层的结壳，底下和结壳一样
            Block vent = UniverseBlocks.GEYSER_VENTS.get(kind).get();
            Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(vent, new TextureMapping()
                    .put(TextureSlot.TOP, new Material(Universe.id("block/" + name + "_vent_top")))
                    .put(TextureSlot.SIDE, new Material(Universe.id("block/" + name + "_vent_side")))
                    .put(TextureSlot.BOTTOM, new Material(Universe.id("block/" + name + "_crust"))), blockModels.modelOutput);
            blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(vent, BlockModelGenerators.plainVariant(model)));
            blockModels.registerSimpleItemModel(vent, model);
        }
    }
}
