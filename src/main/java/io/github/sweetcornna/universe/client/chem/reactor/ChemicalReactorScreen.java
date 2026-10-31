package io.github.sweetcornna.universe.client.chem.reactor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.chem.reactor.ChemicalReactorBlockEntity;
import io.github.sweetcornna.universe.chem.reactor.ReactionBooks;
import io.github.sweetcornna.universe.chem.reactor.ReactorClimate;
import io.github.sweetcornna.universe.chem.reactor.ReactorKnowledge;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorModel;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorResult;
import io.github.sweetcornna.universe.core.chem.reaction.ReactorSettings;
import io.github.sweetcornna.universe.core.chem.reaction.YieldGrid;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipe;
import io.github.sweetcornna.universe.factory.recipe.MachineRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 化学反应釜的界面（ADR-0036 §四、ADR-0037）。左边一栏能滚动：第一行"自由投料"，下面是全服发现了的反应，按类排，左边一道颜色是类。
 * 中间原料、催化剂、产物，右边"温度 × 压强"的产量图：全服在哪一格做成过一批，那一格按放了催化剂时的主产物产量上色，没做过的是灰的，
 * 白框是现在的工况。下面两条滑块调温度和压强（拖动、点产量图的格子、滚轮微调，按住 Shift 调得更细），再下面三行读数按界面上的工况现算。
 *
 * <p>自由投料时，正在做的反应是釜按原料认出来的（界面数据 {@link ChemicalReactorBlockEntity#ACTIVE}）；认不出来时读数那三行换成提示。
 * 标题那一行的右边写正在做的反应的反应式，放不下时不写标题。
 */
public class ChemicalReactorScreen extends MachineScreen {
    private static final int LIST_X = 6;
    private static final int LIST_Y = 20;
    private static final int ROW_WIDTH = 86;
    private static final int ROW_STEP = 13;
    private static final int VISIBLE_ROWS = 5;
    private static final int DEFAULTS_Y = LIST_Y + VISIBLE_ROWS * ROW_STEP + 1;
    private static final int SCROLL_X = LIST_X + ROW_WIDTH + 1;
    private static final int CATALYST_TEXT_X = 120;
    private static final int CATALYST_Y = 42;
    private static final int PROGRESS_X = 100;
    private static final int PROGRESS_Y = 85;
    private static final int PROGRESS_WIDTH = 52;
    private static final int STATUS_X = 126;
    private static final int STATUS_Y = 90;
    private static final int MAP_X = 166;
    private static final int MAP_Y = 20;
    /** 产量图一格：温度 14 列，宽 6；压强 8 行，高 8。 */
    private static final int CELL_W = 6;
    private static final int CELL_H = 8;
    private static final int TRACK_X = 96;
    private static final int TRACK_WIDTH = 152;
    private static final int TEMPERATURE_Y = 99;
    private static final int PRESSURE_Y = 112;
    private static final int READOUT_Y = 125;
    private static final int LINE = 11;
    /** 发了新工况以后，等服务端确认的刻数；过了还没变就按服务端的来。 */
    private static final int PENDING_TICKS = 20;
    private static final double LN_PRESSURE_SPAN = Math.log(ReactorSettings.MAX_PRESSURE / ReactorSettings.MIN_PRESSURE);
    /** 产量图的颜色，从低到高（viridis 的五个点）。 */
    private static final int[] HEAT = {0xFF440154, 0xFF3B528B, 0xFF21918C, 0xFF5EC962, 0xFFFDE725};
    private static final int UNMEASURED = 0xFF283238;
    /** 列表里每类左边那道颜色：氮、硫、碳和合成气、有机、氯。 */
    private static final int[] CATEGORY_COLORS = {0xFF5B8DEF, 0xFFE8C14A, 0xFF9AA5AD, 0xFF5BD47A, 0xFFB8E05A};

    private enum Drag {
        NONE,
        TEMPERATURE,
        PRESSURE
    }

    private Drag drag = Drag.NONE;
    /** 正在拖或刚发出去、服务端还没确认的值；NaN 表示没有。 */
    private double localTemperature = Double.NaN;
    private double localPressure = Double.NaN;
    private int pendingTicks;
    /** 列表滚到第几行。 */
    private int scroll;
    private boolean scrolledToSelection;
    private @Nullable Reaction scrolledFor;
    /** 产量图每格放了催化剂时的产量（占上限的比例，和气温无关），按反应缓存。 */
    private @Nullable Reaction ratesFor;
    private final double[] rates = new double[YieldGrid.CELLS];

    public ChemicalReactorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    // ---- 读界面数据

    /** 选定的反应；自由投料为 {@code null}。 */
    private @Nullable Reaction reaction() {
        return byData(this.menu.data(ChemicalReactorBlockEntity.REACTION));
    }

    /** 正在做的反应：选定的，或者自由投料认出来的。 */
    private @Nullable Reaction active() {
        return byData(this.menu.data(ChemicalReactorBlockEntity.ACTIVE));
    }

    private static @Nullable Reaction byData(int value) {
        int index = value - 1;
        Reaction[] all = Reaction.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    private double serverTemperature() {
        return this.menu.data(ChemicalReactorBlockEntity.TEMPERATURE);
    }

    private double serverPressure() {
        return (this.menu.data(ChemicalReactorBlockEntity.PRESSURE) & 0xFFFF) / (double) ChemicalReactorBlockEntity.PRESSURE_SCALE;
    }

    private ReactorSettings settings() {
        return new ReactorSettings(Double.isNaN(this.localTemperature) ? this.serverTemperature() : this.localTemperature,
                Double.isNaN(this.localPressure) ? this.serverPressure() : this.localPressure);
    }

    private double ambient() {
        return this.menu.data(ChemicalReactorBlockEntity.AMBIENT);
    }

    /** 正在做的反应放了对的催化剂，或者它不用催化剂。 */
    private boolean catalyzed(Reaction active) {
        return active.catalyst() == null || this.menu.data(ChemicalReactorBlockEntity.CATALYST) == ChemicalReactorBlockEntity.CATALYST_RIGHT;
    }

    private boolean measured(int cell) {
        int word = this.menu.data(ChemicalReactorBlockEntity.MEASURED + cell / ReactorKnowledge.WORD_BITS) & 0xFFFF;
        return (word >> cell % ReactorKnowledge.WORD_BITS & 1) != 0;
    }

    /** 客户端收到的这条反应的配方；数据包里没有时为 {@code null}。 */
    private static @Nullable MachineRecipe recipe(Reaction reaction) {
        for (RecipeHolder<MachineRecipe> holder : MachineRecipes.clientList(MachineRecipe.Kind.REACTOR)) {
            if (holder.id().equals(ReactorRecipes.key(reaction))) {
                return holder.value();
            }
        }
        return null;
    }

    private static boolean discovered(Reaction reaction) {
        return ReactionBooks.client().discovered(reaction);
    }

    /** 左边一栏：第一行自由投料（{@code null}），下面是发现了的反应按类排；选着但还没发现的（蓝图带来的）也列出来。 */
    private List<@Nullable Reaction> entries() {
        Reaction selected = this.reaction();
        List<@Nullable Reaction> list = new ArrayList<>();
        list.add(null);
        List<Reaction> known = new ArrayList<>();
        for (Reaction reaction : Reaction.values()) {
            if (discovered(reaction) || reaction == selected) {
                known.add(reaction);
            }
        }
        known.sort(Comparator.comparingInt((Reaction reaction) -> reaction.category().ordinal()).thenComparingInt(Enum::ordinal));
        list.addAll(known);
        return list;
    }

    private int maxScroll(List<@Nullable Reaction> entries) {
        return Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    private double[] rates(Reaction reaction) {
        if (this.ratesFor != reaction) {
            this.ratesFor = reaction;
            double max = reaction.baseBatches() * reaction.mainProduct().count();
            for (int i = 0; i < YieldGrid.CELLS; i++) {
                ReactorResult result = ReactorModel.evaluate(reaction, YieldGrid.center(YieldGrid.byIndex(i)), true, ReactorClimate.DEFAULT_AMBIENT);
                this.rates[i] = result.productPerMinute() / max;
            }
        }
        return this.rates;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        Reaction selected = this.reaction();
        if (!this.scrolledToSelection || selected != this.scrolledFor) {
            // 让选着的那行露出来：打开时界面数据要等一刻才到，换了反应也要跟上
            List<@Nullable Reaction> entries = this.entries();
            int index = Math.max(0, entries.indexOf(selected));
            if (index < this.scroll) {
                this.scroll = index;
            } else if (index >= this.scroll + VISIBLE_ROWS) {
                this.scroll = index - VISIBLE_ROWS + 1;
            }
            this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll(entries)));
            this.scrolledFor = selected;
            this.scrolledToSelection = true;
        }
        if (this.drag != Drag.NONE || this.pendingTicks <= 0) {
            return;
        }
        this.pendingTicks--;
        boolean temperatureDone = Double.isNaN(this.localTemperature) || Math.round(this.localTemperature) == Math.round(this.serverTemperature());
        boolean pressureDone = Double.isNaN(this.localPressure)
                || Math.round(this.localPressure * ChemicalReactorBlockEntity.PRESSURE_SCALE) == this.menu.data(ChemicalReactorBlockEntity.PRESSURE);
        if (this.pendingTicks == 0 || temperatureDone && pressureDone) {
            this.localTemperature = Double.NaN;
            this.localPressure = Double.NaN;
            this.pendingTicks = 0;
        }
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        Reaction selected = this.reaction();
        Reaction active = this.active();
        // 空着的原料格和催化剂格画出该放的东西（半透明）：选定配方时每格是哪种原料；自由投料认出反应后只画催化剂
        List<int[]> ghosts = new ArrayList<>();
        if (selected != null) {
            List<ItemCount> inputs = selected.inputs();
            for (int i = 0; i < ChemicalReactorBlockEntity.INPUTS; i++) {
                this.ghost(graphics, ghosts, i, i < inputs.size() ? inputs.get(i) : null);
            }
        }
        if (active != null && active.catalyst() != null) {
            this.ghost(graphics, ghosts, ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemCount(active.catalyst().item(), 1));
        }
        if (ghosts.isEmpty()) {
            return;
        }
        graphics.nextStratum();
        for (int[] ghost : ghosts) {
            graphics.fill(ghost[0], ghost[1], ghost[0] + 16, ghost[1] + 16, 0x900A1015);
        }
    }

    private void ghost(GuiGraphicsExtractor graphics, List<int[]> ghosts, int slotIndex, @Nullable ItemCount item) {
        var slot = this.menu.getSlot(slotIndex);
        if (!slot.getItem().isEmpty()) {
            return;
        }
        int x = this.leftPos + slot.x;
        int y = this.topPos + slot.y;
        if (item != null) {
            ItemStack stack = new ItemStack(ReactorRecipes.item(item), item.count());
            graphics.fakeItem(stack, x, y);
            graphics.itemDecorations(this.font, stack, x, y);
        }
        ghosts.add(new int[] {x, y});
    }

    /** 标题那一行：右边写反应式；反应式太长时让出标题的位置。 */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Reaction active = this.active();
        int right = this.imageWidth - 8;
        if (active != null) {
            String equation = active.equation();
            int x = Math.max(this.titleLabelX, right - this.font.width(equation));
            graphics.text(this.font, equation, x, this.titleLabelY, DIM, false);
            right = x - 6;
        }
        if (this.titleLabelX + this.font.width(this.title) <= right) {
            graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
        }
        if (this.menu.type().showInventory()) {
            graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
        }
        this.extractInfo(graphics, mouseX - this.leftPos, mouseY - this.topPos);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Reaction selected = this.reaction();
        Reaction active = this.active();
        this.list(graphics, selected, mouseX, mouseY);
        this.textButton(graphics, LIST_X, DEFAULTS_Y, ROW_WIDTH, Component.translatable("gui.universe.chemical_reactor.defaults"),
                active != null ? TEXT : DIM, mouseX, mouseY);
        this.catalystLine(graphics, active);
        bar(graphics, PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, 2, this.menu.data(ChemicalReactorBlockEntity.PROGRESS) / 1000F, ACCENT);
        this.status(graphics);
        ReactorSettings settings = this.settings();
        this.yieldMap(graphics, active, settings, mouseX, mouseY);
        this.slider(graphics, TEMPERATURE_Y, Component.translatable("gui.universe.chemical_reactor.temperature", ReactorRecipes.celsius(settings.temperature())),
                temperatureShare(settings.temperature()), active == null ? Double.NaN : temperatureShare(active.defaults().temperature()), mouseX, mouseY);
        this.slider(graphics, PRESSURE_Y, Component.translatable("gui.universe.chemical_reactor.pressure", ReactorRecipes.bar(settings.pressure())),
                pressureShare(settings.pressure()), active == null ? Double.NaN : pressureShare(active.defaults().pressure()), mouseX, mouseY);
        if (active != null) {
            this.readouts(graphics, active, settings);
        } else {
            this.freeHints(graphics);
        }
    }

    private void list(GuiGraphicsExtractor graphics, @Nullable Reaction selected, int mouseX, int mouseY) {
        List<@Nullable Reaction> entries = this.entries();
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll(entries)));
        for (int row = 0; row < VISIBLE_ROWS && this.scroll + row < entries.size(); row++) {
            Reaction reaction = entries.get(this.scroll + row);
            this.listRow(graphics, LIST_Y + row * ROW_STEP, reaction, reaction == selected, mouseX, mouseY);
        }
        if (entries.size() > VISIBLE_ROWS) {
            // 滚动条：一栏右边的一道细线
            int trackHeight = VISIBLE_ROWS * ROW_STEP - 1;
            int thumb = Math.max(6, trackHeight * VISIBLE_ROWS / entries.size());
            int thumbTop = LIST_Y + (trackHeight - thumb) * this.scroll / this.maxScroll(entries);
            graphics.fill(SCROLL_X, LIST_Y, SCROLL_X + 1, LIST_Y + trackHeight, 0xFF24404D);
            graphics.fill(SCROLL_X, thumbTop, SCROLL_X + 1, thumbTop + thumb, ACCENT);
        }
    }

    /** 一行：自由投料（{@code reaction} 为空），或者一条反应；左边一道颜色是类。没发现的（蓝图带来的）红字。 */
    private void listRow(GuiGraphicsExtractor graphics, int y, @Nullable Reaction reaction, boolean current, int mouseX, int mouseY) {
        boolean locked = reaction != null && !discovered(reaction);
        boolean hovered = !locked && overMode(mouseX, mouseY, LIST_X, y, ROW_WIDTH);
        graphics.fill(LIST_X, y, LIST_X + ROW_WIDTH, y + MODE_HEIGHT, current ? ACCENT : BORDER);
        graphics.fill(LIST_X + 1, y + 1, LIST_X + ROW_WIDTH - 1, y + MODE_HEIGHT - 1, current ? 0xFF1F4A5A : hovered ? 0xFF1A3D4A : 0xFF12303B);
        Component label;
        if (reaction == null) {
            label = Component.translatable("gui.universe.chemical_reactor.free");
        } else {
            graphics.fill(LIST_X + 1, y + 1, LIST_X + 3, y + MODE_HEIGHT - 1, CATEGORY_COLORS[reaction.category().ordinal() % CATEGORY_COLORS.length]);
            label = Component.translatable("gui.universe.chemical_reactor.short." + reaction.id());
        }
        graphics.centeredText(this.font, label, LIST_X + ROW_WIDTH / 2 + 1, y + 2, locked ? BAD : current ? ACCENT : TEXT);
    }

    private void catalystLine(GuiGraphicsExtractor graphics, @Nullable Reaction active) {
        int state = this.menu.data(ChemicalReactorBlockEntity.CATALYST);
        Component text;
        int color;
        switch (state) {
            case ChemicalReactorBlockEntity.CATALYST_RIGHT -> {
                int left = this.menu.data(ChemicalReactorBlockEntity.WEAR);
                text = Component.translatable("gui.universe.chemical_reactor.catalyst_left", left);
                color = left <= 32 ? WARN : GOOD;
            }
            case ChemicalReactorBlockEntity.CATALYST_WRONG -> {
                text = Component.translatable("gui.universe.chemical_reactor.catalyst_wrong");
                color = BAD;
            }
            case ChemicalReactorBlockEntity.CATALYST_UNNEEDED -> {
                text = Component.translatable("gui.universe.chemical_reactor.catalyst_unneeded");
                color = DIM;
            }
            case ChemicalReactorBlockEntity.CATALYST_IDLE -> {
                text = Component.translatable("gui.universe.chemical_reactor.catalyst_idle");
                color = DIM;
            }
            default -> {
                boolean needed = active != null && active.catalyst() != null;
                text = Component.translatable(active != null && !needed ? "gui.universe.chemical_reactor.catalyst_none"
                        : "gui.universe.chemical_reactor.catalyst_empty");
                color = needed ? WARN : DIM;
            }
        }
        graphics.text(this.font, text, CATALYST_TEXT_X, CATALYST_Y + 4, color, false);
    }

    private void status(GuiGraphicsExtractor graphics) {
        int status = this.menu.data(ChemicalReactorBlockEntity.STATUS);
        Component text = switch (status) {
            case ChemicalReactorBlockEntity.RUNNING -> Component.translatable("gui.universe.chemical_reactor.status.running");
            case ChemicalReactorBlockEntity.NO_REACTION -> Component.translatable("gui.universe.chemical_reactor.status.no_recipe");
            case ChemicalReactorBlockEntity.NO_INPUT -> Component.translatable("gui.universe.chemical_reactor.status.no_input");
            case ChemicalReactorBlockEntity.OUTPUT_FULL -> Component.translatable("gui.universe.chemical_reactor.status.output_full");
            case ChemicalReactorBlockEntity.NO_POWER -> Component.translatable("gui.universe.chemical_reactor.status.no_power");
            case ChemicalReactorBlockEntity.LOCKED -> Component.translatable("gui.universe.chemical_reactor.status.locked");
            case ChemicalReactorBlockEntity.UNKNOWN -> Component.translatable("gui.universe.chemical_reactor.status.unknown");
            case ChemicalReactorBlockEntity.AMBIGUOUS -> Component.translatable("gui.universe.chemical_reactor.status.ambiguous",
                    this.menu.data(ChemicalReactorBlockEntity.CANDIDATES));
            default -> Component.translatable("gui.universe.chemical_reactor.status.empty");
        };
        int color = switch (status) {
            case ChemicalReactorBlockEntity.RUNNING -> GOOD;
            case ChemicalReactorBlockEntity.NO_INPUT, ChemicalReactorBlockEntity.OUTPUT_FULL, ChemicalReactorBlockEntity.UNKNOWN,
                    ChemicalReactorBlockEntity.AMBIGUOUS -> WARN;
            case ChemicalReactorBlockEntity.NO_POWER, ChemicalReactorBlockEntity.LOCKED, ChemicalReactorBlockEntity.NO_REACTION -> BAD;
            default -> DIM;
        };
        graphics.centeredText(this.font, text, STATUS_X, STATUS_Y, color);
    }

    private void yieldMap(GuiGraphicsExtractor graphics, @Nullable Reaction reaction, ReactorSettings settings, int mouseX, int mouseY) {
        int width = YieldGrid.TEMPERATURE_CELLS * CELL_W;
        int height = YieldGrid.PRESSURE_CELLS * CELL_H;
        graphics.fill(MAP_X - 1, MAP_Y - 1, MAP_X + width + 1, MAP_Y + height + 1, 0xFF24404D);
        graphics.fill(MAP_X, MAP_Y, MAP_X + width, MAP_Y + height, INSET);
        double[] rates = reaction == null ? null : this.rates(reaction);
        for (int i = 0; i < YieldGrid.CELLS; i++) {
            int[] origin = cellOrigin(YieldGrid.byIndex(i));
            int color = rates != null && this.measured(i) ? heat(rates[i]) : UNMEASURED;
            graphics.fill(origin[0], origin[1], origin[0] + CELL_W - 1, origin[1] + CELL_H - 1, color);
        }
        int hovered = cellAt(mouseX, mouseY);
        if (hovered >= 0) {
            int[] origin = cellOrigin(YieldGrid.byIndex(hovered));
            frame(graphics, origin[0] - 1, origin[1] - 1, CELL_W + 1, CELL_H + 1, 0x80FFFFFF);
        }
        int[] current = cellOrigin(YieldGrid.cell(settings));
        frame(graphics, current[0] - 1, current[1] - 1, CELL_W + 1, CELL_H + 1, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("gui.universe.chemical_reactor.axes"), MAP_X + width / 2, MAP_Y + height + 4, DIM);
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /** 一格的左上角（相对界面）：温度往右升，压强往上升。 */
    private static int[] cellOrigin(YieldGrid.Cell cell) {
        return new int[] {MAP_X + cell.temperature() * CELL_W, MAP_Y + (YieldGrid.PRESSURE_CELLS - 1 - cell.pressure()) * CELL_H};
    }

    /** 鼠标下的格子编号，不在图上为 -1。坐标相对界面。 */
    private static int cellAt(double x, double y) {
        int column = (int) Math.floor((x - MAP_X) / CELL_W);
        int row = (int) Math.floor((y - MAP_Y) / CELL_H);
        if (x < MAP_X || y < MAP_Y || column >= YieldGrid.TEMPERATURE_CELLS || row >= YieldGrid.PRESSURE_CELLS) {
            return -1;
        }
        return new YieldGrid.Cell(column, YieldGrid.PRESSURE_CELLS - 1 - row).index();
    }

    /** 产量比例（0–1）换成颜色，五个点之间线性插值。 */
    static int heat(double share) {
        double t = Math.max(0, Math.min(1, share)) * (HEAT.length - 1);
        int i = Math.min(HEAT.length - 2, (int) t);
        double f = t - i;
        int a = HEAT[i];
        int b = HEAT[i + 1];
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = (int) Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** @param textbook 教科书工况在轨道上的位置；NaN 不画（自由投料还没认出反应） */
    private void slider(GuiGraphicsExtractor graphics, int y, Component label, double share, double textbook, int mouseX, int mouseY) {
        graphics.text(this.font, label, 8, y, TEXT, false);
        int top = y + 1;
        graphics.fill(TRACK_X - 1, top - 1, TRACK_X + TRACK_WIDTH + 1, top + 6, 0xFF24404D);
        graphics.fill(TRACK_X, top, TRACK_X + TRACK_WIDTH, top + 5, INSET);
        int knob = TRACK_X + (int) Math.round(share * (TRACK_WIDTH - 1));
        graphics.fill(TRACK_X, top + 1, knob, top + 4, 0xFF2E7A92);
        if (!Double.isNaN(textbook)) {
            // 教科书的工况在轨道上点一个小记号
            int mark = TRACK_X + (int) Math.round(textbook * (TRACK_WIDTH - 1));
            graphics.fill(mark, top - 2, mark + 1, top, WARN);
        }
        boolean hovered = overTrack(mouseX, mouseY, y);
        graphics.fill(knob - 1, top - 2, knob + 2, top + 7, hovered || this.drag != Drag.NONE ? ACCENT : TEXT);
    }

    private void readouts(GuiGraphicsExtractor graphics, Reaction reaction, ReactorSettings settings) {
        ReactorResult result = ReactorModel.evaluate(reaction, settings, this.catalyzed(reaction), this.ambient());
        graphics.text(this.font, Component.translatable("gui.universe.chemical_reactor.conversion", percent1(result.equilibrium()),
                percent1(result.singlePass())), 8, READOUT_Y, TEXT, false);
        graphics.text(this.font, this.products(reaction, result), 8, READOUT_Y + LINE, TEXT, false);
        int y = READOUT_Y + 2 * LINE;
        if (this.menu.data(0) < 0) {
            graphics.text(this.font, Component.translatable("gui.universe.machine.unpowered"), 8, y, BAD, false);
        } else if ((this.menu.data(3) & 4) != 0) {
            this.powerLine(graphics, 8, y);
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.chemical_reactor.power_need", watts(Math.round(result.power() * 10))),
                    8, y, DIM, false);
        }
    }

    /** 自由投料还没认出反应：读数那三行换成提示。 */
    private void freeHints(GuiGraphicsExtractor graphics) {
        int status = this.menu.data(ChemicalReactorBlockEntity.STATUS);
        Component first = switch (status) {
            case ChemicalReactorBlockEntity.UNKNOWN -> Component.translatable("gui.universe.chemical_reactor.free.unknown");
            case ChemicalReactorBlockEntity.AMBIGUOUS -> Component.translatable("gui.universe.chemical_reactor.free.ambiguous",
                    this.menu.data(ChemicalReactorBlockEntity.CANDIDATES));
            default -> Component.translatable("gui.universe.chemical_reactor.free.empty");
        };
        graphics.text(this.font, first, 8, READOUT_Y, status == ChemicalReactorBlockEntity.UNKNOWN || status == ChemicalReactorBlockEntity.AMBIGUOUS
                ? WARN : TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.chemical_reactor.free.lookup"), 8, READOUT_Y + LINE, DIM, false);
        if (this.menu.data(0) < 0) {
            graphics.text(this.font, Component.translatable("gui.universe.machine.unpowered"), 8, READOUT_Y + 2 * LINE, BAD, false);
        }
    }

    /** "每分钟 23.0 个氨"；几样产物用顿号隔开。 */
    private Component products(Reaction reaction, ReactorResult result) {
        MutableComponent list = Component.empty();
        List<ItemCount> outputs = reaction.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            double rate = result.batchesPerMinute() * outputs.get(i).count();
            if (i > 0) {
                list.append(Component.translatable("gui.universe.chemical_reactor.separator"));
            }
            list.append(this.amount(rate, outputs.get(i)));
        }
        return Component.translatable("gui.universe.chemical_reactor.rate", list);
    }

    private Component amount(double rate, ItemCount item) {
        return Component.translatable("gui.universe.chemical_reactor.amount", String.format(Locale.ROOT, "%.1f", rate),
                new ItemStack(ReactorRecipes.item(item)).getHoverName());
    }

    private static String percent1(double share) {
        return String.format(Locale.ROOT, "%.1f%%", share * 100);
    }

    private static double temperatureShare(double temperature) {
        return (temperature - ReactorSettings.MIN_TEMPERATURE) / (ReactorSettings.MAX_TEMPERATURE - ReactorSettings.MIN_TEMPERATURE);
    }

    private static double pressureShare(double pressure) {
        return Math.log(pressure / ReactorSettings.MIN_PRESSURE) / LN_PRESSURE_SPAN;
    }

    private static boolean overTrack(double x, double y, int rowY) {
        return x >= TRACK_X - 2 && x < TRACK_X + TRACK_WIDTH + 2 && y >= rowY - 2 && y < rowY + 9;
    }

    private static boolean overList(double x, double y) {
        return x >= LIST_X && x < SCROLL_X + 2 && y >= LIST_Y && y < LIST_Y + VISIBLE_ROWS * ROW_STEP;
    }

    /** 鼠标下的那一行在 {@link #entries()} 里的序号，不在列表上为 -1。 */
    private int rowAt(double x, double y, List<@Nullable Reaction> entries) {
        for (int row = 0; row < VISIBLE_ROWS && this.scroll + row < entries.size(); row++) {
            if (overMode(x, y, LIST_X, LIST_Y + row * ROW_STEP, ROW_WIDTH)) {
                return this.scroll + row;
            }
        }
        return -1;
    }

    // ---- 提示

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!this.menu.getCarried().isEmpty() || this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        List<Component> lines = this.tooltip(x, y);
        if (!lines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(double x, double y) {
        List<Component> lines = new ArrayList<>();
        List<@Nullable Reaction> entries = this.entries();
        int row = this.rowAt(x, y, entries);
        if (row >= 0) {
            Reaction reaction = entries.get(row);
            if (reaction == null) {
                lines.add(Component.translatable("gui.universe.chemical_reactor.free").withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("gui.universe.chemical_reactor.free.tip").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.universe.chemical_reactor.free.tip2").withStyle(ChatFormatting.GRAY));
                return lines;
            }
            lines.add(Component.translatable("reaction.universe." + reaction.id()).withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("reaction_category.universe." + reaction.category().id()).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal(reaction.equation()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.universe.chemical_reactor.needs_catalyst", catalystName(reaction.catalyst()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.universe.chemical_reactor.textbook", ReactorRecipes.conditions(reaction))
                    .withStyle(ChatFormatting.GRAY));
            if (recipe(reaction) == null) {
                lines.add(Component.translatable("gui.universe.chemical_reactor.unavailable").withStyle(ChatFormatting.RED));
            } else if (!discovered(reaction)) {
                lines.add(Component.translatable("gui.universe.chemical_reactor.undiscovered").withStyle(ChatFormatting.RED));
            }
            return lines;
        }
        Reaction active = this.active();
        if (overMode(x, y, LIST_X, DEFAULTS_Y, ROW_WIDTH) && active == null) {
            lines.add(Component.translatable("gui.universe.chemical_reactor.defaults_idle").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        int cell = cellAt(x, y);
        if (cell >= 0) {
            YieldGrid.Cell at = YieldGrid.byIndex(cell);
            ReactorSettings low = new ReactorSettings(ReactorSettings.MIN_TEMPERATURE + at.temperature() * cellSpanT(),
                    ReactorSettings.MIN_PRESSURE * Math.exp(at.pressure() * LN_PRESSURE_SPAN / YieldGrid.PRESSURE_CELLS));
            ReactorSettings high = new ReactorSettings(low.temperature() + cellSpanT(),
                    ReactorSettings.MIN_PRESSURE * Math.exp((at.pressure() + 1) * LN_PRESSURE_SPAN / YieldGrid.PRESSURE_CELLS));
            lines.add(Component.translatable("gui.universe.chemical_reactor.cell", ReactorRecipes.celsius(low.temperature()),
                    ReactorRecipes.celsius(high.temperature()), ReactorRecipes.bar(low.pressure()), ReactorRecipes.bar(high.pressure())));
            if (active != null && this.measured(cell)) {
                ReactorResult result = ReactorModel.evaluate(active, YieldGrid.center(at), true, this.ambient());
                lines.add(this.products(active, result).copy().withStyle(ChatFormatting.GRAY));
            } else if (active != null) {
                lines.add(Component.translatable("gui.universe.chemical_reactor.unmeasured").withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable("gui.universe.chemical_reactor.cell_hint").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        if (x >= 99 && x < 160 && y >= CATALYST_Y - 1 && y < CATALYST_Y + 17) {
            if (active == null) {
                lines.add(Component.translatable("gui.universe.chemical_reactor.catalyst_pick").withStyle(ChatFormatting.GRAY));
                return lines;
            }
            lines.add(Component.translatable("gui.universe.chemical_reactor.needs_catalyst", catalystName(active.catalyst())));
            if (active.catalyst() != null) {
                lines.add(Component.translatable("gui.universe.chemical_reactor.catalyst_life", active.catalyst().batches())
                        .withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (overTrack(x, y, TEMPERATURE_Y) || overTrack(x, y, PRESSURE_Y)) {
            lines.add(Component.translatable("gui.universe.chemical_reactor.slider_hint").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static double cellSpanT() {
        return (ReactorSettings.MAX_TEMPERATURE - ReactorSettings.MIN_TEMPERATURE) / YieldGrid.TEMPERATURE_CELLS;
    }

    /** 催化剂的名字；不用催化剂的写"不用"。 */
    private static Component catalystName(@Nullable Catalyst catalyst) {
        return catalyst == null ? Component.translatable("gui.universe.chemical_reactor.no_catalyst")
                : new ItemStack(ReactorRecipes.item(catalyst.item())).getHoverName();
    }

    // ---- 操作

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 26.x 的输入是 SDL：左键是 1，不是 GLFW 的 0
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double x = event.x() - this.leftPos;
        double y = event.y() - this.topPos;
        List<@Nullable Reaction> entries = this.entries();
        int row = this.rowAt(x, y, entries);
        if (row >= 0) {
            Reaction picked = entries.get(row);
            Reaction selected = this.reaction();
            if (picked == null && selected != null) {
                this.send(ChemicalReactorBlockEntity.FREE_BUTTON, true);
                this.clearLocal();
            } else if (picked != null && picked != selected && discovered(picked)) {
                this.send(picked.ordinal(), true);
                this.clearLocal();
            }
            return true;
        }
        if (overMode(x, y, LIST_X, DEFAULTS_Y, ROW_WIDTH)) {
            if (this.active() != null) {
                this.send(ChemicalReactorBlockEntity.DEFAULTS_BUTTON, true);
                this.clearLocal();
            }
            return true;
        }
        int cell = cellAt(x, y);
        if (cell >= 0) {
            ReactorSettings center = YieldGrid.center(YieldGrid.byIndex(cell));
            this.localTemperature = Math.round(center.temperature());
            this.localPressure = roundPressure(center.pressure());
            this.commit(true, true);
            return true;
        }
        if (overTrack(x, y, TEMPERATURE_Y)) {
            this.drag = Drag.TEMPERATURE;
            this.dragTo(x);
            return true;
        }
        if (overTrack(x, y, PRESSURE_Y)) {
            this.drag = Drag.PRESSURE;
            this.dragTo(x);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.drag != Drag.NONE) {
            this.dragTo(event.x() - this.leftPos);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.drag != Drag.NONE) {
            Drag released = this.drag;
            this.drag = Drag.NONE;
            this.commit(released == Drag.TEMPERATURE, released == Drag.PRESSURE);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        if (scrollY == 0 || this.minecraft == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (overList(x, y)) {
            List<@Nullable Reaction> entries = this.entries();
            this.scroll = Math.max(0, Math.min(this.scroll + (scrollY < 0 ? 1 : -1), this.maxScroll(entries)));
            return true;
        }
        boolean fine = this.minecraft.hasShiftDown();
        ReactorSettings settings = this.settings();
        if (overTrack(x, y, TEMPERATURE_Y)) {
            this.localTemperature = Math.round(settings.withTemperature(settings.temperature() + Math.signum(scrollY) * (fine ? 1 : 5)).temperature());
            this.commit(true, false);
            return true;
        }
        if (overTrack(x, y, PRESSURE_Y)) {
            double factor = Math.pow(fine ? 1.01 : 1.05, Math.signum(scrollY));
            double next = roundPressure(settings.withPressure(settings.pressure() * factor).pressure());
            if (next == roundPressure(settings.pressure())) {
                next = roundPressure(settings.withPressure(settings.pressure() + Math.signum(scrollY) * 0.01).pressure());
            }
            this.localPressure = next;
            this.commit(false, true);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void dragTo(double x) {
        double share = Math.max(0, Math.min(1, (x - TRACK_X) / (TRACK_WIDTH - 1)));
        if (this.drag == Drag.TEMPERATURE) {
            this.localTemperature = Math.round(ReactorSettings.MIN_TEMPERATURE + share * (ReactorSettings.MAX_TEMPERATURE - ReactorSettings.MIN_TEMPERATURE));
        } else if (this.drag == Drag.PRESSURE) {
            this.localPressure = roundPressure(ReactorSettings.MIN_PRESSURE * Math.exp(share * LN_PRESSURE_SPAN));
        }
    }

    /** 把界面上的值发给服务端，等它确认。 */
    private void commit(boolean temperature, boolean pressure) {
        if (temperature && !Double.isNaN(this.localTemperature)) {
            this.send(ChemicalReactorBlockEntity.TEMPERATURE_BUTTON + (int) Math.round(this.localTemperature), false);
        }
        if (pressure && !Double.isNaN(this.localPressure)) {
            this.send(ChemicalReactorBlockEntity.PRESSURE_BUTTON + (int) Math.round(this.localPressure * ChemicalReactorBlockEntity.PRESSURE_SCALE), false);
        }
        this.pendingTicks = PENDING_TICKS;
    }

    private void clearLocal() {
        this.localTemperature = Double.NaN;
        this.localPressure = Double.NaN;
        this.pendingTicks = 0;
    }

    /** @param click 响一声按键（选反应、恢复工况）；拖滑块不响 */
    private void send(int button, boolean click) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
            if (click) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
        }
    }

    private static double roundPressure(double pressure) {
        return (double) Math.round(pressure * ChemicalReactorBlockEntity.PRESSURE_SCALE) / ChemicalReactorBlockEntity.PRESSURE_SCALE;
    }
}
