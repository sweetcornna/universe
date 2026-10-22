package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import io.github.sweetcornna.universe.darkfog.Facilities;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.world.planet.Fulgurites;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

/** 方块标签：挖掘工具、阻挡移动等。 */
final class UniverseBlockTagsProvider extends BlockTagsProvider {
    UniverseBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, Universe.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                UniverseBlocks.PLANET_ROCK.key(),
                UniverseBlocks.PLANET_COBBLESTONE.key(),
                UniverseBlocks.PLANET_ICE.key(),
                UniverseBlocks.PLANET_CRYSTAL.key(),
                UniverseBlocks.PLANET_STRATA.key());
        this.tag(BlockTags.MINEABLE_WITH_SHOVEL).add(
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key(),
                UniverseBlocks.PLANET_REGOLITH.key(),
                UniverseBlocks.PLANET_SAND.key(),
                UniverseBlocks.PLANET_SNOW.key(),
                UniverseBlocks.PLANET_ASH.key());
        // 草皮、星壤能种树、长植物
        this.tag(BlockTags.DIRT).add(
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key());
        this.tag(BlockTags.SAND).add(UniverseBlocks.PLANET_SAND.key());
        this.tag(BlockTags.ICE).add(UniverseBlocks.PLANET_ICE.key());
        this.tag(BlockTags.BASE_STONE_OVERWORLD).add(UniverseBlocks.PLANET_ROCK.key());
        // 原版矿石地物认这个标签，星岩里才能长矿
        this.tag(BlockTags.STONE_ORE_REPLACEABLES).add(UniverseBlocks.PLANET_ROCK.key(), UniverseBlocks.PLANET_STRATA.key());
        // 26.x 起"阻挡移动"改由标签决定：高度图（生物刷新、落点）、窒息、传送落点、挡住流体都看它。
        // 实心方块不加进来，高度图会直接穿过整片地形
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.PLANET_ROCK.key(),
                UniverseBlocks.PLANET_COBBLESTONE.key(),
                UniverseBlocks.PLANET_SOIL.key(),
                UniverseBlocks.PLANET_TURF.key(),
                UniverseBlocks.PLANET_REGOLITH.key(),
                UniverseBlocks.PLANET_SAND.key(),
                UniverseBlocks.PLANET_SNOW.key(),
                UniverseBlocks.PLANET_ICE.key(),
                UniverseBlocks.PLANET_ASH.key(),
                UniverseBlocks.PLANET_STRATA.key(),
                UniverseBlocks.PLANET_CRYSTAL.key(),
                UniverseBlocks.PLANET_LOG.key(),
                UniverseBlocks.PLANET_CAP.key(),
                UniverseBlocks.PLANET_BLOOM.key());
        // 树叶进 leaves 标签，原版的 blocks_motion 会把它算进去（高度图分有叶子、没叶子两种）
        this.tag(BlockTags.LEAVES).add(UniverseBlocks.PLANET_LEAVES.key());
        this.tag(BlockTags.LOGS).add(UniverseBlocks.PLANET_LOG.key());
        this.tag(BlockTags.MINEABLE_WITH_AXE).add(UniverseBlocks.PLANET_LOG.key(), UniverseBlocks.PLANET_CAP.key());
        this.tag(BlockTags.MINEABLE_WITH_HOE).add(UniverseBlocks.PLANET_LEAVES.key(), UniverseBlocks.PLANET_BLOOM.key());
        // 资源植物（ADR-0026）：剑砍得快；不算可替换，免得放方块时把它顶掉
        UniverseBlocks.RESOURCE_PLANTS.values().forEach(block -> this.tag(BlockTags.SWORD_EFFICIENT).add(block.key()));
        for (var block : UniverseBlocks.PLANTS.values()) {
            this.tag(BlockTags.REPLACEABLE).add(block.key());
            this.tag(BlockTags.SWORD_EFFICIENT).add(block.key());
            this.tag(BlockTags.REPLACEABLE_BY_TREES).add(block.key());
        }
        // 飞船部件
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                UniverseBlocks.SHIP_HELM.key(),
                UniverseBlocks.DAMAGED_HELM.key(),
                UniverseBlocks.SHIP_THRUSTER.key(),
                UniverseBlocks.FUEL_TANK.key(),
                UniverseBlocks.IMPULSE_ENGINE.key(),
                UniverseBlocks.WARP_DRIVE.key());
        UniverseBlocks.HULL_PLATING.values().forEach(block -> this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key()));
        for (var block : List.of(UniverseBlocks.PHOTON_CANNON, UniverseBlocks.MINING_BEAM, UniverseBlocks.SHIELD_GENERATOR,
                UniverseBlocks.SHIP_THRUSTER_MK2, UniverseBlocks.FUEL_TANK_MK2, UniverseBlocks.IMPULSE_ENGINE_MK2, UniverseBlocks.WARP_DRIVE_MK2,
                UniverseBlocks.ALLOY_HULL_PLATING)) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(block.key());
        }
        // 飞船方块也是实心的：不进"阻挡移动"的话水会穿过船体板，高度图也会漏过去
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.SHIP_HELM.key(),
                UniverseBlocks.SHIP_THRUSTER.key(),
                UniverseBlocks.FUEL_TANK.key(),
                UniverseBlocks.IMPULSE_ENGINE.key(),
                UniverseBlocks.WARP_DRIVE.key(),
                UniverseBlocks.SHIP_SEAT.key());
        UniverseBlocks.HULL_PLATING.values().forEach(block -> this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(block.key()));
        // 工业设备：镐子挖；不会被组装进飞船（飞船停在工厂旁边时不会把设备带走）
        for (var block : List.of(UniverseBlocks.TESLA_TOWER, UniverseBlocks.WIND_TURBINE, UniverseBlocks.THERMAL_PLANT,
                UniverseBlocks.ACCUMULATOR, UniverseBlocks.ARC_SMELTER, UniverseBlocks.ASSEMBLER, UniverseBlocks.OIL_REFINERY,
                UniverseBlocks.MINING_MACHINE, UniverseBlocks.WATER_PUMP, UniverseBlocks.OIL_EXTRACTOR, UniverseBlocks.CONVEYOR_BELT,
                UniverseBlocks.SORTER, UniverseBlocks.STORAGE_DEPOT, UniverseBlocks.MATRIX_LAB, UniverseBlocks.CONVEYOR_BELT_MK2,
                UniverseBlocks.CONVEYOR_BELT_MK3, UniverseBlocks.SORTER_MK2, UniverseBlocks.SORTER_MK3, UniverseBlocks.SPLITTER,
                UniverseBlocks.STORAGE_TANK, UniverseBlocks.ASSEMBLER_MK2, UniverseBlocks.ASSEMBLER_MK3, UniverseBlocks.PLANE_SMELTER,
                UniverseBlocks.CHEMICAL_PLANT, UniverseBlocks.FRACTIONATOR, UniverseBlocks.ADVANCED_MINING_MACHINE,
                UniverseBlocks.FACTORY_ANCHOR, UniverseBlocks.PLANETARY_LOGISTICS_STATION, UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION,
                UniverseBlocks.ORBITAL_COLLECTOR, UniverseBlocks.SOLAR_PANEL, UniverseBlocks.EM_RAIL_EJECTOR,
                UniverseBlocks.VERTICAL_LAUNCHING_SILO, UniverseBlocks.RAY_RECEIVER, UniverseBlocks.MINIATURE_PARTICLE_COLLIDER,
                UniverseBlocks.MINI_FUSION_POWER_PLANT, UniverseBlocks.ARTIFICIAL_STAR,
                UniverseBlocks.TELEPORTER, UniverseBlocks.BASE_BEACON, UniverseBlocks.SPRAY_COATER, UniverseBlocks.AUTOMATIC_PILER,
                UniverseBlocks.HYDROPONIC_TRAY, UniverseBlocks.NUTRIENT_PROCESSOR, UniverseBlocks.PORTABLE_REFINER,
                UniverseBlocks.TRAFFIC_MONITOR, UniverseBlocks.PILE_SORTER, UniverseBlocks.WIRELESS_POWER_TOWER, UniverseBlocks.SATELLITE_SUBSTATION,
                UniverseBlocks.GEOTHERMAL_POWER_STATION, UniverseBlocks.ENERGY_EXCHANGER, UniverseBlocks.NEGENTROPY_SMELTER,
                UniverseBlocks.RECOMPOSING_ASSEMBLER, UniverseBlocks.QUANTUM_CHEMICAL_PLANT, UniverseBlocks.SELF_EVOLUTION_LAB,
                UniverseBlocks.LOGISTICS_DISTRIBUTOR, UniverseBlocks.LANDING_PAD, UniverseBlocks.VEHICLE_SUMMONER, UniverseBlocks.EGG_SEQUENCER)) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
        }
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.FOUNDATION.key());
        // 地上孵着的卵不跟着飞船走（ADR-0026 §九）
        this.tag(ShipParts.EXCLUDED).add(UniverseBlocks.CREATURE_EGG.key());
        // 原油涌泉不会被组装进飞船
        this.tag(ShipParts.EXCLUDED).add(UniverseBlocks.CRUDE_OIL_SEEP.key());
        // 兴趣点的方块（ADR-0014）：都是实心的；能挖的用镐子；固定在原地的不会被组装进飞船
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.DROP_POD.key(),
                UniverseBlocks.DATA_TERMINAL.key(),
                UniverseBlocks.MONOLITH.key(),
                UniverseBlocks.TECH_CACHE.key(),
                UniverseBlocks.SIGNAL_BOOSTER.key(),
                UniverseBlocks.SUPPLY_CRATE.key(),
                UniverseBlocks.SMOLDERING_WRECK.key());
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                UniverseBlocks.DATA_TERMINAL.key(),
                UniverseBlocks.TECH_CACHE.key(),
                UniverseBlocks.SIGNAL_BOOSTER.key(),
                UniverseBlocks.SUPPLY_CRATE.key(),
                UniverseBlocks.SMOLDERING_WRECK.key());
        this.tag(ShipParts.EXCLUDED).add(
                UniverseBlocks.DROP_POD.key(),
                UniverseBlocks.MONOLITH.key(),
                UniverseBlocks.DATA_TERMINAL.key(),
                UniverseBlocks.TECH_CACHE.key(),
                UniverseBlocks.SUPPLY_CRATE.key());
        // 水下（ADR-0026）：海底密封箱和补给箱一样；甲壳蚌用镐子撬
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(UniverseBlocks.SEALED_CRATE.key());
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.SEALED_CRATE.key(), UniverseBlocks.CLAM.key());
        this.tag(ShipParts.EXCLUDED).add(UniverseBlocks.SEALED_CRATE.key(), UniverseBlocks.CLAM.key());
        // 交易站、制造设施、观测站（ADR-0026）：打不坏的方块都是实心的，不会被组装进飞船
        for (var block : List.of(UniverseBlocks.OUTPOST_TRADE_TERMINAL, UniverseBlocks.REINFORCED_DOOR, UniverseBlocks.REINFORCED_PLATING,
                UniverseBlocks.FACILITY_TERMINAL, UniverseBlocks.OBSERVATORY_TERMINAL)) {
            this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
        }
        // 知识石（ADR-0027 §五）：实心，挖不掉，不会被组装进飞船
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(UniverseBlocks.KNOWLEDGE_STONE.key());
        this.tag(ShipParts.EXCLUDED).add(UniverseBlocks.KNOWLEDGE_STONE.key());
        // 野外的地标（ADR-0026）：化石土用铲子，打捞舱和雷击石用镐子；打捞舱固定在原地
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.FOSSIL_DIRT.key(), UniverseBlocks.SALVAGE_CAPSULE.key(), UniverseBlocks.FULGURITE.key());
        this.tag(BlockTags.MINEABLE_WITH_SHOVEL).add(UniverseBlocks.FOSSIL_DIRT.key());
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.SALVAGE_CAPSULE.key(), UniverseBlocks.FULGURITE.key());
        this.tag(ShipParts.EXCLUDED).add(UniverseBlocks.SALVAGE_CAPSULE.key());
        // 雷打在这些天然的沙、土、石头上会熔成雷击石
        this.tag(Fulgurites.HOSTS).addTag(BlockTags.SAND).addTag(BlockTags.DIRT).addTag(BlockTags.BASE_STONE_OVERWORLD).add(
                UniverseBlocks.PLANET_REGOLITH.key(),
                UniverseBlocks.PLANET_ASH.key(),
                UniverseBlocks.PLANET_STRATA.key(),
                UniverseBlocks.PLANET_COBBLESTONE.key(),
                Blocks.GRAVEL.builtInRegistryHolder().key(),
                Blocks.RED_SANDSTONE.builtInRegistryHolder().key(),
                Blocks.SANDSTONE.builtInRegistryHolder().key());
        // 先驱者传送门（ADR-0024）：石头都是实心的，整座门不会被组装进飞船
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.PORTAL_STONE.key(),
                UniverseBlocks.PORTAL_GLYPH.key(),
                UniverseBlocks.PORTAL_DIAL.key());
        this.tag(ShipParts.EXCLUDED).add(
                UniverseBlocks.PORTAL_STONE.key(),
                UniverseBlocks.PORTAL_GLYPH.key(),
                UniverseBlocks.PORTAL_DIAL.key(),
                UniverseBlocks.PORTAL_FIELD.key());
        // 守卫塔（ADR-0026）：实心，不会被组装进飞船
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(
                UniverseBlocks.SENTINEL_PILLAR_PLATING.key(),
                UniverseBlocks.SENTINEL_PILLAR_LIGHT.key(),
                UniverseBlocks.SENTINEL_PILLAR_TERMINAL.key());
        this.tag(ShipParts.EXCLUDED).add(
                UniverseBlocks.SENTINEL_PILLAR_PLATING.key(),
                UniverseBlocks.SENTINEL_PILLAR_LIGHT.key(),
                UniverseBlocks.SENTINEL_PILLAR_TERMINAL.key());
        // 废弃货船里漏电的线管（ADR-0024）
        this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(UniverseBlocks.LIVE_CONDUIT.key());
        this.tag(ShipParts.EXCLUDED).add(UniverseBlocks.LIVE_CONDUIT.key());
        // 星球矿石：进原版的 ores（它已经在"阻挡移动"里）和通用的 c:ores，按矿种要求镐的等级
        UniverseBlocks.ORES.forEach((kind, block) -> {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(BlockTags.ORES).add(block.key());
            this.tag(Tags.BlockItems.ORES.block()).add(block.key());
            switch (kind.toolTier()) {
                case 1 -> this.tag(BlockTags.NEEDS_STONE_TOOL).add(block.key());
                case 2 -> this.tag(BlockTags.NEEDS_IRON_TOOL).add(block.key());
                case 3 -> this.tag(BlockTags.NEEDS_DIAMOND_TOOL).add(block.key());
                default -> {
                }
            }
        });
        this.tag(BlockTags.IRON_ORES).add(UniverseBlocks.ORES.get(OreKind.IRON).key());
        this.tag(BlockTags.COPPER_ORES).add(UniverseBlocks.ORES.get(OreKind.COPPER).key());
        this.tag(BlockItemTags.COAL_ORES.block()).add(UniverseBlocks.ORES.get(OreKind.COAL).key());
        // 天然地形不会被组装进飞船：船停在地上时不会把地面、矿、树叶、草一起带走
        this.tag(ShipParts.EXCLUDED)
                // 26.x 的 dirt 标签不含草方块了，用 substrate_overworld（泥土、草方块、苔藓、泥巴）
                .addTag(BlockTags.SUBSTRATE_OVERWORLD)
                .addTag(BlockTags.SAND)
                .addTag(BlockTags.BASE_STONE_OVERWORLD)
                .addTag(BlockTags.BASE_STONE_NETHER)
                .addTag(BlockTags.LEAVES)
                .addTag(BlockTags.ICE)
                .addTag(BlockTags.SNOW)
                .addTag(BlockTags.REPLACEABLE)
                .addTag(BlockTags.NYLIUM)
                .addTag(BlockTags.BADLANDS_TERRACOTTA)
                .addTag(Tags.BlockItems.ORES.block())
                .addTag(Tags.BlockItems.GRAVELS.block())
                .add(Blocks.BEDROCK.builtInRegistryHolder().key(),
                        Blocks.CLAY.builtInRegistryHolder().key(),
                        Blocks.MUD.builtInRegistryHolder().key(),
                        Blocks.MAGMA_BLOCK.builtInRegistryHolder().key(),
                        Blocks.END_STONE.builtInRegistryHolder().key(),
                        UniverseBlocks.PLANET_REGOLITH.key(),
                        UniverseBlocks.PLANET_SNOW.key(),
                        UniverseBlocks.PLANET_ASH.key(),
                        UniverseBlocks.PLANET_STRATA.key(),
                        UniverseBlocks.PLANET_CRYSTAL.key(),
                        UniverseBlocks.PLANET_CAP.key(),
                        UniverseBlocks.PLANET_BLOOM.key());
        // 星球植物能长在土、沙、雪、风化层、灰烬上，地衣和晶花还能长在岩石上
        this.tag(FloraBlocks.PLANT_SOIL)
                .addTag(BlockTags.DIRT)
                .addTag(BlockTags.SAND)
                .addTag(BlockTags.SUPPORTS_VEGETATION)
                .add(UniverseBlocks.PLANET_REGOLITH.key(),
                        UniverseBlocks.PLANET_SNOW.key(),
                        UniverseBlocks.PLANET_ASH.key(),
                        UniverseBlocks.PLANET_ROCK.key(),
                        UniverseBlocks.PLANET_STRATA.key(),
                        UniverseBlocks.PLANET_CRYSTAL.key());
        this.darkFogBases();
        this.darkFogFacilities();
        this.darkFogTurrets();
        this.darkFogSupport();
        this.darkFogPlasma();
        this.darkFogShield();
    }

    /** 黑雾基地的方块（子代理 P）。 */
    private void darkFogBases() {
        // 底板和导轨用镐子挖（很硬，挖了基地会补）；地核挖不掉。都是实心的，不会被组装进飞船
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.DARK_FOG_PLATING.key(), UniverseBlocks.DARK_FOG_CONDUIT.key());
        this.tag(BlockTags.NEEDS_DIAMOND_TOOL).add(UniverseBlocks.DARK_FOG_PLATING.key(), UniverseBlocks.DARK_FOG_CONDUIT.key());
        for (var block : List.of(UniverseBlocks.DARK_FOG_PLATING, UniverseBlocks.DARK_FOG_CONDUIT, UniverseBlocks.DARK_FOG_CORE_REMNANT)) {
            this.tag(BlockTags.BLOCKS_MOTION_NO_LEAVES).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
        }
    }

    /** 设施标签、残影、战场分析基地（子代理 Q）。 */
    private void darkFogFacilities() {
        // 黑雾会打的设施（ADR-0028 §四）：模组的工厂方块
        this.tag(Facilities.TAG).add(
                UniverseBlocks.TESLA_TOWER.key(),
                UniverseBlocks.WIND_TURBINE.key(),
                UniverseBlocks.SOLAR_PANEL.key(),
                UniverseBlocks.THERMAL_PLANT.key(),
                UniverseBlocks.ACCUMULATOR.key(),
                UniverseBlocks.WIRELESS_POWER_TOWER.key(),
                UniverseBlocks.SATELLITE_SUBSTATION.key(),
                UniverseBlocks.GEOTHERMAL_POWER_STATION.key(),
                UniverseBlocks.ENERGY_EXCHANGER.key(),
                UniverseBlocks.MINI_FUSION_POWER_PLANT.key(),
                UniverseBlocks.ARTIFICIAL_STAR.key(),
                UniverseBlocks.RAY_RECEIVER.key(),
                UniverseBlocks.MINING_MACHINE.key(),
                UniverseBlocks.ADVANCED_MINING_MACHINE.key(),
                UniverseBlocks.WATER_PUMP.key(),
                UniverseBlocks.OIL_EXTRACTOR.key(),
                UniverseBlocks.ORBITAL_COLLECTOR.key(),
                UniverseBlocks.ARC_SMELTER.key(),
                UniverseBlocks.PLANE_SMELTER.key(),
                UniverseBlocks.NEGENTROPY_SMELTER.key(),
                UniverseBlocks.ASSEMBLER.key(),
                UniverseBlocks.ASSEMBLER_MK2.key(),
                UniverseBlocks.ASSEMBLER_MK3.key(),
                UniverseBlocks.RECOMPOSING_ASSEMBLER.key(),
                UniverseBlocks.OIL_REFINERY.key(),
                UniverseBlocks.CHEMICAL_PLANT.key(),
                UniverseBlocks.QUANTUM_CHEMICAL_PLANT.key(),
                UniverseBlocks.FRACTIONATOR.key(),
                UniverseBlocks.MINIATURE_PARTICLE_COLLIDER.key(),
                UniverseBlocks.MATRIX_LAB.key(),
                UniverseBlocks.SELF_EVOLUTION_LAB.key(),
                UniverseBlocks.CONVEYOR_BELT.key(),
                UniverseBlocks.CONVEYOR_BELT_MK2.key(),
                UniverseBlocks.CONVEYOR_BELT_MK3.key(),
                UniverseBlocks.SORTER.key(),
                UniverseBlocks.SORTER_MK2.key(),
                UniverseBlocks.SORTER_MK3.key(),
                UniverseBlocks.PILE_SORTER.key(),
                UniverseBlocks.SPLITTER.key(),
                UniverseBlocks.STORAGE_DEPOT.key(),
                UniverseBlocks.STORAGE_TANK.key(),
                UniverseBlocks.SPRAY_COATER.key(),
                UniverseBlocks.AUTOMATIC_PILER.key(),
                UniverseBlocks.TRAFFIC_MONITOR.key(),
                UniverseBlocks.PLANETARY_LOGISTICS_STATION.key(),
                UniverseBlocks.INTERSTELLAR_LOGISTICS_STATION.key(),
                UniverseBlocks.LOGISTICS_DISTRIBUTOR.key(),
                UniverseBlocks.FACTORY_ANCHOR.key(),
                UniverseBlocks.EM_RAIL_EJECTOR.key(),
                UniverseBlocks.VERTICAL_LAUNCHING_SILO.key(),
                UniverseBlocks.HYDROPONIC_TRAY.key(),
                UniverseBlocks.NUTRIENT_PROCESSOR.key(),
                UniverseBlocks.PORTABLE_REFINER.key(),
                UniverseBlocks.EGG_SEQUENCER.key(),
                UniverseBlocks.BATTLEFIELD_ANALYSIS_BASE.key());
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(UniverseBlocks.BATTLEFIELD_ANALYSIS_BASE.key());
    }

    /** 炮塔（子代理 R1）。 */
    private void darkFogTurrets() {
        // 炮塔：镐子挖，不跟着飞船走，黑雾会打
        for (var block : List.of(UniverseBlocks.GAUSS_TURRET, UniverseBlocks.LASER_TURRET, UniverseBlocks.IMPLOSION_CANNON,
                UniverseBlocks.MISSILE_TURRET)) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
            this.tag(io.github.sweetcornna.universe.darkfog.Facilities.TAG).add(block.key());
        }
    }

    /** 等离子炮塔（ADR-0028 §八，子代理 T）。 */
    private void darkFogPlasma() {
        // 和别的炮塔一样：镐子挖，不跟着飞船走，黑雾会打
        for (var block : List.of(UniverseBlocks.PLASMA_TURRET, UniverseBlocks.SR_PLASMA_TURRET)) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
            this.tag(io.github.sweetcornna.universe.darkfog.Facilities.TAG).add(block.key());
        }
    }

    /** 行星护盾发生器（子代理 S）。 */
    private void darkFogShield() {
        // 镐子挖，不跟着飞船走，地面上的黑雾打得坏
        var block = UniverseBlocks.PLANETARY_SHIELD_GENERATOR;
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
        this.tag(ShipParts.EXCLUDED).add(block.key());
        this.tag(io.github.sweetcornna.universe.darkfog.Facilities.TAG).add(block.key());
    }

    /** 信号塔、干扰塔（子代理 R2）。 */
    private void darkFogSupport() {
        // 镐子挖，不跟着飞船走，黑雾会打
        for (var block : List.of(UniverseBlocks.SIGNAL_TOWER, UniverseBlocks.JAMMER_TOWER)) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
            this.tag(io.github.sweetcornna.universe.darkfog.Facilities.TAG).add(block.key());
        }
    }
}
