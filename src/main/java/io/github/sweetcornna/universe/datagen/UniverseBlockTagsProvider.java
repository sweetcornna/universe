package io.github.sweetcornna.universe.datagen;

import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/** 方块标签：挖掘工具、阻挡移动等。 */
final class UniverseBlockTagsProvider extends BlockTagsProvider {
    UniverseBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                UniverseBlocks.PLANET_ROCK.key(),
                UniverseBlocks.PLANET_COBBLESTONE.key(),
                UniverseBlocks.PLANET_ICE.key(),
                UniverseBlocks.PLANET_CRYSTAL.key(),
                UniverseBlocks.PLANET_STRATA.key());
        this.tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key(),
                UniverseBlocks.PLANET_REGOLITH.key(),
                UniverseBlocks.PLANET_SAND.key(),
                UniverseBlocks.PLANET_SNOW.key(),
                UniverseBlocks.PLANET_ASH.key());
        // 草皮、星壤能种树、长植物
        this.tag(BlockTags.DIRT).add(
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key());
        this.tag(BlockTags.SAND).add(UniverseBlocks.PLANET_SAND.key());
        this.tag(BlockTags.ICE).add(UniverseBlocks.PLANET_ICE.key());
        this.tag(BlockTags.BASE_STONE_OVERWORLD).add(UniverseBlocks.PLANET_ROCK.key());
        // 原版矿石地物认这个标签，星岩里才能长矿
        this.tag(BlockTags.STONE_ORE_REPLACEABLES).add(UniverseBlocks.PLANET_ROCK.key(), UniverseBlocks.PLANET_STRATA.key());
        // 26.x 起"阻挡移动"改由标签决定：高度图（生物刷新、落点）、窒息、传送落点、挡住流体都看它。
        // 实心方块不加进来，高度图会直接穿过整片地形
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.PLANET_ROCK.key(),
                UniverseBlocks.PLANET_COBBLESTONE.key(),
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key(),
                UniverseBlocks.PLANET_REGOLITH.key(),
                UniverseBlocks.PLANET_SAND.key(),
                UniverseBlocks.PLANET_SNOW.key(),
                UniverseBlocks.PLANET_ICE.key(),
                UniverseBlocks.PLANET_ASH.key(),
                UniverseBlocks.PLANET_STRATA.key(),
                UniverseBlocks.PLANET_CRYSTAL.key(),
                UniverseBlocks.PLANET_LOG.key(),
                UniverseBlocks.PLANET_CAP.key(),
                UniverseBlocks.PLANET_BLOOM.key());
        // 树叶进 leaves 标签，原版的 blocks_motion 会把它算进去（高度图分有叶子、没叶子两种）
        this.tag(BlockTags.LEAVES).add(UniverseBlocks.PLANET_LEAVES.key());
        this.tag(BlockTags.LOGS).add(UniverseBlocks.PLANET_LOG.key());
        this.tag(BlockTags.MINEABLE_WITH_AXE).add(UniverseBlocks.PLANET_LOG.key(), UniverseBlocks.PLANET_CAP.key());
        this.tag(BlockTags.MINEABLE_WITH_HOE).add(UniverseBlocks.PLANET_LEAVES.key(), UniverseBlocks.PLANET_BLOOM.key());
        for (var block : UniverseBlocks.PLANTS.values()) {
            this.tag(BlockTags.REPLACEABLE).add(block.key());
            this.tag(BlockTags.SWORD_EFFICIENT).add(block.key());
            this.tag(BlockTags.REPLACEABLE_BY_TREES).add(block.key());
        }
        // 星球植物能长在土、沙、雪、风化层、灰烬上，地衣和晶花还能长在岩石上
        this.tag(FloraBlocks.PLANT_SOIL)
                .addTag(BlockTags.DIRT)
                .addTag(BlockTags.SAND)
                .addTag(BlockTags.SUPPORTS_VEGETATION)
                .add(UniverseBlocks.PLANET_REGOLITH.key(),
                        UniverseBlocks.PLANET_SNOW.key(),
                        UniverseBlocks.PLANET_ASH.key(),
                        UniverseBlocks.PLANET_ROCK.key(),
                        UniverseBlocks.PLANET_STRATA.key(),
                        UniverseBlocks.PLANET_CRYSTAL.key());
    }
}
