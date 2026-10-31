package io.github.sweetcornna.universe.core.dyson;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

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

    /** 层里的一种元素（ADR-0030 §四）。 */
    public enum Element {
        NODE,
        FRAME,
        SHELL;

        public static Element byOrdinal(int ordinal) {
            Element[] values = values();
            return values[Math.floorMod(ordinal, values.length)];
        }
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
    /** 节点、框架、球壳的颜色（ADR-0030 §四）：0 是默认色，别的是 0xFFRRGGBB。 */
    private int[] nodeColors;
    private int[] frameColors;
    private int[] shellColors;
    /** 网格每换一次加一（元素的下标变了），编辑器发来的改动按它判断是不是过时了。不存档。 */
    private int revision;
    /**
     * 节点、框架的结构点、预定或网格每变一次加一。{@link #nextTarget} 按它和纬度限制记下上一次的结果：发射井每刻都问，
     * 球建满了以后每问一次都要把全部节点和框架扫一遍。不存档。
     */
    private int targets;
    private int cachedTargets = -1;
    private double cachedLimit = Double.NaN;
    private @Nullable Target cachedTarget;
    /** 吸帆的零头（帆），和下一片轮到的球壳。 */
    private double absorbCredit;
    private int absorbCursor;

    public DysonLayer(int id, double radius, double inclination, double ascendingNode, DysonGrid.Template template, int density) {
        this(id, radius, inclination, ascendingNode, DysonGrid.of(template, density));
    }

    public DysonLayer(int id, double radius, double inclination, double ascendingNode, DysonGrid grid) {
        this.id = id;
        this.inclination = inclination;
        this.ascendingNode = ascendingNode;
        this.rebuild(radius, grid);
    }

    /** 换半径或模板，进度清零、颜色恢复默认；只在还没开始建时用。 */
    void reshape(double radius, DysonGrid.Template template, int density) {
        this.rebuild(radius, DysonGrid.of(template, density));
        this.revision++;
    }

    /** 只换半径，网格和颜色不变，进度清零；只在还没开始建时用。 */
    void resize(double radius) {
        int[] nodes = this.nodeColors;
        int[] frames = this.frameColors;
        int[] shells = this.shellColors;
        this.rebuild(radius, this.grid);
        this.nodeColors = nodes;
        this.frameColors = frames;
        this.shellColors = shells;
    }

    private void rebuild(double radius, DysonGrid grid) {
        this.radius = radius;
        this.grid = grid;
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
        this.nodeColors = new int[this.grid.nodeCount()];
        this.frameColors = new int[this.grid.frameCount()];
        this.shellColors = new int[this.grid.shellCount()];
        this.absorbCredit = 0;
        this.absorbCursor = 0;
        this.targets++;
    }

    // ---- 自由编辑（ADR-0030 §四）

    /**
     * 换成新的网格，按对应关系搬进度、预定和颜色。map[新下标] 是旧下标，新加的是 -1；没对应上的旧元素的进度丢掉，
     * 调用前要确认它们都没开始建。
     */
    void apply(DysonGrid next, int[] nodeMap, int[] frameMap, int[] shellMap, int[] nodeColors, int[] frameColors, int[] shellColors) {
        int[] oldNodes = this.nodeSp;
        int[] oldFrames = this.frameSp;
        int[] oldCells = this.cells;
        int[] oldNodeReserved = this.nodeReserved;
        int[] oldFrameReserved = this.frameReserved;
        double credit = this.absorbCredit;
        this.rebuild(this.radius, next);
        for (int i = 0; i < nodeMap.length && i < this.nodeSp.length; i++) {
            if (nodeMap[i] >= 0) {
                this.nodeSp[i] = Math.min(NODE_SP, oldNodes[nodeMap[i]]);
                this.nodeReserved[i] = oldNodeReserved[nodeMap[i]];
            }
        }
        for (int i = 0; i < frameMap.length && i < this.frameSp.length; i++) {
            if (frameMap[i] >= 0) {
                this.frameSp[i] = Math.min(this.frameCost[i], oldFrames[frameMap[i]]);
                this.frameReserved[i] = oldFrameReserved[frameMap[i]];
            }
        }
        for (int i = 0; i < shellMap.length && i < this.cells.length; i++) {
            if (shellMap[i] >= 0) {
                this.cells[i] = Math.min(this.shellCost[i], oldCells[shellMap[i]]);
            }
        }
        this.restoreColors(nodeColors, frameColors, shellColors);
        this.absorbCredit = credit;
        this.revision++;
    }

    /** 这个元素建了一点、或者有火箭正往这儿飞（不能直接删，要先拆）。 */
    public boolean started(Element kind, int index) {
        return switch (kind) {
            case NODE -> this.nodeSp[index] > 0 || this.nodeReserved[index] > 0;
            case FRAME -> this.frameSp[index] > 0 || this.frameReserved[index] > 0;
            case SHELL -> this.cells[index] > 0;
        };
    }

    public int count(Element kind) {
        return switch (kind) {
            case NODE -> this.grid.nodeCount();
            case FRAME -> this.grid.frameCount();
            case SHELL -> this.grid.shellCount();
        };
    }

    /**
     * 拆掉一个元素：结构点、单元点清零，不退；节点连着的框架和球壳、框架边上的球壳跟着拆。拆到的节点、框架记进
     * nodes、frames（在路上的火箭跟着作废）。
     */
    void demolish(Element kind, int index, java.util.Set<Integer> nodes, java.util.Set<Integer> frames) {
        this.targets++;
        switch (kind) {
            case NODE -> {
                this.nodeSp[index] = 0;
                this.nodeReserved[index] = 0;
                nodes.add(index);
                for (int f = 0; f < this.grid.frameCount(); f++) {
                    if (this.grid.frame(f)[0] == index || this.grid.frame(f)[1] == index) {
                        this.demolish(Element.FRAME, f, nodes, frames);
                    }
                }
                for (int shell : this.grid.nodeShells(index)) {
                    this.cells[shell] = 0;
                }
            }
            case FRAME -> {
                this.frameSp[index] = 0;
                this.frameReserved[index] = 0;
                frames.add(index);
                for (int s = 0; s < this.grid.shellCount(); s++) {
                    for (int f : this.grid.shellFrames(s)) {
                        if (f == index) {
                            this.cells[s] = 0;
                        }
                    }
                }
            }
            case SHELL -> this.cells[index] = 0;
        }
    }

    public int nodeColor(int node) {
        return this.nodeColors[node];
    }

    public int frameColor(int frame) {
        return this.frameColors[frame];
    }

    public int shellColor(int shell) {
        return this.shellColors[shell];
    }

    void paint(Element kind, int index, int color) {
        switch (kind) {
            case NODE -> this.nodeColors[index] = color;
            case FRAME -> this.frameColors[index] = color;
            case SHELL -> this.shellColors[index] = color;
        }
    }

    public int[] nodeColors() {
        return this.nodeColors.clone();
    }

    public int[] frameColors() {
        return this.frameColors.clone();
    }

    public int[] shellColors() {
        return this.shellColors.clone();
    }

    /** 读档、同步：恢复颜色，长度不对的部分忽略。 */
    public void restoreColors(int[] nodes, int[] frames, int[] shells) {
        System.arraycopy(nodes, 0, this.nodeColors, 0, Math.min(nodes.length, this.nodeColors.length));
        System.arraycopy(frames, 0, this.frameColors, 0, Math.min(frames.length, this.frameColors.length));
        System.arraycopy(shells, 0, this.shellColors, 0, Math.min(shells.length, this.shellColors.length));
    }

    public int revision() {
        return this.revision;
    }

    /** 同步：客户端照服务端的编号。 */
    public void restoreRevision(int revision) {
        this.revision = revision;
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
    public @Nullable Target nextTarget(double limit) {
        if (this.cachedTargets == this.targets && Double.compare(this.cachedLimit, limit) == 0) {
            return this.cachedTarget;
        }
        Target target = this.findTarget(limit);
        this.cachedTargets = this.targets;
        this.cachedLimit = limit;
        this.cachedTarget = target;
        return target;
    }

    private @Nullable Target findTarget(double limit) {
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
        this.targets++;
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
        this.targets++;
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
