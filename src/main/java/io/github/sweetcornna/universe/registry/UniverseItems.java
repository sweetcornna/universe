package io.github.sweetcornna.universe.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.exocraft.ExocraftItem;
import io.github.sweetcornna.universe.factory.belt.StorageTankItem;
import io.github.sweetcornna.universe.logistics.StationItem;
import io.github.sweetcornna.universe.survival.Exosuit;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.survival.SuitRefillItem;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
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
    // ---- 喂养（ADR-0017）：两种饲料，喂过的动物产出的三种东西
    /** 草食饲料：星球植物压成的，温顺、易受惊的动物吃。 */
    public static final DeferredItem<Item> HERBIVORE_FEED = ITEMS.registerSimpleItem("herbivore_feed");
    /** 肉食饲料：生肉拌骨粉，有领地意识的和捕食者吃。 */
    public static final DeferredItem<Item> CARNIVORE_FEED = ITEMS.registerSimpleItem("carnivore_feed");
    /** 异星蛋：两足兽、飞兽和长甲片的四足兽下的，烤了能吃。 */
    public static final DeferredItem<Item> ALIEN_EGG = ITEMS.registerSimpleItem("alien_egg", properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> COOKED_ALIEN_EGG = ITEMS.registerSimpleItem("cooked_alien_egg",
            properties -> properties.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.7F).build()));
    /** 异星绒毛：四足兽身上的，能搓成线、压成羊毛。 */
    public static final DeferredItem<Item> ALIEN_FLUFF = ITEMS.registerSimpleItem("alien_fluff");
    /** 生物胶质：六足虫、节蠕虫分泌的，当黏液球用，在化工厂里做有机晶体。 */
    public static final DeferredItem<Item> BIO_GEL = ITEMS.registerSimpleItem("bio_gel");
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
    public static final DeferredItem<BlockItem> WARP_DRIVE = ITEMS.registerSimpleBlockItem(UniverseBlocks.WARP_DRIVE);
    public static final DeferredItem<BlockItem> SHIP_SEAT = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_SEAT);
    public static final DeferredItem<BlockItem> PHOTON_CANNON = ITEMS.registerSimpleBlockItem(UniverseBlocks.PHOTON_CANNON);
    public static final DeferredItem<BlockItem> MINING_BEAM = ITEMS.registerSimpleBlockItem(UniverseBlocks.MINING_BEAM);
    public static final DeferredItem<BlockItem> SHIELD_GENERATOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIELD_GENERATOR);
    // Mk.II 部件只能在空间站买、在坠毁飞船里拆到，名字用稀有色
    public static final DeferredItem<BlockItem> SHIP_THRUSTER_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_THRUSTER_MK2,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> FUEL_TANK_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.FUEL_TANK_MK2,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> IMPULSE_ENGINE_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.IMPULSE_ENGINE_MK2,
            p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> WARP_DRIVE_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.WARP_DRIVE_MK2,
            p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> ALLOY_HULL_PLATING = ITEMS.registerSimpleBlockItem(UniverseBlocks.ALLOY_HULL_PLATING,
            p -> p.rarity(Rarity.UNCOMMON));
    public static final Map<DyeColor, DeferredItem<BlockItem>> HULL_PLATING = new EnumMap<>(DyeColor.class);

    static {
        UniverseBlocks.HULL_PLATING.forEach((color, block) -> HULL_PLATING.put(color, ITEMS.registerSimpleBlockItem(block)));
    }

    /** 发射燃料：一罐能加满半个燃料舱。 */
    public static final DeferredItem<Item> LAUNCH_FUEL = ITEMS.registerSimpleItem("launch_fuel", properties -> properties.stacksTo(16));
    /** 空间翘曲器：跃迁一次消耗一个（ADR-0012）。 */
    public static final DeferredItem<Item> SPACE_WARPER = ITEMS.registerSimpleItem("space_warper");
    /** 勘探数据：第一个发现恒星系、星球、物种、矿物的玩家拿到，上交给部分科技（ADR-0012）。 */
    public static final DeferredItem<Item> SURVEY_DATA = ITEMS.registerSimpleItem("survey_data");

    /** 导航数据：信号增强器定位兴趣点要消耗一份（ADR-0014）。 */
    public static final DeferredItem<Item> NAVIGATION_DATA = ITEMS.registerSimpleItem("navigation_data");
    /** 守卫机残骸：击毁守卫机掉落，用来做 C 级技术模块（ADR-0014）。 */
    public static final DeferredItem<Item> SENTINEL_SCRAP = ITEMS.registerSimpleItem("sentinel_scrap");
    /** 海盗残骸：打下海盗战机掉的，空间站收（ADR-0017）。 */
    public static final DeferredItem<Item> PIRATE_SCRAP = ITEMS.registerSimpleItem("pirate_scrap");
    public static final DeferredItem<SpawnEggItem> SENTINEL_SPAWN_EGG = ITEMS.registerItem("sentinel_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.SENTINEL.get()));
    public static final DeferredItem<SpawnEggItem> HEAVY_SENTINEL_SPAWN_EGG = ITEMS.registerItem("heavy_sentinel_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.HEAVY_SENTINEL.get()));
    // ---- 兴趣点的方块（ADR-0014）
    public static final DeferredItem<BlockItem> SIGNAL_BOOSTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SIGNAL_BOOSTER);
    public static final DeferredItem<BlockItem> SUPPLY_CRATE = ITEMS.registerSimpleBlockItem(UniverseBlocks.SUPPLY_CRATE);
    public static final DeferredItem<BlockItem> DROP_POD = ITEMS.registerSimpleBlockItem(UniverseBlocks.DROP_POD);
    public static final DeferredItem<BlockItem> DATA_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.DATA_TERMINAL);
    public static final DeferredItem<BlockItem> MONOLITH = ITEMS.registerSimpleBlockItem(UniverseBlocks.MONOLITH);
    public static final DeferredItem<BlockItem> TECH_CACHE = ITEMS.registerSimpleBlockItem(UniverseBlocks.TECH_CACHE);
    public static final DeferredItem<BlockItem> SMOLDERING_WRECK = ITEMS.registerSimpleBlockItem(UniverseBlocks.SMOLDERING_WRECK);
    // ---- 空间站（ADR-0017）
    public static final DeferredItem<BlockItem> TRADE_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.TRADE_TERMINAL);
    public static final DeferredItem<BlockItem> STATION_HULL = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_HULL);
    public static final DeferredItem<BlockItem> STATION_FLOOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_FLOOR);
    public static final DeferredItem<BlockItem> STATION_GLASS = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_GLASS);
    public static final DeferredItem<BlockItem> STATION_LIGHT = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_LIGHT);
    public static final DeferredItem<BlockItem> STATION_PAD = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_PAD);
    public static final DeferredItem<BlockItem> STATION_TRIM = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_TRIM);
    public static final DeferredItem<BlockItem> SHIP_REPAIR = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_REPAIR);

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
    /** 探索车（ADR-0017）：对着地面用就放下一辆。 */
    public static final DeferredItem<ExocraftItem> EXOCRAFT = ITEMS.registerItem("exocraft", ExocraftItem::new, properties -> properties.stacksTo(1));
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

    // ---- 工业（ADR-0011）
    public static final DeferredItem<BlockItem> TESLA_TOWER = ITEMS.registerSimpleBlockItem(UniverseBlocks.TESLA_TOWER);
    public static final DeferredItem<BlockItem> WIND_TURBINE = ITEMS.registerSimpleBlockItem(UniverseBlocks.WIND_TURBINE);
    public static final DeferredItem<BlockItem> SOLAR_PANEL = ITEMS.registerSimpleBlockItem(UniverseBlocks.SOLAR_PANEL);
    public static final DeferredItem<BlockItem> THERMAL_PLANT = ITEMS.registerSimpleBlockItem(UniverseBlocks.THERMAL_PLANT);
    public static final DeferredItem<BlockItem> ACCUMULATOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.ACCUMULATOR);
    public static final DeferredItem<BlockItem> ARC_SMELTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.ARC_SMELTER);
    public static final DeferredItem<BlockItem> ASSEMBLER = ITEMS.registerSimpleBlockItem(UniverseBlocks.ASSEMBLER);
    public static final DeferredItem<BlockItem> OIL_REFINERY = ITEMS.registerSimpleBlockItem(UniverseBlocks.OIL_REFINERY);
    public static final DeferredItem<BlockItem> MATRIX_LAB = ITEMS.registerSimpleBlockItem(UniverseBlocks.MATRIX_LAB);
    public static final DeferredItem<BlockItem> MINING_MACHINE = ITEMS.registerSimpleBlockItem(UniverseBlocks.MINING_MACHINE);
    public static final DeferredItem<BlockItem> WATER_PUMP = ITEMS.registerSimpleBlockItem(UniverseBlocks.WATER_PUMP);
    public static final DeferredItem<BlockItem> OIL_EXTRACTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.OIL_EXTRACTOR);
    public static final DeferredItem<BlockItem> CRUDE_OIL_SEEP = ITEMS.registerSimpleBlockItem(UniverseBlocks.CRUDE_OIL_SEEP);
    public static final DeferredItem<BlockItem> CONVEYOR_BELT = ITEMS.registerSimpleBlockItem(UniverseBlocks.CONVEYOR_BELT);
    public static final DeferredItem<BlockItem> SORTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SORTER);
    public static final DeferredItem<BlockItem> STORAGE_DEPOT = ITEMS.registerSimpleBlockItem(UniverseBlocks.STORAGE_DEPOT);
    // ---- 物流（ADR-0015）
    public static final DeferredItem<BlockItem> CONVEYOR_BELT_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.CONVEYOR_BELT_MK2);
    public static final DeferredItem<BlockItem> CONVEYOR_BELT_MK3 = ITEMS.registerSimpleBlockItem(UniverseBlocks.CONVEYOR_BELT_MK3);
    public static final DeferredItem<BlockItem> SORTER_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.SORTER_MK2);
    public static final DeferredItem<BlockItem> SORTER_MK3 = ITEMS.registerSimpleBlockItem(UniverseBlocks.SORTER_MK3);
    public static final DeferredItem<BlockItem> SPLITTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SPLITTER);
    public static final DeferredItem<StorageTankItem> STORAGE_TANK = ITEMS.registerItem("storage_tank",
            properties -> new StorageTankItem(UniverseBlocks.STORAGE_TANK.get(), properties), () -> new Item.Properties().useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> ASSEMBLER_MK2 = ITEMS.registerSimpleBlockItem(UniverseBlocks.ASSEMBLER_MK2);
    public static final DeferredItem<BlockItem> ASSEMBLER_MK3 = ITEMS.registerSimpleBlockItem(UniverseBlocks.ASSEMBLER_MK3);
    public static final DeferredItem<BlockItem> PLANE_SMELTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANE_SMELTER);
    public static final DeferredItem<BlockItem> CHEMICAL_PLANT = ITEMS.registerSimpleBlockItem(UniverseBlocks.CHEMICAL_PLANT);
    public static final DeferredItem<BlockItem> FRACTIONATOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.FRACTIONATOR);
    public static final DeferredItem<BlockItem> ADVANCED_MINING_MACHINE = ITEMS.registerSimpleBlockItem(UniverseBlocks.ADVANCED_MINING_MACHINE);
    public static final DeferredItem<BlockItem> FACTORY_ANCHOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.FACTORY_ANCHOR);
    public static final DeferredItem<StationItem> PLANETARY_LOGISTICS_STATION = ITEMS.registerItem("planetary_logistics_station",
            properties -> new StationItem(UniverseBlocks.PLANETARY_LOGISTICS_STATION.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());
    public static final DeferredItem<StationItem> INTERSTELLAR_LOGISTICS_STATION = ITEMS.registerItem("interstellar_logistics_station",
            properties -> new StationItem(UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix());
    // 戴森球的设备（ADR-0016）
    public static final DeferredItem<BlockItem> EM_RAIL_EJECTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.EM_RAIL_EJECTOR);
    public static final DeferredItem<BlockItem> VERTICAL_LAUNCHING_SILO = ITEMS.registerSimpleBlockItem(UniverseBlocks.VERTICAL_LAUNCHING_SILO);
    public static final DeferredItem<BlockItem> RAY_RECEIVER = ITEMS.registerSimpleBlockItem(UniverseBlocks.RAY_RECEIVER);
    public static final DeferredItem<BlockItem> MINIATURE_PARTICLE_COLLIDER = ITEMS.registerSimpleBlockItem(
            UniverseBlocks.MINIATURE_PARTICLE_COLLIDER);
    public static final DeferredItem<StationItem> ORBITAL_COLLECTOR = ITEMS.registerItem("orbital_collector",
            properties -> new StationItem(UniverseBlocks.ORBITAL_COLLECTOR.get(), properties), () -> new Item.Properties().useBlockDescriptionPrefix());

    /** 中间产品（戴森球计划的电磁阶段）。 */
    public static final DeferredItem<Item> GEAR = ITEMS.registerSimpleItem("gear");
    public static final DeferredItem<Item> MAGNETIC_COIL = ITEMS.registerSimpleItem("magnetic_coil");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> ELECTRIC_MOTOR = ITEMS.registerSimpleItem("electric_motor");
    public static final DeferredItem<Item> STEEL = ITEMS.registerSimpleItem("steel");
    public static final DeferredItem<Item> PRISM = ITEMS.registerSimpleItem("prism");
    public static final DeferredItem<Item> PLASMA_EXCITER = ITEMS.registerSimpleItem("plasma_exciter");
    /** 高能石墨：两块煤烧一块，也是好燃料（戴森球计划 6.3 MJ，折合燃烧时间 3700 刻）。 */
    public static final DeferredItem<Item> ENERGETIC_GRAPHITE = ITEMS.registerSimpleItem("energetic_graphite", properties -> fuel(properties, 3700));
    /** 液体按物品处理，能放上传送带（ADR-0011）。 */
    public static final DeferredItem<Item> WATER = ITEMS.registerSimpleItem("water");
    public static final DeferredItem<Item> CRUDE_OIL = ITEMS.registerSimpleItem("crude_oil");
    public static final DeferredItem<Item> REFINED_OIL = ITEMS.registerSimpleItem("refined_oil", properties -> fuel(properties, 2600));
    public static final DeferredItem<Item> HYDROGEN = ITEMS.registerSimpleItem("hydrogen", properties -> fuel(properties, 4700));
    /** 重氢：分馏塔从氢里分出来，轨道采集器从气态巨行星采（ADR-0015）。 */
    public static final DeferredItem<Item> DEUTERIUM = ITEMS.registerSimpleItem("deuterium");
    /** 矩阵：研究站消耗它们推进科技树。 */
    public static final DeferredItem<Item> ELECTROMAGNETIC_MATRIX = ITEMS.registerSimpleItem("electromagnetic_matrix");
    public static final DeferredItem<Item> ENERGY_MATRIX = ITEMS.registerSimpleItem("energy_matrix");
    /** 中期的中间产品和矩阵（ADR-0015），配方照戴森球计划。有机晶体是 M2 就有的矿石掉落，这里不另加。 */
    public static final DeferredItem<Item> CRYSTAL_SILICON = ITEMS.registerSimpleItem("crystal_silicon");
    public static final DeferredItem<Item> TITANIUM_ALLOY = ITEMS.registerSimpleItem("titanium_alloy");
    public static final DeferredItem<Item> SYNTHETIC_DIAMOND = ITEMS.registerSimpleItem("synthetic_diamond");
    public static final DeferredItem<Item> PLASTIC = ITEMS.registerSimpleItem("plastic");
    public static final DeferredItem<Item> SULFURIC_ACID = ITEMS.registerSimpleItem("sulfuric_acid");
    public static final DeferredItem<Item> GRAPHENE = ITEMS.registerSimpleItem("graphene");
    public static final DeferredItem<Item> CARBON_NANOTUBE = ITEMS.registerSimpleItem("carbon_nanotube");
    public static final DeferredItem<Item> TITANIUM_CRYSTAL = ITEMS.registerSimpleItem("titanium_crystal");
    public static final DeferredItem<Item> MICROCRYSTALLINE_COMPONENT = ITEMS.registerSimpleItem("microcrystalline_component");
    public static final DeferredItem<Item> PROCESSOR = ITEMS.registerSimpleItem("processor");
    public static final DeferredItem<Item> PARTICLE_BROADBAND = ITEMS.registerSimpleItem("particle_broadband");
    public static final DeferredItem<Item> THRUSTER = ITEMS.registerSimpleItem("thruster");
    public static final DeferredItem<Item> REINFORCED_THRUSTER = ITEMS.registerSimpleItem("reinforced_thruster");
    public static final DeferredItem<Item> ELECTROMAGNETIC_TURBINE = ITEMS.registerSimpleItem("electromagnetic_turbine");
    public static final DeferredItem<Item> SUPER_MAGNETIC_RING = ITEMS.registerSimpleItem("super_magnetic_ring");
    public static final DeferredItem<Item> PARTICLE_CONTAINER = ITEMS.registerSimpleItem("particle_container");
    public static final DeferredItem<Item> LOGISTICS_DRONE = ITEMS.registerSimpleItem("logistics_drone");
    public static final DeferredItem<Item> LOGISTICS_VESSEL = ITEMS.registerSimpleItem("logistics_vessel");
    public static final DeferredItem<Item> STRUCTURE_MATRIX = ITEMS.registerSimpleItem("structure_matrix");
    public static final DeferredItem<Item> INFORMATION_MATRIX = ITEMS.registerSimpleItem("information_matrix");
    /** 戴森球阶段的物品（ADR-0016），配方照戴森球计划。 */
    public static final DeferredItem<Item> PHOTON_COMBINER = ITEMS.registerSimpleItem("photon_combiner");
    public static final DeferredItem<Item> SOLAR_SAIL = ITEMS.registerSimpleItem("solar_sail");
    public static final DeferredItem<Item> FRAME_MATERIAL = ITEMS.registerSimpleItem("frame_material");
    public static final DeferredItem<Item> DYSON_SPHERE_COMPONENT = ITEMS.registerSimpleItem("dyson_sphere_component");
    public static final DeferredItem<Item> SMALL_CARRIER_ROCKET = ITEMS.registerSimpleItem("small_carrier_rocket", properties -> properties.stacksTo(20));
    public static final DeferredItem<Item> DEUTERON_FUEL_ROD = ITEMS.registerSimpleItem("deuteron_fuel_rod", properties -> properties.stacksTo(32));
    public static final DeferredItem<Item> TITANIUM_GLASS = ITEMS.registerSimpleItem("titanium_glass");
    public static final DeferredItem<Item> CASIMIR_CRYSTAL = ITEMS.registerSimpleItem("casimir_crystal");
    public static final DeferredItem<Item> PLANE_FILTER = ITEMS.registerSimpleItem("plane_filter");
    public static final DeferredItem<Item> QUANTUM_CHIP = ITEMS.registerSimpleItem("quantum_chip");
    /** 奇异物质：就是黑洞附近星球上奇异物质矿掉的那种，微型粒子对撞机也能造。 */
    public static final DeferredItem<Item> STRANGE_MATTER = ORE_DROPS.get(OreKind.STRANGE_MATTER);
    public static final DeferredItem<Item> GRAVITON_LENS = ITEMS.registerSimpleItem("graviton_lens");
    public static final DeferredItem<Item> CRITICAL_PHOTON = ITEMS.registerSimpleItem("critical_photon");
    public static final DeferredItem<Item> GRAVITY_MATRIX = ITEMS.registerSimpleItem("gravity_matrix");

    /** 能进火力发电厂和熔炉的燃料，燃烧时间按原版的刻数。 */
    private static Item.Properties fuel(Item.Properties properties, int burnTime) {
        return properties.component(DataComponents.COOKING_FUEL,
                new CookingFuel(new ResolvableInt.Constant(burnTime), new ResolvableFloat.Constant(1.0F)));
    }

    /** 创造模式物品栏里的顺序。 */
    // 技术模块（ADR-0014）：每种一个物品，品级和数值在数据组件里
    public static final Map<ModuleType, DeferredItem<TechModuleItem>> MODULES = new EnumMap<>(ModuleType.class);

    static {
        for (ModuleType type : ModuleType.values()) {
            MODULES.put(type, ITEMS.registerItem(type.itemName(), properties -> new TechModuleItem(type, properties)));
        }
    }

    public static final List<DeferredItem<?>> CREATIVE_ORDER = creativeOrder();

    private static List<DeferredItem<?>> creativeOrder() {
        List<DeferredItem<?>> order = new ArrayList<>(List.of(
                PLANET_ROCK, PLANET_COBBLESTONE, PLANET_SOIL, PLANET_TURF, PLANET_REGOLITH, PLANET_SAND,
                PLANET_SNOW, PLANET_ICE, PLANET_ASH, PLANET_STRATA, PLANET_CRYSTAL,
                PLANET_LOG, PLANET_LEAVES, PLANET_CAP, PLANET_BLOOM));
        order.addAll(PLANTS.values());
        order.addAll(List.of(ALIEN_MEAT, COOKED_ALIEN_MEAT, ALIEN_HIDE, HERBIVORE_FEED, CARNIVORE_FEED, ALIEN_EGG, COOKED_ALIEN_EGG,
                ALIEN_FLUFF, BIO_GEL, ALIEN_CREATURE_SPAWN_EGG, ALIEN_FLYER_SPAWN_EGG));
        order.addAll(List.of(SHIP_HELM, SHIP_THRUSTER, FUEL_TANK, IMPULSE_ENGINE, WARP_DRIVE, SHIP_SEAT, PHOTON_CANNON, MINING_BEAM,
                SHIELD_GENERATOR, SHIP_THRUSTER_MK2, FUEL_TANK_MK2, IMPULSE_ENGINE_MK2, WARP_DRIVE_MK2, ALLOY_HULL_PLATING,
                LAUNCH_FUEL, SPACE_WARPER, SURVEY_DATA, EXOCRAFT));
        order.addAll(HULL_PLATING.values());
        order.addAll(List.of(EXOSUIT_HELMET, EXOSUIT_CHESTPLATE, EXOSUIT_LEGGINGS, EXOSUIT_BOOTS, OXYGEN_CAPSULE, HAZARD_CELL,
                MULTITOOL, DAMAGED_MULTITOOL));
        order.addAll(MODULES.values());
        order.addAll(List.of(NAVIGATION_DATA, SIGNAL_BOOSTER, SUPPLY_CRATE, DROP_POD, DATA_TERMINAL, MONOLITH, TECH_CACHE, SMOLDERING_WRECK));
        order.addAll(List.of(TRADE_TERMINAL, SHIP_REPAIR, STATION_HULL, STATION_FLOOR, STATION_GLASS, STATION_LIGHT, STATION_PAD, STATION_TRIM));
        order.addAll(List.of(SENTINEL_SCRAP, SENTINEL_SPAWN_EGG, HEAVY_SENTINEL_SPAWN_EGG, PIRATE_SCRAP));
        order.addAll(ORES.values());
        order.addAll(ORE_DROPS.values());
        order.addAll(ORE_SMELTED.values());
        order.addAll(List.of(TESLA_TOWER, WIND_TURBINE, SOLAR_PANEL, THERMAL_PLANT, ACCUMULATOR, MINING_MACHINE, WATER_PUMP, OIL_EXTRACTOR,
                ARC_SMELTER, ASSEMBLER, OIL_REFINERY, MATRIX_LAB, CONVEYOR_BELT, CONVEYOR_BELT_MK2, CONVEYOR_BELT_MK3,
                SORTER, SORTER_MK2, SORTER_MK3, SPLITTER, STORAGE_DEPOT, STORAGE_TANK, CRUDE_OIL_SEEP,
                ASSEMBLER_MK2, ASSEMBLER_MK3, PLANE_SMELTER, CHEMICAL_PLANT, FRACTIONATOR, ADVANCED_MINING_MACHINE, FACTORY_ANCHOR,
                PLANETARY_LOGISTICS_STATION, INTERSTELLAR_LOGISTICS_STATION, ORBITAL_COLLECTOR,
                EM_RAIL_EJECTOR, VERTICAL_LAUNCHING_SILO, RAY_RECEIVER, MINIATURE_PARTICLE_COLLIDER,
                GEAR, MAGNETIC_COIL, CIRCUIT_BOARD, ELECTRIC_MOTOR, STEEL, PRISM, PLASMA_EXCITER, ENERGETIC_GRAPHITE,
                WATER, CRUDE_OIL, REFINED_OIL, HYDROGEN, DEUTERIUM, ELECTROMAGNETIC_MATRIX, ENERGY_MATRIX,
                CRYSTAL_SILICON, TITANIUM_ALLOY, SYNTHETIC_DIAMOND, PLASTIC, SULFURIC_ACID, GRAPHENE, CARBON_NANOTUBE, TITANIUM_CRYSTAL, MICROCRYSTALLINE_COMPONENT, PROCESSOR,
                PARTICLE_BROADBAND, THRUSTER, REINFORCED_THRUSTER, ELECTROMAGNETIC_TURBINE, SUPER_MAGNETIC_RING, PARTICLE_CONTAINER, LOGISTICS_DRONE, LOGISTICS_VESSEL, STRUCTURE_MATRIX, INFORMATION_MATRIX,
                PHOTON_COMBINER, SOLAR_SAIL, FRAME_MATERIAL, DYSON_SPHERE_COMPONENT, SMALL_CARRIER_ROCKET, DEUTERON_FUEL_ROD, TITANIUM_GLASS,
                CASIMIR_CRYSTAL, PLANE_FILTER, QUANTUM_CHIP, GRAVITON_LENS, CRITICAL_PHOTON, GRAVITY_MATRIX));
        return List.copyOf(order);
    }

    private UniverseItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
