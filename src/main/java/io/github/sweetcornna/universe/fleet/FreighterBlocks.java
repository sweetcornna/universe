package io.github.sweetcornna.universe.fleet;

import io.github.sweetcornna.universe.core.fleet.FreighterInterior;
import io.github.sweetcornna.universe.core.fleet.FreighterLayout;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 货船用哪些方块（ADR-0024）：船壳、舱壁、地板、窗、灯借用空间站那几种打不坏的方块，深色装甲板和引擎喷口是货船自己的。
 */
public final class FreighterBlocks {
    private FreighterBlocks() {
    }

    /** 外壳和停在边上的护卫舰。 */
    public static BlockState exterior(FreighterLayout.Role role) {
        return switch (role) {
            case HULL -> UniverseBlocks.STATION_HULL.get().defaultBlockState();
            case PLATE -> UniverseBlocks.FREIGHTER_PLATE.get().defaultBlockState();
            case LIGHT -> UniverseBlocks.STATION_LIGHT.get().defaultBlockState();
            case GLASS -> UniverseBlocks.STATION_GLASS.get().defaultBlockState();
            case ENGINE -> UniverseBlocks.FREIGHTER_ENGINE.get().defaultBlockState();
            case FRAME -> UniverseBlocks.STATION_TRIM.get().defaultBlockState();
            case FLOOR -> UniverseBlocks.STATION_FLOOR.get().defaultBlockState();
            case WALL -> UniverseBlocks.FREIGHTER_PLATE.get().defaultBlockState();
            case CRATE -> UniverseBlocks.SUPPLY_CRATE.get().defaultBlockState();
            case SAFE -> UniverseBlocks.SUPPLY_CRATE.get().defaultBlockState();
            case HAZARD -> UniverseBlocks.LIVE_CONDUIT.get().defaultBlockState();
        };
    }

    /** 船舱里。控制台朝着从机库走过来的方向（-z），舰队终端靠着指挥室的东墙、朝西。 */
    public static BlockState interior(FreighterInterior.Part part) {
        return switch (part) {
            case WALL -> UniverseBlocks.STATION_HULL.get().defaultBlockState();
            case FLOOR -> UniverseBlocks.STATION_FLOOR.get().defaultBlockState();
            case GLASS -> UniverseBlocks.STATION_GLASS.get().defaultBlockState();
            case LIGHT -> UniverseBlocks.STATION_LIGHT.get().defaultBlockState();
            case PAD -> UniverseBlocks.STATION_PAD.get().defaultBlockState();
            case DOOR_FRAME -> UniverseBlocks.STATION_TRIM.get().defaultBlockState();
            case CONSOLE -> UniverseBlocks.FREIGHTER_CONSOLE.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            case FLEET_TERMINAL -> UniverseBlocks.FLEET_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
        };
    }

    /** 这一格是不是货船外壳或护卫舰的方块（拆的时候只拆这些，玩家放上去的不动）。 */
    public static boolean isHull(BlockState state) {
        return state.is(UniverseBlocks.STATION_HULL.get()) || state.is(UniverseBlocks.FREIGHTER_PLATE.get())
                || state.is(UniverseBlocks.STATION_LIGHT.get()) || state.is(UniverseBlocks.STATION_GLASS.get())
                || state.is(UniverseBlocks.FREIGHTER_ENGINE.get()) || state.is(UniverseBlocks.STATION_TRIM.get())
                || state.is(UniverseBlocks.STATION_FLOOR.get());
    }
}
