package io.github.sweetcornna.universe.registry;

import io.github.sweetcornna.universe.darkfog.turret.AmmoItem;
import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.CreatureEggItem;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.blueprint.BlueprintItem;
import io.github.sweetcornna.universe.darkfog.defense.ThrowableUnitItem;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.survival.Dish;
import io.github.sweetcornna.universe.encounter.CreditChipItem;
import io.github.sweetcornna.universe.encounter.EmergencyReceiverItem;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.exocraft.ExocraftItem;
import io.github.sweetcornna.universe.exocraft.SubmarineItem;
import io.github.sweetcornna.universe.factory.belt.StorageTankItem;
import io.github.sweetcornna.universe.guide.GuideItem;
import io.github.sweetcornna.universe.logistics.StationItem;
import io.github.sweetcornna.universe.survival.DishItem;
import io.github.sweetcornna.universe.survival.Exosuit;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.survival.ResourceItem;
import io.github.sweetcornna.universe.survival.SuitRefillItem;
import io.github.sweetcornna.universe.tech.DriveItem;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import io.github.sweetcornna.universe.tech.ToolWeaponItem;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
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
import net.minecraft.world.item.component.Consumables;
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
    /** 一个氧气补多少氧气（满 100）。 */
    public static final float OXYGEN_AMOUNT = 10;
    /** 一个钠给每条防护补多少（满 100）。 */
    public static final float SODIUM_AMOUNT = 8;
    /** 一个海带气囊补多少氧气：水下就地补给，比氧气植物多一半。 */
    public static final float KELP_POD_AMOUNT = 15;
    /** 一支防护凝胶给它那一条防护补多少（满 100）。 */
    public static final float GEL_AMOUNT = 40;

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
        // 星球上的植物和原木能烧（营养处理器、便携精炼机开局就靠它们，ADR-0026），和原版的枯灌木、原木一样
        UniverseBlocks.PLANTS.forEach((form, block) -> PLANTS.put(form, ITEMS.registerSimpleBlockItem(block, properties -> fuel(properties, 100))));
    }

    public static final DeferredItem<BlockItem> PLANET_LOG = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANET_LOG,
            properties -> fuel(properties, 300));
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
    /** 生物卵（ADR-0026 §九）：伙伴下的，记着物种、性状和主人，放在地上孵出幼崽。 */
    public static final DeferredItem<CreatureEggItem> CREATURE_EGG = ITEMS.registerItem("creature_egg",
            properties -> new CreatureEggItem(UniverseBlocks.CREATURE_EGG.get(), properties),
            () -> new Item.Properties().useBlockDescriptionPrefix().stacksTo(16));
    /** 刷怪蛋：按脚下的群系挑一种本星球的动物。 */
    public static final DeferredItem<SpawnEggItem> ALIEN_CREATURE_SPAWN_EGG = ITEMS.registerItem("alien_creature_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.ALIEN_CREATURE.get()));
    public static final DeferredItem<SpawnEggItem> ALIEN_FLYER_SPAWN_EGG = ITEMS.registerItem("alien_flyer_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.ALIEN_FLYER.get()));
    /** 刷怪蛋：按水深挑一种本星球的水生动物。 */
    public static final DeferredItem<SpawnEggItem> AQUATIC_CREATURE_SPAWN_EGG = ITEMS.registerItem("aquatic_creature_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.AQUATIC_CREATURE.get()));
    // ---- 水下（ADR-0026）
    /** 异星生鱼：水生动物都掉，和生鳕鱼差不多。 */
    public static final DeferredItem<Item> ALIEN_FISH = ITEMS.registerSimpleItem("alien_fish",
            properties -> properties.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build()));
    public static final DeferredItem<Item> COOKED_ALIEN_FISH = ITEMS.registerSimpleItem("cooked_alien_fish",
            properties -> properties.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
    /** 活珍珠：海底的蚌里长的，只能卖钱（无人深空的 Living Pearl）。 */
    public static final DeferredItem<Item> LIVING_PEARL = ITEMS.registerSimpleItem("living_pearl", properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> CLAM = ITEMS.registerSimpleBlockItem(UniverseBlocks.CLAM);
    /** 潜艇：对着水面用放下一艘。 */
    public static final DeferredItem<SubmarineItem> SUBMARINE = ITEMS.registerItem("submarine", SubmarineItem::new, properties -> properties.stacksTo(1));
    /** 悬浮车、重型车、机甲（ADR-0026）：和探索车一样对着地面用放下。 */
    public static final DeferredItem<ExocraftItem> HOVER_CAR = ITEMS.registerItem("hover_car",
            properties -> new ExocraftItem(UniverseEntities.HOVER_CAR, properties), properties -> properties.stacksTo(1));
    public static final DeferredItem<ExocraftItem> HEAVY_ROVER = ITEMS.registerItem("heavy_rover",
            properties -> new ExocraftItem(UniverseEntities.HEAVY_ROVER, properties), properties -> properties.stacksTo(1));
    public static final DeferredItem<ExocraftItem> EXO_MECH = ITEMS.registerItem("exo_mech",
            properties -> new ExocraftItem(UniverseEntities.EXO_MECH, properties), properties -> properties.stacksTo(1));
    /** 载具召唤台：把你的车叫到台子旁边。 */
    public static final DeferredItem<BlockItem> VEHICLE_SUMMONER = ITEMS.registerSimpleBlockItem(UniverseBlocks.VEHICLE_SUMMONER);

    // ---- 野外的地标（ADR-0026）：化石坑、打捞点、雷击石。化石和废料只能卖钱
    public static final DeferredItem<BlockItem> FOSSIL_DIRT = ITEMS.registerSimpleBlockItem(UniverseBlocks.FOSSIL_DIRT);
    public static final DeferredItem<Item> SHELL_FOSSIL = ITEMS.registerSimpleItem("shell_fossil");
    public static final DeferredItem<Item> ARTHROPOD_FOSSIL = ITEMS.registerSimpleItem("arthropod_fossil",
            properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> SKULL_FOSSIL = ITEMS.registerSimpleItem("skull_fossil", properties -> properties.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> SALVAGE_CAPSULE = ITEMS.registerSimpleBlockItem(UniverseBlocks.SALVAGE_CAPSULE);
    public static final DeferredItem<Item> SALVAGED_SCRAP = ITEMS.registerSimpleItem("salvaged_scrap", properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> FULGURITE = ITEMS.registerSimpleBlockItem(UniverseBlocks.FULGURITE);

    // ---- 飞船
    public static final DeferredItem<BlockItem> SHIP_HELM = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_HELM);
    public static final DeferredItem<BlockItem> LANDING_PAD = ITEMS.registerSimpleBlockItem(UniverseBlocks.LANDING_PAD);
    public static final DeferredItem<BlockItem> DAMAGED_HELM = ITEMS.registerSimpleBlockItem(UniverseBlocks.DAMAGED_HELM);
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
    /** 太空遭遇（ADR-0024）：信用点芯片、应急信号接收器。 */
    public static final DeferredItem<CreditChipItem> CREDIT_CHIP = ITEMS.registerItem("credit_chip", CreditChipItem::new);
    public static final DeferredItem<EmergencyReceiverItem> EMERGENCY_RECEIVER = ITEMS.registerItem("emergency_receiver",
            EmergencyReceiverItem::new, properties -> properties.stacksTo(16));
    public static final DeferredItem<SpawnEggItem> SENTINEL_SPAWN_EGG = ITEMS.registerItem("sentinel_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.SENTINEL.get()));
    public static final DeferredItem<SpawnEggItem> HEAVY_SENTINEL_SPAWN_EGG = ITEMS.registerItem("heavy_sentinel_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.HEAVY_SENTINEL.get()));
    /** 地面守卫机的掉落（ADR-0026）：做机甲和守卫机型多功能工具要用，也能卖。 */
    public static final DeferredItem<Item> QUADRUPED_SERVO = ITEMS.registerSimpleItem("quadruped_servo");
    public static final DeferredItem<Item> WALKER_BRAIN = ITEMS.registerSimpleItem("walker_brain",
            properties -> properties.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<SpawnEggItem> QUADRUPED_SENTINEL_SPAWN_EGG = ITEMS.registerItem("quadruped_sentinel_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.QUADRUPED_SENTINEL.get()));
    public static final DeferredItem<SpawnEggItem> WALKER_SENTINEL_SPAWN_EGG = ITEMS.registerItem("walker_sentinel_spawn_egg",
            SpawnEggItem::new, properties -> properties.spawnEgg(UniverseEntities.WALKER_SENTINEL.get()));
    // ---- 兴趣点的方块（ADR-0014）
    public static final DeferredItem<BlockItem> SIGNAL_BOOSTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SIGNAL_BOOSTER);
    public static final DeferredItem<BlockItem> SUPPLY_CRATE = ITEMS.registerSimpleBlockItem(UniverseBlocks.SUPPLY_CRATE);
    public static final DeferredItem<BlockItem> SEALED_CRATE = ITEMS.registerSimpleBlockItem(UniverseBlocks.SEALED_CRATE);
    public static final DeferredItem<BlockItem> DROP_POD = ITEMS.registerSimpleBlockItem(UniverseBlocks.DROP_POD);
    public static final DeferredItem<BlockItem> DATA_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.DATA_TERMINAL);
    public static final DeferredItem<BlockItem> MONOLITH = ITEMS.registerSimpleBlockItem(UniverseBlocks.MONOLITH);
    public static final DeferredItem<BlockItem> PORTAL_STONE = ITEMS.registerSimpleBlockItem(UniverseBlocks.PORTAL_STONE);
    public static final DeferredItem<BlockItem> PORTAL_GLYPH = ITEMS.registerSimpleBlockItem(UniverseBlocks.PORTAL_GLYPH);
    public static final DeferredItem<BlockItem> PORTAL_DIAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.PORTAL_DIAL);
    public static final DeferredItem<BlockItem> SENTINEL_PILLAR_PLATING = ITEMS.registerSimpleBlockItem(UniverseBlocks.SENTINEL_PILLAR_PLATING);
    public static final DeferredItem<BlockItem> SENTINEL_PILLAR_LIGHT = ITEMS.registerSimpleBlockItem(UniverseBlocks.SENTINEL_PILLAR_LIGHT);
    public static final DeferredItem<BlockItem> SENTINEL_PILLAR_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.SENTINEL_PILLAR_TERMINAL);
    public static final DeferredItem<BlockItem> TECH_CACHE = ITEMS.registerSimpleBlockItem(UniverseBlocks.TECH_CACHE);
    public static final DeferredItem<BlockItem> SMOLDERING_WRECK = ITEMS.registerSimpleBlockItem(UniverseBlocks.SMOLDERING_WRECK);
    // ---- 空间站（ADR-0017）
    public static final DeferredItem<BlockItem> TRADE_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.TRADE_TERMINAL);
    // 地上的地标（ADR-0026）：交易站、制造设施、观测站
    public static final DeferredItem<BlockItem> OUTPOST_TRADE_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.OUTPOST_TRADE_TERMINAL);
    public static final DeferredItem<BlockItem> REINFORCED_DOOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.REINFORCED_DOOR);
    public static final DeferredItem<BlockItem> REINFORCED_PLATING = ITEMS.registerSimpleBlockItem(UniverseBlocks.REINFORCED_PLATING);
    public static final DeferredItem<BlockItem> FACILITY_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.FACILITY_TERMINAL);
    public static final DeferredItem<BlockItem> OBSERVATORY_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.OBSERVATORY_TERMINAL);
    // 知识石（ADR-0027 §五）
    public static final DeferredItem<BlockItem> KNOWLEDGE_STONE = ITEMS.registerSimpleBlockItem(UniverseBlocks.KNOWLEDGE_STONE);
    public static final DeferredItem<BlockItem> STATION_HULL = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_HULL);
    public static final DeferredItem<BlockItem> STATION_FLOOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_FLOOR);
    public static final DeferredItem<BlockItem> STATION_GLASS = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_GLASS);
    public static final DeferredItem<BlockItem> STATION_LIGHT = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_LIGHT);
    public static final DeferredItem<BlockItem> STATION_PAD = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_PAD);
    public static final DeferredItem<BlockItem> STATION_TRIM = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_TRIM);
    public static final DeferredItem<BlockItem> STATION_SOLAR_PANEL = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_SOLAR_PANEL);
    public static final DeferredItem<BlockItem> SHIP_REPAIR = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_REPAIR);
    public static final DeferredItem<BlockItem> STATION_TELEPORTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.STATION_TELEPORTER);
    public static final DeferredItem<BlockItem> MISSION_BOARD = ITEMS.registerSimpleBlockItem(UniverseBlocks.MISSION_BOARD);
    public static final DeferredItem<BlockItem> SHIP_DEALER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SHIP_DEALER);
    public static final DeferredItem<BlockItem> MULTITOOL_CABINET = ITEMS.registerSimpleBlockItem(UniverseBlocks.MULTITOOL_CABINET);
    // ---- 货船（ADR-0024）
    public static final DeferredItem<BlockItem> FREIGHTER_PLATE = ITEMS.registerSimpleBlockItem(UniverseBlocks.FREIGHTER_PLATE);
    public static final DeferredItem<BlockItem> FREIGHTER_ENGINE = ITEMS.registerSimpleBlockItem(UniverseBlocks.FREIGHTER_ENGINE);
    public static final DeferredItem<BlockItem> FREIGHTER_CONSOLE = ITEMS.registerSimpleBlockItem(UniverseBlocks.FREIGHTER_CONSOLE);
    public static final DeferredItem<BlockItem> FLEET_TERMINAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.FLEET_TERMINAL);
    public static final DeferredItem<BlockItem> BASE_BEACON = ITEMS.registerSimpleBlockItem(UniverseBlocks.BASE_BEACON);
    public static final DeferredItem<BlockItem> TELEPORTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.TELEPORTER);

    // ---- 外骨骼
    public static final DeferredItem<Item> EXOSUIT_HELMET = ITEMS.registerSimpleItem("exosuit_helmet",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.HELMET));
    public static final DeferredItem<Item> EXOSUIT_CHESTPLATE = ITEMS.registerSimpleItem("exosuit_chestplate",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> EXOSUIT_LEGGINGS = ITEMS.registerSimpleItem("exosuit_leggings",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.LEGGINGS));
    public static final DeferredItem<Item> EXOSUIT_BOOTS = ITEMS.registerSimpleItem("exosuit_boots",
            properties -> properties.humanoidArmor(Exosuit.MATERIAL, ArmorType.BOOTS));
    // ---- 星球生活（ADR-0026）
    /** 资源植物的产物，按注册名：氧气、钠能直接补外骨骼，别的做菜、精炼、卖。 */
    public static final Map<String, DeferredItem<? extends Item>> RESOURCES = registerResources();
    public static final DeferredItem<BlockItem> HYDROPONIC_TRAY = ITEMS.registerSimpleBlockItem(UniverseBlocks.HYDROPONIC_TRAY);
    public static final DeferredItem<SuitRefillItem> OXYGEN_CAPSULE = ITEMS.registerItem("oxygen_capsule",
            properties -> new SuitRefillItem(SuitRefillItem.Kind.OXYGEN, properties), properties -> properties.stacksTo(16));
    public static final DeferredItem<SuitRefillItem> HAZARD_CELL = ITEMS.registerItem("hazard_cell",
            properties -> new SuitRefillItem(SuitRefillItem.Kind.HAZARD, properties), properties -> properties.stacksTo(16));
    public static final DeferredItem<BlockItem> NUTRIENT_PROCESSOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.NUTRIENT_PROCESSOR);
    /** 便携精炼机拆下来带着里面的东西，和潜影盒一样不叠。 */
    public static final DeferredItem<BlockItem> PORTABLE_REFINER = ITEMS.registerSimpleBlockItem(UniverseBlocks.PORTABLE_REFINER,
            properties -> properties.stacksTo(1));
    /** 卵序列仪（ADR-0026 §九）。 */
    public static final DeferredItem<BlockItem> EGG_SEQUENCER = ITEMS.registerSimpleBlockItem(UniverseBlocks.EGG_SEQUENCER);
    /** 营养处理器做的半成品和菜，按 {@link Dish} 的顺序。 */
    public static final Map<Dish, DeferredItem<DishItem>> DISHES = registerDishes();
    /** 便携精炼机做的防护凝胶，各补一条危害防护。 */
    public static final Map<Hazard, DeferredItem<SuitRefillItem>> GELS = registerGels();

    // ---- 多功能工具
    public static final DeferredItem<MultitoolItem> MULTITOOL = ITEMS.registerItem("multitool", MultitoolItem::new,
            properties -> properties.stacksTo(1).component(DataComponents.TOOL, multitoolRules()));
    /** 探索车（ADR-0017）：对着地面用就放下一辆。 */
    public static final DeferredItem<ExocraftItem> EXOCRAFT = ITEMS.registerItem("exocraft",
            properties -> new ExocraftItem(UniverseEntities.EXOCRAFT, properties), properties -> properties.stacksTo(1));
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
    // 工厂的补全（ADR-0022）
    public static final DeferredItem<BlockItem> SPRAY_COATER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SPRAY_COATER);
    public static final DeferredItem<BlockItem> AUTOMATIC_PILER = ITEMS.registerSimpleBlockItem(UniverseBlocks.AUTOMATIC_PILER);
    public static final DeferredItem<BlockItem> TRAFFIC_MONITOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.TRAFFIC_MONITOR);
    public static final DeferredItem<BlockItem> LOGISTICS_DISTRIBUTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.LOGISTICS_DISTRIBUTOR);
    public static final DeferredItem<BlockItem> PILE_SORTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.PILE_SORTER);
    public static final DeferredItem<BlockItem> WIRELESS_POWER_TOWER = ITEMS.registerSimpleBlockItem(UniverseBlocks.WIRELESS_POWER_TOWER);
    public static final DeferredItem<BlockItem> SATELLITE_SUBSTATION = ITEMS.registerSimpleBlockItem(UniverseBlocks.SATELLITE_SUBSTATION);
    public static final DeferredItem<BlockItem> GEOTHERMAL_POWER_STATION = ITEMS.registerSimpleBlockItem(UniverseBlocks.GEOTHERMAL_POWER_STATION);
    public static final DeferredItem<BlockItem> ENERGY_EXCHANGER = ITEMS.registerSimpleBlockItem(UniverseBlocks.ENERGY_EXCHANGER);
    public static final DeferredItem<BlockItem> NEGENTROPY_SMELTER = ITEMS.registerSimpleBlockItem(UniverseBlocks.NEGENTROPY_SMELTER);
    public static final DeferredItem<BlockItem> RECOMPOSING_ASSEMBLER = ITEMS.registerSimpleBlockItem(UniverseBlocks.RECOMPOSING_ASSEMBLER);
    public static final DeferredItem<BlockItem> QUANTUM_CHEMICAL_PLANT = ITEMS.registerSimpleBlockItem(UniverseBlocks.QUANTUM_CHEMICAL_PLANT);
    public static final DeferredItem<BlockItem> SELF_EVOLUTION_LAB = ITEMS.registerSimpleBlockItem(UniverseBlocks.SELF_EVOLUTION_LAB);
    public static final DeferredItem<BlockItem> FOUNDATION = ITEMS.registerSimpleBlockItem(UniverseBlocks.FOUNDATION);
    // 戴森球的设备（ADR-0016）
    public static final DeferredItem<BlockItem> EM_RAIL_EJECTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.EM_RAIL_EJECTOR);
    public static final DeferredItem<BlockItem> VERTICAL_LAUNCHING_SILO = ITEMS.registerSimpleBlockItem(UniverseBlocks.VERTICAL_LAUNCHING_SILO);
    public static final DeferredItem<BlockItem> RAY_RECEIVER = ITEMS.registerSimpleBlockItem(UniverseBlocks.RAY_RECEIVER);
    public static final DeferredItem<BlockItem> MINIATURE_PARTICLE_COLLIDER = ITEMS.registerSimpleBlockItem(
            UniverseBlocks.MINIATURE_PARTICLE_COLLIDER);
    public static final DeferredItem<BlockItem> MINI_FUSION_POWER_PLANT = ITEMS.registerSimpleBlockItem(UniverseBlocks.MINI_FUSION_POWER_PLANT);
    public static final DeferredItem<BlockItem> ARTIFICIAL_STAR = ITEMS.registerSimpleBlockItem(UniverseBlocks.ARTIFICIAL_STAR);
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
    /** 增产剂三档（ADR-0022）：喷涂机用它们给传送带上的货物喷点数。 */
    public static final DeferredItem<Item> PROLIFERATOR_MK1 = ITEMS.registerSimpleItem("proliferator_mk1");
    public static final DeferredItem<Item> PROLIFERATOR_MK2 = ITEMS.registerSimpleItem("proliferator_mk2");
    public static final DeferredItem<Item> PROLIFERATOR_MK3 = ITEMS.registerSimpleItem("proliferator_mk3");
    /** 燃烧单元：三块煤压成一个，比高能石墨耐烧（戴森球计划 9 MJ）。 */
    /** 燃烧单元：燃料，右键也能扔（ADR-0028 §五）。 */
    public static final DeferredItem<ThrowableUnitItem> COMBUSTIBLE_UNIT = ITEMS.registerItem("combustible_unit", ThrowableUnitItem::new,
            properties -> fuel(properties, 5300));
    /** 液氢燃料棒：钛壳装氢（戴森球计划 54 MJ）。 */
    public static final DeferredItem<Item> HYDROGEN_FUEL_ROD = ITEMS.registerSimpleItem("hydrogen_fuel_rod",
            properties -> fuel(properties.stacksTo(32), 31700));
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
    /** 配送机器人（ADR-0023），放进物流配送器里。 */
    public static final DeferredItem<Item> LOGISTICS_BOT = ITEMS.registerSimpleItem("logistics_bot");
    /** 建造无人机：外骨骼胸甲自带，这个物品只用来画在天上飞的无人机和升级科技的图标，拿不到。 */
    public static final DeferredItem<Item> CONSTRUCTION_DRONE = ITEMS.registerSimpleItem("construction_drone");
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
    /** 终局的物品（ADR-0018），配方照戴森球计划；宇宙矩阵分两步做，先做复合矩阵。 */
    public static final DeferredItem<Item> ANTIMATTER = ITEMS.registerSimpleItem("antimatter");
    public static final DeferredItem<Item> ANNIHILATION_CONSTRAINT_SPHERE = ITEMS.registerSimpleItem("annihilation_constraint_sphere");
    public static final DeferredItem<Item> ANTIMATTER_FUEL_ROD = ITEMS.registerSimpleItem("antimatter_fuel_rod", properties -> properties.stacksTo(32));
    public static final DeferredItem<Item> COMPOSITE_MATRIX = ITEMS.registerSimpleItem("composite_matrix");
    public static final DeferredItem<Item> UNIVERSE_MATRIX = ITEMS.registerSimpleItem("universe_matrix");
    // M16 黑雾的掉落（ADR-0028 §六）
    public static final DeferredItem<Item> ENERGY_SHARD = ITEMS.registerSimpleItem("energy_shard");
    public static final DeferredItem<Item> SILICON_NEURON = ITEMS.registerSimpleItem("silicon_neuron");
    public static final DeferredItem<Item> MATTER_RECOMBINATOR = ITEMS.registerSimpleItem("matter_recombinator");
    public static final DeferredItem<Item> NEGENTROPY_SINGULARITY = ITEMS.registerSimpleItem("negentropy_singularity");
    public static final DeferredItem<Item> CORE_ELEMENT = ITEMS.registerSimpleItem("core_element");
    /** 第七种矩阵。 */
    public static final DeferredItem<Item> DARK_FOG_MATRIX = ITEMS.registerSimpleItem("dark_fog_matrix");
    // M16 弹药（ADR-0028 §五）
    public static final DeferredItem<AmmoItem> MAGNUM_AMMO_BOX = ITEMS.registerItem("magnum_ammo_box", p -> new AmmoItem(AmmoKind.MAGNUM, p));
    public static final DeferredItem<AmmoItem> TITANIUM_AMMO_BOX = ITEMS.registerItem("titanium_ammo_box", p -> new AmmoItem(AmmoKind.TITANIUM, p));
    public static final DeferredItem<AmmoItem> SUPERALLOY_AMMO_BOX = ITEMS.registerItem("superalloy_ammo_box", p -> new AmmoItem(AmmoKind.SUPERALLOY, p));
    public static final DeferredItem<AmmoItem> SHELL_SET = ITEMS.registerItem("shell_set", p -> new AmmoItem(AmmoKind.SHELL, p));
    public static final DeferredItem<AmmoItem> HIGH_EXPLOSIVE_SHELL_SET = ITEMS.registerItem("high_explosive_shell_set", p -> new AmmoItem(AmmoKind.HIGH_EXPLOSIVE_SHELL, p));
    public static final DeferredItem<AmmoItem> CRYSTAL_SHELL_SET = ITEMS.registerItem("crystal_shell_set", p -> new AmmoItem(AmmoKind.CRYSTAL_SHELL, p));
    public static final DeferredItem<AmmoItem> MISSILE_SET = ITEMS.registerItem("missile_set", p -> new AmmoItem(AmmoKind.MISSILE, p));
    public static final DeferredItem<AmmoItem> SUPERSONIC_MISSILE_SET = ITEMS.registerItem("supersonic_missile_set", p -> new AmmoItem(AmmoKind.SUPERSONIC_MISSILE, p));
    public static final DeferredItem<AmmoItem> GRAVITY_MISSILE_SET = ITEMS.registerItem("gravity_missile_set", p -> new AmmoItem(AmmoKind.GRAVITY_MISSILE, p));
    public static final DeferredItem<Item> JAMMING_CAPSULE = ITEMS.registerSimpleItem("jamming_capsule");
    // M16 投掷物（ADR-0028 §五）
    public static final DeferredItem<ThrowableUnitItem> EXPLOSIVE_UNIT = ITEMS.registerItem("explosive_unit", ThrowableUnitItem::new,
            properties -> properties.stacksTo(32));
    public static final DeferredItem<ThrowableUnitItem> CRYSTAL_EXPLOSIVE_UNIT = ITEMS.registerItem("crystal_explosive_unit",
            ThrowableUnitItem::new, properties -> properties.stacksTo(32));
    public static final DeferredItem<BlockItem> BATTLEFIELD_ANALYSIS_BASE = ITEMS.registerSimpleBlockItem(UniverseBlocks.BATTLEFIELD_ANALYSIS_BASE);
    public static final DeferredItem<BlockItem> GAUSS_TURRET = ITEMS.registerSimpleBlockItem(UniverseBlocks.GAUSS_TURRET);
    public static final DeferredItem<BlockItem> LASER_TURRET = ITEMS.registerSimpleBlockItem(UniverseBlocks.LASER_TURRET);
    public static final DeferredItem<BlockItem> IMPLOSION_CANNON = ITEMS.registerSimpleBlockItem(UniverseBlocks.IMPLOSION_CANNON);
    public static final DeferredItem<BlockItem> MISSILE_TURRET = ITEMS.registerSimpleBlockItem(UniverseBlocks.MISSILE_TURRET);
    public static final DeferredItem<BlockItem> SIGNAL_TOWER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SIGNAL_TOWER);
    public static final DeferredItem<BlockItem> JAMMER_TOWER = ITEMS.registerSimpleBlockItem(UniverseBlocks.JAMMER_TOWER);
    // M16 第二块（ADR-0028 §八）
    public static final DeferredItem<BlockItem> PLASMA_TURRET = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLASMA_TURRET);
    public static final DeferredItem<BlockItem> SR_PLASMA_TURRET = ITEMS.registerSimpleBlockItem(UniverseBlocks.SR_PLASMA_TURRET);
    public static final DeferredItem<AmmoItem> PLASMA_CAPSULE = ITEMS.registerItem("plasma_capsule", p -> new AmmoItem(AmmoKind.PLASMA_CAPSULE, p));
    public static final DeferredItem<AmmoItem> ANTIMATTER_CAPSULE = ITEMS.registerItem("antimatter_capsule",
            p -> new AmmoItem(AmmoKind.ANTIMATTER_CAPSULE, p));
    // M16 黑雾：等离子炮塔（T）的物品加在这行上面
    public static final DeferredItem<BlockItem> PLANETARY_SHIELD_GENERATOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.PLANETARY_SHIELD_GENERATOR);
    // M16 黑雾：行星护盾（S）的物品加在这行上面
    /** 酸液桶（ADR-0030 §一）：拿空桶在毒性星球的酸海里舀。 */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> ACID_BUCKET = ITEMS.registerItem("acid_bucket",
            p -> new net.minecraft.world.item.BucketItem(UniverseFluids.ACID.get(), p),
            p -> p.craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    // M17 收尾：酸液（A）的物品加在这行上面
    public static final DeferredItem<BlockItem> GRID_INTERFACE = ITEMS.registerSimpleBlockItem(UniverseBlocks.GRID_INTERFACE);
    // M17 收尾：互通（C）的物品加在这行上面
    // M18 舰队（ADR-0033 §一）：放进机甲的编队格里出动
    public static final DeferredItem<io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem> PROTOTYPE = ITEMS.registerItem("prototype",
            p -> new io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem(io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.PROTOTYPE, p),
            p -> p.stacksTo(16));
    public static final DeferredItem<io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem> PRECISION_DRONE = ITEMS.registerItem("precision_drone",
            p -> new io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem(io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.PRECISION_DRONE, p),
            p -> p.stacksTo(16));
    public static final DeferredItem<io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem> ATTACK_DRONE = ITEMS.registerItem("attack_drone",
            p -> new io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem(io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.ATTACK_DRONE, p),
            p -> p.stacksTo(16));
    public static final DeferredItem<io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem> CORVETTE = ITEMS.registerItem("corvette",
            p -> new io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem(io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.CORVETTE, p),
            p -> p.stacksTo(4));
    public static final DeferredItem<io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem> DESTROYER = ITEMS.registerItem("destroyer",
            p -> new io.github.sweetcornna.universe.mecha.fleet.FleetUnitItem(io.github.sweetcornna.universe.core.mecha.fleet.FleetUnitKind.DESTROYER, p),
            p -> p.stacksTo(4).rarity(Rarity.RARE));
    // M18 舰队和战斗（A）的物品加在这行上面
    // M18 物流调度（B）的物品加在这行上面
    // M18 统计和研究（C）的物品加在这行上面
    /** 黑雾透镜（ADR-0033 §七）：射线接收站的透镜格里用，损耗减半、积累快一倍。 */
    public static final DeferredItem<Item> DARK_FOG_LENS = ITEMS.registerSimpleItem("dark_fog_lens");
    /** 全息信标（ADR-0033 §六）。 */
    public static final DeferredItem<BlockItem> HOLO_BEACON = ITEMS.registerSimpleBlockItem(UniverseBlocks.HOLO_BEACON);
    // M18 信标、透镜、矿脉、蓝图（D）的物品加在这行上面
    // M19 钓鱼（ADR-0034 §一）：96 种鱼、六种专门的鱼饵、三样杂物、漂流瓶、密封补给舱、捕鱼笼
    public static final Map<io.github.sweetcornna.universe.core.fishing.FishSpecies, DeferredItem<io.github.sweetcornna.universe.fishing.FishItem>> FISH = registerFish();
    public static final Map<io.github.sweetcornna.universe.core.fishing.Bait, DeferredItem<io.github.sweetcornna.universe.fishing.BaitItem>> BAITS = registerBaits();
    public static final Map<io.github.sweetcornna.universe.core.fishing.Junk, DeferredItem<Item>> JUNK = registerJunk();
    public static final DeferredItem<io.github.sweetcornna.universe.fishing.FishingFindItem> MESSAGE_BOTTLE = ITEMS.registerItem("message_bottle",
            p -> new io.github.sweetcornna.universe.fishing.FishingFindItem(io.github.sweetcornna.universe.fishing.FishingFindItem.Kind.BOTTLE, p), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<io.github.sweetcornna.universe.fishing.FishingFindItem> SEALED_CACHE = ITEMS.registerItem("sealed_cache",
            p -> new io.github.sweetcornna.universe.fishing.FishingFindItem(io.github.sweetcornna.universe.fishing.FishingFindItem.Kind.CACHE, p), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> FISH_TRAP = ITEMS.registerSimpleBlockItem(UniverseBlocks.FISH_TRAP);

    /** 每种鱼一个物品：能叠，生吃按个头给一两点饱食度。 */
    private static Map<io.github.sweetcornna.universe.core.fishing.FishSpecies, DeferredItem<io.github.sweetcornna.universe.fishing.FishItem>> registerFish() {
        Map<io.github.sweetcornna.universe.core.fishing.FishSpecies, DeferredItem<io.github.sweetcornna.universe.fishing.FishItem>> items = new LinkedHashMap<>();
        for (io.github.sweetcornna.universe.core.fishing.FishSpecies species : io.github.sweetcornna.universe.core.fishing.FishTable.ALL) {
            int nutrition = 1 + species.size().ordinal() / 2;
            items.put(species, ITEMS.registerItem(species.itemId(), p -> new io.github.sweetcornna.universe.fishing.FishItem(species, p),
                    p -> p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(0.1F).build())));
        }
        return items;
    }

    private static Map<io.github.sweetcornna.universe.core.fishing.Bait, DeferredItem<io.github.sweetcornna.universe.fishing.BaitItem>> registerBaits() {
        Map<io.github.sweetcornna.universe.core.fishing.Bait, DeferredItem<io.github.sweetcornna.universe.fishing.BaitItem>> items = new EnumMap<>(io.github.sweetcornna.universe.core.fishing.Bait.class);
        for (io.github.sweetcornna.universe.core.fishing.Bait bait : io.github.sweetcornna.universe.core.fishing.Bait.specials()) {
            items.put(bait, ITEMS.registerItem(bait.id(), p -> new io.github.sweetcornna.universe.fishing.BaitItem(bait, p),
                    p -> bait == io.github.sweetcornna.universe.core.fishing.Bait.STAR_CORE ? p.rarity(Rarity.RARE) : p));
        }
        return items;
    }

    private static Map<io.github.sweetcornna.universe.core.fishing.Junk, DeferredItem<Item>> registerJunk() {
        Map<io.github.sweetcornna.universe.core.fishing.Junk, DeferredItem<Item>> items = new EnumMap<>(io.github.sweetcornna.universe.core.fishing.Junk.class);
        for (io.github.sweetcornna.universe.core.fishing.Junk junk : io.github.sweetcornna.universe.core.fishing.Junk.values()) {
            items.put(junk, ITEMS.registerSimpleItem(junk.id()));
        }
        return items;
    }
    // M19 钓鱼（A）的物品加在这行上面
    // M19 聚落（B）的物品加在这行上面
    // ---- M19 化石（ADR-0034 §三）
    /** 40 种骨头：8 个类群 × 5 个部位。 */
    public static final Map<io.github.sweetcornna.universe.core.fossil.Bone, DeferredItem<io.github.sweetcornna.universe.fossil.BoneItem>> BONES = io.github.sweetcornna.universe.fossil.BoneItem.register(ITEMS);
    /** 化石展品：骨架展台封装出来的，记着五个部件。 */
    public static final DeferredItem<io.github.sweetcornna.universe.fossil.FossilExhibitItem> FOSSIL_EXHIBIT = ITEMS.registerItem("fossil_exhibit", io.github.sweetcornna.universe.fossil.FossilExhibitItem::new,
            p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> FOSSIL_PEDESTAL = ITEMS.registerSimpleBlockItem(UniverseBlocks.FOSSIL_PEDESTAL);
    public static final DeferredItem<BlockItem> SKELETON_DISPLAY = ITEMS.registerSimpleBlockItem(UniverseBlocks.SKELETON_DISPLAY);
    // M19 化石（C）的物品加在这行上面
    // ---- M19 深空（ADR-0034 §四）：拆残骸、打碎片得到的打捞物
    /** 废金属：残骸的舱板、桁架拆下来的，两块回炉成一块铁。 */
    public static final DeferredItem<Item> SCRAP_METAL = ITEMS.registerSimpleItem("scrap_metal");
    /** 合金板：散热板、屏蔽层上拆下来的，回炉成钛合金。 */
    public static final DeferredItem<Item> ALLOY_PLATE = ITEMS.registerSimpleItem("alloy_plate");
    /** 电子元件：导管和设备里拆出来的，在装配机里拼回电路板。 */
    public static final DeferredItem<Item> SALVAGED_ELECTRONICS = ITEMS.registerSimpleItem("salvaged_electronics");
    /** 反应堆芯：一艘残骸只有一个，拆下来残骸就要熔毁。 */
    public static final DeferredItem<Item> SALVAGED_REACTOR_CORE = ITEMS.registerSimpleItem("salvaged_reactor_core",
            properties -> properties.rarity(Rarity.RARE).stacksTo(16));
    // M19 深空和重力枪（D）的物品加在这行上面
    /** 间歇泉的喷口、结壳（ADR-0034 §六）。 */
    public static final Map<io.github.sweetcornna.universe.core.hazard.GeyserKind, DeferredItem<BlockItem>> GEYSER_VENTS =
            io.github.sweetcornna.universe.hazard.HazardBlocks.items(ITEMS, UniverseBlocks.GEYSER_VENTS);
    public static final Map<io.github.sweetcornna.universe.core.hazard.GeyserKind, DeferredItem<BlockItem>> GEYSER_CRUSTS =
            io.github.sweetcornna.universe.hazard.HazardBlocks.items(ITEMS, UniverseBlocks.GEYSER_CRUSTS);
    // M19 科技布局和局部危害（E）的物品加在这行上面
    // ---- M20 化学与生物研究（ADR-0036）：几条线共用的物品。气体、溶液按物品处理（ADR-0011），一个物品按一摩尔配平
    /** 气体（{@link io.github.sweetcornna.universe.core.chem.Gas}），氢和氧以前就有。甲烷能烧（802 kJ/mol，按氢的比例折算）。 */
    public static final DeferredItem<Item> NITROGEN = ITEMS.registerSimpleItem("nitrogen");
    public static final DeferredItem<Item> CARBON_DIOXIDE = ITEMS.registerSimpleItem("carbon_dioxide");
    public static final DeferredItem<Item> CARBON_MONOXIDE = ITEMS.registerSimpleItem("carbon_monoxide");
    public static final DeferredItem<Item> METHANE = ITEMS.registerSimpleItem("methane", properties -> fuel(properties, 15500));
    public static final DeferredItem<Item> ARGON = ITEMS.registerSimpleItem("argon");
    public static final DeferredItem<Item> HELIUM = ITEMS.registerSimpleItem("helium");
    public static final DeferredItem<Item> SULFUR_DIOXIDE = ITEMS.registerSimpleItem("sulfur_dioxide");
    public static final DeferredItem<Item> AMMONIA = ITEMS.registerSimpleItem("ammonia");
    public static final DeferredItem<Item> CHLORINE = ITEMS.registerSimpleItem("chlorine");
    /** 氰化氢：冰冻星球大气里的毒气，只在改造时用得到（大气释放塔、测成分）。 */
    public static final DeferredItem<Item> HYDROGEN_CYANIDE = ITEMS.registerSimpleItem("hydrogen_cyanide");
    /** 化工品。 */
    public static final DeferredItem<Item> SODIUM_HYDROXIDE = ITEMS.registerSimpleItem("sodium_hydroxide");
    public static final DeferredItem<Item> NITRIC_ACID = ITEMS.registerSimpleItem("nitric_acid");
    /** 硝酸铵化肥：水培槽右键施肥。 */
    public static final DeferredItem<Item> AMMONIUM_NITRATE = ITEMS.registerSimpleItem("ammonium_nitrate");
    public static final DeferredItem<Item> TITANIUM_TETRACHLORIDE = ITEMS.registerSimpleItem("titanium_tetrachloride");
    public static final DeferredItem<Item> VANADIUM_SLAG = ITEMS.registerSimpleItem("vanadium_slag");
    public static final DeferredItem<Item> COPPER_LEACH_SOLUTION = ITEMS.registerSimpleItem("copper_leach_solution");
    public static final DeferredItem<Item> GOLD_LEACH_SOLUTION = ITEMS.registerSimpleItem("gold_leach_solution");
    public static final DeferredItem<Item> NICKEL_INGOT = ITEMS.registerSimpleItem("nickel_ingot");
    /** 甲烷液氧燃料：飞船燃料（ADR-0036 §四）。 */
    public static final DeferredItem<Item> METHALOX_FUEL = ITEMS.registerSimpleItem("methalox_fuel", properties -> properties.stacksTo(16));
    /** 反应釜的催化剂：放在催化剂格里，做满一定批数用掉一个。 */
    public static final DeferredItem<Item> IRON_CATALYST = ITEMS.registerSimpleItem("iron_catalyst");
    public static final DeferredItem<Item> VANADIUM_CATALYST = ITEMS.registerSimpleItem("vanadium_catalyst");
    public static final DeferredItem<Item> NICKEL_CATALYST = ITEMS.registerSimpleItem("nickel_catalyst");
    /** 生物实验的耗材。 */
    public static final DeferredItem<Item> AGAR = ITEMS.registerSimpleItem("agar");
    public static final DeferredItem<Item> CULTURE_MEDIUM = ITEMS.registerSimpleItem("culture_medium");
    public static final DeferredItem<Item> SEQUENCING_CHIP = ITEMS.registerSimpleItem("sequencing_chip");
    public static final DeferredItem<Item> EDITING_KIT = ITEMS.registerSimpleItem("editing_kit");
    public static final DeferredItem<Item> ALGAL_BIOMASS = ITEMS.registerSimpleItem("algal_biomass", properties -> fuel(properties, 1600));
    /**
     * 菌种管（带 {@link UniverseDataComponents#ORGANISM}）和改造种苗（带 {@link UniverseDataComponents#ENGINEERED_PLANT}）：
     * 微生物（C）做出来，生物反应器（D）、生态播种站（E）用。这两行的物品类归 C，C 可以换成自己的类，注册名不改。
     */
    public static final DeferredItem<Item> STRAIN_TUBE = ITEMS.<Item>registerItem("strain_tube", io.github.sweetcornna.universe.microbe.StrainTubeItem::new,
            properties -> properties.stacksTo(16));
    public static final DeferredItem<Item> ENGINEERED_SEEDLING = ITEMS.<Item>registerItem("engineered_seedling",
            io.github.sweetcornna.universe.microbe.EngineeredSeedlingItem::new);
    /** 上面这些共用物品，按创造物品栏的顺序。 */
    public static final List<DeferredItem<Item>> M20_SHARED = List.of(NITROGEN, CARBON_DIOXIDE, CARBON_MONOXIDE, METHANE, ARGON, HELIUM,
            SULFUR_DIOXIDE, AMMONIA, CHLORINE, HYDROGEN_CYANIDE, SODIUM_HYDROXIDE, NITRIC_ACID, AMMONIUM_NITRATE, TITANIUM_TETRACHLORIDE, VANADIUM_SLAG,
            COPPER_LEACH_SOLUTION, GOLD_LEACH_SOLUTION, NICKEL_INGOT, METHALOX_FUEL, IRON_CATALYST, VANADIUM_CATALYST, NICKEL_CATALYST,
            AGAR, CULTURE_MEDIUM, SEQUENCING_CHIP, EDITING_KIT, ALGAL_BIOMASS, STRAIN_TUBE, ENGINEERED_SEEDLING);
    public static final DeferredItem<BlockItem> ATMOSPHERIC_EXTRACTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.ATMOSPHERIC_EXTRACTOR);
    public static final DeferredItem<BlockItem> ELECTROLYZER = ITEMS.registerSimpleBlockItem(UniverseBlocks.ELECTROLYZER);
    // M20 化学（A）的物品加在这行上面
    public static final DeferredItem<BlockItem> CHEMICAL_REACTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.CHEMICAL_REACTOR);
    /** 反应釜的产物和中间体（ADR-0037 §三）。 */
    public static final DeferredItem<Item> SULFUR_TRIOXIDE = ITEMS.registerSimpleItem("sulfur_trioxide");
    public static final DeferredItem<Item> NITRIC_OXIDE = ITEMS.registerSimpleItem("nitric_oxide");
    public static final DeferredItem<Item> NITROGEN_DIOXIDE = ITEMS.registerSimpleItem("nitrogen_dioxide");
    public static final DeferredItem<Item> HYDROGEN_CHLORIDE = ITEMS.registerSimpleItem("hydrogen_chloride");
    /** 甲醇：能烧。低热值 638 kJ/mol，照氢 242 kJ/mol 烧 4700 刻折算。 */
    public static final DeferredItem<Item> METHANOL = ITEMS.registerSimpleItem("methanol", properties -> fuel(properties, 12400));
    public static final DeferredItem<Item> ETHYLENE = ITEMS.registerSimpleItem("ethylene");
    /** 尿素：和硝酸铵一样能给水培槽施肥。 */
    public static final DeferredItem<Item> UREA = ITEMS.registerSimpleItem("urea");
    /** 铂：生物浸出铜偶尔多出一个，做铂铑网。 */
    public static final DeferredItem<Item> PLATINUM_INGOT = ITEMS.registerSimpleItem("platinum_ingot");
    public static final DeferredItem<Item> COPPER_ZINC_CATALYST = ITEMS.registerSimpleItem("copper_zinc_catalyst");
    public static final DeferredItem<Item> PLATINUM_RHODIUM_GAUZE = ITEMS.registerSimpleItem("platinum_rhodium_gauze");
    public static final DeferredItem<Item> ZEOLITE_CATALYST = ITEMS.registerSimpleItem("zeolite_catalyst");
    public static final DeferredItem<Item> ZIEGLER_NATTA_CATALYST = ITEMS.registerSimpleItem("ziegler_natta_catalyst");
    public static final DeferredItem<Item> COPPER_CHLORIDE_CATALYST = ITEMS.registerSimpleItem("copper_chloride_catalyst");
    /** 化学手册和实验记录（ADR-0037 §四、§五）。 */
    public static final DeferredItem<Item> CHEMISTRY_HANDBOOK = ITEMS.<Item>registerItem("chemistry_handbook",
            io.github.sweetcornna.universe.chem.book.ChemistryHandbookItem::new, properties -> properties.stacksTo(1));
    public static final DeferredItem<Item> LAB_NOTES = ITEMS.<Item>registerItem("lab_notes",
            io.github.sweetcornna.universe.chem.book.LabNotesItem::new, properties -> properties.stacksTo(16));
    /** 上面这些平面物品，按创造物品栏的顺序。实验记录不进创造物品栏（不带反应的读不了）。 */
    public static final List<DeferredItem<Item>> M20_REACTIONS = List.of(SULFUR_TRIOXIDE, NITRIC_OXIDE, NITROGEN_DIOXIDE, HYDROGEN_CHLORIDE,
            METHANOL, ETHYLENE, UREA, PLATINUM_INGOT, COPPER_ZINC_CATALYST, PLATINUM_RHODIUM_GAUZE, ZEOLITE_CATALYST, ZIEGLER_NATTA_CATALYST,
            COPPER_CHLORIDE_CATALYST, CHEMISTRY_HANDBOOK);
    // M20 反应釜（B）的物品加在这行上面
    /** 采样管：空的右键方块、液体、生物采样，装着样本的放进培养箱或测序仪（ADR-0036 §五）。 */
    public static final DeferredItem<io.github.sweetcornna.universe.microbe.SampleTubeItem> SAMPLE_TUBE = ITEMS.registerItem("sample_tube", io.github.sweetcornna.universe.microbe.SampleTubeItem::new,
            properties -> properties.stacksTo(16));
    public static final DeferredItem<io.github.sweetcornna.universe.microbe.SampleItem> SAMPLE = ITEMS.registerItem("sample", io.github.sweetcornna.universe.microbe.SampleItem::new,
            properties -> properties.stacksTo(16));
    public static final DeferredItem<BlockItem> INCUBATOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.INCUBATOR);
    public static final DeferredItem<BlockItem> SEQUENCER = ITEMS.registerSimpleBlockItem(UniverseBlocks.SEQUENCER);
    public static final DeferredItem<BlockItem> GENE_EDITOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.GENE_EDITOR);
    // M20 微生物和基因（C）的物品加在这行上面
    public static final DeferredItem<BlockItem> BIOREACTOR = ITEMS.registerSimpleBlockItem(UniverseBlocks.BIOREACTOR);
    // M20 生物反应器（D）的物品加在这行上面
    public static final DeferredItem<BlockItem> ATMOSPHERE_RELEASE_TOWER = ITEMS.registerSimpleBlockItem(UniverseBlocks.ATMOSPHERE_RELEASE_TOWER);
    public static final DeferredItem<BlockItem> ORBITAL_MIRROR = ITEMS.registerSimpleBlockItem(UniverseBlocks.ORBITAL_MIRROR);
    public static final DeferredItem<BlockItem> ECOLOGY_SEEDER = ITEMS.registerSimpleBlockItem(UniverseBlocks.ECOLOGY_SEEDER);
    // M20 星球改造（E）的物品加在这行上面

    /** 蓝图（ADR-0018）。 */
    public static final DeferredItem<BlueprintItem> BLUEPRINT = ITEMS.registerItem("blueprint", BlueprintItem::new);
    /** 手册（ADR-0018）。 */
    public static final DeferredItem<GuideItem> GUIDE = ITEMS.registerItem("guide", GuideItem::new, properties -> properties.stacksTo(1));

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

    /** 多功能工具的武器（ADR-0026 §六）：装进工具的技术槽，按 {@link ToolWeapon} 的顺序。 */
    public static final Map<ToolWeapon, DeferredItem<ToolWeaponItem>> WEAPONS = new EnumMap<>(ToolWeapon.class);

    static {
        for (ToolWeapon weapon : ToolWeapon.values()) {
            WEAPONS.put(weapon, ITEMS.registerItem(weapon.id(), properties -> new ToolWeaponItem(weapon, properties), properties -> properties.stacksTo(1)));
        }
    }

    /** 镉、翠金、铟驱动（ADR-0024），装进驾驶台的技术槽。 */
    public static final Map<DriveTier, DeferredItem<DriveItem>> DRIVES = new EnumMap<>(DriveTier.class);

    static {
        for (DriveTier tier : DriveTier.values()) {
            if (tier != DriveTier.BASIC) {
                DRIVES.put(tier, ITEMS.registerItem(DriveItem.itemName(tier), properties -> new DriveItem(tier, properties)));
            }
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
                ALIEN_FLUFF, BIO_GEL, ALIEN_CREATURE_SPAWN_EGG, ALIEN_FLYER_SPAWN_EGG, AQUATIC_CREATURE_SPAWN_EGG,
                ALIEN_FISH, COOKED_ALIEN_FISH, LIVING_PEARL, CLAM));
        order.addAll(List.of(SHIP_HELM, SHIP_THRUSTER, FUEL_TANK, IMPULSE_ENGINE, WARP_DRIVE, SHIP_SEAT, PHOTON_CANNON, MINING_BEAM,
                SHIELD_GENERATOR, SHIP_THRUSTER_MK2, FUEL_TANK_MK2, IMPULSE_ENGINE_MK2, WARP_DRIVE_MK2, ALLOY_HULL_PLATING,
                LAUNCH_FUEL, SPACE_WARPER, SURVEY_DATA, EXOCRAFT, HOVER_CAR, HEAVY_ROVER, EXO_MECH, SUBMARINE,
                VEHICLE_SUMMONER, BLUEPRINT, GUIDE));
        order.addAll(HULL_PLATING.values());
        order.addAll(List.of(EXOSUIT_HELMET, EXOSUIT_CHESTPLATE, EXOSUIT_LEGGINGS, EXOSUIT_BOOTS, OXYGEN_CAPSULE, HAZARD_CELL,
                MULTITOOL, DAMAGED_MULTITOOL));
        order.addAll(RESOURCES.values());
        order.addAll(List.of(HYDROPONIC_TRAY, NUTRIENT_PROCESSOR, PORTABLE_REFINER, EGG_SEQUENCER));
        order.addAll(GELS.values());
        order.addAll(DISHES.values());
        order.addAll(MODULES.values());
        order.addAll(WEAPONS.values());
        order.addAll(DRIVES.values());
        order.add(LANDING_PAD);
        order.add(DAMAGED_HELM);
        order.addAll(List.of(NAVIGATION_DATA, SIGNAL_BOOSTER, SUPPLY_CRATE, SEALED_CRATE, DROP_POD, DATA_TERMINAL, MONOLITH, TECH_CACHE, SMOLDERING_WRECK,
                CREDIT_CHIP, EMERGENCY_RECEIVER));
        order.addAll(List.of(PORTAL_STONE, PORTAL_GLYPH, PORTAL_DIAL));
        order.addAll(List.of(FOSSIL_DIRT, SHELL_FOSSIL, ARTHROPOD_FOSSIL, SKULL_FOSSIL, SALVAGE_CAPSULE, SALVAGED_SCRAP, FULGURITE));
        order.addAll(List.of(OUTPOST_TRADE_TERMINAL, REINFORCED_DOOR, REINFORCED_PLATING, FACILITY_TERMINAL, OBSERVATORY_TERMINAL, KNOWLEDGE_STONE));
        order.addAll(List.of(SENTINEL_PILLAR_PLATING, SENTINEL_PILLAR_LIGHT, SENTINEL_PILLAR_TERMINAL));
        order.addAll(List.of(BASE_BEACON, TELEPORTER));
        order.addAll(List.of(TRADE_TERMINAL, MISSION_BOARD, SHIP_REPAIR, SHIP_DEALER, MULTITOOL_CABINET, FREIGHTER_CONSOLE, FLEET_TERMINAL, STATION_TELEPORTER, STATION_HULL, STATION_FLOOR, STATION_GLASS, STATION_LIGHT, STATION_PAD, STATION_TRIM, STATION_SOLAR_PANEL, FREIGHTER_PLATE, FREIGHTER_ENGINE));
        order.addAll(List.of(SENTINEL_SCRAP, QUADRUPED_SERVO, WALKER_BRAIN, SENTINEL_SPAWN_EGG, HEAVY_SENTINEL_SPAWN_EGG, QUADRUPED_SENTINEL_SPAWN_EGG,
                WALKER_SENTINEL_SPAWN_EGG, PIRATE_SCRAP));
        order.addAll(ORES.values());
        order.addAll(ORE_DROPS.values());
        order.addAll(ORE_SMELTED.values());
        order.addAll(List.of(TESLA_TOWER, WIND_TURBINE, SOLAR_PANEL, THERMAL_PLANT, ACCUMULATOR, MINING_MACHINE, WATER_PUMP, OIL_EXTRACTOR,
                ARC_SMELTER, ASSEMBLER, OIL_REFINERY, MATRIX_LAB, CONVEYOR_BELT, CONVEYOR_BELT_MK2, CONVEYOR_BELT_MK3,
                SORTER, SORTER_MK2, SORTER_MK3, SPLITTER, STORAGE_DEPOT, STORAGE_TANK, CRUDE_OIL_SEEP,
                ASSEMBLER_MK2, ASSEMBLER_MK3, PLANE_SMELTER, CHEMICAL_PLANT, FRACTIONATOR, ADVANCED_MINING_MACHINE, FACTORY_ANCHOR,
                PLANETARY_LOGISTICS_STATION, INTERSTELLAR_LOGISTICS_STATION, ORBITAL_COLLECTOR, LOGISTICS_DISTRIBUTOR,
                SPRAY_COATER, AUTOMATIC_PILER, TRAFFIC_MONITOR, PILE_SORTER, WIRELESS_POWER_TOWER, SATELLITE_SUBSTATION,
                GEOTHERMAL_POWER_STATION, ENERGY_EXCHANGER, FOUNDATION,
                EM_RAIL_EJECTOR, VERTICAL_LAUNCHING_SILO, RAY_RECEIVER, MINIATURE_PARTICLE_COLLIDER, MINI_FUSION_POWER_PLANT, ARTIFICIAL_STAR,
                NEGENTROPY_SMELTER, RECOMPOSING_ASSEMBLER, QUANTUM_CHEMICAL_PLANT, SELF_EVOLUTION_LAB,
                GEAR, MAGNETIC_COIL, CIRCUIT_BOARD, ELECTRIC_MOTOR, STEEL, PRISM, PLASMA_EXCITER, ENERGETIC_GRAPHITE, COMBUSTIBLE_UNIT,
                PROLIFERATOR_MK1, PROLIFERATOR_MK2, PROLIFERATOR_MK3, HYDROGEN_FUEL_ROD,
                WATER, CRUDE_OIL, REFINED_OIL, HYDROGEN, DEUTERIUM, ELECTROMAGNETIC_MATRIX, ENERGY_MATRIX,
                CRYSTAL_SILICON, TITANIUM_ALLOY, SYNTHETIC_DIAMOND, PLASTIC, SULFURIC_ACID, GRAPHENE, CARBON_NANOTUBE, TITANIUM_CRYSTAL, MICROCRYSTALLINE_COMPONENT, PROCESSOR,
                PARTICLE_BROADBAND, THRUSTER, REINFORCED_THRUSTER, ELECTROMAGNETIC_TURBINE, SUPER_MAGNETIC_RING, PARTICLE_CONTAINER, LOGISTICS_DRONE, LOGISTICS_VESSEL, LOGISTICS_BOT, STRUCTURE_MATRIX, INFORMATION_MATRIX,
                PHOTON_COMBINER, SOLAR_SAIL, FRAME_MATERIAL, DYSON_SPHERE_COMPONENT, SMALL_CARRIER_ROCKET, DEUTERON_FUEL_ROD, TITANIUM_GLASS,
                CASIMIR_CRYSTAL, PLANE_FILTER, QUANTUM_CHIP, GRAVITON_LENS, CRITICAL_PHOTON, GRAVITY_MATRIX,
                ANTIMATTER, ANNIHILATION_CONSTRAINT_SPHERE, ANTIMATTER_FUEL_ROD, COMPOSITE_MATRIX, UNIVERSE_MATRIX));
        // M16 黑雾（ADR-0028）
        order.addAll(List.of(ENERGY_SHARD, SILICON_NEURON, MATTER_RECOMBINATOR, NEGENTROPY_SINGULARITY, CORE_ELEMENT, DARK_FOG_MATRIX,
                MAGNUM_AMMO_BOX, TITANIUM_AMMO_BOX, SUPERALLOY_AMMO_BOX, SHELL_SET, HIGH_EXPLOSIVE_SHELL_SET, CRYSTAL_SHELL_SET,
                MISSILE_SET, SUPERSONIC_MISSILE_SET, GRAVITY_MISSILE_SET, JAMMING_CAPSULE, EXPLOSIVE_UNIT, CRYSTAL_EXPLOSIVE_UNIT));
        order.add(BATTLEFIELD_ANALYSIS_BASE);
        order.addAll(List.of(GAUSS_TURRET, LASER_TURRET, IMPLOSION_CANNON, MISSILE_TURRET));
        order.addAll(List.of(SIGNAL_TOWER, JAMMER_TOWER));
        order.addAll(List.of(PLASMA_TURRET, SR_PLASMA_TURRET, PLASMA_CAPSULE, ANTIMATTER_CAPSULE));
        // M16 黑雾：等离子炮塔（T）的创造物品栏顺序加在这行上面
        order.add(PLANETARY_SHIELD_GENERATOR);
        // M16 黑雾：行星护盾（S）的创造物品栏顺序加在这行上面
        order.add(ACID_BUCKET);
        // M17 收尾：酸液（A）的创造物品栏顺序加在这行上面
        order.add(GRID_INTERFACE);
        // M17 收尾：互通（C）的创造物品栏顺序加在这行上面
        order.addAll(List.of(PROTOTYPE, PRECISION_DRONE, ATTACK_DRONE, CORVETTE, DESTROYER));
        // M18 舰队和战斗（A）的创造物品栏顺序加在这行上面
        // M18 物流调度（B）的创造物品栏顺序加在这行上面
        // M18 统计和研究（C）的创造物品栏顺序加在这行上面
        order.addAll(List.of(DARK_FOG_LENS, HOLO_BEACON));
        // M18 信标、透镜、矿脉、蓝图（D）的创造物品栏顺序加在这行上面
        order.addAll(FISH.values());
        order.addAll(BAITS.values());
        order.addAll(JUNK.values());
        order.addAll(List.of(MESSAGE_BOTTLE, SEALED_CACHE, FISH_TRAP));
        // M19 钓鱼（A）的创造物品栏顺序加在这行上面
        // M19 聚落（B）的创造物品栏顺序加在这行上面
        order.addAll(List.of(FOSSIL_PEDESTAL, SKELETON_DISPLAY, FOSSIL_EXHIBIT));
        order.addAll(BONES.values());
        // M19 化石（C）的创造物品栏顺序加在这行上面
        order.addAll(List.of(SCRAP_METAL, ALLOY_PLATE, SALVAGED_ELECTRONICS, SALVAGED_REACTOR_CORE));
        // M19 深空和重力枪（D）的创造物品栏顺序加在这行上面
        order.addAll(GEYSER_CRUSTS.values());
        order.addAll(GEYSER_VENTS.values());
        // M19 科技布局和局部危害（E）的创造物品栏顺序加在这行上面
        order.addAll(M20_SHARED);
        order.addAll(List.of(ATMOSPHERIC_EXTRACTOR, ELECTROLYZER));
        // M20 化学（A）的创造物品栏顺序加在这行上面
        order.add(CHEMICAL_REACTOR);
        order.addAll(M20_REACTIONS);
        // M20 反应釜（B）的创造物品栏顺序加在这行上面
        order.addAll(List.of(SAMPLE_TUBE, SAMPLE, INCUBATOR, SEQUENCER, GENE_EDITOR));
        // M20 微生物和基因（C）的创造物品栏顺序加在这行上面
        order.addAll(List.of(BIOREACTOR));
        // M20 生物反应器（D）的创造物品栏顺序加在这行上面
        order.addAll(List.of(ATMOSPHERE_RELEASE_TOWER, ECOLOGY_SEEDER, ORBITAL_MIRROR));
        // M20 星球改造（E）的创造物品栏顺序加在这行上面
        return List.copyOf(order);
    }

    private static Map<String, DeferredItem<? extends Item>> registerResources() {
        Map<String, DeferredItem<? extends Item>> items = new LinkedHashMap<>();
        items.put("oxygen", ITEMS.registerItem("oxygen",
                properties -> new ResourceItem.Refill("oxygen", SuitRefillItem.Kind.OXYGEN, OXYGEN_AMOUNT, null, properties)));
        items.put("sodium", ITEMS.registerItem("sodium",
                properties -> new ResourceItem.Refill("sodium", SuitRefillItem.Kind.HAZARD, SODIUM_AMOUNT, null, properties)));
        resource(items, "star_bulb", new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build());
        resource(items, "frost_crystal", null);
        resource(items, "solanium", null);
        resource(items, "gamma_root", null);
        resource(items, "fungal_mould", null);
        resource(items, "cactus_flesh", new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build());
        resource(items, "gravitino_ball", null);
        resource(items, "marrow_bulb", new FoodProperties.Builder().nutrition(2).saturationModifier(0.4F).build());
        // 烛光海带的气囊里是氧气（ADR-0026）
        items.put("kelp_pod", ITEMS.registerItem("kelp_pod",
                properties -> new ResourceItem.Refill("kelp_pod", SuitRefillItem.Kind.OXYGEN, KELP_POD_AMOUNT, null, properties)));
        return items;
    }

    private static Map<Dish, DeferredItem<DishItem>> registerDishes() {
        Map<Dish, DeferredItem<DishItem>> items = new EnumMap<>(Dish.class);
        for (Dish dish : Dish.values()) {
            items.put(dish, ITEMS.registerItem(dish.id(), properties -> new DishItem(dish, properties), properties -> {
                if (!dish.edible()) {
                    return properties;
                }
                FoodProperties food = new FoodProperties.Builder().nutrition(dish.nutrition()).saturationModifier(dish.saturation()).build();
                // 汁是喝的
                return dish == Dish.CACTUS_JUICE ? properties.food(food, Consumables.DEFAULT_DRINK) : properties.food(food);
            }));
        }
        return items;
    }

    private static Map<Hazard, DeferredItem<SuitRefillItem>> registerGels() {
        Map<Hazard, DeferredItem<SuitRefillItem>> items = new EnumMap<>(Hazard.class);
        gel(items, Hazard.COLD, "cold_gel");
        gel(items, Hazard.HEAT, "heat_gel");
        gel(items, Hazard.RADIATION, "radiation_gel");
        gel(items, Hazard.TOXIC, "toxin_gel");
        return items;
    }

    private static void gel(Map<Hazard, DeferredItem<SuitRefillItem>> items, Hazard hazard, String name) {
        items.put(hazard, ITEMS.registerItem(name,
                properties -> new SuitRefillItem(SuitRefillItem.Kind.HAZARD, GEL_AMOUNT, hazard, properties), properties -> properties.stacksTo(16)));
    }

    private static void resource(Map<String, DeferredItem<? extends Item>> items, String name, @Nullable FoodProperties food) {
        items.put(name, ITEMS.registerItem(name, properties -> new ResourceItem(name, properties),
                properties -> food == null ? properties : properties.food(food)));
    }

    private UniverseItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
