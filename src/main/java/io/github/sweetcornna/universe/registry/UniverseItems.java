package io.github.sweetcornna.universe.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 物品注册。方块物品和方块同名。 */
public final class UniverseItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Universe.MOD_ID);

    public static final DeferredItem<BlockItem> PLANET_ROCK = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_ROCK);
    public static final DeferredItem<BlockItem> PLANET_COBBLESTONE = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_COBBLESTONE);
    public static final DeferredItem<BlockItem> PLANET_SOIL = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_SOIL);
    public static final DeferredItem<BlockItem> PLANET_TURF = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_TURF);
    public static final DeferredItem<BlockItem> PLANET_REGOLITH = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_REGOLITH);
    public static final DeferredItem<BlockItem> PLANET_SAND = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_SAND);
    public static final DeferredItem<BlockItem> PLANET_SNOW = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_SNOW);
    public static final DeferredItem<BlockItem> PLANET_ICE = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_ICE);
    public static final DeferredItem<BlockItem> PLANET_ASH = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_ASH);
    public static final DeferredItem<BlockItem> PLANET_CRYSTAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_CRYSTAL);
    public static final DeferredItem<BlockItem> PLANET_STRATA = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_STRATA);

    public static final Map<PlantForm, DeferredItem<BlockItem>> PLANTS = new EnumMap<>(PlantForm.class);

    static {
        UniverseBlocks.PLANTS.forEach((form, block) -> PLANTS.put(form, ITEMS.registerSimpleBlockItem(block)));
    }

    public static final DeferredItem<BlockItem> PLANET_LOG = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_LOG);
    public static final DeferredItem<BlockItem> PLANET_LEAVES = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_LEAVES);
    public static final DeferredItem<BlockItem> PLANET_CAP = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_CAP);
    public static final DeferredItem<BlockItem> PLANET_BLOOM = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_BLOOM);

    /** 异星动物的生肉：和牛肉差不多。 */
    public static final DeferredItem<Item> ALIEN_MEAT = ITEMS.registerSimpleItem("alien_meat",
            properties -> properties.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));
    public static final DeferredItem<Item> COOKED_ALIEN_MEAT = ITEMS.registerSimpleItem("cooked_alien_meat",
            properties -> properties.food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.75F).build()));
    /** 异星兽皮：可以鞣成皮革。 */
    public static final DeferredItem<Item> ALIEN_HIDE = ITEMS.registerSimpleItem("alien_hide");
    /** 刷怪蛋：按脚下的群系挑一种本星球的动物。 */
    public static final DeferredItem<SpawnEggItem> ALIEN_CREATURE_SPAWN_EGG = ITEMS.registerItem("alien_creature_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.ALIEN_CREATURE.get()));
    public static final DeferredItem<SpawnEggItem> ALIEN_FLYER_SPAWN_EGG = ITEMS.registerItem("alien_flyer_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.ALIEN_FLYER.get()));

    /** 创造模式物品栏里的顺序。 */
    public static final List<DeferredItem<?>> CREATIVE_ORDER = creativeOrder();

    private static List<DeferredItem<?>> creativeOrder() {
        List<DeferredItem<?>> order = new ArrayList<>(List.of(
                PLANET_ROCK, PLANET_COBBLESTONE, PLANET_SOIL, PLANET_TURF, PLANET_REGOLITH, PLANET_SAND,
                PLANET_SNOW, PLANET_ICE, PLANET_ASH, PLANET_STRATA, PLANET_CRYSTAL,
                PLANET_LOG, PLANET_LEAVES, PLANET_CAP, PLANET_BLOOM));
        order.addAll(PLANTS.values());
        order.addAll(List.of(ALIEN_MEAT, COOKED_ALIEN_MEAT, ALIEN_HIDE, ALIEN_CREATURE_SPAWN_EGG, ALIEN_FLYER_SPAWN_EGG));
        return List.copyOf(order);
    }

    private UniverseItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
