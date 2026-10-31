package io.github.sweetcornna.universe.client.chem.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.chem.reactor.ReactionBooks;
import io.github.sweetcornna.universe.chem.reactor.ReactorRecipes;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.core.chem.reaction.Catalyst;
import io.github.sweetcornna.universe.core.chem.reaction.ItemCount;
import io.github.sweetcornna.universe.core.chem.reaction.Reaction;
import io.github.sweetcornna.universe.factory.research.Research;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * 化学手册（ADR-0037 §四），和手册（ADR-0018）一个样子：左边按类列出反应，右边一条一页，写反应式、进出的物品、催化剂、
 * 教科书工况、反应热、可不可逆、全服做成过没有。研究了对应科技、或者已经发现了的才列出来，别的写"？？？"和怎么拿到。
 * 最上面一行"怎么用"讲手册和反应釜怎么配合。
 */
public class ChemistryHandbookScreen extends Screen {
    /** 比手册宽一些：左边一栏要放得下反应的短名。 */
    private static final int WIDTH = 364;
    private static final int HEIGHT = 216;
    private static final int LIST_WIDTH = 116;
    private static final int ROW = 12;
    private static final int LIST_TOP = 22;
    private static final int VISIBLE_ROWS = (HEIGHT - 6 - LIST_TOP + 2) / ROW;
    private static final int PAGE_X = LIST_WIDTH + 10;
    private static final int PAGE_WIDTH = WIDTH - PAGE_X - 10;
    private static final int BODY_TOP = 34;
    private static final int LINE = 11;
    private static final int BORDER = 0xFF2A6A8C;
    private static final int BACK = 0xF00A1620;
    private static final int TITLE = 0xFF7FE3FF;
    private static final int TEXT = 0xFFD8E0E8;
    private static final int DIM = 0xFF8FA3B3;
    private static final int HEADING = 0xFFFFE6A0;
    private static final int SLOT = 0xFF16283A;
    private static final int SELECTED = 0x662A6A8C;
    private static final int HOVER = 0x22FFFFFF;
    private static final int GOOD = 0xFF5BD47A;
    private static final int WARN = 0xFFE8C14A;
    private static final String ELLIPSIS = "…";

    /** 上次看的是哪一行、列表滚到哪里，下次打开接着看。 */
    private static int selected = 0;
    private static int listScroll;

    private int left;
    private int top;
    private final List<Row> rows = new ArrayList<>();

    /** 左边一行："怎么用"、一类的标题、一条反应。 */
    private record Row(Reaction.@Nullable Category category, @Nullable Reaction reaction) {
        boolean intro() {
            return this.category == null && this.reaction == null;
        }

        boolean selectable() {
            return this.category == null;
        }
    }

    public ChemistryHandbookScreen() {
        super(Component.translatable("gui.universe.handbook.title"));
        this.rows.add(new Row(null, null));
        for (Reaction.Category category : Reaction.Category.values()) {
            this.rows.add(new Row(category, null));
            for (Reaction reaction : Reaction.values()) {
                if (reaction.category() == category) {
                    this.rows.add(new Row(null, reaction));
                }
            }
        }
    }

    /** 手里的化学手册右键时调。 */
    public static void open() {
        Minecraft.getInstance().gui.setScreen(new ChemistryHandbookScreen());
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        selected = Math.max(0, Math.min(selected, this.rows.size() - 1));
        if (!this.rows.get(selected).selectable()) {
            selected = 0;
        }
        this.reveal();
    }

    private boolean listed(Reaction reaction) {
        return this.minecraft != null && this.minecraft.level != null && ReactionBooks.listed(this.minecraft.level, reaction);
    }

    private static boolean discovered(Reaction reaction) {
        return ReactionBooks.client().discovered(reaction);
    }

    private int maxScroll() {
        return Math.max(0, this.rows.size() - VISIBLE_ROWS);
    }

    private void reveal() {
        if (selected < listScroll) {
            listScroll = selected;
        } else if (selected >= listScroll + VISIBLE_ROWS) {
            listScroll = selected - VISIBLE_ROWS + 1;
        }
        listScroll = Math.max(0, Math.min(listScroll, this.maxScroll()));
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.left;
        int y = this.top;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACK);
        graphics.fill(x + LIST_WIDTH, y + 6, x + LIST_WIDTH + 1, y + HEIGHT - 6, BORDER);
        graphics.text(this.font, this.title, x + 8, y + 8, TITLE, false);
        this.list(graphics, mouseX, mouseY);
        ItemStack hovered = this.page(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
        }
    }

    private void list(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.left;
        int last = Math.min(this.rows.size(), listScroll + VISIBLE_ROWS);
        for (int i = listScroll; i < last; i++) {
            Row row = this.rows.get(i);
            int ry = this.top + LIST_TOP + (i - listScroll) * ROW;
            boolean hovered = row.selectable() && mouseX >= x + 4 && mouseX < x + LIST_WIDTH - 4 && mouseY >= ry - 2 && mouseY < ry + ROW - 2;
            if (i == selected) {
                graphics.fill(x + 4, ry - 2, x + LIST_WIDTH - 4, ry + ROW - 2, SELECTED);
            } else if (hovered) {
                graphics.fill(x + 4, ry - 2, x + LIST_WIDTH - 4, ry + ROW - 2, HOVER);
            }
            if (row.intro()) {
                graphics.text(this.font, Component.translatable("gui.universe.handbook.intro"), x + 8, ry, i == selected ? TITLE : TEXT, false);
            } else if (row.category() != null) {
                graphics.text(this.font, categoryName(row.category()), x + 8, ry, HEADING, false);
            } else {
                Reaction reaction = row.reaction();
                boolean listed = this.listed(reaction);
                Component name = listed ? Component.translatable("gui.universe.chemical_reactor.short." + reaction.id())
                        : Component.translatable("gui.universe.handbook.unknown");
                graphics.text(this.font, this.fit(name, LIST_WIDTH - 12 - 18), x + 12, ry, i == selected ? TITLE : listed ? TEXT : DIM, false);
                if (discovered(reaction)) {
                    // 做成过的打个绿点
                    graphics.fill(x + LIST_WIDTH - 12, ry + 2, x + LIST_WIDTH - 9, ry + 5, GOOD);
                }
            }
        }
        if (this.maxScroll() > 0) {
            int trackTop = this.top + LIST_TOP - 2;
            int trackHeight = VISIBLE_ROWS * ROW;
            int thumb = Math.max(8, trackHeight * VISIBLE_ROWS / this.rows.size());
            int thumbTop = trackTop + (trackHeight - thumb) * listScroll / this.maxScroll();
            graphics.fill(x + LIST_WIDTH - 3, trackTop, x + LIST_WIDTH - 2, trackTop + trackHeight, SLOT);
            graphics.fill(x + LIST_WIDTH - 3, thumbTop, x + LIST_WIDTH - 2, thumbTop + thumb, BORDER);
        }
    }

    /** 右边一页，返回鼠标停着的物品。 */
    private ItemStack page(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int px = this.left + PAGE_X;
        int y = this.top;
        Row row = this.rows.get(selected);
        int known = 0;
        for (Reaction reaction : Reaction.values()) {
            if (discovered(reaction)) {
                known++;
            }
        }
        Component count = Component.translatable("gui.universe.handbook.count", known, Reaction.values().length);
        graphics.text(this.font, count, this.left + WIDTH - 10 - this.font.width(count), y + 8, DIM, false);
        if (row.intro()) {
            graphics.text(this.font, Component.translatable("gui.universe.handbook.intro"), px, y + 20, HEADING, false);
            this.paragraph(graphics, Component.translatable("gui.universe.handbook.intro.body"), px, y + BODY_TOP, TEXT);
            return ItemStack.EMPTY;
        }
        Reaction reaction = row.reaction();
        graphics.text(this.font, categoryName(reaction.category()), px, y + 8, DIM, false);
        if (!this.listed(reaction)) {
            graphics.text(this.font, Component.translatable("gui.universe.handbook.unknown"), px, y + 20, HEADING, false);
            int cy = this.paragraph(graphics, Component.translatable("gui.universe.handbook.locked"), px, y + BODY_TOP, TEXT);
            this.paragraph(graphics, Component.translatable("gui.universe.handbook.how", Research.name(reaction.research())), px, cy + 4, DIM);
            return ItemStack.EMPTY;
        }
        graphics.text(this.font, this.fit(Component.translatable("reaction.universe." + reaction.id()), PAGE_WIDTH), px, y + 20, HEADING, false);
        // 一批进出的物品，个数就是反应式的系数；催化剂单放在最右边
        ItemStack hovered = ItemStack.EMPTY;
        int cy = y + BODY_TOP;
        int sx = px;
        for (ItemCount input : reaction.inputs()) {
            hovered = this.slot(graphics, input, sx, cy, mouseX, mouseY, hovered);
            sx += 18;
        }
        graphics.text(this.font, reaction.reversible() ? "⇄" : "→", sx + 3, cy + 5, DIM, false);
        sx += 16;
        for (ItemCount output : reaction.outputs()) {
            hovered = this.slot(graphics, output, sx, cy, mouseX, mouseY, hovered);
            sx += 18;
        }
        Catalyst catalyst = reaction.catalyst();
        if (catalyst != null) {
            hovered = this.slot(graphics, new ItemCount(catalyst.item(), 1), px + PAGE_WIDTH - 17, cy, mouseX, mouseY, hovered);
        }
        cy += 24;
        graphics.text(this.font, reaction.equation(), px, cy, TITLE, false);
        cy += LINE + 4;
        Component catalystName = catalyst == null ? Component.translatable("gui.universe.handbook.no_catalyst")
                : new ItemStack(ReactorRecipes.item(catalyst.item())).getHoverName();
        cy = this.line(graphics, Component.translatable("gui.universe.handbook.catalyst", catalystName), px, cy, TEXT);
        cy = this.line(graphics, Component.translatable("gui.universe.handbook.conditions", ReactorRecipes.conditions(reaction)), px, cy, TEXT);
        double heat = reaction.enthalpy() / 1000;
        String kj = String.format(Locale.ROOT, "%.1f", Math.abs(heat));
        cy = this.line(graphics, Component.translatable(heat < 0 ? "gui.universe.handbook.exothermic" : "gui.universe.handbook.endothermic", kj),
                px, cy, TEXT);
        cy = this.line(graphics, Component.translatable(reaction.reversible() ? "gui.universe.handbook.reversible" : "gui.universe.handbook.irreversible"),
                px, cy, TEXT);
        boolean done = discovered(reaction);
        this.paragraph(graphics, Component.translatable(done ? "gui.universe.handbook.discovered" : "gui.universe.handbook.undiscovered"), px, cy + 4,
                done ? GOOD : WARN);
        return hovered;
    }

    private ItemStack slot(GuiGraphicsExtractor graphics, ItemCount item, int x, int y, int mouseX, int mouseY, ItemStack hovered) {
        ItemStack stack = new ItemStack(ReactorRecipes.item(item), item.count());
        graphics.fill(x, y, x + 17, y + 17, SLOT);
        graphics.fakeItem(stack, x, y);
        graphics.itemDecorations(this.font, stack, x, y);
        return mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17 ? stack : hovered;
    }

    /** 一行，放不下的截掉；返回下一行的 y。 */
    private int line(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(this.font, this.fit(text, PAGE_WIDTH), x, y, color, false);
        return y + LINE;
    }

    /** 一段，按页宽换行；返回下一行的 y。 */
    private int paragraph(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : TextWrap.split(this.font, text, PAGE_WIDTH)) {
            graphics.text(this.font, line, x, y, color, false);
            y += this.font.lineHeight;
        }
        return y;
    }

    /** 放不下时截掉，末尾加省略号。 */
    private FormattedCharSequence fit(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText cut = FormattedText.composite(this.font.substrByWidth(text, width - this.font.width(ELLIPSIS)), Component.literal(ELLIPSIS));
        return Language.getInstance().getVisualOrder(cut);
    }

    private static Component categoryName(Reaction.Category category) {
        return Component.translatable("reaction_category.universe." + category.id());
    }

    // ---- 操作

    private void select(int index) {
        if (index >= 0 && index < this.rows.size() && this.rows.get(index).selectable()) {
            selected = index;
            this.reveal();
        }
    }

    /** 往上或往下挪到下一个能选的行（跳过类名）。 */
    private void step(int delta) {
        int index = selected + delta;
        while (index >= 0 && index < this.rows.size() && !this.rows.get(index).selectable()) {
            index += delta;
        }
        this.select(index);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= this.left + 4 && event.x() < this.left + LIST_WIDTH - 4) {
            int row = (int) Math.floor((event.y() - (this.top + LIST_TOP - 2)) / ROW);
            if (row >= 0 && row < VISIBLE_ROWS && listScroll + row < this.rows.size()) {
                this.select(listScroll + row);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mouseX >= this.left && mouseX < this.left + LIST_WIDTH && this.maxScroll() > 0) {
            listScroll = Math.max(0, Math.min(listScroll + (scrollY < 0 ? 1 : -1), this.maxScroll()));
            return true;
        }
        if (scrollY != 0) {
            this.step(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_DOWN, InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> this.step(1);
            case InputConstants.KEY_UP, InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> this.step(-1);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
