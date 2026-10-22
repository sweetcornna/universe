package io.github.sweetcornna.universe.datagen;

import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.poi.RandomModuleFunction;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.core.Holder;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SmeltItemFunction;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * 动物掉落：生肉（着火时是熟肉）和兽皮。大个子另外多掉的肉在实体里按体型加。
 * 守卫机掉残骸；重型守卫机掉得多，还有机会掉导航数据和一个高一档的模块（ADR-0014）。
 */
final class UniverseEntityLoot extends EntityLootSubProvider {
    UniverseEntityLoot(LootTableSubProvider.Context context) {
        super(FeatureFlags.REGISTRY.allFlags(), context);
    }

    @Override
    public void generate() {
        this.add(UniverseEntities.ALIEN_CREATURE.get(), LootTable.lootTable()
                .withPool(this.meat(1, 2))
                .withPool(LootPool.lootPool()
                        .setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.ALIEN_HIDE.get())
                                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(0, 2)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments,
                                        ContextFloatProviders.between(0.0F, 1.0F))))));
        this.add(UniverseEntities.ALIEN_FLYER.get(), LootTable.lootTable().withPool(this.meat(0, 1)));
        // 水生动物（ADR-0026）：一条生鱼（着火时是熟的），大个子另外多掉的在实体里按体型加；水母没有肉，掉生物胶质
        this.add(UniverseEntities.AQUATIC_CREATURE.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.ALIEN_FISH.get())
                                .apply(SmeltItemFunction.smelted().when(this.shouldSmeltLoot()))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments,
                                        ContextFloatProviders.between(0.0F, 1.0F))))));
        this.add(UniverseEntities.AQUATIC_CREATURE.get(), AquaticCreature.JELLY_LOOT, LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.BIO_GEL.get())
                                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 2)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments,
                                        ContextFloatProviders.between(0.0F, 1.0F))))));
        this.add(UniverseEntities.SENTINEL.get(), LootTable.lootTable().withPool(this.scrap(1, 2)));
        this.add(UniverseEntities.HEAVY_SENTINEL.get(), LootTable.lootTable()
                .withPool(this.scrap(3, 5))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.NAVIGATION_DATA.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.35F)))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.WEAPON_CALIBRATOR).get())
                                .apply(RandomModuleFunction.randomModule(1)))
                        .when(LootItemRandomChanceCondition.randomChance(0.3F))));
        // 太空守卫机的拦截机：守卫机残骸，偶尔有导航数据、飞船模块（ADR-0024）
        this.add(UniverseEntities.SENTINEL_INTERCEPTOR.get(), LootTable.lootTable()
                .withPool(this.scrap(2, 4))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.NAVIGATION_DATA.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.25F)))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.MODULES.get(ModuleType.FIRE_CONTROL).get())
                                .apply(RandomModuleFunction.randomShipModule(0)))
                        .when(LootItemRandomChanceCondition.randomChance(0.12F))));
        // 海盗战机：海盗残骸，偶尔有导航数据（ADR-0017）
        this.add(UniverseEntities.PIRATE_FIGHTER.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.PIRATE_SCRAP.get())
                                .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 3)))))
                .withPool(LootPool.lootPool().setRolls(ContextIntProviders.exactly(1))
                        .add(LootItem.lootTableItem(UniverseItems.NAVIGATION_DATA.get()))
                        .when(LootItemRandomChanceCondition.randomChance(0.2F))));
    }

    private LootPool.Builder scrap(int min, int max) {
        return LootPool.lootPool()
                .setRolls(ContextIntProviders.exactly(1))
                .add(LootItem.lootTableItem(UniverseItems.SENTINEL_SCRAP.get())
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))
                        .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments,
                                ContextFloatProviders.between(0.0F, 1.0F))));
    }

    private LootPool.Builder meat(int min, int max) {
        return LootPool.lootPool()
                .setRolls(ContextIntProviders.exactly(1))
                .add(LootItem.lootTableItem(UniverseItems.ALIEN_MEAT.get())
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(min, max)))
                        .apply(SmeltItemFunction.smelted().when(this.shouldSmeltLoot()))
                        .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments,
                                ContextFloatProviders.between(0.0F, 1.0F))));
    }

    /** 只生成本模组的实体。 */
    @Override
    protected java.util.stream.Stream<EntityType<?>> getKnownEntityTypes() {
        return UniverseEntities.ENTITIES.getEntries().stream().map(Holder::value);
    }
}
