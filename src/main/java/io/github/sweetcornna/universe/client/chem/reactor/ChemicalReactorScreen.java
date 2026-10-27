package io.github.sweetcornna.universe.client.chem.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.chem.reactor.ChemicalReactorBlockEntity;
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
import io.github.sweetcornna.universe.factory.research.Research;
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
 * 化学反应釜的界面（ADR-0036 §四）。左边选反应（没研究的变暗），中间原料、催化剂、产物，右边"温度 × 压强"的产量图：
 * 全服在哪一格做成过一批，那一格按放了催化剂时的主产物产量上色，没做过的是灰的，白框是现在的工况。下面两条滑块调温度和压强
 * （拖动、点产量图的格子、滚轮微调，按住 Shift 调得更细），再下面三行读数按界面上的工况现算，拖动时就跟着变。
 */
public class ChemicalReactorScreen extends MachineScreen {
    private static final int BUTTON_X = 6;
    private static final int BUTTON_Y = 20;
    private static final int BUTTON_WIDTH = 86;
    private static final int BUTTON_STEP = 13;
    private static final int DEFAULTS_Y = BUTTON_Y + 5 * BUTTON_STEP + 1;
    private static final int CATALYST_TEXT_X = 120;
    private static final int CATALYST_Y = 42;
    private static final int PROGRESS_X = 100;
    private static final int PROGRESS_Y = 85;
    private static final int PROGRESS_WIDTH = 52;
    private static final int STATUS_X = 126;
    private static final int STATUS_Y = 90;
    private static final int MAP_X = 166;
    private static final int MAP_Y = 20;
    private static final int CELL = 8;
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
    /** 产量图每格放了催化剂时的产量（占上限的比例，和气温无关），按反应缓存。 */
    private @Nullable Reaction ratesFor;
    private final double[] rates = new double[YieldGrid.CELLS];

    public ChemicalReactorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    // ---- 读界面数据

    private @Nullable Reaction reaction() {
        int index = this.menu.data(ChemicalReactorBlockEntity.REACTION) - 1;
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

    private boolean catalyzed() {
        return (this.menu.data(ChemicalReactorBlockEntity.CATALYST) & 1) != 0;
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

    private boolean unlocked(Reaction reaction) {
        MachineRecipe recipe = recipe(reaction);
        return recipe != null && this.minecraft != null && this.minecraft.level != null
                && Research.isUnlocked(this.minecraft.level, recipe.research());
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
        Reaction reaction = this.reaction();
        if (reaction == null) {
            return;
        }
        // 空着的原料格和催化剂格画出该放的东西（半透明）
        List<int[]> ghosts = new ArrayList<>();
        List<ItemCount> inputs = reaction.inputs();
        for (int i = 0; i < ChemicalReactorBlockEntity.INPUTS; i++) {
            this.ghost(graphics, ghosts, i, i < inputs.size() ? inputs.get(i) : null);
        }
        this.ghost(graphics, ghosts, ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemCount(reaction.catalyst().item(), 1));
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

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Reaction reaction = this.reaction();
        if (reaction != null) {
            // 标题那一行的右边写方程式
            graphics.text(this.font, reaction.equation(), this.imageWidth - 8 - this.font.width(reaction.equation()), this.titleLabelY, DIM, false);
        }
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Reaction selected = this.reaction();
        for (Reaction reaction : Reaction.values()) {
            int y = BUTTON_Y + reaction.ordinal() * BUTTON_STEP;
            boolean unlocked = this.unlocked(reaction);
            this.reactionButton(graphics, y, reaction, reaction == selected, unlocked, mouseX, mouseY);
        }
        if (selected == null) {
            this.status(graphics);
            return;
        }
        this.textButton(graphics, BUTTON_X, DEFAULTS_Y, BUTTON_WIDTH, Component.translatable("gui.universe.chemical_reactor.defaults"), TEXT,
                mouseX, mouseY);
        this.catalystLine(graphics);
        bar(graphics, PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, 2, this.menu.data(ChemicalReactorBlockEntity.PROGRESS) / 1000F, ACCENT);
        this.status(graphics);
        ReactorSettings settings = this.settings();
        this.yieldMap(graphics, selected, settings, mouseX, mouseY);
        this.slider(graphics, TEMPERATURE_Y, Component.translatable("gui.universe.chemical_reactor.temperature", ReactorRecipes.celsius(settings.temperature())),
                temperatureShare(settings.temperature()), temperatureShare(selected.defaults().temperature()), mouseX, mouseY);
        this.slider(graphics, PRESSURE_Y, Component.translatable("gui.universe.chemical_reactor.pressure", ReactorRecipes.bar(settings.pressure())),
                pressureShare(settings.pressure()), pressureShare(selected.defaults().pressure()), mouseX, mouseY);
        this.readouts(graphics, selected, settings);
    }

    private void reactionButton(GuiGraphicsExtractor graphics, int y, Reaction reaction, boolean selected, boolean unlocked, int mouseX, int mouseY) {
        boolean hovered = unlocked && overMode(mouseX, mouseY, BUTTON_X, y, BUTTON_WIDTH);
        graphics.fill(BUTTON_X, y, BUTTON_X + BUTTON_WIDTH, y + MODE_HEIGHT, selected ? ACCENT : unlocked ? BORDER : 0xFF24404D);
        graphics.fill(BUTTON_X + 1, y + 1, BUTTON_X + BUTTON_WIDTH - 1, y + MODE_HEIGHT - 1,
                selected ? 0xFF1F4A5A : hovered ? 0xFF1A3D4A : 0xFF12303B);
        graphics.centeredText(this.font, Component.translatable("gui.universe.chemical_reactor.short." + reaction.id()), BUTTON_X + BUTTON_WIDTH / 2,
                y + 2, selected ? ACCENT : unlocked ? TEXT : DIM);
    }

    private void catalystLine(GuiGraphicsExtractor graphics) {
        int flags = this.menu.data(ChemicalReactorBlockEntity.CATALYST);
        Component text;
        int color;
        if ((flags & 1) != 0) {
            int left = this.menu.data(ChemicalReactorBlockEntity.WEAR);
            text = Component.translatable("gui.universe.chemical_reactor.catalyst_left", left);
            color = left <= 32 ? WARN : GOOD;
        } else if ((flags & 2) != 0) {
            text = Component.translatable("gui.universe.chemical_reactor.catalyst_wrong");
            color = BAD;
        } else {
            text = Component.translatable("gui.universe.chemical_reactor.catalyst_empty");
            color = WARN;
        }
        graphics.text(this.font, text, CATALYST_TEXT_X, CATALYST_Y + 4, color, false);
    }

    private void status(GuiGraphicsExtractor graphics) {
        int status = this.menu.data(ChemicalReactorBlockEntity.STATUS);
        if (this.reaction() == null) {
            status = ChemicalReactorBlockEntity.NO_REACTION;
        }
        String key = switch (status) {
            case ChemicalReactorBlockEntity.RUNNING -> "running";
            case ChemicalReactorBlockEntity.NO_INPUT -> "no_input";
            case ChemicalReactorBlockEntity.OUTPUT_FULL -> "output_full";
            case ChemicalReactorBlockEntity.NO_POWER -> "no_power";
            case ChemicalReactorBlockEntity.LOCKED -> "locked";
            default -> "no_reaction";
        };
        int color = switch (status) {
            case ChemicalReactorBlockEntity.RUNNING -> GOOD;
            case ChemicalReactorBlockEntity.NO_INPUT, ChemicalReactorBlockEntity.OUTPUT_FULL -> WARN;
            case ChemicalReactorBlockEntity.NO_POWER, ChemicalReactorBlockEntity.LOCKED -> BAD;
            default -> DIM;
        };
        graphics.centeredText(this.font, Component.translatable("gui.universe.chemical_reactor.status." + key), STATUS_X, STATUS_Y, color);
    }

    private void yieldMap(GuiGraphicsExtractor graphics, Reaction reaction, ReactorSettings settings, int mouseX, int mouseY) {
        int width = YieldGrid.TEMPERATURE_CELLS * CELL;
        int height = YieldGrid.PRESSURE_CELLS * CELL;
        graphics.fill(MAP_X - 1, MAP_Y - 1, MAP_X + width + 1, MAP_Y + height + 1, 0xFF24404D);
        graphics.fill(MAP_X, MAP_Y, MAP_X + width, MAP_Y + height, INSET);
        double[] rates = this.rates(reaction);
        for (int i = 0; i < YieldGrid.CELLS; i++) {
            int[] origin = cellOrigin(YieldGrid.byIndex(i));
            int color = this.measured(i) ? heat(rates[i]) : UNMEASURED;
            graphics.fill(origin[0], origin[1], origin[0] + CELL - 1, origin[1] + CELL - 1, color);
        }
        int hovered = cellAt(mouseX, mouseY);
        if (hovered >= 0) {
            int[] origin = cellOrigin(YieldGrid.byIndex(hovered));
            frame(graphics, origin[0] - 1, origin[1] - 1, CELL + 1, 0x80FFFFFF);
        }
        int[] current = cellOrigin(YieldGrid.cell(settings));
        frame(graphics, current[0] - 1, current[1] - 1, CELL + 1, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("gui.universe.chemical_reactor.axes"), MAP_X + width / 2, MAP_Y + height + 4, DIM);
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x, y, x + 1, y + size, color);
        graphics.fill(x + size - 1, y, x + size, y + size, color);
    }

    /** 一格的左上角（相对界面）：温度往右升，压强往上升。 */
    private static int[] cellOrigin(YieldGrid.Cell cell) {
        return new int[] {MAP_X + cell.temperature() * CELL, MAP_Y + (YieldGrid.PRESSURE_CELLS - 1 - cell.pressure()) * CELL};
    }

    /** 鼠标下的格子编号，不在图上为 -1。坐标相对界面。 */
    private static int cellAt(double x, double y) {
        int column = (int) Math.floor((x - MAP_X) / CELL);
        int row = (int) Math.floor((y - MAP_Y) / CELL);
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

    private void slider(GuiGraphicsExtractor graphics, int y, Component label, double share, double textbook, int mouseX, int mouseY) {
        graphics.text(this.font, label, 8, y, TEXT, false);
        int top = y + 1;
        graphics.fill(TRACK_X - 1, top - 1, TRACK_X + TRACK_WIDTH + 1, top + 6, 0xFF24404D);
        graphics.fill(TRACK_X, top, TRACK_X + TRACK_WIDTH, top + 5, INSET);
        int knob = TRACK_X + (int) Math.round(share * (TRACK_WIDTH - 1));
        graphics.fill(TRACK_X, top + 1, knob, top + 4, 0xFF2E7A92);
        // 教科书的工况在轨道上点一个小记号
        int mark = TRACK_X + (int) Math.round(textbook * (TRACK_WIDTH - 1));
        graphics.fill(mark, top - 2, mark + 1, top, WARN);
        boolean hovered = overTrack(mouseX, mouseY, y);
        graphics.fill(knob - 1, top - 2, knob + 2, top + 7, hovered || this.drag != Drag.NONE ? ACCENT : TEXT);
    }

    private void readouts(GuiGraphicsExtractor graphics, Reaction reaction, ReactorSettings settings) {
        ReactorResult result = ReactorModel.evaluate(reaction, settings, this.catalyzed(), this.ambient());
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

    /** "每分钟 18.6 个氨"；几样产物用顿号隔开，费托合成的甲烷排在最后。 */
    private Component products(Reaction reaction, ReactorResult result) {
        MutableComponent list = Component.empty();
        List<ItemCount> outputs = reaction.outputs();
        ItemCount byproduct = reaction.byproduct();
        for (int i = 0; i < outputs.size(); i++) {
            double rate = result.batchesPerMinute() * outputs.get(i).count();
            if (i == 0 && byproduct != null) {
                rate *= 1 - result.methaneSelectivity();
            }
            if (i > 0) {
                list.append(Component.translatable("gui.universe.chemical_reactor.separator"));
            }
            list.append(this.amount(rate, outputs.get(i)));
        }
        if (byproduct != null) {
            list.append(Component.translatable("gui.universe.chemical_reactor.separator"));
            list.append(this.amount(result.byproductPerMinute(), byproduct));
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
        for (Reaction reaction : Reaction.values()) {
            if (overMode(x, y, BUTTON_X, BUTTON_Y + reaction.ordinal() * BUTTON_STEP, BUTTON_WIDTH)) {
                lines.add(Component.translatable("reaction.universe." + reaction.id()).withStyle(ChatFormatting.AQUA));
                lines.add(Component.literal(reaction.equation()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.universe.chemical_reactor.needs_catalyst", catalystName(reaction.catalyst()))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.universe.chemical_reactor.textbook", ReactorRecipes.conditions(reaction))
                        .withStyle(ChatFormatting.GRAY));
                MachineRecipe recipe = recipe(reaction);
                if (recipe == null) {
                    lines.add(Component.translatable("gui.universe.chemical_reactor.unavailable").withStyle(ChatFormatting.RED));
                } else if (!this.unlocked(reaction)) {
                    lines.add(Component.translatable("gui.universe.recipe_machine.requires", Research.name(recipe.research()))
                            .withStyle(ChatFormatting.RED));
                }
                return lines;
            }
        }
        Reaction reaction = this.reaction();
        if (reaction == null) {
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
            if (this.measured(cell)) {
                ReactorResult result = ReactorModel.evaluate(reaction, YieldGrid.center(at), true, this.ambient());
                lines.add(this.products(reaction, result).copy().withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("gui.universe.chemical_reactor.unmeasured").withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable("gui.universe.chemical_reactor.cell_hint").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        if (x >= 99 && x < 160 && y >= CATALYST_Y - 1 && y < CATALYST_Y + 17) {
            lines.add(Component.translatable("gui.universe.chemical_reactor.needs_catalyst", catalystName(reaction.catalyst())));
            lines.add(Component.translatable("gui.universe.chemical_reactor.catalyst_life", reaction.catalyst().batches())
                    .withStyle(ChatFormatting.GRAY));
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

    private static Component catalystName(Catalyst catalyst) {
        return new ItemStack(ReactorRecipes.item(catalyst.item())).getHoverName();
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
        for (Reaction reaction : Reaction.values()) {
            if (overMode(x, y, BUTTON_X, BUTTON_Y + reaction.ordinal() * BUTTON_STEP, BUTTON_WIDTH)) {
                if (this.unlocked(reaction) && reaction != this.reaction()) {
                    this.send(reaction.ordinal(), true);
                    this.clearLocal();
                }
                return true;
            }
        }
        Reaction reaction = this.reaction();
        if (reaction == null) {
            return super.mouseClicked(event, doubleClick);
        }
        if (overMode(x, y, BUTTON_X, DEFAULTS_Y, BUTTON_WIDTH)) {
            this.send(ChemicalReactorBlockEntity.DEFAULTS_BUTTON, true);
            this.clearLocal();
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
        if (this.reaction() == null || scrollY == 0 || this.minecraft == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
