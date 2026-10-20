package io.github.sweetcornna.universe.core.blueprint;

/**
 * 蓝图的几何（ADR-0018）：选区的大小、方块在蓝图里的位置怎么编码、转 90° 以后落在哪。蓝图里的位置从选区的最小角
 * 算起，转过以后仍然从新的最小角算起。
 */
public final class BlueprintGeometry {
    /** 选区每边最多几格。 */
    public static final int MAX_SIZE = 32;
    private static final int BITS = 6;
    private static final int MASK = (1 << BITS) - 1;

    private BlueprintGeometry() {
    }

    /** 两个角框出来的选区每边几格：{x, y, z}。 */
    public static int[] size(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new int[] {Math.abs(x2 - x1) + 1, Math.abs(y2 - y1) + 1, Math.abs(z2 - z1) + 1};
    }

    /** 每边都不超过 {@link #MAX_SIZE}。 */
    public static boolean fits(int[] size) {
        return size[0] <= MAX_SIZE && size[1] <= MAX_SIZE && size[2] <= MAX_SIZE;
    }

    /** 蓝图里的位置压成一个整数（每轴 6 位）。 */
    public static int pack(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x > MASK || y > MASK || z > MASK) {
            throw new IllegalArgumentException(x + "," + y + "," + z);
        }
        return x | y << BITS | z << 2 * BITS;
    }

    public static int x(int packed) {
        return packed & MASK;
    }

    public static int y(int packed) {
        return packed >> BITS & MASK;
    }

    public static int z(int packed) {
        return packed >> 2 * BITS & MASK;
    }

    /** 顺时针转 quarterTurns 个 90° 以后的大小（x 和 z 可能对调）。 */
    public static int[] rotatedSize(int[] size, int quarterTurns) {
        return Math.floorMod(quarterTurns, 2) == 0 ? size.clone() : new int[] {size[2], size[1], size[0]};
    }

    /**
     * 从上往下看顺时针转 quarterTurns 个 90° 以后，蓝图里 (x, z) 的方块落在哪：{x, z}，从转过以后的最小角算起。
     * 和原版 {@code Rotation.CLOCKWISE_90} 转方块朝向的方向一致（北 → 东）。
     */
    public static int[] rotate(int x, int z, int sizeX, int sizeZ, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new int[] {sizeZ - 1 - z, x};
            case 2 -> new int[] {sizeX - 1 - x, sizeZ - 1 - z};
            case 3 -> new int[] {z, sizeX - 1 - x};
            default -> new int[] {x, z};
        };
    }
}
