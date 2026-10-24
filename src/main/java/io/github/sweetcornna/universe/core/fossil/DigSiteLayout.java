package io.github.sweetcornna.universe.core.fossil;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.poi.Landmark;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 古生物发掘场的布局（ADR-0034 §三），本地坐标（原点是中心的地面，+x 东、+z 南，放进世界时按地标的朝向转）。
 *
 * <ul>
 *   <li>中间一片 11×7 的探方：3×2 个 3×3 的方坑，坑之间留一格宽的隔梁，坑挖下去一到两格，坑底大多是化石土。</li>
 *   <li>三顶帐篷，门都朝着探方；北边那顶是收藏家的，门口摆着放骨头的桌子，收藏家站在桌子旁边。</li>
 *   <li>探方外面一圈睡着 3–4 个石哨兵。</li>
 * </ul>
 */
public final class DigSiteLayout {
    /** 探方的范围（本地 x、z，含两端）。 */
    public static final int FIELD_HALF_X = 5;
    public static final int FIELD_HALF_Z = 3;
    /** 每个方坑底上化石土的块数。 */
    static final int MIN_FOSSILS_PER_SQUARE = 4;
    static final int MAX_FOSSILS_PER_SQUARE = 7;
    /** 收藏家站的地方、他的桌子。 */
    public static final int[] COLLECTOR = {2, -6};
    public static final int[] TABLE = {0, -6};
    /** 三顶帐篷：中心和门朝哪边（0 南、1 西、2 北、3 东，和原版的水平朝向序号一样）。 */
    static final Tent[] TENTS = {new Tent(0, -9, 0), new Tent(-10, -1, 3), new Tent(10, 1, 1)};
    /** 石哨兵可能睡的地方，挑 3–4 处。 */
    static final int[][] SENTRY_SPOTS = {{-7, 6}, {7, -6}, {-7, -6}, {7, 7}, {11, -6}, {-11, 6}};
    static final int MIN_SENTRIES = 3;
    static final int MAX_SENTRIES = 4;

    /** 化石土的一格：相对中心地面的位置。 */
    public record Cell(int dx, int dy, int dz) {
    }

    /** 一个方坑：x、z 的范围（含两端），挖多深。 */
    public record Square(int x0, int z0, int x1, int z1, int depth) {
        public boolean contains(int x, int z) {
            return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
        }
    }

    /** 一顶帐篷：中心，门朝哪边。占地是顺着门的方向 4 格深（中心往后两格、往前一格）、横着 3 格宽。 */
    public record Tent(int x, int z, int facing) {
        /** 帐篷占地里的一格（本地坐标）。 */
        public boolean covers(int px, int pz) {
            int[] forward = this.forward();
            int dx = px - this.x;
            int dz = pz - this.z;
            int along = dx * forward[0] + dz * forward[1];
            int across = dx * forward[1] - dz * forward[0];
            return Math.abs(across) <= 1 && along >= -2 && along <= 1;
        }

        /** 门朝的方向（本地 x、z 的一步）。 */
        public int[] forward() {
            return switch (Math.floorMod(this.facing, 4)) {
                case 1 -> new int[] {-1, 0};
                case 2 -> new int[] {0, -1};
                case 3 -> new int[] {1, 0};
                default -> new int[] {0, 1};
            };
        }
    }

    public record Layout(List<Square> squares, List<Cell> fossils, List<int[]> sentries, List<Tent> tents) {
    }

    private DigSiteLayout() {
    }

    /** 按地标的种子排出整个发掘场。 */
    public static Layout layout(long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "dig_site"));
        List<Square> squares = new ArrayList<>();
        List<Cell> fossils = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = 0; sz < 2; sz++) {
                int x0 = sx * 4 - 1;
                int z0 = sz == 0 ? -FIELD_HALF_Z : 1;
                Square square = new Square(x0, z0, x0 + 2, z0 + 2, rng.range(1, 2));
                squares.add(square);
                List<Cell> floor = new ArrayList<>();
                for (int x = square.x0(); x <= square.x1(); x++) {
                    for (int z = square.z0(); z <= square.z1(); z++) {
                        floor.add(new Cell(x, -square.depth(), z));
                    }
                }
                int count = rng.range(MIN_FOSSILS_PER_SQUARE, MAX_FOSSILS_PER_SQUARE);
                for (int i = 0; i < count; i++) {
                    fossils.add(floor.remove(rng.nextInt(floor.size())));
                }
            }
        }
        List<int[]> candidates = new ArrayList<>();
        for (int[] spot : SENTRY_SPOTS) {
            candidates.add(spot.clone());
        }
        int sentries = rng.range(MIN_SENTRIES, MAX_SENTRIES);
        List<int[]> chosen = new ArrayList<>();
        for (int i = 0; i < sentries; i++) {
            chosen.add(candidates.remove(rng.nextInt(candidates.size())));
        }
        return new Layout(List.copyOf(squares), List.copyOf(fossils), List.copyOf(chosen), List.of(TENTS));
    }

    /** 收藏家站的地方（世界坐标的 x、z）。 */
    public static int[] collectorSpot(Landmark landmark) {
        return world(landmark, COLLECTOR[0], COLLECTOR[1]);
    }

    /** 本地坐标转成世界坐标的 x、z。 */
    public static int[] world(Landmark landmark, int dx, int dz) {
        int[] r = rotate(landmark.rotation(), dx, dz);
        return new int[] {landmark.x() + r[0], landmark.z() + r[1]};
    }

    /** 转 {@code rotation} 个 90°（顺时针，和地标的建筑一样）：(x, z) → (-z, x)。 */
    static int[] rotate(int rotation, int dx, int dz) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new int[] {-dz, dx};
            case 2 -> new int[] {-dx, -dz};
            case 3 -> new int[] {dz, -dx};
            default -> new int[] {dx, dz};
        };
    }
}
