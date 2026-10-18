package io.github.sweetcornna.universe.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.FloraVariantBlock;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.block.PlanetBloomBlock;
import io.github.sweetcornna.universe.block.PlanetLeavesBlock;
import io.github.sweetcornna.universe.block.PlanetLogBlock;
import io.github.sweetcornna.universe.block.PlanetPlantBlock;
import io.github.sweetcornna.universe.block.ship.FuelTankBlock;
import io.github.sweetcornna.universe.block.ship.ImpulseEngineBlock;
import io.github.sweetcornna.universe.block.ship.ShipHelmBlock;
import io.github.sweetcornna.universe.block.ship.ShipSeatBlock;
import io.github.sweetcornna.universe.block.ship.ShipThrusterBlock;
import io.github.sweetcornna.universe.core.ecology.PlantForm;
import io.github.sweetcornna.universe.core.galaxy.Palette;
import io.github.sweetcornna.universe.factory.ArcSmelterBlock;
import io.github.sweetcornna.universe.factory.MachineBlock;
import io.github.sweetcornna.universe.factory.TallMachineBlock;
import io.github.sweetcornna.universe.factory.resource.CrudeOilSeepBlock;
import io.github.sweetcornna.universe.factory.resource.ResourceMachineBlocks;
import io.github.sweetcornna.universe.factory.power.AccumulatorBlock;
import io.github.sweetcornna.universe.factory.power.ThermalPlantBlock;
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
    public static final DeferredBlock<ShipThrusterBlock> SHIP_THRUSTER = BLOCKS.registerBlock("ship_thruster", ShipThrusterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<FuelTankBlock> FUEL_TANK = BLOCKS.registerBlock("fuel_tank", FuelTankBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops());
    public static final DeferredBlock<ImpulseEngineBlock> IMPULSE_ENGINE = BLOCKS.registerBlock("impulse_engine", ImpulseEngineBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
                    .lightLevel(state -> 7));
    public static final DeferredBlock<ShipSeatBlock> SHIP_SEAT = BLOCKS.registerBlock("ship_seat", ShipSeatBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5F).sound(SoundType.WOOL).noOcclusion());
    // ---- 工业（ADR-0011）
    public static final DeferredBlock<TallMachineBlock> TESLA_TOWER = BLOCKS.registerBlock("tesla_tower",
            p -> new TallMachineBlock(p, UniverseBlockEntities.TESLA_TOWER),
            p -> machine(p).noOcclusion().lightLevel(state -> state.getValue(MachineBlock.WORKING)
                    && state.getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER ? 9 : 0));
    public static final DeferredBlock<TallMachineBlock> WIND_TURBINE = BLOCKS.registerBlock("wind_turbine",
            p -> new TallMachineBlock(p, UniverseBlockEntities.WIND_TURBINE), p -> machine(p).noOcclusion());
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
    /** 原油涌泉：挖不掉，也不掉落。 */
    public static final DeferredBlock<CrudeOilSeepBlock> CRUDE_OIL_SEEP = BLOCKS.registerBlock("crude_oil_seep", CrudeOilSeepBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.MUD));

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
