package io.github.sweetcornna.universe.client.stats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.network.StatsDetailPayload;
import io.github.sweetcornna.universe.network.StatsDysonPayload;
import io.github.sweetcornna.universe.network.StatsRequestPayload;
import io.github.sweetcornna.universe.network.StatsSnapshotPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 统计面板（ADR-0015、ADR-0033 §四，默认 O 键），三页：
 * <ul>
 * <li>生产：每种物品每分钟的产出和消耗，搜索框找物品；选中一种物品，右边是它的曲线、参考产量（在用这个配方、正在运转的
 * 设备满速时每分钟做多少）、星际物流的进出口、上下游配方（点一下跳到那种物品）</li>
 * <li>电力：发电、用电、充电的环形图，按设备的种类分开</li>
 * <li>戴森球：每一层的节点、框架、壳面和发电，太阳帆，接收站取走的电</li>
 * </ul>
 * 看的地方可以是全部、一个星系或一颗星球。图都能钉到仪表盘上。开着时每两秒向服务端要一次。
 */
public class StatsScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int MAX_WIDTH = 460;
    private static final int DETAIL_WIDTH = 200;
    private static final int ROW_HEIGHT = 18;
    private static final int RANGE_WIDTH = 44;
    private static final int TAB_WIDTH = 54;
    private static final int CONTENT_Y = 42;
    private static final int TABLE_Y = 74;
    private static final int RECIPE_ROW = 20;
    private static final int REFRESH_TICKS = 40;
    private static final int IMPORT_COLOR = 0xFF4FC3E0;
    private static final String[] TABS = {"production", "power", "dyson"};

    private enum Sort {
        VOLUME, NAME, PRODUCED, CONSUMED, NET
    }

    /** 能滚动的清单里的一行：小标题（heading）、一句灰字，或者一个配方、一种设备。 */
    private record Line(@Nullable Component label, boolean heading, @Nullable Object value, boolean consumer) {
        static Line heading(Component label) {
            return new Line(label, true, null, false);
        }

        static Line note(Component label) {
            return new Line(label, false, null, false);
        }

        static Line of(Object value, boolean consumer) {
            return new Line(null, false, value, consumer);
        }
    }

    /** 看的地方：维度、{@code system:<序号>}，空字符串是全部。 */
    private String scope;
    private int range;
    private int tab;
    private String selected;
    private final Deque<String> back = new ArrayDeque<>();
    private Sort sort = Sort.VOLUME;
    private int scroll;
    private int detailScroll;
    private int sideScroll;
    private int refresh;
    private int panelWidth;
    private int left;
    private int top;
    private int panelHeight;
    private EditBox search;
    /** 这一帧鼠标下的东西，画完再出提示。 */
    private ItemStack hoveredItem = ItemStack.EMPTY;
    private List<Component> hoveredText = List.of();
    /** 这一帧画出来的可以点的物品图标：x、y、物品在 items 里的序号。 */
    private final List<int[]> itemHits = new ArrayList<>();
    private final List<String> hitItems = new ArrayList<>();

    public StatsScreen(String scope, int range, int tab, String item) {
        super(Component.translatable("gui.universe.stats.title"));
        this.scope = scope;
        this.range = range;
        this.tab = Math.floorMod(tab, TABS.length);
        this.selected = item;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - 8);
        this.panelHeight = Math.min(this.height - 16, 320);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        String text = this.search == null ? "" : this.search.getValue();
        this.search = new EditBox(this.font, this.left + 8, this.top + CONTENT_Y, this.tableWidth(), 14, Component.translatable("gui.universe.stats.search"));
        this.search.setHint(Component.translatable("gui.universe.stats.search"));
        this.search.setMaxLength(48);
        this.search.setValue(text);
        this.search.setResponder(value -> this.scroll = 0);
        this.search.visible = this.tab == 0;
        this.addRenderableWidget(this.search);
        this.request();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void request() {
        this.refresh = REFRESH_TICKS;
        ClientPacketDistributor.sendToServer(new StatsRequestPayload(this.scope, this.range, this.tab == 0 ? this.selected : "", this.tab));
        StatsClient.remember(this.scope, this.range, this.tab, this.selected);
    }

    @Override
    public void tick() {
        if (--this.refresh <= 0) {
            this.request();
        }
    }

    /** 快照对得上现在看的地方和时间范围时才用，免得切换的一瞬间显示旧的。 */
    private Optional<StatsSnapshotPayload> snapshot() {
        return StatsClient.snapshot().filter(s -> s.scope().equals(this.scope) && s.range() == this.range);
    }

    private Optional<StatsDetailPayload> detail() {
        return StatsClient.detail().filter(d -> d.scope().equals(this.scope) && d.range() == this.range && d.item().equals(this.selected));
    }

    // ---- 排版

    private int tableWidth() {
        return this.panelWidth - 16 - DETAIL_WIDTH - 8;
    }

    private int detailX() {
        return this.panelWidth - 8 - DETAIL_WIDTH;
    }

    private int nameRight() {
        return this.tableWidth() - 108;
    }

    private int producedRight() {
        return this.tableWidth() - 72;
    }

    private int consumedRight() {
        return this.tableWidth() - 38;
    }

    private int netRight() {
        return this.tableWidth() - 6;
    }

    private int visibleRows() {
        return Math.max(1, (this.panelHeight - TABLE_Y - 8) / ROW_HEIGHT);
    }

    /** 表里的物品：服务端的数加上搜索出来的、还没有数的本模组物品。 */
    private List<StatsSnapshotPayload.Row> rows(StatsSnapshotPayload snapshot) {
        String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        List<StatsSnapshotPayload.Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (StatsSnapshotPayload.Row row : snapshot.rows()) {
            seen.add(row.item());
            if (matches(row.item(), query)) {
                rows.add(row);
            }
        }
        if (!query.isEmpty()) {
            int extra = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (extra >= 64 || !id.getNamespace().equals(Universe.MOD_ID) || seen.contains(id.toString())) {
                    continue;
                }
                if (matches(id.toString(), query)) {
                    rows.add(new StatsSnapshotPayload.Row(id.toString(), 0, 0, 0, 0));
                    extra++;
                }
            }
        }
        switch (this.sort) {
            case NAME -> rows.sort(Comparator.comparing(row -> name(row.item())));
            case PRODUCED -> rows.sort(Comparator.comparingDouble((StatsSnapshotPayload.Row row) -> row.produced()).reversed());
            case CONSUMED -> rows.sort(Comparator.comparingDouble((StatsSnapshotPayload.Row row) -> row.consumed()).reversed());
            case NET -> rows.sort(Comparator.comparingDouble(row -> row.consumed() + row.exported() - row.produced() - row.imported()));
            default -> {
            }
        }
        return rows;
    }

    private static boolean matches(String item, String query) {
        return query.isEmpty() || item.toLowerCase(Locale.ROOT).contains(query) || name(item).toLowerCase(Locale.ROOT).contains(query);
    }

    private static String name(String id) {
        return StatsCharts.itemName(id).getString();
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.hoveredItem = ItemStack.EMPTY;
        this.hoveredText = List.of();
        this.itemHits.clear();
        this.hitItems.clear();
        int x = this.left;
        int y = this.top;
        graphics.fill(x, y, x + this.panelWidth, y + this.panelHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + this.panelWidth - 1, y + this.panelHeight - 1, MachineScreen.PANEL);
        graphics.text(this.font, this.title, x + 8, y + 6, MachineScreen.ACCENT, false);
        int mx = mouseX - x;
        int my = mouseY - y;
        Component edit = Component.translatable("gui.universe.dashboard.edit");
        int editX = 16 + this.font.width(this.title);
        this.button(graphics, x + editX, y + 3, this.font.width(edit) + 8, edit, false, inside(mx, my, editX, 3, this.font.width(edit) + 8, 14));
        for (int i = 0; i < TABS.length; i++) {
            int tx = this.panelWidth - 8 - (TABS.length - i) * TAB_WIDTH;
            this.button(graphics, x + tx, y + 3, TAB_WIDTH - 2, Component.translatable("gui.universe.stats.tab." + TABS[i]), this.tab == i,
                    inside(mx, my, tx, 3, TAB_WIDTH - 2, 14));
        }
        graphics.fill(x + 6, y + 19, x + this.panelWidth - 6, y + 20, 0x602E7A92);
        this.selector(graphics, x, y, mx, my);
        switch (this.tab) {
            case 1 -> this.powerTab(graphics, x, y, mx, my);
            case 2 -> this.dysonTab(graphics, x, y, mx, my);
            default -> this.productionTab(graphics, x, y, mx, my, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (!this.hoveredItem.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, this.hoveredItem, mouseX, mouseY);
        } else if (!this.hoveredText.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, this.hoveredText, Optional.empty(), mouseX, mouseY);
        }
    }

    /** 第二行：看的地方的左右箭头和名字，右边三个时间范围（戴森球那页没有时间范围）。 */
    private void selector(GuiGraphicsExtractor graphics, int x, int y, int mx, int my) {
        int rowY = y + 23;
        int selectorRight = this.selectorRight();
        this.button(graphics, x + 8, rowY, 14, Component.literal("<"), false, inside(mx, my, 8, 23, 14, 14));
        this.button(graphics, x + selectorRight - 14, rowY, 14, Component.literal(">"), false, inside(mx, my, selectorRight - 14, 23, 14, 14));
        Component name = this.tab == 2 ? this.dysonName() : StatsCharts.scopeName(this.scope);
        String shown = this.font.plainSubstrByWidth(name.getString(), selectorRight - 14 - 26);
        graphics.centeredText(this.font, Component.literal(shown), x + (8 + 14 + selectorRight - 14) / 2, rowY + 3, MachineScreen.TEXT);
        if (this.tab == 2) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            int bx = this.panelWidth - 8 - (3 - i) * RANGE_WIDTH;
            this.button(graphics, x + bx, rowY, RANGE_WIDTH - 2, Component.translatable("gui.universe.stats.range." + i), this.range == i,
                    inside(mx, my, bx, 23, RANGE_WIDTH - 2, 14));
        }
    }

    private int selectorRight() {
        return this.tab == 2 ? this.panelWidth - 8 : this.panelWidth - 8 - 3 * RANGE_WIDTH - 8;
    }

    private Component dysonName() {
        Optional<StatsDysonPayload> dyson = StatsClient.dyson();
        if (dyson.isEmpty() || dyson.get().system() < 0) {
            return Component.translatable("gui.universe.stats.dyson.none_short");
        }
        return StatsCharts.systemName(dyson.get().system());
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, boolean selected, boolean hovered) {
        int border = selected ? MachineScreen.ACCENT : hovered ? 0xFF4FA3C0 : 0xFF2A4A58;
        graphics.fill(x, y, x + width, y + 14, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 13, selected ? 0xFF1C4352 : hovered ? 0xFF16303B : MachineScreen.INSET);
        graphics.centeredText(this.font, label, x + width / 2, y + 3, selected ? MachineScreen.TEXT : MachineScreen.DIM);
    }

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(this.font, text, right - this.font.width(text), y, color, false);
    }

    private static boolean inside(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    /** 画一个能点的物品图标（点了跳到这种物品），鼠标在上面时出提示。 */
    private void itemIcon(GuiGraphicsExtractor graphics, String item, int count, int x, int y, int mx, int my) {
        ItemStack stack = DashboardHud.stack(item);
        graphics.fakeItem(stack, x, y);
        if (count > 1) {
            graphics.nextStratum();
            String text = Integer.toString(count);
            graphics.text(this.font, text, x + 17 - this.font.width(text), y + 9, 0xFFFFFFFF, true);
        }
        this.itemHits.add(new int[] {x - this.left, y - this.top, this.hitItems.size()});
        this.hitItems.add(item);
        if (inside(mx, my, x - this.left, y - this.top, 16, 16)) {
            this.hoveredItem = stack;
        }
    }

    // ---- 生产

    private void productionTab(GuiGraphicsExtractor graphics, int x, int y, int mx, int my, int mouseX, int mouseY) {
        Optional<StatsSnapshotPayload> snapshot = this.snapshot();
        if (snapshot.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.waiting"), x + this.panelWidth / 2, y + 100,
                    MachineScreen.DIM);
            return;
        }
        this.table(graphics, x, y, mx, my, snapshot.get());
        this.detailPanel(graphics, x + this.detailX(), y, mx, my);
    }

    private void table(GuiGraphicsExtractor graphics, int x, int y, int mx, int my, StatsSnapshotPayload snapshot) {
        int headerY = y + TABLE_Y - 12;
        this.header(graphics, x + 28, headerY, "gui.universe.stats.item", Sort.NAME, false, mx, my);
        this.header(graphics, x + 8 + this.producedRight(), headerY, "gui.universe.stats.produced", Sort.PRODUCED, true, mx, my);
        this.header(graphics, x + 8 + this.consumedRight(), headerY, "gui.universe.stats.consumed", Sort.CONSUMED, true, mx, my);
        this.header(graphics, x + 8 + this.netRight(), headerY, "gui.universe.stats.net", Sort.NET, true, mx, my);
        List<StatsSnapshotPayload.Row> rows = this.rows(snapshot);
        int visible = this.visibleRows();
        this.scroll = Math.max(0, Math.min(this.scroll, rows.size() - visible));
        int tableWidth = this.tableWidth();
        if (rows.isEmpty()) {
            int cx = x + 8 + tableWidth / 2;
            boolean searching = !this.search.getValue().isBlank();
            graphics.centeredText(this.font, Component.translatable(searching ? "gui.universe.stats.no_match" : "gui.universe.stats.empty"), cx,
                    y + TABLE_Y + 20, MachineScreen.TEXT);
            if (!searching) {
                int lineY = y + TABLE_Y + 34;
                for (var line : TextWrap.split(this.font, Component.translatable("gui.universe.stats.empty_hint"), tableWidth - 16)) {
                    graphics.centeredText(this.font, line, cx, lineY, MachineScreen.DIM);
                    lineY += 10;
                }
            }
            return;
        }
        float most = 0;
        for (StatsSnapshotPayload.Row row : rows) {
            most = Math.max(most, Math.max(row.produced() + row.imported(), row.consumed() + row.exported()));
        }
        most = Math.max(most, 1e-6F);
        for (int i = 0; i < visible && this.scroll + i < rows.size(); i++) {
            StatsSnapshotPayload.Row row = rows.get(this.scroll + i);
            int rowY = y + TABLE_Y + i * ROW_HEIGHT;
            int rowLeft = x + 6;
            int rowRight = x + 8 + tableWidth - 4;
            if (row.item().equals(this.selected)) {
                graphics.fill(rowLeft, rowY - 1, rowRight, rowY + ROW_HEIGHT - 1, 0xFF173542);
            } else if (i % 2 == 1) {
                graphics.fill(rowLeft, rowY - 1, rowRight, rowY + ROW_HEIGHT - 1, 0x10FFFFFF);
            }
            ItemStack stack = DashboardHud.stack(row.item());
            graphics.fakeItem(stack, x + 8, rowY);
            if (inside(mx, my, 6, rowY - y - 1, tableWidth - 2, ROW_HEIGHT)) {
                graphics.fill(rowLeft, rowY - 1, rowRight, rowY + ROW_HEIGHT - 1, 0x20FFFFFF);
                if (mx < 26) {
                    this.hoveredItem = stack;
                }
            }
            int nameX = x + 28;
            int nameWidth = this.nameRight() - 20 - (row.imported() > 0 || row.exported() > 0 ? 10 : 0);
            String name = this.font.plainSubstrByWidth(stack.getHoverName().getString(), Math.max(20, nameWidth));
            graphics.text(this.font, name, nameX, rowY + 2, MachineScreen.TEXT, false);
            if (row.imported() > 0 || row.exported() > 0) {
                // 有星际物流运进运出的标一个箭头
                String arrow = row.imported() >= row.exported() ? "↓" : "↑";
                graphics.text(this.font, arrow, nameX + this.font.width(name) + 3, rowY + 2, IMPORT_COLOR, false);
            }
            // 名字下面两道细条：产出（绿）、消耗（橙），运进、运出的接在后面（青），按表里最大的那个数归一
            int barWidth = this.producedRight() - 44 - 20;
            graphics.fill(nameX, rowY + 12, nameX + barWidth, rowY + 16, StatsCharts.TRACK);
            int produced = Math.round(barWidth * row.produced() / most);
            int imported = Math.round(barWidth * row.imported() / most);
            int consumed = Math.round(barWidth * row.consumed() / most);
            int exported = Math.round(barWidth * row.exported() / most);
            graphics.fill(nameX, rowY + 12, nameX + produced, rowY + 14, StatsCharts.PRODUCED);
            graphics.fill(nameX + produced, rowY + 12, Math.min(nameX + barWidth, nameX + produced + imported), rowY + 14, IMPORT_COLOR);
            graphics.fill(nameX, rowY + 14, nameX + consumed, rowY + 16, StatsCharts.CONSUMED);
            graphics.fill(nameX + consumed, rowY + 14, Math.min(nameX + barWidth, nameX + consumed + exported), rowY + 16, IMPORT_COLOR);
            this.right(graphics, StatsCharts.rate(row.produced()), x + 8 + this.producedRight(), rowY + 5,
                    row.produced() > 0 ? StatsCharts.PRODUCED : MachineScreen.DIM);
            this.right(graphics, StatsCharts.rate(row.consumed()), x + 8 + this.consumedRight(), rowY + 5,
                    row.consumed() > 0 ? StatsCharts.CONSUMED : MachineScreen.DIM);
            double net = row.produced() + row.imported() - row.consumed() - row.exported();
            String netText = (net > 0.05 ? "+" : "") + StatsCharts.rate(net);
            this.right(graphics, netText, x + 8 + this.netRight(), rowY + 5,
                    net > 0.05 ? MachineScreen.GOOD : net < -0.05 ? MachineScreen.BAD : MachineScreen.DIM);
        }
        // 滚动条
        if (rows.size() > visible) {
            int trackX = x + 8 + tableWidth - 3;
            int trackTop = y + TABLE_Y - 1;
            int trackHeight = visible * ROW_HEIGHT;
            int thumb = Math.max(10, trackHeight * visible / rows.size());
            int thumbY = trackTop + (trackHeight - thumb) * this.scroll / (rows.size() - visible);
            graphics.fill(trackX, trackTop, trackX + 3, trackTop + trackHeight, StatsCharts.TRACK);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumb, 0xFF4FA3C0);
        }
    }

    private void header(GuiGraphicsExtractor graphics, int x, int y, String key, Sort sort, boolean alignRight, int mx, int my) {
        Component label = Component.translatable(key);
        int width = this.font.width(label);
        int left = alignRight ? x - width : x;
        boolean hovered = inside(mx, my, left - this.left, y - this.top - 1, width, 10);
        int color = this.sort == sort ? MachineScreen.ACCENT : hovered ? MachineScreen.TEXT : MachineScreen.DIM;
        graphics.text(this.font, label, left, y, color, false);
        if (this.sort == sort) {
            graphics.fill(left, y + 9, left + width, y + 10, MachineScreen.ACCENT);
        }
    }

    /** 右边：选中物品的详情。 */
    private void detailPanel(GuiGraphicsExtractor graphics, int x, int y, int mx, int my) {
        int top = y + CONTENT_Y;
        int bottom = y + this.panelHeight - 8;
        graphics.fill(x, top, x + DETAIL_WIDTH, bottom, MachineScreen.INSET);
        if (this.selected.isEmpty()) {
            int lineY = top + 30;
            for (var line : TextWrap.split(this.font, Component.translatable("gui.universe.stats.pick_item"), DETAIL_WIDTH - 16)) {
                graphics.centeredText(this.font, line, x + DETAIL_WIDTH / 2, lineY, MachineScreen.DIM);
                lineY += 10;
            }
            return;
        }
        int ox = x - this.left;
        int oy = top - this.top;
        // 标题：返回、图标、名字、钉到仪表盘
        int titleX = x + 4;
        if (!this.back.isEmpty()) {
            this.button(graphics, x + 4, top + 4, 14, Component.literal("←"), false, inside(mx, my, ox + 4, oy + 4, 14, 14));
            titleX += 18;
        }
        ItemStack stack = DashboardHud.stack(this.selected);
        graphics.fakeItem(stack, titleX, top + 3);
        if (inside(mx, my, titleX - this.left, oy + 3, 16, 16)) {
            this.hoveredItem = stack;
        }
        Component pin = Component.translatable("gui.universe.dashboard.pin_short");
        int pinWidth = this.font.width(pin) + 8;
        int pinX = DETAIL_WIDTH - 4 - pinWidth;
        this.button(graphics, x + pinX, top + 4, pinWidth, pin, false, inside(mx, my, ox + pinX, oy + 4, pinWidth, 14));
        if (inside(mx, my, ox + pinX, oy + 4, pinWidth, 14)) {
            this.hoveredText = List.of(Component.translatable("gui.universe.dashboard.pin"));
        }
        String name = this.font.plainSubstrByWidth(stack.getHoverName().getString(), pinX - (titleX - x) - 22);
        graphics.text(this.font, name, titleX + 20, top + 7, MachineScreen.TEXT, false);
        Optional<StatsDetailPayload> detail = this.detail();
        if (detail.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.waiting"), x + DETAIL_WIDTH / 2, top + 40, MachineScreen.DIM);
            return;
        }
        StatsDetailPayload d = detail.get();
        StatsSnapshotPayload.Row row = this.snapshot().flatMap(s -> s.rows().stream().filter(r -> r.item().equals(this.selected)).findFirst())
                .orElse(new StatsSnapshotPayload.Row(this.selected, 0, 0, 0, 0));
        int lineY = top + 24;
        graphics.text(this.font, Component.translatable("gui.universe.stats.detail.produced", StatsCharts.rate(row.produced())), x + 6, lineY,
                StatsCharts.PRODUCED, false);
        this.reference(graphics, d.referenceProduced(), d.producers(), x + DETAIL_WIDTH - 6, lineY, mx, my);
        lineY += 11;
        graphics.text(this.font, Component.translatable("gui.universe.stats.detail.consumed", StatsCharts.rate(row.consumed())), x + 6, lineY,
                StatsCharts.CONSUMED, false);
        this.reference(graphics, d.referenceConsumed(), d.consumers(), x + DETAIL_WIDTH - 6, lineY, mx, my);
        lineY += 13;
        float max = StatsCharts.lines(graphics, x + 6, lineY, DETAIL_WIDTH - 12, 40, d.produced(), d.consumed(), StatsCharts.PRODUCED,
                StatsCharts.CONSUMED);
        if (max > 0) {
            graphics.text(this.font, StatsCharts.rate(max), x + 8, lineY + 2, MachineScreen.DIM, false);
        }
        String span = Component.translatable("gui.universe.stats.range." + this.range).getString();
        graphics.text(this.font, span, x + DETAIL_WIDTH - 8 - this.font.width(span), lineY + 31, MachineScreen.DIM, false);
        lineY += 44;
        // 进出口
        if (row.imported() > 0 || row.exported() > 0 || !d.trades().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.stats.detail.trade", StatsCharts.rate(row.imported()),
                    StatsCharts.rate(row.exported())), x + 6, lineY, IMPORT_COLOR, false);
            lineY += 10;
            for (int i = 0; i < Math.min(2, d.trades().size()); i++) {
                StatsDetailPayload.Trade trade = d.trades().get(i);
                String text = this.font.plainSubstrByWidth(StatsCharts.dimensionName(trade.partner()).getString(), DETAIL_WIDTH - 86);
                graphics.text(this.font, text, x + 10, lineY, MachineScreen.DIM, false);
                this.right(graphics, "↓" + StatsCharts.rate(trade.imported()) + " ↑" + StatsCharts.rate(trade.exported()), x + DETAIL_WIDTH - 6,
                        lineY, MachineScreen.DIM);
                lineY += 10;
            }
            lineY += 2;
        }
        // 上下游，能滚动
        this.recipes(graphics, d, x, lineY, bottom, mx, my);
    }

    /** 参考产量：在运转的设备满速时每分钟多少，右对齐写在实际数的右边。 */
    private void reference(GuiGraphicsExtractor graphics, float value, int machines, int right, int y, int mx, int my) {
        String text = Component.translatable("gui.universe.stats.detail.reference", StatsCharts.rate(value)).getString();
        int width = this.font.width(text);
        graphics.text(this.font, text, right - width, y, machines > 0 ? MachineScreen.TEXT : MachineScreen.DIM, false);
        if (inside(mx, my, right - width - this.left, y - this.top, width, 9)) {
            this.hoveredText = List.of(Component.translatable("gui.universe.stats.detail.reference_tip", machines));
        }
    }

    private void recipes(GuiGraphicsExtractor graphics, StatsDetailPayload d, int x, int top, int bottom, int mx, int my) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.heading(Component.translatable("gui.universe.stats.detail.upstream", d.upstream().size())));
        d.upstream().forEach(recipe -> lines.add(Line.of(recipe, false)));
        if (d.upstream().isEmpty()) {
            lines.add(Line.note(Component.translatable("gui.universe.stats.detail.none")));
        }
        lines.add(Line.heading(Component.translatable("gui.universe.stats.detail.downstream", d.downstream().size())));
        d.downstream().forEach(recipe -> lines.add(Line.of(recipe, true)));
        if (d.downstream().isEmpty()) {
            lines.add(Line.note(Component.translatable("gui.universe.stats.detail.none")));
        }
        int visible = Math.max(1, (bottom - top - 2) / RECIPE_ROW);
        this.detailScroll = Math.max(0, Math.min(this.detailScroll, lines.size() - visible));
        graphics.enableScissor(x, top, x + DETAIL_WIDTH, bottom);
        for (int i = 0; i < visible && this.detailScroll + i < lines.size(); i++) {
            Line line = lines.get(this.detailScroll + i);
            int rowY = top + i * RECIPE_ROW;
            if (line.label() != null) {
                graphics.text(this.font, line.label(), x + 6, rowY + 6, line.heading() ? MachineScreen.ACCENT : MachineScreen.DIM, false);
            } else if (line.value() instanceof StatsDetailPayload.Recipe recipe) {
                this.recipe(graphics, recipe, x + 4, rowY + 2, mx, my);
            }
        }
        graphics.disableScissor();
        if (lines.size() > visible) {
            int trackTop = top;
            int trackHeight = bottom - top;
            int thumb = Math.max(10, trackHeight * visible / lines.size());
            int thumbY = trackTop + (trackHeight - thumb) * this.detailScroll / (lines.size() - visible);
            graphics.fill(x + DETAIL_WIDTH - 3, trackTop, x + DETAIL_WIDTH, trackTop + trackHeight, StatsCharts.TRACK);
            graphics.fill(x + DETAIL_WIDTH - 3, thumbY, x + DETAIL_WIDTH, thumbY + thumb, 0xFF4FA3C0);
        }
    }

    /** 一个配方一行：设备、原料、箭头、产物。 */
    private void recipe(GuiGraphicsExtractor graphics, StatsDetailPayload.Recipe recipe, int x, int y, int mx, int my) {
        ItemStack machine = DashboardHud.stack(recipe.machine());
        graphics.fakeItem(machine, x, y);
        if (inside(mx, my, x - this.left, y - this.top, 16, 16)) {
            List<Component> tip = new ArrayList<>();
            tip.add(machine.getHoverName());
            if (recipe.time() > 0) {
                tip.add(Component.translatable("gui.universe.stats.detail.time", String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0)));
            }
            this.hoveredText = tip;
        }
        int cx = x + 20;
        graphics.fill(cx - 2, y + 2, cx - 1, y + 14, 0xFF2A4A58);
        for (StatsDetailPayload.Stack input : recipe.inputs()) {
            if (cx > x + DETAIL_WIDTH - 60) {
                break;
            }
            this.itemIcon(graphics, input.item(), input.count(), cx, y, mx, my);
            cx += 17;
        }
        graphics.text(this.font, "→", cx + 1, y + 4, MachineScreen.DIM, false);
        cx += 10;
        for (StatsDetailPayload.Stack output : recipe.outputs()) {
            if (cx > x + DETAIL_WIDTH - 22) {
                break;
            }
            this.itemIcon(graphics, output.item(), output.count(), cx, y, mx, my);
            cx += 17;
        }
    }

    // ---- 电力

    private void powerTab(GuiGraphicsExtractor graphics, int x, int y, int mx, int my) {
        Optional<StatsSnapshotPayload> found = this.snapshot();
        if (found.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.waiting"), x + this.panelWidth / 2, y + 100,
                    MachineScreen.DIM);
            return;
        }
        StatsSnapshotPayload snapshot = found.get();
        int top = y + CONTENT_Y;
        Component pin = Component.translatable("gui.universe.dashboard.pin");
        int pinWidth = this.font.width(pin) + 8;
        if (snapshot.generation() <= 0 && snapshot.demand() <= 0 && snapshot.charging() <= 0) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.no_power"), x + this.panelWidth / 2, top + 50,
                    MachineScreen.DIM);
            return;
        }
        // 左边：环形图，中间写满足率，下面是图例
        int ringX = x + 8 + 70;
        int ringY = top + 64;
        double generation = snapshot.generation();
        double used = Math.min(snapshot.demand(), generation);
        double charging = Math.min(snapshot.charging(), Math.max(0, generation - used));
        if (snapshot.demand() > generation) {
            // 缺电：整圈是用电，缺的部分标红
            StatsCharts.ring(graphics, ringX, ringY, 56, 40, new double[] {generation}, new int[] {StatsCharts.CONSUMED}, snapshot.demand(),
                    MachineScreen.BAD);
        } else {
            StatsCharts.ring(graphics, ringX, ringY, 56, 40, new double[] {used, charging}, new int[] {StatsCharts.CONSUMED, StatsCharts.CHARGING},
                    generation, StatsCharts.SPARE);
        }
        double satisfaction = snapshot.satisfaction();
        int color = satisfaction >= 0.999 ? MachineScreen.GOOD : satisfaction >= 0.5 ? MachineScreen.WARN : MachineScreen.BAD;
        graphics.centeredText(this.font, Component.literal(StatsCharts.percent(satisfaction)), ringX, ringY - 8, color);
        graphics.centeredText(this.font, Component.translatable("gui.universe.stats.power.satisfaction_short"), ringX, ringY + 3, MachineScreen.DIM);
        int legendY = ringY + 64;
        this.legend(graphics, x + 12, legendY, StatsCharts.PRODUCED, "gui.universe.stats.generation", StatsCharts.watts(generation));
        this.legend(graphics, x + 12, legendY + 11, StatsCharts.CONSUMED, "gui.universe.stats.demand", StatsCharts.watts(snapshot.demand()));
        this.legend(graphics, x + 12, legendY + 22, StatsCharts.CHARGING, "gui.universe.stats.charging", StatsCharts.watts(snapshot.charging()));
        this.legend(graphics, x + 12, legendY + 33, StatsCharts.SPARE, "gui.universe.stats.power.spare",
                StatsCharts.watts(Math.max(0, generation - snapshot.demand() - snapshot.charging())));
        int pinX = 8;
        int pinY = legendY + 48 - y;
        if (pinY + 14 < this.panelHeight - 4) {
            this.button(graphics, x + pinX, y + pinY, pinWidth, pin, false, inside(mx, my, pinX, pinY, pinWidth, 14));
        }
        // 右边：按设备分开的发电和用电
        int listX = x + 170;
        int listWidth = this.panelWidth - 170 - 8;
        int bottom = y + this.panelHeight - 8;
        List<Line> lines = new ArrayList<>();
        lines.add(Line.heading(Component.translatable("gui.universe.stats.power.generators")));
        snapshot.generators().forEach(part -> lines.add(Line.of(part, false)));
        if (snapshot.generators().isEmpty()) {
            lines.add(Line.note(Component.translatable("gui.universe.stats.detail.none")));
        }
        lines.add(Line.heading(Component.translatable("gui.universe.stats.power.consumers")));
        snapshot.consumers().forEach(part -> lines.add(Line.of(part, true)));
        if (snapshot.consumers().isEmpty()) {
            lines.add(Line.note(Component.translatable("gui.universe.stats.detail.none")));
        }
        double other = generation - snapshot.generators().stream().mapToDouble(StatsSnapshotPayload.Part::power).sum();
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        this.sideScroll = Math.max(0, Math.min(this.sideScroll, lines.size() - visible));
        for (int i = 0; i < visible && this.sideScroll + i < lines.size(); i++) {
            Line line = lines.get(this.sideScroll + i);
            int rowY = top + i * ROW_HEIGHT;
            if (line.label() != null) {
                graphics.text(this.font, line.label(), listX, rowY + 6, line.heading() ? MachineScreen.ACCENT : MachineScreen.DIM, false);
                continue;
            }
            if (!(line.value() instanceof StatsSnapshotPayload.Part part)) {
                continue;
            }
            boolean consumers = line.consumer();
            Block block = StatsCharts.block(part.kind());
            ItemStack stack = new ItemStack(block.asItem());
            graphics.fakeItem(stack, listX, rowY);
            if (inside(mx, my, listX - this.left, rowY - this.top, listWidth, 16)) {
                this.hoveredText = List.of(block.getName());
            }
            double total = consumers ? snapshot.demand() : generation;
            String watts = StatsCharts.watts(part.power());
            String share = total > 0 ? StatsCharts.percent(part.power() / total) : "";
            graphics.text(this.font, this.font.plainSubstrByWidth(block.getName().getString(), listWidth - 110), listX + 20, rowY + 1,
                    MachineScreen.TEXT, false);
            this.right(graphics, watts, listX + listWidth - 34, rowY + 1, consumers ? StatsCharts.CONSUMED : StatsCharts.PRODUCED);
            this.right(graphics, share, listX + listWidth, rowY + 1, MachineScreen.DIM);
            StatsCharts.bar(graphics, listX + 20, rowY + 11, listWidth - 20, 3, total > 0 ? part.power() / total : 0,
                    consumers ? StatsCharts.CONSUMED : StatsCharts.PRODUCED);
        }
        if (other > generation * 0.01 && other > 1 && this.sideScroll == 0 && lines.size() <= visible) {
            graphics.text(this.font, Component.translatable("gui.universe.stats.power.other", StatsCharts.watts(other)), listX,
                    bottom - 10, MachineScreen.DIM, false);
        }
    }

    private void legend(GuiGraphicsExtractor graphics, int x, int y, int color, String key, String value) {
        graphics.fill(x, y + 1, x + 6, y + 7, color);
        graphics.text(this.font, Component.translatable(key, value), x + 10, y, MachineScreen.TEXT, false);
    }

    // ---- 戴森球

    private void dysonTab(GuiGraphicsExtractor graphics, int x, int y, int mx, int my) {
        Optional<StatsDysonPayload> found = StatsClient.dyson();
        int top = y + CONTENT_Y;
        if (found.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.waiting"), x + this.panelWidth / 2, top + 50, MachineScreen.DIM);
            return;
        }
        StatsDysonPayload dyson = found.get();
        if (dyson.system() < 0) {
            int lineY = top + 40;
            for (var line : TextWrap.split(this.font, Component.translatable("gui.universe.stats.dyson.none"), this.panelWidth - 60)) {
                graphics.centeredText(this.font, line, x + this.panelWidth / 2, lineY, MachineScreen.DIM);
                lineY += 10;
            }
            return;
        }
        // 总数
        graphics.fill(x + 8, top, x + this.panelWidth - 8, top + 46, MachineScreen.INSET);
        double shells = Math.max(0, dyson.generation() - dyson.swarm());
        graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.generation", StatsCharts.watts(dyson.generation())), x + 14,
                top + 5, StatsCharts.PRODUCED, false);
        graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.swarm", StatsCharts.watts(dyson.swarm()), dyson.sails()), x + 14,
                top + 17, MachineScreen.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.shells", StatsCharts.watts(shells)), x + 14, top + 29,
                MachineScreen.TEXT, false);
        int col = x + this.panelWidth / 2 + 10;
        graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.drawn", StatsCharts.watts(dyson.drawn())), col, top + 5,
                StatsCharts.CONSUMED, false);
        if (dyson.siphon() > 0) {
            graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.siphon", StatsCharts.watts(dyson.siphon())), col, top + 17,
                    MachineScreen.BAD, false);
        }
        Component pin = Component.translatable("gui.universe.dashboard.pin");
        int pinWidth = this.font.width(pin) + 8;
        int pinX = this.panelWidth - 14 - pinWidth;
        this.button(graphics, x + pinX, top + 28, pinWidth, pin, false, inside(mx, my, pinX, CONTENT_Y + 28, pinWidth, 14));
        // 每一层
        int tableTop = top + 54;
        int[] cols = this.dysonColumns();
        String[] heads = {"layer", "radius", "nodes", "frames", "shells", "power"};
        for (int i = 0; i < heads.length; i++) {
            Component head = Component.translatable("gui.universe.stats.dyson.col." + heads[i]);
            if (i == 0) {
                graphics.text(this.font, head, x + cols[i], tableTop, MachineScreen.DIM, false);
            } else {
                this.right(graphics, head.getString(), x + cols[i], tableTop, MachineScreen.DIM);
            }
        }
        int bottom = y + this.panelHeight - 8;
        int visible = Math.max(1, (bottom - tableTop - 12) / 14);
        List<StatsDysonPayload.Layer> layers = dyson.layers();
        this.sideScroll = Math.max(0, Math.min(this.sideScroll, layers.size() - visible));
        if (layers.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.no_layers"), x + 14, tableTop + 16, MachineScreen.DIM, false);
        }
        for (int i = 0; i < visible && this.sideScroll + i < layers.size(); i++) {
            StatsDysonPayload.Layer layer = layers.get(this.sideScroll + i);
            int rowY = tableTop + 13 + i * 14;
            if (i % 2 == 1) {
                graphics.fill(x + 8, rowY - 2, x + this.panelWidth - 8, rowY + 11, 0x10FFFFFF);
            }
            graphics.text(this.font, Component.translatable("gui.universe.stats.dyson.layer", layer.id()), x + cols[0], rowY, MachineScreen.TEXT, false);
            this.right(graphics, String.format(Locale.ROOT, "%.2f AU", layer.radius()), x + cols[1], rowY, MachineScreen.TEXT);
            this.fraction(graphics, layer.nodes(), layer.nodeTotal(), x + cols[2], rowY);
            this.fraction(graphics, layer.frames(), layer.frameTotal(), x + cols[3], rowY);
            this.fraction(graphics, layer.shells(), layer.shellTotal(), x + cols[4], rowY);
            this.right(graphics, StatsCharts.watts(layer.power()), x + cols[5], rowY, layer.power() > 0 ? StatsCharts.PRODUCED : MachineScreen.DIM);
        }
    }

    private int[] dysonColumns() {
        int w = this.panelWidth;
        return new int[] {14, w * 30 / 100, w * 45 / 100, w * 60 / 100, w * 75 / 100, w - 14};
    }

    private void fraction(GuiGraphicsExtractor graphics, int done, int total, int right, int y) {
        String text = done + "/" + total;
        this.right(graphics, text, right, y, total > 0 && done >= total ? MachineScreen.GOOD : done > 0 ? MachineScreen.TEXT : MachineScreen.DIM);
    }

    // ---- 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        int mx = (int) event.x() - this.left;
        int my = (int) event.y() - this.top;
        this.search.setFocused(false);
        Component edit = Component.translatable("gui.universe.dashboard.edit");
        int editX = 16 + this.font.width(this.title);
        if (inside(mx, my, editX, 3, this.font.width(edit) + 8, 14)) {
            this.click();
            this.minecraft.gui.setScreen(new DashboardScreen(this));
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (inside(mx, my, this.panelWidth - 8 - (TABS.length - i) * TAB_WIDTH, 3, TAB_WIDTH - 2, 14) && this.tab != i) {
                this.tab = i;
                this.sideScroll = 0;
                this.search.visible = i == 0;
                this.click();
                this.request();
                return true;
            }
        }
        int selectorRight = this.selectorRight();
        if (inside(mx, my, 8, 23, 14, 14)) {
            this.cycle(-1);
            return true;
        }
        if (inside(mx, my, selectorRight - 14, 23, 14, 14)) {
            this.cycle(1);
            return true;
        }
        if (this.tab != 2) {
            for (int i = 0; i < 3; i++) {
                if (inside(mx, my, this.panelWidth - 8 - (3 - i) * RANGE_WIDTH, 23, RANGE_WIDTH - 2, 14) && this.range != i) {
                    this.range = i;
                    this.click();
                    this.request();
                    return true;
                }
            }
        }
        return switch (this.tab) {
            case 1 -> this.clickPower(mx, my);
            case 2 -> this.clickDyson(mx, my);
            default -> this.clickProduction(mx, my);
        };
    }

    private boolean clickProduction(int mx, int my) {
        Optional<StatsSnapshotPayload> snapshot = this.snapshot();
        if (snapshot.isEmpty()) {
            return false;
        }
        int headerY = TABLE_Y - 13;
        if (mx < 8 + this.tableWidth() && my >= headerY && my < headerY + 11) {
            int relative = mx - 8;
            Sort clicked = relative < this.producedRight() - 40 ? Sort.NAME : relative < this.producedRight() + 4 ? Sort.PRODUCED
                    : relative < this.consumedRight() + 4 ? Sort.CONSUMED : Sort.NET;
            this.sort = this.sort == clicked ? Sort.VOLUME : clicked;
            this.scroll = 0;
            this.click();
            return true;
        }
        if (mx >= 6 && mx < 8 + this.tableWidth() && my >= TABLE_Y - 1) {
            int index = (my - TABLE_Y + 1) / ROW_HEIGHT;
            List<StatsSnapshotPayload.Row> rows = this.rows(snapshot.get());
            if (index < this.visibleRows() && this.scroll + index < rows.size()) {
                this.select(rows.get(this.scroll + index).item(), false);
                return true;
            }
        }
        int dx = this.detailX();
        if (mx >= dx && mx < dx + DETAIL_WIDTH && !this.selected.isEmpty()) {
            int oy = CONTENT_Y;
            if (!this.back.isEmpty() && inside(mx, my, dx + 4, oy + 4, 14, 14)) {
                this.selected = this.back.pop();
                this.detailScroll = 0;
                this.click();
                this.request();
                return true;
            }
            Component pin = Component.translatable("gui.universe.dashboard.pin_short");
            int pinWidth = this.font.width(pin) + 8;
            if (inside(mx, my, dx + DETAIL_WIDTH - 4 - pinWidth, oy + 4, pinWidth, 14)) {
                this.click();
                DashboardHud.pin(Dashboard.Pin.of(Dashboard.Kind.ITEM, this.selected, this.scope, this.range));
                return true;
            }
            for (int[] hit : this.itemHits) {
                if (inside(mx, my, hit[0], hit[1], 16, 16)) {
                    String item = this.hitItems.get(hit[2]);
                    if (!item.equals(this.selected)) {
                        this.select(item, true);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** 选中一种物品；jump 为真时（从上下游点过来）记下来，可以点"←"回去。 */
    private void select(String item, boolean jump) {
        if (jump && !this.selected.isEmpty()) {
            this.back.push(this.selected);
            while (this.back.size() > 16) {
                this.back.removeLast();
            }
        } else if (!jump) {
            this.back.clear();
        }
        this.selected = item;
        this.detailScroll = 0;
        this.click();
        this.request();
    }

    private boolean clickPower(int mx, int my) {
        Optional<StatsSnapshotPayload> snapshot = this.snapshot();
        if (snapshot.isEmpty()) {
            return false;
        }
        Component pin = Component.translatable("gui.universe.dashboard.pin");
        int pinWidth = this.font.width(pin) + 8;
        int pinY = CONTENT_Y + 64 + 64 + 48;
        if (pinY + 14 < this.panelHeight - 4 && inside(mx, my, 8, pinY, pinWidth, 14)) {
            this.click();
            DashboardHud.pin(Dashboard.Pin.of(Dashboard.Kind.POWER, "", this.scope, this.range));
            return true;
        }
        return false;
    }

    private boolean clickDyson(int mx, int my) {
        Optional<StatsDysonPayload> dyson = StatsClient.dyson();
        if (dyson.isEmpty() || dyson.get().system() < 0) {
            return false;
        }
        Component pin = Component.translatable("gui.universe.dashboard.pin");
        int pinWidth = this.font.width(pin) + 8;
        if (inside(mx, my, this.panelWidth - 14 - pinWidth, CONTENT_Y + 28, pinWidth, 14)) {
            this.click();
            DashboardHud.pin(Dashboard.Pin.of(Dashboard.Kind.DYSON, Integer.toString(dyson.get().system()), "", 0));
            return true;
        }
        return false;
    }

    /**
     * 换一个看的地方。生产和电力在"全部"、每个星系、星系里有统计的维度之间轮换（星系排在它的星球前面）；戴森球那页在有
     * 戴森球的星系之间轮换。
     */
    private void cycle(int step) {
        List<String> options = new ArrayList<>();
        if (this.tab == 2) {
            StatsClient.dyson().ifPresent(dyson -> dyson.systems().forEach(system -> options.add(StatsCharts.SYSTEM_PREFIX + system)));
            if (options.isEmpty()) {
                return;
            }
            String current = StatsClient.dyson().filter(d -> d.system() >= 0).map(d -> StatsCharts.SYSTEM_PREFIX + d.system()).orElse(this.scope);
            int index = options.indexOf(current);
            this.scope = options.get(Math.floorMod((index < 0 ? 0 : index) + (index < 0 ? 0 : step), options.size()));
        } else {
            options.add("");
            TreeMap<Integer, List<String>> systems = new TreeMap<>();
            List<String> loose = new ArrayList<>();
            StatsClient.snapshot().ifPresent(snapshot -> {
                for (String dimension : snapshot.dimensions()) {
                    OptionalInt system = StatsCharts.systemOf(dimension);
                    if (system.isPresent()) {
                        systems.computeIfAbsent(system.getAsInt(), key -> new ArrayList<>()).add(dimension);
                    } else {
                        loose.add(dimension);
                    }
                }
            });
            systems.forEach((system, dimensions) -> {
                // 一个星系只有一个维度时不单列星系
                if (dimensions.size() > 1) {
                    options.add(StatsCharts.SYSTEM_PREFIX + system);
                }
                options.addAll(dimensions);
            });
            options.addAll(loose);
            if (!options.contains(this.scope)) {
                options.add(this.scope);
            }
            int index = options.indexOf(this.scope);
            this.scope = options.get(Math.floorMod(index + step, options.size()));
        }
        this.scroll = 0;
        this.sideScroll = 0;
        this.click();
        this.request();
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int mx = (int) x - this.left;
        int step = (int) Math.signum(scrollY);
        if (this.tab == 0 && mx >= this.detailX()) {
            this.detailScroll = Math.max(0, this.detailScroll - step);
        } else if (this.tab == 0) {
            this.scroll = Math.max(0, this.scroll - step * 2);
        } else {
            this.sideScroll = Math.max(0, this.sideScroll - step);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.search.isFocused() && this.search.visible) {
            if (event.isEscape()) {
                this.search.setFocused(false);
                return true;
            }
            return this.search.keyPressed(event) || this.search.canConsumeInput() || super.keyPressed(event);
        }
        if (UniverseKeys.STATS.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
