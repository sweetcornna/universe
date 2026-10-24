package io.github.sweetcornna.universe.datagen;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.FossilFinds;
import io.github.sweetcornna.universe.fossil.BoneLootFunction;
import io.github.sweetcornna.universe.fossil.DormantSentryBlock;
import io.github.sweetcornna.universe.poi.WildStructures;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * M19 化石的数据生成（子代理 C，ADR-0034 §三）：模型、方块标签、战利品。配方写在 {@code UniverseRecipes}（要用它的研究配方）。
 *
 * <ul>
 *   <li>骨头和展品是平面物品。底座、骨架展台、沉睡的石哨兵是手写的模型（{@code models/block/} 下），展台按朝向转，
 *       石哨兵按"不安"的三级换贴图（眼缝和裂缝越来越亮）。</li>
 *   <li>石哨兵掉一到两块骨头，偏稀有的类群；发掘场的箱子里是营地的补给、几块化石和一块骨头。</li>
 * </ul>
 */
final class FossilData {
    private static final ModelTemplate DORMANT_SENTRY = new ModelTemplate(Optional.of(Universe.id("block/dormant_stone_sentry_template")),
            Optional.empty(), TextureSlot.FRONT, TextureSlot.SIDE, TextureSlot.TOP, TextureSlot.BOTTOM);

    private FossilData() {
    }

    // ---- 模型

    static void models(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        UniverseItems.BONES.values().forEach(item -> itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM));
        itemModels.generateFlatItem(UniverseItems.FOSSIL_EXHIBIT.get(), ModelTemplates.FLAT_ITEM);

        Block pedestal = UniverseBlocks.FOSSIL_PEDESTAL.get();
        Identifier pedestalModel = ModelLocationUtils.getModelLocation(pedestal);
        blockModels.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(pedestal, BlockModelGenerators.plainVariant(pedestalModel)));
        blockModels.registerSimpleItemModel(pedestal, pedestalModel);

        Block display = UniverseBlocks.SKELETON_DISPLAY.get();
        Identifier displayModel = ModelLocationUtils.getModelLocation(display);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(display, BlockModelGenerators.plainVariant(displayModel))
                .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blockModels.registerSimpleItemModel(display, displayModel);

        Block sentry = UniverseBlocks.DORMANT_STONE_SENTRY.get();
        Identifier[] stages = new Identifier[DormantSentryBlock.UNREST.getPossibleValues().size()];
        for (int i = 0; i < stages.length; i++) {
            stages[i] = DORMANT_SENTRY.createWithSuffix(sentry, "_" + i, new TextureMapping()
                    .put(TextureSlot.FRONT, texture("dormant_stone_sentry_front_" + i))
                    .put(TextureSlot.SIDE, texture("dormant_stone_sentry_side_" + i))
                    .put(TextureSlot.TOP, texture("dormant_stone_sentry_top"))
                    .put(TextureSlot.BOTTOM, texture("dormant_stone_sentry_bottom")), blockModels.modelOutput);
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(sentry)
                .with(PropertyDispatch.initial(DormantSentryBlock.UNREST).generate(stage -> BlockModelGenerators.plainVariant(stages[stage]))));
    }

    private static Material texture(String name) {
        return new Material(Universe.id("block/" + name));
    }

    // ---- 标签

    static void blockTags(Function<TagKey<Block>, TagAppender<Block>> tag) {
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.FOSSIL_PEDESTAL.key());
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.SKELETON_DISPLAY.key());
        tag.apply(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.DORMANT_STONE_SENTRY.key());
    }

    // ---- 战利品

    /** 化石土另外一池：不用精准采集时有 {@link FossilFinds#DIRT_CHANCE} 的机会多挖出一块骨头，类群按星球抽。 */
    static LootPool.Builder dirtBones(LootPool.Builder pool) {
        return pool.setRolls(ContextIntProviders.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance((float) FossilFinds.DIRT_CHANCE))
                .add(bone().apply(BoneLootFunction.dig()));
    }

    static void blockLoot(Consumer<Block> dropSelf) {
        dropSelf.accept(UniverseBlocks.FOSSIL_PEDESTAL.get());
        dropSelf.accept(UniverseBlocks.SKELETON_DISPLAY.get());
    }

    /** 石哨兵：一到两块骨头（偏稀有），几块燧石。自爆的不掉东西。 */
    static void entityLoot(Consumer<LootTable.Builder> sentry) {
        sentry.accept(LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(1, 2)).add(bone().apply(BoneLootFunction.sentry())))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(item(Items.FLINT, 0, 2, 1))));
    }

    /** 发掘场的箱子和桶：营地的吃的、氧气、纸和绳子，几块化石，一块骨头，有时一份导航数据。 */
    static void chestLoot(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> add) {
        add.accept(WildStructures.DIG_SITE_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.between(3, 5))
                        .add(item(UniverseItems.COOKED_ALIEN_MEAT.get(), 1, 3, 6))
                        .add(item(UniverseItems.OXYGEN_CAPSULE.get(), 1, 2, 6))
                        .add(item(Items.PAPER, 2, 5, 4))
                        .add(item(Items.STRING, 2, 4, 3))
                        .add(item(Items.TORCH, 2, 6, 4))
                        .add(item(Items.BRUSH, 1, 1, 2))
                        .add(item(UniverseItems.SHELL_FOSSIL.get(), 1, 3, 5))
                        .add(item(UniverseItems.ARTHROPOD_FOSSIL.get(), 1, 2, 3)))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(bone().apply(BoneLootFunction.dig())))
                .withPool(chance(item(UniverseItems.SKULL_FOSSIL.get(), 1, 1, 1), 0.25F))
                .withPool(chance(item(UniverseItems.NAVIGATION_DATA.get(), 1, 1, 1), 0.15F)));
    }

    /** 骨头的占位条目：真正给哪块由 {@link BoneLootFunction} 换。 */
    private static UniformContainerBase.Builder<?> bone() {
        return LootItem.lootTableItem(UniverseItems.BONES.get(Bone.all().getFirst()).get());
    }

    private static UniformContainerBase.Builder<?> item(ItemLike item, int min, int max, int weight) {
        return LootItem.lootTableItem(item).setWeight(weight).apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)));
    }

    private static LootPool.Builder chance(UniformContainerBase.Builder<?> entry, float probability) {
        return LootPool.lootPool().setRolls(ContextIntProviders.exactly(1)).add(entry).when(LootItemRandomChanceCondition.randomChance(probability));
    }
}
