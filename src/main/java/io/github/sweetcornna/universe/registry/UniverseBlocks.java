package io.github.sweetcornna.universe.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.FloraVariantBlock;
import io.github.sweetcornna.universe.block.HydroponicTrayBlock;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.block.PlanetBloomBlock;
import io.github.sweetcornna.universe.block.PlanetLeavesBlock;
import io.github.sweetcornna.universe.block.PlanetLogBlock;
import io.github.sweetcornna.universe.block.PlanetPlantBlock;
import io.github.sweetcornna.universe.block.ResourcePlantBlock;
import io.github.sweetcornna.universe.block.encounter.LiveConduitBlock;
import io.github.sweetcornna.universe.block.poi.DataTerminalBlock;
import io.github.sweetcornna.universe.block.poi.DropPodBlock;
import io.github.sweetcornna.universe.block.poi.MonolithBlock;
import io.github.sweetcornna.universe.block.portal.PortalDialBlock;
import io.github.sweetcornna.universe.block.portal.PortalFieldBlock;
import io.github.sweetcornna.universe.block.portal.PortalGlyphBlock;
import io.github.sweetcornna.universe.block.poi.SignalBoosterBlock;
import io.github.sweetcornna.universe.block.poi.SmolderingWreckBlock;
import io.github.sweetcornna.universe.block.poi.SupplyCrateBlock;
import io.github.sweetcornna.universe.block.fleet.FleetTerminalBlock;
import io.github.sweetcornna.universe.block.fleet.FreighterConsoleBlock;
import io.github.sweetcornna.universe.block.ship.DamagedHelmBlock;
import io.github.sweetcornna.universe.block.ship.FuelTankBlock;
import io.github.sweetcornna.universe.block.ship.ImpulseEngineBlock;
import io.github.sweetcornna.universe.block.ship.ShieldGeneratorBlock;
import io.github.sweetcornna.universe.block.ship.LandingPadBlock;
import io.github.sweetcornna.universe.block.ship.ShipHelmBlock;
import io.github.sweetcornna.universe.block.ship.ShipSeatBlock;
import io.github.sweetcornna.universe.block.ship.ShipThrusterBlock;
import io.github.sweetcornna.universe.block.ship.ShipWeaponBlock;
import io.github.sweetcornna.universe.block.ship.WarpDriveBlock;
import io.github.sweetcornna.universe.block.station.MissionBoardBlock;
import io.github.sweetcornna.universe.block.station.ShipDealerBlock;
import io.github.sweetcornna.universe.block.station.ShipRepairBlock;
import io.github.sweetcornna.universe.block.station.TradeTerminalBlock;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.ecology.ResourcePlant;
import io.github.sweetcornna.universe.core.factory.BeltTier;
import io.github.sweetcornna.universe.core.factory.SorterTier;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.dyson.DysonBlocks;
import io.github.sweetcornna.universe.factory.ArcSmelterBlock;
import io.github.sweetcornna.universe.factory.FactoryAnchorBlock;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.ProcessingBlocks;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.belt.BeltBlock;
import io.github.sweetcornna.universe.factory.belt.PilerBlock;
import io.github.sweetcornna.universe.factory.belt.SorterBlock;
import io.github.sweetcornna.universe.factory.belt.SplitterBlock;
import io.github.sweetcornna.universe.factory.belt.SprayCoaterBlock;
import io.github.sweetcornna.universe.factory.belt.StorageDepotBlock;
import io.github.sweetcornna.universe.factory.belt.StorageTankBlock;
import io.github.sweetcornna.universe.factory.belt.TrafficMonitorBlock;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlock;
import io.github.sweetcornna.universe.factory.power.ArtificialStarBlock;
import io.github.sweetcornna.universe.factory.power.FusionPlantBlock;
import io.github.sweetcornna.universe.factory.power.PowerTowerBlock;
import io.github.sweetcornna.universe.factory.power.SolarPanelBlock;
import io.github.sweetcornna.universe.factory.power.TeslaTowerBlockEntity;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlock;
import io.github.sweetcornna.universe.factory.resource.CrudeOilSeepBlock;
import io.github.sweetcornna.universe.factory.resource.ResourceMachineBlocks;
import io.github.sweetcornna.universe.logistics.DistributorBlock;
import io.github.sweetcornna.universe.logistics.OrbitalCollectorBlock;
import io.github.sweetcornna.universe.logistics.StationBlock;
import io.github.sweetcornna.universe.teleport.BaseBeaconBlock;
import io.github.sweetcornna.universe.teleport.TeleporterBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SandBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块注册。
 *
 * <p>星球方块（{@code planet_*}）用灰度贴图，客户端按所在星球的配色染色（技术架构 §3.4）：
 * 同一块岩石在紫色星球上是紫的，带回主世界就变成中性灰。这和原版的草方块随群系变色是一个道理。
 */
public final class UniverseBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Universe.MOD_ID);

    public static final DeferredBlock<Block> PLANET_ROCK = BLOCKS.registerSimpleBlock("planet_rock",
            p -> p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
    public static final DeferredBlock<Block> PLANET_COBBLESTONE = BLOCKS.registerSimpleBlock("planet_cobblestone",
            p -> p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F));
    public static final DeferredBlock<Block> PLANET_SOIL = BLOCKS.registerSimpleBlock("planet_soil",
            p -> p.mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.GRAVEL));
    public static final DeferredBlock<Block> PLANET_TURF = BLOCKS.registerSimpleBlock("planet_turf",
            p -> p.mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS));
    public static final DeferredBlock<Block> PLANET_REGOLITH = BLOCKS.registerSimpleBlock("planet_regolith",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.5F).sound(SoundType.GRAVEL));
    public static final DeferredBlock<SandBlock> PLANET_SAND = BLOCKS.registerBlock("planet_sand",
            p -> new SandBlock(new ColorRGBA(0xFFD6CDA6), p),
            p -> p.mapColor(MapColor.SAND).instrument(NoteBlockInstrument.SNARE).strength(0.5F).sound(SoundType.SAND));
    public static final DeferredBlock<Block> PLANET_SNOW = BLOCKS.registerSimpleBlock("planet_snow",
            p -> p.mapColor(MapColor.SNOW).strength(0.2F).sound(SoundType.SNOW));
    public static final DeferredBlock<Block> PLANET_ICE = BLOCKS.registerSimpleBlock("planet_ice",
            p -> p.mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME).friction(0.98F).strength(0.5F).sound(SoundType.GLASS));
    public static final DeferredBlock<Block> PLANET_ASH = BLOCKS.registerSimpleBlock("planet_ash",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND));
    /** 岩层：按高度分层着色（每颗星球一套层序），方山和峡谷的崖壁用它。 */
    public static final DeferredBlock<Block> PLANET_STRATA = BLOCKS.registerSimpleBlock("planet_strata",
            p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                    .strength(1.25F, 4.2F));
    public static final DeferredBlock<Block> PLANET_CRYSTAL = BLOCKS.registerSimpleBlock("planet_crystal",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.5F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
                    .lightLevel(state -> 10));

    /** 地面植物：每种形态一个方块，物种之间靠变体号区分颜色（{@link FloraBlocks}）。 */
    public static final Map<PlantForm, DeferredBlock<PlanetPlantBlock>> PLANTS = registerPlants();
    public static final DeferredBlock<PlanetLogBlock> PLANET_LOG = BLOCKS.registerBlock("planet_log", PlanetLogBlock::new,
            p -> p.mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<PlanetLeavesBlock> PLANET_LEAVES = BLOCKS.registerBlock("planet_leaves", PlanetLeavesBlock::new,
            p -> p.mapColor(MapColor.PLANT).strength(0.2F).sound(SoundType.GRASS).noOcclusion().isValidSpawn(Blocks::never)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false).ignitedByLava().pushReaction(PushReaction.POPPED)
                    .isRedstoneConductor((state, level, pos) -> false));
    /** 肉质的实心方块：巨菌的菌盖、柱状肉茎。 */
    public static final DeferredBlock<FloraVariantBlock> PLANET_CAP = BLOCKS.registerBlock("planet_cap", FloraVariantBlock::new,
            p -> p.mapColor(MapColor.PLANT).strength(0.8F).sound(SoundType.FUNGUS));
    /** 树上的花果，有的物种会发光。 */
    public static final DeferredBlock<PlanetBloomBlock> PLANET_BLOOM = BLOCKS.registerBlock("planet_bloom", PlanetBloomBlock::new,
            p -> p.mapColor(MapColor.COLOR_PINK).strength(0.4F).sound(SoundType.SHROOMLIGHT).noOcclusion()
                    .isValidSpawn(Blocks::never).isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos, box) -> false)
                    .lightLevel(state -> state.getValue(FloraBlocks.LIT) ? 13 : 0));

    // ---- 飞船部件（ADR-0008）
    public static final DeferredBlock<ShipHelmBlock> SHIP_HELM = BLOCKS.registerBlock("ship_helm", ShipHelmBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    /** 坠毁飞船上损坏的驾驶台（ADR-0024），修好变回驾驶台。 */
    public static final DeferredBlock<DamagedHelmBlock> DAMAGED_HELM = BLOCKS.registerBlock("damaged_ship_helm", DamagedHelmBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<ShipThrusterBlock> SHIP_THRUSTER = BLOCKS.registerBlock("ship_thruster", ShipThrusterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<FuelTankBlock> FUEL_TANK = BLOCKS.registerBlock("fuel_tank", FuelTankBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops());
    public static final DeferredBlock<ImpulseEngineBlock> IMPULSE_ENGINE = BLOCKS.registerBlock("impulse_engine", ImpulseEngineBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> 7));
    public static final DeferredBlock<WarpDriveBlock> WARP_DRIVE = BLOCKS.registerBlock("warp_drive", WarpDriveBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> 9));
    public static final DeferredBlock<ShipSeatBlock> SHIP_SEAT = BLOCKS.registerBlock("ship_seat", ShipSeatBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5F).sound(SoundType.WOOL).noOcclusion());
    /** 着陆台（ADR-0024）：接电网给停在上面的船加燃料，也是召唤飞船的落点。 */
    public static final DeferredBlock<LandingPadBlock> LANDING_PAD = BLOCKS.registerBlock("landing_pad", LandingPadBlock::new,
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 9 : 3));
    // ---- 飞船武器、护盾和 Mk.II 部件（ADR-0017）
    public static final DeferredBlock<ShipWeaponBlock> PHOTON_CANNON = BLOCKS.registerBlock("photon_cannon", ShipWeaponBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(state -> 4));
    public static final DeferredBlock<ShipWeaponBlock> MINING_BEAM = BLOCKS.registerBlock("mining_beam", ShipWeaponBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(state -> 4));
    public static final DeferredBlock<ShieldGeneratorBlock> SHIELD_GENERATOR = BLOCKS.registerBlock("shield_generator", ShieldGeneratorBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> 8));
    public static final DeferredBlock<ShipThrusterBlock> SHIP_THRUSTER_MK2 = BLOCKS.registerBlock("ship_thruster_mk2", ShipThrusterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<FuelTankBlock> FUEL_TANK_MK2 = BLOCKS.registerBlock("fuel_tank_mk2",
            p -> new FuelTankBlock(p, ShipStats.FUEL_PER_TANK_MK2),
            p -> p.mapColor(MapColor.COLOR_RED).strength(3.5F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());
    public static final DeferredBlock<ImpulseEngineBlock> IMPULSE_ENGINE_MK2 = BLOCKS.registerBlock("impulse_engine_mk2", ImpulseEngineBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(5.0F, 10.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> 9));
    public static final DeferredBlock<WarpDriveBlock> WARP_DRIVE_MK2 = BLOCKS.registerBlock("warp_drive_mk2", WarpDriveBlock::new,
            p -> p.mapColor(MapColor.COLOR_MAGENTA).strength(5.0F, 10.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> 11));
    public static final DeferredBlock<Block> ALLOY_HULL_PLATING = BLOCKS.registerSimpleBlock("alloy_hull_plating",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(5.0F, 12.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());
    // ---- 兴趣点（ADR-0014）
    /** 掉落舱的控制台：挖不掉，修好一次就亮绿灯。 */
    public static final DeferredBlock<DropPodBlock> DROP_POD = BLOCKS.registerBlock("drop_pod", DropPodBlock::new,
            p -> p.mapColor(MapColor.SNOW).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable()
                    .lightLevel(state -> state.getValue(DropPodBlock.REPAIRED) ? 9 : 4).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<DataTerminalBlock> DATA_TERMINAL = BLOCKS.registerBlock("data_terminal", DataTerminalBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(state -> 6));
    /** 先驱者的石碑：挖不掉。 */
    public static final DeferredBlock<MonolithBlock> MONOLITH = BLOCKS.registerBlock("monolith", MonolithBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_TILES).noLootTable()
                    .lightLevel(state -> 7).pushReaction(PushReaction.IMMOVEABLE));
    /** 先驱者传送门（ADR-0024）：石台和石环、刻着地址的符文石、拨号台、门开时环里的那层光。都挖不掉、推不动。 */
    public static final DeferredBlock<Block> PORTAL_STONE = BLOCKS.registerSimpleBlock("portal_stone",
            p -> p.mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_TILES).noLootTable()
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<PortalGlyphBlock> PORTAL_GLYPH = BLOCKS.registerBlock("portal_glyph", PortalGlyphBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_TILES).noLootTable()
                    .lightLevel(state -> 7).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<PortalDialBlock> PORTAL_DIAL = BLOCKS.registerBlock("portal_dial", PortalDialBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_TILES).noLootTable()
                    .lightLevel(state -> 5).pushReaction(PushReaction.IMMOVEABLE));
    /** 废弃货船里漏电的线管（ADR-0024）：打不坏，发光，冒火花。 */
    public static final DeferredBlock<LiveConduitBlock> LIVE_CONDUIT = BLOCKS.registerBlock("live_conduit", LiveConduitBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.COPPER).noLootTable().lightLevel(state -> 7)
                    .pushReaction(PushReaction.IMMOVEABLE).isValidSpawn(Blocks::never));
    public static final DeferredBlock<PortalFieldBlock> PORTAL_FIELD = BLOCKS.registerBlock("portal_field", PortalFieldBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).sound(SoundType.GLASS).noLootTable().noCollision()
                    .noOcclusion().randomTicks().lightLevel(state -> 12).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn(Blocks::never)
                    .isRedstoneConductor((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false));
    // ---- 星球生活（ADR-0026）
    /** 资源植物：每种一个方块，长熟时能收获。 */
    public static final Map<ResourcePlant, DeferredBlock<ResourcePlantBlock>> RESOURCE_PLANTS = registerResourcePlants();
    public static final DeferredBlock<HydroponicTrayBlock> HYDROPONIC_TRAY = BLOCKS.registerBlock("hydroponic_tray", HydroponicTrayBlock::new,
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 6 : 0));
    /** 科技储藏箱：埋在地下，挖开掉一个随机模块。 */
    public static final DeferredBlock<Block> TECH_CACHE = BLOCKS.registerSimpleBlock("tech_cache",
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.5F, 6.0F).sound(SoundType.METAL).lightLevel(state -> 5));
    public static final DeferredBlock<SignalBoosterBlock> SIGNAL_BOOSTER = BLOCKS.registerBlock("signal_booster", SignalBoosterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> 4));
    public static final DeferredBlock<SupplyCrateBlock> SUPPLY_CRATE = BLOCKS.registerBlock("supply_crate", SupplyCrateBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6.0F).sound(SoundType.METAL));
    /** 坠毁飞船上还在冒烟的残骸，挖掉出船体板。 */
    public static final DeferredBlock<SmolderingWreckBlock> SMOLDERING_WRECK = BLOCKS.registerBlock("smoldering_wreck",
            SmolderingWreckBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops().lightLevel(state -> 3));
    // ---- 空间站（ADR-0017）：外壳、地板、玻璃、灯板、停机坪标线、灯带都挖不掉
    public static final DeferredBlock<Block> STATION_HULL = BLOCKS.registerSimpleBlock("station_hull",
            p -> station(p.mapColor(MapColor.METAL)));
    public static final DeferredBlock<Block> STATION_FLOOR = BLOCKS.registerSimpleBlock("station_floor",
            p -> station(p.mapColor(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<TransparentBlock> STATION_GLASS = BLOCKS.registerBlock("station_glass", TransparentBlock::new,
            p -> station(p.mapColor(MapColor.NONE)).sound(SoundType.GLASS).noOcclusion().isValidSpawn(Blocks::never)
                    .isRedstoneConductor((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false));
    public static final DeferredBlock<Block> STATION_LIGHT = BLOCKS.registerSimpleBlock("station_light",
            p -> station(p.mapColor(MapColor.SNOW)).lightLevel(state -> 15));
    public static final DeferredBlock<Block> STATION_PAD = BLOCKS.registerSimpleBlock("station_pad",
            p -> station(p.mapColor(MapColor.COLOR_YELLOW)));
    public static final DeferredBlock<Block> STATION_TRIM = BLOCKS.registerSimpleBlock("station_trim",
            p -> station(p.mapColor(MapColor.COLOR_CYAN)).lightLevel(state -> 9));
    /** 空间站两翼的太阳能板（ADR-0021），只是外观，不发电。 */
    public static final DeferredBlock<Block> STATION_SOLAR_PANEL = BLOCKS.registerSimpleBlock("station_solar_panel",
            p -> station(p.mapColor(MapColor.COLOR_BLUE)));
    /** 交易终端：挖不掉，只在空间站里有。 */
    public static final DeferredBlock<TradeTerminalBlock> TRADE_TERMINAL = BLOCKS.registerBlock("trade_terminal", TradeTerminalBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable().lightLevel(state -> 10)
                    .pushReaction(PushReaction.IMMOVEABLE));
    /** 任务板：挖不掉，只在空间站的大厅里有。 */
    public static final DeferredBlock<MissionBoardBlock> MISSION_BOARD = BLOCKS.registerBlock("mission_board", MissionBoardBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable().lightLevel(state -> 10)
                    .pushReaction(PushReaction.IMMOVEABLE));
    /** 飞船维修台：挖不掉，只在空间站的机库里有。 */
    public static final DeferredBlock<ShipRepairBlock> SHIP_REPAIR = BLOCKS.registerBlock("ship_repair", ShipRepairBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable().lightLevel(state -> 10)
                    .pushReaction(PushReaction.IMMOVEABLE));
    /** 飞船交易终端（ADR-0024）：挖不掉，只在空间站的机库里有。 */
    public static final DeferredBlock<ShipDealerBlock> SHIP_DEALER = BLOCKS.registerBlock("ship_dealer", ShipDealerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable().lightLevel(state -> 10)
                    .pushReaction(PushReaction.IMMOVEABLE));
    // ---- 货船（ADR-0024）：外壳的深色装甲板和引擎喷口，舰桥的控制台、指挥室的舰队终端，都挖不掉
    public static final DeferredBlock<Block> FREIGHTER_PLATE = BLOCKS.registerSimpleBlock("freighter_plate",
            p -> station(p.mapColor(MapColor.COLOR_GRAY)));
    public static final DeferredBlock<Block> FREIGHTER_ENGINE = BLOCKS.registerSimpleBlock("freighter_engine",
            p -> station(p.mapColor(MapColor.COLOR_LIGHT_BLUE)).lightLevel(state -> 15));
    public static final DeferredBlock<FreighterConsoleBlock> FREIGHTER_CONSOLE = BLOCKS.registerBlock("freighter_console",
            FreighterConsoleBlock::new, p -> station(p.mapColor(MapColor.METAL)).lightLevel(state -> 10));
    public static final DeferredBlock<FleetTerminalBlock> FLEET_TERMINAL = BLOCKS.registerBlock("fleet_terminal", FleetTerminalBlock::new,
            p -> station(p.mapColor(MapColor.METAL)).lightLevel(state -> 10));
    /** 空间站的传送器：挖不掉，不用电。 */
    public static final DeferredBlock<TeleporterBlock> STATION_TELEPORTER = BLOCKS.registerBlock("station_teleporter", TeleporterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable()
                    .lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 4).pushReaction(PushReaction.IMMOVEABLE));
    // ---- 基地和传送（ADR-0017）
    public static final DeferredBlock<BaseBeaconBlock> BASE_BEACON = BLOCKS.registerBlock("base_beacon", BaseBeaconBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()
                    .lightLevel(state -> 7));
    public static final DeferredBlock<TeleporterBlock> TELEPORTER = BLOCKS.registerBlock("teleporter", TeleporterBlock::new,
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 2));
    // ---- 工业（ADR-0011）
    public static final DeferredBlock<PowerTowerBlock> TESLA_TOWER = BLOCKS.registerBlock("tesla_tower",
            p -> new PowerTowerBlock(p, TeslaTowerBlockEntity.SPEC, 0), UniverseBlocks::tower);
    public static final DeferredBlock<TallMachineBlock> WIND_TURBINE = BLOCKS.registerBlock("wind_turbine",
            p -> new TallMachineBlock(p, UniverseBlockEntities.WIND_TURBINE), p -> machine(p).noOcclusion());
    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel",
            p -> new SolarPanelBlock(p, UniverseBlockEntities.SOLAR_PANEL), p -> machine(p).noOcclusion());
    public static final DeferredBlock<ThermalPlantBlock> THERMAL_PLANT = BLOCKS.registerBlock("thermal_plant", ThermalPlantBlock::new,
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING)
                    && state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.LOWER ? 11 : 0));
    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR = BLOCKS.registerBlock("accumulator", AccumulatorBlock::new,
            p -> machine(p).lightLevel(state -> state.getValue(AccumulatorBlock.CHARGE) > 0 ? 4 : 0));
    public static final DeferredBlock<ArcSmelterBlock> ARC_SMELTER = BLOCKS.registerBlock("arc_smelter", ArcSmelterBlock::new,
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 12 : 0));
    public static final DeferredBlock<MachineBlock> ASSEMBLER = BLOCKS.registerBlock("assembler",
            p -> new MachineBlock(p, UniverseBlockEntities.ASSEMBLER),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 6 : 0));
    public static final DeferredBlock<ResourceMachineBlocks.MiningMachine> MINING_MACHINE = BLOCKS.registerBlock("mining_machine",
            ResourceMachineBlocks.MiningMachine::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 5 : 0));
    public static final DeferredBlock<ResourceMachineBlocks.WaterPump> WATER_PUMP = BLOCKS.registerBlock("water_pump",
            ResourceMachineBlocks.WaterPump::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 5 : 0));
    public static final DeferredBlock<ResourceMachineBlocks.OilExtractor> OIL_EXTRACTOR = BLOCKS.registerBlock("oil_extractor",
            ResourceMachineBlocks.OilExtractor::new, p -> machine(p).noOcclusion());
    public static final DeferredBlock<ResourceMachineBlocks.OilRefinery> OIL_REFINERY = BLOCKS.registerBlock("oil_refinery",
            ResourceMachineBlocks.OilRefinery::new, p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING)
                    && state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.LOWER ? 10 : 0));
    public static final DeferredBlock<MachineBlock> MATRIX_LAB = BLOCKS.registerBlock("matrix_lab",
            p -> new MachineBlock(p, UniverseBlockEntities.MATRIX_LAB),
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 3));
    /** 传送带：拆起来很快，不挑工具。三档只差速度（ADR-0015）。 */
    public static final DeferredBlock<BeltBlock> CONVEYOR_BELT = BLOCKS.registerBlock("conveyor_belt", p -> new BeltBlock(p, BeltTier.MK1),
            UniverseBlocks::logistics);
    public static final DeferredBlock<BeltBlock> CONVEYOR_BELT_MK2 = BLOCKS.registerBlock("conveyor_belt_mk2",
            p -> new BeltBlock(p, BeltTier.MK2), UniverseBlocks::logistics);
    public static final DeferredBlock<BeltBlock> CONVEYOR_BELT_MK3 = BLOCKS.registerBlock("conveyor_belt_mk3",
            p -> new BeltBlock(p, BeltTier.MK3), UniverseBlocks::logistics);
    public static final DeferredBlock<SorterBlock> SORTER = BLOCKS.registerBlock("sorter", p -> new SorterBlock(p, SorterTier.MK1),
            UniverseBlocks::logistics);
    public static final DeferredBlock<SorterBlock> SORTER_MK2 = BLOCKS.registerBlock("sorter_mk2", p -> new SorterBlock(p, SorterTier.MK2),
            UniverseBlocks::logistics);
    public static final DeferredBlock<SorterBlock> SORTER_MK3 = BLOCKS.registerBlock("sorter_mk3", p -> new SorterBlock(p, SorterTier.MK3),
            UniverseBlocks::logistics);
    public static final DeferredBlock<SplitterBlock> SPLITTER = BLOCKS.registerBlock("splitter", SplitterBlock::new, UniverseBlocks::logistics);
    public static final DeferredBlock<StorageDepotBlock> STORAGE_DEPOT = BLOCKS.registerBlock("storage_depot", StorageDepotBlock::new,
            p -> machine(p));
    public static final DeferredBlock<StorageTankBlock> STORAGE_TANK = BLOCKS.registerBlock("storage_tank", StorageTankBlock::new,
            p -> machine(p).noOcclusion());
    // ---- 中期工业（ADR-0015）
    public static final DeferredBlock<MachineBlock> ASSEMBLER_MK2 = BLOCKS.registerBlock("assembler_mk2",
            p -> new MachineBlock(p, UniverseBlockEntities.ASSEMBLER_MK2),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 7 : 0));
    public static final DeferredBlock<MachineBlock> ASSEMBLER_MK3 = BLOCKS.registerBlock("assembler_mk3",
            p -> new MachineBlock(p, UniverseBlockEntities.ASSEMBLER_MK3),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 8 : 0));
    public static final DeferredBlock<ProcessingBlocks.PlaneSmelter> PLANE_SMELTER = BLOCKS.registerBlock("plane_smelter",
            ProcessingBlocks.PlaneSmelter::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 13 : 2));
    public static final DeferredBlock<ProcessingBlocks.ChemicalPlant> CHEMICAL_PLANT = BLOCKS.registerBlock("chemical_plant",
            ProcessingBlocks.ChemicalPlant::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 7 : 0));
    public static final DeferredBlock<ProcessingBlocks.Fractionator> FRACTIONATOR = BLOCKS.registerBlock("fractionator",
            ProcessingBlocks.Fractionator::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 9 : 0));
    public static final DeferredBlock<ResourceMachineBlocks.AdvancedMiningMachine> ADVANCED_MINING_MACHINE = BLOCKS.registerBlock(
            "advanced_mining_machine", ResourceMachineBlocks.AdvancedMiningMachine::new,
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 7 : 0));
    public static final DeferredBlock<FactoryAnchorBlock> FACTORY_ANCHOR = BLOCKS.registerBlock("factory_anchor", FactoryAnchorBlock::new,
            p -> machine(p).lightLevel(state -> 11));
    // ---- 物流站（ADR-0015）：上半截是停机坪，工作时亮灯
    public static final DeferredBlock<StationBlock> PLANETARY_LOGISTICS_STATION = BLOCKS.registerBlock("planetary_logistics_station",
            p -> new StationBlock(p, UniverseBlockEntities.PLANETARY_LOGISTICS_STATION),
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER
                    && state.getValue(MachineBlock.WORKING) ? 10 : 0));
    public static final DeferredBlock<StationBlock> INTERSTELLAR_LOGISTICS_STATION = BLOCKS.registerBlock("interstellar_logistics_station",
            p -> new StationBlock(p, UniverseBlockEntities.INTERSTELLAR_LOGISTICS_STATION),
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER
                    && state.getValue(MachineBlock.WORKING) ? 12 : 0));
    public static final DeferredBlock<OrbitalCollectorBlock> ORBITAL_COLLECTOR = BLOCKS.registerBlock("orbital_collector",
            p -> new OrbitalCollectorBlock(p, UniverseBlockEntities.ORBITAL_COLLECTOR), p -> machine(p).noOcclusion().lightLevel(state -> 9));
    // ---- 戴森球（ADR-0016）
    public static final DeferredBlock<DysonBlocks.Ejector> EM_RAIL_EJECTOR = BLOCKS.registerBlock("em_rail_ejector", DysonBlocks.Ejector::new,
            p -> machine(p).noOcclusion());
    public static final DeferredBlock<DysonBlocks.Silo> VERTICAL_LAUNCHING_SILO = BLOCKS.registerBlock("vertical_launching_silo",
            DysonBlocks.Silo::new, p -> machine(p).noOcclusion());
    public static final DeferredBlock<DysonBlocks.Receiver> RAY_RECEIVER = BLOCKS.registerBlock("ray_receiver", DysonBlocks.Receiver::new,
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 0));
    public static final DeferredBlock<DysonBlocks.Collider> MINIATURE_PARTICLE_COLLIDER = BLOCKS.registerBlock("miniature_particle_collider",
            DysonBlocks.Collider::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 11 : 2));
    /** 终局的发电设备（ADR-0018）。 */
    public static final DeferredBlock<FusionPlantBlock> MINI_FUSION_POWER_PLANT = BLOCKS.registerBlock("mini_fusion_power_plant",
            FusionPlantBlock::new, p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 12 : 2));
    public static final DeferredBlock<ArtificialStarBlock> ARTIFICIAL_STAR = BLOCKS.registerBlock("artificial_star", ArtificialStarBlock::new,
            p -> machine(p).noOcclusion().lightLevel(state -> !state.getValue(MachineBlock.WORKING) ? 0
                    : state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER ? 15 : 9));
    // ---- 工厂的补全（ADR-0022）
    public static final DeferredBlock<SprayCoaterBlock> SPRAY_COATER = BLOCKS.registerBlock("spray_coater", SprayCoaterBlock::new,
            UniverseBlocks::logistics);
    public static final DeferredBlock<PilerBlock> AUTOMATIC_PILER = BLOCKS.registerBlock("automatic_piler", PilerBlock::new,
            UniverseBlocks::logistics);
    public static final DeferredBlock<TrafficMonitorBlock> TRAFFIC_MONITOR = BLOCKS.registerBlock("traffic_monitor", TrafficMonitorBlock::new,
            p -> logistics(p).lightLevel(state -> state.getValue(TrafficMonitorBlock.ALARM) ? 7 : 0));
    public static final DeferredBlock<SorterBlock> PILE_SORTER = BLOCKS.registerBlock("pile_sorter", p -> new SorterBlock(p, SorterTier.PILE),
            UniverseBlocks::logistics);
    public static final DeferredBlock<PowerTowerBlock> WIRELESS_POWER_TOWER = BLOCKS.registerBlock("wireless_power_tower",
            p -> new PowerTowerBlock(p, TeslaTowerBlockEntity.WIRELESS_SPEC, TeslaTowerBlockEntity.WIRELESS_CHARGE_RADIUS), UniverseBlocks::tower);
    public static final DeferredBlock<PowerTowerBlock> SATELLITE_SUBSTATION = BLOCKS.registerBlock("satellite_substation",
            p -> new PowerTowerBlock(p, TeslaTowerBlockEntity.SATELLITE_SPEC, 0), UniverseBlocks::tower);
    public static final DeferredBlock<MachineBlock> GEOTHERMAL_POWER_STATION = BLOCKS.registerBlock("geothermal_power_station",
            p -> new MachineBlock(p, UniverseBlockEntities.GEOTHERMAL_POWER_STATION),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 12 : 3));
    /** 物流配送器（ADR-0023）：装在储物仓顶上。 */
    public static final DeferredBlock<DistributorBlock> LOGISTICS_DISTRIBUTOR = BLOCKS.registerBlock("logistics_distributor",
            DistributorBlock::new, p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 8 : 2));
    public static final DeferredBlock<MachineBlock> ENERGY_EXCHANGER = BLOCKS.registerBlock("energy_exchanger",
            p -> new MachineBlock(p, UniverseBlockEntities.ENERGY_EXCHANGER),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 2));
    public static final DeferredBlock<MachineBlock> NEGENTROPY_SMELTER = BLOCKS.registerBlock("negentropy_smelter",
            p -> new MachineBlock(p, UniverseBlockEntities.NEGENTROPY_SMELTER),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 14 : 3));
    public static final DeferredBlock<MachineBlock> RECOMPOSING_ASSEMBLER = BLOCKS.registerBlock("recomposing_assembler",
            p -> new MachineBlock(p, UniverseBlockEntities.RECOMPOSING_ASSEMBLER),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 3));
    public static final DeferredBlock<MachineBlock> QUANTUM_CHEMICAL_PLANT = BLOCKS.registerBlock("quantum_chemical_plant",
            p -> new MachineBlock(p, UniverseBlockEntities.QUANTUM_CHEMICAL_PLANT),
            p -> machine(p).lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 10 : 3));
    public static final DeferredBlock<MachineBlock> SELF_EVOLUTION_LAB = BLOCKS.registerBlock("self_evolution_lab",
            p -> new MachineBlock(p, UniverseBlockEntities.MATRIX_LAB),
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING) ? 12 : 4));
    /** 地基：铺地面用的建筑方块，比石头硬一点。 */
    public static final DeferredBlock<Block> FOUNDATION = BLOCKS.registerSimpleBlock("foundation",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());
    /** 原油涌泉：挖不掉，也不掉落。 */
    public static final DeferredBlock<CrudeOilSeepBlock> CRUDE_OIL_SEEP = BLOCKS.registerBlock("crude_oil_seep", CrudeOilSeepBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.MUD));

    /** 输电的塔：两格高，上半截工作时亮。 */
    private static BlockBehaviour.Properties tower(BlockBehaviour.Properties properties) {
        return machine(properties).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING)
                && state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER ? 9 : 0);
    }

    /** 空间站的方块：挖不掉、炸不坏、推不动，不掉落。 */
    private static BlockBehaviour.Properties station(BlockBehaviour.Properties properties) {
        return properties.strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noLootTable().pushReaction(PushReaction.IMMOVEABLE)
                .isValidSpawn(Blocks::never);
    }

    /** 传送带、分拣器、分流器：金属，拆起来很快，不挑工具。 */
    private static BlockBehaviour.Properties logistics(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.METAL).strength(1.0F, 3.0F).sound(SoundType.METAL).noOcclusion();
    }

    /** 设备的通用属性：金属、要镐子挖，活塞推不动。 */
    private static BlockBehaviour.Properties machine(BlockBehaviour.Properties properties) {
        return properties.mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                .pushReaction(PushReaction.IMMOVEABLE);
    }

    /** 船体板：16 种颜色，比普通方块轻一半，给飞船换外观用。 */
    public static final Map<DyeColor, DeferredBlock<Block>> HULL_PLATING = registerPlating();
    /** 星球矿石：按星球资源生成，底色跟星岩一样按星球染色。 */
    public static final Map<OreKind, DeferredBlock<DropExperienceBlock>> ORES = registerOres();

    private static Map<OreKind, DeferredBlock<DropExperienceBlock>> registerOres() {
        Map<OreKind, DeferredBlock<DropExperienceBlock>> ores = new EnumMap<>(OreKind.class);
        for (OreKind kind : OreKind.values()) {
            ores.put(kind, BLOCKS.registerBlock(kind.blockName(), p -> new DropExperienceBlock(UniformInt.of(kind.minXp(), kind.maxXp()), p),
                    p -> p.mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                            .strength(kind.toolTier() >= 3 ? 5.0F : 3.0F, 3.0F).lightLevel(state -> kind.light())));
        }
        return ores;
    }

    private static Map<DyeColor, DeferredBlock<Block>> registerPlating() {
        Map<DyeColor, DeferredBlock<Block>> plating = new EnumMap<>(DyeColor.class);
        for (DyeColor color : DyeColor.values()) {
            plating.put(color, BLOCKS.registerSimpleBlock("hull_plating_" + color.getSerializedName(),
                    p -> p.mapColor(color).strength(2.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops()));
        }
        return plating;
    }

    private static Map<PlantForm, DeferredBlock<PlanetPlantBlock>> registerPlants() {
        Map<PlantForm, DeferredBlock<PlanetPlantBlock>> plants = new EnumMap<>(PlantForm.class);
        for (PlantForm form : PlantForm.values()) {
            plants.put(form, BLOCKS.registerBlock(form.blockName(), p -> new PlanetPlantBlock(form, p), p -> plantProperties(p, form)));
        }
        return plants;
    }

    private static Map<ResourcePlant, DeferredBlock<ResourcePlantBlock>> registerResourcePlants() {
        Map<ResourcePlant, DeferredBlock<ResourcePlantBlock>> plants = new EnumMap<>(ResourcePlant.class);
        for (ResourcePlant plant : ResourcePlant.values()) {
            plants.put(plant, BLOCKS.registerBlock(plant.id(), p -> new ResourcePlantBlock(plant, p), p -> resourcePlantProperties(p, plant)));
        }
        return plants;
    }

    private static BlockBehaviour.Properties resourcePlantProperties(BlockBehaviour.Properties p, ResourcePlant plant) {
        p.noCollision().instabreak().offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.POPPED).randomTicks();
        return switch (plant) {
            case SALT_CRYSTAL -> p.mapColor(MapColor.QUARTZ).sound(SoundType.AMETHYST_CLUSTER);
            case GRAVITINO_HOST -> p.mapColor(MapColor.COLOR_PURPLE).sound(SoundType.GRASS).lightLevel(state -> 8);
            case CAVE_MARROW -> p.mapColor(MapColor.TERRACOTTA_WHITE).sound(SoundType.FUNGUS).lightLevel(state -> state.getValue(ResourcePlantBlock.RIPE) ? 6 : 2);
            case FUNGAL_CLUSTER -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).sound(SoundType.FUNGUS);
            case FROSTWORT -> p.mapColor(MapColor.ICE).sound(SoundType.GRASS);
            case SOLAR_VINE -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.GRASS).lightLevel(state -> state.getValue(ResourcePlantBlock.RIPE) ? 5 : 0);
            default -> p.mapColor(MapColor.PLANT).sound(SoundType.GRASS);
        };
    }

    private static BlockBehaviour.Properties plantProperties(BlockBehaviour.Properties p, PlantForm form) {
        p.mapColor(MapColor.PLANT).replaceable().noCollision().instabreak().pushReaction(PushReaction.POPPED)
                .lightLevel(state -> state.getValue(FloraBlocks.LIT) ? 9 : 0);
        switch (form) {
            case CRYSTAL_BLOOM -> p.sound(SoundType.AMETHYST_CLUSTER).offsetType(BlockBehaviour.OffsetType.XZ);
            case FUNGUS -> p.sound(SoundType.FUNGUS).offsetType(BlockBehaviour.OffsetType.XZ).ignitedByLava();
            case LICHEN -> p.sound(SoundType.MOSS_CARPET).ignitedByLava();
            default -> p.sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XYZ).ignitedByLava();
        }
        return p;
    }

    /**
     * 按星球配色染色的方块。
     *
     * @param primary   染色索引 0 用的配色
     * @param secondary 染色索引 1 用的配色（草皮方块的泥土部分）
     */
    public record Tinted(DeferredBlock<? extends Block> block, Palette.Channel primary, Optional<Palette.Channel> secondary) {
        static Tinted of(DeferredBlock<? extends Block> block, Palette.Channel channel) {
            return new Tinted(block, channel, Optional.empty());
        }
    }

    public static final List<Tinted> TINTED = List.of(
            Tinted.of(PLANET_ROCK, Palette.Channel.ROCK),
            Tinted.of(PLANET_COBBLESTONE, Palette.Channel.ROCK),
            Tinted.of(PLANET_SOIL, Palette.Channel.SOIL),
            new Tinted(PLANET_TURF, Palette.Channel.GRASS, Optional.of(Palette.Channel.SOIL)),
            Tinted.of(PLANET_REGOLITH, Palette.Channel.SURFACE),
            Tinted.of(PLANET_SAND, Palette.Channel.SAND),
            Tinted.of(PLANET_SNOW, Palette.Channel.SNOW),
            Tinted.of(PLANET_ICE, Palette.Channel.ICE),
            Tinted.of(PLANET_ASH, Palette.Channel.ASH),
            Tinted.of(PLANET_CRYSTAL, Palette.Channel.ACCENT),
            // 方块上的颜色按高度分层（见 PlanetTints），物品用岩石色
            Tinted.of(PLANET_STRATA, Palette.Channel.ROCK));

    private UniverseBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
