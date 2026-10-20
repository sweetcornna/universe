package io.github.sweetcornna.universe.core.dyson;

import java.util.Arrays;

/**
 * 戴森球的一层（ADR-0016）：半径、朝向、网格，以及每个节点、框架已建的结构点和每片球壳已铺的单元点。
 *
 * <p>结构点的"预定"是已经发射、还在路上的火箭，不存档，读档时按在飞的火箭重新算。
 */
public final class DysonLayer {
    /** 一个节点要几个结构点（戴森球计划 30）。 */
    public static final int NODE_SP = 30;
    /** 框架每天文单位弧长要几个结构点。 */
    public static final double FRAME_SP_PER_AU = 60;
    /** 球壳每平方天文单位要几个单元点。 */
    public static final double CELLS_PER_AU2 = 40000;

    /** 下一个要建的结构：节点或框架。 */
    public record Target(boolean node, int index) {
    }

    private final int id;
    private double radius;
    private double inclination;
    private double ascendingNode;
    private DysonGrid grid;
    private int[] frameCost;
    private int[] shellCost;
    private int[] nodeSp;
    private int[] frameSp;
    private int[] cells;
    private int[] nodeReserved;
    private int[] frameReserved;
    /** 吸帆的零头（帆），和下一片轮到的球壳。 */
    private double absorbCredit;
    private int absorbCursor;

    public DysonLayer(int id, double radius, double inclination, double ascendingNode, DysonGrid.Template template, int density) {
        this.id = id;
        this.inclination = inclination;
        this.ascendingNode = ascendingNode;
        this.reshape(radius, template, density);
    }

    /** 换半径或模板，进度清零；只在还没开始建时用。 */
    void reshape(double radius, DysonGrid.Template template, int density) {
        this.radius = radius;
        this.grid = DysonGrid.of(template, density);
        this.frameCost = new int[this.grid.frameCount()];
        for (int i = 0; i < this.frameCost.length; i++) {
            this.frameCost[i] = frameCost(this.grid.frameArc(i), radius);
        }
        this.shellCost = new int[this.grid.shellCount()];
        for (int i = 0; i < this.shellCost.length; i++) {
            this.shellCost[i] = shellCost(this.grid.shellArea(i), radius);
        }
        this.nodeSp = new int[this.grid.nodeCount()];
        this.frameSp = new int[this.grid.frameCount()];
        this.cells = new int[this.grid.shellCount()];
        this.nodeReserved = new int[this.grid.nodeCount()];
        this.frameReserved = new int[this.grid.frameCount()];
        this.absorbCredit = 0;
        this.absorbCursor = 0;
    }

    /** 弧长（弧度）为 arc 的框架在半径 radius 天文单位处要几个结构点，至少 1 个。 */
    public static int frameCost(double arc, double radius) {
        return Math.max(1, (int) Math.ceil(arc * radius * FRAME_SP_PER_AU - 1e-9));
    }

    /** 面积（球面度）为 area 的球壳在半径 radius 处要几个单元点，至少 1 个。 */
    public static int shellCost(double area, double radius) {
        return Math.max(1, (int) Math.round(area * radius * radius * CELLS_PER_AU2));
    }

    public int id() {
        return this.id;
    }

    public double radius() {
        return this.radius;
    }

    public double inclination() {
        return this.inclination;
    }

    public double ascendingNode() {
        return this.ascendingNode;
    }

    void orient(double inclination, double ascendingNode) {
        this.inclination = inclination;
        this.ascendingNode = ascendingNode;
    }

    public DysonGrid grid() {
        return this.grid;
    }

    public int nodeSp(int node) {
        return this.nodeSp[node];
    }

    public int frameSp(int frame) {
        return this.frameSp[frame];
    }

    public int frameCost(int frame) {
        return this.frameCost[frame];
    }

    public int cells(int shell) {
        return this.cells[shell];
    }

    public int shellCost(int shell) {
        return this.shellCost[shell];
    }

    public boolean nodeBuilt(int node) {
        return this.nodeSp[node] >= NODE_SP;
    }

    public boolean frameBuilt(int frame) {
        return this.frameSp[frame] >= this.frameCost[frame];
    }

    /** 球壳的节点和框架都建完了，可以铺帆。 */
    public boolean shellReady(int shell) {
        for (int node : this.grid.shell(shell)) {
            if (!this.nodeBuilt(node)) {
                return false;
            }
        }
        for (int frame : this.grid.shellFrames(shell)) {
            if (!this.frameBuilt(frame)) {
                return false;
            }
        }
        return true;
    }

    public boolean shellFull(int shell) {
        return this.cells[shell] >= this.shellCost[shell];
    }

    /** 铺满了几片球壳。 */
    public int fullShells() {
        int full = 0;
        for (int shell = 0; shell < this.cells.length; shell++) {
            if (this.shellFull(shell)) {
                full++;
            }
        }
        return full;
    }

    /** 一点都还没建（可以换半径和模板）。 */
    public boolean empty() {
        return Arrays.stream(this.nodeSp).allMatch(sp -> sp == 0) && Arrays.stream(this.frameSp).allMatch(sp -> sp == 0)
                && Arrays.stream(this.nodeReserved).allMatch(sp -> sp == 0) && Arrays.stream(this.frameReserved).allMatch(sp -> sp == 0);
    }

    /** 已建的结构点。 */
    public long structurePoints() {
        return (long) Arrays.stream(this.nodeSp).sum() + Arrays.stream(this.frameSp).sum();
    }

    /** 已铺的单元点。 */
    public long cellPoints() {
        return Arrays.stream(this.cells).asLongStream().sum();
    }

    /** 纬度限制以内全部建完要几个结构点。 */
    public long structureCapacity(double limit) {
        long total = 0;
        for (int i = 0; i < this.nodeSp.length; i++) {
            if (this.grid.nodeWithin(i, limit)) {
                total += NODE_SP;
            }
        }
        for (int i = 0; i < this.frameSp.length; i++) {
            if (this.grid.frameWithin(i, limit)) {
                total += this.frameCost[i];
            }
        }
        return total;
    }

    /** 纬度限制以内全部铺满要几个单元点。 */
    public long cellCapacity(double limit) {
        long total = 0;
        for (int i = 0; i < this.cells.length; i++) {
            if (this.grid.shellWithin(i, limit)) {
                total += this.shellCost[i];
            }
        }
        return total;
    }

    /**
     * 下一枚火箭建什么：先按顺序建纬度限制以内的节点，再建两端节点都建完的框架；算上在路上的火箭。
     *
     * @return 没有可建的时为 null
     */
    public Target nextTarget(double limit) {
        for (int i = 0; i < this.nodeSp.length; i++) {
            if (this.nodeSp[i] + this.nodeReserved[i] < NODE_SP && this.grid.nodeWithin(i, limit)) {
                return new Target(true, i);
            }
        }
        for (int i = 0; i < this.frameSp.length; i++) {
            int[] ends = this.grid.frame(i);
            if (this.frameSp[i] + this.frameReserved[i] < this.frameCost[i] && this.grid.frameWithin(i, limit)
                    && this.nodeBuilt(ends[0]) && this.nodeBuilt(ends[1])) {
                return new Target(false, i);
            }
        }
        return null;
    }

    void reserve(Target target, int delta) {
        if (target.node()) {
            this.nodeReserved[target.index()] = Math.max(0, this.nodeReserved[target.index()] + delta);
        } else {
            this.frameReserved[target.index()] = Math.max(0, this.frameReserved[target.index()] + delta);
        }
    }

    /** 火箭到了：加一个结构点（已经建满的不再加）。 */
    void build(Target target) {
        this.reserve(target, -1);
        if (target.node()) {
            this.nodeSp[target.index()] = Math.min(NODE_SP, this.nodeSp[target.index()] + 1);
        } else {
            this.frameSp[target.index()] = Math.min(this.frameCost[target.index()], this.frameSp[target.index()] + 1);
        }
    }

    /** 能吸帆的节点数：建完了、旁边有能铺还没铺满的球壳。 */
    int absorbingNodes() {
        int count = 0;
        for (int node = 0; node < this.nodeSp.length; node++) {
            if (!this.nodeBuilt(node)) {
                continue;
            }
            for (int shell : this.grid.nodeShells(node)) {
                if (!this.shellFull(shell) && this.shellReady(shell)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** 这一段时间能吸几张帆：每个能吸帆的节点每分钟 {@link DysonSphere#ABSORB_PER_MINUTE} 张，零头攒着。 */
    int absorbQuota(double seconds) {
        int nodes = this.absorbingNodes();
        if (nodes == 0) {
            this.absorbCredit = 0;
            return 0;
        }
        this.absorbCredit += nodes * DysonSphere.ABSORB_PER_MINUTE * seconds / 60.0;
        int quota = (int) Math.floor(this.absorbCredit);
        this.absorbCredit -= quota;
        return quota;
    }

    /** 把帆轮流铺进能铺的球壳，返回实际铺了几张。 */
    int lay(int sails) {
        int laid = 0;
        int count = this.cells.length;
        int idle = 0;
        while (laid < sails && idle < count) {
            int shell = this.absorbCursor;
            this.absorbCursor = (this.absorbCursor + 1) % count;
            if (!this.shellFull(shell) && this.shellReady(shell)) {
                this.cells[shell]++;
                laid++;
                idle = 0;
            } else {
                idle++;
            }
        }
        return laid;
    }

    // ---- 存档 ----

    public int[] nodeProgress() {
        return this.nodeSp.clone();
    }

    public int[] frameProgress() {
        return this.frameSp.clone();
    }

    public int[] cellProgress() {
        return this.cells.clone();
    }

    /** 读档、同步：恢复进度，长度不对（模板改过）的部分忽略，超出上限的截掉。 */
    public void restore(int[] nodes, int[] frames, int[] shellCells) {
        for (int i = 0; i < Math.min(nodes.length, this.nodeSp.length); i++) {
            this.nodeSp[i] = Math.max(0, Math.min(NODE_SP, nodes[i]));
        }
        for (int i = 0; i < Math.min(frames.length, this.frameSp.length); i++) {
            this.frameSp[i] = Math.max(0, Math.min(this.frameCost[i], frames[i]));
        }
        for (int i = 0; i < Math.min(shellCells.length, this.cells.length); i++) {
            this.cells[i] = Math.max(0, Math.min(this.shellCost[i], shellCells[i]));
        }
    }
}
