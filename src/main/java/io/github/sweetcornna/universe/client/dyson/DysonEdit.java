package io.github.sweetcornna.universe.client.dyson;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.dyson.DysonBlueprint;
import io.github.sweetcornna.universe.core.dyson.DysonDesign;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SphereGeometry;
import io.github.sweetcornna.universe.network.DysonLayerEditPayload;

/**
 * 编辑一层时客户端这边的状态（ADR-0030 §四）：手上这份还没发出去的设计、选中的东西、当前的工具。
 *
 * <p>改动先改手上这份，马上看得到；按"应用"才整份发给服务端（{@link DysonLayerEditPayload.Action#REDESIGN}）。
 * 服务端同步回来以后 {@link #rebase} 跟着换一份新的，选中的东西按元素在原网格里的下标（{@code origin}）跟住。
 * 拆和上色是立刻生效的操作，不进手上这份设计。
 */
final class DysonEdit {
    /** 吸附的经纬格（度），按住 Shift 时细一些。 */
    static final double SNAP = 3;
    static final double SNAP_FINE = 1;
    /** 点中一个节点、一段框架要多近（度）。 */
    private static final double PICK_NODE = 2.5;
    private static final double PICK_FRAME = 1.5;
    private static final int MAX_UNDO = 32;

    /** 手上的工具。 */
    enum Tool {
        /** 选：点中节点、框架、球壳；拖着挪节点。 */
        SELECT,
        /** 摆节点：点空处放一个。 */
        NODE,
        /** 连框架：依次点两个节点。 */
        FRAME,
        /** 铺球壳：点框架围成的圈里面。 */
        SHELL,
        /** 擦：点中的删掉（建了的要先拆）。 */
        ERASE,
        /** 上色：点中的涂成当前的颜色。 */
        PAINT;

        String key() {
            return "gui.universe.dyson.tool." + this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final int layerId;
    /** 手上这份设计。 */
    private DysonDesign design;
    /** 服务端那份（判断手上这份改没改过）。 */
    private DysonDesign server;
    private int revision;
    private final List<DysonDesign> undo = new ArrayList<>();
    private Tool tool = Tool.SELECT;
    private DysonLayer.Element kind = DysonLayer.Element.NODE;
    private final Set<Integer> selection = new LinkedHashSet<>();
    /** 连框架时点的第一个节点；没点是 -1。 */
    private int pendingNode = -1;
    /** 调色盘里选的颜色，0 是默认色。 */
    private int color;
    /** 正在拖的节点；没拖是 -1。 */
    private int dragNode = -1;

    DysonEdit(DysonLayer layer) {
        this.layerId = layer.id();
        this.design = DysonDesign.of(layer);
        this.server = this.design.copy();
        this.revision = layer.revision();
    }

    int layerId() {
        return this.layerId;
    }

    DysonDesign design() {
        return this.design;
    }

    int revision() {
        return this.revision;
    }

    Tool tool() {
        return this.tool;
    }

    void setTool(Tool tool) {
        this.tool = tool;
        this.pendingNode = -1;
        if (tool != Tool.SELECT && tool != Tool.ERASE && tool != Tool.PAINT) {
            this.selection.clear();
        }
    }

    DysonLayer.Element kind() {
        return this.kind;
    }

    /** 换选的种类（节点／框架／球壳）：选中的东西清掉。 */
    void setKind(DysonLayer.Element kind) {
        if (this.kind != kind) {
            this.kind = kind;
            this.selection.clear();
            this.pendingNode = -1;
        }
    }

    int color() {
        return this.color;
    }

    void setColor(int color) {
        this.color = color;
    }

    Set<Integer> selection() {
        return this.selection;
    }

    int pendingNode() {
        return this.pendingNode;
    }

    boolean dragging() {
        return this.dragNode >= 0;
    }

    /** 手上这份和服务端那份不一样（有没发出去的改动）。 */
    boolean dirty() {
        return !this.design.sameAs(this.server);
    }

    boolean canUndo() {
        return !this.undo.isEmpty();
    }

    /** 服务端发来新的一层：手上没改过就跟着换；改过就只换服务端那份，留着玩家的改动。 */
    void rebase(DysonLayer layer) {
        DysonDesign fresh = DysonDesign.of(layer);
        boolean clean = !this.dirty();
        this.server = fresh.copy();
        this.revision = layer.revision();
        if (clean) {
            this.design = fresh;
            this.undo.clear();
            this.remap();
        }
    }

    /** 选中的下标按 {@code origin} 跟住新的网格；跟不上的丢掉。 */
    private void remap() {
        Set<Integer> kept = new LinkedHashSet<>();
        for (int i = 0; i < this.count(); i++) {
            if (this.selection.contains(this.originOf(i))) {
                kept.add(i);
            }
        }
        this.selection.clear();
        this.selection.addAll(kept);
        this.pendingNode = -1;
    }

    private int originOf(int index) {
        return switch (this.kind) {
            case NODE -> this.design.node(index).origin();
            case FRAME -> this.design.frame(index).origin();
            case SHELL -> this.design.shell(index).origin();
        };
    }

    private int count() {
        return switch (this.kind) {
            case NODE -> this.design.nodeCount();
            case FRAME -> this.design.frameCount();
            case SHELL -> this.design.shellCount();
        };
    }

    private void push() {
        this.undo.add(this.design.copy());
        if (this.undo.size() > MAX_UNDO) {
            this.undo.removeFirst();
        }
    }

    void undo() {
        if (!this.undo.isEmpty()) {
            this.design = this.undo.removeLast();
            this.selection.clear();
            this.pendingNode = -1;
        }
    }

    /** 丢掉所有没发出去的改动。 */
    void revert() {
        this.design = this.server.copy();
        this.undo.clear();
        this.selection.clear();
        this.pendingNode = -1;
    }

    /** 整份发给服务端。 */
    DysonLayerEditPayload apply() {
        return DysonLayerEditPayload.redesign(this.layerId, DysonBlueprint.pack(this.design));
    }

    /** 导出成蓝图串。 */
    String export() {
        return DysonBlueprint.export(this.design);
    }

    // ---- 点下去

    /**
     * 在球面上的 p 点一下（p 是层自己坐标里的单位向量）。
     *
     * @param snap  吸附的格子（度），0 不吸附
     * @param layer 服务端那一层，判断元素建了没有
     * @return 改不成的原因；成了是 null
     */
    DysonDesign.@Nullable Problem click(double[] p, double snap, DysonLayer layer, boolean add) {
        return switch (this.tool) {
            case SELECT -> {
                this.pick(p, add);
                yield null;
            }
            case NODE -> {
                this.push();
                DysonDesign.Problem problem = this.design.addNode(SphereGeometry.snap(p, snap));
                if (problem != null) {
                    this.undo.removeLast();
                }
                yield problem;
            }
            case FRAME -> this.frame(p);
            case SHELL -> {
                this.push();
                DysonDesign.Problem problem = this.design.addShellAt(p);
                if (problem != null) {
                    this.undo.removeLast();
                }
                yield problem;
            }
            case ERASE -> this.erase(p, layer);
            case PAINT -> {
                this.pick(p, add);
                yield null;
            }
        };
    }

    /** 选工具：点中的加进选中的东西里（add 是按着 Shift，接着选）。 */
    private void pick(double[] p, boolean add) {
        int hit = this.hit(p);
        if (!add) {
            this.selection.clear();
        }
        if (hit >= 0 && !this.selection.add(hit) && add) {
            this.selection.remove(hit);
        }
    }

    /** p 点中的、当前种类的元素；没点中是 -1。 */
    int hit(double[] p) {
        return switch (this.kind) {
            case NODE -> this.design.nearestNode(p, PICK_NODE);
            case FRAME -> this.design.nearestFrame(p, PICK_FRAME);
            case SHELL -> this.design.shellAt(p);
        };
    }

    private DysonDesign.@Nullable Problem frame(double[] p) {
        int node = this.design.nearestNode(p, PICK_NODE);
        if (node < 0) {
            this.pendingNode = -1;
            return null;
        }
        if (this.pendingNode < 0) {
            this.pendingNode = node;
            return null;
        }
        if (this.pendingNode == node) {
            this.pendingNode = -1;
            return null;
        }
        this.push();
        DysonDesign.Problem problem = this.design.addFrame(this.pendingNode, node);
        if (problem != null) {
            this.undo.removeLast();
        } else {
            // 连着画：这一段的终点当下一段的起点
            this.pendingNode = node;
        }
        return problem;
    }

    /** 擦：点中的删掉。建了的不能直接删（要先拆），返回 BUILT 让界面提示。 */
    private DysonDesign.@Nullable Problem erase(double[] p, DysonLayer layer) {
        int hit = this.hit(p);
        if (hit < 0) {
            return null;
        }
        int origin = this.originOf(hit);
        if (origin >= 0 && origin < layer.count(this.kind) && layer.started(this.kind, origin)) {
            return DysonDesign.Problem.BROKEN;
        }
        this.push();
        switch (this.kind) {
            case NODE -> this.design.removeNodes(hit);
            case FRAME -> this.design.removeFrames(hit);
            case SHELL -> this.design.removeShells(hit);
        }
        this.selection.remove(hit);
        return null;
    }

    // ---- 拖着挪节点

    /** 开始拖 p 点中的节点；点中的节点已经建了（挪不了）或者没点中返回 false。 */
    boolean startDrag(double[] p, DysonLayer layer) {
        if (this.tool != Tool.SELECT || this.kind != DysonLayer.Element.NODE) {
            return false;
        }
        int node = this.design.nearestNode(p, PICK_NODE);
        if (node < 0) {
            return false;
        }
        int origin = this.design.node(node).origin();
        if (origin >= 0 && origin < layer.count(DysonLayer.Element.NODE) && layer.started(DysonLayer.Element.NODE, origin)) {
            return false;
        }
        this.push();
        this.dragNode = node;
        return true;
    }

    /** 拖到 p；挪不过去就留在原处。 */
    void drag(double[] p, double snap) {
        if (this.dragNode >= 0) {
            this.design.moveNode(this.dragNode, SphereGeometry.snap(p, snap));
        }
    }

    void endDrag() {
        if (this.dragNode >= 0 && this.design.sameAs(this.undo.isEmpty() ? this.design : this.undo.getLast())) {
            // 一点没挪动：别占一格撤销
            if (!this.undo.isEmpty()) {
                this.undo.removeLast();
            }
        }
        this.dragNode = -1;
    }

    // ---- 立刻生效的操作

    /** 把选中的东西涂成当前的颜色；没选中返回 null。 */
    @Nullable DysonLayerEditPayload paint() {
        int[] indices = this.serverIndices();
        return indices.length == 0 ? null : DysonLayerEditPayload.paint(this.layerId, this.revision, this.kind.ordinal(), indices, this.color);
    }

    /** 拆掉选中的、已经建了的东西；没有可拆的返回 null。 */
    @Nullable DysonLayerEditPayload demolish() {
        int[] indices = this.serverIndices();
        return indices.length == 0 ? null : DysonLayerEditPayload.demolish(this.layerId, this.revision, this.kind.ordinal(), indices);
    }

    /** 选中的东西在服务端那份网格里的下标（手上新加的还没有，去掉）。 */
    private int[] serverIndices() {
        List<Integer> out = new ArrayList<>();
        for (int index : this.selection) {
            if (index < this.count()) {
                int origin = this.originOf(index);
                if (origin >= 0) {
                    out.add(origin);
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 选中的东西里有没有已经建了的（能不能拆）。 */
    boolean anyBuilt(DysonLayer layer) {
        for (int index : this.selection) {
            if (index < this.count()) {
                int origin = this.originOf(index);
                if (origin >= 0 && origin < layer.count(this.kind) && layer.started(this.kind, origin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 框架围成的圈全铺上球壳；返回铺了几片。 */
    int fill() {
        this.push();
        int added = this.design.fillFaces();
        if (added == 0) {
            this.undo.removeLast();
        }
        return added;
    }

    /** 全清掉（建了的留着：发给服务端时会被拒，界面上先提示）。 */
    void clear() {
        this.push();
        this.design.clear();
        this.selection.clear();
        this.pendingNode = -1;
    }

    /** 手上这份能不能应用到服务端（客户端先查一遍，马上提示）。 */
    DysonSphere.EditResult check(DysonSphere sphere) {
        return sphere.check(this.layerId, this.design, false);
    }

    /** 手上这份整体合不合法。 */
    DysonDesign.@Nullable Problem validate() {
        return this.design.validate();
    }
}
