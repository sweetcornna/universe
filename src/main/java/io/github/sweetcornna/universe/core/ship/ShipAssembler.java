package io.github.sweetcornna.universe.core.ship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 组装：从驾驶座出发，把六面相连的方块都算作船身（广度优先）。地形方块不算，免得把整座山当成飞船。
 */
public final class ShipAssembler {
    /** 一艘船最多多少个方块。 */
    public static final int MAX_BLOCKS = 4096;

    private ShipAssembler() {
    }

    /** 世界的只读视图：这一格是不是能装上船的方块。 */
    @FunctionalInterface
    public interface World {
        boolean isShipBlock(int x, int y, int z);
    }

    /**
     * 组装结果。
     *
     * @param positions 船身方块的世界坐标（{@link #pack} 打包），起点在第一个
     * @param tooLarge  超出上限（多半是连到了地形或者建筑上）
     */
    public record Result(List<Long> positions, boolean tooLarge) {
    }

    public static Result assemble(World world, int x, int y, int z, int limit) {
        List<Long> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long start = pack(x, y, z);
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            long p = queue.poll();
            found.add(p);
            if (found.size() > limit) {
                return new Result(List.of(), true);
            }
            int px = unpackX(p);
            int py = unpackY(p);
            int pz = unpackZ(p);
            for (int[] d : NEIGHBOURS) {
                int nx = px + d[0];
                int ny = py + d[1];
                int nz = pz + d[2];
                long n = pack(nx, ny, nz);
                if (!seen.contains(n) && world.isShipBlock(nx, ny, nz)) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return new Result(found, false);
    }

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** 打包成和原版 {@code BlockPos#asLong} 一样的格式（x、z 各 26 位，y 12 位）。 */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    public static int unpackX(long p) {
        return (int) (p >> 38);
    }

    public static int unpackY(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }
}
