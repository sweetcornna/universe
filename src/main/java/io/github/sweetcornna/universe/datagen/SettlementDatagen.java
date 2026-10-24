package io.github.sweetcornna.universe.datagen;

import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.settlement.Cost;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;

/**
 * M19 聚落的数据生成（子代理 B，ADR-0034 §二）：聚落终端和炮台的模型，建设物资的三个物品标签。两种方块都没有物品、挖不掉。
 *
 * <p>建设物资：石料是各种石头、圆石、石砖和异星的岩石；木料是木板和原木；玻璃是各种玻璃方块。
 */
final class SettlementDatagen {
    private SettlementDatagen() {
    }

    private static TagKey<Item> tag(String id) {
        return TagKey.create(Registries.ITEM, Identifier.parse(id.startsWith("#") ? id.substring(1) : id));
    }

    private static ResourceKey<Item> key(ItemLike item) {
        return BuiltInRegistries.ITEM.getResourceKey(item.asItem()).orElseThrow();
    }

    // ---- 模型

    static void models(BlockModelGenerators blockModels) {
        Block terminal = UniverseBlocks.SETTLEMENT_TERMINAL.get();
        Identifier model = ModelTemplates.CUBE_ORIENTABLE_TOP_BOTTOM.create(terminal, new TextureMapping()
                .put(TextureSlot.FRONT, new Material(Universe.id("block/settlement_terminal_front")))
                .put(TextureSlot.SIDE, new Material(Universe.id("block/settlement_terminal_side")))
                .put(TextureSlot.TOP, new Material(Universe.id("block/settlement_terminal_top")))
                .put(TextureSlot.BOTTOM, new Material(Universe.id("block/settlement_terminal_top"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(terminal, BlockModelGenerators.plainVariant(model))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        // 炮台：手写的模型 models/block/settlement_turret.json（底座、炮塔、两根炮管、观察罩），炮口朝 -z（北）
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(UniverseBlocks.SETTLEMENT_TURRET.get(),
                BlockModelGenerators.plainVariant(Universe.id("block/settlement_turret"))).with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
    }

    // ---- 标签

    static void itemTags(Function<TagKey<Item>, TagAppender<Item>> tag) {
        tag.apply(tag(Cost.STONE)).addTag(Tags.BlockItems.COBBLESTONES.item()).addTag(Tags.BlockItems.STONES.item()).addTag(ItemTags.STONE_BRICKS)
                .addTag(Tags.BlockItems.SANDSTONE_BLOCKS.item())
                .add(key(Items.BLACKSTONE), key(Items.POLISHED_BLACKSTONE_BRICKS), key(Items.DEEPSLATE_BRICKS), key(Items.MUD_BRICKS),
                        key(Items.BRICKS), UniverseItems.PLANET_ROCK.key(), UniverseItems.PLANET_COBBLESTONE.key(), UniverseItems.PLANET_STRATA.key());
        tag.apply(tag(Cost.TIMBER)).addTag(ItemTags.PLANKS).addTag(ItemTags.LOGS);
        tag.apply(tag(Cost.GLASS)).addTag(Tags.BlockItems.GLASS_BLOCKS.item()).add(UniverseItems.STATION_GLASS.key());
    }

    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        for (var block : java.util.List.of(UniverseBlocks.SETTLEMENT_TERMINAL, UniverseBlocks.SETTLEMENT_TURRET)) {
            tag.apply(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(block.key());
            tag.apply(ShipParts.EXCLUDED).add(block.key());
        }
    }
}
