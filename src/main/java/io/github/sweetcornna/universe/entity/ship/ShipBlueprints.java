package io.github.sweetcornna.universe.entity.ship;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.ship.ShipDesign;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Face;
import io.github.sweetcornna.universe.core.ship.ShipDesigner;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.trade.ItemValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 把飞船设计器（{@link ShipDesigner}）的设计换成方块（ADR-0024）：空间站卖的船、星球上坠毁的船都这么放。
 * 设计里船头朝北（-z），放的时候按要朝的方向转。
 */
public final class ShipBlueprints {
    private ShipBlueprints() {
    }

    /** 一格在设计的朝向下（船头朝北）是什么方块。 */
    public static BlockState state(ShipDesign design, Cell cell) {
        return switch (cell.role()) {
            case HULL -> plating(design.primary());
            case TRIM -> plating(design.accent());
            case ALLOY -> UniverseBlocks.ALLOY_HULL_PLATING.get().defaultBlockState();
            case GLASS -> Blocks.STAINED_GLASS.lightBlue().defaultBlockState();
            case LAMP -> Blocks.SEA_LANTERN.defaultBlockState();
            case HELM -> horizontal(UniverseBlocks.SHIP_HELM.get(), cell.face());
            case THRUSTER -> directional(UniverseBlocks.SHIP_THRUSTER.get(), cell.face());
            case THRUSTER_MK2 -> directional(UniverseBlocks.SHIP_THRUSTER_MK2.get(), cell.face());
            case FUEL_TANK -> UniverseBlocks.FUEL_TANK.get().defaultBlockState();
            case FUEL_TANK_MK2 -> UniverseBlocks.FUEL_TANK_MK2.get().defaultBlockState();
            case IMPULSE -> horizontal(UniverseBlocks.IMPULSE_ENGINE.get(), cell.face());
            case IMPULSE_MK2 -> horizontal(UniverseBlocks.IMPULSE_ENGINE_MK2.get(), cell.face());
            case WARP -> horizontal(UniverseBlocks.WARP_DRIVE.get(), cell.face());
            case WARP_MK2 -> horizontal(UniverseBlocks.WARP_DRIVE_MK2.get(), cell.face());
            case CANNON -> horizontal(UniverseBlocks.PHOTON_CANNON.get(), cell.face());
            case SHIELD -> UniverseBlocks.SHIELD_GENERATOR.get().defaultBlockState();
            case CARGO -> Blocks.CHEST.defaultBlockState();
        };
    }

    private static BlockState plating(int dye) {
        return UniverseBlocks.HULL_PLATING.get(DyeColor.byId(dye)).get().defaultBlockState();
    }

    private static BlockState horizontal(Block block, Face face) {
        Direction direction = direction(face);
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                direction.getAxis().isHorizontal() ? direction : Direction.NORTH);
    }

    private static BlockState directional(Block block, Face face) {
        return block.defaultBlockState().setValue(DirectionalBlock.FACING, direction(face));
    }

    public static Direction direction(Face face) {
        return switch (face) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    /** 船头从北转到 {@code forward} 要转多少。 */
    public static Rotation rotation(Direction forward) {
        return switch (forward) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * 每一格放在世界里的哪、是什么方块：船头朝 {@code forward}，包围盒底面的中心落在 {@code bottomCenter}。
     * 顺序和设计里的格子一样。
     */
    public static Map<BlockPos, BlockState> layout(ShipDesign design, BlockPos bottomCenter, Direction forward) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : design.cells()) {
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
        }
        int cx = Math.floorDiv(minX + maxX, 2);
        int cz = Math.floorDiv(minZ + maxZ, 2);
        int minY = design.minY();
        Rotation rotation = rotation(forward);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Cell cell : design.cells()) {
            BlockPos local = new BlockPos(cell.x() - cx, cell.y() - minY, cell.z() - cz).rotate(rotation);
            out.put(bottomCenter.offset(local), state(design, cell).rotate(rotation));
        }
        return out;
    }

    /** 这些方块按交易价值多少信用点；算不出价钱的方块不算。 */
    public static double value(MinecraftServer server, Iterable<BlockState> states) {
        double total = 0;
        for (BlockState state : states) {
            total += ItemValues.value(server, state.getBlock().asItem()).orElse(0);
        }
        return total;
    }

    public static double value(MinecraftServer server, ShipDesign design) {
        double total = 0;
        for (Cell cell : design.cells()) {
            total += ItemValues.value(server, state(design, cell).getBlock().asItem()).orElse(0);
        }
        return total;
    }
}
