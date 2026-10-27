package io.github.sweetcornna.universe.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.recipe.ResearchShapedRecipe;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.terraform.Greening;
import io.github.sweetcornna.universe.terraform.TerraformBlocks;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * M20 星球改造的数据生成（子代理 E，ADR-0036 §六）：轨道反射镜和地衣的模型（大气释放塔、生态播种站用
 * {@code UniverseModelProvider} 的 {@code recipeMachine}）、三样设备的掉落、标签和配方，以及绿化认的岩石和土。
 * 配方都要研究"星球改造"。
 */
final class TerraformDatagen {
    /** 贴地的一层（{@code assets/universe/models/block/lichen_cover.json}）。 */
    private static final ModelTemplate LICHEN_COVER = new ModelTemplate(Optional.of(Universe.id("block/lichen_cover")), Optional.empty(),
            TextureSlot.TEXTURE);

    private TerraformDatagen() {
    }

    private static List<Block> blocks() {
        return List.of(UniverseBlocks.ATMOSPHERE_RELEASE_TOWER.get(), UniverseBlocks.ECOLOGY_SEEDER.get(), UniverseBlocks.ORBITAL_MIRROR.get());
    }

    // ---- 模型

    /** 轨道反射镜：半格高的镜板，顶面是镜面或遮阳面，四周是框架，底面是背衬和桁架。 */
    static void models(BlockModelGenerators blockModels) {
        Block mirror = UniverseBlocks.ORBITAL_MIRROR.get();
        TextureMapping mirrorTextures = new TextureMapping()
                .put(TextureSlot.TOP, texture("orbital_mirror_top"))
                .put(TextureSlot.SIDE, texture("orbital_mirror_side"))
                .put(TextureSlot.BOTTOM, texture("orbital_mirror_bottom"));
        TextureMapping shadeTextures = new TextureMapping()
                .put(TextureSlot.TOP, texture("orbital_mirror_shade_top"))
                .put(TextureSlot.SIDE, texture("orbital_mirror_side"))
                .put(TextureSlot.BOTTOM, texture("orbital_mirror_bottom"));
        Identifier mirrorModel = ModelTemplates.SLAB_BOTTOM.create(mirror, mirrorTextures, blockModels.modelOutput);
        Identifier shadeModel = ModelTemplates.SLAB_BOTTOM.createWithSuffix(mirror, "_shade", shadeTextures, blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(mirror)
                .with(PropertyDispatch.initial(TerraformBlocks.OrbitalMirror.SHADE, MachineBlock.WORKING)
                        .generate((shade, working) -> BlockModelGenerators.plainVariant(shade ? shadeModel : mirrorModel)))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(mirror, mirrorModel);
        lichen(blockModels, UniverseBlocks.GRAY_LICHEN.get(), "gray_lichen");
        lichen(blockModels, UniverseBlocks.ORANGE_LICHEN.get(), "orange_lichen");
    }

    /** 地衣：贴地的一片，四个朝向随机转，铺开来看不出重复。没有物品。 */
    private static void lichen(BlockModelGenerators blockModels, Block block, String name) {
        Identifier model = LICHEN_COVER.create(block, new TextureMapping().put(TextureSlot.TEXTURE, texture(name)), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block,
                BlockModelGenerators.createRotatedVariants(BlockModelGenerators.plainModel(model))));
    }

    private static Material texture(String name) {
        return new Material(Universe.id("block/" + name));
    }

    // ---- 掉落、标签

    static void blockLoot(Consumer<Block> dropSelf) {
        blocks().forEach(dropSelf);
    }

    /** 和别的工业设备一样：镐子挖，不会被组装进飞船。 */
    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        for (Block block : blocks()) {
            ResourceKey<Block> key = block.builtInRegistryHolder().key();
            tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(key);
            tag.apply(ShipParts.EXCLUDED).add(key);
        }
        tag.apply(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(UniverseBlocks.ATMOSPHERE_RELEASE_TOWER.get().builtInRegistryHolder().key(),
                UniverseBlocks.ECOLOGY_SEEDER.get().builtInRegistryHolder().key());
        // 地衣和苔藓垫一样用锄头挖
        tag.apply(BlockTags.MINEABLE_WITH_HOE).add(UniverseBlocks.GRAY_LICHEN.get().builtInRegistryHolder().key(),
                UniverseBlocks.ORANGE_LICHEN.get().builtInRegistryHolder().key());
        tag.apply(Greening.ROCK).add(key(UniverseBlocks.PLANET_ROCK.get()), key(UniverseBlocks.PLANET_COBBLESTONE.get()),
                        key(UniverseBlocks.PLANET_STRATA.get()), key(UniverseBlocks.PLANET_REGOLITH.get()), key(UniverseBlocks.PLANET_ASH.get()),
                        key(Blocks.COBBLESTONE), key(Blocks.GRAVEL))
                .addTag(BlockTags.BASE_STONE_OVERWORLD);
        tag.apply(Greening.SOIL).add(key(UniverseBlocks.PLANET_SOIL.get()), key(UniverseBlocks.PLANET_SAND.get()), key(Blocks.DIRT),
                key(Blocks.COARSE_DIRT), key(Blocks.ROOTED_DIRT), key(Blocks.SAND), key(Blocks.RED_SAND));
    }

    private static ResourceKey<Block> key(Block block) {
        return block.builtInRegistryHolder().key();
    }

    // ---- 配方

    static void recipes(RecipeOutput output) {
        // 释放塔：两台压缩机（电机）把气体打上去，钛合金的塔身和喷口格栅，处理器管着阀门
        shaped(output, UniverseItems.ATMOSPHERE_RELEASE_TOWER.get(), List.of("TGT", "MPM", "TST"),
                Map.of('T', UniverseItems.TITANIUM_ALLOY.get(), 'G', Items.IRON_BARS, 'M', UniverseItems.ELECTRIC_MOTOR.get(),
                        'P', UniverseItems.PROCESSOR.get(), 'S', UniverseItems.STEEL.get()));
        // 反射镜：六片太阳帆的薄膜绷在两块框架材料上，中间一台推进器保持位置（光压会把镜子推走）
        shaped(output, UniverseItems.ORBITAL_MIRROR.get(), List.of("SSS", "FTF", "SSS"),
                Map.of('S', UniverseItems.SOLAR_SAIL.get(), 'F', UniverseItems.FRAME_MATERIAL.get(), 'T', UniverseItems.THRUSTER.get()));
        // 播种站：玻璃罩下是培养基里的菌，电机带喷头的水泵，处理器管着保温和播种
        shaped(output, UniverseItems.ECOLOGY_SEEDER.get(), List.of("GCG", "TMT", "SPS"),
                Map.of('G', Items.GLASS, 'C', UniverseItems.CULTURE_MEDIUM.get(), 'T', UniverseItems.TITANIUM_ALLOY.get(),
                        'M', UniverseItems.ELECTRIC_MOTOR.get(), 'S', UniverseItems.STEEL.get(), 'P', UniverseItems.PROCESSOR.get()));
    }

    /** 要研究"星球改造"的有序合成，配方 ID 用产物的名字。 */
    private static void shaped(RecipeOutput output, ItemLike result, List<String> pattern, Map<Character, ? extends ItemLike> key) {
        Map<Character, Ingredient> ingredients = new LinkedHashMap<>();
        new TreeMap<Character, ItemLike>(key).forEach((symbol, item) -> ingredients.put(symbol, Ingredient.of(item)));
        ResearchShapedRecipe recipe = new ResearchShapedRecipe(RecipeBuilder.createCraftingCommonInfo(true),
                RecipeBuilder.createCraftingBookInfo(RecipeCategory.REDSTONE, null), ShapedRecipePattern.of(ingredients, pattern),
                new ItemStackTemplate(result.asItem(), 1), "terraforming");
        output.accept(RecipeBuilder.getDefaultRecipeId(recipe.result()), recipe, null);
    }
}
