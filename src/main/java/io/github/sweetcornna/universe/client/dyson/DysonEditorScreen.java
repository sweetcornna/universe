package io.github.sweetcornna.universe.client.dyson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.dyson.DysonBlueprint;
import io.github.sweetcornna.universe.core.dyson.DysonDesign;
import io.github.sweetcornna.universe.core.dyson.DysonGrid;
import io.github.sweetcornna.universe.core.dyson.DysonLayer;
import io.github.sweetcornna.universe.core.dyson.DysonPalette;
import io.github.sweetcornna.universe.core.dyson.DysonSphere;
import io.github.sweetcornna.universe.core.dyson.SphereGeometry;
import io.github.sweetcornna.universe.core.dyson.SwarmOrbit;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarMapCamera;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.network.DysonEditPayload;
import io.github.sweetcornna.universe.network.DysonLayerEditPayload;
import io.github.sweetcornna.universe.network.DysonResultPayload;
import io.github.sweetcornna.universe.network.DysonSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 戴森球编辑器（ADR-0016）：左边列出戴森云的轨道和戴森壳的层，中间是恒星周围的三维视图，右边改选中的那一个，
 * 下面是总的发电和建造进度。修改发给服务端，服务端改完把新的戴森球同步回来，界面总是显示服务端的状态。
 */
public class DysonEditorScreen extends Screen {
    private static final int LIST_WIDTH = 118;
    private static final int PROPS_WIDTH = 156;
    private static final int ROW = 12;
    private static final int BUTTON = 11;
    /** 三维视图里半径上限画多大（视图单位）。 */
    private static final double VIEW_RADIUS = 30;
    private static final int SWARM_COLOR = 0xFFF2C94C;
    private static final int FRAME_BUILT = 0xFFFFD27A;
    private static final int FRAME_PLANNED = 0x6090B8D0;
    private static final int FRAME_LOCKED = 0x283A5060;
    private static final int NODE_BUILT = 0xFFFFF2C0;
    /** 编辑时：选中的、正在连的、不合法的。 */
    private static final int PICKED = 0xFF7FE0F7;
    private static final int PENDING = 0xFF9CF08A;
    /** 调色盘一格多大。 */
    private static final int SWATCH = 11;

    private final StarMapCamera camera = new StarMapCamera();
    private final GuiMesh mesh = new GuiMesh();
    /** 选中的：轨道（true）或层，和编号；0 是没选。 */
    private boolean orbitSelected = true;
    private int selected;
    private int listScroll;
    /** 刚点了"新建"：记下当时已有的编号，收到同步后选中新的那个。 */
    private @Nullable Set<Integer> pendingAdd;
    private boolean pendingOrbit;
    private long pendingVersion;
    /** 点过一次删除，等第二次确认。 */
    private boolean confirmDelete;
    private boolean dragging;
    /** 第一次拿到戴森球数据时按最外面的轨道或层把镜头拉到合适的远近。 */
    private boolean framed;
    private double lastMouseX;
    private double lastMouseY;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private final List<Hit> hits = new ArrayList<>();
    /** 正在自由编辑的那一层（ADR-0030 §四）；没在编辑是 null。 */
    private @Nullable DysonEdit edit;
    /** 上一次操作的结果：语言键和它是不是错误；没有是 null。 */
    private @Nullable Component message;
    private boolean messageBad;
    /** 视图的下边（编辑模式下工具条占了位置）：鼠标命中判断用。 */
    private int viewBottom;
    /** 提示显示到什么时候（客户端刻）。 */
    private long messageUntil;
    private long lastEditVersion = -1;

    /** 这一帧画出来的可点的东西。{@code label} 只给开发脚本按文字找，界面上不用。 */
    private record Hit(int x0, int y0, int x1, int y1, String label, Runnable action) {
        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    /** 开发脚本用：这一帧可点的东西都叫什么。见 {@code DevScript} 的 hits。 */
    public List<String> labels() {
        return this.hits.stream().map(Hit::label).toList();
    }

    /** 开发脚本用：按文字点这一帧画出来的东西（^ 开头时按开头比），返回有没有点到。见 {@code DevScript} 的 hit。 */
    public boolean clickByLabel(String text) {
        boolean start = text.startsWith("^");
        String want = start ? text.substring(1) : text;
        for (Hit hit : this.hits) {
            if (start ? hit.label().startsWith(want) : hit.label().contains(want)) {
                hit.action().run();
                return true;
            }
        }
        Universe.LOGGER.info("Dyson editor hit: no {} among {}", text, this.hits.stream().map(Hit::label).toList());
        return false;
    }

    public DysonEditorScreen() {
        super(Component.translatable("key.universe.dyson_editor"));
        this.camera.rotate(0, -22);
        this.camera.setDistance(VIEW_RADIUS * 3.2);
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 12, 520);
        this.panelHeight = Math.min(this.height - 12, 320);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Optional<DysonSphere> sphere() {
        OptionalInt system = ClientDyson.currentSystem();
        return system.isPresent() ? ClientDyson.sphere(system.getAsInt()) : Optional.empty();
    }

    private Optional<StarSystem> system() {
        OptionalInt index = ClientDyson.currentSystem();
        if (index.isEmpty()) {
            return Optional.empty();
        }
        return ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(index.getAsInt()));
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.hits.clear();
        int x0 = this.left;
        int y0 = this.top;
        int x1 = x0 + this.panelWidth;
        int y1 = y0 + this.panelHeight;
        graphics.fill(x0, y0, x1, y1, MachineScreen.BORDER);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, MachineScreen.PANEL);
        Optional<StarSystem> system = this.system();
        Component name = Component.literal(system.map(GalaxyText::system).orElse("—"));
        graphics.text(this.font, Component.translatable("gui.universe.dyson.title", name), x0 + 8, y0 + 6, MachineScreen.ACCENT, false);
        graphics.fill(x0 + 6, y0 + 16, x1 - 6, y0 + 17, 0x602E7A92);
        Optional<DysonSphere> found = this.sphere();
        if (ClientDyson.currentSystem().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.dyson.no_system"), (x0 + x1) / 2, (y0 + y1) / 2, MachineScreen.DIM);
            return;
        }
        if (found.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.dyson.waiting"), (x0 + x1) / 2, (y0 + y1) / 2, MachineScreen.DIM);
            return;
        }
        DysonSphere sphere = found.get();
        this.resolvePending(sphere);
        double limit = ClientDyson.payload().map(DysonSyncPayload::latitudeLimit).orElse(DysonSphere.LATITUDE_BASE);
        DysonEdit editor = this.editing(sphere);
        int viewX0 = x0 + 8 + LIST_WIDTH;
        int viewX1 = x1 - 8 - PROPS_WIDTH;
        int viewY0 = y0 + 20;
        // 编辑模式下工具条占两行，视图让出位置
        int viewY1 = y1 - 28 - (editor == null ? 0 : 2 * BUTTON + 4);
        this.viewBottom = viewY1;
        this.view(graphics, sphere, system.orElse(null), limit, viewX0, viewY0, viewX1, viewY1, partialTick);
        this.list(graphics, sphere, x0 + 6, viewY0, x0 + 6 + LIST_WIDTH, viewY1, mouseX, mouseY);
        this.properties(graphics, sphere, limit, viewX1 + 4, viewY0, x1 - 6, viewY1, mouseX, mouseY);
        if (editor != null) {
            DysonLayer layer = sphere.layer(editor.layerId());
            if (layer != null) {
                this.tools(graphics, editor, sphere, layer, x0 + 8, viewY1 + 3, x1 - 8, mouseX, mouseY);
            }
        }
        this.footer(graphics, sphere, limit, x0 + 8, y1 - 24, x1 - 8);
        if (this.message != null && Util.getMillis() < this.messageUntil) {
            graphics.text(this.font, this.message, viewX0 + 4, viewY0 + 4, this.messageBad ? MachineScreen.WARN : MachineScreen.GOOD, true);
        }
    }

    private void resolvePending(DysonSphere sphere) {
        if (this.pendingAdd == null || ClientDyson.version() == this.pendingVersion) {
            return;
        }
        Set<Integer> before = this.pendingAdd;
        this.pendingAdd = null;
        if (this.pendingOrbit) {
            sphere.orbits().stream().map(SwarmOrbit::id).filter(id -> !before.contains(id)).findFirst()
                    .ifPresent(id -> this.select(true, id));
        } else {
            sphere.layers().stream().map(DysonLayer::id).filter(id -> !before.contains(id)).findFirst()
                    .ifPresent(id -> this.select(false, id));
        }
    }

    private void select(boolean orbit, int id) {
        this.orbitSelected = orbit;
        this.selected = id;
        this.confirmDelete = false;
        this.edit = null;
    }

    /** 服务端回的编辑结果（ADR-0030 §四）。 */
    void showResult(DysonResultPayload result) {
        this.say(DysonResultText.of(result), !result.ok());
    }

    private void say(Component text, boolean bad) {
        this.message = text;
        this.messageBad = bad;
        this.messageUntil = Util.getMillis() + 6000;
    }

    private void problem(DysonDesign.@Nullable Problem problem) {
        if (problem != null) {
            this.say(Component.translatable(problem.key()), true);
        }
    }

    /** 正在编辑的那一层；选的不是层、没进编辑模式是 null。 */
    private @Nullable DysonEdit editing(DysonSphere sphere) {
        DysonEdit current = this.edit;
        if (current == null) {
            return null;
        }
        DysonLayer layer = sphere.layer(current.layerId());
        if (layer == null) {
            this.edit = null;
            return null;
        }
        if (this.lastEditVersion != ClientDyson.version()) {
            this.lastEditVersion = ClientDyson.version();
            current.rebase(layer);
        }
        return current;
    }

    /** 视图单位：半径上限画成 {@link #VIEW_RADIUS}。 */
    private static double viewScale(DysonSphere sphere) {
        return VIEW_RADIUS / sphere.maxRadius();
    }

    private double[] project(double x, double y, double z, double cx, double cy, double height) {
        StarMapCamera.Projected p = this.camera.project(x, y, z, cx, cy, height);
        return p == null ? null : new double[] {p.x(), p.y(), p.depth()};
    }

    private void view(GuiGraphicsExtractor graphics, DysonSphere sphere, @Nullable StarSystem system, double limit, int x0, int y0, int x1, int y1,
            float partialTick) {
        graphics.fill(x0, y0, x1, y1, MachineScreen.INSET);
        graphics.enableScissor(x0, y0, x1, y1);
        double cx = (x0 + x1) / 2.0;
        double cy = (y0 + y1) / 2.0;
        double height = y1 - y0;
        double scale = viewScale(sphere);
        if (!this.framed) {
            this.framed = true;
            this.frame(sphere, scale);
        }
        double time = this.time(partialTick);
        double focusDepth = this.camera.distance();
        // 行星轨道：半径上限三倍以内的
        if (system != null) {
            for (Planet planet : system.planets()) {
                double r = planet.orbit().radius() * scale;
                if (r > VIEW_RADIUS * 3.5) {
                    continue;
                }
                this.ring(r, 0, 0, 0, cx, cy, height, 0x40507888, 1);
                double angle = planet.orbit().angleAt(time / 24000.0);
                double[] p = this.project(Math.cos(angle) * r, 0, Math.sin(angle) * r, cx, cy, height);
                if (p != null) {
                    this.mesh.dot(p[0], p[1], 3, 0xFF8FB4C4);
                }
            }
            this.mesh.submit(graphics);
        }
        // 背面：比焦点远的部分先画，然后恒星，再画近的部分
        for (int pass = 0; pass < 2; pass++) {
            boolean far = pass == 0;
            for (DysonLayer layer : sphere.layers()) {
                this.layer(layer, limit, scale, time, cx, cy, height, focusDepth, far);
            }
            for (SwarmOrbit orbit : sphere.orbits()) {
                this.swarm(orbit, scale, time, cx, cy, height, focusDepth, far);
            }
            if (far) {
                int color = system == null ? 0xFFFFE6A0 : 0xFF000000 | system.star().color();
                double[] star = this.project(0, 0, 0, cx, cy, height);
                if (star != null) {
                    double radius = Math.max(3, 0.9 * scale * DysonSphere.MIN_RADIUS * height / 2 / Math.tan(Math.toRadians(30)) / star[2]);
                    this.mesh.disc(star[0], star[1], radius * 2.6, color & 0x60FFFFFF, color & 0x00FFFFFF);
                    this.mesh.disc(star[0], star[1], radius, 0xFFFFFFFF, color);
                }
            }
            this.mesh.submit(graphics);
        }
        // 编辑模式：手上这份设计画在最上面
        DysonEdit editor = this.edit;
        if (editor != null) {
            DysonLayer edited = sphere.layer(editor.layerId());
            if (edited != null) {
                this.overlay(graphics, editor, edited, scale, time, cx, cy, height);
            }
        }
        graphics.disableScissor();
        graphics.text(this.font, Component.translatable(editor == null ? "gui.universe.dyson.hint" : "gui.universe.dyson.edit.hint"), x0 + 4,
                y1 - 10, 0xFF5D7682, false);
    }

    /** 让最外面的轨道或层占满视图高度的八成左右；还什么都没有就看整个可建的范围。 */
    private void frame(DysonSphere sphere, double scale) {
        double outermost = 0;
        for (DysonLayer layer : sphere.layers()) {
            outermost = Math.max(outermost, layer.radius());
        }
        for (SwarmOrbit orbit : sphere.orbits()) {
            outermost = Math.max(outermost, orbit.radius());
        }
        double fit = outermost <= 0 ? VIEW_RADIUS : Math.max(outermost * scale, VIEW_RADIUS * 0.25) * 1.25;
        this.camera.setDistance(fit / Math.tan(Math.toRadians(StarMapCamera.FOV / 2)));
    }

    private double time(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime() + partialTick;
    }

    private void ring(double radius, double inclination, double node, double spin, double cx, double cy, double height, int color, float width) {
        int segments = 72;
        double[] previous = null;
        for (int i = 0; i <= segments; i++) {
            double a = Math.PI * 2 * i / segments;
            double[] point = DysonSphere.orient(new double[] {Math.cos(a), 0, Math.sin(a)}, inclination, node, spin);
            double[] p = this.project(point[0] * radius, point[1] * radius, point[2] * radius, cx, cy, height);
            if (p != null && previous != null) {
                this.mesh.line(previous[0], previous[1], p[0], p[1], width, color);
            }
            previous = p;
        }
    }

    private void swarm(SwarmOrbit orbit, double scale, double time, double cx, double cy, double height, double focusDepth, boolean far) {
        boolean chosen = this.orbitSelected && this.selected == orbit.id();
        double radius = orbit.radius() * scale;
        if (far) {
            this.ring(radius, orbit.inclination(), orbit.ascendingNode(), 0, cx, cy, height, chosen ? 0xC0F2C94C : 0x50F2C94C, chosen ? 1.5F : 1);
        }
        // 帆：沿轨道运动的光点，帆越多点越多
        int dots = (int) Math.min(240, orbit.sails() / 4 + (orbit.sails() > 0 ? 6 : 0));
        double phase = time / DysonSphere.sailPeriod(orbit.radius()) * Math.PI * 2;
        for (int i = 0; i < dots; i++) {
            double a = phase + i * 2.399963 + Math.sin(i * 12.9898) * 0.05;
            double wobble = 1 + Math.sin(i * 78.233) * 0.03;
            double[] point = DysonSphere.orient(new double[] {Math.cos(a) * wobble, Math.sin(i * 3.1) * 0.02, Math.sin(a) * wobble},
                    orbit.inclination(), orbit.ascendingNode(), 0);
            double[] p = this.project(point[0] * radius, point[1] * radius, point[2] * radius, cx, cy, height);
            if (p != null && p[2] > focusDepth == far) {
                this.mesh.dot(p[0], p[1], 1.5F, SWARM_COLOR);
            }
        }
    }

    private void layer(DysonLayer layer, double limit, double scale, double time, double cx, double cy, double height, double focusDepth,
            boolean far) {
        boolean chosen = !this.orbitSelected && this.selected == layer.id();
        DysonGrid grid = layer.grid();
        double radius = layer.radius() * scale;
        double spin = time / DysonSphere.spinPeriod(layer.radius()) * 360;
        double[][] points = new double[grid.nodeCount()][];
        for (int i = 0; i < grid.nodeCount(); i++) {
            double[] world = DysonSphere.orient(grid.node(i), layer.inclination(), layer.ascendingNode(), spin);
            points[i] = this.project(world[0] * radius, world[1] * radius, world[2] * radius, cx, cy, height);
        }
        // 球壳：铺了多少就多亮
        for (int s = 0; s < grid.shellCount(); s++) {
            int cells = layer.cells(s);
            if (cells <= 0) {
                continue;
            }
            int[] shell = grid.shell(s);
            if (!this.onSide(points, shell, focusDepth, far)) {
                continue;
            }
            double fill = cells / (double) layer.shellCost(s);
            int alpha = (int) (40 + 150 * fill);
            int color = alpha << 24 | 0xF0B040;
            double[] a = points[shell[0]];
            for (int k = 1; k + 1 < shell.length; k++) {
                double[] b = points[shell[k]];
                double[] c = points[shell[k + 1]];
                if (a != null && b != null && c != null) {
                    this.mesh.triangle((float) a[0], (float) a[1], (float) b[0], (float) b[1], (float) c[0], (float) c[1], color);
                }
            }
        }
        // 框架：建好的金色，建了一半的按进度画一截，没建的暗线，超出纬度限制的更暗
        for (int f = 0; f < grid.frameCount(); f++) {
            int[] ends = grid.frame(f);
            double[] a = points[ends[0]];
            double[] b = points[ends[1]];
            if (a == null || b == null || (a[2] + b[2]) / 2 > focusDepth != far) {
                continue;
            }
            boolean within = grid.frameWithin(f, limit);
            double progress = layer.frameSp(f) / (double) layer.frameCost(f);
            if (progress >= 1) {
                this.mesh.line(a[0], a[1], b[0], b[1], chosen ? 1.5F : 1.1F, FRAME_BUILT);
            } else {
                this.mesh.line(a[0], a[1], b[0], b[1], 0.8F, within ? (chosen ? 0x90A0D0E8 : FRAME_PLANNED) : FRAME_LOCKED);
                if (progress > 0) {
                    double mx = a[0] + (b[0] - a[0]) * progress;
                    double my = a[1] + (b[1] - a[1]) * progress;
                    this.mesh.line(a[0], a[1], mx, my, 1.1F, FRAME_BUILT);
                }
            }
        }
        for (int i = 0; i < grid.nodeCount(); i++) {
            double[] p = points[i];
            if (p == null || p[2] > focusDepth != far) {
                continue;
            }
            int sp = layer.nodeSp(i);
            if (sp >= DysonLayer.NODE_SP) {
                this.mesh.dot(p[0], p[1], chosen ? 3 : 2.5F, NODE_BUILT);
            } else if (grid.nodeWithin(i, limit)) {
                this.mesh.dot(p[0], p[1], 2, sp > 0 ? 0xFFE0A050 : 0xA080A8C0);
            }
        }
    }

    /**
     * 编辑模式：手上这份设计画在上面（没发出去的部分淡一些），选中的高亮。颜色按元素自己的颜色画，和天上一致。
     */
    private void overlay(GuiGraphicsExtractor graphics, DysonEdit editor, DysonLayer layer, double scale, double time, double cx, double cy,
            double height) {
        DysonDesign design = editor.design();
        double radius = layer.radius() * scale;
        double spin = time / DysonSphere.spinPeriod(layer.radius()) * 360;
        double[][] points = new double[design.nodeCount()][];
        for (int i = 0; i < design.nodeCount(); i++) {
            double[] world = DysonSphere.orient(design.position(i), layer.inclination(), layer.ascendingNode(), spin);
            points[i] = this.project(world[0] * radius, world[1] * radius, world[2] * radius, cx, cy, height);
        }
        boolean shells = editor.kind() == DysonLayer.Element.SHELL;
        for (int s = 0; s < design.shellCount(); s++) {
            int[] shell = design.shell(s).nodes();
            boolean picked = shells && editor.selection().contains(s);
            int rgb = DysonPalette.rgb(design.shell(s).color(), DysonPalette.SHELL);
            int color = (picked ? 0x90 : 0x40) << 24 | (picked ? PICKED & 0xFFFFFF : rgb);
            double[] a = points[shell[0]];
            for (int k = 1; k + 1 < shell.length; k++) {
                double[] b = points[shell[k]];
                double[] c = points[shell[k + 1]];
                if (a != null && b != null && c != null) {
                    this.mesh.triangle((float) a[0], (float) a[1], (float) b[0], (float) b[1], (float) c[0], (float) c[1], color);
                }
            }
        }
        boolean frames = editor.kind() == DysonLayer.Element.FRAME;
        for (int f = 0; f < design.frameCount(); f++) {
            double[] a = points[design.frame(f).a()];
            double[] b = points[design.frame(f).b()];
            if (a == null || b == null) {
                continue;
            }
            boolean picked = frames && editor.selection().contains(f);
            int rgb = DysonPalette.rgb(design.frame(f).color(), DysonPalette.FRAME);
            this.mesh.line(a[0], a[1], b[0], b[1], picked ? 2 : 1.2F, picked ? PICKED : 0xC0000000 | rgb);
        }
        boolean nodes = editor.kind() == DysonLayer.Element.NODE;
        for (int i = 0; i < design.nodeCount(); i++) {
            double[] p = points[i];
            if (p == null) {
                continue;
            }
            boolean picked = nodes && editor.selection().contains(i);
            int rgb = DysonPalette.rgb(design.node(i).color(), DysonPalette.NODE);
            if (i == editor.pendingNode()) {
                this.mesh.dot(p[0], p[1], 4, PENDING);
            } else {
                this.mesh.dot(p[0], p[1], picked ? 4 : 2.5F, picked ? PICKED : 0xE0000000 | rgb);
            }
        }
        this.mesh.submit(graphics);
    }

    private boolean onSide(double[][] points, int[] shell, double focusDepth, boolean far) {
        double depth = 0;
        for (int node : shell) {
            if (points[node] == null) {
                return false;
            }
            depth += points[node][2];
        }
        return depth / shell.length > focusDepth == far;
    }

    // ---- 左边的列表

    private void list(GuiGraphicsExtractor graphics, DysonSphere sphere, int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        graphics.fill(x0, y0, x1, y1, 0x80060C10);
        graphics.enableScissor(x0, y0, x1, y1);
        int y = y0 + 3 - this.listScroll;
        y = this.header(graphics, Component.translatable("gui.universe.dyson.swarm"), sphere.orbits().size(), DysonSphere.MAX_ORBITS, x0, y, x1,
                mouseX, mouseY, () -> this.add(sphere, true));
        for (SwarmOrbit orbit : sphere.orbits()) {
            boolean chosen = this.orbitSelected && this.selected == orbit.id();
            String detail = String.format(Locale.ROOT, "%.3f · %s", orbit.radius(), compact(orbit.sails()));
            y = this.row(graphics, Component.translatable("gui.universe.dyson.orbit", orbit.id()), detail, chosen, x0, y, x1, mouseX, mouseY,
                    () -> this.select(true, orbit.id()));
        }
        y += 4;
        y = this.header(graphics, Component.translatable("gui.universe.dyson.shells"), sphere.layers().size(), DysonSphere.MAX_LAYERS, x0, y, x1,
                mouseX, mouseY, () -> this.add(sphere, false));
        double limit = ClientDyson.payload().map(DysonSyncPayload::latitudeLimit).orElse(DysonSphere.LATITUDE_BASE);
        for (DysonLayer layer : sphere.layers()) {
            boolean chosen = !this.orbitSelected && this.selected == layer.id();
            long total = layer.structureCapacity(limit) + layer.cellCapacity(limit);
            long done = layer.structurePoints() + layer.cellPoints();
            String detail = String.format(Locale.ROOT, "%.3f · %d%%", layer.radius(), total == 0 ? 0 : Math.round(done * 100.0 / total));
            y = this.row(graphics, Component.translatable("gui.universe.dyson.layer", layer.id()), detail, chosen, x0, y, x1, mouseX, mouseY,
                    () -> this.select(false, layer.id()));
        }
        graphics.disableScissor();
        int content = y + this.listScroll - y0;
        this.listScroll = Math.max(0, Math.min(this.listScroll, content - (y1 - y0)));
    }

    private int header(GuiGraphicsExtractor graphics, Component label, int count, int max, int x0, int y, int x1, int mouseX, int mouseY,
            Runnable add) {
        graphics.text(this.font, label.copy().append(" " + count + "/" + max), x0 + 4, y + 2, MachineScreen.ACCENT, false);
        if (count < max) {
            this.button(graphics, "+", x1 - 4 - BUTTON, y, BUTTON, mouseX, mouseY, true, add);
        }
        return y + ROW + 1;
    }

    private int row(GuiGraphicsExtractor graphics, Component label, String detail, boolean chosen, int x0, int y, int x1, int mouseX, int mouseY,
            Runnable action) {
        boolean hovered = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + ROW;
        if (chosen || hovered) {
            graphics.fill(x0 + 1, y, x1 - 1, y + ROW, chosen ? 0xFF1F4A5A : 0xFF12303B);
        }
        graphics.text(this.font, label, x0 + 6, y + 2, chosen ? MachineScreen.TEXT : MachineScreen.DIM, false);
        graphics.text(this.font, detail, x1 - 4 - this.font.width(detail), y + 2, chosen ? MachineScreen.TEXT : 0xFF6F8A96, false);
        this.hits.add(new Hit(x0, y, x1, y + ROW, label.getString(), action));
        return y + ROW;
    }

    private void button(GuiGraphicsExtractor graphics, String label, int x, int y, int width, int mouseX, int mouseY, boolean enabled,
            Runnable action) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON;
        graphics.fill(x, y, x + width, y + BUTTON, enabled ? MachineScreen.BORDER : 0xFF1A2A33);
        graphics.fill(x + 1, y + 1, x + width - 1, y + BUTTON - 1, !enabled ? 0xFF0C151B : hovered ? 0xFF1F4A5A : 0xFF12303B);
        graphics.centeredText(this.font, label, x + width / 2, y + 2, enabled ? MachineScreen.TEXT : MachineScreen.DIM);
        if (enabled) {
            this.hits.add(new Hit(x, y, x + width, y + BUTTON, label, action));
        }
    }

    private void button(GuiGraphicsExtractor graphics, Component label, int x, int y, int width, int mouseX, int mouseY, boolean enabled,
            Runnable action) {
        this.button(graphics, label.getString(), x, y, width, mouseX, mouseY, enabled, action);
    }

    // ---- 右边的属性

    private void properties(GuiGraphicsExtractor graphics, DysonSphere sphere, double limit, int x0, int y0, int x1, int y1, int mouseX,
            int mouseY) {
        graphics.fill(x0, y0, x1, y1, 0x80060C10);
        int x = x0 + 5;
        int width = x1 - x0 - 10;
        int y = y0 + 4;
        SwarmOrbit orbit = this.orbitSelected ? sphere.orbit(this.selected) : null;
        DysonLayer layer = this.orbitSelected ? null : sphere.layer(this.selected);
        if (orbit == null && layer == null) {
            y += this.wrapped(graphics, Component.translatable("gui.universe.dyson.light", String.format(Locale.ROOT, "%.2f", sphere.lightFactor()),
                    String.format(Locale.ROOT, "%.3f", sphere.maxRadius())), x, y, width, MachineScreen.DIM, false) + 4;
            if (sphere.orbits().isEmpty() && sphere.layers().isEmpty()) {
                this.wrapped(graphics, Component.translatable("gui.universe.dyson.empty"), x, y, width, MachineScreen.TEXT, true);
            }
            return;
        }
        boolean isOrbit = orbit != null;
        double radius = isOrbit ? orbit.radius() : layer.radius();
        double inclination = isOrbit ? orbit.inclination() : layer.inclination();
        double node = isOrbit ? orbit.ascendingNode() : layer.ascendingNode();
        int id = isOrbit ? orbit.id() : layer.id();
        DysonGrid.Template template = isOrbit ? DysonGrid.Template.GEODESIC : layer.grid().template();
        int density = isOrbit ? 1 : layer.grid().density();
        boolean shape = isOrbit || layer.empty();
        graphics.text(this.font, Component.translatable(isOrbit ? "gui.universe.dyson.orbit" : "gui.universe.dyson.layer", id), x, y,
                MachineScreen.ACCENT, false);
        y += ROW + 2;
        boolean fine = Minecraft.getInstance().hasShiftDown();
        double radiusStep = fine ? 0.002 : sphere.maxRadius() > 0.5 ? 0.02 : 0.01;
        y = this.stepper(graphics, Component.translatable("gui.universe.dyson.radius", String.format(Locale.ROOT, "%.3f", radius)), x, y, x1 - 5,
                mouseX, mouseY, shape,
                () -> this.edit(isOrbit, id, radius - radiusStep, inclination, node, template, density),
                () -> this.edit(isOrbit, id, radius + radiusStep, inclination, node, template, density));
        double angleStep = fine ? 1 : 5;
        y = this.stepper(graphics, Component.translatable("gui.universe.dyson.inclination", Math.round(inclination)), x, y, x1 - 5, mouseX, mouseY,
                true, () -> this.edit(isOrbit, id, radius, inclination - angleStep, node, template, density),
                () -> this.edit(isOrbit, id, radius, inclination + angleStep, node, template, density));
        double nodeStep = fine ? 5 : 15;
        y = this.stepper(graphics, Component.translatable("gui.universe.dyson.node", Math.round(node)), x, y, x1 - 5, mouseX, mouseY, true,
                () -> this.edit(isOrbit, id, radius, inclination, node - nodeStep, template, density),
                () -> this.edit(isOrbit, id, radius, inclination, node + nodeStep, template, density));
        if (isOrbit) {
            y += 4;
            graphics.text(this.font, Component.translatable("gui.universe.dyson.sails", String.format(Locale.ROOT, "%,d", orbit.sails())), x, y,
                    MachineScreen.TEXT, false);
            y += ROW;
            double power = orbit.sails() * DysonSphere.SAIL_POWER * sphere.lightFactor();
            graphics.text(this.font, Component.translatable("gui.universe.dyson.power", watts(power)), x, y, SWARM_COLOR, false);
            y += ROW;
            graphics.text(this.font, Component.translatable("gui.universe.dyson.sail_life"), x, y, MachineScreen.DIM, false);
        } else {
            Component templateName = Component.translatable("gui.universe.dyson.template." + template.ordinal());
            y = this.stepper(graphics, Component.translatable("gui.universe.dyson.template").copy().append(" ").append(templateName), x, y, x1 - 5,
                    mouseX, mouseY, shape,
                    () -> this.edit(false, id, radius, inclination, node, DysonGrid.Template.byOrdinal(template.ordinal() - 1), density),
                    () -> this.edit(false, id, radius, inclination, node, DysonGrid.Template.byOrdinal(template.ordinal() + 1), density));
            y = this.stepper(graphics, Component.translatable("gui.universe.dyson.density", density), x, y, x1 - 5, mouseX, mouseY,
                    shape && density > 0, () -> this.edit(false, id, radius, inclination, node, template, Math.max(1, density - 1)),
                    () -> this.edit(false, id, radius, inclination, node, template, Math.min(template.maxDensity(), density + 1)));
            if (!shape) {
                y += this.wrapped(graphics, Component.translatable("gui.universe.dyson.locked"), x, y, width, MachineScreen.WARN, false);
            }
            y += 3;
            y = this.gridRow(graphics, layer, x, y, x1 - 5, mouseX, mouseY);
            y = this.layerStats(graphics, sphere, layer, limit, x, y, width);
        }
        // 删除：点两次
        int deleteY = y1 - 4 - BUTTON;
        Component label = Component.translatable(this.confirmDelete ? "gui.universe.dyson.confirm" : "gui.universe.dyson.remove");
        int deleteWidth = this.font.width(label) + 12;
        this.button(graphics, label, x1 - 5 - deleteWidth, deleteY, deleteWidth, mouseX, mouseY, true, () -> {
            if (!this.confirmDelete) {
                this.confirmDelete = true;
                return;
            }
            this.send(new DysonEditPayload(isOrbit ? DysonEditPayload.Action.REMOVE_ORBIT : DysonEditPayload.Action.REMOVE_LAYER, id, 0, 0, 0, 0, 0));
            this.selected = 0;
            this.confirmDelete = false;
        });
    }

    private int layerStats(GuiGraphicsExtractor graphics, DysonSphere sphere, DysonLayer layer, double limit, int x, int y, int width) {
        DysonGrid grid = layer.grid();
        int nodes = 0;
        int nodesBuilt = 0;
        for (int i = 0; i < grid.nodeCount(); i++) {
            if (grid.nodeWithin(i, limit)) {
                nodes++;
                nodesBuilt += layer.nodeBuilt(i) ? 1 : 0;
            }
        }
        int frames = 0;
        int framesBuilt = 0;
        for (int i = 0; i < grid.frameCount(); i++) {
            if (grid.frameWithin(i, limit)) {
                frames++;
                framesBuilt += layer.frameBuilt(i) ? 1 : 0;
            }
        }
        int shells = 0;
        int shellsFull = 0;
        for (int i = 0; i < grid.shellCount(); i++) {
            if (grid.shellWithin(i, limit)) {
                shells++;
                shellsFull += layer.shellFull(i) ? 1 : 0;
            }
        }
        y += this.wrapped(graphics, Component.translatable("gui.universe.dyson.elements", nodesBuilt, nodes, framesBuilt, frames, shellsFull, shells),
                x, y, width, MachineScreen.TEXT, false) + 2;
        long spCapacity = layer.structureCapacity(limit);
        long cellCapacity = layer.cellCapacity(limit);
        graphics.text(this.font, Component.translatable("gui.universe.dyson.sp", compact(layer.structurePoints()), compact(spCapacity)), x, y,
                MachineScreen.TEXT, false);
        y += 10;
        MachineScreen.bar(graphics, x, y, width, 3, spCapacity == 0 ? 0 : layer.structurePoints() / (float) spCapacity, FRAME_BUILT);
        y += 6;
        graphics.text(this.font, Component.translatable("gui.universe.dyson.cells", compact(layer.cellPoints()), compact(cellCapacity)), x, y,
                MachineScreen.TEXT, false);
        y += 10;
        MachineScreen.bar(graphics, x, y, width, 3, cellCapacity == 0 ? 0 : layer.cellPoints() / (float) cellCapacity, 0xFFF0B040);
        y += 7;
        double now = (layer.structurePoints() * DysonSphere.SP_POWER + layer.cellPoints() * DysonSphere.CELL_POWER) * sphere.lightFactor();
        double full = (spCapacity * DysonSphere.SP_POWER + cellCapacity * DysonSphere.CELL_POWER) * sphere.lightFactor();
        graphics.text(this.font, Component.translatable("gui.universe.dyson.power", watts(now)), x, y, MachineScreen.GOOD, false);
        y += ROW;
        graphics.text(this.font, Component.translatable("gui.universe.dyson.capacity", watts(full)), x, y, MachineScreen.DIM, false);
        return y + ROW;
    }

    // ---- 自由编辑（ADR-0030 §四）

    /** 网格那一行：进出编辑模式、导入导出蓝图。 */
    private int gridRow(GuiGraphicsExtractor graphics, DysonLayer layer, int x, int y, int right, int mouseX, int mouseY) {
        boolean editing = this.edit != null && this.edit.layerId() == layer.id();
        Component label = Component.translatable(editing ? "gui.universe.dyson.edit.done" : "gui.universe.dyson.edit.start");
        int buttonWidth = this.font.width(label) + 10;
        graphics.text(this.font, Component.translatable("gui.universe.dyson.grid"), x, y + 2, MachineScreen.TEXT, false);
        this.button(graphics, label, right - buttonWidth, y, buttonWidth, mouseX, mouseY, true, () -> {
            if (editing) {
                this.edit = null;
            } else {
                this.edit = new DysonEdit(layer);
                this.lastEditVersion = ClientDyson.version();
            }
        });
        y += ROW + 1;
        Component paste = Component.translatable("gui.universe.dyson.blueprint.import");
        Component copy = Component.translatable("gui.universe.dyson.blueprint.export");
        int pasteWidth = this.font.width(paste) + 8;
        int copyWidth = this.font.width(copy) + 8;
        this.button(graphics, copy, right - copyWidth, y, copyWidth, mouseX, mouseY, true, () -> this.exportBlueprint(layer));
        this.button(graphics, paste, right - copyWidth - pasteWidth - 2, y, pasteWidth, mouseX, mouseY, layer.empty(),
                () -> Minecraft.getInstance().gui.setScreen(new DysonBlueprintScreen(this, layer.id())));
        return y + ROW + 2;
    }

    /** 导出这一层：复制到剪贴板，同时存进本地蓝图库。 */
    private void exportBlueprint(DysonLayer layer) {
        String text = DysonBlueprint.export(DysonDesign.of(layer));
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        String name = DysonLibrary.save("layer_" + layer.id(), text);
        this.say(name == null ? Component.translatable("gui.universe.dyson.blueprint.copied")
                : Component.translatable("gui.universe.dyson.blueprint.saved", name), false);
    }

    /** 从蓝图串导入到这一层（{@link DysonBlueprintScreen} 调）。 */
    void importBlueprint(int layerId, String text) {
        this.send(DysonLayerEditPayload.importing(layerId, text));
    }

    /** 编辑模式的工具条、调色盘和几个按钮，画在视图下面。 */
    private void tools(GuiGraphicsExtractor graphics, DysonEdit editor, DysonSphere sphere, DysonLayer layer, int x0, int y, int x1, int mouseX,
            int mouseY) {
        int x = x0;
        for (DysonEdit.Tool tool : DysonEdit.Tool.values()) {
            Component label = Component.translatable(tool.key());
            int width = this.font.width(label) + 8;
            boolean chosen = editor.tool() == tool;
            graphics.fill(x, y, x + width, y + BUTTON, chosen ? MachineScreen.ACCENT : MachineScreen.BORDER);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON;
            graphics.fill(x + 1, y + 1, x + width - 1, y + BUTTON - 1, chosen ? 0xFF1F4A5A : hovered ? 0xFF163B49 : 0xFF0E242D);
            graphics.centeredText(this.font, label, x + width / 2, y + 2, chosen ? MachineScreen.TEXT : MachineScreen.DIM);
            this.hits.add(new Hit(x, y, x + width, y + BUTTON, label.getString(), () -> editor.setTool(tool)));
            x += width + 2;
        }
        // 选的种类
        x += 4;
        for (DysonLayer.Element kind : DysonLayer.Element.values()) {
            Component label = Component.translatable("gui.universe.dyson.element." + kind.ordinal());
            int width = this.font.width(label) + 8;
            boolean chosen = editor.kind() == kind;
            graphics.fill(x, y, x + width, y + BUTTON, chosen ? MachineScreen.ACCENT : MachineScreen.BORDER);
            graphics.fill(x + 1, y + 1, x + width - 1, y + BUTTON - 1, chosen ? 0xFF1F4A5A : 0xFF0E242D);
            graphics.centeredText(this.font, label, x + width / 2, y + 2, chosen ? MachineScreen.TEXT : MachineScreen.DIM);
            this.hits.add(new Hit(x, y, x + width, y + BUTTON, label.getString(), () -> editor.setKind(kind)));
            x += width + 2;
        }
        int row2 = y + BUTTON + 2;
        // 调色盘：默认色 + 预设
        int sx = x0;
        this.swatch(graphics, sx, row2, 0, editor, mouseX, mouseY);
        sx += SWATCH + 2;
        for (int preset : DysonPalette.PRESETS) {
            this.swatch(graphics, sx, row2, preset, editor, mouseX, mouseY);
            sx += SWATCH + 1;
        }
        sx += 4;
        // 上色、拆、铺满、撤销、还原、应用
        sx = this.action(graphics, "gui.universe.dyson.edit.paint", sx, row2, mouseX, mouseY, !editor.selection().isEmpty(), () -> {
            DysonLayerEditPayload payload = editor.paint();
            if (payload != null) {
                this.send(payload);
            }
        });
        sx = this.action(graphics, "gui.universe.dyson.edit.demolish", sx, row2, mouseX, mouseY, editor.anyBuilt(layer), () -> {
            DysonLayerEditPayload payload = editor.demolish();
            if (payload != null) {
                this.send(payload);
            }
        });
        sx = this.action(graphics, "gui.universe.dyson.edit.fill", sx, row2, mouseX, mouseY, true, () -> {
            int added = editor.fill();
            this.say(Component.translatable("gui.universe.dyson.edit.filled", added), added == 0);
        });
        sx = this.action(graphics, "gui.universe.dyson.edit.undo", sx, row2, mouseX, mouseY, editor.canUndo(), editor::undo);
        sx = this.action(graphics, "gui.universe.dyson.edit.revert", sx, row2, mouseX, mouseY, editor.dirty(), editor::revert);
        this.action(graphics, "gui.universe.dyson.edit.apply", sx, row2, mouseX, mouseY, editor.dirty(), () -> {
            DysonDesign.Problem problem = editor.validate();
            if (problem != null) {
                this.problem(problem);
                return;
            }
            DysonSphere.EditResult check = editor.check(sphere);
            if (check != DysonSphere.EditResult.OK) {
                this.say(Component.translatable(check.key()), true);
                return;
            }
            this.send(editor.apply());
        });
    }

    private void swatch(GuiGraphicsExtractor graphics, int x, int y, int color, DysonEdit editor, int mouseX, int mouseY) {
        boolean chosen = editor.color() == color;
        graphics.fill(x, y, x + SWATCH, y + SWATCH, chosen ? MachineScreen.ACCENT : MachineScreen.BORDER);
        int rgb = color == 0 ? DysonPalette.FRAME : color;
        graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0xFF000000 | rgb);
        if (color == 0) {
            // 默认色：中间一道斜杠，和预设区分开
            graphics.fill(x + 3, y + SWATCH / 2, x + SWATCH - 3, y + SWATCH / 2 + 1, 0xFF0A1015);
        }
        // 色块没有文字，给开发脚本留个记号：默认色是 color:0，预设是 color:RRGGBB
        this.hits.add(new Hit(x, y, x + SWATCH, y + SWATCH, String.format(Locale.ROOT, "color:%06x", rgb & 0xFFFFFF),
                () -> editor.setColor(color)));
    }

    private int action(GuiGraphicsExtractor graphics, String key, int x, int y, int mouseX, int mouseY, boolean enabled, Runnable run) {
        Component label = Component.translatable(key);
        int width = this.font.width(label) + 8;
        this.button(graphics, label, x, y, width, mouseX, mouseY, enabled, run);
        return x + width + 2;
    }

    /** 一行：标签，右边 − + 两个按钮。 */
    private int stepper(GuiGraphicsExtractor graphics, Component label, int x, int y, int right, int mouseX, int mouseY, boolean enabled,
            Runnable minus, Runnable plus) {
        graphics.text(this.font, label, x, y + 2, enabled ? MachineScreen.TEXT : MachineScreen.DIM, false);
        this.button(graphics, "−", right - 2 * BUTTON - 2, y, BUTTON, mouseX, mouseY, enabled, minus);
        this.button(graphics, "+", right - BUTTON, y, BUTTON, mouseX, mouseY, enabled, plus);
        return y + ROW + 1;
    }

    private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color, boolean shadow) {
        TextWrap.draw(graphics, this.font, text, x, y, width, color, shadow);
        return TextWrap.split(this.font, text, width).size() * this.font.lineHeight;
    }

    private void footer(GuiGraphicsExtractor graphics, DysonSphere sphere, double limit, int x0, int y, int x1) {
        double drawn = ClientDyson.payload().map(DysonSyncPayload::drawn).orElse(0.0);
        int rockets = ClientDyson.payload().map(DysonSyncPayload::rockets).orElse(0);
        Component power = Component.translatable("gui.universe.dyson.power", watts(sphere.generation())).copy().append("  ")
                .append(Component.translatable("gui.universe.dyson.power_split", watts(sphere.swarmPower()), watts(sphere.shellPower())));
        graphics.text(this.font, power, x0, y, MachineScreen.GOOD, false);
        Component second = Component.translatable("gui.universe.dyson.drawn", watts(drawn)).copy().append("  ")
                .append(Component.translatable("gui.universe.dyson.latitude", Math.round(limit))).append("  ")
                .append(Component.translatable("gui.universe.dyson.rockets", rockets));
        graphics.text(this.font, second, x0, y + 11, MachineScreen.DIM, false);
    }

    // ---- 操作

    private void add(DysonSphere sphere, boolean orbit) {
        Set<Integer> ids = new HashSet<>();
        if (orbit) {
            sphere.orbits().forEach(o -> ids.add(o.id()));
        } else {
            sphere.layers().forEach(l -> ids.add(l.id()));
        }
        this.pendingAdd = ids;
        this.pendingOrbit = orbit;
        this.pendingVersion = ClientDyson.version();
        // 新的轨道、层放在已有的外面一点
        double outermost = 0;
        for (SwarmOrbit o : sphere.orbits()) {
            outermost = orbit ? Math.max(outermost, o.radius()) : outermost;
        }
        for (DysonLayer l : sphere.layers()) {
            outermost = orbit ? outermost : Math.max(outermost, l.radius());
        }
        double radius = outermost <= 0 ? sphere.maxRadius() * (orbit ? 0.6 : 0.4) : Math.min(sphere.maxRadius(), outermost * 1.25);
        this.send(new DysonEditPayload(orbit ? DysonEditPayload.Action.ADD_ORBIT : DysonEditPayload.Action.ADD_LAYER, 0, radius, 0, 0,
                DysonGrid.Template.GEODESIC.ordinal(), 1));
    }

    private void edit(boolean orbit, int id, double radius, double inclination, double node, DysonGrid.Template template, int density) {
        this.confirmDelete = false;
        double rounded = Math.round(radius * 1000) / 1000.0;
        this.send(new DysonEditPayload(orbit ? DysonEditPayload.Action.UPDATE_ORBIT : DysonEditPayload.Action.UPDATE_LAYER, id, rounded,
                inclination, node, template.ordinal(), density));
    }

    private void send(DysonEditPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    private void send(DysonLayerEditPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    // ---- 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Hit hit : List.copyOf(this.hits)) {
            if (hit.contains(event.x(), event.y())) {
                hit.action.run();
                return true;
            }
        }
        if (this.inView(event.x(), event.y())) {
            // 编辑模式：左键是工具，右键照旧转镜头
            if (event.button() == 0 && this.editClick(event.x(), event.y())) {
                return true;
            }
            this.dragging = true;
            this.lastMouseX = event.x();
            this.lastMouseY = event.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** 编辑模式下在视图里点了一下；没在编辑、没打中球面返回 false（照旧转镜头）。 */
    private boolean editClick(double mouseX, double mouseY) {
        DysonEdit editor = this.edit;
        Optional<DysonSphere> found = this.sphere();
        if (editor == null || found.isEmpty()) {
            return false;
        }
        DysonSphere sphere = found.get();
        DysonLayer layer = sphere.layer(editor.layerId());
        if (layer == null) {
            return false;
        }
        double[] p = this.pointAt(sphere, layer, mouseX, mouseY);
        if (p == null) {
            return false;
        }
        if (editor.startDrag(p, layer)) {
            return true;
        }
        this.problem(editor.click(p, snapStep(), layer, Minecraft.getInstance().hasShiftDown()));
        if (editor.tool() == DysonEdit.Tool.PAINT && !editor.selection().isEmpty()) {
            DysonLayerEditPayload payload = editor.paint();
            if (payload != null) {
                this.send(payload);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        if (this.edit != null) {
            this.edit.endDrag();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        DysonEdit editor = this.edit;
        if (editor != null && editor.dragging()) {
            Optional<DysonSphere> found = this.sphere();
            DysonLayer layer = found.map(sphere -> sphere.layer(editor.layerId())).orElse(null);
            if (layer != null) {
                double[] p = this.pointAt(found.get(), layer, event.x(), event.y());
                if (p != null) {
                    editor.drag(p, snapStep());
                }
            }
            return true;
        }
        if (this.dragging) {
            this.camera.rotate(-(event.x() - this.lastMouseX) * 0.5, (event.y() - this.lastMouseY) * 0.5);
            this.lastMouseX = event.x();
            this.lastMouseY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < this.left + 8 + LIST_WIDTH) {
            this.listScroll = Math.max(0, this.listScroll - (int) Math.round(scrollY * ROW));
            return true;
        }
        if (this.inView(mouseX, mouseY)) {
            this.camera.zoom(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean inView(double x, double y) {
        return x >= this.left + 8 + LIST_WIDTH && x < this.left + this.panelWidth - 8 - PROPS_WIDTH && y >= this.top + 20
                && y < (this.viewBottom > 0 ? this.viewBottom : this.top + this.panelHeight - 28);
    }

    /**
     * 屏幕上的一点打在正在编辑的那一层上的位置，换算回层自己的坐标；没打中（射线没碰到球）是 null。
     */
    private double @Nullable [] pointAt(DysonSphere sphere, DysonLayer layer, double mouseX, double mouseY) {
        int viewX0 = this.left + 8 + LIST_WIDTH;
        int viewX1 = this.left + this.panelWidth - 8 - PROPS_WIDTH;
        int viewY0 = this.top + 20;
        int viewY1 = this.viewBottom > 0 ? this.viewBottom : this.top + this.panelHeight - 28;
        double cx = (viewX0 + viewX1) / 2.0;
        double cy = (viewY0 + viewY1) / 2.0;
        double height = viewY1 - viewY0;
        double[] eye = this.camera.eye();
        // 屏幕坐标反推视线方向：project 的逆
        double focal = height / 2 / Math.tan(Math.toRadians(StarMapCamera.FOV / 2));
        double yaw = Math.toRadians(this.camera.yaw());
        double pitch = Math.toRadians(this.camera.pitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = -Math.cos(yaw) * Math.cos(pitch);
        double rx = Math.cos(yaw);
        double rz = -Math.sin(yaw);
        double upX = -rz * fy;
        double upY = rz * fx - rx * fz;
        double upZ = rx * fy;
        double sx = (mouseX - cx) / focal;
        double sy = -(mouseY - cy) / focal;
        double[] direction = {fx + rx * sx + upX * sy, fy + upY * sy, fz + rz * sx + upZ * sy};
        double radius = layer.radius() * viewScale(sphere);
        double[] hit = SphereGeometry.rayHit(eye, direction, radius);
        if (hit == null) {
            return null;
        }
        double time = this.time(0);
        double spin = time / DysonSphere.spinPeriod(layer.radius()) * 360;
        return SphereGeometry.unorient(SphereGeometry.normalize(hit), layer.inclination(), layer.ascendingNode(), spin);
    }

    /** 当前吸附的格子：按住 Shift 细一些。 */
    private static double snapStep() {
        return Minecraft.getInstance().hasShiftDown() ? DysonEdit.SNAP_FINE : DysonEdit.SNAP;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UniverseKeys.DYSON_EDITOR.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // ---- 格式

    /** 焦/刻换成瓦特（1 焦/刻在界面上是 20 W）。 */
    static String watts(double joulesPerTick) {
        return MachineScreen.watts(Math.round(joulesPerTick * 10));
    }

    static String compact(long value) {
        if (value < 10_000) {
            return Long.toString(value);
        }
        if (value < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        }
        return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
    }
}
