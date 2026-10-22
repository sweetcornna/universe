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
