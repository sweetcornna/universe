package io.github.sweetcornna.universe.core.fleet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 货船的外壳（ADR-0024）：太空里那一大片方块。只放表面一层，里面是空的；玩家进的是另一个维度里的船舱。
 *
 * <p>坐标：船底中线为 y = 0，船头朝 -z，左舷（-x）的中段是机库口。船头往前收窄，背上一道脊，后部是舰桥塔，
 * 船尾几台发光的引擎。两侧每隔几格一盏灯。
 */
public final class FreighterLayout {
    public enum Role {
        /** 浅色的船壳。 */
        HULL,
        /** 深色的装甲板，做出分段的纹路。 */
        PLATE,
        LIGHT,
        /** 舰桥的窗。 */
        GLASS,
        /** 引擎的喷口，发光。 */
        ENGINE,
        /** 机库口的发光边框。 */
        FRAME,
        // ---- 只有废弃货船里面有
        /** 甲板。 */
        FLOOR,
        /** 舱壁。 */
        WALL,
        /** 货箱。 */
        CRATE,
        /** 船长保险箱。 */
        SAFE,
        /** 漏电的地方。 */
        HAZARD
    }

    public record Cell(int x, int y, int z, Role role) {
    }

    /**
     * 一艘货船的外壳。
     *
     * @param hangarMin 机库凹进去那块的最小角（含）
     * @param hangarMax 最大角（含）
     */
    public record Exterior(List<Cell> cells, int length, int halfWidth, int height, int[] hangarMin, int[] hangarMax) {
        /** 船在不在机库里（按船的中心算）。 */
        public boolean inHangar(double x, double y, double z) {
            return x >= this.hangarMin[0] && x <= this.hangarMax[0] + 1 && y >= this.hangarMin[1] && y <= this.hangarMax[1] + 1
                    && z >= this.hangarMin[2] && z <= this.hangarMax[2] + 1;
        }

        /** 机库口外面一点：从船舱里飞出来时停在这里，船头朝外（-x）。 */
        public double[] mouth() {
            return new double[] {this.hangarMin[0] - 12, (this.hangarMin[1] + this.hangarMax[1]) / 2.0,
                    (this.hangarMin[2] + this.hangarMax[2] + 1) / 2.0};
        }

        /** 包围盒：{minX, minY, minZ, maxX, maxY, maxZ}。 */
        public int[] bounds() {
            int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (Cell c : this.cells) {
                b[0] = Math.min(b[0], c.x());
                b[1] = Math.min(b[1], c.y());
                b[2] = Math.min(b[2], c.z());
                b[3] = Math.max(b[3], c.x());
                b[4] = Math.max(b[4], c.y());
                b[5] = Math.max(b[5], c.z());
            }
            return b;
        }
    }

    /**
     * 废弃货船：小一号的外壳，破了几个洞；机库后墙开了门，里面隔成一间间舱室，舱壁上的门一左一右错开，
     * 靠墙放着货箱，最前面那间有船长保险箱，几处漏电。
     */
    public record Derelict(Exterior hull, List<Cell> interior) {
        public List<Cell> all() {
            List<Cell> all = new ArrayList<>(this.hull.cells());
            all.addAll(this.interior);
            return all;
        }
    }

    /** 废弃货船上的货箱数。 */
    static final int MIN_CRATES = 6;
    static final int MAX_CRATES = 10;

    /** 船头收窄的长度。 */
    static final int BOW = 14;
    /** 机库凹进去多深、多高、多宽（半宽）。 */
    static final int HANGAR_DEPTH = 12;
    static final int HANGAR_BOTTOM = 2;
    static final int HANGAR_TOP = 8;
    static final int HANGAR_HALF = 7;
    /** 机库口中间那一层（相对船底）。 */
    public static final int HANGAR_MIDDLE = (HANGAR_BOTTOM + HANGAR_TOP) / 2;

    private final int length;
    private final int halfWidth;
    private final int height;
    private final int z0;
    private final int z1;
    private final int[][] engines;

    private FreighterLayout(Rng rng, int minLength, int maxLength) {
        this.length = rng.range(minLength, maxLength);
        this.halfWidth = rng.range(9, 12);
        this.height = rng.range(9, 12);
        this.z0 = -this.length / 2;
        this.z1 = this.z0 + this.length - 1;
        int count = rng.range(2, 4);
        this.engines = new int[count][];
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : (double) i / (count - 1);
            int x = (int) Math.round((t - 0.5) * (this.halfWidth * 1.2));
            this.engines[i] = new int[] {x, this.height / 2};
        }
    }

    /** 玩家的货船。 */
    public static Exterior exterior(long seed) {
        return new FreighterLayout(Rng.of(seed), 64, 80).build();
    }

    /** 小一点的：废弃货船、遇险的货船用。 */
    public static Exterior small(long seed) {
        return new FreighterLayout(Rng.of(seed), 52, 60).build();
    }

    public static Derelict derelict(long seed) {
        Rng rng = Rng.of(seed);
        FreighterLayout layout = new FreighterLayout(rng.fork("hull"), 52, 60);
        return layout.wreck(rng);
    }

    private Derelict wreck(Rng rng) {
        Exterior hull = this.build();
        // 破洞：船壳上挖掉几个球，避开机库口
        List<int[]> breaches = new ArrayList<>();
        int count = rng.range(3, 6);
        for (int i = 0; i < count; i++) {
            Cell c = hull.cells().get(rng.nextInt(hull.cells().size()));
            if (c.y() > 1 && Math.abs(c.z()) > HANGAR_HALF + 4) {
                breaches.add(new int[] {c.x(), c.y(), c.z(), rng.range(2, 3)});
            }
        }
        int doorX = -this.halfWidth + HANGAR_DEPTH;
        List<Cell> shell = new ArrayList<>();
        for (Cell c : hull.cells()) {
            boolean breached = false;
            for (int[] b : breaches) {
                int dx = c.x() - b[0];
                int dy = c.y() - b[1];
                int dz = c.z() - b[2];
                if (dx * dx + dy * dy + dz * dz <= b[3] * b[3]) {
                    breached = true;
                }
            }
            // 机库后墙上的门
            boolean door = c.x() == doorX && Math.abs(c.z()) <= 1 && c.y() >= HANGAR_BOTTOM && c.y() <= HANGAR_BOTTOM + 2;
            Cell kept = c;
            // 灯大多坏了
            if (c.role() == Role.LIGHT && rng.chance(0.7)) {
                kept = new Cell(c.x(), c.y(), c.z(), Role.PLATE);
            }
            if (!breached && !door) {
                shell.add(kept);
            }
        }
        Exterior broken = new Exterior(List.copyOf(shell), hull.length(), hull.halfWidth(), hull.height(), hull.hangarMin(), hull.hangarMax());
        List<Cell> inside = new ArrayList<>();
        // 机库底是 y = 1 的船壳，里面的甲板也铺在 y = 1，人走在 y = 2，和机库一样高
        int floorY = HANGAR_BOTTOM - 1;
        int ceiling = floorY + 5;
        int fore = this.z0 + 3;
        int aft = this.z1 - 1;
        Set<Long> used = new HashSet<>();
        for (Cell c : shell) {
            used.add(key(c.x(), c.y(), c.z()));
        }
        // 舱室：每 8 格一道横舱壁，门一左一右错开；机库那一段不隔
        for (int z = fore; z <= aft; z++) {
            int hw = this.widthAt(z);
            boolean bulkhead = Math.floorMod(z, 8) == 0 && Math.abs(z) > HANGAR_HALF + 1;
            int door = (Math.floorDiv(z, 8) % 2 == 0 ? 1 : -1) * Math.max(1, hw / 2);
            for (int x = -hw + 1; x <= hw - 1; x++) {
                if (!this.interior(x, floorY, z)) {
                    continue;
                }
                this.add(inside, used, x, floorY, z, Role.FLOOR);
                if (this.interior(x, ceiling, z)) {
                    this.add(inside, used, x, ceiling, z, Role.PLATE);
                }
                if (bulkhead && Math.abs(x - door) > 1) {
                    for (int y = floorY + 1; y < ceiling; y++) {
                        if (this.interior(x, y, z)) {
                            this.add(inside, used, x, y, z, Role.WALL);
                        }
                    }
                }
            }
        }
        // 货箱：贴着船壳、不挡门的格子
        List<int[]> spots = new ArrayList<>();
        for (int z = fore + 1; z < aft; z++) {
            int hw = this.widthAt(z);
            for (int side = -1; side <= 1; side += 2) {
                int x = side * (hw - 1);
                if (Math.floorMod(z, 8) != 0 && this.interior(x, floorY + 1, z) && !used.contains(key(x, floorY + 1, z))) {
                    spots.add(new int[] {x, floorY + 1, z});
                }
            }
        }
        int crates = Math.min(spots.size(), rng.range(MIN_CRATES, MAX_CRATES));
        for (int i = 0; i < crates; i++) {
            int[] p = spots.remove(rng.nextInt(spots.size()));
            this.add(inside, used, p[0], p[1], p[2], Role.CRATE);
        }
        // 保险箱在最靠船头的那间
        for (int z = fore + 1; z < fore + 8; z++) {
            if (this.interior(0, floorY + 1, z) && !used.contains(key(0, floorY + 1, z))) {
                this.add(inside, used, 0, floorY + 1, z, Role.SAFE);
                break;
            }
        }
        // 漏电：天花板下面几处
        int hazards = rng.range(3, 5);
        for (int i = 0; i < hazards; i++) {
            int z = rng.range(fore + 2, aft - 2);
            if (Math.abs(z) <= HANGAR_HALF + 2) {
                continue;
            }
            int hw = this.widthAt(z);
            int x = rng.range(-hw + 2, hw - 2);
            if (this.interior(x, ceiling - 1, z)) {
                this.add(inside, used, x, ceiling - 1, z, Role.HAZARD);
            }
        }
        return new Derelict(broken, List.copyOf(inside));
    }

    /** 这一段船身的半宽（船头收窄）。 */
    private int widthAt(int z) {
        double t = Math.min(1, Math.max(0, (z - this.z0) / (double) BOW));
        return (int) Math.round(this.halfWidth * (0.3 + 0.7 * t));
    }

    /** 船体里面、不在船壳上的格子。 */
    private boolean interior(int x, int y, int z) {
        return this.solid(x, y, z) && !this.surface(x, y, z);
    }

    private void add(List<Cell> cells, Set<Long> used, int x, int y, int z, Role role) {
        if (used.add(key(x, y, z))) {
            cells.add(new Cell(x, y, z, role));
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    private Exterior build() {
        List<Cell> cells = new ArrayList<>();
        int top = this.height + 9;
        for (int z = this.z0 - 1; z <= this.z1 + 5; z++) {
            for (int y = -1; y <= top + 1; y++) {
                for (int x = -this.halfWidth - 1; x <= this.halfWidth + 1; x++) {
                    if (!this.solid(x, y, z) || !this.surface(x, y, z)) {
                        continue;
                    }
                    cells.add(new Cell(x, y, z, this.role(x, y, z)));
                }
            }
        }
        int[] min = {-this.halfWidth, HANGAR_BOTTOM, -HANGAR_HALF};
        int[] max = {-this.halfWidth + HANGAR_DEPTH - 1, HANGAR_TOP, HANGAR_HALF};
        return new Exterior(List.copyOf(cells), this.length, this.halfWidth, this.height, min, max);
    }

    /** 这一格在不在船体里。 */
    boolean solid(int x, int y, int z) {
        if (this.inHangar(x, y, z)) {
            return false;
        }
        if (z >= this.z0 && z <= this.z1) {
            double t = Math.min(1, (z - this.z0) / (double) BOW);
            int hw = this.widthAt(z);
            int h = (int) Math.round(this.height * (0.5 + 0.5 * t));
            if (Math.abs(x) <= hw && y >= 0 && y <= h) {
                return true;
            }
            // 背上的脊
            if (Math.abs(x) <= 3 && y > h && y <= h + 3 && z >= this.z0 + 8 && z <= this.z1 - 16) {
                return true;
            }
            // 舰桥塔
            if (Math.abs(x) <= 4 && y > this.height && y <= this.height + 9 && z >= this.z1 - 22 && z <= this.z1 - 12) {
                return true;
            }
            return false;
        }
        // 船尾的引擎：每台一个半径 3 的圆柱，伸出 4 格
        if (z > this.z1 && z <= this.z1 + 4) {
            for (int[] e : this.engines) {
                int dx = x - e[0];
                int dy = y - e[1];
                if (dx * dx + dy * dy <= 9) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean inHangar(int x, int y, int z) {
        return x >= -this.halfWidth - 1 && x <= -this.halfWidth + HANGAR_DEPTH - 1 && y >= HANGAR_BOTTOM && y <= HANGAR_TOP
                && Math.abs(z) <= HANGAR_HALF;
    }

    private boolean surface(int x, int y, int z) {
        return !this.solid(x + 1, y, z) || !this.solid(x - 1, y, z) || !this.solid(x, y + 1, z) || !this.solid(x, y - 1, z)
                || !this.solid(x, y, z + 1) || !this.solid(x, y, z - 1);
    }

    private Role role(int x, int y, int z) {
        // 引擎尾面发光
        if (z == this.z1 + 4) {
            return Role.ENGINE;
        }
        // 机库口的边框：凹口外沿一圈
        boolean nearMouth = x <= -this.halfWidth + 1 && x >= -this.halfWidth - 1;
        if (nearMouth && (Math.abs(z) == HANGAR_HALF + 1 && y >= HANGAR_BOTTOM - 1 && y <= HANGAR_TOP + 1
                || (y == HANGAR_BOTTOM - 1 || y == HANGAR_TOP + 1) && Math.abs(z) <= HANGAR_HALF + 1)) {
            return Role.FRAME;
        }
        // 机库里面的后墙也亮一点
        if (x == -this.halfWidth + HANGAR_DEPTH && y >= HANGAR_BOTTOM && y <= HANGAR_TOP && Math.abs(z) <= HANGAR_HALF
                && (y == HANGAR_BOTTOM || y == HANGAR_TOP)) {
            return Role.LIGHT;
        }
        // 舰桥的窗：塔的前面和两侧两行
        if (y >= this.height + 5 && y <= this.height + 6 && z >= this.z1 - 22 && z <= this.z1 - 12
                && (z == this.z1 - 22 || Math.abs(x) == 4)) {
            return Role.GLASS;
        }
        // 两侧的灯：每 8 格一盏
        if (Math.abs(x) >= this.halfWidth - 1 && y == this.height / 2 && Math.floorMod(z, 8) == 0) {
            return Role.LIGHT;
        }
        // 分段：每 6 格一道深色，船底也是深色
        if (Math.floorMod(z, 6) == 0 || y == 0) {
            return Role.PLATE;
        }
        return Role.HULL;
    }
}
