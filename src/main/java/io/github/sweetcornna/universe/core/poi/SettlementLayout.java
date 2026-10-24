package io.github.sweetcornna.universe.core.poi;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚落（ADR-0027 §五）的布局：小屋、交易终端、知识石和 NPC 站的位置，按地标的中心和朝向算。建筑（适配层）和 NPC 的管理器都从这里取位置，
 * 两边对得上。
 *
 * <p>本地坐标和建筑一样：原点是中心，"前方"是 -z，转法和结构模板一致（顺时针 90° 时 (x, z) → (-z, x)）。
 * 聚落和交易站的地面都压平到中心那一列原来的地面高度，NPC 站在这一层上面。聚落的站位头上都是空的；
 * 交易站的访客在屋里，头上有屋顶，取高度时要从屋顶往下找地板。
 *
 * <p>聚落的样子：中间一片半径 5 的广场，知识石立在广场靠里的地方；广场后面是带交易终端的货摊，左右两边三四间小屋，
 * 门都朝着广场；前面一条路进来。两个访客站在广场两边，技术商站在货摊门口的右手边。
 */
public final class SettlementLayout {
    /** 广场的半径（本地坐标 dx² + dz² ≤ {@code PLAZA²}）。 */
    public static final int PLAZA = 5;
    /** 小屋的中心：左前、右前、左后、右后。 */
    private static final int[][] HUTS = {{-8, -4}, {8, -4}, {-8, 5}, {8, 5}};
    /** 最大的小屋（戍卫族的石堡）的半宽：7×7。别的种族的小屋是 5×5。 */
    public static final int HUT_HALF = 3;
    /** 灯：路口两盏，广场后面两盏。 */
    private static final int[][] LAMPS = {{-2, -7}, {2, -7}, {-4, 4}, {4, 4}};
    /** 知识石的位置。 */
    private static final int[] STONE = {0, 2};
    /** 交易终端的位置（货摊靠后墙）。 */
    private static final int[] TERMINAL = {0, 9};
    /** NPC 站的地方：两个访客、一个技术商。 */
    private static final int[][] NPCS = {{-3, -1}, {3, -1}, {2, 6}};
    /** 交易站：终端在屋里靠后墙的 (0, 2)，访客站在它右手边，不挡从门口到终端的路，也不挡两边的补给箱。 */
    private static final int[] TRADING_POST_VISITOR = {1, 2};
    /**
     * 接管以后的 8 块地基（ADR-0034 §二）的中心：一圈方形的环路外面，左前、左、左后、后面两块、右后、右、右前，离中心 23–27 格。
     * 前面那一段空着走路。
     */
    private static final int[][] PLOTS = {{-20, -17}, {-23, 0}, {-20, 17}, {-7, 23}, {7, 23}, {20, 17}, {23, 0}, {20, -17}};
    /** 地基的半宽：11×11，最大的建筑（9×9 的着陆坪）外面再留一圈边。 */
    public static final int PLOT_HALF = 5;
    /** 环路：本地坐标 |x| 或 |z| 在这两个数之间的一圈（两格宽），把广场和地基连起来。 */
    public static final int RING_INNER = 13;
    public static final int RING_OUTER = 14;

    private SettlementLayout() {
    }

    /** 3 个 NPC 站的地方（世界坐标的 x、z）。前两个是访客，第三个是技术商。 */
    public static List<int[]> npcSpots(Landmark landmark) {
        List<int[]> spots = new ArrayList<>(NPCS.length);
        for (int[] spot : NPCS) {
            spots.add(world(landmark, spot[0], spot[1]));
        }
        return spots;
    }

    /** 交易站里访客站的地方（交易终端旁边，世界坐标的 x、z）。 */
    public static int[] tradingPostSpot(Landmark landmark) {
        return world(landmark, TRADING_POST_VISITOR[0], TRADING_POST_VISITOR[1]);
    }

    /** 小屋的中心（本地坐标）：三间或四间，按地标的种子定。门都朝着广场：左边的朝 +x，右边的朝 -x。 */
    public static List<int[]> huts(Landmark landmark) {
        List<int[]> huts = new ArrayList<>(HUTS.length);
        boolean four = (landmark.seed() & 1) == 0;
        int skipped = (int) (landmark.seed() >>> 1 & 3);
        for (int i = 0; i < HUTS.length; i++) {
            if (four || i != skipped) {
                huts.add(HUTS[i].clone());
            }
        }
        return huts;
    }

    /** 知识石的位置（本地坐标）。 */
    public static int[] stone() {
        return STONE.clone();
    }

    /** 交易终端的位置（本地坐标）。 */
    public static int[] terminal() {
        return TERMINAL.clone();
    }

    /** 货摊占的地方（本地坐标，含两端）：{最小 x, 最小 z, 最大 x, 最大 z}。终端靠后墙，前面空一格站人。 */
    public static int[] stall() {
        return new int[] {TERMINAL[0] - 2, TERMINAL[1] - 2, TERMINAL[0] + 2, TERMINAL[1] + 1};
    }

    /** 灯的位置（本地坐标）。 */
    public static List<int[]> lamps() {
        List<int[]> lamps = new ArrayList<>(LAMPS.length);
        for (int[] lamp : LAMPS) {
            lamps.add(lamp.clone());
        }
        return lamps;
    }

    /** 地基的个数。 */
    public static int plotCount() {
        return PLOTS.length;
    }

    /** 第 {@code index} 块地基的中心（本地坐标）。 */
    public static int[] plot(int index) {
        return PLOTS[index].clone();
    }

    /**
     * 第 {@code index} 块地基上的建筑在聚落本地坐标里转几个 90°：建筑自己的门在它的 -z，转过去朝着环路。
     * 左右两边的朝 ±x，后面两块朝 -z。
     */
    public static int plotRotation(int index) {
        int[] c = PLOTS[index];
        // 要的门朝向（聚落本地）
        int fx = Math.abs(c[0]) >= Math.abs(c[1]) ? -Integer.signum(c[0]) : 0;
        int fz = fx == 0 ? -Integer.signum(c[1]) : 0;
        for (int r = 0; r < 4; r++) {
            int[] d = rotate(r, 0, -1);
            if (d[0] == fx && d[1] == fz) {
                return r;
            }
        }
        return 0;
    }

    /** 地基上的建筑坐标（建筑本地的 bx、bz）转成聚落本地坐标。 */
    public static int[] plotLocal(int index, int bx, int bz) {
        int[] c = PLOTS[index];
        int[] r = rotate(plotRotation(index), bx, bz);
        return new int[] {c[0] + r[0], c[1] + r[1]};
    }

    /** 地基上的建筑坐标转世界坐标（x、z）。 */
    public static int[] plotWorld(Landmark landmark, int index, int bx, int bz) {
        int[] local = plotLocal(index, bx, bz);
        return world(landmark, local[0], local[1]);
    }

    /** 地基上的建筑转到世界里一共转几个 90°（地标的朝向加地基自己的）。 */
    public static int plotWorldRotation(Landmark landmark, int index) {
        return Math.floorMod(landmark.rotation() + plotRotation(index), 4);
    }

    /** 本地坐标 (dx, dz) 落在第几块地基上（含地基的边），不在任何一块上是 -1。 */
    public static int plotAt(int dx, int dz) {
        for (int i = 0; i < PLOTS.length; i++) {
            if (Math.abs(dx - PLOTS[i][0]) <= PLOT_HALF && Math.abs(dz - PLOTS[i][1]) <= PLOT_HALF) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 聚落本地坐标转回来：世界坐标 (x, z) 相对地标中心转回本地，和 {@link #world} 互逆。
     */
    public static int[] local(Landmark landmark, int x, int z) {
        return rotate(4 - Math.floorMod(landmark.rotation(), 4), x - landmark.x(), z - landmark.z());
    }

    /**
     * 接管时铺的路（本地坐标，每格一个 {x, z}）：一圈方形的环路，从广场往前、往左、往右三条接到环路上，再从每块地基的门口
     * 一条短路接到环路。不经过小屋和货摊。
     */
    public static List<int[]> roads(Landmark landmark) {
        java.util.Set<Long> cells = new java.util.LinkedHashSet<>();
        for (int a = -RING_OUTER; a <= RING_OUTER; a++) {
            for (int w = RING_INNER; w <= RING_OUTER; w++) {
                add(cells, a, w);
                add(cells, a, -w);
                add(cells, w, a);
                add(cells, -w, a);
            }
        }
        // 前面那条路接到环路，左右两条从两排小屋中间穿过
        for (int dz = -RING_INNER; dz < -PLAZA; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                add(cells, dx, dz);
            }
        }
        for (int dx = PLAZA + 1; dx < RING_INNER; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                add(cells, dx, dz);
                add(cells, -dx, dz);
            }
        }
        for (int i = 0; i < PLOTS.length; i++) {
            int[] door = plotLocal(i, 0, -PLOT_HALF - 1);
            int[] c = PLOTS[i];
            // 门口先顺着门的朝向走到环路那一条线上，再横着接过去
            int sx = Integer.signum(door[0] - c[0]);
            int sz = Integer.signum(door[1] - c[1]);
            int x = door[0];
            int z = door[1];
            for (int step = 0; step < 16 && !onRing(x, z); step++) {
                int mx = 0;
                int mz = 0;
                if (sx != 0 && Math.abs(x) > RING_OUTER) {
                    mx = sx;
                } else if (sz != 0 && Math.abs(z) > RING_OUTER) {
                    mz = sz;
                } else if (Math.abs(x) > RING_OUTER) {
                    mx = -Integer.signum(x);
                } else {
                    mz = -Integer.signum(z);
                }
                // 两格宽：另一格放在靠聚落中间的那一边
                add(cells, x, z);
                if (mx != 0) {
                    add(cells, x, z == 0 ? 1 : z - Integer.signum(z));
                } else {
                    add(cells, x == 0 ? 1 : x - Integer.signum(x), z);
                }
                x += mx;
                z += mz;
            }
        }
        // 小屋、货摊、广场里不铺
        List<int[]> huts = huts(landmark);
        int[] stall = stall();
        List<int[]> roads = new ArrayList<>(cells.size());
        for (long cell : cells) {
            int x = (int) (cell >> 32);
            int z = (int) cell;
            if (x * x + z * z <= PLAZA * PLAZA + 5) {
                continue;
            }
            if (x >= stall[0] - 1 && x <= stall[2] + 1 && z >= stall[1] - 1 && z <= stall[3] + 1) {
                continue;
            }
            boolean hut = false;
            for (int[] h : huts) {
                hut |= Math.abs(x - h[0]) <= HUT_HALF && Math.abs(z - h[1]) <= HUT_HALF;
            }
            if (!hut && plotAt(x, z) < 0) {
                roads.add(new int[] {x, z});
            }
        }
        return roads;
    }

    private static boolean onRing(int x, int z) {
        int m = Math.max(Math.abs(x), Math.abs(z));
        return m >= RING_INNER && m <= RING_OUTER;
    }

    private static void add(java.util.Set<Long> cells, int x, int z) {
        cells.add((long) x << 32 | (z & 0xFFFFFFFFL));
    }

    /** 本地坐标转世界坐标（x、z），和建筑的转法一样。 */
    public static int[] world(Landmark landmark, int dx, int dz) {
        int[] r = rotate(landmark.rotation(), dx, dz);
        return new int[] {landmark.x() + r[0], landmark.z() + r[1]};
    }

    /** 转 {@code rotation} 个 90°（顺时针）：(x, z) → (-z, x)。 */
    static int[] rotate(int rotation, int dx, int dz) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new int[] {-dz, dx};
            case 2 -> new int[] {-dx, -dz};
            case 3 -> new int[] {dz, -dx};
            default -> new int[] {dx, dz};
        };
    }
}
