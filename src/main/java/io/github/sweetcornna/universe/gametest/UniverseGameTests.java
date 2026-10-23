package io.github.sweetcornna.universe.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.block.CreatureEggBlockEntity;
import io.github.sweetcornna.universe.block.EggSequencerBlockEntity;
import io.github.sweetcornna.universe.block.HydroponicTrayBlockEntity;
import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.portal.PortalGlyphBlock;
import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.block.ship.LandingPadBlockEntity;
import io.github.sweetcornna.universe.core.factory.Cargo;
import io.github.sweetcornna.universe.core.factory.Proliferation;
import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.Fleet;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateType;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.core.portal.PortalAddress;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.ship.CrashedShip;
import io.github.sweetcornna.universe.core.ship.ShipDesign;
import io.github.sweetcornna.universe.core.ship.ShipDesigner;
import io.github.sweetcornna.universe.core.ship.ShipMarket;
import io.github.sweetcornna.universe.core.ship.ShipType;
import io.github.sweetcornna.universe.core.space.DriveTier;
import io.github.sweetcornna.universe.core.survival.Dish;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.entity.ship.ShipAssembly;
import io.github.sweetcornna.universe.entity.ship.ShipBlueprints;
import io.github.sweetcornna.universe.entity.ship.ShipEntity;
import io.github.sweetcornna.universe.entity.ship.ShipOwners;
import io.github.sweetcornna.universe.factory.FuelRecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import io.github.sweetcornna.universe.core.survival.Acid;
import io.github.sweetcornna.universe.fluid.AcidFluid;
import io.github.sweetcornna.universe.factory.resource.WaterPumpBlockEntity;
import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.BeltNetwork;
import io.github.sweetcornna.universe.factory.belt.BeltPassBlock;
import io.github.sweetcornna.universe.factory.belt.SprayCoaterBlockEntity;
import io.github.sweetcornna.universe.factory.belt.StorageDepotBlockEntity;
import io.github.sweetcornna.universe.factory.power.FuelGeneratorBlockEntity;
import io.github.sweetcornna.universe.factory.power.GeothermalBlockEntity;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.factory.research.ResearchData;
import io.github.sweetcornna.universe.fleet.FleetData;
import io.github.sweetcornna.universe.fleet.FleetTerminal;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.logistics.StationHandler;
import io.github.sweetcornna.universe.poi.PoiInteractions;
import io.github.sweetcornna.universe.portal.Portals;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseEntities;
import io.github.sweetcornna.universe.sentinel.QuadrupedSentinel;
import io.github.sweetcornna.universe.sentinel.RepairSentinel;
import io.github.sweetcornna.universe.sentinel.WalkerSentinel;
import net.minecraft.world.damagesource.DamageSource;
import io.github.sweetcornna.universe.sentinel.Sentinel;
import io.github.sweetcornna.universe.sentinel.SentinelPillars;
import io.github.sweetcornna.universe.sentinel.Sentinels;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.block.ClamBlock;
import io.github.sweetcornna.universe.block.poi.SalvageCapsuleBlock;
import io.github.sweetcornna.universe.block.landmark.KnowledgeStoneBlock;
import io.github.sweetcornna.universe.block.landmark.ReinforcedDoorBlock;
import io.github.sweetcornna.universe.core.poi.DoorBreach;
import io.github.sweetcornna.universe.poi.OutpostInteractions;
import io.github.sweetcornna.universe.world.planet.Fulgurites;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import io.github.sweetcornna.universe.entity.PlasmaGrenade;
import io.github.sweetcornna.universe.survival.WeaponFire;
import net.minecraft.world.entity.item.ItemEntity;
import io.github.sweetcornna.universe.entity.AquaticCreature;
import io.github.sweetcornna.universe.exocraft.Exocraft;
import io.github.sweetcornna.universe.exocraft.HeavyRover;
import io.github.sweetcornna.universe.exocraft.HoverCar;
import io.github.sweetcornna.universe.exocraft.Submarine;
import io.github.sweetcornna.universe.exocraft.VehicleRefiner;
import io.github.sweetcornna.universe.exocraft.VehicleRoster;
import io.github.sweetcornna.universe.exocraft.VehicleSummoning;
import io.github.sweetcornna.universe.exocraft.Torpedo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.civ.CivCodecs;
import io.github.sweetcornna.universe.core.civ.CivState;
import io.github.sweetcornna.universe.core.civ.Guild;
import io.github.sweetcornna.universe.core.civ.Lexicon;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.survival.ToolWeapon;
import io.github.sweetcornna.universe.core.trade.MultitoolMarket;
import io.github.sweetcornna.universe.entity.ship.ShipDealer;
import io.github.sweetcornna.universe.station.MultitoolCabinet;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.tech.TechMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.component.ItemContainerContents;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Husbandry;
import io.github.sweetcornna.universe.entity.AlienCreature;
import io.github.sweetcornna.universe.entity.CreatureEggs;
import io.github.sweetcornna.universe.core.ecology.CreatureTraits;
import io.github.sweetcornna.universe.core.ecology.Companion;
import io.github.sweetcornna.universe.core.ecology.Temperament;
import io.github.sweetcornna.universe.entity.FaunaSpawner;

/**
 * 游戏内测试（ADR-0019）：在真的世界里搭几台设备，看电网、配方、传送带、研究站连起来能不能转。单元测试只管 core，
 * 这里管适配层。{@code ./gradlew runGameTestServer} 跑完全部测试后退出，有失败时退出码非零；开发客户端里也能用
 * {@code /test} 命令单个跑。
 *
 * <p>只在 NeoForge 打开游戏内测试时（开发环境）注册，正式版里没有这些条目，所以不放在 registry 包里。测试场地是
 * 一块 7×4×7、底下一层石台的空结构（{@code data/universe/structure/gametest/platform.nbt}，由 {@code tools/gametest_platform.py} 生成）。
 */
public final class UniverseGameTests {
    /** 黑雾测试四周留的空地：比炮塔、信号塔、战场分析基地的范围都大。 */
    private static final int DARK_FOG_PADDING = 48;
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Universe.MOD_ID);
    private static final Identifier PLATFORM = Universe.id("gametest/platform");
    /** 17×8×17、石头地面的大场地，放整艘设计出来的船（ADR-0024）。 */
    private static final Identifier HANGAR = Universe.id("gametest/hangar");
    /** 13×12×11 的石台，放得下一整座先驱者传送门。 */
    private static final Identifier PORTAL = Universe.id("gametest/portal");
    private static final String GEAR_RECIPE = "universe:assembling/gear";
    private static final String LAB_TECH = "fluid_storage";
    // 字段按顺序初始化：这几个要排在 FUNCTIONS 后面
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLER_MAKES_GEARS = function("assembler_makes_gears",
            UniverseGameTests::assemblerMakesGears);
    private static final ResourceKey<Consumer<GameTestHelper>> ASSEMBLER_WAITS_FOR_POWER = function("assembler_waits_for_power",
            UniverseGameTests::assemblerWaitsForPower);
    private static final ResourceKey<Consumer<GameTestHelper>> BELT_DELIVERS_TO_DEPOT = function("belt_delivers_to_depot",
            UniverseGameTests::beltDeliversToDepot);
    private static final ResourceKey<Consumer<GameTestHelper>> LAB_ADDS_POINTS = function("lab_adds_points", UniverseGameTests::labAddsPoints);
    private static final ResourceKey<Consumer<GameTestHelper>> COATER_SPRAYS_CARGO = function("coater_sprays_cargo",
            UniverseGameTests::coaterSpraysCargo);
    private static final ResourceKey<Consumer<GameTestHelper>> PILER_STACKS_CARGO = function("piler_stacks_cargo",
            UniverseGameTests::pilerStacksCargo);
    private static final ResourceKey<Consumer<GameTestHelper>> SPRAYED_INPUT_MAKES_MORE = function("sprayed_input_makes_more",
            UniverseGameTests::sprayedInputMakesMore);
    private static final ResourceKey<Consumer<GameTestHelper>> STACKED_LABS_SHARE = function("stacked_labs_share",
            UniverseGameTests::stackedLabsShare);
    private static final ResourceKey<Consumer<GameTestHelper>> GEOTHERMAL_NEEDS_LAVA = function("geothermal_needs_lava",
            UniverseGameTests::geothermalNeedsLava);
    private static final ResourceKey<Consumer<GameTestHelper>> STATION_KEEPS_POINTS = function("station_keeps_points",
            UniverseGameTests::stationKeepsPoints);
    private static final ResourceKey<Consumer<GameTestHelper>> DISTRIBUTORS_SHARE_ITEMS = function("distributors_share_items",
            UniverseGameTests::distributorsShareItems);
    private static final ResourceKey<Consumer<GameTestHelper>> HELM_TECH_TUNES_SHIP = function("helm_tech_tunes_ship",
            UniverseGameTests::helmTechTunesShip);
    private static final ResourceKey<Consumer<GameTestHelper>> LANDING_PAD_REFUELS_SHIP = function("landing_pad_refuels_ship",
            UniverseGameTests::landingPadRefuelsShip);
    private static final ResourceKey<Consumer<GameTestHelper>> HYDROPONIC_TRAY_GROWS = function("hydroponic_tray_grows",
            UniverseGameTests::hydroponicTrayGrows);
    private static final ResourceKey<Consumer<GameTestHelper>> SEQUENCER_GROWS_EGG = function("sequencer_grows_egg",
            UniverseGameTests::sequencerGrowsEgg);
    private static final ResourceKey<Consumer<GameTestHelper>> EGG_HATCHES_OWNED = function("egg_hatches_owned",
            UniverseGameTests::eggHatchesOwned);
    private static final ResourceKey<Consumer<GameTestHelper>> FUEL_MACHINES_COOK = function("fuel_machines_cook",
            UniverseGameTests::fuelMachinesCook);
    private static final ResourceKey<Consumer<GameTestHelper>> FLEET_SAVE_ROUND_TRIP = function("fleet_save_round_trip",
            UniverseGameTests::fleetSaveRoundTrip);
    private static final ResourceKey<Consumer<GameTestHelper>> FLEET_VOYAGE_CLAIM = function("fleet_voyage_claim",
            UniverseGameTests::fleetVoyageClaim);
    private static final ResourceKey<Consumer<GameTestHelper>> DEALER_SHIPS_ASSEMBLE = function("dealer_ships_assemble",
            UniverseGameTests::dealerShipsAssemble);
    private static final ResourceKey<Consumer<GameTestHelper>> CRASHED_SHIP_REPAIRS = function("crashed_ship_repairs",
            UniverseGameTests::crashedShipRepairs);
    private static final ResourceKey<Consumer<GameTestHelper>> PORTAL_DIALS_AND_CLOSES = function("portal_dials_and_closes",
            UniverseGameTests::portalDialsAndCloses);
    private static final ResourceKey<Consumer<GameTestHelper>> CLAM_GIVES_PEARL = function("clam_gives_pearl",
            UniverseGameTests::clamGivesPearl);
    private static final ResourceKey<Consumer<GameTestHelper>> SUBMARINE_HOVERS = function("submarine_hovers",
            UniverseGameTests::submarineHovers);
    private static final ResourceKey<Consumer<GameTestHelper>> TORPEDO_HITS_CREATURE = function("torpedo_hits_creature",
            UniverseGameTests::torpedoHitsCreature);
    private static final ResourceKey<Consumer<GameTestHelper>> FOSSIL_DIRT_GIVES_FOSSIL = function("fossil_dirt_gives_fossil",
            UniverseGameTests::fossilDirtGivesFossil);
    private static final ResourceKey<Consumer<GameTestHelper>> SALVAGE_CAPSULE_OPENS_ONCE = function("salvage_capsule_opens_once",
            UniverseGameTests::salvageCapsuleOpensOnce);
    private static final ResourceKey<Consumer<GameTestHelper>> REINFORCED_DOOR_CUTS_OPEN = function("reinforced_door_cuts_open",
            UniverseGameTests::reinforcedDoorCutsOpen);
    private static final ResourceKey<Consumer<GameTestHelper>> LIGHTNING_FUSES_FULGURITE = function("lightning_fuses_fulgurite",
            UniverseGameTests::lightningFusesFulgurite);
    private static final ResourceKey<Consumer<GameTestHelper>> QUAD_BACK_HIT_DOUBLES = function("quad_back_hit_doubles",
            UniverseGameTests::quadBackHitDoubles);
    private static final ResourceKey<Consumer<GameTestHelper>> GROUND_SENTINELS_WALK = function("ground_sentinels_walk",
            UniverseGameTests::groundSentinelsWalk);
    private static final ResourceKey<Consumer<GameTestHelper>> PILLAR_TERMINAL_SHUTS_DOWN = function("pillar_terminal_shuts_down",
            UniverseGameTests::pillarTerminalShutsDown);
    private static final ResourceKey<Consumer<GameTestHelper>> PILLAR_GIVES_SENTINEL_TOOL = function("pillar_gives_sentinel_tool",
            UniverseGameTests::pillarGivesSentinelTool);
    private static final ResourceKey<Consumer<GameTestHelper>> CABINET_MOVES_MODULES = function("cabinet_moves_modules",
            UniverseGameTests::cabinetMovesModules);
    private static final ResourceKey<Consumer<GameTestHelper>> REPAIR_DRONE_HEALS = function("repair_drone_heals",
            UniverseGameTests::repairDroneHeals);
    private static final ResourceKey<Consumer<GameTestHelper>> GRENADE_SPARES_BLOCKS = function("grenade_spares_blocks",
            UniverseGameTests::grenadeSparesBlocks);
    private static final ResourceKey<Consumer<GameTestHelper>> SCATTER_HITS_CLOSE = function("scatter_hits_close",
            UniverseGameTests::scatterHitsClose);
    private static final ResourceKey<Consumer<GameTestHelper>> JAVELIN_HITS_FIRST = function("javelin_hits_first",
            UniverseGameTests::javelinHitsFirst);
    private static final ResourceKey<Consumer<GameTestHelper>> HOVER_CAR_FLOATS_ON_WATER = function("hover_car_floats_on_water",
            UniverseGameTests::hoverCarFloatsOnWater);
    private static final ResourceKey<Consumer<GameTestHelper>> SUMMONER_FETCHES_VEHICLE = function("summoner_fetches_vehicle",
            UniverseGameTests::summonerFetchesVehicle);
    private static final ResourceKey<Consumer<GameTestHelper>> HEAVY_ROVER_REFINES = function("heavy_rover_refines",
            UniverseGameTests::heavyRoverRefines);
    private static final ResourceKey<Consumer<GameTestHelper>> GIANT_NEEDS_ROOM = function("giant_needs_room",
            UniverseGameTests::giantNeedsRoom);
    private static final ResourceKey<Consumer<GameTestHelper>> HATCHED_BABY_IS_COMPANION = function("hatched_baby_is_companion",
            UniverseGameTests::hatchedBabyIsCompanion);
    private static final ResourceKey<Consumer<GameTestHelper>> COMPANION_DIGS_ONCE_A_DAY = function("companion_digs_once_a_day",
            UniverseGameTests::companionDigsOnceADay);
    private static final ResourceKey<Consumer<GameTestHelper>> CIV_TEACHES_WORDS = function("civ_teaches_words",
            UniverseGameTests::civTeachesWords);
    private static final ResourceKey<Consumer<GameTestHelper>> KNOWLEDGE_STONE_TEACHES_ONCE = function("knowledge_stone_teaches_once",
            UniverseGameTests::knowledgeStoneTeachesOnce);
    // 外星 NPC 和对话（ADR-0027 §三、§四），测试写在 NpcGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> NPC_RIGHT_ANSWER_TEACHES = function("npc_right_answer_teaches",
            NpcGameTests::rightAnswerTeachesWord);
    private static final ResourceKey<Consumer<GameTestHelper>> NPC_GIFT_ONCE_A_DAY = function("npc_gift_once_a_day", NpcGameTests::giftOnceADay);
    private static final ResourceKey<Consumer<GameTestHelper>> NPC_HIT_COSTS_STANDING = function("npc_hit_costs_standing",
            NpcGameTests::hitCostsStanding);
    // 技术商的货（ADR-0027 §六），测试写在 TechShopGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> TECH_SHOP_ONCE_A_DAY = function("tech_shop_once_a_day",
            TechShopGameTests::merchantSellsOncePerDay);
    // 公会（ADR-0027 §六），测试写在 GuildGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> GUILD_MISSION_STANDING = function("guild_mission_standing",
            GuildGameTests::missionRaisesStanding);
    private static final ResourceKey<Consumer<GameTestHelper>> GUILD_ENVOY_OFFERS = function("guild_envoy_offers",
            GuildGameTests::envoyOffersFixedForTheDay);
    private static final ResourceKey<Consumer<GameTestHelper>> GUILD_GIFT_ONCE_PER_RANK = function("guild_gift_once_per_rank",
            GuildGameTests::giftOncePerRank);
    // M16 黑雾（ADR-0028）：各自的测试写在自己的类里，函数加在自己那行上面
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_CORE_LEAVES_REMNANT = function("dark_fog_core_leaves_remnant",
            DarkFogBaseGameTests::coreDestroyedLeavesRemnant);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_BASE_SAVES = function("dark_fog_base_saves",
            DarkFogBaseGameTests::baseStateSurvivesSave);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_SENDS_WAVE = function("dark_fog_sends_wave",
            DarkFogBaseGameTests::fullThreatSendsWave);
    // 黑雾的单位和建筑（ADR-0028 §二），测试写在 DarkFogUnitGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_RAIDER_CLIMBS = function("dark_fog_raider_climbs",
            DarkFogUnitGameTests::raiderClimbsToRaisedTarget);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_RAIDER_STRIKES_FACILITY = function("dark_fog_raider_strikes_facility",
            DarkFogUnitGameTests::raiderStrikesFacility);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_RANGER_SHOOTS_BACK = function("dark_fog_ranger_shoots_back",
            DarkFogUnitGameTests::rangerShootsBack);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_GUARDIAN_RETURNS_HOME = function("dark_fog_guardian_returns_home",
            DarkFogUnitGameTests::guardianReturnsHome);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_UNPOWERED_LASER_HOLDS_FIRE = function("dark_fog_unpowered_laser_holds_fire",
            DarkFogUnitGameTests::unpoweredLaserTowerHoldsFire);
    // 设施的耐久、残影、战场分析基地（ADR-0028 §四、§五），测试写在 DarkFogFacilityGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> FACILITY_GHOST_RESTORES = function("facility_ghost_restores",
            DarkFogFacilityGameTests::ghostKeepsAndRestores);
    private static final ResourceKey<Consumer<GameTestHelper>> BATTLEFIELD_BASE_REPAIRS = function("battlefield_base_repairs",
            DarkFogFacilityGameTests::baseRepairsAndRebuilds);
    // 炮塔（ADR-0028 §五），测试写在 DarkFogTurretGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> GAUSS_KILLS_RAIDER = function("gauss_kills_raider",
            DarkFogTurretGameTests::gaussKillsRaider);
    private static final ResourceKey<Consumer<GameTestHelper>> LASER_NEEDS_NO_AMMO = function("laser_needs_no_ammo",
            DarkFogTurretGameTests::laserNeedsNoAmmo);
    private static final ResourceKey<Consumer<GameTestHelper>> UNPOWERED_TURRET_HOLDS_FIRE = function("unpowered_turret_holds_fire",
            DarkFogTurretGameTests::unpoweredHoldsFire);
    private static final ResourceKey<Consumer<GameTestHelper>> CANNON_IGNORES_FLYERS = function("cannon_ignores_flyers",
            DarkFogTurretGameTests::cannonIgnoresFlyers);
    private static final ResourceKey<Consumer<GameTestHelper>> JAMMER_SLOWS_RAIDER = function("jammer_slows_raider",
            DarkFogSupportGameTests::jammerSlowsRaider);
    private static final ResourceKey<Consumer<GameTestHelper>> SIGNAL_TOWER_DRAWS_DARK_FOG = function("signal_tower_draws_dark_fog",
            DarkFogSupportGameTests::signalTowerDrawsDarkFog);
    private static final ResourceKey<Consumer<GameTestHelper>> EXPLOSIVE_UNIT_SPARES_BLOCKS = function("explosive_unit_spares_blocks",
            DarkFogSupportGameTests::explosiveUnitSparesBlocks);
    private static final ResourceKey<Consumer<GameTestHelper>> SENTINEL_STRENGTH_SCALES = function("sentinel_strength_scales",
            WorldOptionsGameTests::sentinelStrengthScales);
    private static final ResourceKey<Consumer<GameTestHelper>> RESOURCE_MULTIPLIER_SCALES_VEINS = function("resource_multiplier_scales_veins",
            WorldOptionsGameTests::resourceMultiplierScalesVeins);
    private static final ResourceKey<Consumer<GameTestHelper>> RULES_READ_INTO_SETTINGS = function("rules_read_into_settings",
            WorldOptionsGameTests::rulesReadIntoSettings);
    // M16 第二块（ADR-0028 §八）：各自的测试写在自己的类里
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_HIVE_SAVES = function("dark_fog_hive_saves",
            DarkFogHiveGameTests::hiveStateSurvivesSave);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_HIVE_REBUILDS_WITH_RELAYS = function("dark_fog_hive_rebuilds_with_relays",
            DarkFogHiveGameTests::coreLossRebuildsOnlyWithRelays);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_HIVE_STRIKE_AND_THREAT = function("dark_fog_hive_strike_and_threat",
            DarkFogHiveGameTests::strikeAndRelayThreat);
    // M16 黑雾：巢穴（H）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> HIVE_BUILDING_TAKES_PLAYER_DAMAGE = function("hive_building_takes_player_damage",
            HiveBuildingGameTests::takesOnlyPlayerDamage);
    private static final ResourceKey<Consumer<GameTestHelper>> HIVE_LASER_NEEDS_POWER = function("hive_laser_needs_power",
            HiveBuildingGameTests::laserFiresOnlyWhenPowered);
    private static final ResourceKey<Consumer<GameTestHelper>> HIVE_PLASMA_HITS_PLAYER = function("hive_plasma_hits_player",
            HiveBuildingGameTests::plasmaTowerHitsPlayer);
    // M16 黑雾：巢穴建筑（B）的测试函数加在这行上面
    // 太空单位和枪骑轰炸（ADR-0028 §八），测试写在 SpaceUnitGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> SPACE_LANCER_FIRES_AT_ATTACKER = function("space_lancer_fires_at_attacker",
            SpaceUnitGameTests::lancerFiresAtAttacker);
    private static final ResourceKey<Consumer<GameTestHelper>> SPACE_HUMPBACK_SHELLS_ATTACKER = function("space_humpback_shells_attacker",
            SpaceUnitGameTests::humpbackShellsAttacker);
    private static final ResourceKey<Consumer<GameTestHelper>> LANCER_RAID_BOMBS_AND_ENDS = function("lancer_raid_bombs_and_ends",
            SpaceUnitGameTests::raidBombsAndEnds);
    private static final ResourceKey<Consumer<GameTestHelper>> SHIELD_STOPS_LANCER_BOMBS = function("shield_stops_lancer_bombs",
            SpaceUnitGameTests::shieldStopsBombs);
    // M16 黑雾：太空单位（U）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_RELAY_LANDS = function("dark_fog_relay_lands",
            DarkFogFlightGameTests::relayLandsNewBase);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_RELAY_RESTORES = function("dark_fog_relay_restores",
            DarkFogFlightGameTests::relayRestoresLostRelay);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_RELAY_AVOIDS_SHIELDS = function("dark_fog_relay_avoids_shields",
            DarkFogFlightGameTests::relayAvoidsShields);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_SEED_FOUNDS_HIVE = function("dark_fog_seed_founds_hive",
            DarkFogFlightGameTests::seedFoundsHive);
    private static final ResourceKey<Consumer<GameTestHelper>> DARK_FOG_CRAFT_SHOT_DOWN = function("dark_fog_craft_shot_down",
            DarkFogFlightGameTests::craftShotDownEndsFlight);
    // M16 黑雾：中继站和种子（R）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> PLASMA_KILLS_RAIDER = function("plasma_kills_raider",
            PlasmaTurretGameTests::plasmaKillsRaider);
    private static final ResourceKey<Consumer<GameTestHelper>> SR_PLASMA_NEEDS_NO_AMMO = function("sr_plasma_needs_no_ammo",
            PlasmaTurretGameTests::shortRangeNeedsNoAmmo);
    private static final ResourceKey<Consumer<GameTestHelper>> GAUSS_IGNORES_HIGH_ALTITUDE = function("gauss_ignores_high_altitude",
            PlasmaTurretGameTests::gaussIgnoresHighAltitude);
    private static final ResourceKey<Consumer<GameTestHelper>> MISSILE_AIMS_AT_HIGH_ALTITUDE = function("missile_aims_at_high_altitude",
            PlasmaTurretGameTests::missileAimsAtHighAltitude);
    private static final ResourceKey<Consumer<GameTestHelper>> PLASMA_HITS_HIGH_ALTITUDE = function("plasma_hits_high_altitude",
            PlasmaTurretGameTests::plasmaHitsHighAltitude);
    private static final ResourceKey<Consumer<GameTestHelper>> FLEET_SORTIE_STRIKES_HIVE = function("fleet_sortie_strikes_hive",
            PlasmaTurretGameTests::sortieStrikesHive);
    // M16 黑雾：等离子炮塔（T）的测试函数加在这行上面
    // 行星护盾（ADR-0028 §八），测试写在 ShieldGameTests
    private static final ResourceKey<Consumer<GameTestHelper>> SHIELD_CHARGES_AND_RAISES = function("shield_charges_and_raises",
            ShieldGameTests::chargesAndRaises);
    private static final ResourceKey<Consumer<GameTestHelper>> SHIELD_ABSORBS_AND_COLLAPSES = function("shield_absorbs_and_collapses",
            ShieldGameTests::absorbsAndCollapses);
    private static final ResourceKey<Consumer<GameTestHelper>> SHIELD_LIMIT_AND_OFFLINE_COVERS = function("shield_limit_and_offline_covers",
            ShieldGameTests::limitAndOfflineCovers);
    // M16 黑雾：行星护盾（S）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> ACID_BURNS = function("acid_burns", UniverseGameTests::acidBurns);
    private static final ResourceKey<Consumer<GameTestHelper>> ACID_CORRODES_ITEMS = function("acid_corrodes_items",
            UniverseGameTests::acidCorrodesItems);
    private static final ResourceKey<Consumer<GameTestHelper>> PUMP_MAKES_SULFURIC_ACID = function("pump_makes_sulfuric_acid",
            UniverseGameTests::pumpMakesSulfuricAcid);
    // M17 收尾：酸液（A）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> GRID_INTERFACE_OUTPUT_FEEDS_NEIGHBOR = function(
            "grid_interface_output_feeds_neighbor", InteropGameTests::outputFeedsNeighbor);
    private static final ResourceKey<Consumer<GameTestHelper>> GRID_INTERFACE_INPUT_FEEDS_GRID = function("grid_interface_input_feeds_grid",
            InteropGameTests::inputFeedsGrid);
    private static final ResourceKey<Consumer<GameTestHelper>> TANK_AND_PUMP_FLUIDS = function("tank_and_pump_fluids",
            InteropGameTests::tankAndPumpFluids);
    // M17 收尾：互通（C）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> DYSON_FREE_DESIGN_APPLIES = function("dyson_free_design_applies",
            DysonEditGameTests::freeDesignApplies);
    private static final ResourceKey<Consumer<GameTestHelper>> DYSON_PAINT_STICKS_AND_STALE_FAILS = function("dyson_paint_sticks_and_stale_fails",
            DysonEditGameTests::paintSticksAndStaleFails);
    private static final ResourceKey<Consumer<GameTestHelper>> DYSON_BLUEPRINT_ROUND_TRIPS = function("dyson_blueprint_round_trips",
            DysonEditGameTests::blueprintRoundTrips);
    private static final ResourceKey<Consumer<GameTestHelper>> DYSON_BUILT_NODE_CANNOT_BE_REMOVED = function("dyson_built_node_cannot_be_removed",
            DysonEditGameTests::builtNodeCannotBeRemoved);
    // M17 收尾：戴森球（D）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> HOPPER_PULLS_FROM_STATION = function("hopper_pulls_from_station",
            FactoryTailGameTests::hopperPullsFromStation);
    private static final ResourceKey<Consumer<GameTestHelper>> COMPARATOR_READS_STATION = function("comparator_reads_station",
            FactoryTailGameTests::comparatorReadsStation);
    private static final ResourceKey<Consumer<GameTestHelper>> LENS_RECEIVES_WITHOUT_SUN = function("lens_receives_without_sun",
            FactoryTailGameTests::lensReceivesWithoutSun);
    private static final ResourceKey<Consumer<GameTestHelper>> BLUEPRINT_KEEPS_SETTINGS = function("blueprint_keeps_settings",
            FactoryTailGameTests::blueprintKeepsSettings);
    // M17 收尾：工厂物流（E）的测试函数加在这行上面
    private static final ResourceKey<Consumer<GameTestHelper>> FLYING_SHIP_CARGO = function("flying_ship_cargo",
            ShipTailGameTests::flyingShipCargo);
    private static final ResourceKey<Consumer<GameTestHelper>> SHIP_WITHOUT_HOLDS = function("ship_without_holds",
            ShipTailGameTests::shipWithoutHolds);
    private static final ResourceKey<Consumer<GameTestHelper>> ENTITY_GRAVITY_FOLLOWS_PLANET = function("entity_gravity_follows_planet",
            ShipTailGameTests::entityGravityFollowsPlanet);
    private static final ResourceKey<Consumer<GameTestHelper>> PLACED_PLANT_PICKS_LOCAL_SPECIES = function("placed_plant_picks_local_species",
            ShipTailGameTests::placedPlantPicksLocalSpecies);
    // M17 收尾：飞船和星球（F）的测试函数加在这行上面

    private UniverseGameTests() {
    }

    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(RegisterGameTestsEvent.class, UniverseGameTests::registerTests);
    }

    private static ResourceKey<Consumer<GameTestHelper>> function(String name, Consumer<GameTestHelper> test) {
        FUNCTIONS.register(name, () -> test);
        return ResourceKey.create(Registries.TEST_FUNCTION, Universe.id(name));
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Universe.id("default"));
        // 关停守卫机对整个维度生效，单独一批跑，免得别的测试里的守卫机跟着撤走
        Holder<TestEnvironmentDefinition<?>> alone = event.registerEnvironment(Universe.id("sentinels_off"));
        test(event, environment, ASSEMBLER_MAKES_GEARS, 200);
        test(event, environment, ASSEMBLER_WAITS_FOR_POWER, 100);
        test(event, environment, BELT_DELIVERS_TO_DEPOT, 200);
        test(event, environment, LAB_ADDS_POINTS, 400);
        test(event, environment, COATER_SPRAYS_CARGO, 200);
        test(event, environment, PILER_STACKS_CARGO, 200);
        test(event, environment, SPRAYED_INPUT_MAKES_MORE, 600);
        test(event, environment, STACKED_LABS_SHARE, 100);
        test(event, environment, GEOTHERMAL_NEEDS_LAVA, 60);
        test(event, environment, STATION_KEEPS_POINTS, 40);
        test(event, environment, DISTRIBUTORS_SHARE_ITEMS, 200);
        test(event, environment, HELM_TECH_TUNES_SHIP, 40);
        test(event, environment, LANDING_PAD_REFUELS_SHIP, 300);
        test(event, environment, HYDROPONIC_TRAY_GROWS, 200);
        test(event, environment, SEQUENCER_GROWS_EGG, 200);
        test(event, environment, EGG_HATCHES_OWNED, 40);
        test(event, environment, FUEL_MACHINES_COOK, 200);
        test(event, environment, DEALER_SHIPS_ASSEMBLE, HANGAR, 40);
        test(event, environment, CRASHED_SHIP_REPAIRS, HANGAR, 40);
        test(event, environment, PORTAL_DIALS_AND_CLOSES, PORTAL, 20);
        test(event, environment, FLEET_SAVE_ROUND_TRIP, 20);
        test(event, environment, FLEET_VOYAGE_CLAIM, 20);
        test(event, environment, CLAM_GIVES_PEARL, 40);
        test(event, environment, SUBMARINE_HOVERS, 80);
        test(event, environment, TORPEDO_HITS_CREATURE, 60);
        test(event, environment, FOSSIL_DIRT_GIVES_FOSSIL, 20);
        test(event, environment, SALVAGE_CAPSULE_OPENS_ONCE, 20);
        test(event, environment, REINFORCED_DOOR_CUTS_OPEN, 20);
        test(event, environment, LIGHTNING_FUSES_FULGURITE, 20);
        test(event, environment, QUAD_BACK_HIT_DOUBLES, 20);
        test(event, environment, GROUND_SENTINELS_WALK, 60);
        test(event, alone, PILLAR_TERMINAL_SHUTS_DOWN, 20);
        test(event, environment, PILLAR_GIVES_SENTINEL_TOOL, 20);
        test(event, environment, CABINET_MOVES_MODULES, 20);
        test(event, environment, REPAIR_DRONE_HEALS, 80);
        test(event, environment, GRENADE_SPARES_BLOCKS, 80);
        test(event, environment, SCATTER_HITS_CLOSE, 40);
        test(event, environment, JAVELIN_HITS_FIRST, 20);
        test(event, environment, HOVER_CAR_FLOATS_ON_WATER, 80);
        test(event, environment, SUMMONER_FETCHES_VEHICLE, 20);
        test(event, environment, HEAVY_ROVER_REFINES, 200);
        test(event, environment, HATCHED_BABY_IS_COMPANION, 20);
        test(event, environment, COMPANION_DIGS_ONCE_A_DAY, 120);
        test(event, environment, CIV_TEACHES_WORDS, 20);
        test(event, environment, KNOWLEDGE_STONE_TEACHES_ONCE, 20);
        test(event, environment, NPC_RIGHT_ANSWER_TEACHES, 20);
        test(event, environment, NPC_GIFT_ONCE_A_DAY, 20);
        test(event, environment, NPC_HIT_COSTS_STANDING, 20);
        test(event, environment, TECH_SHOP_ONCE_A_DAY, 20);
        test(event, environment, GUILD_MISSION_STANDING, 20);
        test(event, environment, GUILD_ENVOY_OFFERS, 20);
        test(event, environment, GUILD_GIFT_ONCE_PER_RANK, 20);
        test(event, environment, GIANT_NEEDS_ROOM, HANGAR, 20);
        spaced(event, environment, DARK_FOG_CORE_LEAVES_REMNANT, 20);
        spaced(event, environment, DARK_FOG_BASE_SAVES, 20);
        spaced(event, environment, DARK_FOG_SENDS_WAVE, 20);
        spaced(event, environment, DARK_FOG_RAIDER_STRIKES_FACILITY, HANGAR, 300);
        spaced(event, environment, DARK_FOG_RAIDER_CLIMBS, HANGAR, 300, true);
        spaced(event, environment, DARK_FOG_RANGER_SHOOTS_BACK, HANGAR, 200);
        spaced(event, environment, DARK_FOG_GUARDIAN_RETURNS_HOME, HANGAR, 200);
        spaced(event, environment, DARK_FOG_UNPOWERED_LASER_HOLDS_FIRE, HANGAR, 200);
        test(event, environment, FACILITY_GHOST_RESTORES, 20);
        spaced(event, environment, BATTLEFIELD_BASE_REPAIRS, 200);
        spaced(event, environment, GAUSS_KILLS_RAIDER, HANGAR, 200);
        spaced(event, environment, LASER_NEEDS_NO_AMMO, HANGAR, 100);
        spaced(event, environment, UNPOWERED_TURRET_HOLDS_FIRE, HANGAR, 100);
        spaced(event, environment, CANNON_IGNORES_FLYERS, HANGAR, 100);
        spaced(event, environment, JAMMER_SLOWS_RAIDER, 200);
        spaced(event, environment, SIGNAL_TOWER_DRAWS_DARK_FOG, 200);
        spaced(event, environment, EXPLOSIVE_UNIT_SPARES_BLOCKS, 60);
        test(event, environment, SENTINEL_STRENGTH_SCALES, 20);
        test(event, environment, RESOURCE_MULTIPLIER_SCALES_VEINS, 20);
        test(event, environment, RULES_READ_INTO_SETTINGS, 20);
        test(event, environment, DARK_FOG_HIVE_SAVES, 20);
        test(event, environment, DARK_FOG_HIVE_REBUILDS_WITH_RELAYS, 20);
        test(event, environment, DARK_FOG_HIVE_STRIKE_AND_THREAT, 20);
        // M16 黑雾：巢穴（H）的测试加在这行上面
        spaced(event, environment, HIVE_BUILDING_TAKES_PLAYER_DAMAGE, HANGAR, 20);
        spaced(event, environment, HIVE_LASER_NEEDS_POWER, HANGAR, 200);
        spaced(event, environment, HIVE_PLASMA_HITS_PLAYER, HANGAR, 200);
        // M16 黑雾：巢穴建筑（B）的测试加在这行上面
        spaced(event, environment, SPACE_LANCER_FIRES_AT_ATTACKER, HANGAR, 200, true);
        spaced(event, environment, SPACE_HUMPBACK_SHELLS_ATTACKER, HANGAR, 200, true);
        spaced(event, environment, LANCER_RAID_BOMBS_AND_ENDS, HANGAR, 600, true);
        spaced(event, environment, SHIELD_STOPS_LANCER_BOMBS, HANGAR, 300, true);
        // M16 黑雾：太空单位（U）的测试加在这行上面
        test(event, environment, DARK_FOG_RELAY_LANDS, 20);
        test(event, environment, DARK_FOG_RELAY_RESTORES, 20);
        test(event, environment, DARK_FOG_RELAY_AVOIDS_SHIELDS, 20);
        test(event, environment, DARK_FOG_SEED_FOUNDS_HIVE, 20);
        spaced(event, environment, DARK_FOG_CRAFT_SHOT_DOWN, 20);
        // M16 黑雾：中继站和种子（R）的测试加在这行上面
        spaced(event, environment, PLASMA_KILLS_RAIDER, HANGAR, 200);
        spaced(event, environment, SR_PLASMA_NEEDS_NO_AMMO, HANGAR, 100);
        spaced(event, environment, GAUSS_IGNORES_HIGH_ALTITUDE, HANGAR, 100);
        spaced(event, environment, MISSILE_AIMS_AT_HIGH_ALTITUDE, HANGAR, 100);
        spaced(event, environment, PLASMA_HITS_HIGH_ALTITUDE, HANGAR, 200);
        test(event, environment, FLEET_SORTIE_STRIKES_HIVE, 20);
        // M16 黑雾：等离子炮塔（T）的测试加在这行上面
        spaced(event, environment, SHIELD_CHARGES_AND_RAISES, 200);
        spaced(event, environment, SHIELD_ABSORBS_AND_COLLAPSES, 40);
        test(event, environment, SHIELD_LIMIT_AND_OFFLINE_COVERS, 20);
        // M16 黑雾：行星护盾（S）的测试加在这行上面
        test(event, environment, ACID_BURNS, 120);
        test(event, environment, ACID_CORRODES_ITEMS, 200);
        test(event, environment, PUMP_MAKES_SULFURIC_ACID, 200);
        // M17 收尾：酸液（A）的测试加在这行上面
        test(event, environment, GRID_INTERFACE_OUTPUT_FEEDS_NEIGHBOR, 200);
        test(event, environment, GRID_INTERFACE_INPUT_FEEDS_GRID, 100);
        test(event, environment, TANK_AND_PUMP_FLUIDS, 20);
        // M17 收尾：互通（C）的测试加在这行上面
        test(event, environment, DYSON_FREE_DESIGN_APPLIES, 20);
        test(event, environment, DYSON_PAINT_STICKS_AND_STALE_FAILS, 20);
        test(event, environment, DYSON_BLUEPRINT_ROUND_TRIPS, 20);
        test(event, environment, DYSON_BUILT_NODE_CANNOT_BE_REMOVED, 20);
        // M17 收尾：戴森球（D）的测试加在这行上面
        test(event, environment, HOPPER_PULLS_FROM_STATION, 100);
        test(event, environment, COMPARATOR_READS_STATION, 20);
        test(event, environment, LENS_RECEIVES_WITHOUT_SUN, 200);
        test(event, environment, BLUEPRINT_KEEPS_SETTINGS, 20);
        // M17 收尾：工厂物流（E）的测试加在这行上面
        test(event, environment, FLYING_SHIP_CARGO, 20);
        test(event, environment, SHIP_WITHOUT_HOLDS, 20);
        test(event, environment, ENTITY_GRAVITY_FOLLOWS_PLANET, 20);
        test(event, environment, PLACED_PLANT_PICKS_LOCAL_SPECIES, 20);
        // M17 收尾：飞船和星球（F）的测试加在这行上面
    }

    /**
     * 四周留出 {@link #DARK_FOG_PADDING} 格空地的测试。黑雾的单位会四处找人、找设施打，炮塔、信号塔、战场分析基地的范围都有几十格，
     * 挨着别的测试放会互相干扰（连上隔壁的电网、被隔壁的信号塔引走、炮塔打掉隔壁的单位、修好隔壁掉血的设施）。
     */
    private static void spaced(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            int maxTicks) {
        spaced(event, environment, function, PLATFORM, maxTicks);
    }

    private static void spaced(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            Identifier structure, int maxTicks) {
        spaced(event, environment, function, structure, maxTicks, false);
    }

    /** {@code skyAccess}：场地上面不封屏障，单位能飞出场地。 */
    private static void spaced(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            Identifier structure, int maxTicks, boolean skyAccess) {
        event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, Level.OVERWORLD, structure, maxTicks,
                0, true, Rotation.NONE, false, 1, 1, skyAccess, DARK_FOG_PADDING)));
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            int maxTicks) {
        test(event, environment, function, PLATFORM, maxTicks);
    }

    private static void test(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, ResourceKey<Consumer<GameTestHelper>> function,
            Identifier structure, int maxTicks) {
        event.registerTest(function.identifier(), new FunctionGameTestInstance(function, new TestData<>(environment, structure, maxTicks, 0, true)));
    }

    // ---- 测试

    /** 感应塔把人造恒星的电送到一型制造台，制造台用铁锭做出齿轮。 */
    private static void assemblerMakesGears(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos machine = assembler(helper, new BlockPos(3, 1, 3));
        helper.succeedWhen(() -> {
            RecipeMachineBlockEntity assembler = helper.getBlockEntity(machine, RecipeMachineBlockEntity.class);
            helper.assertTrue(assembler.countItem(UniverseItems.GEAR.get()) > 0, "制造台没有做出齿轮");
        });
    }

    /** 没接电的制造台停在“缺电”，不出东西，原料不动。 */
    private static void assemblerWaitsForPower(GameTestHelper helper) {
        BlockPos machine = assembler(helper, new BlockPos(3, 1, 3));
        helper.runAfterDelay(60, () -> {
            RecipeMachineBlockEntity assembler = helper.getBlockEntity(machine, RecipeMachineBlockEntity.class);
            helper.assertValueEqual(assembler.data().get(RecipeMachineBlockEntity.STATUS), RecipeMachineBlockEntity.NO_POWER, "制造台状态");
            helper.assertValueEqual(assembler.countItem(UniverseItems.GEAR.get()), 0, "齿轮数量");
            helper.assertValueEqual(assembler.countItem(Items.IRON_INGOT), 64, "铁锭数量");
            helper.succeed();
        });
    }

    /** 一排朝东的传送带把头上放的铁锭送进尽头的储物仓。 */
    private static void beltDeliversToDepot(GameTestHelper helper) {
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState().setValue(BeltBlock.FACING, Direction.EAST);
        for (int x = 0; x < 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), belt);
        }
        BlockPos depot = new BlockPos(6, 1, 3);
        helper.setBlock(depot, UniverseBlocks.STORAGE_DEPOT.get());
        // 传送带网络在下一刻才把新放的几格连成线
        helper.runAfterDelay(2, () -> helper.assertTrue(BeltNetwork.of(helper.getLevel())
                .insertAt(helper.absolutePos(new BlockPos(0, 1, 3)), 0, BeltNetwork.id(Items.IRON_INGOT)), "传送带上放不下铁锭"));
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(depot, StorageDepotBlockEntity.class).countItem(Items.IRON_INGOT) == 1,
                "铁锭没有进储物仓"));
    }

    /** 接上电、放进电磁矩阵的研究站给当前研究攒点数。 */
    private static void labAddsPoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResearchState state = ResearchData.get(server).state();
        if (state.isDone(LAB_TECH)) {
            state.revoke(LAB_TECH);
        }
        completeRequirements(state, LAB_TECH);
        helper.assertTrue(state.start(Research.TREE, LAB_TECH), "研究开不了头");
        Research.changed(server);
        int before = state.progress(LAB_TECH);

        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.MATRIX_LAB.get());
        MatrixLabBlockEntity lab = helper.getBlockEntity(pos, MatrixLabBlockEntity.class);
        String matrix = Research.TREE.get(LAB_TECH).orElseThrow().matrices().getFirst();
        lab.setItem(TechTree.MATRICES.indexOf(matrix), new ItemStack(UniverseItems.ELECTROMAGNETIC_MATRIX.get(), MatrixLabBlockEntity.AUTOMATION_LIMIT));
        helper.succeedWhen(() -> helper.assertTrue(state.progress(LAB_TECH) > before || state.isDone(LAB_TECH), "研究点数没有增加"));
    }

    /** 传送带中间串一台装了 Mk.I 增产剂的喷涂机，经过的铁锭进储物仓时每个带 1 点（ADR-0022）。 */
    private static void coaterSpraysCargo(GameTestHelper helper) {
        powerPlant(helper);
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState().setValue(BeltBlock.FACING, Direction.EAST);
        for (int x = 0; x < 6; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), belt);
        }
        BlockPos coater = new BlockPos(3, 1, 3);
        helper.setBlock(coater, UniverseBlocks.SPRAY_COATER.get().defaultBlockState().setValue(BeltPassBlock.FACING, Direction.EAST));
        helper.assertValueEqual(helper.getBlockEntity(coater, SprayCoaterBlockEntity.class)
                .insert(new ItemStack(UniverseItems.PROLIFERATOR_MK1.get(), 1)), 1, "装进去的增产剂");
        BlockPos depot = new BlockPos(6, 1, 3);
        helper.setBlock(depot, UniverseBlocks.STORAGE_DEPOT.get());
        // 等电网通上电再放货：没电时货物照样流过去，只是不喷
        helper.runAfterDelay(40, () -> helper.assertTrue(BeltNetwork.of(helper.getLevel())
                .insertAt(helper.absolutePos(new BlockPos(0, 1, 3)), 0, BeltNetwork.id(Items.IRON_INGOT)), "传送带上放不下铁锭"));
        helper.succeedWhen(() -> {
            ItemStack stack = helper.getBlockEntity(depot, StorageDepotBlockEntity.class).getItem(0);
            helper.assertTrue(stack.is(Items.IRON_INGOT), "铁锭没有进储物仓");
            helper.assertValueEqual(BeltNetwork.level(stack), 1, "铁锭的增产点数");
        });
    }

    /** 自动集装机把先后来的两个铁锭叠成一格（货物集装 0 级叠 2 个），后面的传送带上是一格 2 个。 */
    private static void pilerStacksCargo(GameTestHelper helper) {
        powerPlant(helper);
        BlockState belt = UniverseBlocks.CONVEYOR_BELT.get().defaultBlockState().setValue(BeltBlock.FACING, Direction.EAST);
        for (int x = 0; x < 7; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), belt);
        }
        helper.setBlock(new BlockPos(3, 1, 3), UniverseBlocks.AUTOMATIC_PILER.get().defaultBlockState().setValue(BeltPassBlock.FACING, Direction.EAST));
        int iron = BeltNetwork.id(Items.IRON_INGOT);
        // 同一刻放在相邻两格：Mk.I 传送带走一格约 17 刻，集装机最多等 20 刻
        helper.runAfterDelay(40, () -> {
            BeltNetwork network = BeltNetwork.of(helper.getLevel());
            helper.assertTrue(network.insertAt(helper.absolutePos(new BlockPos(1, 1, 3)), 0, iron)
                    && network.insertAt(helper.absolutePos(new BlockPos(0, 1, 3)), 0, iron), "传送带上放不下铁锭");
        });
        helper.succeedWhen(() -> {
            int piled = 0;
            for (int x = 4; x < 7; x++) {
                BeltNetwork.BlockItems items = BeltNetwork.of(helper.getLevel()).itemsAt(helper.absolutePos(new BlockPos(x, 1, 3)));
                if (items != null) {
                    for (int cargo : items.items()) {
                        piled = Math.max(piled, Cargo.count(cargo));
                    }
                }
            }
            helper.assertValueEqual(piled, 2, "集装机后面一格货物的个数");
        });
    }

    /** 两台做齿轮的制造台，一台的铁锭喷过 Mk.III：同样的时间里它多出齿轮（默认额外产出）。 */
    private static void sprayedInputMakesMore(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos plain = assembler(helper, new BlockPos(2, 1, 4));
        BlockPos sprayed = assembler(helper, new BlockPos(4, 1, 4));
        helper.getBlockEntity(sprayed, RecipeMachineBlockEntity.class).setItem(0, BeltNetwork.stack(Items.IRON_INGOT, 64, Proliferation.MAX_LEVEL));
        helper.succeedWhen(() -> {
            int a = helper.getBlockEntity(plain, RecipeMachineBlockEntity.class).countItem(UniverseItems.GEAR.get());
            int b = helper.getBlockEntity(sprayed, RecipeMachineBlockEntity.class).countItem(UniverseItems.GEAR.get());
            helper.assertTrue(a >= 4 && b > a, "喷过的铁锭没有多出齿轮：" + a + " / " + b);
        });
    }

    /** 自演化研究站叠在矩阵研究站上，每秒从下面那台拿一半矩阵。 */
    private static void stackedLabsShare(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(3, 1, 3);
        helper.setBlock(bottom, UniverseBlocks.MATRIX_LAB.get());
        helper.setBlock(bottom.above(), UniverseBlocks.SELF_EVOLUTION_LAB.get());
        Item matrix = UniverseItems.ELECTROMAGNETIC_MATRIX.get();
        int slot = TechTree.MATRICES.indexOf(BuiltInRegistries.ITEM.getKey(matrix).toString());
        helper.getBlockEntity(bottom, MatrixLabBlockEntity.class).setItem(slot, BeltNetwork.stack(matrix, 20, 2));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(helper.getBlockEntity(bottom.above(), MatrixLabBlockEntity.class).countItem(matrix), 10, "上面那台的矩阵");
            helper.assertValueEqual(helper.getBlockEntity(bottom, MatrixLabBlockEntity.class).countItem(matrix), 10, "下面那台的矩阵");
        });
    }

    /** 地热发电站下面是熔岩才有地热；放在石头上的是 0。 */
    private static void geothermalNeedsLava(GameTestHelper helper) {
        BlockPos lava = new BlockPos(3, 1, 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(lava.relative(side), Blocks.STONE);
        }
        helper.setBlock(lava, Blocks.LAVA);
        BlockPos hot = lava.above();
        helper.setBlock(hot, UniverseBlocks.GEOTHERMAL_POWER_STATION.get());
        BlockPos dry = new BlockPos(5, 1, 5);
        helper.setBlock(dry, UniverseBlocks.GEOTHERMAL_POWER_STATION.get());
        helper.runAfterDelay(25, () -> {
            helper.assertValueEqual(helper.getBlockEntity(hot, GeothermalBlockEntity.class).data().get(GeothermalBlockEntity.HEAT), 100, "熔岩上的地热");
            helper.assertValueEqual(helper.getBlockEntity(dry, GeothermalBlockEntity.class).data().get(GeothermalBlockEntity.HEAT), 0, "石头上的地热");
            helper.succeed();
        });
    }

    /** 物流站收喷过的铁锭，点数记进格子；回滚的事务不留点数；拿货时只给带同样点数的物品。 */
    private static void stationKeepsPoints(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        tall(helper, pos, UniverseBlocks.PLANETARY_LOGISTICS_STATION.get().defaultBlockState());
        helper.runAfterDelay(1, () -> {
            StationBlockEntity entity = helper.getBlockEntity(pos, StationBlockEntity.class);
            Station station = entity.station();
            helper.assertTrue(station != null, "账本里没有这座站");
            StationSlot slot = station.slot(0);
            helper.assertTrue(slot.setItem("minecraft:iron_ingot"), "设不了物品");
            slot.setMax(1000);
            StationHandler handler = entity.handler();
            ItemResource sprayed = ItemResource.of(Items.IRON_INGOT).with(UniverseDataComponents.PROLIFERATION.get(), 2);
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(handler.insert(0, sprayed, 10, transaction), 10, "放进去的铁锭");
            }
            helper.assertValueEqual(slot.count(), 0, "回滚后的数量");
            helper.assertValueEqual(slot.points(), 0L, "回滚后的点数");
            try (Transaction transaction = Transaction.openRoot()) {
                handler.insert(0, sprayed, 10, transaction);
                transaction.commit();
            }
            helper.assertValueEqual(slot.points(), 20L, "点数");
            helper.assertValueEqual(handler.getResource(0), sprayed, "格子里的物品");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertValueEqual(handler.extract(0, ItemResource.of(Items.IRON_INGOT), 5, transaction), 0, "没喷过的铁锭");
                helper.assertValueEqual(handler.extract(0, sprayed, 5, transaction), 5, "喷过的铁锭");
                transaction.commit();
            }
            helper.assertValueEqual(slot.points(), 10L, "剩下的点数");
            helper.succeed();
        });
    }

    /** 两台配送器各自顶在储物仓上：需求的那台派机器人去供应的那台取铁锭，放进自己下面（ADR-0023）。 */
    private static void distributorsShareItems(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos supply = new BlockPos(2, 1, 4);
        BlockPos demand = new BlockPos(4, 1, 4);
        helper.setBlock(supply, UniverseBlocks.STORAGE_DEPOT.get());
        helper.setBlock(demand, UniverseBlocks.STORAGE_DEPOT.get());
        helper.getBlockEntity(supply, StorageDepotBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 32));
        distributor(helper, supply.above(), Distribution.Icarus.NONE, Distribution.Peer.SUPPLY);
        distributor(helper, demand.above(), Distribution.Icarus.NONE, Distribution.Peer.DEMAND);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(demand, StorageDepotBlockEntity.class).countItem(Items.IRON_INGOT) > 0,
                "需求的配送器没有取到铁锭"));
    }

    // ---- 搭场地

    /** 一台管铁锭、装了两个配送机器人的物流配送器。 */
    private static void distributor(GameTestHelper helper, BlockPos pos, Distribution.Icarus icarus, Distribution.Peer peer) {
        helper.setBlock(pos, UniverseBlocks.LOGISTICS_DISTRIBUTOR.get());
        DistributorBlockEntity distributor = helper.getBlockEntity(pos, DistributorBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Filter", "minecraft:iron_ingot");
        settings.putString("Icarus", icarus.name());
        settings.putString("Peer", peer.name());
        distributor.applyBlueprintSettings(settings);
        distributor.setItem(DistributorBlockEntity.BOT_SLOT, new ItemStack(UniverseItems.LOGISTICS_BOT.get(), 2));
    }

    /**
     * 驾驶台里的船体加固模块和镉驱动（ADR-0024）跟着船走：组装出来的船记了主人，船体比只按部件算的高，驱动是镉驱动；
     * 停船以后主力船记成"停了"，模块和驱动还在驾驶台里。
     */
    private static void helmTechTunesShip(GameTestHelper helper) {
        BlockPos helm = smallShip(helper, 1);
        HelmBlockEntity tech = helper.getBlockEntity(helm, HelmBlockEntity.class);
        ItemStack module = new ItemStack(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get());
        module.set(UniverseDataComponents.TECH_MODULE.get(), TechModule.typical(ModuleType.HULL_REINFORCEMENT, Grade.A));
        tech.items().set(0, module);
        tech.items().set(1, new ItemStack(UniverseItems.DRIVES.get(DriveTier.CADMIUM).get()));
        tech.setChanged();
        UUID owner = UUID.randomUUID();
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), owner, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "没有组装出飞船");
        helper.assertValueEqual(ship.structure().rating().type(), ShipType.SHUTTLE, "类型");
        helper.assertValueEqual(ship.structure().tech().modules().size(), 1, "生效的模块数");
        helper.assertValueEqual(ship.structure().stats().drive(), DriveTier.CADMIUM, "驱动");
        helper.assertTrue(ship.structure().stats().hull() > ship.structure().baseStats().hull() + 1, "船体没有加上去");
        helper.assertValueEqual(ship.owner(), owner, "主人");
        ShipOwners owners = ShipOwners.get(helper.getLevel().getServer());
        owners.piloted(owner, ship);
        helper.assertTrue(ShipAssembly.disassemble(ship), "停不了船");
        helper.assertTrue(owners.main(owner).orElseThrow().parked(), "停船没记下来");
        HelmBlockEntity back = helper.getBlockEntity(helm, HelmBlockEntity.class);
        helper.assertTrue(back.items().get(0).is(UniverseItems.MODULES.get(ModuleType.HULL_REINFORCEMENT).get()), "模块没放回驾驶台");
        helper.assertTrue(back.items().get(1).is(UniverseItems.DRIVES.get(DriveTier.CADMIUM).get()), "驱动没放回驾驶台");
        helper.succeed();
    }

    /** 着陆台接上电，给停在上面、燃料舱空着的船加燃料（ADR-0024）。 */
    private static void landingPadRefuelsShip(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pad = new BlockPos(3, 1, 3);
        helper.setBlock(pad, UniverseBlocks.LANDING_PAD.get());
        BlockPos helm = smallShip(helper, 2);
        ShipEntity ship = ShipAssembly.assemble(helper.getLevel(), helper.absolutePos(helm), null, message -> {
        });
        helper.assertTrue(ship != null, "没有组装出飞船");
        helper.assertValueEqual(ship.fuelServer(), 0.0, "燃料舱一开始是空的");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockEntity(pad, LandingPadBlockEntity.class).shipAbove() == ship, "着陆台上没找到船");
            helper.assertTrue(ship.fuelServer() > 20, "燃料没加上去：" + ship.fuelServer());
            ship.discard();
        });
    }

    /** 水培槽（ADR-0026）：洞穴髓球种不进去；种下氧囊草、接上电就开始长，补光灯亮。 */
    private static void hydroponicTrayGrows(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.HYDROPONIC_TRAY.get());
        HydroponicTrayBlockEntity tray = helper.getBlockEntity(pos, HydroponicTrayBlockEntity.class);
        helper.assertFalse(tray.sow(new ItemStack(UniverseItems.RESOURCES.get("marrow_bulb").get())), "洞穴髓球不该能种进水培槽");
        helper.assertTrue(tray.sow(new ItemStack(UniverseItems.RESOURCES.get("oxygen").get())), "氧气种不下去");
        helper.succeedWhen(() -> {
            helper.assertTrue(tray.growth() > 0.01, "没有长：" + tray.growth());
            helper.assertBlockProperty(pos, MachineBlock.WORKING, true);
        });
    }

    /** 卵序列仪（ADR-0026 §九）：泥土放不进材料格；放进卵和两份生物胶质，5 秒后产物格里是大一级的卵，胶质只用掉一份。 */
    private static void sequencerGrowsEgg(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.EGG_SEQUENCER.get());
        EggSequencerBlockEntity sequencer = helper.getBlockEntity(pos, EggSequencerBlockEntity.class);
        CreatureEggs.Egg egg = new CreatureEggs.Egg("-", 0, new CreatureTraits(1, CreatureTraits.NO_TINT, Temperament.SKITTISH), Optional.empty(),
                "测试兽", "Testbeast");
        helper.assertFalse(sequencer.canPlaceItem(EggSequencerBlockEntity.MATERIAL, new ItemStack(Items.DIRT)), "泥土不该能放进材料格");
        sequencer.setItem(EggSequencerBlockEntity.EGG, CreatureEggs.stack(egg));
        sequencer.setItem(EggSequencerBlockEntity.MATERIAL, new ItemStack(UniverseItems.BIO_GEL.get(), 2));
        helper.succeedWhen(() -> {
            CreatureEggs.Egg out = sequencer.output();
            helper.assertTrue(out != null && out.traits().size() == 1.1, "产物格里没有大一级的卵：" + out);
            helper.assertTrue(out.traits().temperament() == Temperament.SKITTISH && out.species() == 0, "别的性状变了：" + out);
            helper.assertTrue(sequencer.getItem(EggSequencerBlockEntity.EGG).isEmpty(), "卵还在卵格里");
            helper.assertTrue(sequencer.getItem(EggSequencerBlockEntity.MATERIAL).getCount() == 1, "胶质不是只用掉一份");
        });
    }

    /** 生物卵（ADR-0026 §九）：孵够了的卵孵出一只幼崽，归卵上的主人，性状照着卵；方块没了。 */
    private static void eggHatchesOwned(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos.below(), Blocks.SMOOTH_STONE);
        helper.setBlock(pos, UniverseBlocks.CREATURE_EGG.get());
        // 主人不在线（不在玩家列表里）也照样孵
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        CreatureTraits traits = new CreatureTraits(1.2, 0xB02E26, Temperament.DOCILE);
        CreatureEggBlockEntity egg = helper.getBlockEntity(pos, CreatureEggBlockEntity.class);
        egg.applyComponentsFromItemStack(CreatureEggs.stack(new CreatureEggs.Egg("-", 0, traits, Optional.of(owner.getUUID()), "测试兽",
                "Testbeast")));
        egg.skipToHatch();
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, pos);
            // 会飞的物种孵出来是飞兽，也是异星生物
            List<AlienCreature> babies = helper.getLevel().getEntitiesOfClass(AlienCreature.class, new AABB(helper.absolutePos(pos)).inflate(3));
            helper.assertTrue(babies.size() == 1, "孵出来的不是一只：" + babies.size());
            AlienCreature baby = babies.getFirst();
            helper.assertTrue(baby.isOwnedBy(owner), "幼崽不归卵的主人");
            CreatureTraits hatched = baby.traits();
            // 体型系数在实体上按 float 同步
            helper.assertTrue(Math.abs(hatched.size() - traits.size()) < 1e-4 && hatched.tint() == traits.tint()
                    && hatched.temperament() == traits.temperament(), "幼崽的性状和卵不一样：" + hatched);
            baby.discard();
        });
    }

    /** 化石坑（ADR-0026）：挖开化石土（没有精准采集）掉一块化石。 */
    private static void fossilDirtGivesFossil(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.FOSSIL_DIRT.get());
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        Set<Item> fossils = Set.of(UniverseItems.SHELL_FOSSIL.get(), UniverseItems.ARTHROPOD_FOSSIL.get(), UniverseItems.SKULL_FOSSIL.get());
        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, pos, 3);
            helper.assertTrue(drops.size() == 1 && fossils.contains(drops.get(0).getItem().getItem())
                    && drops.get(0).getItem().getCount() == 1, "掉的不是一块化石：" + drops);
        });
    }

    /** 制造设施（ADR-0026）：两扇并排的加固门，采矿激光照 199 刻还在，照满 200 刻整扇门一起烧开。 */
    private static void reinforcedDoorCutsOpen(GameTestHelper helper) {
        List<BlockPos> door = List.of(new BlockPos(3, 1, 3), new BlockPos(3, 2, 3), new BlockPos(4, 1, 3), new BlockPos(4, 2, 3));
        BlockState lower = UniverseBlocks.REINFORCED_DOOR.get().defaultBlockState()
                .setValue(ReinforcedDoorBlock.FACING, Direction.NORTH).setValue(ReinforcedDoorBlock.HALF, DoubleBlockHalf.LOWER);
        for (BlockPos pos : door) {
            helper.setBlock(pos, pos.getY() == 1 ? lower : lower.setValue(ReinforcedDoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        BlockPos target = helper.absolutePos(new BlockPos(4, 2, 3));
        for (int i = 1; i < DoorBreach.TICKS; i++) {
            OutpostInteractions.laser(null, helper.getLevel(), target, helper.getLevel().getBlockState(target));
        }
        door.forEach(pos -> helper.assertBlockPresent(UniverseBlocks.REINFORCED_DOOR.get(), pos));
        helper.assertTrue(OutpostInteractions.laser(null, helper.getLevel(), target, helper.getLevel().getBlockState(target)),
                "激光没认出加固门");
        door.forEach(pos -> helper.assertBlockPresent(Blocks.AIR, pos));
        helper.succeed();
    }

    /** 打捞点（ADR-0026）：打捞舱右键打开倒出废料，打开的再右键什么也没有。 */
    private static void salvageCapsuleOpensOnce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, UniverseBlocks.SALVAGE_CAPSULE.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, player);
        helper.assertBlockProperty(pos, SalvageCapsuleBlock.OPEN, true);
        helper.assertItemEntityPresent(UniverseItems.SALVAGED_SCRAP.get(), pos, 3);
        helper.killAllEntitiesOfClass(ItemEntity.class);
        helper.useBlock(pos, player);
        helper.assertItemEntityNotPresent(UniverseItems.SALVAGED_SCRAP.get(), pos, 3);
        helper.succeed();
    }

    /** 雷击石（ADR-0026）：雷打中的沙子熔成雷击石。 */
    private static void lightningFusesFulgurite(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.SAND);
        int fused = Fulgurites.fuse(helper.getLevel(), helper.absolutePos(pos), Rng.of(7));
        helper.assertTrue(fused >= 1, "没有熔出雷击石");
        helper.assertBlockPresent(UniverseBlocks.FULGURITE.get(), pos);
        helper.succeed();
    }

    /** 甲壳蚌（ADR-0026）：右键撬开掉一颗活珍珠，撬开的再右键没有了。 */
    private static void clamGivesPearl(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        flood(helper, 2, 1, 2, 4, 2, 4);
        helper.setBlock(pos, UniverseBlocks.CLAM.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.useBlock(pos, player);
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(pos, ClamBlock.OPEN, true);
            helper.assertItemEntityPresent(UniverseItems.LIVING_PEARL.get(), pos, 3);
        });
    }

    /** 潜艇（ADR-0026）：没人开的时候在水里悬停，不沉也不浮；在空气里往下掉。 */
    private static void submarineHovers(GameTestHelper helper) {
        flood(helper, 0, 1, 0, 3, 3, 6);
        Submarine wet = helper.spawn(UniverseEntities.SUBMARINE.get(), new Vec3(1.7, 1.5, 3.5));
        Submarine dry = helper.spawn(UniverseEntities.SUBMARINE.get(), new Vec3(5.4, 2.4, 3.5));
        double start = wet.getY();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(wet.isInWater() && wet.submerged(), "潜艇不在水里");
            helper.assertTrue(Math.abs(wet.getY() - start) < 0.15, "水里的潜艇漂走了：" + start + " → " + wet.getY());
            helper.assertTrue(dry.getY() < helper.absoluteVec(new Vec3(0, 1.1, 0)).y, "空气里的潜艇没掉下去：" + dry.getY());
            helper.succeed();
        });
    }

    /** 鱼雷（ADR-0026）：在水里直线跑，打中水生动物炸开；打不中发射它的潜艇。 */
    private static void torpedoHitsCreature(GameTestHelper helper) {
        flood(helper, 0, 1, 0, 6, 3, 6);
        Submarine submarine = helper.spawn(UniverseEntities.SUBMARINE.get(), new Vec3(1.2, 1.2, 3.5));
        AquaticCreature target = helper.spawn(UniverseEntities.AQUATIC_CREATURE.get(), new Vec3(5.5, 1.8, 3.5));
        float health = target.getHealth();
        Vec3 from = helper.absoluteVec(new Vec3(2.6, 2.0, 3.5));
        helper.getLevel().addFreshEntity(Torpedo.launch(helper.getLevel(), submarine, from, new Vec3(1, -0.05, 0)));
        helper.succeedWhen(() -> {
            helper.assertTrue(!target.isAlive() || target.getHealth() < health, "鱼雷没打中：" + target.getHealth());
            helper.assertTrue(submarine.isAlive(), "鱼雷炸了自己的潜艇");
        });
    }

    /** 地面守卫机的弱点（ADR-0026）：两台朝南的四足机挨同样的一下，从北边（背后）打的掉血是从南边（正面）打的两倍左右。 */
    private static void quadBackHitDoubles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuadrupedSentinel front = helper.spawn(UniverseEntities.QUADRUPED_SENTINEL.get(), new Vec3(1.5, 1, 2.5));
        QuadrupedSentinel back = helper.spawn(UniverseEntities.QUADRUPED_SENTINEL.get(), new Vec3(5.5, 1, 2.5));
        for (QuadrupedSentinel quad : List.of(front, back)) {
            quad.setNoAi(true);
            quad.setYRot(0);
            quad.setYHeadRot(0);
            quad.setYBodyRot(0);
        }
        Holder<net.minecraft.world.damagesource.DamageType> generic = level.damageSources().generic().typeHolder();
        front.hurtServer(level, new DamageSource(generic, front.position().add(0, 1, 2)), 6);
        back.hurtServer(level, new DamageSource(generic, back.position().add(0, 1, -2)), 6);
        float frontLoss = QuadrupedSentinel.MAX_HEALTH - front.getHealth();
        float backLoss = QuadrupedSentinel.MAX_HEALTH - back.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(frontLoss > 0, "正面打没掉血");
            helper.assertTrue(backLoss >= frontLoss * 1.8F, "背后打掉 " + backLoss + "，正面掉 " + frontLoss);
        });
    }

    /** 四足机、步行机走地面（ADR-0026）：从半空放下会落到平台上，不会像无人机那样飘着。 */
    private static void groundSentinelsWalk(GameTestHelper helper) {
        QuadrupedSentinel quad = helper.spawn(UniverseEntities.QUADRUPED_SENTINEL.get(), new Vec3(1.5, 3.5, 1.5));
        WalkerSentinel walker = helper.spawn(UniverseEntities.WALKER_SENTINEL.get(), new Vec3(4.6, 2.5, 4.6));
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        helper.succeedWhen(() -> {
            for (Sentinel sentinel : List.<Sentinel>of(quad, walker)) {
                helper.assertTrue(!sentinel.isNoGravity(), sentinel.getType() + " 没有重力");
                helper.assertTrue(sentinel.onGround() && sentinel.getY() < floor + 0.5, sentinel.getType() + " 还在 " + sentinel.getY());
            }
        });
    }

    /** 维修无人机（ADR-0026）：给附近受伤的守卫机回血，自己不受影响。 */
    private static void repairDroneHeals(GameTestHelper helper) {
        Sentinel patient = helper.spawn(UniverseEntities.SENTINEL.get(), new Vec3(2.5, 3.0, 3.5));
        RepairSentinel medic = helper.spawn(UniverseEntities.REPAIR_SENTINEL.get(), new Vec3(5.5, 3.0, 3.5));
        patient.setHealth(8);
        helper.assertTrue(medic.getMaxHealth() == 16, "维修无人机的生命不是 16：" + medic.getMaxHealth());
        helper.succeedWhen(() -> helper.assertTrue(patient.isAlive() && patient.getHealth() > 9,
                "守卫机没被修：" + patient.getHealth()));
    }

    /** 守卫塔的终端（ADR-0026）：被通缉时不响应；关停这个维度的守卫机；同一个游戏日第二次用不了。 */
    /**
     * 守卫机型多功能工具（ADR-0026 §六）：没有步行机主脑取不出来；交一个主脑取出一把，一半的格子是坏的；同一座塔不能再取。
     * 拿着 3 个守卫机残骸在技术配置界面点坏格子，修好一格。
     */
    private static void pillarGivesSentinelTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos terminal = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), UniverseBlocks.SENTINEL_PILLAR_TERMINAL.get());
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        helper.assertTrue(SentinelPillars.takeTool(player, level, terminal) == SentinelPillars.ToolResult.NO_BRAIN, "没有主脑也取出了工具");
        player.getInventory().setItem(8, new ItemStack(UniverseItems.WALKER_BRAIN.get(), 2));
        helper.assertTrue(SentinelPillars.takeTool(player, level, terminal) == SentinelPillars.ToolResult.GIVEN, "交了主脑没给工具");
        helper.assertTrue(player.getInventory().getItem(8).getCount() == 1, "主脑没扣或者扣多了");
        ItemStack tool = player.getInventory().getItem(0);
        helper.assertTrue(tool.getItem() instanceof MultitoolItem, "工具没进背包");
        MultitoolSpec spec = MultitoolItem.spec(tool);
        helper.assertTrue(spec.type() == MultitoolClass.SENTINEL, "不是守卫机型");
        helper.assertTrue(spec.broken() == spec.slots() / 2 && spec.broken() > 0, "坏格不是一半：" + spec);
        helper.assertTrue(SentinelPillars.takeTool(player, level, terminal) == SentinelPillars.ToolResult.TAKEN, "同一座塔取了两次");
        helper.assertTrue(player.getInventory().getItem(8).getCount() == 1, "取过了还扣主脑");
        // 修坏格：工具在主手（快捷栏第一格），手上拿着 3 个残骸点第一个坏格子
        player.getInventory().setSelectedSlot(0);
        TechMenu menu = TechMenu.server(0, player.getInventory(), player);
        int brokenSlot = TechMenu.TOOL_START + spec.working();
        helper.assertTrue(!menu.getSlot(brokenSlot).mayPlace(new ItemStack(UniverseItems.SENTINEL_SCRAP.get())), "坏格子放进了东西");
        menu.setCarried(new ItemStack(UniverseItems.SENTINEL_SCRAP.get(), MultitoolSpec.REPAIR_SCRAP));
        menu.clicked(brokenSlot, 0, ContainerInput.PICKUP, player);
        MultitoolSpec repaired = MultitoolItem.spec(player.getInventory().getItem(0));
        helper.assertTrue(repaired.broken() == spec.broken() - 1, "残骸没修好坏格：" + repaired);
        helper.assertTrue(menu.getCarried().isEmpty(), "修好以后残骸没扣");
        helper.assertTrue(!menu.toolSlotBroken(spec.working()), "界面里那一格还是坏的");
        helper.succeed();
    }

    /**
     * 工具柜买工具时把旧工具里的东西装进新工具（ADR-0026 §六）：C 档 4 格，同种模块最多 3 个、同种武器 1 个，装不下的退回来；
     * 买来的工具按标价折一半；C 档手枪谁都能做，和没有类型的旧工具一样按材料折。
     */
    private static void cabinetMovesModules(GameTestHelper helper) {
        MultitoolSpec spec = new MultitoolSpec(MultitoolClass.PISTOL, Grade.C, 0);
        ItemStack tool = MultitoolItem.create(spec);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            stacks.add(ShipDealer.moduleStack(TechModule.typical(ModuleType.BEAM_FOCUS, Grade.B)));
        }
        stacks.add(new ItemStack(UniverseItems.WEAPONS.get(ToolWeapon.SCATTER_BLASTER).get()));
        stacks.add(new ItemStack(UniverseItems.WEAPONS.get(ToolWeapon.SCATTER_BLASTER).get()));
        stacks.add(ShipDealer.moduleStack(TechModule.typical(ModuleType.SCANNER_ARRAY, Grade.B)));
        List<ItemStack> leftover = MultitoolCabinet.install(tool, spec, stacks);
        long installed = tool.getOrDefault(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
        helper.assertTrue(installed == 4, "装进去的不是 4 个：" + installed);
        helper.assertTrue(leftover.size() == 3, "退回来的不是 3 个：" + leftover.size());
        MinecraftServer server = helper.getLevel().getServer();
        MultitoolSpec bought = new MultitoolSpec(MultitoolClass.RIFLE, Grade.B, 0);
        helper.assertTrue(MultitoolCabinet.tradeIn(server, MultitoolItem.create(bought)) == MultitoolMarket.tradeIn(bought), "买来的工具折价不对");
        long crafted = MultitoolCabinet.tradeIn(server, new ItemStack(UniverseItems.MULTITOOL.get()));
        helper.assertTrue(crafted >= 0, "做出来的工具不能折价");
        helper.assertTrue(MultitoolCabinet.tradeIn(server, tool) == crafted, "C 档手枪没按材料折价");
        helper.assertTrue(MultitoolCabinet.tradeIn(server, new ItemStack(UniverseItems.WALKER_BRAIN.get())) == -1, "不是工具也能折价");
        helper.succeed();
    }

    private static void pillarTerminalShutsDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(SentinelPillars.use(level, true) == SentinelPillars.Use.WANTED, "通缉时终端响应了");
        helper.assertTrue(!Sentinels.dormant(level), "通缉时守卫机被关停了");
        helper.assertTrue(SentinelPillars.use(level, false) == SentinelPillars.Use.SHUT_DOWN, "终端没有关停守卫机");
        helper.assertTrue(Sentinels.dormant(level), "关停以后守卫机还在");
        helper.assertTrue(SentinelPillars.use(level, false) == SentinelPillars.Use.USED_TODAY, "同一天能关停两次");
        Sentinels.wake(level);
        helper.succeed();
    }

    /**
     * 等离子发射器的榴弹（ADR-0026 §六）：落在地上停住，2 秒后炸开；旁边的牛挨了伤害、没被炸死，
     * 紧挨着的玻璃和脚下的石头都还在。
     */
    private static void grenadeSparesBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 场地的地板在测试世界里放不出来（脚下只有屏障），榴弹落点自己铺一块石头
        for (BlockPos pos : BlockPos.betweenClosed(2, 0, 2, 4, 0, 4)) {
            helper.setBlock(pos, Blocks.SMOOTH_STONE);
        }
        helper.setBlock(new BlockPos(2, 1, 3), Blocks.GLASS);
        Mob cow = helper.spawn(EntityTypes.COW, new Vec3(5.5, 1, 3.5));
        cow.setNoAi(true);
        float health = cow.getHealth();
        Player shooter = helper.makeMockPlayer(GameType.SURVIVAL);
        PlasmaGrenade grenade = WeaponFire.launch(level, shooter, helper.absoluteVec(new Vec3(3.5, 1.6, 3.5)), new Vec3(0, -1, 0), 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(grenade.isRemoved(), "榴弹还没炸");
            helper.assertTrue(cow.isAlive() && cow.getHealth() < health, "牛的生命：" + cow.getHealth());
            helper.assertBlockPresent(Blocks.GLASS, new BlockPos(2, 1, 3));
            helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(3, 0, 3));
        });
    }

    /** 散射爆能枪（ADR-0026 §六）：4 格外的牛挨了不止一颗弹丸（每颗 1.5，弹丸之间没有受伤无敌时间）。 */
    private static void scatterHitsClose(GameTestHelper helper) {
        Mob cow = helper.spawn(EntityTypes.COW, new Vec3(5.5, 1, 3.5));
        cow.setNoAi(true);
        float health = cow.getHealth();
        Player shooter = helper.makeMockPlayer(GameType.SURVIVAL);
        WeaponFire.scatter(helper.getLevel(), shooter, helper.absoluteVec(new Vec3(1.5, 1.6, 3.5)), new Vec3(1, 0, 0), 1);
        helper.succeedWhen(() -> helper.assertTrue(!cow.isAlive() || cow.getHealth() <= health - 3, "牛的生命：" + cow.getHealth()));
    }

    /** 烈焰标枪（ADR-0026 §六）：一下打中视线上的第一只牛，18 点打死；后面那只没事。 */
    private static void javelinHitsFirst(GameTestHelper helper) {
        Mob first = helper.spawn(EntityTypes.COW, new Vec3(3.5, 1, 3.5));
        Mob behind = helper.spawn(EntityTypes.COW, new Vec3(5.5, 1, 3.5));
        first.setNoAi(true);
        behind.setNoAi(true);
        Player shooter = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 eye = helper.absoluteVec(new Vec3(0.5, 1.5, 3.5));
        Entity struck = WeaponFire.javelin(helper.getLevel(), shooter, eye, new Vec3(1, 0, 0), eye, 1);
        helper.assertTrue(struck == first, "打中的是 " + struck);
        helper.succeedWhen(() -> {
            helper.assertTrue(!first.isAlive(), "第一只牛还活着：" + first.getHealth());
            helper.assertTrue(behind.isAlive() && behind.getHealth() == behind.getMaxHealth(), "后面的牛挨了打");
        });
    }

    /**
     * 悬浮车（ADR-0026）：没人开也悬在离水面一格左右的地方，不沉、不进水。
     * 水灌在地板那一层：场地顶上有一层屏障，水再深车就顶到天花板了。
     */
    private static void hoverCarFloatsOnWater(GameTestHelper helper) {
        flood(helper, 0, 0, 0, 6, 0, 6);
        HoverCar car = helper.spawn(UniverseEntities.HOVER_CAR.get(), new Vec3(3.5, 1.2, 3.5));
        // 水源方块的水面在方块里 8/9 高的地方
        double surface = helper.absoluteVec(new Vec3(0, 8 / 9.0, 0)).y;
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(car.isInWater(), "悬浮车掉进水里了");
            double clearance = car.getY() - surface;
            helper.assertTrue(clearance > 0.6 && clearance < 1.4, "悬浮车离水面 " + clearance + " 格");
            helper.succeed();
        });
    }

    /** 载具召唤台（ADR-0026）：把名册上同一个维度的探索车挪到台子正面，名册上的位置跟着更新。 */
    private static void summonerFetchesVehicle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos summonerPos = new BlockPos(3, 1, 0);
        helper.setBlock(summonerPos, UniverseBlocks.VEHICLE_SUMMONER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        Exocraft exocraft = helper.spawn(UniverseEntities.EXOCRAFT.get(), new Vec3(1.0, 1, 5.5));
        UUID owner = UUID.randomUUID();
        exocraft.setOwner(owner);
        VehicleRoster roster = VehicleRoster.get(level.getServer());
        roster.update(owner, exocraft);
        VehicleSummoning.Result result = VehicleSummoning.summon(level, helper.absolutePos(summonerPos), owner, exocraft.getUUID());
        helper.assertTrue(result == VehicleSummoning.Result.ARRIVED, "召唤的结果：" + result);
        // 台子朝南，车停在台子南边两格半左右
        Vec3 expected = helper.absoluteVec(new Vec3(3.5, 1, 3.0));
        helper.assertTrue(exocraft.position().distanceTo(expected) < 1.5, "车停在 " + helper.relativeVec(exocraft.position()));
        VehicleRoster.Entry entry = roster.entry(owner, exocraft.getUUID());
        helper.assertTrue(entry != null && entry.pos().equals(exocraft.blockPosition()), "名册上的位置没更新：" + entry);
        roster.remove(owner, exocraft.getUUID());
        helper.succeed();
    }

    /** 重型车车尾的精炼机（ADR-0026）：车停着没人开，也烧异星原木把粗铁炼成铁锭。 */
    private static void heavyRoverRefines(GameTestHelper helper) {
        HeavyRover rover = helper.spawn(UniverseEntities.HEAVY_ROVER.get(), new Vec3(3.5, 1, 3.5));
        rover.refiner().restore(new VehicleRefiner.Contents(ItemContainerContents.fromItems(List.of(new ItemStack(Items.RAW_IRON, 2),
                ItemStack.EMPTY, new ItemStack(UniverseItems.PLANET_LOG.get()))), "universe:portable_refining/iron_ingot", 0, 0));
        helper.succeedWhen(() -> helper.assertTrue(rover.refiner().getItem(1).is(Items.IRON_INGOT),
                "重型车的精炼机没有炼出铁锭：" + rover.refiner().getItems()));
    }

    /**
     * 巨型生物（ADR-0026 §九）：刷怪器在空地上刷得出备用物种表里的巨型物种，3.5–6 格高、血 120 以上、驯服后能骑，
     * 刷出来以后附近就算"已经有一只"；头顶两格有方块的地方装不下，不刷。地板自己铺，场地的地板在测试世界里放不出来。
     */
    private static void giantNeedsRoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreatureSpecies species = Fauna.FALLBACK.giants().get(0);
        for (BlockPos pos : BlockPos.betweenClosed(9, 0, 9, 13, 0, 13)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        for (BlockPos pos : BlockPos.betweenClosed(2, 0, 2, 4, 0, 4)) {
            helper.setBlock(pos, Blocks.STONE);
        }
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE);
        helper.assertTrue(FaunaSpawner.spawn(level, species, helper.absolutePos(new BlockPos(3, 1, 3)), level.getRandom()) == null,
                "头顶有方块也刷出了巨型生物");
        AlienCreature giant = FaunaSpawner.spawn(level, species, helper.absolutePos(new BlockPos(11, 1, 11)), level.getRandom());
        helper.assertTrue(giant != null, "空地上没刷出巨型生物");
        helper.assertValueEqual(giant.species().index(), Fauna.GIANT_INDEX, "物种序号");
        helper.assertTrue(Fauna.giant(giant.species()) && Husbandry.rideable(giant.species()), "不是能骑的巨型物种");
        float height = giant.getBbHeight();
        helper.assertTrue(height >= Fauna.GIANT_MIN_HEIGHT - 0.01 && height <= Fauna.GIANT_MAX_HEIGHT + 0.01, "身高 " + height);
        helper.assertTrue(giant.getMaxHealth() >= 120, "生命 " + giant.getMaxHealth());
        helper.assertTrue(FaunaSpawner.giantNearby(level, giant.getX(), giant.getZ()), "刷怪器没认出附近已经有巨型生物");
        helper.succeed();
    }

    /** 卵孵出的幼崽（ADR-0026 §九）：直接是主人的伙伴（信任 50、吃饱的），体型是成年的一半，还不能骑、不下蛋。 */
    private static void hatchedBabyIsCompanion(GameTestHelper helper) {
        companionFloor(helper);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        CreatureSpecies species = Fauna.FALLBACK.get(0);
        CreatureTraits traits = CreatureTraits.of(species).withSize(1.2);
        AlienCreature baby = AlienCreature.hatch(helper.getLevel(), helper.absoluteVec(new Vec3(3.5, 1, 3.5)), "-", species.index(), traits, owner.getUUID());
        helper.assertTrue(baby != null, "幼崽没孵出来");
        helper.assertTrue(baby.isOwnedBy(owner) && baby.isYoung(), "幼崽不归主人，或者一孵出来就成年了");
        Companion companion = baby.companion();
        helper.assertTrue(companion != null && companion.trust() == Companion.TAME_TRUST && !companion.hungry(), "幼崽不是刚驯服的伙伴");
        double expected = species.size() * 1.2 * 0.5;
        helper.assertTrue(Math.abs(baby.getScale() - expected) < 0.01, "体型 " + baby.getScale() + "，应该是 " + expected);
        helper.assertFalse(baby.canBeRidden(), "幼崽能骑");
        baby.discard();
        helper.succeed();
    }

    /** 伙伴挖掘（ADR-0026 §九）：原地刨 3 秒挖出东西；刨的时候、同一个游戏日里都不能再挖。 */
    private static void companionDigsOnceADay(GameTestHelper helper) {
        companionFloor(helper);
        ServerLevel level = helper.getLevel();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        AlienCreature pet = AlienCreature.hatch(level, helper.absoluteVec(new Vec3(3.5, 1, 3.5)), "-", 0,
                CreatureTraits.of(Fauna.FALLBACK.get(0)), owner.getUUID());
        helper.assertTrue(pet != null, "伙伴没放出来");
        pet.setNoAi(true);
        helper.assertTrue(pet.startDig(level) == AlienCreature.DigResult.STARTED, "没开始挖");
        helper.assertTrue(pet.startDig(level) == AlienCreature.DigResult.BUSY, "刨着的时候又挖了一次");
        helper.runAfterDelay(AlienCreature.DIG_TICKS + 10, () -> {
            helper.assertFalse(pet.isDigging(), "三秒了还在刨");
            List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, pet.getBoundingBox().inflate(4));
            helper.assertFalse(found.isEmpty(), "什么也没挖出来");
            helper.assertTrue(pet.startDig(level) == AlienCreature.DigResult.NOT_READY, "一天挖了两次");
            found.forEach(ItemEntity::discard);
            pet.discard();
            helper.succeed();
        });
    }

    /** 伙伴的测试铺一块地：场地的地板在测试世界里放不出来。 */
    private static void companionFloor(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(1, 0, 1, 5, 0, 5)) {
            helper.setBlock(pos, Blocks.GRASS_BLOCK);
        }
    }

    /** 测试场地里这一块（含两端，相对坐标）灌满水。 */
    private static void flood(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(pos, Blocks.WATER);
        }
    }

    /**
     * 烧燃料的配方设备（ADR-0026）不接电也干活：营养处理器烧一块煤把牛奶做成黄油，空桶留在原料格里；
     * 便携精炼机烧异星原木把粗铁炼成铁锭。
     */
    private static void fuelMachinesCook(GameTestHelper helper) {
        BlockPos processorPos = new BlockPos(2, 1, 3);
        helper.setBlock(processorPos, UniverseBlocks.NUTRIENT_PROCESSOR.get());
        FuelRecipeMachineBlockEntity processor = helper.getBlockEntity(processorPos, FuelRecipeMachineBlockEntity.class);
        selectRecipe(processor, "universe:nutrient/butter");
        processor.setItem(0, new ItemStack(Items.MILK_BUCKET));
        processor.setItem(processor.fuelSlot(), new ItemStack(Items.COAL));
        BlockPos refinerPos = new BlockPos(5, 1, 3);
        helper.setBlock(refinerPos, UniverseBlocks.PORTABLE_REFINER.get());
        FuelRecipeMachineBlockEntity refiner = helper.getBlockEntity(refinerPos, FuelRecipeMachineBlockEntity.class);
        selectRecipe(refiner, "universe:portable_refining/iron_ingot");
        refiner.setItem(0, new ItemStack(Items.RAW_IRON, 2));
        refiner.setItem(refiner.fuelSlot(), new ItemStack(UniverseItems.PLANET_LOG.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(processor.getItem(3).is(UniverseItems.DISHES.get(Dish.BUTTER).get()), "营养处理器没有做出黄油");
            helper.assertTrue(processor.getItem(0).is(Items.BUCKET), "空桶没有留在原料格里：" + processor.getItem(0));
            helper.assertTrue(processor.getItem(processor.fuelSlot()).isEmpty(), "煤没有烧掉");
            helper.assertTrue(refiner.getItem(1).is(Items.IRON_INGOT), "便携精炼机没有炼出铁锭");
        });
    }

    private static void selectRecipe(RecipeMachineBlockEntity machine, String recipe) {
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", recipe);
        machine.applyBlueprintSettings(settings);
    }

    /**
     * 飞船交易终端卖的四种船（ADR-0024）放成方块、装上自带的模块、组装起来：方块一个不多一个不少（石头地面没带上），
     * 类型和档次跟设计时一样，自带模块都生效，1 g 下推重比够。
     */
    private static void dealerShipsAssemble(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<ShipType> seen = EnumSet.noneOf(ShipType.class);
        for (long day = 0; day < 40 && seen.size() < ShipType.values().length; day++) {
            for (ShipMarket.Offer offer : ShipMarket.ships(11, Economy.Wealth.WEALTHY, day)) {
                if (!seen.add(offer.type())) {
                    continue;
                }
                ShipDesign design = offer.design();
                Map<BlockPos, BlockState> layout = ShipBlueprints.layout(design, helper.absolutePos(new BlockPos(8, 1, 8)), Direction.EAST);
                BlockPos helm = null;
                for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                    if (entry.getValue().is(UniverseBlocks.SHIP_HELM.get())) {
                        helm = entry.getKey();
                    }
                }
                helper.assertTrue(helm != null, offer.type() + " 没有驾驶台");
                HelmBlockEntity tech = (HelmBlockEntity) level.getBlockEntity(helm);
                List<TechModule> modules = offer.modules();
                for (int i = 0; i < modules.size(); i++) {
                    ItemStack stack = new ItemStack(UniverseItems.MODULES.get(modules.get(i).type()).get());
                    stack.set(UniverseDataComponents.TECH_MODULE.get(), modules.get(i));
                    tech.items().set(i, stack);
                }
                tech.setChanged();
                ShipEntity ship = ShipAssembly.assemble(level, helm, null, message -> {
                });
                helper.assertTrue(ship != null && ship.structure() != null, offer.type() + " 组装不起来");
                helper.assertValueEqual(ship.structure().blocks().size(), design.cells().size(), offer.type() + " 的方块数");
                helper.assertValueEqual(ship.structure().rating().type(), design.rating().type(), offer.type() + " 的类型");
                helper.assertValueEqual(ship.structure().rating().grade(), design.rating().grade(), offer.type() + " 的档次");
                helper.assertValueEqual(ship.structure().tech().modules().size(), modules.size(), offer.type() + " 生效的模块");
                helper.assertTrue(ship.structure().stats().thrustToWeight(1) >= ShipDesigner.MIN_TWR, offer.type() + " 推重比不够");
                ship.discard();
            }
        }
        helper.assertValueEqual(seen.size(), ShipType.values().length, "四种船没凑齐");
        helper.succeed();
    }

    /** 舰队的存档（ADR-0024）：货船、护卫舰（受损的也记着）、在外面的远征、接过的任务，存了再读回来一样。 */
    private static void fleetSaveRoundTrip(GameTestHelper helper) {
        FleetData data = FleetData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        UUID owner = UUID.randomUUID();
        Fleet fleet = data.entry(owner).fleet();
        fleet.setFreighter(new Fleet.Freighter(data.takeFreighterId(), 99, Grade.A));
        Rng rng = Rng.of(1);
        for (int i = 0; i < 3; i++) {
            fleet.hire(Frigate.roll(i, rng));
        }
        fleet.frigate(1).setDamaged(true);
        Fleet.Voyage voyage = fleet.launch(5, 1, new Expedition.Offer(Expedition.Kind.TRADE, 2, 16), List.of(0, 2), 1234, Rng.of(2));
        Tag tag = FleetData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        FleetData back = FleetData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        Fleet read = back.find(owner).orElseThrow().fleet();
        helper.assertValueEqual(read.freighter(), fleet.freighter(), "货船");
        helper.assertValueEqual(read.frigates().size(), 3, "护卫舰数");
        helper.assertTrue(read.frigate(1).damaged(), "受损没记住");
        for (Frigate frigate : fleet.frigates()) {
            Frigate other = read.frigate(frigate.id());
            helper.assertTrue(other != null && other.total() == frigate.total() && other.traits().equals(frigate.traits())
                    && other.designation().equals(frigate.designation()), frigate.designation() + " 读回来不一样");
        }
        helper.assertValueEqual(read.voyages(), List.of(voyage), "远征");
        helper.assertTrue(read.taken(5, 1), "接过的任务");
        helper.assertValueEqual(read.nextFrigate(), 3, "下一个编号");
        helper.succeed();
    }

    /**
     * 远征回来（ADR-0024）：五艘强的打一星任务全胜，领了以后远征没了、护卫舰都出勤一次；各种任务、各个难度带回来的
     * 物品 ID 都认得出来（一样不丢），技术模块是飞船模块。
     */
    private static void fleetVoyageClaim(GameTestHelper helper) {
        Fleet fleet = new Fleet();
        fleet.setFreighter(new Fleet.Freighter(0, 7, Grade.C));
        List<Integer> team = new ArrayList<>();
        for (int i = 0; i < Expedition.MAX_TEAM; i++) {
            team.add(fleet.hire(new Frigate(0, "GT-10" + i, FrigateType.SUPPORT, new int[] {30, 30, 30, 30}, List.of(), 2, 0, false)).id());
        }
        Fleet.Voyage voyage = fleet.launch(0, 0, new Expedition.Offer(Expedition.Kind.INDUSTRIAL, 1, 10), team, 0, Rng.of(3));
        Fleet.Claim claim = fleet.claim(voyage);
        helper.assertTrue(fleet.voyages().isEmpty(), "远征还在");
        helper.assertTrue(voyage.events().stream().allMatch(Expedition.Event::success), "五艘强的没全胜");
        for (Frigate frigate : fleet.frigates()) {
            helper.assertValueEqual(frigate.expeditions(), 1, frigate.designation() + " 的出勤次数");
        }
        FleetTerminal.Haul haul = FleetTerminal.haul(voyage, claim.rewards());
        helper.assertTrue(haul.credits() >= 500, "全胜的信用点没给");
        // 各种任务、各个难度：每一样奖励都换得成东西
        for (Expedition.Kind kind : Expedition.Kind.values()) {
            for (int difficulty = 1; difficulty <= 5; difficulty++) {
                for (long seed = 0; seed < 8; seed++) {
                    Expedition.Offer offer = new Expedition.Offer(kind, difficulty, 10);
                    Rng rng = Rng.of(seed);
                    List<Expedition.Event> events = new ArrayList<>();
                    for (int i = 0; i < 2 + difficulty; i++) {
                        events.add(new Expedition.Event(rng.pick(io.github.sweetcornna.universe.core.fleet.Area.values()), true, -1,
                                Expedition.Outcome.NONE));
                    }
                    Fleet.Voyage trip = new Fleet.Voyage(0, 0, offer, team, 0, events, seed);
                    List<Expedition.Reward> rewards = Expedition.rewards(offer, events, rng);
                    int expected = rewards.stream().filter(r -> !r.key().equals(Expedition.CREDITS) && !r.key().equals(Expedition.GLYPH))
                            .mapToInt(Expedition.Reward::count).sum();
                    FleetTerminal.Haul got = FleetTerminal.haul(trip, rewards);
                    helper.assertValueEqual(got.count(), expected, kind + " " + difficulty + "★ 的奖励丢了东西");
                    for (ItemStack stack : got.items()) {
                        TechModule module = stack.get(UniverseDataComponents.TECH_MODULE.get());
                        helper.assertTrue(module == null || module.type().host() == io.github.sweetcornna.universe.core.tech.ModuleHost.SHIP,
                                "远征给了不是飞船的模块");
                    }
                }
            }
        }
        helper.succeed();
    }

    /** 坠毁的飞船（ADR-0024）：损坏的驾驶台组装不了；修好以后整艘船组装起来，1 g 下飞得起来。 */
    private static void crashedShipRepairs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long seed = 77;
        ShipDesign design = CrashedShip.design(seed);
        List<ShipDesign.Cell> wreck = CrashedShip.wreck(design, seed);
        ShipDesign broken = new ShipDesign(design.type(), design.grade(), design.primary(), design.accent(), wreck);
        BlockPos helm = null;
        for (Map.Entry<BlockPos, BlockState> entry : ShipBlueprints.layout(broken, helper.absolutePos(new BlockPos(8, 1, 8)), Direction.NORTH)
                .entrySet()) {
            BlockState state = entry.getValue();
            if (state.is(UniverseBlocks.SHIP_HELM.get())) {
                helm = entry.getKey();
                state = UniverseBlocks.DAMAGED_HELM.get().defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, state.getValue(HorizontalDirectionalBlock.FACING));
            }
            level.setBlock(entry.getKey(), state, 3);
        }
        helper.assertTrue(helm != null, "没有驾驶台");
        helper.assertTrue(level.getBlockState(helm).is(UniverseBlocks.DAMAGED_HELM.get()), "驾驶台没坏");
        PoiInteractions.fixHelm(level, helm, level.getBlockState(helm));
        helper.assertTrue(level.getBlockState(helm).is(UniverseBlocks.SHIP_HELM.get()), "没修好");
        ShipEntity ship = ShipAssembly.assemble(level, helm, null, message -> {
        });
        helper.assertTrue(ship != null && ship.structure() != null, "修好的船组装不起来");
        helper.assertValueEqual(ship.structure().blocks().size(), wreck.size(), "方块数");
        helper.assertTrue(ship.structure().stats().canLift(1), "飞不起来");
        ship.discard();
        helper.succeed();
    }

    /** 先驱者传送门（ADR-0024）：石环上刻着这颗星球的地址；拨自己、拨错的地址都不开；拨别的星球开门，环里填满光，关门撤掉。 */
    private static void portalDialsAndCloses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BodyId home = Planets.bodyId(level.dimension()).orElseThrow();
        int[] own = Portals.address(server, home);
        Portals.Site site = new Portals.Site(helper.absolutePos(new BlockPos(6, 1, 5)), Direction.SOUTH);
        Portals.build(level, site, own);
        for (PortalLayout.Cell cell : PortalLayout.cells()) {
            BlockState state = level.getBlockState(site.at(cell.x(), cell.y(), cell.z()));
            switch (cell.part()) {
                case STONE -> helper.assertTrue(state.is(UniverseBlocks.PORTAL_STONE.get()), "石环和石台");
                case GLYPH -> helper.assertValueEqual(state.getValue(PortalGlyphBlock.GLYPH), own[cell.glyph()], "第 " + (cell.glyph() + 1) + " 个符文");
                case DIAL -> helper.assertTrue(state.is(UniverseBlocks.PORTAL_DIAL.get()), "拨号台");
                case FIELD -> helper.assertTrue(state.isAir(), "门关着，环里该是空的");
            }
        }
        helper.assertTrue(Portals.dial(level, site, own) != null, "拨自己不该开门");
        // 改一个符文，挑一个校验对不上的
        int[] wrong = own.clone();
        do {
            wrong[PortalAddress.LENGTH - 1] = (wrong[PortalAddress.LENGTH - 1] + 1) % PortalAddress.GLYPHS;
        } while (PortalAddress.decode(Planets.galaxy(server).seed(), wrong).isPresent());
        helper.assertTrue(Portals.dial(level, site, wrong) != null, "拨错的地址不该开门");
        BodyId target = null;
        for (StarSystem system : Planets.galaxy(server).systems()) {
            for (Planet planet : system.planets()) {
                if (target == null && planet.landable() && !planet.id().equals(home)) {
                    target = planet.id();
                }
            }
        }
        helper.assertTrue(target != null, "星团里没有别的能着陆的星球");
        helper.assertTrue(Portals.dial(level, site, Portals.address(server, target)) == null, "拨别的星球该开门");
        for (int[] c : PortalLayout.field()) {
            helper.assertTrue(level.getBlockState(site.at(c[0], c[1], c[2])).is(UniverseBlocks.PORTAL_FIELD.get()), "门开了，环里该有光");
        }
        Portals.close(level, site);
        for (int[] c : PortalLayout.field()) {
            helper.assertTrue(level.getBlockState(site.at(c[0], c[1], c[2])).isAir(), "门关了，光该撤掉");
        }
        helper.succeed();
    }

    /** 一艘最小的船：驾驶台在 (3, y, 3) 朝北，东西两边各一个推进器，后面一个燃料舱。返回驾驶台的位置。 */
    private static BlockPos smallShip(GameTestHelper helper, int y) {
        BlockPos helm = new BlockPos(3, y, 3);
        helper.setBlock(helm, UniverseBlocks.SHIP_HELM.get());
        helper.setBlock(new BlockPos(2, y, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(4, y, 3), UniverseBlocks.SHIP_THRUSTER.get());
        helper.setBlock(new BlockPos(3, y, 4), UniverseBlocks.FUEL_TANK.get());
        return helm;
    }

    /** 场地西北角一座感应塔，东北角一台装了燃料棒的人造恒星，中间的设备都在塔的覆盖范围里。 */
    private static void powerPlant(GameTestHelper helper) {
        tall(helper, new BlockPos(1, 1, 1), UniverseBlocks.TESLA_TOWER.get().defaultBlockState());
        BlockPos star = new BlockPos(5, 1, 1);
        tall(helper, star, UniverseBlocks.ARTIFICIAL_STAR.get().defaultBlockState());
        helper.getBlockEntity(star, FuelGeneratorBlockEntity.class)
                .setItem(FuelGeneratorBlockEntity.FUEL_SLOT, new ItemStack(UniverseItems.ANTIMATTER_FUEL_ROD.get(), 4));
    }

    /**
     * 外星文明（ADR-0027）：只教没学过的词，教完返回 -1；声望夹在 −20 到 100；每天一次按游戏日记；
     * 附件的存档格式和同步格式来回一趟不丢东西；主世界算母星系，主导种族是商旅族。
     */
    private static void civTeachesWords(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        CivState state = Civ.state(player);
        helper.assertTrue(state.knownCount(Race.SCHOLAR) == Lexicon.INNATE, "一开始认识的词不是 8 个");
        long mask = 1L << 30 | 1L << 31;
        int first = Civ.teachWord(player, Race.SCHOLAR, mask, 1);
        int second = Civ.teachWord(player, Race.SCHOLAR, mask, 1);
        helper.assertTrue(first >= 30 && second >= 30 && first != second, "没从给的两个词里各教一个：" + first + "、" + second);
        helper.assertTrue(Civ.teachWord(player, Race.SCHOLAR, mask, 1) == -1, "两个词都会了还在教");
        int taught = 0;
        while (Civ.teachWord(player, Race.WARDEN, -1L, taught) >= 0) {
            taught++;
        }
        helper.assertTrue(taught == Lexicon.SIZE - Lexicon.INNATE, "整张词表教了 " + taught + " 个");
        helper.assertTrue(Civ.state(player).knownCount(Race.WARDEN) == Lexicon.SIZE, "词表没学全");
        helper.assertTrue(!Civ.state(player).knows(Race.TRADER, first), "教了学者族的词，商旅族的也会了");

        Civ.changeStanding(player, Race.TRADER, 250);
        helper.assertTrue(Civ.state(player).standing(Race.TRADER) == Standing.MAX, "声望没夹在 100");
        helper.assertTrue(Civ.state(player).tier(Race.TRADER) == Standing.Tier.KINDRED, "100 不是至交");
        Civ.changeStanding(player, Guild.EXPLORERS, -250);
        helper.assertTrue(Civ.state(player).standing(Guild.EXPLORERS) == Standing.MIN, "公会声望没夹在 -20");

        helper.assertTrue(!Civ.usedToday(player, "talk:test"), "没做过就算做过了");
        Civ.markUsed(player, "talk:test");
        helper.assertTrue(Civ.usedToday(player, "talk:test"), "做过了没记下");

        CivState saved = CivCodecs.CODEC.codec()
                .parse(NbtOps.INSTANCE, CivCodecs.CODEC.codec().encodeStart(NbtOps.INSTANCE, state).getOrThrow()).getOrThrow();
        helper.assertTrue(saved.knownCount(Race.WARDEN) == Lexicon.SIZE && saved.knows(Race.SCHOLAR, first) && saved.standing(Race.TRADER) == Standing.MAX
                && saved.standing(Guild.EXPLORERS) == Standing.MIN && saved.daily().equals(state.daily()), "存档来回一趟丢了东西");
        ByteBuf buf = Unpooled.buffer();
        CivCodecs.STREAM_CODEC.encode(buf, state);
        CivState synced = CivCodecs.STREAM_CODEC.decode(buf);
        helper.assertTrue(synced.knownCount(Race.SCHOLAR) == state.knownCount(Race.SCHOLAR) && synced.standing(Race.TRADER) == Standing.MAX,
                "同步来回一趟丢了东西");

        helper.assertTrue(Civ.dominant(helper.getLevel().getServer().overworld()).orElse(null) == Race.TRADER, "母星系不是商旅族");
        helper.succeed();
    }

    /**
     * 知识石（ADR-0027 §五）：第一次摸学会一个主导种族的词，记下这一块；再摸（上半也算这一块）不教。另一块还能学。
     * 主世界算母星系，主导种族是商旅族。场地的地面不会放，自己铺一块。
     */
    private static void knowledgeStoneTeachesOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 6; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
        BlockState lower = UniverseBlocks.KNOWLEDGE_STONE.get().defaultBlockState();
        BlockState upper = lower.setValue(KnowledgeStoneBlock.HALF, DoubleBlockHalf.UPPER);
        BlockPos first = new BlockPos(2, 1, 3);
        BlockPos second = new BlockPos(5, 1, 3);
        for (BlockPos pos : List.of(first, second)) {
            helper.setBlock(pos, lower);
            helper.setBlock(pos.above(), upper);
        }
        helper.assertTrue(Civ.dominant(level).equals(Optional.of(Race.TRADER)), "主世界的主导种族不是商旅族");
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        int known = Civ.state(player).knownCount(Race.TRADER);
        BlockPos at = helper.absolutePos(first);
        helper.assertTrue(KnowledgeStoneBlock.touch(player, level, at) == KnowledgeStoneBlock.Reading.LEARNED, "第一次摸没教词");
        helper.assertTrue(Civ.state(player).knownCount(Race.TRADER) == known + 1, "认识的词没多一个");
        helper.assertTrue(Civ.state(player).stones().contains(KnowledgeStoneBlock.key(level, at)), "没记下摸过这一块");
        // 右键上半，算同一块
        helper.useBlock(first.above(), player);
        helper.assertTrue(Civ.state(player).knownCount(Race.TRADER) == known + 1, "摸上半又教了一个词");
        helper.assertTrue(KnowledgeStoneBlock.touch(player, level, at) == KnowledgeStoneBlock.Reading.READ_BEFORE, "第二次摸没说读过了");
        helper.assertTrue(Civ.state(player).knownCount(Race.TRADER) == known + 1, "第二次摸又教了一个词");
        helper.assertTrue(Civ.state(player).stones().size() == 1, "记了不止一块");
        helper.useBlock(second, player);
        helper.assertTrue(Civ.state(player).knownCount(Race.TRADER) == known + 2, "另一块没教词");
        // 挖不掉
        helper.assertTrue(level.getBlockState(at).getDestroySpeed(level, at) < 0, "知识石能挖");
        helper.succeed();
    }

    /** 一台选好齿轮配方、放了一组铁锭的一型制造台。 */
    private static BlockPos assembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, UniverseBlocks.ASSEMBLER.get());
        RecipeMachineBlockEntity assembler = helper.getBlockEntity(pos, RecipeMachineBlockEntity.class);
        CompoundTag settings = new CompoundTag();
        settings.putString("Recipe", GEAR_RECIPE);
        assembler.applyBlueprintSettings(settings);
        assembler.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        return pos;
    }

    private static void tall(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(pos.above(), state.setValue(TallMachineBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** 把一个科技的前置（连同前置的前置）都记成已完成。 */
    private static void completeRequirements(ResearchState state, String id) {
        Tech tech = Research.TREE.get(id).orElseThrow();
        for (String required : tech.requires()) {
            if (!state.isDone(required)) {
                completeRequirements(state, required);
                state.complete(required);
            }
        }
    }

    /** 泡在酸液里的生物会被灼伤；酸液不算水，水生植物不认它（ADR-0030 §一）。 */
    private static void acidBurns(GameTestHelper helper) {
        BlockPos pool = new BlockPos(3, 2, 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(pool.relative(side), Blocks.STONE);
        }
        helper.setBlock(pool, UniverseBlocks.ACID.get());
        // 酸液不在 minecraft:water 里：海带这类水生植物放不下去
        helper.assertTrue(!helper.getBlockState(pool).getFluidState().is(FluidTags.WATER), "酸液被当成了水");
        helper.assertTrue(AcidFluid.is(helper.getBlockState(pool).getFluidState()), "这格不是酸液");
        Cow cow = helper.spawn(EntityTypes.COW, pool);
        float health = cow.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(!cow.isAlive() || cow.getHealth() < health,
                "泡在酸里没掉血：" + cow.getHealth()));
    }

    /** 掉进酸液的铁锭会被蚀掉，玻璃不怕酸（ADR-0030 §一）。 */
    private static void acidCorrodesItems(GameTestHelper helper) {
        BlockPos pool = new BlockPos(3, 2, 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(pool.relative(side), Blocks.STONE);
        }
        helper.setBlock(pool, UniverseBlocks.ACID.get());
        // 让物品自己沉进酸里：浮着不动的实体每刻不走 move()，泡的深度算不出来
        Vec3 centre = helper.absoluteVec(new Vec3(3.5, 2.4, 3.5));
        ServerLevel level = helper.getLevel();
        ItemEntity iron = new ItemEntity(level, centre.x, centre.y, centre.z, new ItemStack(Items.IRON_INGOT, 64));
        ItemEntity glass = new ItemEntity(level, centre.x, centre.y, centre.z, new ItemStack(Items.GLASS, 64));
        iron.setDeltaMovement(0, 0, 0);
        glass.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(iron);
        level.addFreshEntity(glass);
        // 腐蚀过 CORRODE_DELAY 刻才开始，再等两轮：沉下去那几刻不算泡着
        helper.runAfterDelay(Acid.CORRODE_DELAY + Acid.CORRODE_INTERVAL * 2 + 20, () -> {
            helper.assertTrue(!iron.isAlive() || iron.getItem().getCount() < 64, "铁锭没被蚀掉：" + iron.getItem().getCount());
            helper.assertTrue(glass.isAlive() && glass.getItem().getCount() == 64, "玻璃被蚀掉了");
            helper.succeed();
        });
    }

    /** 抽水站放在酸液源头上抽出硫酸，放在水源上还是水（ADR-0030 §一）。 */
    private static void pumpMakesSulfuricAcid(GameTestHelper helper) {
        powerPlant(helper);
        BlockPos acid = new BlockPos(3, 1, 3);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(acid.relative(side), Blocks.STONE);
        }
        helper.setBlock(acid, UniverseBlocks.ACID.get());
        BlockPos pump = acid.above();
        helper.setBlock(pump, UniverseBlocks.WATER_PUMP.get());
        helper.succeedWhen(() -> {
            MachineBlockEntity entity = helper.getBlockEntity(pump, MachineBlockEntity.class);
            helper.assertValueEqual(entity.data().get(WaterPumpBlockEntity.SOURCE), WaterPumpBlockEntity.ACID, "抽水站下面的液体");
            helper.assertTrue(entity.countItem(UniverseItems.SULFURIC_ACID.get()) > 0, "抽水站没有产出硫酸");
        });
    }

}
