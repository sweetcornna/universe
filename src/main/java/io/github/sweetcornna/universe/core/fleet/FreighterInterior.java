package io.github.sweetcornna.universe.core.fleet;

import org.jspecify.annotations.Nullable;

/**
 * 货船里面（ADR-0024）：每艘货船一个维度，生成区块时照这里放。坐标相对 {@link #ORIGIN_Y} 那层地板的中心。
 *
 * <p>从前往后：机库（两个起降台，前墙是敞开的机库门，飞出去就回到太空）→ 走廊 → 舰桥（货船控制台）→
 * 右手边的舰队指挥室（舰队终端）→ 后面一大片空甲板，随便建。墙、地板、天花板都打不坏。
 */
public final class FreighterInterior {
    /** 地板那一层在世界里的 y。 */
    public static final int ORIGIN_Y = 64;

    public enum Part {
        WALL, FLOOR, GLASS, LIGHT, PAD, CONSOLE, FLEET_TERMINAL, DOOR_FRAME
    }

    /** 机库：x、z 各 ±{@link #HANGAR_HALF}，净高 {@link #HANGAR_HEIGHT}。机库门在 z = -HANGAR_HALF - 1 那面墙上。 */
    public static final int HANGAR_HALF = 12;
    public static final int HANGAR_HEIGHT = 9;
    /** 机库门：半宽和高。 */
    public static final int DOOR_HALF = 7;
    public static final int DOOR_HEIGHT = 7;
    /** 两个起降台的中心。 */
    public static final int[][] PADS = {{-6, 3}, {6, 3}};
    public static final int PAD_RADIUS = 3;
    /** 舰桥。 */
    static final int BRIDGE_X = 7;
    static final int BRIDGE_Z0 = 21;
    static final int BRIDGE_Z1 = 31;
    static final int BRIDGE_HEIGHT = 5;
    /** 货船控制台的位置。 */
    public static final int[] CONSOLE = {0, 1, 29};
    /** 舰队指挥室。 */
    static final int FLEET_X0 = 9;
    static final int FLEET_X1 = 17;
    static final int FLEET_Z0 = 23;
    static final int FLEET_Z1 = 29;
    public static final int[] FLEET = {16, 1, 26};
    /** 甲板。 */
    public static final int DECK_HALF = 20;
    public static final int DECK_Z0 = 36;
    public static final int DECK_Z1 = 83;
    public static final int DECK_HEIGHT = 14;
    /** 走路进来的人落在机库中间。 */
    public static final int[] ARRIVAL = {0, 1, 0};
    /** 整个船舱的范围：{minX, minY, minZ, maxX, maxY, maxZ}。 */
    public static final int[] BOUNDS = {-DECK_HALF - 1, 0, -HANGAR_HALF - 1, DECK_HALF + 1, DECK_HEIGHT + 1, DECK_Z1 + 1};

    private FreighterInterior() {
    }

    /** 空着的格子（房间里面）。 */
    private static boolean open(int x, int y, int z) {
        if (y < 1) {
            return false;
        }
        // 机库
        if (Math.abs(x) <= HANGAR_HALF && Math.abs(z) <= HANGAR_HALF && y <= HANGAR_HEIGHT) {
            return true;
        }
        // 机库门
        if (z == -HANGAR_HALF - 1 && Math.abs(x) <= DOOR_HALF && y <= DOOR_HEIGHT) {
            return true;
        }
        // 机库到舰桥的走廊
        if (Math.abs(x) <= 2 && z > HANGAR_HALF && z < BRIDGE_Z0 && y <= 4) {
            return true;
        }
        // 舰桥
        if (Math.abs(x) <= BRIDGE_X && z >= BRIDGE_Z0 && z <= BRIDGE_Z1 && y <= BRIDGE_HEIGHT) {
            return true;
        }
        // 舰桥到指挥室的门和指挥室
        if (x == BRIDGE_X + 1 && z >= 25 && z <= 27 && y <= 3) {
            return true;
        }
        if (x >= FLEET_X0 && x <= FLEET_X1 && z >= FLEET_Z0 && z <= FLEET_Z1 && y <= 4) {
            return true;
        }
        // 舰桥到甲板的走廊
        if (Math.abs(x) <= 2 && z > BRIDGE_Z1 && z < DECK_Z0 && y <= 4) {
            return true;
        }
        // 甲板
        return Math.abs(x) <= DECK_HALF && z >= DECK_Z0 && z <= DECK_Z1 && y <= DECK_HEIGHT;
    }

    /** 这一格放什么；{@code null} 是空气或者船舱外面的虚空。 */
    public static @Nullable Part at(int x, int y, int z) {
        if (x < BOUNDS[0] || x > BOUNDS[3] || y < BOUNDS[1] || y > BOUNDS[4] || z < BOUNDS[2] || z > BOUNDS[5]) {
            return null;
        }
        if (open(x, y, z)) {
            if (x == CONSOLE[0] && y == CONSOLE[1] && z == CONSOLE[2]) {
                return Part.CONSOLE;
            }
            if (x == FLEET[0] && y == FLEET[1] && z == FLEET[2]) {
                return Part.FLEET_TERMINAL;
            }
            return null;
        }
        // 只在房间边上放：挨着一格空的
        boolean edge = open(x + 1, y, z) || open(x - 1, y, z) || open(x, y + 1, z) || open(x, y - 1, z) || open(x, y, z + 1)
                || open(x, y, z - 1);
        if (!edge) {
            return null;
        }
        if (open(x, y + 1, z)) {
            // 下面是实的、上面是空的：地板
            return floor(x, z);
        }
        if (open(x, y - 1, z)) {
            // 天花板：每隔几格一盏灯
            return Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0 ? Part.LIGHT : Part.WALL;
        }
        // 机库门的门框
        if (z == -HANGAR_HALF - 1 && (Math.abs(x) == DOOR_HALF + 1 || y == DOOR_HEIGHT + 1)) {
            return Part.DOOR_FRAME;
        }
        // 舰桥和甲板两侧开窗
        if ((Math.abs(x) == BRIDGE_X + 1 && z >= BRIDGE_Z0 && z <= BRIDGE_Z1 || Math.abs(x) == DECK_HALF + 1 && z >= DECK_Z0)
                && y >= 2 && y <= 4) {
            return Part.GLASS;
        }
        return Part.WALL;
    }

    private static Part floor(int x, int z) {
        for (int[] pad : PADS) {
            int dx = x - pad[0];
            int dz = z - pad[1];
            if (dx * dx + dz * dz <= PAD_RADIUS * PAD_RADIUS) {
                return Part.PAD;
            }
        }
        return Part.FLOOR;
    }

    /** 船在机库门外面了（该回太空了）。 */
    public static boolean outside(double z) {
        return z < -HANGAR_HALF - 2;
    }
}
