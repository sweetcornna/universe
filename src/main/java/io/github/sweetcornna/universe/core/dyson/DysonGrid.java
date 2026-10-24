package io.github.sweetcornna.universe.core.dyson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 戴森球一层的网格（ADR-0016）：节点、框架、球壳，由模板和密度算出来，服务端和客户端算出的一样。
 *
 * <p>坐标是层自己的单位球，y 轴是层的极轴，纬度从赤道算。节点按离赤道的远近、再按经度排好，框架和球壳按它们
 * 最靠极的那个节点排，所以建造顺序从赤道往两极铺。球壳的顶点按逆时针（从球外看）排，相邻两个顶点之间就是一段框架。
 */
public final class DysonGrid {
    /** 网格模板。 */
    public enum Template {
        /** 二十面体细分的三角网。 */
        GEODESIC(4),
        /** 测地网的对偶：五边形和六边形。 */
        HEXAGONAL(3),
        /** 经线和纬线围成的方格，两极是三角形。 */
        LATITUDE(4);

        private final int maxDensity;

        Template(int maxDensity) {
            this.maxDensity = maxDensity;
        }

        public int maxDensity() {
            return this.maxDensity;
        }

        public static Template byOrdinal(int ordinal) {
            Template[] values = values();
            return values[Math.floorMod(ordinal, values.length)];
        }
    }

    private static final Map<Template, DysonGrid[]> CACHE = new EnumMap<>(Template.class);

    private final Template template;
    private final int density;
    private final double[][] nodes;
    private final double[] latitudes;
    private final int[][] frames;
    private final double[] frameArcs;
    private final int[][] shells;
    private final int[][] shellFrames;
    private final double[] shellAreas;
    private final int[][] nodeShells;
    /** 玩家改过的网格（ADR-0030 §四）：不按模板算，元素的顺序就是给定的顺序。 */
    private final boolean custom;
    /** 球壳剖成的三角形和凸不凸，画面用，第一次要的时候算。 */
    private int[][] triangles;
    private boolean[] convex;

    /**
     * 自定义网格：节点、框架、球壳照给的顺序（不重新排），球壳转成从球外看逆时针。下标不对、球壳的边不是框架时抛
     * {@link IllegalArgumentException}；几何上合不合法由 {@link DysonDesign#validate()} 管。
     */
    private DysonGrid(Template template, int density, double[][] nodes, int[][] frames, int[][] shells) {
        this.template = template;
        this.density = density;
        this.custom = true;
        int n = nodes.length;
        this.nodes = new double[n][];
        this.latitudes = new double[n];
        for (int i = 0; i < n; i++) {
            this.nodes[i] = nodes[i].clone();
            this.latitudes[i] = Math.toDegrees(Math.asin(clamp(this.nodes[i][1])));
        }
        this.frames = new int[frames.length][];
        this.frameArcs = new double[frames.length];
        Map<Long, Integer> frameIndex = new HashMap<>();
        for (int i = 0; i < frames.length; i++) {
            int a = frames[i][0];
            int b = frames[i][1];
            if (a < 0 || b < 0 || a >= n || b >= n || a == b || frameIndex.put(edgeKey(a, b), i) != null) {
                throw new IllegalArgumentException("bad frame " + i);
            }
            this.frames[i] = new int[] {Math.min(a, b), Math.max(a, b)};
            this.frameArcs[i] = Math.acos(clamp(dot(this.nodes[a], this.nodes[b])));
        }
        this.shells = new int[shells.length][];
        this.shellFrames = new int[shells.length][];
        this.shellAreas = new double[shells.length];
        List<List<Integer>> byNode = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byNode.add(new ArrayList<>());
        }
        for (int s = 0; s < shells.length; s++) {
            int[] shell = shells[s].clone();
            if (shell.length < 3) {
                throw new IllegalArgumentException("bad shell " + s);
            }
            double[][] points = new double[shell.length][];
            for (int k = 0; k < shell.length; k++) {
                if (shell[k] < 0 || shell[k] >= n) {
                    throw new IllegalArgumentException("bad shell " + s);
                }
                points[k] = this.nodes[shell[k]];
            }
            double area = SphereGeometry.signedArea(points);
            if (area < 0) {
                for (int k = 0; k < shell.length / 2; k++) {
                    int t = shell[k];
                    shell[k] = shell[shell.length - 1 - k];
                    shell[shell.length - 1 - k] = t;
                }
            }
            this.shells[s] = shell;
            this.shellAreas[s] = Math.abs(area);
            this.shellFrames[s] = new int[shell.length];
            for (int k = 0; k < shell.length; k++) {
                Integer frame = frameIndex.get(edgeKey(shell[k], shell[(k + 1) % shell.length]));
                if (frame == null) {
                    throw new IllegalArgumentException("shell " + s + " edge is not a frame");
                }
                this.shellFrames[s][k] = frame;
                byNode.get(shell[k]).add(s);
            }
        }
        this.nodeShells = new int[n][];
        for (int i = 0; i < n; i++) {
            this.nodeShells[i] = byNode.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /**
     * 自定义网格。
     *
     * @param template 从哪个模板改来的（界面上显示、存档里记着；空网格随便）
     */
    public static DysonGrid custom(Template template, int density, double[][] nodes, int[][] frames, int[][] shells) {
        return new DysonGrid(template, Math.max(1, Math.min(template.maxDensity(), density)), nodes, frames, shells);
    }

    private DysonGrid(Template template, int density, List<double[]> rawNodes, List<int[]> rawShells) {
        this.template = template;
        this.density = density;
        this.custom = false;
        // 节点按离赤道的远近排，同一纬度按经度
        int n = rawNodes.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.<Integer>comparingDouble(i -> round(Math.abs(rawNodes.get(i)[1])))
                .thenComparingDouble(i -> -rawNodes.get(i)[1])
                .thenComparingDouble(i -> longitude(rawNodes.get(i))));
        int[] newIndex = new int[n];
        this.nodes = new double[n][];
        this.latitudes = new double[n];
        for (int i = 0; i < n; i++) {
            newIndex[order[i]] = i;
            this.nodes[i] = rawNodes.get(order[i]);
            this.latitudes[i] = Math.toDegrees(Math.asin(clamp(this.nodes[i][1])));
        }
        List<int[]> polygons = new ArrayList<>(rawShells.size());
        for (int[] shell : rawShells) {
            int[] mapped = new int[shell.length];
            for (int k = 0; k < shell.length; k++) {
                mapped[k] = newIndex[shell[k]];
            }
            polygons.add(this.outward(mapped));
        }
        // 框架是球壳的边，去重
        Map<Long, int[]> edges = new HashMap<>();
        for (int[] shell : polygons) {
            for (int k = 0; k < shell.length; k++) {
                int a = shell[k];
                int b = shell[(k + 1) % shell.length];
                edges.putIfAbsent(edgeKey(a, b), new int[] {Math.min(a, b), Math.max(a, b)});
            }
        }
        List<int[]> frameList = new ArrayList<>(edges.values());
        frameList.sort(Comparator.<int[]>comparingDouble(f -> this.reach(f))
                .thenComparingDouble(f -> -(this.nodes[f[0]][1] + this.nodes[f[1]][1]))
                .thenComparingDouble(f -> longitude(midpoint(this.nodes[f[0]], this.nodes[f[1]])))
                .thenComparingInt(f -> f[0]).thenComparingInt(f -> f[1]));
        this.frames = frameList.toArray(new int[0][]);
        this.frameArcs = new double[this.frames.length];
        Map<Long, Integer> frameIndex = new HashMap<>();
        for (int i = 0; i < this.frames.length; i++) {
            this.frameArcs[i] = Math.acos(clamp(dot(this.nodes[this.frames[i][0]], this.nodes[this.frames[i][1]])));
            frameIndex.put(edgeKey(this.frames[i][0], this.frames[i][1]), i);
        }
        polygons.sort(Comparator.<int[]>comparingDouble(this::reach)
                .thenComparingDouble(s -> -this.centroid(s)[1])
                .thenComparingDouble(s -> longitude(this.centroid(s))));
        this.shells = polygons.toArray(new int[0][]);
        this.shellFrames = new int[this.shells.length][];
        this.shellAreas = new double[this.shells.length];
        List<List<Integer>> byNode = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byNode.add(new ArrayList<>());
        }
        for (int s = 0; s < this.shells.length; s++) {
            int[] shell = this.shells[s];
            this.shellFrames[s] = new int[shell.length];
            for (int k = 0; k < shell.length; k++) {
                this.shellFrames[s][k] = frameIndex.get(edgeKey(shell[k], shell[(k + 1) % shell.length]));
                byNode.get(shell[k]).add(s);
            }
            this.shellAreas[s] = this.area(shell);
        }
        this.nodeShells = new int[n][];
        for (int i = 0; i < n; i++) {
            this.nodeShells[i] = byNode.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** 模板和密度对应的网格，算一次以后缓存起来；密度超出范围时取最近的。 */
    public static DysonGrid of(Template template, int density) {
        int d = Math.max(1, Math.min(template.maxDensity(), density));
        synchronized (CACHE) {
            DysonGrid[] grids = CACHE.computeIfAbsent(template, key -> new DysonGrid[key.maxDensity() + 1]);
            if (grids[d] == null) {
                grids[d] = switch (template) {
                    case GEODESIC -> geodesic(d);
                    case HEXAGONAL -> hexagonal(d);
                    case LATITUDE -> latitudeGrid(d);
                };
            }
            return grids[d];
        }
    }

    public Template template() {
        return this.template;
    }

    public int density() {
        return this.density;
    }

    /** 玩家改过（不是模板算出来的）。 */
    public boolean custom() {
        return this.custom;
    }

    /** 球壳剖成的三角形：节点下标，每三个一组。 */
    public int[] shellTriangles(int shell) {
        this.triangulate();
        return this.triangles[shell];
    }

    /** 球壳是凸的（可以从中心扇形地画）。 */
    public boolean shellConvex(int shell) {
        this.triangulate();
        return this.convex[shell];
    }

    private synchronized void triangulate() {
        if (this.triangles != null) {
            return;
        }
        int[][] all = new int[this.shells.length][];
        boolean[] flags = new boolean[this.shells.length];
        for (int s = 0; s < this.shells.length; s++) {
            int[] shell = this.shells[s];
            double[][] points = new double[shell.length][];
            for (int k = 0; k < shell.length; k++) {
                points[k] = this.nodes[shell[k]];
            }
            double[][] flat = SphereGeometry.gnomonic(SphereGeometry.centroid(points), points);
            int[] local;
            if (flat == null) {
                local = new int[(shell.length - 2) * 3];
                for (int k = 1; k + 1 < shell.length; k++) {
                    local[(k - 1) * 3] = 0;
                    local[(k - 1) * 3 + 1] = k;
                    local[(k - 1) * 3 + 2] = k + 1;
                }
                flags[s] = true;
            } else {
                local = SphereGeometry.triangulate(flat);
                flags[s] = SphereGeometry.convex(flat);
            }
            int[] mapped = new int[local.length];
            for (int k = 0; k < local.length; k++) {
                mapped[k] = shell[local[k]];
            }
            all[s] = mapped;
        }
        this.convex = flags;
        this.triangles = all;
    }

    public int nodeCount() {
        return this.nodes.length;
    }

    public int frameCount() {
        return this.frames.length;
    }

    public int shellCount() {
        return this.shells.length;
    }

    /** 节点在单位球上的位置（不要改）。 */
    public double[] node(int index) {
        return this.nodes[index];
    }

    /** 节点的纬度（度）。 */
    public double latitude(int node) {
        return this.latitudes[node];
    }

    /** 框架两端的节点。 */
    public int[] frame(int index) {
        return this.frames[index];
    }

    /** 框架在单位球上的弧长（弧度）。 */
    public double frameArc(int index) {
        return this.frameArcs[index];
    }

    /** 球壳的顶点，从球外看逆时针。 */
    public int[] shell(int index) {
        return this.shells[index];
    }

    /** 球壳的边对应的框架，第 k 条是顶点 k 到 k+1。 */
    public int[] shellFrames(int index) {
        return this.shellFrames[index];
    }

    /** 球壳在单位球上的面积（球面度）。 */
    public double shellArea(int index) {
        return this.shellAreas[index];
    }

    /** 以这个节点为顶点的球壳。 */
    public int[] nodeShells(int node) {
        return this.nodeShells[node];
    }

    /** 节点在纬度限制以内。 */
    public boolean nodeWithin(int node, double limit) {
        return Math.abs(this.latitudes[node]) <= limit + 1e-6;
    }

    public boolean frameWithin(int frame, double limit) {
        return this.nodeWithin(this.frames[frame][0], limit) && this.nodeWithin(this.frames[frame][1], limit);
    }

    public boolean shellWithin(int shell, double limit) {
        for (int node : this.shells[shell]) {
            if (!this.nodeWithin(node, limit)) {
                return false;
            }
        }
        return true;
    }

    // ---- 三种模板 ----

    /** 极轴朝上的二十面体：两极各一个顶点，中间两圈各五个，在 ±26.57°。 */
    private static void icosahedron(List<double[]> vertices, List<int[]> faces) {
        double lat = Math.atan(0.5);
        vertices.add(new double[] {0, 1, 0});
        for (int k = 0; k < 5; k++) {
            double lon = 2 * Math.PI * k / 5;
            vertices.add(new double[] {Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)});
        }
        for (int k = 0; k < 5; k++) {
            double lon = 2 * Math.PI * k / 5 + Math.PI / 5;
            vertices.add(new double[] {Math.cos(lat) * Math.cos(lon), -Math.sin(lat), Math.cos(lat) * Math.sin(lon)});
        }
        vertices.add(new double[] {0, -1, 0});
        for (int k = 0; k < 5; k++) {
            int u = 1 + k;
            int u1 = 1 + (k + 1) % 5;
            int l = 6 + k;
            int l1 = 6 + (k + 1) % 5;
            faces.add(new int[] {0, u, u1});
            faces.add(new int[] {u, l, u1});
            faces.add(new int[] {u1, l, l1});
            faces.add(new int[] {11, l1, l});
        }
    }

    /** 测地网的顶点和三角形（未排序），对偶的蜂窝网也用它。 */
    private static void geodesicRaw(int frequency, List<double[]> nodes, List<int[]> triangles) {
        List<double[]> base = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();
        icosahedron(base, faces);
        Map<String, Integer> index = new HashMap<>();
        for (int[] face : faces) {
            double[] a = base.get(face[0]);
            double[] b = base.get(face[1]);
            double[] c = base.get(face[2]);
            int[][] grid = new int[frequency + 1][];
            for (int i = 0; i <= frequency; i++) {
                grid[i] = new int[frequency + 1 - i];
                for (int j = 0; j <= frequency - i; j++) {
                    double u = (double) i / frequency;
                    double v = (double) j / frequency;
                    double[] p = normalize(new double[] {
                            a[0] + (b[0] - a[0]) * u + (c[0] - a[0]) * v,
                            a[1] + (b[1] - a[1]) * u + (c[1] - a[1]) * v,
                            a[2] + (b[2] - a[2]) * u + (c[2] - a[2]) * v});
                    String key = key(p);
                    Integer existing = index.get(key);
                    if (existing == null) {
                        existing = nodes.size();
                        nodes.add(p);
                        index.put(key, existing);
                    }
                    grid[i][j] = existing;
                }
            }
            for (int i = 0; i < frequency; i++) {
                for (int j = 0; j < frequency - i; j++) {
                    triangles.add(new int[] {grid[i][j], grid[i + 1][j], grid[i][j + 1]});
                    if (j + 1 <= frequency - i - 1) {
                        triangles.add(new int[] {grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]});
                    }
                }
            }
        }
    }

    private static DysonGrid geodesic(int frequency) {
        List<double[]> nodes = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();
        geodesicRaw(frequency, nodes, triangles);
        return new DysonGrid(Template.GEODESIC, frequency, nodes, triangles);
    }

    /** 测地网的对偶：每个三角形的中心是一个节点，每个测地网顶点周围的一圈三角形围成一片壳。 */
    private static DysonGrid hexagonal(int frequency) {
        List<double[]> vertices = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();
        geodesicRaw(frequency, vertices, triangles);
        List<double[]> nodes = new ArrayList<>(triangles.size());
        List<List<Integer>> around = new ArrayList<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            around.add(new ArrayList<>());
        }
        for (int t = 0; t < triangles.size(); t++) {
            int[] tri = triangles.get(t);
            double[] a = vertices.get(tri[0]);
            double[] b = vertices.get(tri[1]);
            double[] c = vertices.get(tri[2]);
            nodes.add(normalize(new double[] {a[0] + b[0] + c[0], a[1] + b[1] + c[1], a[2] + b[2] + c[2]}));
            for (int v : tri) {
                around.get(v).add(t);
            }
        }
        List<int[]> shells = new ArrayList<>(vertices.size());
        for (int v = 0; v < vertices.size(); v++) {
            double[] center = vertices.get(v);
            double[] east = tangent(center);
            double[] north = cross(center, east);
            List<Integer> ring = around.get(v);
            ring.sort(Comparator.comparingDouble(t -> {
                double[] p = nodes.get(t);
                return Math.atan2(dot(p, north), dot(p, east));
            }));
            shells.add(ring.stream().mapToInt(Integer::intValue).toArray());
        }
        return new DysonGrid(Template.HEXAGONAL, frequency, nodes, shells);
    }

    /** 经纬网：2d+1 圈纬线（含赤道）等分纬度，4d+4 条经线，加两极。 */
    private static DysonGrid latitudeGrid(int density) {
        int rings = 2 * density + 1;
        int meridians = 4 * density + 4;
        List<double[]> nodes = new ArrayList<>();
        for (int r = 0; r < rings; r++) {
            double lat = Math.toRadians(90 - 180.0 * (r + 1) / (rings + 1));
            for (int m = 0; m < meridians; m++) {
                double lon = 2 * Math.PI * m / meridians;
                nodes.add(new double[] {Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)});
            }
        }
        int north = nodes.size();
        nodes.add(new double[] {0, 1, 0});
        int south = nodes.size();
        nodes.add(new double[] {0, -1, 0});
        List<int[]> shells = new ArrayList<>();
        for (int m = 0; m < meridians; m++) {
            int m1 = (m + 1) % meridians;
            shells.add(new int[] {north, m, m1});
            for (int r = 0; r + 1 < rings; r++) {
                shells.add(new int[] {r * meridians + m, (r + 1) * meridians + m, (r + 1) * meridians + m1, r * meridians + m1});
            }
            int last = (rings - 1) * meridians;
            shells.add(new int[] {south, last + m1, last + m});
        }
        return new DysonGrid(Template.LATITUDE, density, nodes, shells);
    }

    // ---- 几何 ----

    /** 让顶点从球外看是逆时针。 */
    private int[] outward(int[] shell) {
        double[] c = this.centroid(shell);
        double[] a = this.nodes[shell[0]];
        double[] b = this.nodes[shell[1]];
        double[] d = this.nodes[shell[2]];
        double[] normal = cross(sub(b, a), sub(d, b));
        if (dot(normal, c) >= 0) {
            return shell;
        }
        int[] reversed = new int[shell.length];
        for (int k = 0; k < shell.length; k++) {
            reversed[k] = shell[shell.length - 1 - k];
        }
        return reversed;
    }

    private double[] centroid(int[] shell) {
        double[] c = new double[3];
        for (int node : shell) {
            double[] p = this.nodes[node];
            c[0] += p[0];
            c[1] += p[1];
            c[2] += p[2];
        }
        return normalize(c);
    }

    /** 最靠极的顶点离赤道多远，排序用。 */
    private double reach(int[] nodes) {
        double max = 0;
        for (int node : nodes) {
            max = Math.max(max, round(Math.abs(this.nodes[node][1])));
        }
        return max;
    }

    /** 球面多边形的面积：从第一个顶点扇形分成三角形，每个用 Van Oosterom–Strackee 公式算球面角盈。 */
    private double area(int[] shell) {
        double total = 0;
        double[] a = this.nodes[shell[0]];
        for (int k = 1; k + 1 < shell.length; k++) {
            double[] b = this.nodes[shell[k]];
            double[] c = this.nodes[shell[k + 1]];
            double numerator = Math.abs(dot(a, cross(b, c)));
            double denominator = 1 + dot(a, b) + dot(b, c) + dot(c, a);
            total += 2 * Math.atan2(numerator, denominator);
        }
        return total;
    }

    private static long edgeKey(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    private static String key(double[] p) {
        return Math.round(p[0] * 1e6) + "," + Math.round(p[1] * 1e6) + "," + Math.round(p[2] * 1e6);
    }

    /** 排序用的取整，免得浮点误差把同一圈的节点拆开。 */
    private static double round(double value) {
        return Math.round(value * 1e6) / 1e6;
    }

    private static double longitude(double[] p) {
        double lon = Math.atan2(p[2], p[0]);
        return lon < -1e-9 ? lon + 2 * Math.PI : Math.max(0, lon);
    }

    private static double[] midpoint(double[] a, double[] b) {
        return normalize(new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]});
    }

    /** 和 p 垂直的一个方向（两极处取 x 轴）。 */
    private static double[] tangent(double[] p) {
        double[] up = Math.abs(p[1]) < 0.99 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        return normalize(cross(up, p));
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static double[] normalize(double[] p) {
        double length = Math.sqrt(dot(p, p));
        return new double[] {p[0] / length, p[1] / length, p[2] / length};
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }
}
