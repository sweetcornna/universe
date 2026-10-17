package io.github.sweetcornna.universe.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.survival.Exosuit;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.survival.SuitRefillItem;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.equipment.ArmorType;
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

    // ---- 飞船
    public static final DeferredItem<BlockItem> SHIP_HELM = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_HELM);
    public static final DeferredItem<BlockItem> SHIP_THRUSTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_THRUSTER);
    public static final DeferredItem<BlockItem> FUEL_TANK = ITEMS.registerSimpleBlockItem(UniverseBlocks.FUEL_TANK);
    public static final DeferredItem<BlockItem> IMPULSE_ENGINE = ITEMS.registerSimpleBlockItem(UniverseBlocks.IMPULSE_ENGINE);
    public static final DeferredItem<BlockItem> SHIP_SEAT = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_SEAT);
    public static final Map<DyeColor, DeferredItem<BlockItem>> HULL_PLATING = new EnumMap<>(DyeColor.class);

    static {
        UniverseBlocks.HULL_PLATING.forEach((color, block) -> HULL_PLATING.put(color, ITEMS.registerSimpleBlockItem(block)));
    }

    /** 发射燃料：一罐能加满半个燃料舱。 */
    public static final DeferredItem<Item> LAUNCH_FUEL = ITEMS.registerSimpleItem("launch_fuel", properties -> properties.stacksTo(16));

    // ---- 外骨骼
    public static final DeferredItem<Item> EXOSUIT_HELMET = ITEMS.registerSimpleItem("exosuit_helmet",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.HELMET));
    public static final DeferredItem<Item> EXOSUIT_CHESTPLATE = ITEMS.registerSimpleItem("exosuit_chestplate",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> EXOSUIT_LEGGINGS = ITEMS.registerSimpleItem("exosuit_leggings",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.LEGGINGS));
    public static final DeferredItem<Item> EXOSUIT_BOOTS = ITEMS.registerSimpleItem("exosuit_boots",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.BOOTS));
    public static final DeferredItem<SuitRefillItem> OXYGEN_CAPSULE = ITEMS.registerItem("oxygen_capsule",
            properties -> new SuitRefillItem(SuitRefillItem.Kind.OXYGEN, properties), properties -> properties.stacksTo(16));
    public static final DeferredItem<SuitRefillItem> HAZARD_CELL = ITEMS.registerItem("hazard_cell",
            properties -> new SuitRefillItem(SuitRefillItem.Kind.HAZARD, properties), properties -> properties.stacksTo(16));

    // ---- 多功能工具
    public static final DeferredItem<MultitoolItem> MULTITOOL = ITEMS.registerItem("multitool", MultitoolItem::new,
            properties -> properties.stacksTo(1).component(DataComponents.TOOL, multitoolRules()));
    /** 坠毁的探测器里找到的，修好才能用。 */
    public static final DeferredItem<Item> DAMAGED_MULTITOOL = ITEMS.registerSimpleItem("damaged_multitool", properties -> properties.stacksTo(1));

    /** 左键挖掘：镐、铲、斧能挖的都能挖，速度和铁镐一样，钻石级的矿挖不下来。 */
    private static Tool multitoolRules() {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        return new Tool(List.of(
                Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_IRON_TOOL)),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), 6.0F),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), 6.0F),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), 6.0F)), 1.0F, 0, true);
    }

    // ---- 星球矿石和资源
    public static final Map<OreKind, DeferredItem<BlockItem>> ORES = new EnumMap<>(OreKind.class);
    /** 矿石掉的本模组物品（粗矿、硫、晶体……）；铁、铜、煤掉原版的，不在这里。 */
    public static final Map<OreKind, DeferredItem<Item>> ORE_DROPS = new EnumMap<>(OreKind.class);
    /** 粗矿烧出来的锭。 */
    public static final Map<OreKind, DeferredItem<Item>> ORE_SMELTED = new EnumMap<>(OreKind.class);

    static {
        UniverseBlocks.ORES.forEach((kind, block) -> ORES.put(kind, ITEMS.registerSimpleBlockItem(block)));
        for (OreKind kind : OreKind.values()) {
            if (!kind.vanillaDrop()) {
                ORE_DROPS.put(kind, ITEMS.registerSimpleItem(kind.drop()));
            }
            if (kind.smelted() != null) {
                ORE_SMELTED.put(kind, ITEMS.registerSimpleItem(kind.smelted()));
            }
        }
    }

    /** 矿石掉的物品。 */
    public static Item oreDrop(OreKind kind) {
        return kind.vanillaDrop() ? BuiltInRegistries.ITEM.getValue(Identifier.parse(kind.drop())) : ORE_DROPS.get(kind).get();
    }

    /** 创造模式物品栏里的顺序。 */
    public static final List<DeferredItem<?>> CREATIVE_ORDER = creativeOrder();

    private static List<DeferredItem<?>> creativeOrder() {
        List<DeferredItem<?>> order = new ArrayList<>(List.of(
                PLANET_ROCK, PLANET_COBBLESTONE, PLANET_SOIL, PLANET_TURF, PLANET_REGOLITH, PLANET_SAND,
                PLANET_SNOW, PLANET_ICE, PLANET_ASH, PLANET_STRATA, PLANET_CRYSTAL,
                PLANET_LOG, PLANET_LEAVES, PLANET_CAP, PLANET_BLOOM));
        order.addAll(PLANTS.values());
        order.addAll(List.of(ALIEN_MEAT, COOKED_ALIEN_MEAT, ALIEN_HIDE, ALIEN_CREATURE_SPAWN_EGG, ALIEN_FLYER_SPAWN_EGG));
        order.addAll(List.of(SHIP_HELM, SHIP_THRUSTER, FUEL_TANK, IMPULSE_ENGINE, SHIP_SEAT, LAUNCH_FUEL));
        order.addAll(HULL_PLATING.values());
        order.addAll(List.of(EXOSUIT_HELMET, EXOSUIT_CHESTPLATE, EXOSUIT_LEGGINGS, EXOSUIT_BOOTS, OXYGEN_CAPSULE, HAZARD_CELL,
                MULTITOOL, DAMAGED_MULTITOOL));
        order.addAll(ORES.values());
        order.addAll(ORE_DROPS.values());
        order.addAll(ORE_SMELTED.values());
        return List.copyOf(order);
    }

    private UniverseItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
