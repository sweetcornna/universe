package io.github.sweetcornna.universe.world;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 露在地表的矿脉（玩法设计 §9.1，ADR-0011）：一片圆形的矿，替换地表往下几层，远远就能看见，采矿机直接放在上面开采。
 * 原版地物和星球地物共用。只在本区块里放，不越界。
 */
public final class Outcrops {
    /** 往下替换几层。 */
    private static final int DEPTH = 3;
    /** 找地表时最多越过几格空气和植物。 */
    private static final int SURFACE_SCAN = 8;

    private Outcrops() {
    }

    /**
     * 放一片露头。
     *
     * @param chunkX      区块西北角
     * @param chunkZ      区块西北角
     * @param radius      半径（格），2–4
     * @param replaceable 可以换成矿的方块（土、沙、石头）
     * @return 放了多少个方块
     */
    public static int patch(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, int radius, BlockState ore,
            Predicate<BlockState> replaceable) {
        int cx = chunkX + radius + random.nextInt(Math.max(1, 16 - 2 * radius));
        int cz = chunkZ + radius + random.nextInt(Math.max(1, 16 - 2 * radius));
        int placed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // 边缘参差一点，不是规整的圆
                double edge = radius + 0.5 - random.nextDouble() * 0.9;
                if (dx * dx + dz * dz > edge * edge) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int top = surface(level, x, z);
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                int depth = dx * dx + dz * dz <= (radius - 1) * (radius - 1) ? DEPTH : 1 + random.nextInt(DEPTH);
                for (int d = 0; d < depth; d++) {
                    cursor.set(x, top - d, z);
                    if (replaceable.test(level.getBlockState(cursor))) {
                        level.setBlock(cursor, ore, 2);
                        placed++;
                    }
                }
                clearPlants(level, cursor.set(x, top + 1, z));
            }
        }
        return placed;
    }

    /**
     * 放一个单独的方块在地表（原油涌泉）。
     *
     * @return 放下了
     */
    public static boolean single(WorldGenLevel level, RandomSource random, int chunkX, int chunkZ, BlockState block,
            Predicate<BlockState> replaceable) {
        int x = chunkX + 3 + random.nextInt(10);
        int z = chunkZ + 3 + random.nextInt(10);
        int top = surface(level, x, z);
        if (top == Integer.MIN_VALUE) {
            return false;
        }
        BlockPos pos = new BlockPos(x, top, z);
        if (!replaceable.test(level.getBlockState(pos))) {
            return false;
        }
        level.setBlock(pos, block, 2);
        clearPlants(level, pos.above().mutable());
        return true;
    }

    /**
     * 地表最上面一个实心方块的高度，草、花、雪层这类可替换的东西不算；有水盖着时返回 {@link Integer#MIN_VALUE}。
     *
     * <p>从高度图起步，往上找到第一格空气，再往下越过植物找到实心的地面。不直接比较两张高度图，是因为草和花
     * 会让两张图不一样，地表长满草的平原就放不下露头了。
     */
    private static int surface(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
        while (pos.getY() < level.getMaxY() && !level.getBlockState(pos).isAir()) {
            pos.move(Direction.UP);
        }
        for (int i = 0; i < SURFACE_SCAN && pos.getY() > level.getMinY(); i++) {
            pos.move(Direction.DOWN);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (!state.isAir() && !state.canBeReplaced()) {
                return pos.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    /** 矿上面的草、花这类可以直接替换的植物清掉（包括两格高的）。 */
    private static void clearPlants(WorldGenLevel level, BlockPos.MutableBlockPos pos) {
        for (int i = 0; i < 2; i++) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                return;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            pos.move(0, 1, 0);
        }
    }
}
