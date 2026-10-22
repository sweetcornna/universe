package io.github.sweetcornna.universe.sentinel;

import io.github.sweetcornna.universe.block.sentinel.PillarTerminalBlock;
import io.github.sweetcornna.universe.core.portal.PortalLayout;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 守卫塔的样子（ADR-0026）：9×9 的底座上一段 5×5 的塔基，正面嵌着终端；往上是 3×3 的塔身，四面正中一道琥珀色的灯带，
 * 和守卫机巡逻时扫描的光一个颜色；顶上一圈发光的环带，像守卫机的面罩，再往上是尖顶。底座以上 {@value #HEIGHT} 格高。
 *
 * <p>本地坐标：x 往右手边，z 往前（终端朝的方向），y 往上；底座那一层是 y = 0。
 */
public final class PillarStructure {
    /** 底座半宽。 */
    public static final int BASE = 4;
    /** 底座以上多高（尖顶的灯也算）。 */
    public static final int HEIGHT = 28;
    /** 终端在哪 {x, y, z}：塔基正面的中间。 */
    static final int[] TERMINAL = {0, 1, 2};
    /** 底座以下最多往下垫多深（站在水上、熔岩上、悬崖边时）。 */
    private static final int SUPPORT_DEPTH = 96;

    private PillarStructure() {
    }

    /** 本地坐标转世界坐标。 */
    static BlockPos at(BlockPos origin, Direction front, int x, int y, int z) {
        int[] d = PortalLayout.rotate(x, z, front.getStepX(), front.getStepZ());
        return origin.offset(d[0], y, d[1]);
    }

    /** 把整座塔摆出来：先清空底座以上，底座以下垫到实心的地面，再一层层放。GameTest 也用。 */
    public static void build(ServerLevel level, BlockPos origin, Direction front) {
        BlockState plating = UniverseBlocks.SENTINEL_PILLAR_PLATING.get().defaultBlockState();
        BlockState light = UniverseBlocks.SENTINEL_PILLAR_LIGHT.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -BASE; x <= BASE; x++) {
            for (int z = -BASE; z <= BASE; z++) {
                for (int y = 1; y <= HEIGHT + 2; y++) {
                    set(level, at(origin, front, x, y, z), air);
                }
                // 底座四角嵌着灯
                set(level, at(origin, front, x, 0, z), Math.abs(x) == BASE && Math.abs(z) == BASE ? light : plating);
                for (int y = -1; y >= -SUPPORT_DEPTH; y--) {
                    BlockPos below = at(origin, front, x, y, z);
                    BlockState there = level.getBlockState(below);
                    if (below.getY() < level.getMinY() || there.isSolidRender() && there.getFluidState().isEmpty()) {
                        break;
                    }
                    set(level, below, plating);
                }
            }
        }
        // 塔基 5×5，四个角上立着矮柱，柱顶一盏灯
        for (int y = 1; y <= 3; y++) {
            fill(level, origin, front, 2, y, plating);
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 1; y <= 4; y++) {
                    set(level, at(origin, front, sx * 3, y, sz * 3), plating);
                }
                set(level, at(origin, front, sx * 3, 5, sz * 3), light);
            }
        }
        // 塔身 3×3，四面正中是灯带
        for (int y = 4; y <= 20; y++) {
            fill(level, origin, front, 1, y, plating);
            if (y >= 5 && y <= 19) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    set(level, at(origin, front, side.getStepX(), y, side.getStepZ()), light);
                }
            }
        }
        // 面罩那一圈：上下两层 5×5 夹着一圈灯
        fill(level, origin, front, 2, 21, plating);
        fill(level, origin, front, 2, 22, plating);
        for (int i = -1; i <= 1; i++) {
            set(level, at(origin, front, i, 22, 2), light);
            set(level, at(origin, front, i, 22, -2), light);
            set(level, at(origin, front, 2, 22, i), light);
            set(level, at(origin, front, -2, 22, i), light);
        }
        fill(level, origin, front, 2, 23, plating);
        // 尖顶
        fill(level, origin, front, 1, 24, plating);
        for (int y = 25; y <= 27; y++) {
            set(level, at(origin, front, 0, y, 0), plating);
        }
        set(level, at(origin, front, 0, HEIGHT, 0), light);
        // 终端嵌在塔基正面，朝外
        BlockState terminal = UniverseBlocks.SENTINEL_PILLAR_TERMINAL.get().defaultBlockState().setValue(PillarTerminalBlock.FACING, front);
        level.setBlock(terminal(origin, front), terminal, Block.UPDATE_ALL);
    }

    /** 终端的位置。 */
    static BlockPos terminal(BlockPos origin, Direction front) {
        return at(origin, front, TERMINAL[0], TERMINAL[1], TERMINAL[2]);
    }

    /** 一层 (2r+1)×(2r+1) 的外壳。 */
    private static void fill(ServerLevel level, BlockPos origin, Direction front, int r, int y, BlockState state) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                set(level, at(origin, front, x, y, z), state);
            }
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }
}
