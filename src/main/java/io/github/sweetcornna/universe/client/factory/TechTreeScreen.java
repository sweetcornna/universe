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

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.stats.DashboardHud;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.core.research.TechTree;
import io.github.sweetcornna.universe.core.research.Upgrade;
import io.github.sweetcornna.universe.core.stats.Dashboard;
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
 * 科技直接上交，要研究站的科技选为当前研究（全服共用一个）。升级科技（ADR-0018）单独排在最右边一列，图标右下角
 * 写着级数，详情里是现在和下一级的效果。
 *
 * <p>右边是研究队列（ADR-0033 §五）：详情里点"加入队列"（或者按住 Shift 点卡片）排到队尾，当前的研究完成或停下时
 * 自动开始队里第一个能研究的。队里的每一项可以往前、往后挪，可以去掉。全服共用一条队列。
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
    /** 升级科技那一列顶上的标题占的高度。 */
    private static final int UPGRADE_LABEL = 12;
    private static final int QUEUE_WIDTH = 140;
    private static final int QUEUE_TOP = 20;
    private static final int QUEUE_ROW = 22;
    private static final int QUEUE_BUTTON = 10;

    private final TechTree tree = Research.TREE;
    private double panX;
    private double panY;
    private boolean placed;
    private boolean dragging;
    private int queueScroll;
    private @Nullable String selected;
    /** 这一帧鼠标下的东西，画完再出提示。 */
    private @Nullable String hoveredId;
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
            if (this.selected != null && this.known(this.selected)) {
                this.panX = this.width / 2.0 - (MARGIN + this.column(this.selected) * (CARD_WIDTH + COLUMN_GAP) + CARD_WIDTH / 2.0);
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

    private int columns() {
        return this.tree.columns() + (this.tree.upgrades().isEmpty() ? 0 : 1);
    }

    private int contentWidth() {
        return MARGIN * 2 + this.columns() * (CARD_WIDTH + COLUMN_GAP) - COLUMN_GAP;
    }

    private int contentHeight() {
        int techs = this.tree.rows() * (CARD_HEIGHT + ROW_GAP) - ROW_GAP;
        int upgrades = this.tree.upgrades().isEmpty() ? 0 : UPGRADE_LABEL + this.tree.upgrades().size() * (CARD_HEIGHT + ROW_GAP) - ROW_GAP;
        return MARGIN * 2 + Math.max(techs, upgrades);
    }

    /** 画在树上的所有东西：先是科技，后是升级科技。 */
    private List<String> ids() {
        List<String> ids = new ArrayList<>();
        this.tree.techs().forEach(tech -> ids.add(tech.id()));
        this.tree.upgrades().forEach(upgrade -> ids.add(upgrade.id()));
        return ids;
    }

    private boolean known(String id) {
        return this.tree.get(id).isPresent() || this.tree.upgrade(id).isPresent();
    }

    private @Nullable Upgrade upgrade(String id) {
        return this.tree.upgrade(id).orElse(null);
    }

    private int column(String id) {
        return this.upgrade(id) != null ? this.tree.columns() : this.tree.depth(id);
    }

    /** 树占的宽度：右边留给研究队列。 */
    private int treeRight() {
        return this.width - QUEUE_WIDTH;
    }

    /** 内容比画面小时居中，比画面大时不让拖出边。 */
    private void clampPan() {
        int areaWidth = this.treeRight();
        int areaHeight = this.areaBottom() - this.areaTop();
        this.panX = this.contentWidth() <= areaWidth ? (areaWidth - this.contentWidth()) / 2.0
                : Math.clamp(this.panX, areaWidth - this.contentWidth(), 0);
        this.panY = this.contentHeight() <= areaHeight ? (areaHeight - this.contentHeight()) / 2.0
                : Math.clamp(this.panY, areaHeight - this.contentHeight(), 0);
    }

    private int columnX(int column) {
        return (int) this.panX + MARGIN + column * (CARD_WIDTH + COLUMN_GAP);
    }

    private int cardX(String id) {
        return this.columnX(this.column(id));
    }

    private int cardY(String id) {
        Upgrade upgrade = this.upgrade(id);
        int top = this.areaTop() + (int) this.panY + MARGIN;
        if (upgrade != null) {
            return top + UPGRADE_LABEL + this.tree.upgrades().indexOf(upgrade) * (CARD_HEIGHT + ROW_GAP);
        }
        return top + this.tree.row(id) * (CARD_HEIGHT + ROW_GAP);
    }

    private @Nullable String nodeAt(double mouseX, double mouseY) {
        if (mouseY < this.areaTop() || mouseY >= this.areaBottom() || mouseX >= this.treeRight()) {
            return null;
        }
        for (String id : this.ids()) {
            int x = this.cardX(id);
            int y = this.cardY(id);
            if (mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT) {
                return id;
            }
        }
        return null;
    }

    private Status status(ResearchState state, String id) {
        Upgrade upgrade = this.upgrade(id);
        if (upgrade != null ? state.isMaxed(upgrade) : state.isDone(id)) {
            return Status.DONE;
        }
        if (id.equals(state.current())) {
            return Status.CURRENT;
        }
        return state.available(this.tree, id) ? Status.AVAILABLE : Status.LOCKED;
    }

    /** 研究站研究的（不是上交物品的）。 */
    private boolean isLab(String id) {
        return this.upgrade(id) != null || this.tree.get(id).map(tech -> !tech.isHandIn()).orElse(false);
    }

    /** 卡片和详情里的名字：升级科技带上正在研究（或满级时已到）的级数。 */
    private MutableComponent displayName(ResearchState state, String id) {
        Upgrade upgrade = this.upgrade(id);
        if (upgrade == null) {
            return Research.name(id);
        }
        int level = state.level(id);
        return state.isMaxed(upgrade) ? Research.name(id, level) : Research.name(id, level + 1);
    }

    /** 还没完成的前置：科技是它的前置，升级科技是下一级的前置和矩阵的科技。 */
    private List<String> requirements(ResearchState state, String id) {
        Upgrade upgrade = this.upgrade(id);
        List<String> required = upgrade != null ? this.tree.requirements(upgrade, state.level(id) + 1)
                : this.tree.get(id).map(Tech::requires).orElse(List.of());
        return required.stream().filter(tech -> !state.isDone(tech)).toList();
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ResearchState state = ClientResearch.state();
        this.hoveredId = this.nodeAt(mouseX, mouseY);
        this.hoveredItem = ItemStack.EMPTY;

        // 树的底色
        graphics.fill(0, this.areaTop(), this.treeRight(), this.areaBottom(), 0xD0070D12);
        graphics.enableScissor(0, this.areaTop(), this.treeRight(), this.areaBottom());
        for (Tech tech : this.tree.techs()) {
            for (String required : tech.requires()) {
                this.tree.get(required).ifPresent(parent -> this.connect(graphics, parent, tech, state.isDone(required) ? LINE_DONE : LINE));
            }
        }
        if (!this.tree.upgrades().isEmpty()) {
            // 升级科技那一列：前面一道竖线隔开，顶上写标题
            int x = this.columnX(this.tree.columns());
            graphics.fill(x - COLUMN_GAP / 2, this.areaTop(), x - COLUMN_GAP / 2 + 1, this.areaBottom(), LINE);
            graphics.text(this.font, Component.translatable("gui.universe.tech_tree.upgrades"), x + 2,
                    this.areaTop() + (int) this.panY + MARGIN + 1, DIM, false);
        }
        List<int[]> dimmed = new ArrayList<>();
        List<int[]> badges = new ArrayList<>();
        for (String id : this.ids()) {
            this.card(graphics, state, id, dimmed, badges);
        }
        graphics.disableScissor();

        // 盖在图标上的一层：没解锁的变暗，升级科技写上级数，排在队里的标上第几
        graphics.nextStratum();
        graphics.enableScissor(0, this.areaTop(), this.treeRight(), this.areaBottom());
        for (int[] cell : dimmed) {
            graphics.fill(cell[0], cell[1], cell[0] + 16, cell[1] + 16, 0xB0070D12);
        }
        for (int[] badge : badges) {
            String level = Integer.toString(badge[2]);
            graphics.text(this.font, level, badge[0] + 17 - this.font.width(level), badge[1] + 9, 0xFFFFFFFF, true);
        }
        for (int i = 0; i < state.queue().size(); i++) {
            String id = state.queue().get(i);
            if (!this.known(id) || state.queue().indexOf(id) != i) {
                continue;
            }
            String number = "#" + (i + 1);
            int x = this.cardX(id) + CARD_WIDTH - this.font.width(number) - 3;
            int y = this.cardY(id) + 2;
            graphics.text(this.font, number, x, y, ACCENT, false);
        }
        graphics.disableScissor();

        this.header(graphics, state);
        this.queue(graphics, state, mouseX, mouseY);
        this.detail(graphics, state, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.tooltip(graphics, state, mouseX, mouseY);
    }

    /** 前置连到后续：从前置的右边出来，在后续前面的空隙里拐弯。 */
    private void connect(GuiGraphicsExtractor graphics, Tech parent, Tech child, int color) {
        int startX = this.cardX(parent.id()) + CARD_WIDTH;
        int startY = this.cardY(parent.id()) + CARD_HEIGHT / 2;
        int endX = this.cardX(child.id());
        int endY = this.cardY(child.id()) + CARD_HEIGHT / 2;
        int bendX = endX - COLUMN_GAP / 2;
        graphics.fill(startX, startY, bendX + 1, startY + 1, color);
        graphics.fill(bendX, Math.min(startY, endY), bendX + 1, Math.max(startY, endY) + 1, color);
        graphics.fill(bendX, endY, endX, endY + 1, color);
    }

    private void card(GuiGraphicsExtractor graphics, ResearchState state, String id, List<int[]> dimmed, List<int[]> badges) {
        int x = this.cardX(id);
        int y = this.cardY(id);
        if (x + CARD_WIDTH < 0 || x > this.width || y + CARD_HEIGHT < this.areaTop() || y > this.areaBottom()) {
            return;
        }
        Status status = this.status(state, id);
        int border = switch (status) {
            case DONE -> GOOD;
            case CURRENT -> pulse(ACCENT);
            case AVAILABLE -> WARN;
            case LOCKED -> LOCKED;
        };
        boolean selected = id.equals(this.selected);
        boolean hovered = id.equals(this.hoveredId);
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, selected ? 0xFF173542 : hovered ? 0xFF12242D : 0xFF0C151B);
        if (selected) {
            graphics.outline(x - 2, y - 2, CARD_WIDTH + 4, CARD_HEIGHT + 4, ACCENT);
        }
        int iconY = y + (CARD_HEIGHT - 16) / 2;
        graphics.fill(x + 4, iconY - 1, x + 22, iconY + 17, INSET);
        graphics.fakeItem(this.icon(id), x + 5, iconY);
        if (status == Status.LOCKED) {
            dimmed.add(new int[] {x + 5, iconY});
        }
        Upgrade upgrade = this.upgrade(id);
        if (upgrade != null && state.level(id) > 0) {
            badges.add(new int[] {x + 5, iconY, state.level(id)});
        }
        // 名字最多两行，下面一行状态，整块上下居中
        List<FormattedCharSequence> name = TextWrap.split(this.font, Research.name(id), CARD_WIDTH - 30);
        int lines = Math.min(2, name.size());
        int top = y + (CARD_HEIGHT - (lines * 9 + 11)) / 2;
        for (int i = 0; i < lines; i++) {
            graphics.text(this.font, name.get(i), x + 26, top + i * 9, status == Status.LOCKED ? DIM : TEXT, false);
        }
        int statusY = top + lines * 9 + 2;
        int needed = state.pointsNeeded(this.tree, id);
        switch (status) {
            case DONE -> graphics.text(this.font, Component.translatable(upgrade != null ? "gui.universe.tech_tree.card.maxed"
                    : "gui.universe.tech_tree.card.done"), x + 26, statusY, GOOD, false);
            case CURRENT -> MachineScreen.bar(graphics, x + 26, statusY + 3, CARD_WIDTH - 32, 3, (float) state.progress(id) / needed, ACCENT);
            case AVAILABLE, LOCKED -> {
                Component line = !this.isLab(id) ? Component.translatable("gui.universe.tech_tree.card.hand_in")
                        : Component.translatable("gui.universe.tech_tree.card.points", state.progress(id), needed);
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

    private ItemStack icon(String id) {
        Upgrade upgrade = this.upgrade(id);
        Tech tech = this.tree.get(id).orElse(null);
        Item item = upgrade != null ? Research.item(upgrade.icon())
                : tech == null || tech.unlocks().isEmpty() ? Items.PAPER : Research.item(tech.unlocks().getFirst());
        return new ItemStack(item == Items.AIR ? Items.PAPER : item);
    }

    private void header(GuiGraphicsExtractor graphics, ResearchState state) {
        graphics.fill(0, 0, this.width, HEADER, PANEL);
        graphics.fill(0, HEADER - 1, this.width, HEADER, BORDER);
        graphics.text(this.font, this.title, 8, 7, ACCENT, false);
        int x = 16 + this.font.width(this.title);
        String current = state.current();
        boolean researching = current != null && this.known(current);
        Component summary = !researching ? Component.translatable("gui.universe.tech_tree.idle")
                : Component.translatable("gui.universe.tech_tree.current", this.displayName(state, current), state.progress(current),
                        state.pointsNeeded(this.tree, current));
        graphics.text(this.font, summary, x, 7, researching ? TEXT : DIM, false);
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
        String id = this.selected;
        if (id == null || !this.known(id)) {
            return;
        }
        Tech tech = this.tree.get(id).orElse(null);
        Upgrade upgrade = this.upgrade(id);
        Status status = this.status(state, id);
        int x = 10;
        int y = top + 7;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, INSET);
        graphics.fakeItem(this.icon(id), x, y);
        graphics.text(this.font, this.displayName(state, id).withStyle(ChatFormatting.BOLD), x + 22, y + 4, ACCENT, false);

        // 状态
        MutableComponent line;
        int color;
        switch (status) {
            case DONE -> {
                line = Component.translatable(upgrade != null ? "gui.universe.tech_tree.maxed" : "gui.universe.tech_tree.done");
                color = GOOD;
            }
            case CURRENT -> {
                line = Component.translatable("gui.universe.tech_tree.researching");
                color = ACCENT;
            }
            case AVAILABLE -> {
                line = Component.translatable(this.isLab(id) ? "gui.universe.tech_tree.available_lab" : "gui.universe.tech_tree.available_hand_in");
                color = WARN;
            }
            default -> {
                MutableComponent names = Component.empty();
                for (String required : this.requirements(state, id)) {
                    if (!names.getSiblings().isEmpty()) {
                        names.append(Component.translatable("gui.universe.tech_tree.separator"));
                    }
                    names.append(Research.name(required));
                }
                line = Component.translatable("gui.universe.tech_tree.locked", names);
                color = BAD;
            }
        }
        graphics.text(this.font, line, x, y + 20, color, false);

        // 费用和解锁（升级科技是效果）
        Component costLabel = Component.translatable("gui.universe.tech_tree.cost");
        Component unlockLabel = Component.translatable(upgrade != null ? "gui.universe.tech_tree.effect" : "gui.universe.tech_tree.unlocks");
        int iconsX = x + Math.max(this.font.width(costLabel), this.font.width(unlockLabel)) + 8;
        int costY = y + 32;
        int unlockY = y + 51;
        graphics.text(this.font, costLabel, x, costY + 4, DIM, false);
        graphics.text(this.font, unlockLabel, x, unlockY + 4, DIM, false);
        int cx = iconsX;
        Player player = this.minecraft.player;
        if (upgrade != null) {
            this.upgradeDetail(graphics, state, upgrade, iconsX, costY, unlockY, mouseX, mouseY);
        } else if (tech.isHandIn()) {
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
        if (tech != null) {
            int ux = iconsX;
            for (String unlock : tech.unlocks()) {
                ux = this.itemIcon(graphics, new ItemStack(Research.item(unlock)), ux, unlockY, mouseX, mouseY);
            }
            // 不解锁物品的科技（比如戴森球应力系统）写一句效果
            String effect = "tech.universe." + tech.id() + ".effect";
            if (Language.getInstance().has(effect)) {
                graphics.text(this.font, Component.translatable(effect), ux + (tech.unlocks().isEmpty() ? 0 : 4), unlockY + 4, TEXT, false);
            }
        }

        // 右边：进度和按钮
        int buttonX = this.width - BUTTON_WIDTH - 12;
        int buttonY = top + DETAIL - BUTTON_HEIGHT - 12;
        if (this.isLab(id) && status != Status.DONE) {
            int needed = state.pointsNeeded(this.tree, id);
            Component points = Component.translatable("gui.universe.tech_tree.points", state.progress(id), needed);
            graphics.text(this.font, points, buttonX, top + 12, TEXT, false);
            MachineScreen.bar(graphics, buttonX, top + 24, BUTTON_WIDTH, 4, (float) state.progress(id) / needed,
                    status == Status.CURRENT ? ACCENT : DIM);
        }
        Action action = this.action(state, id, status);
        if (action != null) {
            this.button(graphics, buttonX, buttonY, action.label, action.enabled, mouseX, mouseY);
        }
        if (this.isLab(id) && status != Status.DONE) {
            this.button(graphics, buttonX - BUTTON_WIDTH - 6, buttonY, this.enqueueLabel(state, id), this.canEnqueue(state, id), mouseX, mouseY);
        }
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
        graphics.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, enabled ? BORDER : LOCKED);
        graphics.fill(x + 1, y + 1, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 1, !enabled ? 0xFF0C151B : hovered ? 0xFF1F4A5A : 0xFF12303B);
        graphics.centeredText(this.font, label, x + BUTTON_WIDTH / 2, y + 5, enabled ? TEXT : DIM);
    }

    private boolean canEnqueue(ResearchState state, String id) {
        return state.canEnqueue(this.tree, id, Research.queueLimit(state));
    }

    private Component enqueueLabel(ResearchState state, String id) {
        if (state.queue().size() >= Research.queueLimit(state)) {
            return Component.translatable("gui.universe.tech_tree.queue_full");
        }
        return Component.translatable("gui.universe.tech_tree.enqueue");
    }

    // ---- 研究队列

    private int queueRows() {
        return Math.max(1, (this.areaBottom() - this.areaTop() - QUEUE_TOP - 20) / QUEUE_ROW);
    }

    /** 右边的研究队列：标题、钉到仪表盘，每一项的图标、名字、往前、往后、去掉，最下面清空。 */
    private void queue(GuiGraphicsExtractor graphics, ResearchState state, int mouseX, int mouseY) {
        int x0 = this.treeRight();
        int y0 = this.areaTop();
        int y1 = this.areaBottom();
        graphics.fill(x0, y0, this.width, y1, PANEL);
        graphics.fill(x0, y0, x0 + 1, y1, BORDER);
        List<String> queue = state.queue();
        int limit = Research.queueLimit(state);
        graphics.text(this.font, Component.translatable("gui.universe.tech_tree.queue", queue.size(), limit), x0 + 6, y0 + 6, ACCENT, false);
        Component pin = Component.translatable("gui.universe.dashboard.pin_short");
        int pinWidth = this.font.width(pin) + 6;
        this.smallButton(graphics, this.width - pinWidth - 4, y0 + 4, pinWidth, pin, true, mouseX, mouseY);
        if (queue.isEmpty()) {
            int lineY = y0 + QUEUE_TOP + 6;
            for (FormattedCharSequence line : TextWrap.split(this.font, Component.translatable("gui.universe.tech_tree.queue_empty"),
                    QUEUE_WIDTH - 12)) {
                graphics.text(this.font, line, x0 + 6, lineY, DIM, false);
                lineY += 10;
            }
            return;
        }
        int rows = this.queueRows();
        this.queueScroll = Math.max(0, Math.min(this.queueScroll, queue.size() - rows));
        List<int[]> dimmed = new ArrayList<>();
        for (int r = 0; r < rows && this.queueScroll + r < queue.size(); r++) {
            int i = this.queueScroll + r;
            String id = queue.get(i);
            int y = y0 + QUEUE_TOP + r * QUEUE_ROW;
            boolean blocked = !state.available(this.tree, id);
            if (id.equals(this.selected)) {
                graphics.fill(x0 + 2, y, this.width - 2, y + QUEUE_ROW - 2, 0xFF173542);
            }
            graphics.fill(x0 + 4, y + 1, x0 + 22, y + 19, INSET);
            graphics.fakeItem(this.icon(id), x0 + 5, y + 2);
            if (blocked) {
                dimmed.add(new int[] {x0 + 5, y + 2});
            }
            int level = state.plannedLevel(this.tree, i);
            Component name = this.upgrade(id) != null ? Research.name(id, level) : Research.name(id);
            int textWidth = QUEUE_WIDTH - 26 - 3 * (QUEUE_BUTTON + 1) - 6;
            graphics.text(this.font, this.font.plainSubstrByWidth((i + 1) + ". " + name.getString(), textWidth), x0 + 25, y + 2,
                    blocked ? DIM : TEXT, false);
            graphics.text(this.font, Component.translatable(blocked ? "gui.universe.tech_tree.queue_waiting" : "gui.universe.tech_tree.queue_ready"),
                    x0 + 25, y + 11, blocked ? WARN : GOOD, false);
            int bx = this.width - 4 - 3 * (QUEUE_BUTTON + 1);
            this.smallButton(graphics, bx, y + 5, QUEUE_BUTTON, Component.literal("▲"), i > 0, mouseX, mouseY);
            this.smallButton(graphics, bx + QUEUE_BUTTON + 1, y + 5, QUEUE_BUTTON, Component.literal("▼"), i < queue.size() - 1, mouseX, mouseY);
            this.smallButton(graphics, bx + 2 * (QUEUE_BUTTON + 1), y + 5, QUEUE_BUTTON, Component.literal("×"), true, mouseX, mouseY);
        }
        if (!dimmed.isEmpty()) {
            graphics.nextStratum();
            for (int[] cell : dimmed) {
                graphics.fill(cell[0], cell[1], cell[0] + 16, cell[1] + 16, 0xB0070D12);
            }
        }
        if (queue.size() > rows) {
            String more = "+" + (queue.size() - rows - this.queueScroll);
            if (queue.size() - rows - this.queueScroll > 0) {
                graphics.text(this.font, more, x0 + 6, y0 + QUEUE_TOP + rows * QUEUE_ROW, DIM, false);
            }
        }
        Component clear = Component.translatable("gui.universe.tech_tree.queue_clear");
        int clearWidth = this.font.width(clear) + 8;
        this.smallButton(graphics, this.width - clearWidth - 4, y1 - 16, clearWidth, clear, true, mouseX, mouseY);
    }

    private void smallButton(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12;
        graphics.fill(x, y, x + width, y + 12, enabled ? hovered ? ACCENT : BORDER : LOCKED);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 11, hovered ? 0xFF1F4A5A : 0xFF0C151B);
        graphics.centeredText(this.font, label, x + width / 2 + 1, y + 2, enabled ? TEXT : DIM);
    }

    /** 点研究队列那一栏；点到了返回 true。 */
    private boolean clickQueue(ResearchState state, double mouseX, double mouseY) {
        int x0 = this.treeRight();
        int y0 = this.areaTop();
        int y1 = this.areaBottom();
        if (mouseX < x0 || mouseY < y0 || mouseY >= y1) {
            return false;
        }
        Component pin = Component.translatable("gui.universe.dashboard.pin_short");
        int pinWidth = this.font.width(pin) + 6;
        if (hit(mouseX, mouseY, this.width - pinWidth - 4, y0 + 4, pinWidth, 12)) {
            DashboardHud.pin(Dashboard.Pin.of(Dashboard.Kind.RESEARCH, "", "", 0));
            this.click();
            return true;
        }
        List<String> queue = state.queue();
        if (queue.isEmpty()) {
            return true;
        }
        Component clear = Component.translatable("gui.universe.tech_tree.queue_clear");
        int clearWidth = this.font.width(clear) + 8;
        if (hit(mouseX, mouseY, this.width - clearWidth - 4, y1 - 16, clearWidth, 12)) {
            this.send(ResearchActionPayload.Action.QUEUE_CLEAR, "", -1);
            return true;
        }
        int rows = this.queueRows();
        for (int r = 0; r < rows && this.queueScroll + r < queue.size(); r++) {
            int i = this.queueScroll + r;
            String id = queue.get(i);
            int y = y0 + QUEUE_TOP + r * QUEUE_ROW;
            int bx = this.width - 4 - 3 * (QUEUE_BUTTON + 1);
            if (hit(mouseX, mouseY, bx, y + 5, QUEUE_BUTTON, 12)) {
                if (i > 0) {
                    this.send(ResearchActionPayload.Action.QUEUE_UP, id, i);
                }
                return true;
            }
            if (hit(mouseX, mouseY, bx + QUEUE_BUTTON + 1, y + 5, QUEUE_BUTTON, 12)) {
                if (i < queue.size() - 1) {
                    this.send(ResearchActionPayload.Action.QUEUE_DOWN, id, i);
                }
                return true;
            }
            if (hit(mouseX, mouseY, bx + 2 * (QUEUE_BUTTON + 1), y + 5, QUEUE_BUTTON, 12)) {
                this.send(ResearchActionPayload.Action.QUEUE_REMOVE, id, i);
                return true;
            }
            if (hit(mouseX, mouseY, x0 + 2, y, QUEUE_WIDTH - 4, QUEUE_ROW - 2) && this.known(id)) {
                this.selected = id;
                this.focus(id);
                this.click();
                return true;
            }
        }
        return true;
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void send(ResearchActionPayload.Action action, String id, int index) {
        ClientPacketDistributor.sendToServer(new ResearchActionPayload(action, id, index));
        this.click();
    }

    /** 让一个科技落在画面里。 */
    private void focus(String id) {
        int x = this.cardX(id);
        int y = this.cardY(id);
        if (x < 0 || x + CARD_WIDTH > this.treeRight()) {
            this.panX += this.treeRight() / 2.0 - (x + CARD_WIDTH / 2.0);
        }
        if (y < this.areaTop() || y + CARD_HEIGHT > this.areaBottom()) {
            this.panY += (this.areaTop() + this.areaBottom()) / 2.0 - (y + CARD_HEIGHT / 2.0);
        }
        this.clampPan();
    }

    /** 升级科技的详情：下一级要的矩阵和点数；现在的效果 → 下一级的效果。 */
    private void upgradeDetail(GuiGraphicsExtractor graphics, ResearchState state, Upgrade upgrade, int iconsX, int costY, int effectY,
            int mouseX, int mouseY) {
        int level = state.level(upgrade.id());
        boolean maxed = state.isMaxed(upgrade);
        if (maxed) {
            graphics.text(this.font, Component.translatable("gui.universe.tech_tree.none"), iconsX, costY + 4, DIM, false);
        } else {
            int cx = iconsX;
            for (String matrix : upgrade.matrices(level + 1)) {
                cx = this.itemIcon(graphics, new ItemStack(Research.item(matrix)), cx, costY, mouseX, mouseY);
            }
            graphics.text(this.font, Component.translatable("gui.universe.tech_tree.per_point", upgrade.points(level + 1)), cx + 4, costY + 4,
                    TEXT, false);
        }
        Component now = level > 0 ? Research.upgradeEffect(upgrade.id(), level) : Component.translatable("gui.universe.tech_tree.none");
        int ex = iconsX;
        graphics.text(this.font, now, ex, effectY + 4, level > 0 ? TEXT : DIM, false);
        if (!maxed) {
            ex += this.font.width(now) + 4;
            graphics.text(this.font, "→", ex, effectY + 4, DIM, false);
            ex += this.font.width("→") + 4;
            graphics.text(this.font, Research.upgradeEffect(upgrade.id(), level + 1), ex, effectY + 4, GOOD, false);
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
        String id = this.hoveredId;
        if (id == null) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(this.displayName(state, id).withStyle(ChatFormatting.AQUA));
        if (this.isLab(id) && this.status(state, id) != Status.DONE) {
            lines.add(Component.translatable("gui.universe.tech_tree.points", state.progress(id), state.pointsNeeded(this.tree, id))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.universe.tech_tree.click").withStyle(ChatFormatting.DARK_GRAY));
        if (this.isLab(id) && this.status(state, id) != Status.DONE) {
            lines.add(Component.translatable("gui.universe.tech_tree.shift_click").withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ---- 操作

    /** 详情里的按钮。 */
    private record Action(Component label, boolean enabled, ResearchActionPayload.@Nullable Action payload) {
    }

    private @Nullable Action action(ResearchState state, String id, Status status) {
        return switch (status) {
            case DONE -> null;
            case LOCKED -> new Action(Component.translatable("gui.universe.tech_tree.locked_button"), false, null);
            case CURRENT -> new Action(Component.translatable("gui.universe.tech_tree.stop"), true, ResearchActionPayload.Action.STOP);
            case AVAILABLE -> {
                Tech tech = this.tree.get(id).orElse(null);
                if (this.isLab(id) || tech == null) {
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
        String id = this.selected;
        if (id != null && this.known(id)) {
            int buttonX = this.width - BUTTON_WIDTH - 12;
            int buttonY = this.areaBottom() + DETAIL - BUTTON_HEIGHT - 12;
            Status status = this.status(state, id);
            Action action = this.action(state, id, status);
            if (action != null && action.enabled && action.payload != null && mouseX >= buttonX && mouseX < buttonX + BUTTON_WIDTH
                    && mouseY >= buttonY && mouseY < buttonY + BUTTON_HEIGHT) {
                ClientPacketDistributor.sendToServer(new ResearchActionPayload(action.payload, id));
                this.click();
                return true;
            }
            if (this.isLab(id) && status != Status.DONE && this.canEnqueue(state, id)
                    && hit(mouseX, mouseY, buttonX - BUTTON_WIDTH - 6, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
                this.send(ResearchActionPayload.Action.QUEUE_ADD, id, -1);
                return true;
            }
        }
        if (this.clickQueue(state, mouseX, mouseY)) {
            return true;
        }
        if (mouseY >= this.areaTop() && mouseY < this.areaBottom()) {
            String hit = this.nodeAt(mouseX, mouseY);
            if (hit != null) {
                this.selected = hit;
                // 按住 Shift 点：直接排进队列
                if (event.hasShiftDown() && this.isLab(hit) && this.canEnqueue(state, hit)) {
                    this.send(ResearchActionPayload.Action.QUEUE_ADD, hit, -1);
                } else {
                    this.click();
                }
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
        if (x >= this.treeRight() && y >= this.areaTop() && y < this.areaBottom()) {
            this.queueScroll = Math.max(0, this.queueScroll - (int) Math.signum(scrollY));
            return true;
        }
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
