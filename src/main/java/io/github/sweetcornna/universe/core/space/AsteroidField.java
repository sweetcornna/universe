package io.github.sweetcornna.universe.core.space;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import org.jspecify.annotations.Nullable;

/**
 * 小行星（ADR-0017）。
 *
 * <p>每个恒星系有一两条小行星带：在相邻两颗行星的轨道之间（或最外侧行星的外面）绕恒星一圈，带宽几百格，上下 200 格以内，
 * 中间密、边上稀。太空按 {@link #CELL} 格的立方体分格，每格里有哪几颗小行星由恒星系种子和格子坐标定：同一格永远是同样几颗。
 * 适配层只在玩家附近生成实体，离开就删掉，回来再按种子生成（和无人深空一样"重生"）；打碎的那颗要过
 * {@link #REGROW_TICKS} 才重新出现。带外的太空里偶尔也有零散的一颗。
 *
 * <p>一颗小行星是 5–40 个方块拼成的一团（{@link Shape}），由自己的种子长出来：岩石的表面嵌着矿点，矿点的种类就是打碎后的产出。
 */
public final class AsteroidField {
    /** 分格的边长（格）。 */
    public static final int CELL = 128;
    /** 每格的候选点数：带正中间每个候选点都留下，所以每格最多这么多颗。 */
    static final int CANDIDATES = 6;
    /** 带外每个候选点留下的概率：每格平均约 0.05 颗。 */
    static final double SCATTER = 0.008;
    /** 带的内外边离天体（大气球）至少多远。 */
    static final double BODY_MARGIN = 400;
    /** 带的内边离恒星表面至少多远。 */
    static final double STAR_MARGIN = 800;
    /** 带宽、上下半厚的范围（格）。 */
    static final double MIN_WIDTH = 300;
    static final double MAX_WIDTH = 650;
    static final double MIN_HALF_HEIGHT = 90;
    static final double MAX_HALF_HEIGHT = 200;
    /** 打碎后多久才重新长出来（刻）：十分钟。 */
    public static final int REGROW_TICKS = 20 * 60 * 10;
    /**
     * 耐久 = 这个数 × 方块数^{@link #DURABILITY_EXPONENT}：比方块数涨得慢，大的不至于挖到天荒地老。采矿光束每台每刻打 1 点：
     * 两台光束挖小的一秒上下，中等的两到四秒，大的五到九秒（最大的要过热一次）。
     */
    public static final double DURABILITY_SCALE = 6;
    /** 小行星包围球半径的上限（最大的一百六十个方块也包得住）。 */
    public static final double MAX_RADIUS = 10;
    public static final double DURABILITY_EXPONENT = 0.8;
    /** 生成的小行星离天体的大气球、离恒星表面至少多远。 */
    static final double SPAWN_BODY_CLEARANCE = 80;
    static final double SPAWN_STAR_CLEARANCE = 300;

    private AsteroidField() {
    }

    // ---- 小行星带

    /**
     * 一条小行星带：绕恒星（原点）的圆环，中心半径 {@code radius}，宽 {@code width}，黄道面上下各 {@code halfHeight}。
     */
    public record Belt(double radius, double width, double halfHeight) {
        public double inner() {
            return this.radius - this.width / 2;
        }

        public double outer() {
            return this.radius + this.width / 2;
        }

        /** 这一点的密度（0–1）：带的正中间是 1，往内外两边、往上下渐稀，出了带是 0。 */
        public double density(double x, double y, double z) {
            double r = Math.sqrt(x * x + z * z);
            double u = Math.abs(r - this.radius) / (this.width / 2);
            double v = Math.abs(y) / this.halfHeight;
            if (u >= 1 || v >= 1) {
                return 0;
            }
            return (1 - u * u) * (1 - v * v);
        }

        public boolean contains(double x, double y, double z) {
            return this.density(x, y, z) > 0;
        }

        /** 带里离这一点最近的地方：方位角不变，半径和高度夹进带里（在带里就是这一点自己）。 */
        public double[] nearestPoint(double x, double y, double z) {
            double r = Math.sqrt(x * x + z * z);
            double clamped = Math.max(this.inner(), Math.min(this.outer(), r));
            double cy = Math.max(-this.halfHeight, Math.min(this.halfHeight, y));
            if (r < 1e-6) {
                return new double[] {clamped, cy, 0};
            }
            return new double[] {x / r * clamped, cy, z / r * clamped};
        }

        public double distanceTo(double x, double y, double z) {
            double[] p = this.nearestPoint(x, y, z);
            double dx = p[0] - x;
            double dy = p[1] - y;
            double dz = p[2] - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * 恒星系的小行星带（一两条）：在天体之间的空隙里挑，带的内外边离每个天体的大气球（连卫星）至少 {@link #BODY_MARGIN} 格。
     * 最外侧天体的外面也算一个空隙。
     */
    public static List<Belt> belts(StarSystem system) {
        // 天体占掉的半径区间：离恒星的距离加减大气球半径，再留出余量
        List<double[]> taken = new ArrayList<>();
        double outermost = SpaceLayout.starRadius(system) + STAR_MARGIN;
        for (SpaceLayout.Body body : SpaceLayout.of(system)) {
            double d = Math.sqrt(body.x() * body.x() + body.z() * body.z());
            double reach = body.radius() + BODY_MARGIN;
            taken.add(new double[] {d - reach, d + reach});
            outermost = Math.max(outermost, d + reach);
        }
        taken.sort((a, b) -> Double.compare(a[0], b[0]));
        List<double[]> gaps = new ArrayList<>();
        double from = SpaceLayout.starRadius(system) + STAR_MARGIN;
        for (double[] band : taken) {
            if (band[0] - from >= MIN_WIDTH) {
                gaps.add(new double[] {from, band[0]});
            }
            from = Math.max(from, band[1]);
        }
        gaps.add(new double[] {Math.max(from, outermost), Math.max(from, outermost) + 2500});

        Rng rng = Rng.of(Seeds.derive(system.seed(), "asteroid_belts"));
        int count = Math.min(gaps.size(), rng.chance(0.55) ? 2 : 1);
        List<double[]> pool = new ArrayList<>(gaps);
        List<Belt> belts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double[] gap = pool.remove(rng.nextInt(pool.size()));
            double room = gap[1] - gap[0];
            double width = Math.min(rng.range(MIN_WIDTH, MAX_WIDTH), room);
            double radius = gap[0] + width / 2 + rng.nextDouble() * (room - width);
            double halfHeight = rng.range(MIN_HALF_HEIGHT, MAX_HALF_HEIGHT);
            belts.add(new Belt(radius, width, halfHeight));
        }
        belts.sort((a, b) -> Double.compare(a.radius(), b.radius()));
        return List.copyOf(belts);
    }

    /** 这一点的小行星密度（0–1）：几条带里最大的那个。 */
    public static double density(List<Belt> belts, double x, double y, double z) {
        double best = 0;
        for (Belt belt : belts) {
            best = Math.max(best, belt.density(x, y, z));
        }
        return best;
    }

    /** 离这一点最近的小行星带；没有带时返回 null。 */
    public static Belt nearest(List<Belt> belts, double x, double y, double z) {
        Belt best = null;
        double distance = Double.MAX_VALUE;
        for (Belt belt : belts) {
            double d = belt.distanceTo(x, y, z);
            if (d < distance) {
                distance = d;
                best = belt;
            }
        }
        return best;
    }

    // ---- 分格

    /**
     * 一颗小行星在太空里的位置和种子。
     *
     * @param index 它在格子里的序号：和格子坐标一起是它的身份
     */
    public record Slot(int cx, int cy, int cz, int index, double x, double y, double z, long seed) {
        /** 身份压成一个 long：打碎后按它记冷却、生成时按它查重。 */
        public long key() {
            return key(this.cx, this.cy, this.cz, this.index);
        }

        public static long key(int cx, int cy, int cz, int index) {
            return ((long) (cx & 0x1FFFFF) << 43) | ((long) (cz & 0x1FFFFF) << 22) | ((long) (cy & 0xFF) << 4) | (index & 0xF);
        }
    }

    /** 格子 ({@code cx}, {@code cy}, {@code cz}) 里的小行星：每个候选点按那里的密度（带外按 {@link #SCATTER}）决定留不留。 */
    public static List<Slot> cell(long systemSeed, List<Belt> belts, int cx, int cy, int cz) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(Seeds.derive(Seeds.derive(systemSeed, "asteroids"), cx), cy), cz));
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < CANDIDATES; i++) {
            double x = (cx + rng.nextDouble()) * CELL;
            double y = (cy + rng.nextDouble()) * CELL;
            double z = (cz + rng.nextDouble()) * CELL;
            long seed = rng.nextLong();
            double roll = rng.nextDouble();
            if (roll < Math.max(SCATTER, density(belts, x, y, z))) {
                slots.add(new Slot(cx, cy, cz, i, x, y, z, seed));
            }
        }
        return slots;
    }

    /** 这一点能不能放小行星：离每个大气球 {@link #SPAWN_BODY_CLEARANCE} 格、离恒星表面 {@link #SPAWN_STAR_CLEARANCE} 格以外。 */
    public static boolean clear(List<SpaceLayout.Body> bodies, double starRadius, double x, double y, double z) {
        if (Math.sqrt(x * x + y * y + z * z) < starRadius + SPAWN_STAR_CLEARANCE) {
            return false;
        }
        for (SpaceLayout.Body body : bodies) {
            if (body.distanceTo(x, y, z) < body.radius() + SPAWN_BODY_CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    // ---- 一颗小行星

    /** 大小：方块数的范围。 */
    public enum Size {
        SMALL(8, 18),
        MEDIUM(24, 60),
        LARGE(80, 160);

        private final int min;
        private final int max;

        Size(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int min() {
            return this.min;
        }

        public int max() {
            return this.max;
        }
    }

    /** 方块的材质：两种岩石，四种常见矿，三种稀有矿。 */
    public enum Material {
        ROCK,
        DARK_ROCK,
        IRON,
        COPPER,
        SILICON,
        TITANIUM,
        GOLD,
        OPTICAL,
        TITANIUM_CRYSTAL;

        public boolean ore() {
            return this != ROCK && this != DARK_ROCK;
        }

        public boolean rare() {
            return this == GOLD || this == OPTICAL || this == TITANIUM_CRYSTAL;
        }
    }

    /** 一个方块：格子坐标（方块的角）和材质。 */
    public record Voxel(int x, int y, int z, Material material) {
    }

    /**
     * 一颗小行星的样子。
     *
     * @param center   几何中心（方块中心的平均），自转绕它转
     * @param radius   从中心到最远的方块外角的距离（格）
     * @param spinAxis 自转轴（单位向量）
     * @param spinRate 每刻转多少度
     */
    public record Shape(long seed, Size size, List<Voxel> voxels, double[] center, double radius, double[] spinAxis, double spinRate) {
        public double durability() {
            return DURABILITY_SCALE * Math.pow(this.voxels.size(), DURABILITY_EXPONENT);
        }

        /** 每种矿有几个矿点（按材质的顺序）。 */
        public Map<Material, Integer> ores() {
            Map<Material, Integer> counts = new EnumMap<>(Material.class);
            for (Voxel voxel : this.voxels) {
                if (voxel.material().ore()) {
                    counts.merge(voxel.material(), 1, Integer::sum);
                }
            }
            return counts;
        }
    }

    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /**
     * 由种子长出一颗小行星：先定大小，从一个方块开始，每次在已有方块的空邻居里挑一个加上（离中心越近、贴着的方块越多越容易被挑中，
     * 每个方向的伸展程度不同，所以有圆有扁有长）；再定岩石和矿：一种主矿，一半有副矿，一成多有一种稀有矿，矿点嵌在表面。
     */
    public static Shape shape(long seed) {
        Rng rng = Rng.of(seed);
        double sizeRoll = rng.nextDouble();
        Size size = sizeRoll < 0.5 ? Size.SMALL : sizeRoll < 0.85 ? Size.MEDIUM : Size.LARGE;
        int n = rng.range(size.min(), size.max());
        double[] stretch = {rng.range(0.7, 1.4), rng.range(0.6, 1.1), rng.range(0.7, 1.4)};
        double spread = Math.cbrt(n) * 0.9;

        List<int[]> cells = new ArrayList<>();
        Set<Long> filled = new HashSet<>();
        cells.add(new int[] {0, 0, 0});
        filled.add(pack(0, 0, 0));
        while (cells.size() < n) {
            List<int[]> frontier = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            double total = 0;
            for (int[] c : cells) {
                for (int[] d : NEIGHBORS) {
                    int x = c[0] + d[0];
                    int y = c[1] + d[1];
                    int z = c[2] + d[2];
                    long key = pack(x, y, z);
                    if (filled.contains(key) || !seen.add(key)) {
                        continue;
                    }
                    int touching = 0;
                    for (int[] e : NEIGHBORS) {
                        if (filled.contains(pack(x + e[0], y + e[1], z + e[2]))) {
                            touching++;
                        }
                    }
                    double sx = x / stretch[0];
                    double sy = y / stretch[1];
                    double sz = z / stretch[2];
                    double w = touching * touching * Math.exp(-(sx * sx + sy * sy + sz * sz) / (spread * spread));
                    frontier.add(new int[] {x, y, z});
                    weights.add(w);
                    total += w;
                }
            }
            double pick = rng.nextDouble() * total;
            int chosen = frontier.size() - 1;
            for (int i = 0; i < frontier.size(); i++) {
                pick -= weights.get(i);
                if (pick <= 0) {
                    chosen = i;
                    break;
                }
            }
            int[] c = frontier.get(chosen);
            cells.add(c);
            filled.add(pack(c[0], c[1], c[2]));
        }

        // 材质
        Material rock = rng.chance(0.3) ? Material.DARK_ROCK : Material.ROCK;
        Material[] common = {Material.IRON, Material.COPPER, Material.SILICON, Material.TITANIUM};
        double[] commonWeights = {0.35, 0.25, 0.2, 0.2};
        Material primary = common[pickWeighted(rng, commonWeights)];
        Material secondary = null;
        if (rng.chance(0.5)) {
            do {
                secondary = common[pickWeighted(rng, commonWeights)];
            } while (secondary == primary);
        }
        Material rare = null;
        if (rng.chance(0.13)) {
            Material[] rares = {Material.GOLD, Material.OPTICAL, Material.TITANIUM_CRYSTAL};
            rare = rares[pickWeighted(rng, new double[] {0.5, 0.3, 0.2})];
        }
        // 矿点放在表面（至少一面露在外面）的方块上，打乱顺序后依次分给主矿、副矿、稀有矿
        List<Integer> surface = new ArrayList<>();
        List<Integer> inner = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            boolean exposed = false;
            for (int[] d : NEIGHBORS) {
                if (!filled.contains(pack(c[0] + d[0], c[1] + d[1], c[2] + d[2]))) {
                    exposed = true;
                    break;
                }
            }
            (exposed ? surface : inner).add(i);
        }
        shuffle(surface, rng);
        shuffle(inner, rng);
        List<Integer> order = new ArrayList<>(surface);
        order.addAll(inner);
        Material[] materials = new Material[n];
        Arrays.fill(materials, rock);
        int next = 0;
        next = assign(materials, order, next, primary, Math.max(1, (int) Math.round(n * 0.22)));
        if (secondary != null) {
            next = assign(materials, order, next, secondary, Math.max(1, (int) Math.round(n * 0.1)));
        }
        if (rare != null) {
            assign(materials, order, next, rare, Math.max(1, (int) Math.round(n * 0.07)));
        }

        double[] center = new double[3];
        for (int[] c : cells) {
            center[0] += c[0] + 0.5;
            center[1] += c[1] + 0.5;
            center[2] += c[2] + 0.5;
        }
        center[0] /= n;
        center[1] /= n;
        center[2] /= n;
        double radius = 0;
        List<Voxel> voxels = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            voxels.add(new Voxel(c[0], c[1], c[2], materials[i]));
            double dx = Math.abs(c[0] + 0.5 - center[0]) + 0.5;
            double dy = Math.abs(c[1] + 0.5 - center[1]) + 0.5;
            double dz = Math.abs(c[2] + 0.5 - center[2]) + 0.5;
            radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        double ax = rng.gaussian();
        double ay = rng.gaussian();
        double az = rng.gaussian();
        double length = Math.max(1e-9, Math.sqrt(ax * ax + ay * ay + az * az));
        double[] axis = {ax / length, ay / length, az / length};
        // 小的转得快，大的转得慢
        double rate = rng.range(0.3, 1.2) / Math.sqrt(n / 8.0) * (rng.chance(0.5) ? 1 : -1);
        return new Shape(seed, size, List.copyOf(voxels), center, radius, axis, rate);
    }

    private static int assign(Material[] materials, List<Integer> order, int next, Material material, int count) {
        for (int k = 0; k < count && next < order.size(); k++) {
            materials[order.get(next++)] = material;
        }
        return next;
    }

    private static int pickWeighted(Rng rng, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double pick = rng.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private static void shuffle(List<Integer> list, Rng rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, rng.nextInt(i + 1));
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
    }

    // ---- 产出

    /** 一份产出：哪种矿、几个。 */
    public record Yield(Material material, int count) {
    }

    /** 打碎后的产出（按材质的顺序）：常见矿每个矿点 2–3 个，稀有矿每个矿点 1–2 个，岩石不出东西。 */
    /**
     * 碰撞：这一刻从 {@code start} 移动了 {@code moved}，有没有撞进以 {@code center} 为心、{@code reach} 为半径的球。
     * 一开始就在球里的，从移动后的位置往外推；不然取线段进球的那一点（高速擦过也算，法线和速度相对，撞得多狠看法线方向的速度）。
     *
     * @return 撞上了就是球面上的法线（单位向量），没撞上为空
     */
    public static double @Nullable [] contact(double[] start, double[] moved, double[] center, double reach) {
        double[] rel = {start[0] - center[0], start[1] - center[1], start[2] - center[2]};
        double a = moved[0] * moved[0] + moved[1] * moved[1] + moved[2] * moved[2];
        double b = rel[0] * moved[0] + rel[1] * moved[1] + rel[2] * moved[2];
        double c = rel[0] * rel[0] + rel[1] * rel[1] + rel[2] * rel[2] - reach * reach;
        double[] point;
        if (c < 0) {
            point = new double[] {start[0] + moved[0], start[1] + moved[1], start[2] + moved[2]};
            double[] off = {point[0] - center[0], point[1] - center[1], point[2] - center[2]};
            double d = Math.sqrt(off[0] * off[0] + off[1] * off[1] + off[2] * off[2]);
            if (d >= reach) {
                return null;
            }
            if (d > 1e-6) {
                return new double[] {off[0] / d, off[1] / d, off[2] / d};
            }
            if (a > 1e-12) {
                double m = Math.sqrt(a);
                return new double[] {-moved[0] / m, -moved[1] / m, -moved[2] / m};
            }
            return new double[] {0, 1, 0};
        }
        if (a < 1e-12) {
            return null;
        }
        double disc = b * b - a * c;
        if (disc < 0) {
            return null;
        }
        double t = (-b - Math.sqrt(disc)) / a;
        if (t < 0 || t > 1) {
            return null;
        }
        double[] n = {rel[0] + moved[0] * t, rel[1] + moved[1] * t, rel[2] + moved[2] * t};
        double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        return new double[] {n[0] / len, n[1] / len, n[2] / len};
    }

    public static List<Yield> yield(Shape shape, Rng rng) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (Voxel voxel : shape.voxels()) {
            Material material = voxel.material();
            if (material.ore()) {
                counts.merge(material, material.rare() ? 1 : rng.range(1, 2), Integer::sum);
            }
        }
        List<Yield> yields = new ArrayList<>();
        counts.forEach((material, count) -> yields.add(new Yield(material, count)));
        return yields;
    }
}
