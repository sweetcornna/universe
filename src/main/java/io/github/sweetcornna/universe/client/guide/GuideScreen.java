package io.github.sweetcornna.universe.client.guide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.story.ClientStory;
import io.github.sweetcornna.universe.core.guide.Guide;
import io.github.sweetcornna.universe.core.story.Milestone;
import io.github.sweetcornna.universe.network.EndingRequestPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 手册（ADR-0018）：左边章节，右边分页的正文，标题下面可以有一排物品和一个配方。一页放不下的正文接着排到下一页，
 * 像沉浸工程的手册那样。"日志"一章按剧情进度列出收到的探测器日志，"结局"一章在研究完"任务完成"以后能重看结局。
 */
public class GuideScreen extends Screen {
    private static final int WIDTH = 344;
    private static final int HEIGHT = 216;
    private static final int LIST_WIDTH = 96;
    private static final int ROW = 12;
    private static final int PAGE_X = LIST_WIDTH + 10;
    private static final int PAGE_WIDTH = WIDTH - PAGE_X - 10;
    private static final int BORDER = 0xFF2A6A8C;
    private static final int BACK = 0xF00A1620;
    private static final int TITLE = 0xFF7FE3FF;
    private static final int TEXT = 0xFFD8E0E8;
    private static final int DIM = 0xFF8FA3B3;
    private static final int HEADING = 0xFFFFE6A0;
    private static final int SLOT = 0xFF16283A;
    private static final int SELECTED = 0x662A6A8C;
    private static final int HOVER = 0x22FFFFFF;
    /** 正文从哪儿开始、到哪儿为止（翻页按钮上面）。 */
    private static final int BODY_TOP = 34;
    private static final int BODY_BOTTOM = HEIGHT - 30;
    private static final int ITEMS_HEIGHT = 22;
    private static final int CRAFTING_HEIGHT = 58;
    private static final int MACHINE_HEIGHT = 34;

    /** 上次看到哪一章哪一页，下次打开接着看。 */
    private static int chapterIndex;
    private static int pageIndex;

    private int left;
    private int top;
    private List<PageView> pages = List.of();
    private @Nullable Button previous;
    private @Nullable Button next;

    /** 手册里写的一页。 */
    private record Source(Component title, Component body, List<ItemStack> items, String recipe, boolean ending) {
    }

    /** 排好版、屏幕上的一页；continued 是接着上一页的正文。 */
    private record PageView(Component title, List<FormattedCharSequence> lines, List<ItemStack> items, GuideRecipes.@Nullable View recipe,
            boolean ending, boolean continued) {
    }

    public GuideScreen() {
        super(Component.translatable("gui.universe.guide.title"));
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        chapterIndex = Math.floorMod(chapterIndex, Guide.CHAPTERS.size());
        this.pages = layout(this.font, this.minecraft.level, Guide.CHAPTERS.get(chapterIndex));
        pageIndex = Math.max(0, Math.min(pageIndex, this.pages.size() - 1));
        PageView page = this.pages.get(pageIndex);
        int y = this.top + HEIGHT - 26;
        this.previous = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.turn(-1))
                .bounds(this.left + WIDTH - 10 - 20 * 2 - 4, y, 20, 18).build());
        this.next = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.turn(1))
                .bounds(this.left + WIDTH - 10 - 20, y, 20, 18).build());
        this.previous.active = pageIndex > 0;
        this.next.active = pageIndex < this.pages.size() - 1;
        if (page.ending()) {
            Button replay = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.guide.replay"), button -> {
                ClientPacketDistributor.sendToServer(new EndingRequestPayload());
                this.onClose();
            }).bounds(this.left + PAGE_X, y, 90, 18).build());
            replay.active = ClientStory.reached(Milestone.MISSION_COMPLETE);
        }
    }

    /** 打开手册，翻到某一章某一页（开发脚本截图用）。 */
    public static GuideScreen at(int chapter, int page) {
        chapterIndex = chapter;
        pageIndex = page;
        return new GuideScreen();
    }

    /** 哪些页的正文一页放不下、接到了下一页，哪些章名在左边一栏放不下（开发脚本检查用）。 */
    public static List<String> continued(Font font, Level level) {
        List<String> report = new ArrayList<>();
        for (Guide.Chapter chapter : Guide.CHAPTERS) {
            int width = font.width(Component.translatable("guide.universe." + chapter.id()));
            if (width > LIST_WIDTH - 12) {
                report.add(chapter.id() + " name: " + width + "/" + (LIST_WIDTH - 12));
            }
            List<PageView> list = layout(font, level, chapter);
            for (int i = 0; i < list.size(); i++) {
                PageView page = list.get(i);
                if (page.continued()) {
                    report.add(chapter.id() + " " + i + ": " + page.lines().size() + " lines");
                }
            }
        }
        return report;
    }

    /** 把一章排成屏幕上的页：物品和配方占掉的地方不放正文，剩下的正文接到下一页，下一页开头的空行去掉。 */
    private static List<PageView> layout(Font font, @Nullable Level level, Guide.Chapter chapter) {
        int full = (BODY_BOTTOM - BODY_TOP) / font.lineHeight;
        List<PageView> result = new ArrayList<>();
        for (Source source : sources(chapter)) {
            GuideRecipes.View recipe = level == null ? null : GuideRecipes.find(level, source.recipe());
            int room = BODY_BOTTOM - BODY_TOP - (source.items().isEmpty() ? 0 : ITEMS_HEIGHT) - height(recipe);
            List<FormattedCharSequence> lines = TextWrap.split(font, source.body(), PAGE_WIDTH);
            int at = Math.min(Math.max(0, room / font.lineHeight), lines.size());
            result.add(new PageView(source.title(), lines.subList(0, at), source.items(), recipe, source.ending(), false));
            while (true) {
                while (at < lines.size() && font.width(lines.get(at)) == 0) {
                    at++;
                }
                if (at >= lines.size()) {
                    break;
                }
                int end = Math.min(at + full, lines.size());
                result.add(new PageView(source.title(), lines.subList(at, end), List.of(), null, false, true));
                at = end;
            }
        }
        return result;
    }

    private static int height(GuideRecipes.@Nullable View view) {
        return switch (view) {
            case null -> 0;
            case GuideRecipes.Crafting crafting -> CRAFTING_HEIGHT;
            case GuideRecipes.Machine machine -> MACHINE_HEIGHT;
        };
    }

    /** 一章写了哪些页；"日志"和"结局"现做。 */
    private static List<Source> sources(Guide.Chapter chapter) {
        String base = "guide.universe." + chapter.id();
        if (chapter.id().equals(Guide.LOG)) {
            List<Source> logs = new ArrayList<>();
            ClientStory.milestones().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                    .forEach(entry -> Milestone.byId(entry.getKey()).filter(Milestone::hasLog).ifPresent(milestone -> logs.add(new Source(
                            Component.translatable("story.universe.log." + milestone.id() + ".title"),
                            Component.translatable("gui.universe.guide.log.day", entry.getValue() / 24000L + 1).append("\n\n")
                                    .append(Component.translatable("story.universe.log." + milestone.id())),
                            List.of(), "", false))));
            if (logs.isEmpty()) {
                logs.add(new Source(Component.translatable(base), Component.translatable(base + ".empty"), List.of(), "", false));
            }
            return logs;
        }
        if (chapter.id().equals(Guide.ENDING)) {
            boolean done = ClientStory.reached(Milestone.MISSION_COMPLETE);
            return List.of(new Source(Component.translatable(base), Component.translatable(base + (done ? ".done" : ".locked")), List.of(), "",
                    true));
        }
        List<Source> list = new ArrayList<>();
        for (Guide.Page page : chapter.pages()) {
            String key = base + "." + page.id();
            List<ItemStack> items = page.items().stream().map(GuideScreen::stack).filter(stack -> !stack.isEmpty()).toList();
            list.add(new Source(Component.translatable(key + ".title"), Component.translatable(key), items, page.recipe(), false));
        }
        return list;
    }

    private static ItemStack stack(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.get(key).map(item -> new ItemStack(item)).orElse(ItemStack.EMPTY);
    }

    private void turn(int delta) {
        int target = pageIndex + delta;
        if (target >= 0 && target < this.pages.size()) {
            pageIndex = target;
            this.rebuildWidgets();
        }
    }

    private void open(int chapter) {
        if (chapter != chapterIndex) {
            chapterIndex = chapter;
            pageIndex = 0;
            this.rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.left;
        int y = this.top;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, BACK);
        graphics.fill(x + LIST_WIDTH, y + 6, x + LIST_WIDTH + 1, y + HEIGHT - 6, BORDER);
        graphics.text(this.font, this.title, x + 8, y + 8, TITLE, false);
        // 章节
        for (int i = 0; i < Guide.CHAPTERS.size(); i++) {
            int ry = y + 22 + i * ROW;
            boolean hovered = mouseX >= x + 4 && mouseX < x + LIST_WIDTH - 4 && mouseY >= ry - 2 && mouseY < ry + ROW - 2;
            if (i == chapterIndex) {
                graphics.fill(x + 4, ry - 2, x + LIST_WIDTH - 4, ry + ROW - 2, SELECTED);
            } else if (hovered) {
                graphics.fill(x + 4, ry - 2, x + LIST_WIDTH - 4, ry + ROW - 2, HOVER);
            }
            Component name = Component.translatable("guide.universe." + Guide.CHAPTERS.get(i).id());
            graphics.text(this.font, name, x + 8, ry, i == chapterIndex ? TITLE : TEXT, false);
        }
        // 正文
        PageView page = this.pages.get(pageIndex);
        int px = x + PAGE_X;
        Component chapter = Component.translatable("guide.universe." + Guide.CHAPTERS.get(chapterIndex).id());
        graphics.text(this.font, chapter, px, y + 8, DIM, false);
        graphics.text(this.font, page.title(), px, y + 20, HEADING, false);
        ItemStack hovered = ItemStack.EMPTY;
        int cy = y + BODY_TOP;
        if (!page.items().isEmpty()) {
            for (int i = 0; i < page.items().size(); i++) {
                int ix = px + i * 18;
                ItemStack stack = page.items().get(i);
                graphics.fill(ix, cy, ix + 17, cy + 17, SLOT);
                graphics.fakeItem(stack, ix, cy);
                if (inside(mouseX, mouseY, ix, cy)) {
                    hovered = stack;
                }
            }
            cy += ITEMS_HEIGHT;
        }
        if (page.recipe() != null) {
            hovered = this.recipe(graphics, page.recipe(), px, cy, mouseX, mouseY, hovered);
            cy += height(page.recipe());
        }
        for (FormattedCharSequence line : page.lines()) {
            graphics.text(this.font, line, px, cy, TEXT, false);
            cy += this.font.lineHeight;
        }
        if (this.pages.size() > 1) {
            graphics.text(this.font, Component.translatable("gui.universe.guide.page", pageIndex + 1, this.pages.size()),
                    x + WIDTH - 10 - 20 * 2 - 4 - 34, y + HEIGHT - 21, DIM, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
        }
    }

    /** 画一个配方，返回鼠标停着的物品。 */
    private ItemStack recipe(GuiGraphicsExtractor graphics, GuideRecipes.View view, int x, int y, int mouseX, int mouseY, ItemStack hovered) {
        int tick = (int) (System.currentTimeMillis() / 1000L);
        switch (view) {
            case GuideRecipes.Crafting crafting -> {
                for (int i = 0; i < 9; i++) {
                    int sx = x + i % 3 * 18;
                    int sy = y + i / 3 * 18;
                    graphics.fill(sx, sy, sx + 17, sy + 17, SLOT);
                    ItemStack stack = pick(crafting.grid().get(i), tick);
                    graphics.fakeItem(stack, sx, sy);
                    if (!stack.isEmpty() && inside(mouseX, mouseY, sx, sy)) {
                        hovered = stack;
                    }
                }
                int ox = x + 3 * 18 + 22;
                int oy = y + 18;
                graphics.text(this.font, "→", x + 3 * 18 + 7, oy + 5, DIM, false);
                graphics.fill(ox, oy, ox + 17, oy + 17, SLOT);
                graphics.fakeItem(crafting.result(), ox, oy);
                graphics.itemDecorations(this.font, crafting.result(), ox, oy);
                if (inside(mouseX, mouseY, ox, oy)) {
                    hovered = crafting.result();
                }
                graphics.text(this.font, Component.translatable("gui.universe.guide.crafting"), ox + 24, oy + 5, DIM, false);
            }
            case GuideRecipes.Machine machine -> {
                int sx = x;
                for (List<ItemStack> input : machine.inputs()) {
                    ItemStack stack = pick(input, tick);
                    graphics.fill(sx, y, sx + 17, y + 17, SLOT);
                    graphics.fakeItem(stack, sx, y);
                    graphics.itemDecorations(this.font, stack, sx, y);
                    if (inside(mouseX, mouseY, sx, y)) {
                        hovered = stack;
                    }
                    sx += 18;
                }
                graphics.text(this.font, "→", sx + 3, y + 5, DIM, false);
                sx += 16;
                for (ItemStack output : machine.outputs()) {
                    graphics.fill(sx, y, sx + 17, y + 17, SLOT);
                    graphics.fakeItem(output, sx, y);
                    graphics.itemDecorations(this.font, output, sx, y);
                    if (inside(mouseX, mouseY, sx, y)) {
                        hovered = output;
                    }
                    sx += 18;
                }
                String seconds = machine.seconds() == Math.floor(machine.seconds())
                        ? String.valueOf((int) machine.seconds()) : String.format(java.util.Locale.ROOT, "%.1f", machine.seconds());
                graphics.text(this.font, Component.translatable("gui.universe.guide.machine", machine.machine(), seconds), x, y + 21, DIM, false);
            }
        }
        return hovered;
    }

    private static ItemStack pick(List<ItemStack> options, int tick) {
        return options.isEmpty() ? ItemStack.EMPTY : options.get(Math.floorMod(tick, options.size()));
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 17 && mouseY >= y && mouseY < y + 17;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.x() >= this.left + 4 && event.x() < this.left + LIST_WIDTH - 4) {
            int row = (int) Math.floor((event.y() - (this.top + 20)) / ROW);
            if (row >= 0 && row < Guide.CHAPTERS.size()) {
                this.open(row);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            this.turn(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> this.turn(1);
            case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> this.turn(-1);
            case InputConstants.KEY_DOWN -> this.open(Math.min(chapterIndex + 1, Guide.CHAPTERS.size() - 1));
            case InputConstants.KEY_UP -> this.open(Math.max(chapterIndex - 1, 0));
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
