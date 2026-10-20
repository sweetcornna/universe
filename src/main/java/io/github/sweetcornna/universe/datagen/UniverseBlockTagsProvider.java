package io.github.sweetcornna.universe.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.block.FloraBlocks;
import io.github.sweetcornna.universe.block.OreKind;
import io.github.sweetcornna.universe.entity.ship.ShipParts;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
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
        for (var block : UniverseBlocks.PLANTS.values()) {
            this.tag(BlockTags.REPLACEABLE).add(block.key());
            this.tag(BlockTags.SWORD_EFFICIENT).add(block.key());
            this.tag(BlockTags.REPLACEABLE_BY_TREES).add(block.key());
        }
        // 飞船部件
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                UniverseBlocks.SHIP_HELM.key(),
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
                UniverseBlocks.TELEPORTER, UniverseBlocks.BASE_BEACON)) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(block.key());
            this.tag(ShipParts.EXCLUDED).add(block.key());
        }
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
    }
}
