package io.github.sweetcornna.universe.client.factory;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BAD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.GOOD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.INSET;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.WARN;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.network.ResearchActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 科技树界面（ADR-0011）：按前置关系从左往右排成几列，拖动平移、滚轮滚动。点一个科技在下面看详情：上交物品的
 * 科技直接上交，要研究站的科技选为当前研究（全服共用一个）。
 */
public class TechTreeScreen extends Screen {
    private static final int CARD_WIDTH = 124;
    private static final int CARD_HEIGHT = 34;
    private static final int COLUMN_GAP = 28;
    private static final int ROW_GAP = 6;
    private static final int MARGIN = 10;
    private static final int HEADER = 22;
    private static final int DETAIL = 80;
    private static final int BUTTON_WIDTH = 92;
    private static final int BUTTON_HEIGHT = 18;
    private static final int LINE_DONE = 0xFF3E8F5A;
    private static final int LINE = 0xFF2A4452;
    private static final int LOCKED = 0xFF2A3A44;

    private final TechTree tree = Research.TREE;
    private double panX;
    private double panY;
    private boolean placed;
    private boolean dragging;
    private @Nullable String selected;
    /** 这一帧鼠标下的东西，画完再出提示。 */
    private @Nullable Tech hoveredTech;
    private ItemStack hoveredItem = ItemStack.EMPTY;

    private enum Status {
        DONE, CURRENT, AVAILABLE, LOCKED
    }

    public TechTreeScreen() {
        super(Component.translatable("gui.universe.tech_tree"));
    }

    @Override
    protected void init() {
        if (!this.placed) {
            this.placed = true;
            this.selected = this.initialSelection(ClientResearch.state());
            // 让选中的科技落在画面里
            Tech tech = this.selected == null ? null : this.tree.get(this.selected).orElse(null);
            if (tech != null) {
                this.panX = this.width / 2.0 - (MARGIN + this.tree.depth(tech.id()) * (CARD_WIDTH + COLUMN_GAP) + CARD_WIDTH / 2.0);
            }
        }
        this.clampPan();
    }

    /** 打开时选中：正在研究的，否则第一个能做的，否则第一个。 */
    private @Nullable String initialSelection(ResearchState state) {
        if (state.current() != null) {
            return state.current();
        }
        for (Tech tech : this.tree.techs()) {
            if (state.available(this.tree, tech.id())) {
                return tech.id();
            }
        }
        return this.tree.techs().isEmpty() ? null : this.tree.techs().getFirst().id();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 排版

    private int areaTop() {
        return HEADER;
    }

    private int areaBottom() {
        return this.height - DETAIL;
    }

    private int contentWidth() {
        return MARGIN * 2 + this.tree.columns() * (CARD_WIDTH + COLUMN_GAP) - COLUMN_GAP;
    }

    private int contentHeight() {
        return MARGIN * 2 + this.tree.rows() * (CARD_HEIGHT + ROW_GAP) - ROW_GAP;
    }

    /** 内容比画面小时居中，比画面大时不让拖出边。 */
    private void clampPan() {
        int areaWidth = this.width;
        int areaHeight = this.areaBottom() - this.areaTop();
        this.panX = this.contentWidth() <= areaWidth ? (areaWidth - this.contentWidth()) / 2.0
                : Math.clamp(this.panX, areaWidth - this.contentWidth(), 0);
        this.panY = this.contentHeight() <= areaHeight ? (areaHeight - this.contentHeight()) / 2.0
                : Math.clamp(this.panY, areaHeight - this.contentHeight(), 0);
    }

    private int cardX(Tech tech) {
        return (int) this.panX + MARGIN + this.tree.depth(tech.id()) * (CARD_WIDTH + COLUMN_GAP);
    }

    private int cardY(Tech tech) {
        return this.areaTop() + (int) this.panY + MARGIN + this.tree.row(tech.id()) * (CARD_HEIGHT + ROW_GAP);
    }

    private @Nullable Tech techAt(double mouseX, double mouseY) {
        if (mouseY < this.areaTop() || mouseY >= this.areaBottom()) {
            return null;
        }
        for (Tech tech : this.tree.techs()) {
            int x = this.cardX(tech);
            int y = this.cardY(tech);
            if (mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT) {
                return tech;
            }
        }
        return null;
    }

    private Status status(ResearchState state, Tech tech) {
        if (state.isDone(tech.id())) {
            return Status.DONE;
        }
        if (tech.id().equals(state.current())) {
            return Status.CURRENT;
        }
        return state.available(this.tree, tech.id()) ? Status.AVAILABLE : Status.LOCKED;
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ResearchState state = ClientResearch.state();
        this.hoveredTech = this.techAt(mouseX, mouseY);
        this.hoveredItem = ItemStack.EMPTY;

        // 树的底色
        graphics.fill(0, this.areaTop(), this.width, this.areaBottom(), 0xD0070D12);
        graphics.enableScissor(0, this.areaTop(), this.width, this.areaBottom());
        for (Tech tech : this.tree.techs()) {
            for (String required : tech.requires()) {
                this.tree.get(required).ifPresent(parent -> this.connect(graphics, parent, tech, state.isDone(required) ? LINE_DONE : LINE));
            }
        }
        List<int[]> dimmed = new ArrayList<>();
        for (Tech tech : this.tree.techs()) {
            this.card(graphics, state, tech, dimmed);
        }
        graphics.disableScissor();

        // 盖在图标上的一层：没解锁的变暗
        graphics.nextStratum();
        graphics.enableScissor(0, this.areaTop(), this.width, this.areaBottom());
        for (int[] cell : dimmed) {
            graphics.fill(cell[0], cell[1], cell[0] + 16, cell[1] + 16, 0xB0070D12);
        }
        graphics.disableScissor();

        this.header(graphics, state);
        this.detail(graphics, state, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.tooltip(graphics, state, mouseX, mouseY);
    }

    /** 前置连到后续：从前置的右边出来，在后续前面的空隙里拐弯。 */
    private void connect(GuiGraphicsExtractor graphics, Tech parent, Tech child, int color) {
        int startX = this.cardX(parent) + CARD_WIDTH;
        int startY = this.cardY(parent) + CARD_HEIGHT / 2;
        int endX = this.cardX(child);
        int endY = this.cardY(child) + CARD_HEIGHT / 2;
        int bendX = endX - COLUMN_GAP / 2;
        graphics.fill(startX, startY, bendX + 1, startY + 1, color);
        graphics.fill(bendX, Math.min(startY, endY), bendX + 1, Math.max(startY, endY) + 1, color);
        graphics.fill(bendX, endY, endX, endY + 1, color);
    }

    private void card(GuiGraphicsExtractor graphics, ResearchState state, Tech tech, List<int[]> dimmed) {
        int x = this.cardX(tech);
        int y = this.cardY(tech);
        if (x + CARD_WIDTH < 0 || x > this.width || y + CARD_HEIGHT < this.areaTop() || y > this.areaBottom()) {
            return;
        }
        Status status = this.status(state, tech);
        int border = switch (status) {
            case DONE -> GOOD;
            case CURRENT -> pulse(ACCENT);
            case AVAILABLE -> WARN;
            case LOCKED -> LOCKED;
        };
        boolean selected = tech.id().equals(this.selected);
        boolean hovered = tech == this.hoveredTech;
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, selected ? 0xFF173542 : hovered ? 0xFF12242D : 0xFF0C151B);
        if (selected) {
            graphics.outline(x - 2, y - 2, CARD_WIDTH + 4, CARD_HEIGHT + 4, ACCENT);
        }
        int iconY = y + (CARD_HEIGHT - 16) / 2;
        graphics.fill(x + 4, iconY - 1, x + 22, iconY + 17, INSET);
        graphics.fakeItem(icon(tech), x + 5, iconY);
        if (status == Status.LOCKED) {
            dimmed.add(new int[] {x + 5, iconY});
        }
        // 名字最多两行，下面一行状态，整块上下居中
        List<FormattedCharSequence> name = this.font.split(Research.name(tech.id()), CARD_WIDTH - 30);
        int lines = Math.min(2, name.size());
        int top = y + (CARD_HEIGHT - (lines * 9 + 11)) / 2;
        for (int i = 0; i < lines; i++) {
            graphics.text(this.font, name.get(i), x + 26, top + i * 9, status == Status.LOCKED ? DIM : TEXT, false);
        }
        int statusY = top + lines * 9 + 2;
        switch (status) {
            case DONE -> graphics.text(this.font, Component.translatable("gui.universe.tech_tree.card.done"), x + 26, statusY, GOOD, false);
            case CURRENT -> MachineScreen.bar(graphics, x + 26, statusY + 3, CARD_WIDTH - 32, 3,
                    (float) state.progress(tech.id()) / tech.points(), ACCENT);
            case AVAILABLE, LOCKED -> {
                Component line = tech.isHandIn() ? Component.translatable("gui.universe.tech_tree.card.hand_in")
                        : Component.translatable("gui.universe.tech_tree.card.points", state.progress(tech.id()), tech.points());
                graphics.text(this.font, line, x + 26, statusY, status == Status.AVAILABLE ? WARN : DIM, false);
            }
        }
    }

    /** 正在研究的科技边框一明一暗。 */
    private static int pulse(int color) {
        double t = (Math.sin(System.currentTimeMillis() / 300.0) + 1) / 2;
        int alpha = (int) (0x90 + 0x6F * t);
        return alpha << 24 | color & 0xFFFFFF;
    }

    private static ItemStack icon(Tech tech) {
        Item item = tech.unlocks().isEmpty() ? Items.PAPER : Research.item(tech.unlocks().getFirst());
        return new ItemStack(item == Items.AIR ? Items.PAPER : item);
    }

    private void header(GuiGraphicsExtractor graphics, ResearchState state) {
        graphics.fill(0, 0, this.width, HEADER, PANEL);
        graphics.fill(0, HEADER - 1, this.width, HEADER, BORDER);
        graphics.text(this.font, this.title, 8, 7, ACCENT, false);
        int x = 16 + this.font.width(this.title);
        String current = state.current();
        Tech tech = current == null ? null : this.tree.get(current).orElse(null);
        Component summary = tech == null ? Component.translatable("gui.universe.tech_tree.idle")
                : Component.translatable("gui.universe.tech_tree.current", Research.name(tech.id()), state.progress(tech.id()), tech.points());
        graphics.text(this.font, summary, x, 7, tech == null ? DIM : TEXT, false);
        Component hint = Component.translatable("gui.universe.tech_tree.hint", UniverseKeys.TECH_TREE.getTranslatedKeyMessage());
        int hintX = this.width - 8 - this.font.width(hint);
        if (hintX > x + this.font.width(summary) + 12) {
            graphics.text(this.font, hint, hintX, 7, DIM, false);
        }
    }

    private void detail(GuiGraphicsExtractor graphics, ResearchState state, int mouseX, int mouseY) {
        int top = this.areaBottom();
        graphics.fill(0, top, this.width, this.height, PANEL);
        graphics.fill(0, top, this.width, top + 1, BORDER);
        Tech tech = this.selected == null ? null : this.tree.get(this.selected).orElse(null);
        if (tech == null) {
            return;
        }
        Status status = this.status(state, tech);
        int x = 10;
        int y = top + 7;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, INSET);
        graphics.fakeItem(icon(tech), x, y);
        graphics.text(this.font, Research.name(tech.id()).withStyle(ChatFormatting.BOLD), x + 22, y + 4, ACCENT, false);

        // 状态
        MutableComponent line;
        int color;
        switch (status) {
            case DONE -> {
                line = Component.translatable("gui.universe.tech_tree.done");
                color = GOOD;
            }
            case CURRENT -> {
                line = Component.translatable("gui.universe.tech_tree.researching");
                color = ACCENT;
            }
            case AVAILABLE -> {
                line = Component.translatable(tech.isHandIn() ? "gui.universe.tech_tree.available_hand_in" : "gui.universe.tech_tree.available_lab");
                color = WARN;
            }
            default -> {
                MutableComponent names = Component.empty();
                for (String required : tech.requires()) {
                    if (!state.isDone(required)) {
                        if (!names.getSiblings().isEmpty()) {
                            names.append(Component.translatable("gui.universe.tech_tree.separator"));
                        }
                        names.append(Research.name(required));
                    }
                }
                line = Component.translatable("gui.universe.tech_tree.locked", names);
                color = BAD;
            }
        }
        graphics.text(this.font, line, x, y + 20, color, false);

        // 费用和解锁
        Component costLabel = Component.translatable("gui.universe.tech_tree.cost");
        Component unlockLabel = Component.translatable("gui.universe.tech_tree.unlocks");
        int iconsX = x + Math.max(this.font.width(costLabel), this.font.width(unlockLabel)) + 8;
        int costY = y + 32;
        graphics.text(this.font, costLabel, x, costY + 4, DIM, false);
        int cx = iconsX;
        Player player = this.minecraft.player;
        if (tech.isHandIn()) {
            for (Tech.ItemCost cost : tech.handIn()) {
                ItemStack stack = new ItemStack(Research.item(cost.item()));
                cx = this.itemIcon(graphics, stack, cx, costY, mouseX, mouseY);
                int have = player == null ? 0 : count(player.getInventory(), stack.getItem());
                boolean enough = status == Status.DONE || have >= cost.count() || player != null && player.isCreative();
                String text = status == Status.DONE ? "×" + cost.count() : Math.min(have, 999) + "/" + cost.count();
                graphics.text(this.font, text, cx + 2, costY + 4, enough ? GOOD : BAD, false);
                cx += this.font.width(text) + 12;
            }
        } else {
            for (String matrix : tech.matrices()) {
                cx = this.itemIcon(graphics, new ItemStack(Research.item(matrix)), cx, costY, mouseX, mouseY);
            }
            graphics.text(this.font, Component.translatable("gui.universe.tech_tree.per_point", tech.points()), cx + 4, costY + 4, TEXT, false);
        }
        int unlockY = y + 51;
        graphics.text(this.font, unlockLabel, x, unlockY + 4, DIM, false);
        int ux = iconsX;
        for (String unlock : tech.unlocks()) {
            ux = this.itemIcon(graphics, new ItemStack(Research.item(unlock)), ux, unlockY, mouseX, mouseY);
        }
        // 不解锁物品的科技（比如戴森球应力系统）写一句效果
        String effect = "tech.universe." + tech.id() + ".effect";
        if (Language.getInstance().has(effect)) {
            graphics.text(this.font, Component.translatable(effect), ux + (tech.unlocks().isEmpty() ? 0 : 4), unlockY + 4, TEXT, false);
        }

        // 右边：进度和按钮
        int buttonX = this.width - BUTTON_WIDTH - 12;
        int buttonY = top + DETAIL - BUTTON_HEIGHT - 12;
        if (!tech.isHandIn() && status != Status.DONE) {
            Component points = Component.translatable("gui.universe.tech_tree.points", state.progress(tech.id()), tech.points());
            graphics.text(this.font, points, buttonX, top + 12, TEXT, false);
            MachineScreen.bar(graphics, buttonX, top + 24, BUTTON_WIDTH, 4, (float) state.progress(tech.id()) / tech.points(),
                    status == Status.CURRENT ? ACCENT : DIM);
        }
        Action action = this.action(state, tech, status);
        if (action != null) {
            boolean hovered = action.enabled && mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH && mouseY >= buttonY
                    && mouseY < buttonY + BUTTON_HEIGHT;
            graphics.fill(buttonX, buttonY, buttonX + BUTTON_WIDTH, buttonY + BUTTON_HEIGHT, action.enabled ? BORDER : LOCKED);
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + BUTTON_WIDTH - 1, buttonY + BUTTON_HEIGHT - 1,
                    !action.enabled ? 0xFF0C151B : hovered ? 0xFF1F4A5A : 0xFF12303B);
            graphics.centeredText(this.font, action.label, buttonX + BUTTON_WIDTH / 2, buttonY + 5, action.enabled ? TEXT : DIM);
        }
    }

    /** 画一个物品图标，鼠标在上面时记下来出提示；返回下一个图标的位置。 */
    private int itemIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, INSET);
        graphics.fakeItem(stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            this.hoveredItem = stack;
        }
        return x + 20;
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void tooltip(GuiGraphicsExtractor graphics, ResearchState state, int mouseX, int mouseY) {
        if (!this.hoveredItem.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, this.hoveredItem, mouseX, mouseY);
            return;
        }
        Tech tech = this.hoveredTech;
        if (tech == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Research.name(tech.id()).withStyle(ChatFormatting.AQUA));
        if (!tech.isHandIn()) {
            lines.add(Component.translatable("gui.universe.tech_tree.points", state.progress(tech.id()), tech.points())
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.universe.tech_tree.click").withStyle(ChatFormatting.DARK_GRAY));
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ---- 操作

    /** 详情里的按钮。 */
    private record Action(Component label, boolean enabled, ResearchActionPayload.@Nullable Action payload) {
    }

    private @Nullable Action action(ResearchState state, Tech tech, Status status) {
        return switch (status) {
            case DONE -> null;
            case LOCKED -> new Action(Component.translatable("gui.universe.tech_tree.locked_button"), false, null);
            case CURRENT -> new Action(Component.translatable("gui.universe.tech_tree.stop"), true, ResearchActionPayload.Action.STOP);
            case AVAILABLE -> {
                if (!tech.isHandIn()) {
                    yield new Action(Component.translatable("gui.universe.tech_tree.start"), true, ResearchActionPayload.Action.START);
                }
                boolean enough = this.minecraft.player != null && (this.minecraft.player.isCreative() || this.hasItems(tech));
                yield new Action(Component.translatable(enough ? "gui.universe.tech_tree.hand_in" : "gui.universe.tech_tree.missing"),
                        enough, ResearchActionPayload.Action.HAND_IN);
            }
        };
    }

    private boolean hasItems(Tech tech) {
        Player player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        for (Tech.ItemCost cost : tech.handIn()) {
            if (count(player.getInventory(), Research.item(cost.item())) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        ResearchState state = ClientResearch.state();
        Tech tech = this.selected == null ? null : this.tree.get(this.selected).orElse(null);
        if (tech != null) {
            int buttonX = this.width - BUTTON_WIDTH - 12;
            int buttonY = this.areaBottom() + DETAIL - BUTTON_HEIGHT - 12;
            Action action = this.action(state, tech, this.status(state, tech));
            if (action != null && action.enabled && action.payload != null && mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
                ClientPacketDistributor.sendToServer(new ResearchActionPayload(action.payload, tech.id()));
                this.click();
                return true;
            }
        }
        if (mouseY >= this.areaTop() && mouseY < this.areaBottom()) {
            Tech hit = this.techAt(mouseX, mouseY);
            if (hit != null) {
                this.selected = hit.id();
                this.click();
            } else {
                this.dragging = true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging) {
            this.panX += dx;
            this.panY += dy;
            this.clampPan();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // 只能横着拖的时候，竖着的滚轮也拿来横着滚
        boolean tall = this.contentHeight() > this.areaBottom() - this.areaTop();
        if (tall) {
            this.panY += scrollY * 20;
        } else {
            this.panX += scrollY * 20;
        }
        this.panX += scrollX * 20;
        this.clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UniverseKeys.TECH_TREE.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
