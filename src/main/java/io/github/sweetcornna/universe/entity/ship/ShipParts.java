package io.github.sweetcornna.universe.entity.ship;

import java.util.HashMap;
import java.util.Map;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.core.ship.ShipPart;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.BlockRelocability;

/** 方块在飞船上的角色，以及哪些方块能装上船。 */
public final class ShipParts {
    /** 不会被组装进飞船的方块：天然地形、植物等。船停在地上时不会把地面一起带走。 */
    public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK, Universe.id("ship_excluded"));

    /** 功能部件和船体板（第一次用到时建表；客户端和服务端各建一次也没关系，结果一样）。 */
    private static volatile Map<Block, ShipPart> parts;

    private ShipParts() {
    }

    public static ShipPart of(BlockState state) {
        Map<Block, ShipPart> table = parts;
        if (table == null) {
            table = buildParts();
            parts = table;
        }
        ShipPart part = table.get(state.getBlock());
        if (part != null) {
            return part;
        }
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Tags.BlockItems.GLASS_BLOCKS.block())
                || state.is(Tags.BlockItems.GLASS_PANES.block()) || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            return ShipPart.LIGHT;
        }
        return ShipPart.HULL;
    }

    private static Map<Block, ShipPart> buildParts() {
        Map<Block, ShipPart> map = new HashMap<>();
        map.put(UniverseBlocks.SHIP_HELM.get(), ShipPart.HELM);
        map.put(UniverseBlocks.SHIP_THRUSTER.get(), ShipPart.THRUSTER);
        map.put(UniverseBlocks.FUEL_TANK.get(), ShipPart.FUEL_TANK);
        map.put(UniverseBlocks.IMPULSE_ENGINE.get(), ShipPart.IMPULSE_ENGINE);
        map.put(UniverseBlocks.SHIP_SEAT.get(), ShipPart.SEAT);
        map.put(UniverseBlocks.WARP_DRIVE.get(), ShipPart.WARP_DRIVE);
        map.put(UniverseBlocks.PHOTON_CANNON.get(), ShipPart.PHOTON_CANNON);
        map.put(UniverseBlocks.MINING_BEAM.get(), ShipPart.MINING_BEAM);
        map.put(UniverseBlocks.SHIELD_GENERATOR.get(), ShipPart.SHIELD_GENERATOR);
        map.put(UniverseBlocks.SHIP_THRUSTER_MK2.get(), ShipPart.THRUSTER_MK2);
        map.put(UniverseBlocks.FUEL_TANK_MK2.get(), ShipPart.FUEL_TANK_MK2);
        map.put(UniverseBlocks.IMPULSE_ENGINE_MK2.get(), ShipPart.IMPULSE_ENGINE_MK2);
        map.put(UniverseBlocks.WARP_DRIVE_MK2.get(), ShipPart.WARP_DRIVE_MK2);
        map.put(UniverseBlocks.ALLOY_HULL_PLATING.get(), ShipPart.ALLOY_PLATING);
        UniverseBlocks.HULL_PLATING.values().forEach(block -> map.put(block.get(), ShipPart.PLATING));
        return Map.copyOf(map);
    }

    /** 这一格能不能装上船：不是空气、液体、天然地形、不可破坏的方块，也没有拒绝被搬动。 */
    public static boolean canAssemble(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(EXCLUDED)) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return !(state.getRelocability(level, pos) instanceof BlockRelocability.No);
    }
}
