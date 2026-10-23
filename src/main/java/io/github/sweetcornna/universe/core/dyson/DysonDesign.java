package io.github.sweetcornna.universe.core.dyson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 戴森壳一层能改的网格（ADR-0030 §四）：节点、节点之间的框架、框架围成的圈铺的球壳，和它们的颜色。照戴森球计划的
 * 规矩：框架不交叉，框架不从别的节点旁边擦过，球壳里不能再有节点和框架，球壳不重叠。
 *
 * <p>编辑器在客户端改它，改完整个发给服务端；服务端再查一遍，保留建了的部分（{@link DysonSphere#redesign}）。
 * 每个元素记着它在原网格里的下标（{@code origin}，新加的是 -1），编辑器据此跟住选中的东西。颜色 0 是默认色，
 * 别的是 0xFFRRGGBB。
 */
public final class DysonDesign {
    public static final int MAX_NODES = 512;
    public static final int MAX_FRAMES = 1536;
    public static final int MAX_SHELLS = 1024;
    public static final int MAX_SIDES = 24;
    /** 两个节点至少离多远（度）。 */
    public static final double MIN_NODE_GAP = 1.5;
    /** 节点离不连着它的框架至少多远（度）。 */
    public static final double MIN_NODE_FRAME_GAP = 0.75;
    /** 一段框架最长多少（度）。 */
    public static final double MAX_FRAME_ARC = 75;
    /** 球壳的顶点离它的中心最远多少（度）。 */
    public static final double MAX_SHELL_REACH = 70;
    private static final double SAME = 1e-9;

    /** 改不成的原因，界面上显示 {@link #key()}。 */
    public enum Problem {
        TOO_MANY_NODES,
        TOO_MANY_FRAMES,
        TOO_MANY_SHELLS,
        NODE_TOO_CLOSE,
        NODE_ON_FRAME,
        NODE_IN_SHELL,
        FRAME_SAME_NODE,
        FRAME_EXISTS,
        FRAME_TOO_LONG,
        FRAME_CROSSES,
        FRAME_IN_SHELL,
        SHELL_SIDES,
        SHELL_OPEN,
        SHELL_TWISTED,
        SHELL_TOO_LARGE,
        SHELL_HAS_NODE,
        SHELL_HAS_FRAME,
        SHELL_EXISTS,
        SHELL_OVERLAP,
        NO_FACE,
        BROKEN;

        public String key() {
            return "gui.universe.dyson.problem." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Node(double[] position, int color, int origin) {
    }

    public record Frame(int a, int b, int color, int origin) {
    }

    public record Shell(int[] nodes, int color, int origin) {
    }

    private final DysonGrid.Template base;
    private final int density;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Frame> frames = new ArrayList<>();
    private final List<Shell> shells = new ArrayList<>();

    public DysonDesign(DysonGrid.Template base, int density) {
        this.base = base;
        this.density = density;
    }

    /** 从一层现在的网格和颜色拿一份能改的。 */
    public static DysonDesign of(DysonLayer layer) {
        DysonGrid grid = layer.grid();
        DysonDesign design = new DysonDesign(grid.template(), grid.density());
        for (int i = 0; i < grid.nodeCount(); i++) {
            design.nodes.add(new Node(grid.node(i).clone(), layer.nodeColor(i), i));
        }
        for (int i = 0; i < grid.frameCount(); i++) {
            design.frames.add(new Frame(grid.frame(i)[0], grid.frame(i)[1], layer.frameColor(i), i));
        }
        for (int i = 0; i < grid.shellCount(); i++) {
            design.shells.add(new Shell(grid.shell(i).clone(), layer.shellColor(i), i));
        }
        return design;
    }

    /** 不查合不合法地直接加（读蓝图、读存档用；之后要 {@link #validate()}）。 */
    public void rawNode(double[] position, int color) {
        this.nodes.add(new Node(SphereGeometry.normalize(position), color, -1));
    }

    public void rawFrame(int a, int b, int color) {
        this.frames.add(new Frame(a, b, color, -1));
    }

    public void rawShell(int[] nodes, int color) {
        this.shells.add(new Shell(nodes.clone(), color, -1));
    }

    public DysonGrid.Template base() {
        return this.base;
    }

    public int density() {
        return this.density;
    }

    public int nodeCount() {
        return this.nodes.size();
    }

    public int frameCount() {
        return this.frames.size();
    }

    public int shellCount() {
        return this.shells.size();
    }

    public Node node(int i) {
        return this.nodes.get(i);
    }

    public Frame frame(int i) {
        return this.frames.get(i);
    }

    public Shell shell(int i) {
        return this.shells.get(i);
    }

    public double[] position(int node) {
        return this.nodes.get(node).position;
    }

    public int[] nodeColors() {
        return this.nodes.stream().mapToInt(Node::color).toArray();
    }

    public int[] frameColors() {
        return this.frames.stream().mapToInt(Frame::color).toArray();
    }

    public int[] shellColors() {
        return this.shells.stream().mapToInt(Shell::color).toArray();
    }

    /** 换成网格。没 {@link #validate()} 过、下标不对时抛 {@link IllegalArgumentException}。 */
    public DysonGrid toGrid() {
        double[][] positions = this.nodes.stream().map(Node::position).toArray(double[][]::new);
        int[][] edges = this.frames.stream().map(f -> new int[] {f.a, f.b}).toArray(int[][]::new);
        int[][] loops = this.shells.stream().map(Shell::nodes).toArray(int[][]::new);
        return DysonGrid.custom(this.base, this.density, positions, edges, loops);
    }

    public DysonDesign copy() {
        DysonDesign copy = new DysonDesign(this.base, this.density);
        this.nodes.forEach(n -> copy.nodes.add(new Node(n.position.clone(), n.color, n.origin)));
        copy.frames.addAll(this.frames);
        this.shells.forEach(s -> copy.shells.add(new Shell(s.nodes.clone(), s.color, s.origin)));
        return copy;
    }

    /** 两份设计一样（编辑器判断撤销有没有用）：同样的节点位置、框架、球壳、颜色，顺序也一样。 */
    public boolean sameAs(DysonDesign other) {
        if (this.nodes.size() != other.nodes.size() || this.frames.size() != other.frames.size() || this.shells.size() != other.shells.size()) {
            return false;
        }
        for (int i = 0; i < this.nodes.size(); i++) {
            if (SphereGeometry.angle(this.position(i), other.position(i)) > 1e-7 || this.nodes.get(i).color != other.nodes.get(i).color) {
                return false;
            }
        }
        for (int i = 0; i < this.frames.size(); i++) {
            Frame a = this.frames.get(i);
            Frame b = other.frames.get(i);
            if (a.a != b.a || a.b != b.b || a.color != b.color) {
                return false;
            }
        }
        for (int i = 0; i < this.shells.size(); i++) {
            if (!Arrays.equals(this.shells.get(i).nodes, other.shells.get(i).nodes) || this.shells.get(i).color != other.shells.get(i).color) {
                return false;
            }
        }
        return true;
    }

    // ---- 查找

    /** 连着 a、b 的框架；没有是 -1。 */
    public int frameBetween(int a, int b) {
        for (int i = 0; i < this.frames.size(); i++) {
            Frame f = this.frames.get(i);
            if (f.a == a && f.b == b || f.a == b && f.b == a) {
                return i;
            }
        }
        return -1;
    }

    /** 离 p 最近、角距离在 within 度以内的节点；没有是 -1。 */
    public int nearestNode(double[] p, double within) {
        int best = -1;
        double bestAngle = Math.toRadians(within);
        for (int i = 0; i < this.nodes.size(); i++) {
            double a = SphereGeometry.angle(p, this.position(i));
            if (a <= bestAngle) {
                best = i;
                bestAngle = a;
            }
        }
        return best;
    }

    /** 离 p 最近、角距离在 within 度以内的框架；没有是 -1。 */
    public int nearestFrame(double[] p, double within) {
        int best = -1;
        double bestAngle = Math.toRadians(within);
        for (int i = 0; i < this.frames.size(); i++) {
            Frame f = this.frames.get(i);
            double a = SphereGeometry.arcDistance(p, this.position(f.a), this.position(f.b));
            if (a <= bestAngle) {
                best = i;
                bestAngle = a;
            }
        }
        return best;
    }

    /** p 所在的球壳；没有是 -1。 */
    public int shellAt(double[] p) {
        for (int i = 0; i < this.shells.size(); i++) {
            double[][] points = this.points(this.shells.get(i).nodes);
            if (SphereGeometry.contains(points, SphereGeometry.centroid(points), p)) {
                return i;
            }
        }
        return -1;
    }

    private double[][] points(int[] loop) {
        double[][] points = new double[loop.length][];
        for (int k = 0; k < loop.length; k++) {
            points[k] = this.position(loop[k]);
        }
        return points;
    }

    // ---- 编辑：成功返回 null，新的元素加在最后

    /** 在 p 加一个节点。 */
    public Problem addNode(double[] p) {
        double[] point = SphereGeometry.normalize(p);
        Problem problem = this.canPlace(point, -1);
        if (problem != null) {
            return problem;
        }
        this.nodes.add(new Node(point, 0, -1));
        return null;
    }

    /** 节点能不能放在 p（ignore 是正在挪的那个节点）。 */
    public Problem canPlace(double[] p, int ignore) {
        if (ignore < 0 && this.nodes.size() >= MAX_NODES) {
            return Problem.TOO_MANY_NODES;
        }
        double gap = Math.toRadians(MIN_NODE_GAP);
        for (int i = 0; i < this.nodes.size(); i++) {
            if (i != ignore && SphereGeometry.angle(p, this.position(i)) < gap) {
                return Problem.NODE_TOO_CLOSE;
            }
        }
        double frameGap = Math.toRadians(MIN_NODE_FRAME_GAP);
        for (Frame f : this.frames) {
            if (f.a != ignore && f.b != ignore && SphereGeometry.arcDistance(p, this.position(f.a), this.position(f.b)) < frameGap) {
                return Problem.NODE_ON_FRAME;
            }
        }
        for (Shell s : this.shells) {
            if (!contains(s.nodes, ignore) && this.inside(s.nodes, p)) {
                return Problem.NODE_IN_SHELL;
            }
        }
        return null;
    }

    /** 连 a、b 两个节点。 */
    public Problem addFrame(int a, int b) {
        if (a == b) {
            return Problem.FRAME_SAME_NODE;
        }
        if (this.frameBetween(a, b) >= 0) {
            return Problem.FRAME_EXISTS;
        }
        if (this.frames.size() >= MAX_FRAMES) {
            return Problem.TOO_MANY_FRAMES;
        }
        Problem problem = this.checkFrame(a, b, -1, new Index());
        if (problem != null) {
            return problem;
        }
        this.frames.add(new Frame(Math.min(a, b), Math.max(a, b), 0, -1));
        return null;
    }

    /** 一段框架和别的框架、节点、球壳合不合（skip 是它自己在列表里的下标，还没加进去是 -1）。 */
    private Problem checkFrame(int a, int b, int skip, Index index) {
        double[] pa = this.position(a);
        double[] pb = this.position(b);
        double arc = SphereGeometry.angle(pa, pb);
        if (arc > Math.toRadians(MAX_FRAME_ARC)) {
            return Problem.FRAME_TOO_LONG;
        }
        double[] mid = SphereGeometry.normalize(new double[] {pa[0] + pb[0], pa[1] + pb[1], pa[2] + pb[2]});
        // 两段框架的中点离得比两个半长加起来还远，就不可能交叉：比余弦，cos(x + y) = cos x cos y − sin x sin y
        double cosHalf = Math.cos(arc / 2 + 1e-6);
        double sinHalf = Math.sin(arc / 2 + 1e-6);
        for (int i = 0; i < this.frames.size(); i++) {
            Frame f = this.frames.get(i);
            if (i == skip || f.a == a || f.a == b || f.b == a || f.b == b
                    || SphereGeometry.dot(mid, index.mids[i]) < cosHalf * index.cosHalves[i] - sinHalf * index.sinHalves[i]) {
                continue;
            }
            if (SphereGeometry.arcsCross(pa, pb, this.position(f.a), this.position(f.b))) {
                return Problem.FRAME_CROSSES;
            }
        }
        double frameGap = Math.toRadians(MIN_NODE_FRAME_GAP);
        double near = Math.cos(arc / 2 + frameGap);
        for (int i = 0; i < this.nodes.size(); i++) {
            if (i != a && i != b && SphereGeometry.dot(this.position(i), mid) > near
                    && SphereGeometry.arcDistance(this.position(i), pa, pb) < frameGap) {
                return Problem.NODE_ON_FRAME;
            }
        }
        for (int i = 0; i < this.shells.size(); i++) {
            Cap cap = index.caps.get(i);
            if (adjacent(this.shells.get(i).nodes, a, b) || SphereGeometry.angle(cap.center, mid) > cap.reach + arc / 2 + 1e-6) {
                continue;
            }
            if (frameInside(cap, pa, pb)) {
                return Problem.FRAME_IN_SHELL;
            }
        }
        return null;
    }

    /** 用 loop 围成的圈铺一片球壳（顶点顺序随便，会转成逆时针）。 */
    public Problem addShell(int[] loop) {
        if (this.shells.size() >= MAX_SHELLS) {
            return Problem.TOO_MANY_SHELLS;
        }
        Problem problem = this.checkShell(loop, -1, new Index());
        if (problem != null) {
            return problem;
        }
        this.shells.add(new Shell(this.counterClockwise(loop), 0, -1));
        return null;
    }

    /** 点 p 所在的、框架围成的最小的圈，铺成球壳。 */
    public Problem addShellAt(double[] p) {
        int[] face = this.faceAt(p);
        if (face == null) {
            return Problem.NO_FACE;
        }
        return this.addShell(face);
    }

    /** 框架围成的圈全铺上球壳；返回铺了几片。 */
    public int fillFaces() {
        int added = 0;
        for (int[] face : this.faces()) {
            if (this.addShell(face) == null) {
                added++;
            }
        }
        return added;
    }

    /** 挪一个节点，挪了以后不合法就挪回去。 */
    public Problem moveNode(int node, double[] p) {
        double[] point = SphereGeometry.normalize(p);
        Node old = this.nodes.get(node);
        this.nodes.set(node, new Node(point, old.color, old.origin));
        Problem problem = this.validate();
        if (problem != null) {
            this.nodes.set(node, old);
        }
        return problem;
    }

    /** 删节点（连着的框架、球壳一起删）。下标从大到小删，免得错位。 */
    public void removeNodes(int... indices) {
        int[] sorted = indices.clone();
        Arrays.sort(sorted);
        for (int k = sorted.length - 1; k >= 0; k--) {
            int node = sorted[k];
            if (node < 0 || node >= this.nodes.size() || k + 1 < sorted.length && sorted[k + 1] == node) {
                continue;
            }
            this.shells.removeIf(s -> contains(s.nodes, node));
            this.frames.removeIf(f -> f.a == node || f.b == node);
            this.nodes.remove(node);
            this.frames.replaceAll(f -> new Frame(f.a > node ? f.a - 1 : f.a, f.b > node ? f.b - 1 : f.b, f.color, f.origin));
            this.shells.replaceAll(s -> new Shell(Arrays.stream(s.nodes).map(n -> n > node ? n - 1 : n).toArray(), s.color, s.origin));
        }
    }

    /** 删框架（用到它的球壳一起删）。 */
    public void removeFrames(int... indices) {
        Set<Long> gone = new HashSet<>();
        for (int i : indices) {
            if (i >= 0 && i < this.frames.size()) {
                gone.add(key(this.frames.get(i).a, this.frames.get(i).b));
            }
        }
        this.shells.removeIf(s -> {
            for (int k = 0; k < s.nodes.length; k++) {
                if (gone.contains(key(s.nodes[k], s.nodes[(k + 1) % s.nodes.length]))) {
                    return true;
                }
            }
            return false;
        });
        this.frames.removeIf(f -> gone.contains(key(f.a, f.b)));
    }

    public void removeShells(int... indices) {
        Set<Integer> gone = new HashSet<>();
        for (int i : indices) {
            gone.add(i);
        }
        List<Shell> kept = new ArrayList<>();
        for (int i = 0; i < this.shells.size(); i++) {
            if (!gone.contains(i)) {
                kept.add(this.shells.get(i));
            }
        }
        this.shells.clear();
        this.shells.addAll(kept);
    }

    /** 全删掉。 */
    public void clear() {
        this.shells.clear();
        this.frames.clear();
        this.nodes.clear();
    }

    public void paintNode(int i, int color) {
        Node n = this.nodes.get(i);
        this.nodes.set(i, new Node(n.position, color, n.origin));
    }

    public void paintFrame(int i, int color) {
        Frame f = this.frames.get(i);
        this.frames.set(i, new Frame(f.a, f.b, color, f.origin));
    }

    public void paintShell(int i, int color) {
        Shell s = this.shells.get(i);
        this.shells.set(i, new Shell(s.nodes, color, s.origin));
    }

    // ---- 球壳的几何

    /** 一片球壳（或者一个圈）的顶点、中心、顶点离中心最远多少（弧度）。 */
    private record Cap(double[][] points, double[] center, double reach) {
        boolean inside(double[] p) {
            return SphereGeometry.angle(this.center, p) <= this.reach + 1e-6 && SphereGeometry.contains(this.points, this.center, p);
        }

        /** 一定在圈里面的一点：剖出的最大的三角形的中心。 */
        double[] interior() {
            double[][] flat = SphereGeometry.gnomonic(this.center, this.points);
            if (flat == null) {
                return this.center;
            }
            int[] triangles = SphereGeometry.triangulate(flat);
            double best = -1;
            double[] point = this.center;
            for (int t = 0; t + 2 < triangles.length; t += 3) {
                double[][] tri = {flat[triangles[t]], flat[triangles[t + 1]], flat[triangles[t + 2]]};
                double area = Math.abs(SphereGeometry.area2(tri));
                if (area > best) {
                    best = area;
                    double[] a = this.points[triangles[t]];
                    double[] b = this.points[triangles[t + 1]];
                    double[] c = this.points[triangles[t + 2]];
                    point = SphereGeometry.normalize(new double[] {a[0] + b[0] + c[0], a[1] + b[1] + c[1], a[2] + b[2] + c[2]});
                }
            }
            return point;
        }
    }

    private Cap cap(int[] loop) {
        double[][] points = this.points(loop);
        double[] center = SphereGeometry.centroid(points);
        double reach = 0;
        for (double[] p : points) {
            reach = Math.max(reach, SphereGeometry.angle(center, p));
        }
        return new Cap(points, center, reach);
    }

    private boolean inside(int[] loop, double[] p) {
        return this.cap(loop).inside(p);
    }

    /** 弧 ab 有一段在圈里面（查四分之一、一半、四分之三三个点）。 */
    private static boolean frameInside(Cap cap, double[] a, double[] b) {
        for (double t : new double[] {0.25, 0.5, 0.75}) {
            double[] p = SphereGeometry.normalize(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
            if (cap.inside(p)) {
                return true;
            }
        }
        return false;
    }

    private int[] counterClockwise(int[] loop) {
        int[] out = loop.clone();
        if (SphereGeometry.signedArea(this.points(out)) < 0) {
            for (int k = 0; k < out.length / 2; k++) {
                int t = out[k];
                out[k] = out[out.length - 1 - k];
                out[out.length - 1 - k] = t;
            }
        }
        return out;
    }

    /** 一个圈能不能铺成球壳（skip 是它自己在列表里的下标，还没加进去是 -1）。 */
    private Problem checkShell(int[] loop, int skip, Index index) {
        if (loop.length < 3 || loop.length > MAX_SIDES) {
            return Problem.SHELL_SIDES;
        }
        Set<Integer> distinct = new HashSet<>();
        for (int n : loop) {
            if (n < 0 || n >= this.nodes.size()) {
                return Problem.BROKEN;
            }
            if (!distinct.add(n)) {
                return Problem.SHELL_TWISTED;
            }
        }
        Set<Long> edges = new HashSet<>();
        for (int k = 0; k < loop.length; k++) {
            int a = loop[k];
            int b = loop[(k + 1) % loop.length];
            if (this.frameBetween(a, b) < 0) {
                return Problem.SHELL_OPEN;
            }
            edges.add(key(a, b));
        }
        Cap cap = this.cap(loop);
        if (cap.reach > Math.toRadians(MAX_SHELL_REACH)) {
            return Problem.SHELL_TOO_LARGE;
        }
        double[][] flat = SphereGeometry.gnomonic(cap.center, cap.points);
        if (flat == null) {
            return Problem.SHELL_TOO_LARGE;
        }
        if (!SphereGeometry.simple(flat) || Math.abs(SphereGeometry.area2(flat)) < 1e-9) {
            return Problem.SHELL_TWISTED;
        }
        int[] sorted = loop.clone();
        Arrays.sort(sorted);
        for (int i = 0; i < this.shells.size(); i++) {
            if (i != skip && index.sorted.get(i).length == sorted.length && Arrays.equals(sorted, index.sorted.get(i))) {
                return Problem.SHELL_EXISTS;
            }
        }
        double cosReach = Math.cos(Math.min(Math.PI / 2, cap.reach + 1e-6));
        for (int i = 0; i < this.nodes.size(); i++) {
            if (!distinct.contains(i) && SphereGeometry.dot(this.position(i), cap.center) >= cosReach && cap.inside(this.position(i))) {
                return Problem.SHELL_HAS_NODE;
            }
        }
        for (int i = 0; i < this.frames.size(); i++) {
            Frame f = this.frames.get(i);
            if (edges.contains(key(f.a, f.b)) || SphereGeometry.angle(cap.center, index.mids[i]) > cap.reach + index.halves[i] + 1e-6) {
                continue;
            }
            if (frameInside(cap, this.position(f.a), this.position(f.b))) {
                return Problem.SHELL_HAS_FRAME;
            }
        }
        double[] interior = cap.interior();
        for (int i = 0; i < this.shells.size(); i++) {
            if (i == skip) {
                continue;
            }
            Cap other = index.caps.get(i);
            if (SphereGeometry.angle(cap.center, other.center) < cap.reach + other.reach
                    && (other.inside(interior) || cap.inside(index.interior(i)))) {
                return Problem.SHELL_OVERLAP;
            }
        }
        return null;
    }

    /** 查合法性用的缓存：每片球壳的圈，每段框架的中点和半长（弧度）。 */
    private final class Index {
        final List<Cap> caps = new ArrayList<>();
        final List<int[]> sorted = new ArrayList<>();
        final double[][] mids;
        final double[] halves;
        final double[] cosHalves;
        final double[] sinHalves;
        private final double[][] interiors;

        Index() {
            for (Shell s : DysonDesign.this.shells) {
                this.caps.add(DysonDesign.this.cap(s.nodes));
                int[] nodes = s.nodes.clone();
                Arrays.sort(nodes);
                this.sorted.add(nodes);
            }
            this.interiors = new double[this.caps.size()][];
            int count = DysonDesign.this.frames.size();
            this.mids = new double[count][];
            this.halves = new double[count];
            this.cosHalves = new double[count];
            this.sinHalves = new double[count];
            for (int i = 0; i < count; i++) {
                Frame f = DysonDesign.this.frames.get(i);
                double[] a = DysonDesign.this.position(f.a);
                double[] b = DysonDesign.this.position(f.b);
                this.mids[i] = SphereGeometry.normalize(new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]});
                this.halves[i] = SphereGeometry.angle(a, b) / 2;
                this.cosHalves[i] = Math.cos(this.halves[i]);
                this.sinHalves[i] = Math.sin(this.halves[i]);
            }
        }

        double[] interior(int shell) {
            if (this.interiors[shell] == null) {
                this.interiors[shell] = this.caps.get(shell).interior();
            }
            return this.interiors[shell];
        }
    }

    // ---- 圈

    /** 框架围成的所有圈：每条有向的框架走一次，到了节点往右手边最近的那条拐；顶点重复的、边太多的不要。 */
    public List<int[]> faces() {
        int n = this.nodes.size();
        List<List<Integer>> around = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            around.add(new ArrayList<>());
        }
        for (Frame f : this.frames) {
            around.get(f.a).add(f.b);
            around.get(f.b).add(f.a);
        }
        int[][] order = new int[n][];
        for (int v = 0; v < n; v++) {
            double[] c = this.position(v);
            double[] up = Math.abs(c[1]) < 0.99 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
            double[] east = SphereGeometry.normalize(SphereGeometry.cross(up, c));
            double[] north = SphereGeometry.cross(c, east);
            order[v] = around.get(v).stream().sorted(Comparator.comparingDouble(u -> {
                double[] p = this.position(u);
                return Math.atan2(SphereGeometry.dot(p, north), SphereGeometry.dot(p, east));
            })).mapToInt(Integer::intValue).toArray();
        }
        Set<Long> used = new HashSet<>();
        List<int[]> faces = new ArrayList<>();
        for (int s = 0; s < n; s++) {
            for (int t : order[s]) {
                if (used.contains(directed(s, t))) {
                    continue;
                }
                List<Integer> loop = new ArrayList<>();
                int u = s;
                int v = t;
                while (used.add(directed(u, v))) {
                    loop.add(u);
                    int[] ring = order[v];
                    int at = 0;
                    while (ring[at] != u) {
                        at++;
                    }
                    int w = ring[(at - 1 + ring.length) % ring.length];
                    u = v;
                    v = w;
                }
                if (loop.size() >= 3 && loop.size() <= MAX_SIDES && new HashSet<>(loop).size() == loop.size()) {
                    faces.add(loop.stream().mapToInt(Integer::intValue).toArray());
                }
            }
        }
        return faces;
    }

    /** p 所在的最小的圈（逆时针、不太大）；没有是 null。 */
    public int[] faceAt(double[] p) {
        int[] best = null;
        double bestArea = Double.MAX_VALUE;
        for (int[] face : this.faces()) {
            double area = SphereGeometry.signedArea(this.points(face));
            if (area <= 0 || area >= bestArea) {
                continue;
            }
            Cap cap = this.cap(face);
            if (cap.reach <= Math.toRadians(MAX_SHELL_REACH) && cap.inside(p)) {
                best = face;
                bestArea = area;
            }
        }
        return best;
    }

    // ---- 整体检查

    /** 整份设计合不合法（读蓝图、服务端收到改动时查）；合法返回 null。 */
    public Problem validate() {
        int n = this.nodes.size();
        if (n > MAX_NODES) {
            return Problem.TOO_MANY_NODES;
        }
        if (this.frames.size() > MAX_FRAMES) {
            return Problem.TOO_MANY_FRAMES;
        }
        if (this.shells.size() > MAX_SHELLS) {
            return Problem.TOO_MANY_SHELLS;
        }
        double cosGap = Math.cos(Math.toRadians(MIN_NODE_GAP));
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (SphereGeometry.dot(this.position(i), this.position(j)) > cosGap) {
                    return Problem.NODE_TOO_CLOSE;
                }
            }
        }
        Set<Long> seen = new HashSet<>();
        for (Frame f : this.frames) {
            if (f.a < 0 || f.b < 0 || f.a >= n || f.b >= n) {
                return Problem.BROKEN;
            }
            if (f.a == f.b) {
                return Problem.FRAME_SAME_NODE;
            }
            if (!seen.add(key(f.a, f.b))) {
                return Problem.FRAME_EXISTS;
            }
        }
        for (Shell s : this.shells) {
            for (int v : s.nodes) {
                if (v < 0 || v >= n) {
                    return Problem.BROKEN;
                }
            }
            if (s.nodes.length < 3 || s.nodes.length > MAX_SIDES) {
                return Problem.SHELL_SIDES;
            }
        }
        Index index = new Index();
        for (int i = 0; i < this.frames.size(); i++) {
            Problem problem = this.checkFrame(this.frames.get(i).a, this.frames.get(i).b, i, index);
            if (problem != null) {
                return problem;
            }
        }
        for (int i = 0; i < this.shells.size(); i++) {
            Problem problem = this.checkShell(this.shells.get(i).nodes, i, index);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static boolean contains(int[] loop, int node) {
        for (int n : loop) {
            if (n == node) {
                return true;
            }
        }
        return false;
    }

    private static boolean adjacent(int[] loop, int a, int b) {
        for (int k = 0; k < loop.length; k++) {
            int x = loop[k];
            int y = loop[(k + 1) % loop.length];
            if (x == a && y == b || x == b && y == a) {
                return true;
            }
        }
        return false;
    }

    private static long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    private static long directed(int a, int b) {
        return ((long) a << 32) | b;
    }
}
