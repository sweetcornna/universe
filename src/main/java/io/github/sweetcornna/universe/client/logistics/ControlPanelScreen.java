package io.github.sweetcornna.universe.client.logistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.discovery.ClientDiscoveries;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.core.logistics.Logic;
import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.logistics.StationBlockEntity;
import io.github.sweetcornna.universe.network.ControlPanelPayloads;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.DistributorRow;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.RouteRow;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.SlotRow;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.Snapshot;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.StationEdit;
import io.github.sweetcornna.universe.network.ControlPanelPayloads.StationRow;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 物流控制面板（ADR-0033 §三，默认 I）：全服的物流站、配送器和物品路线列成表，不用走到跟前就能看、能改。
 *
 * <p>四页：星际（星际物流站和轨道采集器）、行星内（行星物流站和星际物流站）、配送器、路线。上面一行能按名字、星球、
 * 星系、物品筛，按名字、编号、物品排。选中一行，右边是详情：站的名字、分组、伙伴、指定配对、起送量、航程，每格的
 * 物品、供需和上限；配送器只改两种模式；路线能新建、删除，看哪些站在走。改设置都发给服务端，和走到站前面改一样。
 * "定位"在 HUD 上标出那个地方。
 */
public class ControlPanelScreen extends Screen {
    private static final int MAX_WIDTH = 480;
    private static final int MAX_HEIGHT = 330;
    private static final int DETAIL_WIDTH = 206;
    private static final int TAB_Y = 20;
    private static final int TOOL_Y = 38;
    private static final int LIST_Y = 58;
    private static final int ROW = 22;
    private static final int BUTTON = 14;
    private static final int SMALL = 12;
    private static final int LOGIC_WIDTH = 40;
    private static final int REFRESH_TICKS = 40;
    private static final int SUPPLY = 0xFF4FA3F7;
    private static final int DEMAND = 0xFFE89A3C;
    private static final int STORAGE = 0xFF5A6A74;
    private static final int RANGE_STEP = 250;
    private static final int PICKER_WIDTH = 232;
    private static final int PICKER_HEIGHT = 214;
    private static final int PICKER_COLUMNS = 12;
    private static final int PICKER_ROW = 12;

    enum Tab {
        INTERSTELLAR, PLANETARY, DISTRIBUTORS, ROUTES
    }

    enum Filter {
        ALL, NAME, PLANET, SYSTEM, ITEM
    }

    enum Sort {
        NAME, NUMBER, ITEM
    }

    /** 选择框在选什么。 */
    private enum Pick {
        SLOT_ITEM, ROUTE_ITEM, ROUTE_FROM, ROUTE_TO, PARTNER
    }

    private record Hit(int x, int y, int width, int height, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.width && my >= this.y && my < this.y + this.height;
        }
    }

    /** 路线的一头：编号（天体路径或星系序号）和显示名。 */
    private record Place(String id, String name) {
    }

    private static Tab lastTab = Tab.INTERSTELLAR;
    private static Filter lastFilter = Filter.ALL;
    private static Sort lastSort = Sort.NAME;

    private @Nullable String focusDimension;
    private @Nullable BlockPos focusPos;
    private Tab tab = lastTab;
    private Filter filter = lastFilter;
    private Sort sort = lastSort;
    private @Nullable UUID selectedStation;
    private @Nullable String selectedDistributorDimension;
    private @Nullable BlockPos selectedDistributorPos;
    private @Nullable UUID selectedRoute;
    private boolean drafting;
    private String draftItem = "";
    private boolean draftSystems;
    private String draftFrom = "";
    private String draftTo = "";
    private @Nullable Pick pick;
    private int pickSlot;
    private int pickScroll;
    private List<Place> places = List.of();
    private String itemQuery = "\u0000";
    private List<Item> itemMatches = List.of();
    private int listScroll;
    private int detailScroll;
    private int detailHeight;
    private int refresh;
    private boolean shift;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listWidth;
    private int detailWidth;
    /** 详情这一块能点的范围（滚出去的按钮不算），画详情的时候 {@code clipping} 为真。 */
    private int clipTop;
    private int clipBottom;
    private boolean clipping;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> pickerHits = new ArrayList<>();
    private @Nullable EditBox search;
    private @Nullable EditBox rename;
    private @Nullable EditBox pickerSearch;
    /** 改名框里现在放的是哪座站的名字。 */
    private @Nullable UUID renameFor;
    private List<Component> tooltip = List.of();
    private ItemStack tooltipStack = ItemStack.EMPTY;
    /** 详情里是别的玩家的东西（快照说不能改）：按钮都是灰的，指着灰按钮时提示为什么。 */
    private boolean readOnly;
    private boolean lockedHover;

    /** 从物流站的界面打开时，选中那座站。 */
    public ControlPanelScreen(@Nullable BlockPos focus) {
        super(Component.translatable("gui.universe.control_panel.title"));
        if (focus != null && net.minecraft.client.Minecraft.getInstance().level != null) {
            this.focusDimension = net.minecraft.client.Minecraft.getInstance().level.dimension().identifier().toString();
            this.focusPos = focus;
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 16, MAX_WIDTH);
        this.panelHeight = Math.min(this.height - 16, MAX_HEIGHT);
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.detailWidth = Math.min(DETAIL_WIDTH, this.panelWidth / 2);
        this.listWidth = this.panelWidth - 8 - 8 - 8 - this.detailWidth;
        String query = this.search == null ? "" : this.search.getValue();
        this.search = new EditBox(this.font, this.left + 8, this.top + TOOL_Y, Math.min(130, this.listWidth - 4), BUTTON,
                Component.translatable("gui.universe.control_panel.search"));
        this.search.setHint(Component.translatable("gui.universe.control_panel.search"));
        this.search.setMaxLength(32);
        this.search.setValue(query);
        this.search.setResponder(value -> this.listScroll = 0);
        this.addRenderableWidget(this.search);
        String name = this.rename == null ? "" : this.rename.getValue();
        this.rename = new EditBox(this.font, 0, 0, this.detailWidth - 44, BUTTON, Component.translatable("gui.universe.control_panel.name_hint"));
        this.rename.setMaxLength(Station.MAX_NAME);
        this.rename.setValue(name);
        this.rename.visible = false;
        this.addRenderableWidget(this.rename);
        String picked = this.pickerSearch == null ? "" : this.pickerSearch.getValue();
        this.pickerSearch = new EditBox(this.font, 0, 0, PICKER_WIDTH - 16, BUTTON, Component.translatable("gui.universe.control_panel.search"));
        this.pickerSearch.setHint(Component.translatable("gui.universe.control_panel.search"));
        this.pickerSearch.setMaxLength(32);
        this.pickerSearch.setValue(picked);
        this.pickerSearch.setResponder(value -> this.pickScroll = 0);
        this.pickerSearch.visible = this.pick != null;
        // 选择框的搜索框盖在面板上面，自己画
        this.addWidget(this.pickerSearch);
        this.refresh = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (--this.refresh <= 0) {
            this.refresh = REFRESH_TICKS;
            ControlPanelClient.request();
        }
    }

    @Override
    public void removed() {
        lastTab = this.tab;
        lastFilter = this.filter;
        lastSort = this.sort;
        super.removed();
    }

    // ---- 数据

    private static StationKind kind(StationRow row) {
        StationKind[] kinds = StationKind.values();
        return kinds[Math.floorMod(row.kind(), kinds.length)];
    }

    private static Logic logic(int ordinal) {
        Logic[] logics = Logic.values();
        return logics[Math.floorMod(ordinal, logics.length)];
    }

    private String query() {
        return this.search == null ? "" : this.search.getValue().strip().toLowerCase(Locale.ROOT);
    }

    private static boolean has(String text, String query) {
        return text.toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesStation(StationRow row, String query) {
        if (query.isEmpty()) {
            return true;
        }
        boolean name = has(ControlPanelClient.stationName(row).getString(), query);
        boolean planet = has(ControlPanelClient.placeName(row.body()), query);
        boolean system = has(ControlPanelClient.systemName(row.system()), query);
        boolean item = false;
        for (SlotRow slot : row.slots()) {
            if (!slot.item().isEmpty() && (has(ControlPanelClient.itemName(slot.item()), query) || has(slot.item(), query))) {
                item = true;
            }
        }
        return switch (this.filter) {
            case ALL -> name || planet || system || item;
            case NAME -> name;
            case PLANET -> planet;
            case SYSTEM -> system;
            case ITEM -> item;
        };
    }

    private List<StationRow> stations(Snapshot snapshot) {
        String query = this.query();
        List<StationRow> rows = new ArrayList<>();
        for (StationRow row : snapshot.stations()) {
            StationKind kind = kind(row);
            boolean shown = this.tab == Tab.INTERSTELLAR ? kind != StationKind.PLANETARY : kind != StationKind.COLLECTOR;
            if (shown && this.matchesStation(row, query)) {
                rows.add(row);
            }
        }
        Comparator<StationRow> byNumber = Comparator.comparingInt((StationRow row) -> row.kind()).thenComparingInt(StationRow::number);
        switch (this.sort) {
            case NAME -> rows.sort(Comparator.comparing((StationRow row) -> ControlPanelClient.stationName(row).getString()).thenComparing(byNumber));
            case NUMBER -> rows.sort(byNumber);
            case ITEM -> rows.sort(Comparator.comparing((StationRow row) -> firstItem(row)).thenComparing(byNumber));
        }
        return rows;
    }

    private static String firstItem(StationRow row) {
        for (SlotRow slot : row.slots()) {
            if (!slot.item().isEmpty()) {
                return ControlPanelClient.itemName(slot.item());
            }
        }
        return "￿";
    }

    private List<DistributorRow> distributors(Snapshot snapshot) {
        String query = this.query();
        List<DistributorRow> rows = new ArrayList<>();
        for (DistributorRow row : snapshot.distributors()) {
            String item = row.item().isEmpty() ? "" : ControlPanelClient.itemName(row.item());
            String place = dimensionName(row.dimension());
            boolean matches = query.isEmpty() || switch (this.filter) {
                case ALL -> has(item, query) || has(row.item(), query) || has(place, query);
                case NAME, ITEM -> has(item, query) || has(row.item(), query);
                case PLANET, SYSTEM -> has(place, query);
            };
            if (matches) {
                rows.add(row);
            }
        }
        Comparator<DistributorRow> byPlace = Comparator.comparing(DistributorRow::dimension).thenComparingLong(row -> row.pos().asLong());
        if (this.sort == Sort.NUMBER) {
            rows.sort(byPlace);
        } else {
            rows.sort(Comparator.comparing((DistributorRow row) -> row.item().isEmpty() ? "￿" : ControlPanelClient.itemName(row.item()))
                    .thenComparing(byPlace));
        }
        return rows;
    }

    private List<RouteRow> routes(Snapshot snapshot) {
        String query = this.query();
        List<RouteRow> rows = new ArrayList<>();
        for (RouteRow row : snapshot.routes()) {
            String item = ControlPanelClient.itemName(row.item());
            String ends = endName(row.systems(), row.from()) + " " + endName(row.systems(), row.to());
            boolean matches = query.isEmpty() || switch (this.filter) {
                case ALL -> has(item, query) || has(row.item(), query) || has(ends, query);
                case NAME, ITEM -> has(item, query) || has(row.item(), query);
                case PLANET, SYSTEM -> has(ends, query);
            };
            if (matches) {
                rows.add(row);
            }
        }
        if (this.sort != Sort.NUMBER) {
            rows.sort(Comparator.comparing((RouteRow row) -> ControlPanelClient.itemName(row.item())));
        }
        return rows;
    }

    private static @Nullable StationRow station(Snapshot snapshot, @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        for (StationRow row : snapshot.stations()) {
            if (row.id().equals(id)) {
                return row;
            }
        }
        return null;
    }

    private @Nullable DistributorRow selectedDistributor(Snapshot snapshot) {
        for (DistributorRow row : snapshot.distributors()) {
            if (row.dimension().equals(this.selectedDistributorDimension) && row.pos().equals(this.selectedDistributorPos)) {
                return row;
            }
        }
        return null;
    }

    private @Nullable RouteRow selectedRoute(Snapshot snapshot) {
        for (RouteRow row : snapshot.routes()) {
            if (row.id().equals(this.selectedRoute)) {
                return row;
            }
        }
        return null;
    }

    /** 维度的显示名：星球的名字，或者"某某 太空"；别的维度用它的编号。 */
    static String dimensionName(String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (id == null || galaxy.isEmpty()) {
            return dimension;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        OptionalInt space = SpaceDimensions.system(key);
        if (space.isPresent()) {
            return Component.translatable("gui.universe.control_panel.space", ControlPanelClient.systemName(space.getAsInt())).getString();
        }
        Optional<BodyId> body = Planets.bodyId(key);
        if (body.isPresent()) {
            return galaxy.get().body(body.get()).map(GalaxyText::planet).orElse(dimension);
        }
        return id.getPath();
    }

    /** 路线一头的显示名。 */
    private static String endName(boolean systems, String end) {
        if (end.isEmpty()) {
            return "—";
        }
        if (systems) {
            try {
                return ControlPanelClient.systemName(Integer.parseInt(end));
            } catch (NumberFormatException e) {
                return end;
            }
        }
        return ControlPanelClient.placeName(end);
    }

    /** 站里存货、上限的短写：999、1.2k、12k。 */
    private static String count(int value) {
        if (value < 1000) {
            return Integer.toString(value);
        }
        if (value < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        }
        return (value / 1000) + "k";
    }

    private static int logicColor(Logic logic) {
        return switch (logic) {
            case SUPPLY -> SUPPLY;
            case DEMAND -> DEMAND;
            case STORAGE -> STORAGE;
        };
    }

    // ---- 发给服务端

    private void sendStation(UUID station, int action, int slot, int value, String text, UUID other) {
        ClientPacketDistributor.sendToServer(new StationEdit(station, action, slot, value, text, other));
        this.refresh = REFRESH_TICKS;
        this.click();
    }

    private void sendStation(UUID station, int action, int value) {
        this.sendStation(station, action, 0, value, "", StationEdit.NONE);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.hits.clear();
        this.pickerHits.clear();
        this.tooltip = List.of();
        this.tooltipStack = ItemStack.EMPTY;
        this.lockedHover = false;
        int x = this.left;
        int y = this.top;
        graphics.fill(x, y, x + this.panelWidth, y + this.panelHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + this.panelWidth - 1, y + this.panelHeight - 1, MachineScreen.PANEL);
        graphics.text(this.font, this.title, x + 8, y + 6, MachineScreen.ACCENT, false);
        graphics.fill(x + 6, y + 16, x + this.panelWidth - 6, y + 17, 0x602E7A92);
        Snapshot snapshot = ControlPanelClient.snapshot().orElse(null);
        this.tabs(graphics, snapshot, mouseX, mouseY);
        this.tools(graphics, mouseX, mouseY);
        if (this.rename != null) {
            this.rename.visible = false;
        }
        if (snapshot == null) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.control_panel.waiting"), x + this.panelWidth / 2, y + 120,
                    MachineScreen.DIM);
        } else {
            this.applyFocus(snapshot);
            this.list(graphics, snapshot, mouseX, mouseY);
            this.detail(graphics, snapshot, mouseX, mouseY);
            if (this.lockedHover) {
                List<Component> lines = new ArrayList<>(this.tooltip);
                lines.add(Component.translatable("gui.universe.control_panel.not_yours").withStyle(net.minecraft.ChatFormatting.GRAY));
                this.tooltip = lines;
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.pickerSearch != null) {
            this.pickerSearch.visible = this.pick != null && snapshot != null;
        }
        if (this.pick != null && snapshot != null) {
            this.tooltip = List.of();
            this.tooltipStack = ItemStack.EMPTY;
            graphics.nextStratum();
            this.picker(graphics, snapshot, mouseX, mouseY, a);
        }
        if (!this.tooltipStack.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, this.tooltipStack, mouseX, mouseY);
        } else if (!this.tooltip.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(this.font, this.tooltip, mouseX, mouseY);
        }
    }

    /** 从物流站的界面打开的：快照到了以后选中那座站。 */
    private void applyFocus(Snapshot snapshot) {
        if (this.focusPos == null) {
            return;
        }
        for (StationRow row : snapshot.stations()) {
            if (row.dimension().equals(this.focusDimension) && row.pos().equals(this.focusPos)) {
                this.tab = kind(row) == StationKind.PLANETARY ? Tab.PLANETARY : Tab.INTERSTELLAR;
                this.selectedStation = row.id();
                this.detailScroll = 0;
                int index = this.stations(snapshot).indexOf(row);
                this.listScroll = Math.max(0, index - 2);
                break;
            }
        }
        this.focusPos = null;
    }

    private void tabs(GuiGraphicsExtractor graphics, @Nullable Snapshot snapshot, int mouseX, int mouseY) {
        int x = this.left + 8;
        for (Tab each : Tab.values()) {
            Component label = Component.translatable("gui.universe.control_panel.tab." + each.name().toLowerCase(Locale.ROOT));
            int width = Math.max(52, this.font.width(label) + 12);
            this.button(graphics, x, this.top + TAB_Y, width, BUTTON, label, this.tab == each, mouseX, mouseY, () -> {
                if (this.tab != each) {
                    this.tab = each;
                    this.listScroll = 0;
                    this.detailScroll = 0;
                    this.drafting = false;
                    this.click();
                }
            }, true);
            x += width + 2;
        }
    }

    private void tools(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.left + 8 + (this.search == null ? 0 : this.search.getWidth()) + 4;
        int y = this.top + TOOL_Y;
        Component filterLabel = Component.translatable("gui.universe.control_panel.filter",
                Component.translatable("gui.universe.control_panel.filter." + this.filter.name().toLowerCase(Locale.ROOT)));
        int filterWidth = this.font.width(filterLabel) + 10;
        this.button(graphics, x, y, filterWidth, BUTTON, filterLabel, false, mouseX, mouseY, () -> {
            this.filter = Filter.values()[(this.filter.ordinal() + 1) % Filter.values().length];
            this.listScroll = 0;
            this.click();
        }, true);
        x += filterWidth + 2;
        Component sortLabel = Component.translatable("gui.universe.control_panel.sort",
                Component.translatable("gui.universe.control_panel.sort." + this.sort.name().toLowerCase(Locale.ROOT)));
        int sortWidth = this.font.width(sortLabel) + 10;
        this.button(graphics, x, y, sortWidth, BUTTON, sortLabel, false, mouseX, mouseY, () -> {
            this.sort = Sort.values()[(this.sort.ordinal() + 1) % Sort.values().length];
            this.click();
        }, true);
        if (this.tab == Tab.ROUTES) {
            Component label = Component.translatable("gui.universe.control_panel.route.new");
            int width = this.font.width(label) + 12;
            this.button(graphics, this.left + this.panelWidth - 8 - width, y, width, BUTTON, label, this.drafting, mouseX, mouseY,
                    this::startDraft, true);
        }
    }

    private int listX() {
        return this.left + 8;
    }

    private int detailX() {
        return this.left + this.panelWidth - 8 - this.detailWidth;
    }

    private int bottom() {
        return this.top + this.panelHeight - 8;
    }

    private int visibleRows() {
        return Math.max(1, (this.bottom() - this.top - LIST_Y) / ROW);
    }

    private void list(GuiGraphicsExtractor graphics, Snapshot snapshot, int mouseX, int mouseY) {
        int x = this.listX();
        int y = this.top + LIST_Y;
        graphics.fill(x, y, x + this.listWidth, this.bottom(), MachineScreen.INSET);
        int size;
        switch (this.tab) {
            case INTERSTELLAR, PLANETARY -> size = this.stationRows(graphics, snapshot, mouseX, mouseY);
            case DISTRIBUTORS -> size = this.distributorRows(graphics, snapshot, mouseX, mouseY);
            default -> size = this.routeRows(graphics, snapshot, mouseX, mouseY);
        }
        if (size == 0) {
            boolean filtered = !this.query().isEmpty();
            String key = filtered ? "filtered" : switch (this.tab) {
                case DISTRIBUTORS -> "distributors";
                case ROUTES -> "routes";
                default -> "stations";
            };
            int lineY = y + 30;
            for (FormattedCharSequence line : TextWrap.split(this.font, Component.translatable("gui.universe.control_panel.empty." + key),
                    this.listWidth - 16)) {
                graphics.centeredText(this.font, line, x + this.listWidth / 2, lineY, MachineScreen.DIM);
                lineY += 10;
            }
        }
        int visible = this.visibleRows();
        if (size > visible) {
            int trackHeight = visible * ROW;
            int thumb = Math.max(10, trackHeight * visible / size);
            int thumbY = y + (trackHeight - thumb) * this.listScroll / (size - visible);
            graphics.fill(x + this.listWidth - 3, y, x + this.listWidth, y + trackHeight, 0xFF1A2A33);
            graphics.fill(x + this.listWidth - 3, thumbY, x + this.listWidth, thumbY + thumb, 0xFF4FA3C0);
        }
    }

    /** 列表的一行：底色、选中和指着的高亮。返回指着没有。 */
    private boolean rowBackground(GuiGraphicsExtractor graphics, int index, int rowY, boolean selected, int mouseX, int mouseY) {
        int x = this.listX();
        boolean hovered = this.pick == null && mouseX >= x && mouseX < x + this.listWidth - 3 && mouseY >= rowY && mouseY < rowY + ROW;
        if (index % 2 == 1) {
            graphics.fill(x, rowY, x + this.listWidth - 3, rowY + ROW, 0x10FFFFFF);
        }
        if (selected) {
            graphics.fill(x, rowY, x + this.listWidth - 3, rowY + ROW, 0x402E7A92);
            graphics.fill(x, rowY, x + 2, rowY + ROW, MachineScreen.ACCENT);
        } else if (hovered) {
            graphics.fill(x, rowY, x + this.listWidth - 3, rowY + ROW, 0x18FFFFFF);
        }
        return hovered;
    }

    private int clampScroll(int size) {
        this.listScroll = Math.max(0, Math.min(this.listScroll, size - this.visibleRows()));
        return this.listScroll;
    }

    private int stationRows(GuiGraphicsExtractor graphics, Snapshot snapshot, int mouseX, int mouseY) {
        List<StationRow> rows = this.stations(snapshot);
        int scroll = this.clampScroll(rows.size());
        int x = this.listX();
        // 数字右对齐在图标右下角，"10k" 这类比图标宽：按这一页最宽的数字统一列宽，各行的列对得齐
        int step = 18;
        for (int i = 0; i < this.visibleRows() && scroll + i < rows.size(); i++) {
            for (SlotRow slot : rows.get(scroll + i).slots()) {
                if (!slot.item().isEmpty()) {
                    step = Math.max(step, this.font.width(count(slot.count())) + 2);
                }
            }
        }
        for (int i = 0; i < this.visibleRows() && scroll + i < rows.size(); i++) {
            StationRow row = rows.get(scroll + i);
            int rowY = this.top + LIST_Y + i * ROW;
            this.rowBackground(graphics, i, rowY, row.id().equals(this.selectedStation), mouseX, mouseY);
            // 右边是各格的物品，供需的颜色画在下面：星际页看远程，行星内页看本地
            int iconX = x + this.listWidth - 3 - 18;
            List<SlotRow> slots = row.slots();
            int iconsLeft = iconX;
            for (int k = slots.size() - 1; k >= 0; k--) {
                SlotRow slot = slots.get(k);
                if (slot.item().isEmpty()) {
                    continue;
                }
                ItemStack stack = new ItemStack(ControlPanelClient.item(slot.item()));
                String amount = count(slot.count());
                graphics.fakeItem(stack, iconX, rowY + 1);
                graphics.itemDecorations(this.font, stack, iconX, rowY + 1, amount);
                Logic logic = logic(this.tab == Tab.PLANETARY ? slot.local() : slot.remote());
                graphics.fill(iconX + 1, rowY + 18, iconX + 15, rowY + 20, logicColor(logic));
                if (this.pick == null && mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + ROW) {
                    this.tooltip = this.slotTooltip(row, slot);
                }
                iconsLeft = Math.min(iconX, iconX + 17 - this.font.width(amount));
                iconX -= step;
            }
            int textWidth = iconsLeft - x - 8;
            String name = this.font.plainSubstrByWidth(ControlPanelClient.stationName(row).getString(), textWidth);
            graphics.text(this.font, name, x + 5, rowY + 2, MachineScreen.TEXT, false);
            String place = ControlPanelClient.placeName(row.body());
            String vehicles = kind(row) == StationKind.COLLECTOR ? "" : this.tab == Tab.PLANETARY || kind(row) == StationKind.PLANETARY
                    ? "  " + Component.translatable("gui.universe.control_panel.short_drones", row.idleDrones(), row.drones()).getString()
                    : "  " + Component.translatable("gui.universe.control_panel.short_vessels", row.idleVessels(), row.vessels()).getString();
            graphics.text(this.font, this.font.plainSubstrByWidth(place + vehicles, textWidth), x + 5, rowY + 12, MachineScreen.DIM, false);
            this.hit(x, rowY, this.listWidth - 3, ROW, () -> {
                if (!row.id().equals(this.selectedStation)) {
                    this.selectedStation = row.id();
                    this.detailScroll = 0;
                    this.click();
                }
            });
        }
        return rows.size();
    }

    private List<Component> slotTooltip(StationRow row, SlotRow slot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(ControlPanelClient.itemName(slot.item())));
        lines.add(Component.translatable("gui.universe.station.stock", slot.count(), slot.max(), kind(row).slotCapacity())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (kind(row) != StationKind.COLLECTOR) {
            lines.add(Component.translatable("gui.universe.control_panel.logic_local",
                    Component.translatable("gui.universe.station.logic." + logic(slot.local()).name().toLowerCase(Locale.ROOT)))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (kind(row).remote()) {
            lines.add(Component.translatable("gui.universe.control_panel.logic_remote",
                    Component.translatable("gui.universe.station.logic." + logic(slot.remote()).name().toLowerCase(Locale.ROOT)))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (slot.incoming() > 0) {
            lines.add(Component.translatable("gui.universe.station.incoming", slot.incoming()).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        return lines;
    }

    private int distributorRows(GuiGraphicsExtractor graphics, Snapshot snapshot, int mouseX, int mouseY) {
        List<DistributorRow> rows = this.distributors(snapshot);
        int scroll = this.clampScroll(rows.size());
        int x = this.listX();
        for (int i = 0; i < this.visibleRows() && scroll + i < rows.size(); i++) {
            DistributorRow row = rows.get(scroll + i);
            int rowY = this.top + LIST_Y + i * ROW;
            boolean selected = row.dimension().equals(this.selectedDistributorDimension) && row.pos().equals(this.selectedDistributorPos);
            this.rowBackground(graphics, i, rowY, selected, mouseX, mouseY);
            int textX = x + 24;
            if (!row.item().isEmpty()) {
                graphics.fakeItem(new ItemStack(ControlPanelClient.item(row.item())), x + 4, rowY + 3);
            }
            int textWidth = this.listWidth - 30;
            Component name = row.item().isEmpty() ? Component.translatable("gui.universe.distributor.no_item")
                    : Component.literal(ControlPanelClient.itemName(row.item()));
            graphics.text(this.font, this.font.plainSubstrByWidth(name.getString(), textWidth), textX, rowY + 2,
                    row.item().isEmpty() ? MachineScreen.DIM : MachineScreen.TEXT, false);
            String modes = icarus(row).getString() + "  " + peer(row).getString();
            graphics.text(this.font, this.font.plainSubstrByWidth(dimensionName(row.dimension()) + "  " + modes, textWidth), textX, rowY + 12,
                    MachineScreen.DIM, false);
            this.hit(x, rowY, this.listWidth - 3, ROW, () -> {
                this.selectedDistributorDimension = row.dimension();
                this.selectedDistributorPos = row.pos();
                this.detailScroll = 0;
                this.click();
            });
        }
        return rows.size();
    }

    private static Component icarus(DistributorRow row) {
        Distribution.Icarus[] values = Distribution.Icarus.values();
        return Component.translatable("gui.universe.distributor.icarus." + values[Math.floorMod(row.icarus(), values.length)].name()
                .toLowerCase(Locale.ROOT));
    }

    private static Component peer(DistributorRow row) {
        Distribution.Peer[] values = Distribution.Peer.values();
        return Component.translatable("gui.universe.distributor.peer." + values[Math.floorMod(row.peer(), values.length)].name()
                .toLowerCase(Locale.ROOT));
    }

    private int routeRows(GuiGraphicsExtractor graphics, Snapshot snapshot, int mouseX, int mouseY) {
        List<RouteRow> rows = this.routes(snapshot);
        int scroll = this.clampScroll(rows.size());
        int x = this.listX();
        for (int i = 0; i < this.visibleRows() && scroll + i < rows.size(); i++) {
            RouteRow row = rows.get(scroll + i);
            int rowY = this.top + LIST_Y + i * ROW;
            this.rowBackground(graphics, i, rowY, !this.drafting && row.id().equals(this.selectedRoute), mouseX, mouseY);
            graphics.fakeItem(new ItemStack(ControlPanelClient.item(row.item())), x + 4, rowY + 3);
            int textWidth = this.listWidth - 30;
            String count = Integer.toString(row.stations().size());
            graphics.text(this.font, this.font.plainSubstrByWidth(ControlPanelClient.itemName(row.item()), textWidth - 20), x + 24, rowY + 2,
                    MachineScreen.TEXT, false);
            graphics.text(this.font, count, x + this.listWidth - 8 - this.font.width(count), rowY + 2,
                    row.stations().isEmpty() ? MachineScreen.DIM : MachineScreen.GOOD, false);
            String ends = endName(row.systems(), row.from()) + " → " + endName(row.systems(), row.to());
            graphics.text(this.font, this.font.plainSubstrByWidth(ends, textWidth), x + 24, rowY + 12, ControlPanelClient.itemColor(row.item()),
                    false);
            this.hit(x, rowY, this.listWidth - 3, ROW, () -> {
                this.selectedRoute = row.id();
                this.drafting = false;
                this.detailScroll = 0;
                this.click();
            });
        }
        return rows.size();
    }

    // ---- 详情

    private void detail(GuiGraphicsExtractor graphics, Snapshot snapshot, int mouseX, int mouseY) {
        int x = this.detailX();
        int y = this.top + LIST_Y;
        graphics.fill(x - 3, y, x + this.detailWidth, this.bottom(), 0x60060B0F);
        this.clipTop = y;
        this.clipBottom = this.bottom();
        this.clipping = true;
        graphics.enableScissor(x - 3, y, x + this.detailWidth, this.bottom());
        int start = y + 4 - this.detailScroll;
        int end = start;
        this.readOnly = false;
        switch (this.tab) {
            case INTERSTELLAR, PLANETARY -> {
                StationRow row = station(snapshot, this.selectedStation);
                end = row == null ? this.hint(graphics, x, start) : this.stationDetail(graphics, snapshot, row, x, start, mouseX, mouseY);
            }
            case DISTRIBUTORS -> {
                DistributorRow row = this.selectedDistributor(snapshot);
                end = row == null ? this.hint(graphics, x, start) : this.distributorDetail(graphics, row, x, start, mouseX, mouseY);
            }
            case ROUTES -> {
                RouteRow row = this.drafting ? null : this.selectedRoute(snapshot);
                end = this.drafting ? this.draftDetail(graphics, x, start, mouseX, mouseY)
                        : row == null ? this.hint(graphics, x, start) : this.routeDetail(graphics, snapshot, row, x, start, mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        this.clipping = false;
        this.readOnly = false;
        this.detailHeight = end + this.detailScroll - y;
        int room = this.bottom() - y;
        this.detailScroll = Math.max(0, Math.min(this.detailScroll, this.detailHeight - room));
        if (this.detailHeight > room) {
            int thumb = Math.max(10, room * room / this.detailHeight);
            int thumbY = y + (room - thumb) * this.detailScroll / Math.max(1, this.detailHeight - room);
            graphics.fill(x + this.detailWidth - 2, y, x + this.detailWidth, y + room, 0xFF1A2A33);
            graphics.fill(x + this.detailWidth - 2, thumbY, x + this.detailWidth, thumbY + thumb, 0xFF4FA3C0);
        }
    }

    private int hint(GuiGraphicsExtractor graphics, int x, int y) {
        int lineY = y + 20;
        for (FormattedCharSequence line : TextWrap.split(this.font, Component.translatable("gui.universe.control_panel.select_hint"),
                this.detailWidth - 12)) {
            graphics.centeredText(this.font, line, x + this.detailWidth / 2, lineY, MachineScreen.DIM);
            lineY += 10;
        }
        return lineY;
    }

    private int wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : TextWrap.split(this.font, text, this.detailWidth - 8)) {
            graphics.text(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private int stationDetail(GuiGraphicsExtractor graphics, Snapshot snapshot, StationRow row, int x, int y, int mouseX, int mouseY) {
        StationKind kind = kind(row);
        int w = this.detailWidth - 6;
        // 别的玩家的站只能看
        boolean edit = row.editable();
        this.readOnly = !edit;
        // 名字：输入框加"改名"
        if (this.rename != null) {
            if (!row.id().equals(this.renameFor)) {
                this.renameFor = row.id();
                this.rename.setValue(row.name());
                this.rename.setFocused(false);
            } else if (!this.rename.isFocused() && !this.rename.getValue().equals(row.name())) {
                // 别人改了名，或者刚改好：跟着账本
                this.rename.setValue(row.name());
            }
            this.rename.setHint(ControlPanelClient.defaultName(row));
            this.rename.setX(x);
            this.rename.setY(y);
            this.rename.setWidth(w - 42);
            this.rename.setEditable(edit);
            this.rename.visible = y >= this.clipTop && y + BUTTON <= this.clipBottom && this.pick == null;
        }
        this.button(graphics, x + w - 40, y, 40, BUTTON, Component.translatable("gui.universe.control_panel.rename"), false, mouseX, mouseY,
                this::commitRename, edit);
        y += BUTTON + 4;
        if (!edit) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.not_yours"), x, y, MachineScreen.WARN) + 2;
        }
        String place = ControlPanelClient.placeName(row.body()) + " · " + ControlPanelClient.systemName(row.system());
        graphics.text(this.font, this.font.plainSubstrByWidth(place, w), x, y, MachineScreen.TEXT, false);
        y += 10;
        String where = Component.translatable("block.universe." + switch (kind) {
            case PLANETARY -> "planetary_logistics_station";
            case INTERSTELLAR -> "interstellar_logistics_station";
            case COLLECTOR -> "orbital_collector";
        }).getString() + "  " + row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ();
        graphics.text(this.font, this.font.plainSubstrByWidth(where, w), x, y, MachineScreen.DIM, false);
        y += 13;
        // 分组、指定配对
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.group"), x, y + 3, MachineScreen.DIM, false);
        int gx = x + 46;
        this.button(graphics, gx, y, SMALL, BUTTON, Component.literal("-"), false, mouseX, mouseY,
                () -> this.sendStation(row.id(), StationEdit.GROUP, Math.floorMod(row.group() - 1, Station.MAX_GROUP + 1)), edit);
        Component group = row.group() == 0 ? Component.translatable("gui.universe.control_panel.group_none") : Component.literal(
                Integer.toString(row.group()));
        graphics.centeredText(this.font, group, gx + SMALL + 14, y + 3, row.group() == 0 ? MachineScreen.DIM : MachineScreen.ACCENT);
        this.button(graphics, gx + SMALL + 28, y, SMALL, BUTTON, Component.literal("+"), false, mouseX, mouseY,
                () -> this.sendStation(row.id(), StationEdit.GROUP, (row.group() + 1) % (Station.MAX_GROUP + 1)), edit);
        Component isolated = Component.translatable(row.isolated() ? "gui.universe.control_panel.on" : "gui.universe.control_panel.off");
        int isolatedX = x + w - 34;
        Component isolatedLabel = Component.translatable("gui.universe.control_panel.isolated");
        graphics.text(this.font, isolatedLabel, isolatedX - 4 - this.font.width(isolatedLabel), y + 3, MachineScreen.DIM, false);
        this.button(graphics, isolatedX, y, 34, BUTTON, isolated, row.isolated(), mouseX, mouseY,
                () -> this.sendStation(row.id(), StationEdit.ISOLATE, row.isolated() ? 0 : 1), edit);
        if (this.hovering(isolatedX - 60, y, 94, BUTTON, mouseX, mouseY)) {
            this.tooltip = List.of(Component.translatable("gui.universe.control_panel.isolated_hint"));
        }
        y += BUTTON + 4;
        if (kind != StationKind.COLLECTOR) {
            // 无人机航程
            graphics.text(this.font, Component.translatable("gui.universe.control_panel.range"), x, y + 3, MachineScreen.DIM, false);
            int rx = x + 46;
            this.button(graphics, rx, y, SMALL, BUTTON, Component.literal("-"), false, mouseX, mouseY,
                    () -> this.sendStation(row.id(), StationEdit.RANGE, row.range() - RANGE_STEP), edit);
            graphics.centeredText(this.font, Component.literal(Integer.toString(row.range())), rx + SMALL + 14, y + 3, MachineScreen.TEXT);
            this.button(graphics, rx + SMALL + 28, y, SMALL, BUTTON, Component.literal("+"), false, mouseX, mouseY,
                    () -> this.sendStation(row.id(), StationEdit.RANGE, row.range() + RANGE_STEP), edit);
            if (kind.remote()) {
                // 运输船的起送量
                Component load = row.minLoad() == 0 ? Component.translatable("gui.universe.control_panel.min_load_default")
                        : Component.literal(row.minLoad() + "%");
                int loadWidth = 54;
                int loadX = x + w - loadWidth;
                Component loadLabel = Component.translatable("gui.universe.control_panel.min_load");
                graphics.text(this.font, loadLabel, loadX - 4 - this.font.width(loadLabel), y + 3, MachineScreen.DIM, false);
                this.button(graphics, loadX, y, loadWidth, BUTTON, load, false, mouseX, mouseY, () -> {
                    int next = 0;
                    for (int i = 0; i < Station.MIN_LOADS.length; i++) {
                        if (Station.MIN_LOADS[i] == row.minLoad()) {
                            next = Station.MIN_LOADS[(i + 1) % Station.MIN_LOADS.length];
                        }
                    }
                    this.sendStation(row.id(), StationEdit.MIN_LOAD, next);
                }, edit);
                if (this.hovering(loadX - 50, y, loadWidth + 50, BUTTON, mouseX, mouseY)) {
                    this.tooltip = List.of(Component.translatable("gui.universe.control_panel.min_load_hint"));
                }
            }
            y += BUTTON + 4;
        }
        // 点对点的伙伴
        List<String> names = new ArrayList<>();
        for (StationRow other : snapshot.stations()) {
            if (partnered(row, other)) {
                names.add(ControlPanelClient.stationName(other).getString());
            }
        }
        Component partners = Component.translatable("gui.universe.control_panel.partners", names.size());
        graphics.text(this.font, partners, x, y + 3, MachineScreen.DIM, false);
        int namesX = x + this.font.width(partners) + 6;
        String joined = names.isEmpty() ? Component.translatable("gui.universe.control_panel.no_partners").getString() : String.join("、", names);
        graphics.text(this.font, this.font.plainSubstrByWidth(joined, w - 44 - (namesX - x)), namesX, y + 3,
                names.isEmpty() ? MachineScreen.DIM : MachineScreen.TEXT, false);
        this.button(graphics, x + w - 40, y, 40, BUTTON, Component.translatable("gui.universe.control_panel.manage"), false, mouseX, mouseY,
                () -> this.openPicker(Pick.PARTNER, 0), edit);
        if (!names.isEmpty() && this.hovering(namesX, y, w - 44 - (namesX - x), BUTTON, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            names.forEach(name -> lines.add(Component.literal(name)));
            this.tooltip = lines;
        }
        y += BUTTON + 6;
        // 每一格
        graphics.text(this.font, Component.translatable("gui.universe.station.item"), x, y, MachineScreen.DIM, false);
        int buttonsLeft = x + w - 3 * (SMALL + 1) - (kind.remote() ? 2 * (LOGIC_WIDTH + 2) : LOGIC_WIDTH + 2);
        if (kind != StationKind.COLLECTOR) {
            graphics.text(this.font, Component.translatable("gui.universe.station.local"), buttonsLeft, y, MachineScreen.DIM, false);
            if (kind.remote()) {
                graphics.text(this.font, Component.translatable("gui.universe.station.remote"), buttonsLeft + LOGIC_WIDTH + 2, y, MachineScreen.DIM,
                        false);
            }
        }
        y += 10;
        for (int i = 0; i < row.slots().size(); i++) {
            this.slotRow(graphics, row, i, x, y, w, buttonsLeft, mouseX, mouseY);
            y += 20;
        }
        if (kind == StationKind.COLLECTOR) {
            graphics.text(this.font, Component.translatable("gui.universe.control_panel.collector_note"), x, y, MachineScreen.DIM, false);
            y += 12;
        }
        // 载具和定位
        Component vehicles = kind == StationKind.INTERSTELLAR
                ? Component.translatable("gui.universe.control_panel.vehicles", row.idleDrones(), row.drones(), row.idleVessels(), row.vessels(),
                        row.warpers())
                : kind == StationKind.PLANETARY ? Component.translatable("gui.universe.control_panel.drones", row.idleDrones(), row.drones())
                : Component.empty();
        y = this.wrapped(graphics, vehicles, x, y + 2, MachineScreen.DIM) + 2;
        y = this.locate(graphics, row.dimension(), row.pos(), ControlPanelClient.stationName(row), x, y, mouseX, mouseY,
                () -> {
                    io.github.sweetcornna.universe.client.stats.DashboardHud.pinStation(row.id());
                    this.click();
                });
        return y;
    }

    private static boolean partnered(StationRow a, StationRow b) {
        return !a.id().equals(b.id()) && (a.partners().contains(b.id()) || b.partners().contains(a.id()));
    }

    private void commitRename() {
        UUID id = this.renameFor;
        StationRow row = id == null ? null : ControlPanelClient.snapshot().map(snapshot -> station(snapshot, id)).orElse(null);
        if (this.rename == null || row == null || !row.editable()) {
            return;
        }
        this.sendStation(this.renameFor, StationEdit.RENAME, 0, 0, this.rename.getValue(), StationEdit.NONE);
        this.rename.setFocused(false);
        this.setFocused(null);
    }

    private void slotRow(GuiGraphicsExtractor graphics, StationRow row, int index, int x, int y, int w, int buttonsLeft, int mouseX, int mouseY) {
        SlotRow slot = row.slots().get(index);
        StationKind kind = kind(row);
        boolean collector = kind == StationKind.COLLECTOR;
        boolean edit = row.editable();
        boolean hovered = this.hovering(x, y, 18, 18, mouseX, mouseY);
        graphics.fill(x, y, x + 18, y + 18, hovered && !collector && edit ? MachineScreen.ACCENT : 0xFF2A4A58);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, MachineScreen.INSET);
        boolean empty = slot.item().isEmpty();
        if (!empty) {
            graphics.fakeItem(new ItemStack(ControlPanelClient.item(slot.item())), x + 1, y + 1);
        }
        boolean locked = !empty && (slot.count() > 0 || slot.incoming() > 0);
        if (hovered) {
            List<Component> lines = new ArrayList<>(empty ? List.of(Component.translatable("gui.universe.control_panel.slot_empty"))
                    : this.slotTooltip(row, slot));
            if (!collector && edit) {
                lines.add(Component.translatable(locked ? "gui.universe.control_panel.slot_locked" : "gui.universe.control_panel.slot_hint")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            this.lockedHover |= !collector && !edit;
            this.tooltip = lines;
        }
        if (!collector && edit) {
            this.hit(x, y, 18, 18, () -> {
                if (!locked) {
                    this.openPicker(Pick.SLOT_ITEM, index);
                }
            });
        }
        String amount = empty ? Component.translatable("gui.universe.control_panel.slot_empty").getString()
                : count(slot.count()) + "/" + count(Math.min(slot.max(), kind.slotCapacity()));
        graphics.text(this.font, this.font.plainSubstrByWidth(amount, buttonsLeft - x - 22), x + 21, y + 5,
                empty ? MachineScreen.DIM : MachineScreen.TEXT, false);
        if (collector) {
            return;
        }
        int bx = buttonsLeft;
        this.logicButton(graphics, row, index, logic(slot.local()), StationBlockEntity.CYCLE_LOCAL, bx, y + 2, mouseX, mouseY, empty);
        bx += LOGIC_WIDTH + 2;
        if (kind.remote()) {
            this.logicButton(graphics, row, index, logic(slot.remote()), StationBlockEntity.CYCLE_REMOTE, bx, y + 2, mouseX, mouseY, empty);
            bx += LOGIC_WIDTH + 2;
        }
        this.button(graphics, bx, y + 2, SMALL, BUTTON, Component.literal("-"), false, mouseX, mouseY, () -> this.sendStation(row.id(),
                StationEdit.SLOT_BUTTON, index, this.shift ? StationBlockEntity.MAX_MINUS_BIG : StationBlockEntity.MAX_MINUS, "", StationEdit.NONE),
                !empty && edit);
        this.button(graphics, bx + SMALL + 1, y + 2, SMALL, BUTTON, Component.literal("+"), false, mouseX, mouseY, () -> this.sendStation(
                row.id(), StationEdit.SLOT_BUTTON, index, this.shift ? StationBlockEntity.MAX_PLUS_BIG : StationBlockEntity.MAX_PLUS, "",
                StationEdit.NONE), !empty && edit);
        this.button(graphics, bx + 2 * (SMALL + 1), y + 2, SMALL, BUTTON, Component.literal("×"), false, mouseX, mouseY,
                () -> this.sendStation(row.id(), StationEdit.SLOT_BUTTON, index, StationBlockEntity.CLEAR, "", StationEdit.NONE),
                !empty && !locked && edit);
        if (this.hovering(bx, y + 2, 2 * SMALL + 1, BUTTON, mouseX, mouseY) && !empty && edit) {
            this.tooltip = List.of(Component.translatable("gui.universe.control_panel.limit_hint"));
        } else if (this.hovering(bx + 2 * (SMALL + 1), y + 2, SMALL, BUTTON, mouseX, mouseY) && !empty && edit) {
            this.tooltip = List.of(Component.translatable(locked ? "gui.universe.control_panel.slot_locked" : "gui.universe.control_panel.clear"));
        }
    }

    /** 供需按钮；别的玩家的站照样显示颜色，只是点不了。 */
    private void logicButton(GuiGraphicsExtractor graphics, StationRow row, int index, Logic logic, int action, int x, int y, int mouseX, int mouseY,
            boolean empty) {
        Component label = Component.translatable("gui.universe.station.logic." + logic.name().toLowerCase(Locale.ROOT));
        boolean pointed = !empty && this.hovering(x, y, LOGIC_WIDTH, BUTTON, mouseX, mouseY);
        boolean hovered = pointed && row.editable();
        int color = logicColor(logic);
        graphics.fill(x, y, x + LOGIC_WIDTH, y + BUTTON, hovered ? MachineScreen.ACCENT : empty ? 0xFF1C2A31 : color);
        graphics.fill(x + 1, y + 1, x + LOGIC_WIDTH - 1, y + BUTTON - 1, hovered ? 0xFF16303B : MachineScreen.INSET);
        graphics.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(label.getString(), LOGIC_WIDTH - 4)),
                x + LOGIC_WIDTH / 2, y + 3, empty ? 0xFF3A4A52 : color);
        if (!empty && row.editable()) {
            this.hit(x, y, LOGIC_WIDTH, BUTTON, () -> this.sendStation(row.id(), StationEdit.SLOT_BUTTON, index, action, "", StationEdit.NONE));
        }
        this.lockedHover |= pointed && !row.editable();
    }

    /** "定位"按钮：再点一下取消；标在别的星球上时写一句。站的后面跟一个"钉到仪表盘"（{@code pin}）。 */
    private int locate(GuiGraphicsExtractor graphics, String dimension, BlockPos pos, Component label, int x, int y, int mouseX, int mouseY,
            @Nullable Runnable pin) {
        boolean on = ControlPanelClient.locating(dimension, pos);
        Component text = Component.translatable(on ? "gui.universe.control_panel.locate_off" : "gui.universe.control_panel.locate");
        int width = this.font.width(text) + 14;
        this.button(graphics, x, y, width, BUTTON, text, on, mouseX, mouseY, () -> {
            if (ControlPanelClient.locating(dimension, pos)) {
                ControlPanelClient.clearTarget();
            } else {
                ControlPanelClient.locate(dimension, pos, label);
            }
            this.click();
        }, true);
        if (pin != null) {
            Component pinLabel = Component.translatable("gui.universe.control_panel.pin");
            this.button(graphics, x + width + 4, y, this.font.width(pinLabel) + 14, BUTTON, pinLabel, false, mouseX, mouseY, pin, true);
        }
        y += BUTTON + 3;
        String here = this.minecraft.level == null ? "" : this.minecraft.level.dimension().identifier().toString();
        if (on && !here.equals(dimension)) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.located_elsewhere", dimensionName(dimension)), x, y,
                    MachineScreen.WARN);
        }
        return y + 2;
    }

    private int distributorDetail(GuiGraphicsExtractor graphics, DistributorRow row, int x, int y, int mouseX, int mouseY) {
        int w = this.detailWidth - 6;
        graphics.fill(x, y, x + 18, y + 18, 0xFF2A4A58);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, MachineScreen.INSET);
        if (!row.item().isEmpty()) {
            graphics.fakeItem(new ItemStack(ControlPanelClient.item(row.item())), x + 1, y + 1);
        }
        Component name = row.item().isEmpty() ? Component.translatable("gui.universe.distributor.no_item")
                : Component.literal(ControlPanelClient.itemName(row.item()));
        graphics.text(this.font, this.font.plainSubstrByWidth(name.getString(), w - 22), x + 22, y + 5, MachineScreen.TEXT, false);
        y += 22;
        graphics.text(this.font, this.font.plainSubstrByWidth(dimensionName(row.dimension()), w), x, y, MachineScreen.TEXT, false);
        y += 10;
        graphics.text(this.font, row.pos().getX() + ", " + row.pos().getY() + ", " + row.pos().getZ(), x, y, MachineScreen.DIM, false);
        y += 10;
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.bots", row.bots()), x, y, MachineScreen.DIM, false);
        y += 14;
        boolean edit = row.editable();
        this.readOnly = !edit;
        if (!edit) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.not_yours"), x, y, MachineScreen.WARN) + 2;
        }
        Distribution.Icarus[] icarus = Distribution.Icarus.values();
        Distribution.Peer[] peers = Distribution.Peer.values();
        String icarusKey = icarus[Math.floorMod(row.icarus(), icarus.length)].name().toLowerCase(Locale.ROOT);
        String peerKey = peers[Math.floorMod(row.peer(), peers.length)].name().toLowerCase(Locale.ROOT);
        this.button(graphics, x, y, w, BUTTON, icarus(row), false, mouseX, mouseY,
                () -> this.sendDistributor(row, DistributorBlockEntity.CYCLE_ICARUS), edit);
        if (this.hovering(x, y, w, BUTTON, mouseX, mouseY)) {
            this.tooltip = List.of(Component.translatable("gui.universe.distributor.icarus_hint." + icarusKey));
        }
        y += BUTTON + 3;
        this.button(graphics, x, y, w, BUTTON, peer(row), false, mouseX, mouseY,
                () -> this.sendDistributor(row, DistributorBlockEntity.CYCLE_PEER), edit);
        if (this.hovering(x, y, w, BUTTON, mouseX, mouseY)) {
            this.tooltip = List.of(Component.translatable("gui.universe.distributor.peer_hint." + peerKey));
        }
        y += BUTTON + 4;
        if (row.pending()) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.pending"), x, y, MachineScreen.WARN) + 2;
        }
        y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.item_onsite"), x, y, MachineScreen.DIM) + 4;
        return this.locate(graphics, row.dimension(), row.pos(), name, x, y, mouseX, mouseY, null);
    }

    private void sendDistributor(DistributorRow row, int button) {
        ClientPacketDistributor.sendToServer(new ControlPanelPayloads.DistributorEdit(row.dimension(), row.pos(), button));
        this.refresh = REFRESH_TICKS;
        this.click();
    }

    private int routeDetail(GuiGraphicsExtractor graphics, Snapshot snapshot, RouteRow row, int x, int y, int mouseX, int mouseY) {
        int w = this.detailWidth - 6;
        graphics.fakeItem(new ItemStack(ControlPanelClient.item(row.item())), x + 1, y + 1);
        graphics.text(this.font, this.font.plainSubstrByWidth(ControlPanelClient.itemName(row.item()), w - 22), x + 22, y + 5, MachineScreen.TEXT,
                false);
        y += 22;
        graphics.text(this.font, Component.translatable(row.systems() ? "gui.universe.control_panel.route.systems"
                : "gui.universe.control_panel.route.planets"), x, y, ControlPanelClient.itemColor(row.item()), false);
        y += 12;
        y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.route.from_to", endName(row.systems(), row.from()),
                endName(row.systems(), row.to())), x, y, MachineScreen.TEXT) + 4;
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.route.stations", row.stations().size()), x, y,
                MachineScreen.DIM, false);
        y += 12;
        if (row.stations().isEmpty()) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.route.none"), x, y, MachineScreen.DIM) + 2;
        }
        for (UUID id : row.stations()) {
            StationRow station = station(snapshot, id);
            if (station == null) {
                continue;
            }
            boolean hovered = this.hovering(x, y, w, 11, mouseX, mouseY);
            String line = ControlPanelClient.stationName(station).getString() + " · " + ControlPanelClient.placeName(station.body());
            graphics.text(this.font, this.font.plainSubstrByWidth(line, w - 4), x + 4, y, hovered ? MachineScreen.ACCENT : MachineScreen.TEXT, false);
            this.hit(x, y, w, 11, () -> {
                this.tab = Tab.INTERSTELLAR;
                this.selectedStation = station.id();
                this.listScroll = Math.max(0, this.stations(snapshot).indexOf(station) - 2);
                this.detailScroll = 0;
                this.click();
            });
            y += 11;
        }
        y += 4;
        this.readOnly = !row.editable();
        this.button(graphics, x, y, 50, BUTTON, Component.translatable("gui.universe.control_panel.route.delete"), false, mouseX, mouseY, () -> {
            ClientPacketDistributor.sendToServer(new ControlPanelPayloads.RouteEdit(false, row.id(), "", false, "", ""));
            this.selectedRoute = null;
            this.refresh = REFRESH_TICKS;
            this.click();
        }, row.editable());
        y += BUTTON + 4;
        if (!row.editable()) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.not_yours"), x, y, MachineScreen.WARN) + 2;
        }
        return y;
    }

    private void startDraft() {
        this.drafting = true;
        this.selectedRoute = null;
        this.detailScroll = 0;
        this.draftItem = "";
        this.draftTo = "";
        this.draftFrom = this.currentEnd(this.draftSystems);
        this.click();
    }

    /** 玩家现在所在的那一头：星球路线是脚下的星球，星系路线是这个星系。 */
    private String currentEnd(boolean systems) {
        if (systems) {
            OptionalInt system = ClientGalaxy.currentSystem();
            return system.isPresent() ? Integer.toString(system.getAsInt()) : "";
        }
        return ClientGalaxy.currentPlanet().map(planet -> planet.id().path()).orElse("");
    }

    private int draftDetail(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        int w = this.detailWidth - 6;
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.route.new"), x, y, MachineScreen.ACCENT, false);
        y += 14;
        // 物品
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.route.item"), x, y + 5, MachineScreen.DIM, false);
        int fieldX = x + 40;
        boolean hovered = this.hovering(fieldX, y, 18, 18, mouseX, mouseY);
        graphics.fill(fieldX, y, fieldX + 18, y + 18, hovered ? MachineScreen.ACCENT : 0xFF2A4A58);
        graphics.fill(fieldX + 1, y + 1, fieldX + 17, y + 17, MachineScreen.INSET);
        if (!this.draftItem.isEmpty()) {
            graphics.fakeItem(new ItemStack(ControlPanelClient.item(this.draftItem)), fieldX + 1, y + 1);
        }
        Component itemName = this.draftItem.isEmpty() ? Component.translatable("gui.universe.control_panel.route.pick")
                : Component.literal(ControlPanelClient.itemName(this.draftItem));
        graphics.text(this.font, this.font.plainSubstrByWidth(itemName.getString(), w - 62), fieldX + 22, y + 5,
                this.draftItem.isEmpty() ? MachineScreen.DIM : MachineScreen.TEXT, false);
        this.hit(fieldX, y, w - 40, 18, () -> this.openPicker(Pick.ROUTE_ITEM, 0));
        y += 22;
        // 范围
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.route.scope"), x, y + 3, MachineScreen.DIM, false);
        int half = (w - 40 - 2) / 2;
        this.button(graphics, fieldX, y, half, BUTTON, Component.translatable("gui.universe.control_panel.route.scope.planets"), !this.draftSystems,
                mouseX, mouseY, () -> this.setDraftSystems(false), true);
        this.button(graphics, fieldX + half + 2, y, half, BUTTON, Component.translatable("gui.universe.control_panel.route.scope.systems"),
                this.draftSystems, mouseX, mouseY, () -> this.setDraftSystems(true), true);
        y += BUTTON + 4;
        // 从、到
        y = this.endField(graphics, "from", this.draftFrom, Pick.ROUTE_FROM, x, fieldX, y, w, mouseX, mouseY);
        y = this.endField(graphics, "to", this.draftTo, Pick.ROUTE_TO, x, fieldX, y, w, mouseX, mouseY);
        boolean ready = !this.draftItem.isEmpty() && !this.draftFrom.isEmpty() && !this.draftTo.isEmpty() && !this.draftFrom.equals(this.draftTo);
        this.button(graphics, x, y, 50, BUTTON, Component.translatable("gui.universe.control_panel.route.create"), false, mouseX, mouseY, () -> {
            ClientPacketDistributor.sendToServer(new ControlPanelPayloads.RouteEdit(true, StationEdit.NONE, this.draftItem, this.draftSystems,
                    this.draftFrom, this.draftTo));
            this.drafting = false;
            this.refresh = REFRESH_TICKS;
            this.click();
        }, ready);
        this.button(graphics, x + 54, y, 50, BUTTON, Component.translatable("gui.universe.control_panel.route.cancel"), false, mouseX, mouseY, () -> {
            this.drafting = false;
            this.click();
        }, true);
        y += BUTTON + 4;
        if (!this.draftFrom.isEmpty() && this.draftFrom.equals(this.draftTo)) {
            y = this.wrapped(graphics, Component.translatable("gui.universe.control_panel.route.same"), x, y, MachineScreen.WARN) + 2;
        }
        return this.wrapped(graphics, Component.translatable("gui.universe.control_panel.route.hint"), x, y, MachineScreen.DIM);
    }

    private void setDraftSystems(boolean systems) {
        if (this.draftSystems != systems) {
            this.draftSystems = systems;
            this.draftFrom = this.currentEnd(systems);
            this.draftTo = "";
            this.click();
        }
    }

    private int endField(GuiGraphicsExtractor graphics, String key, String value, Pick target, int x, int fieldX, int y, int w, int mouseX,
            int mouseY) {
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.route." + key), x, y + 3, MachineScreen.DIM, false);
        Component label = value.isEmpty() ? Component.translatable("gui.universe.control_panel.route.pick")
                : Component.literal(this.font.plainSubstrByWidth(endName(this.draftSystems, value), w - 50));
        this.button(graphics, fieldX, y, w - 40, BUTTON, label, false, mouseX, mouseY, () -> this.openPicker(target, 0), true);
        return y + BUTTON + 4;
    }

    // ---- 选择框

    private void openPicker(Pick mode, int slot) {
        this.pick = mode;
        this.pickSlot = slot;
        this.pickScroll = 0;
        if (this.pickerSearch != null) {
            this.pickerSearch.setValue("");
            this.pickerSearch.visible = true;
            this.setFocused(this.pickerSearch);
            this.pickerSearch.setFocused(true);
        }
        if (mode == Pick.ROUTE_FROM || mode == Pick.ROUTE_TO) {
            this.places = this.places(this.draftSystems);
        }
        this.click();
    }

    private void closePicker() {
        this.pick = null;
        if (this.pickerSearch != null) {
            this.pickerSearch.setFocused(false);
            this.pickerSearch.visible = false;
        }
        this.setFocused(null);
    }

    /** 路线能选的地方：发现过的星系、现在在的星系、有站的星系；星球路线列出这些星系里的天体。 */
    private List<Place> places(boolean systems) {
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (galaxy.isEmpty()) {
            return List.of();
        }
        Set<Integer> known = new LinkedHashSet<>();
        ClientGalaxy.currentSystem().ifPresent(known::add);
        ControlPanelClient.snapshot().ifPresent(snapshot -> snapshot.stations().forEach(row -> known.add(row.system())));
        for (StarSystem system : galaxy.get().systems()) {
            if (ClientDiscoveries.known(DiscoveryKey.system(system.index()))) {
                known.add(system.index());
            }
        }
        List<Place> result = new ArrayList<>();
        for (int index : known) {
            Optional<StarSystem> system = galaxy.get().system(index);
            if (system.isEmpty()) {
                continue;
            }
            String systemName = GalaxyText.system(system.get());
            if (systems) {
                result.add(new Place(Integer.toString(index), systemName));
                continue;
            }
            for (Planet body : system.get().bodies()) {
                result.add(new Place(body.id().path(), GalaxyText.planet(body) + " · " + systemName));
            }
        }
        return result;
    }

    private String pickerQuery() {
        return this.pickerSearch == null ? "" : this.pickerSearch.getValue().strip().toLowerCase(Locale.ROOT);
    }

    /** 能选的物品，按搜索框筛；搜索词没变就用上次的结果。 */
    private List<Item> items() {
        String query = this.pickerQuery();
        if (!query.equals(this.itemQuery)) {
            this.itemQuery = query;
            List<Item> matches = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                if (query.isEmpty() || has(BuiltInRegistries.ITEM.getKey(item).toString(), query)
                        || has(Component.translatable(item.getDescriptionId()).getString(), query)) {
                    matches.add(item);
                }
            }
            this.itemMatches = matches;
        }
        return this.itemMatches;
    }

    private void picker(GuiGraphicsExtractor graphics, Snapshot snapshot, int mouseX, int mouseY, float a) {
        graphics.fill(this.left, this.top, this.left + this.panelWidth, this.top + this.panelHeight, 0xB0000000);
        int height = Math.min(PICKER_HEIGHT, this.panelHeight - 8);
        int x = this.left + (this.panelWidth - PICKER_WIDTH) / 2;
        int y = this.top + (this.panelHeight - height) / 2;
        graphics.fill(x, y, x + PICKER_WIDTH, y + height, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + PICKER_WIDTH - 1, y + height - 1, MachineScreen.PANEL);
        String title = switch (this.pick) {
            case SLOT_ITEM, ROUTE_ITEM -> "item";
            case ROUTE_FROM, ROUTE_TO -> "place";
            case PARTNER -> "partner";
            case null -> "item";
        };
        graphics.text(this.font, Component.translatable("gui.universe.control_panel.picker." + title), x + 8, y + 6, MachineScreen.ACCENT, false);
        Component close = Component.literal("×");
        this.pickerButton(graphics, x + PICKER_WIDTH - 8 - SMALL, y + 4, SMALL, close, mouseX, mouseY, this::closePicker);
        if (this.pickerSearch != null) {
            this.pickerSearch.setX(x + 8);
            this.pickerSearch.setY(y + 20);
            this.pickerSearch.setWidth(PICKER_WIDTH - 16);
            this.pickerSearch.visible = true;
            this.pickerSearch.extractRenderState(graphics, mouseX, mouseY, a);
        }
        int areaTop = y + 40;
        int areaBottom = y + height - 8;
        graphics.fill(x + 8, areaTop, x + PICKER_WIDTH - 8, areaBottom, MachineScreen.INSET);
        if (this.pick == Pick.SLOT_ITEM || this.pick == Pick.ROUTE_ITEM) {
            this.itemGrid(graphics, x + 8, areaTop, areaBottom, mouseX, mouseY);
        } else if (this.pick == Pick.PARTNER) {
            this.partnerList(graphics, snapshot, x + 8, areaTop, areaBottom, mouseX, mouseY);
        } else {
            this.placeList(graphics, x + 8, areaTop, areaBottom, mouseX, mouseY);
        }
    }

    private void pickerButton(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mouseX, int mouseY, Runnable action) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON;
        graphics.fill(x, y, x + width, y + BUTTON, hovered ? 0xFF4FA3C0 : 0xFF2A4A58);
        graphics.fill(x + 1, y + 1, x + width - 1, y + BUTTON - 1, hovered ? 0xFF16303B : MachineScreen.INSET);
        graphics.centeredText(this.font, label, x + width / 2, y + 3, MachineScreen.TEXT);
        this.pickerHits.add(new Hit(x, y, width, BUTTON, action));
    }

    private int pickerRows(int areaTop, int areaBottom, int rowHeight, int size) {
        int visible = Math.max(1, (areaBottom - areaTop - 2) / rowHeight);
        this.pickScroll = Math.max(0, Math.min(this.pickScroll, size - visible));
        return visible;
    }

    private void itemGrid(GuiGraphicsExtractor graphics, int x, int areaTop, int areaBottom, int mouseX, int mouseY) {
        List<Item> items = this.items();
        int rows = (items.size() + PICKER_COLUMNS - 1) / PICKER_COLUMNS;
        int visible = this.pickerRows(areaTop, areaBottom, 18, rows);
        for (int r = 0; r < visible && this.pickScroll + r < rows; r++) {
            for (int c = 0; c < PICKER_COLUMNS; c++) {
                int index = (this.pickScroll + r) * PICKER_COLUMNS + c;
                if (index >= items.size()) {
                    break;
                }
                Item item = items.get(index);
                int ix = x + 1 + c * 18;
                int iy = areaTop + 1 + r * 18;
                ItemStack stack = new ItemStack(item);
                if (mouseX >= ix && mouseX < ix + 18 && mouseY >= iy && mouseY < iy + 18) {
                    graphics.fill(ix, iy, ix + 18, iy + 18, 0x40FFFFFF);
                    this.tooltipStack = stack;
                }
                graphics.fakeItem(stack, ix + 1, iy + 1);
                this.pickerHits.add(new Hit(ix, iy, 18, 18, () -> this.chooseItem(BuiltInRegistries.ITEM.getKey(item).toString())));
            }
        }
        if (items.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.control_panel.empty.filtered"), x + (PICKER_WIDTH - 16) / 2,
                    areaTop + 20, MachineScreen.DIM);
        }
    }

    private void chooseItem(String item) {
        Snapshot snapshot = ControlPanelClient.snapshot().orElse(null);
        if (this.pick == Pick.SLOT_ITEM && this.selectedStation != null) {
            this.sendStation(this.selectedStation, StationEdit.SLOT_ITEM, this.pickSlot, 0, item, StationEdit.NONE);
            // 同一种物品已经在别的格子里时服务端不改，这里提示一声
            StationRow row = snapshot == null ? null : station(snapshot, this.selectedStation);
            if (row != null) {
                for (int i = 0; i < row.slots().size(); i++) {
                    if (i != this.pickSlot && row.slots().get(i).item().equals(item) && this.minecraft.player != null) {
                        this.minecraft.player.sendOverlayMessage(Component.translatable("gui.universe.control_panel.duplicate_item"));
                    }
                }
            }
        } else if (this.pick == Pick.ROUTE_ITEM) {
            this.draftItem = item;
            this.click();
        }
        this.closePicker();
    }

    private void placeList(GuiGraphicsExtractor graphics, int x, int areaTop, int areaBottom, int mouseX, int mouseY) {
        String query = this.pickerQuery();
        List<Place> shown = new ArrayList<>();
        for (Place place : this.places) {
            if (query.isEmpty() || has(place.name, query)) {
                shown.add(place);
            }
        }
        int visible = this.pickerRows(areaTop, areaBottom, PICKER_ROW, shown.size());
        int width = PICKER_WIDTH - 16;
        String chosen = this.pick == Pick.ROUTE_FROM ? this.draftFrom : this.draftTo;
        for (int i = 0; i < visible && this.pickScroll + i < shown.size(); i++) {
            Place place = shown.get(this.pickScroll + i);
            int rowY = areaTop + 1 + i * PICKER_ROW;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + PICKER_ROW;
            if (hovered) {
                graphics.fill(x, rowY, x + width, rowY + PICKER_ROW, 0x30FFFFFF);
            }
            int color = place.id.equals(chosen) ? MachineScreen.ACCENT : MachineScreen.TEXT;
            graphics.text(this.font, this.font.plainSubstrByWidth(place.name, width - 8), x + 4, rowY + 2, color, false);
            this.pickerHits.add(new Hit(x, rowY, width, PICKER_ROW, () -> {
                if (this.pick == Pick.ROUTE_FROM) {
                    this.draftFrom = place.id;
                } else {
                    this.draftTo = place.id;
                }
                this.click();
                this.closePicker();
            }));
        }
        if (shown.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.control_panel.empty.filtered"), x + width / 2, areaTop + 20,
                    MachineScreen.DIM);
        }
    }

    /** 伙伴：别的站一行一个，打了勾的是伙伴，点一下加上或去掉。 */
    private void partnerList(GuiGraphicsExtractor graphics, Snapshot snapshot, int x, int areaTop, int areaBottom, int mouseX, int mouseY) {
        StationRow self = station(snapshot, this.selectedStation);
        if (self == null) {
            this.closePicker();
            return;
        }
        String query = this.pickerQuery();
        List<StationRow> shown = new ArrayList<>();
        for (StationRow row : snapshot.stations()) {
            if (row.id().equals(self.id())) {
                continue;
            }
            String text = ControlPanelClient.stationName(row).getString() + " " + ControlPanelClient.placeName(row.body());
            if (query.isEmpty() || has(text, query)) {
                shown.add(row);
            }
        }
        // 伙伴排前面，再按名字
        Map<UUID, Boolean> partner = new HashMap<>();
        shown.forEach(row -> partner.put(row.id(), partnered(self, row)));
        shown.sort(Comparator.comparing((StationRow row) -> !partner.get(row.id()))
                .thenComparing(row -> ControlPanelClient.stationName(row).getString()));
        int visible = this.pickerRows(areaTop, areaBottom, PICKER_ROW, shown.size());
        int width = PICKER_WIDTH - 16;
        for (int i = 0; i < visible && this.pickScroll + i < shown.size(); i++) {
            StationRow row = shown.get(this.pickScroll + i);
            int rowY = areaTop + 1 + i * PICKER_ROW;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + PICKER_ROW;
            if (hovered) {
                graphics.fill(x, rowY, x + width, rowY + PICKER_ROW, 0x30FFFFFF);
            }
            boolean on = partner.get(row.id());
            // 伙伴两边都算：别的玩家的站加不了，已经是伙伴的能去掉
            boolean locked = !on && !row.editable();
            graphics.fill(x + 3, rowY + 2, x + 11, rowY + 10, on ? MachineScreen.ACCENT : locked ? 0xFF1C2A31 : 0xFF2A4A58);
            graphics.fill(x + 4, rowY + 3, x + 10, rowY + 9, on ? MachineScreen.ACCENT : MachineScreen.INSET);
            String text = ControlPanelClient.stationName(row).getString() + " · " + ControlPanelClient.placeName(row.body());
            graphics.text(this.font, this.font.plainSubstrByWidth(text, width - 18), x + 14, rowY + 2,
                    on ? MachineScreen.TEXT : locked ? 0xFF3A4A52 : MachineScreen.DIM, false);
            if (locked) {
                if (hovered) {
                    this.tooltip = List.of(Component.translatable("gui.universe.control_panel.partner_not_yours"));
                }
                continue;
            }
            this.pickerHits.add(new Hit(x, rowY, width, PICKER_ROW, () -> this.sendStation(self.id(),
                    on ? StationEdit.PARTNER_REMOVE : StationEdit.PARTNER_ADD, 0, 0, "", row.id())));
        }
        int hintY = areaBottom + 0;
        if (shown.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.control_panel.empty.filtered"), x + width / 2, areaTop + 20,
                    MachineScreen.DIM);
        }
        if (self.partners().size() >= Station.MAX_PARTNERS) {
            graphics.text(this.font, Component.translatable("gui.universe.control_panel.partners_full", Station.MAX_PARTNERS), x, hintY - 10,
                    MachineScreen.WARN, false);
        }
    }

    // ---- 按钮

    private boolean hovering(int x, int y, int width, int height, int mouseX, int mouseY) {
        return this.pick == null && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
                && (!this.clipping || mouseY >= this.clipTop && mouseY < this.clipBottom);
    }

    /** 记下一块能点的地方；画详情时只算没滚出去的那部分。 */
    private void hit(int x, int y, int width, int height, Runnable action) {
        int top = this.clipping ? Math.max(y, this.clipTop) : y;
        int bottom = this.clipping ? Math.min(y + height, this.clipBottom) : y + height;
        if (bottom > top) {
            this.hits.add(new Hit(x, top, width, bottom - top, action));
        }
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Component label, boolean selected, int mouseX, int mouseY,
            Runnable action, boolean active) {
        boolean hovered = active && this.hovering(x, y, width, height, mouseX, mouseY);
        this.lockedHover |= !active && this.readOnly && this.hovering(x, y, width, height, mouseX, mouseY);
        int border = selected ? MachineScreen.ACCENT : hovered ? 0xFF4FA3C0 : active ? 0xFF2A4A58 : 0xFF1C2A31;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, selected ? 0xFF1C4352 : hovered ? 0xFF16303B : MachineScreen.INSET);
        int color = !active ? 0xFF3A4A52 : selected || hovered ? MachineScreen.TEXT : MachineScreen.DIM;
        graphics.centeredText(this.font, Component.literal(this.font.plainSubstrByWidth(label.getString(), width - 4)), x + width / 2,
                y + (height - 8) / 2, color);
        if (active) {
            this.hit(x, y, width, height, action);
        }
    }

    // ---- 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.shift = event.hasShiftDown();
        double mx = event.x();
        double my = event.y();
        if (this.pick != null) {
            if (this.pickerSearch != null && this.pickerSearch.isMouseOver(mx, my)) {
                return super.mouseClicked(event, doubleClick);
            }
            for (Hit hit : List.copyOf(this.pickerHits)) {
                if (hit.contains(mx, my)) {
                    hit.action.run();
                    return true;
                }
            }
            int height = Math.min(PICKER_HEIGHT, this.panelHeight - 8);
            int x = this.left + (this.panelWidth - PICKER_WIDTH) / 2;
            int y = this.top + (this.panelHeight - height) / 2;
            if (mx < x || mx >= x + PICKER_WIDTH || my < y || my >= y + height) {
                this.closePicker();
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (Hit hit : List.copyOf(this.hits)) {
                if (hit.contains(mx, my)) {
                    hit.action.run();
                    return true;
                }
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!handled) {
            this.setFocused(null);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY);
        if (this.pick != null) {
            this.pickScroll = Math.max(0, this.pickScroll - step);
            return true;
        }
        if (x >= this.detailX() - 3) {
            this.detailScroll = Math.max(0, this.detailScroll - step * 12);
        } else {
            this.listScroll = Math.max(0, this.listScroll - step);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.pick != null && event.key() == InputConstants.KEY_ESCAPE) {
            this.closePicker();
            return true;
        }
        if (this.rename != null && this.rename.isFocused()
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            this.commitRename();
            return true;
        }
        boolean typing = this.getFocused() instanceof EditBox box && box.isFocused();
        if (!typing && UniverseKeys.CONTROL_PANEL.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
