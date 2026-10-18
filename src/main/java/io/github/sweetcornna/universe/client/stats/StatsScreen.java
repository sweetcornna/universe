package io.github.sweetcornna.universe.client.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.network.StatsRequestPayload;
import io.github.sweetcornna.universe.network.StatsSnapshotPayload;
import io.github.sweetcornna.universe.world.planet.Planets;
import io.github.sweetcornna.universe.world.space.SpaceDimensions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 生产统计面板（ADR-0015，默认 O 键）：选星球（或全部）和时间范围，看每种物品每分钟的产出和消耗，以及电网的发电、
 * 用电和满足率。打开时和之后每两秒向服务端要一份快照。
 */
public class StatsScreen extends net.minecraft.client.gui.screens.Screen {
    private static final int WIDTH = 330;
    private static final int ROW_HEIGHT = 18;
    private static final int TABLE_Y = 96;
    private static final int RANGE_WIDTH = 46;
    private static final int NAME_X = 28;
    private static final int PRODUCED_RIGHT = 212;
    private static final int CONSUMED_RIGHT = 262;
    private static final int NET_RIGHT = 312;
    private static final int PRODUCED_COLOR = 0xFF5BD47A;
    private static final int CONSUMED_COLOR = 0xFFE89A3C;
    private static final int REFRESH_TICKS = 40;

    private enum Sort {
        VOLUME, NAME, PRODUCED, CONSUMED, NET
    }

    /** 选中的维度；空字符串是全部。 */
    private String dimension;
    private int range;
    private Sort sort = Sort.VOLUME;
    private int scroll;
    private int refresh;
    private int left;
    private int top;
    private int panelHeight;

    public StatsScreen(String dimension, int range) {
        super(Component.translatable("gui.universe.stats.title"));
        this.dimension = dimension;
        this.range = range;
    }

    @Override
    protected void init() {
        this.panelHeight = Math.min(this.height - 20, 300);
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.request();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void request() {
        this.refresh = REFRESH_TICKS;
        ClientPacketDistributor.sendToServer(new StatsRequestPayload(this.dimension, this.range));
        StatsClient.remember(this.dimension, this.range);
    }

    @Override
    public void tick() {
        if (--this.refresh <= 0) {
            this.request();
        }
    }

    /** 快照对得上现在选的星球和时间范围时才用，免得切换的一瞬间显示旧的。 */
    private Optional<StatsSnapshotPayload> snapshot() {
        return StatsClient.snapshot().filter(s -> s.dimension().equals(this.dimension) && s.range() == this.range);
    }

    // ---- 排版

    private int visibleRows() {
        return Math.max(1, (this.panelHeight - TABLE_Y - 8) / ROW_HEIGHT);
    }

    private List<StatsSnapshotPayload.Row> rows(StatsSnapshotPayload snapshot) {
        List<StatsSnapshotPayload.Row> rows = new ArrayList<>(snapshot.rows());
        switch (this.sort) {
            case NAME -> rows.sort(Comparator.comparing(row -> name(row.item())));
            case PRODUCED -> rows.sort(Comparator.comparingDouble((StatsSnapshotPayload.Row row) -> row.produced()).reversed());
            case CONSUMED -> rows.sort(Comparator.comparingDouble((StatsSnapshotPayload.Row row) -> row.consumed()).reversed());
            case NET -> rows.sort(Comparator.comparingDouble(row -> row.consumed() - row.produced()));
            default -> {
            }
        }
        return rows;
    }

    private static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.BARRIER);
    }

    private static String name(String id) {
        return Component.translatable(item(id).getDescriptionId()).getString();
    }

    /** 维度的显示名：全部、星球的名字（含主世界的母星）、"某某星系 太空"，别的维度用它的 id。 */
    static Component dimensionName(String dimension) {
        if (dimension.isEmpty()) {
            return Component.translatable("gui.universe.stats.all");
        }
        Identifier id = Identifier.tryParse(dimension);
        Optional<Galaxy> galaxy = ClientGalaxy.galaxy();
        if (id == null || galaxy.isEmpty()) {
            return Component.literal(dimension);
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        OptionalInt space = SpaceDimensions.system(key);
        if (space.isPresent()) {
            return galaxy.get().system(space.getAsInt())
                    .<Component>map(system -> Component.translatable("gui.universe.stats.space", GalaxyText.system(system)))
                    .orElse(Component.literal(dimension));
        }
        Optional<BodyId> body = Planets.bodyId(key);
        if (body.isPresent()) {
            return galaxy.get().body(body.get()).<Component>map(planet -> Component.literal(GalaxyText.planet(planet)))
                    .orElse(Component.literal(dimension));
        }
        return Component.literal(id.getPath());
    }

    /** 每分钟的数：小的留一位小数，大的用 k、M。 */
    static String rate(double value) {
        double size = Math.abs(value);
        if (size < 0.05) {
            return "0";
        }
        if (size < 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        if (size < 10_000) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (size < 10_000_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000);
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int x = this.left;
        int y = this.top;
        graphics.fill(x, y, x + WIDTH, y + this.panelHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + this.panelHeight - 1, MachineScreen.PANEL);
        graphics.text(this.font, this.title, x + 8, y + 6, MachineScreen.ACCENT, false);
        graphics.fill(x + 6, y + 16, x + WIDTH - 6, y + 17, 0x602E7A92);
        int mx = mouseX - x;
        int my = mouseY - y;
        this.selector(graphics, x, y, mx, my);
        Optional<StatsSnapshotPayload> snapshot = this.snapshot();
        if (snapshot.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.waiting"), x + WIDTH / 2, y + 100, MachineScreen.DIM);
            return;
        }
        this.power(graphics, x, y, snapshot.get());
        this.table(graphics, x, y, mx, my, mouseX, mouseY, snapshot.get());
    }

    /** 第一行：星球的左右箭头和名字，右边三个时间范围。 */
    private void selector(GuiGraphicsExtractor graphics, int x, int y, int mx, int my) {
        int rowY = y + 21;
        int selectorRight = WIDTH - 8 - 3 * RANGE_WIDTH - 8;
        this.button(graphics, x + 8, rowY, 14, Component.literal("<"), false, inside(mx, my, 8, 21, 14, 14));
        this.button(graphics, x + selectorRight - 14, rowY, 14, Component.literal(">"), false, inside(mx, my, selectorRight - 14, 21, 14, 14));
        Component name = dimensionName(this.dimension);
        int nameWidth = selectorRight - 14 - 26;
        String shown = this.font.plainSubstrByWidth(name.getString(), nameWidth);
        graphics.centeredText(this.font, Component.literal(shown), x + (8 + 14 + selectorRight - 14) / 2, rowY + 3, MachineScreen.TEXT);
        for (int i = 0; i < 3; i++) {
            int bx = WIDTH - 8 - (3 - i) * RANGE_WIDTH;
            this.button(graphics, x + bx, rowY, RANGE_WIDTH - 2, Component.translatable("gui.universe.stats.range." + i), this.range == i,
                    inside(mx, my, bx, 21, RANGE_WIDTH - 2, 14));
        }
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, boolean selected, boolean hovered) {
        int border = selected ? MachineScreen.ACCENT : hovered ? 0xFF4FA3C0 : 0xFF2A4A58;
        graphics.fill(x, y, x + width, y + 14, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 13, selected ? 0xFF1C4352 : hovered ? 0xFF16303B : MachineScreen.INSET);
        graphics.centeredText(this.font, label, x + width / 2, y + 3, selected ? MachineScreen.TEXT : MachineScreen.DIM);
    }

    /** 电网：左边发电能力、用电需求、充电三行，右边满足率和一根条。 */
    private void power(GuiGraphicsExtractor graphics, int x, int y, StatsSnapshotPayload snapshot) {
        int boxY = y + 40;
        graphics.fill(x + 8, boxY, x + WIDTH - 8, boxY + 40, MachineScreen.INSET);
        if (snapshot.generation() <= 0 && snapshot.demand() <= 0 && snapshot.charging() <= 0) {
            graphics.text(this.font, Component.translatable("gui.universe.stats.no_power"), x + 14, boxY + 16, MachineScreen.DIM, false);
            return;
        }
        String generation = MachineScreen.watts(Math.round(snapshot.generation() * 10));
        String demand = MachineScreen.watts(Math.round(snapshot.demand() * 10));
        String charging = MachineScreen.watts(Math.round(snapshot.charging() * 10));
        graphics.text(this.font, Component.translatable("gui.universe.stats.generation", generation), x + 14, boxY + 4, PRODUCED_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.universe.stats.demand", demand), x + 14, boxY + 16, CONSUMED_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.universe.stats.charging", charging), x + 14, boxY + 28,
                snapshot.charging() > 0 ? MachineScreen.ACCENT : MachineScreen.DIM, false);
        double satisfaction = snapshot.satisfaction();
        int color = satisfaction >= 0.999 ? MachineScreen.GOOD : satisfaction >= 0.5 ? MachineScreen.WARN : MachineScreen.BAD;
        Component label = Component.translatable("gui.universe.stats.satisfaction", Math.round(satisfaction * 100));
        int labelX = x + 180;
        graphics.text(this.font, label, labelX, boxY + 10, color, false);
        int barRight = x + WIDTH - 14;
        graphics.fill(labelX, boxY + 23, barRight, boxY + 29, 0xFF1A2A33);
        graphics.fill(labelX, boxY + 23, labelX + (int) Math.round((barRight - labelX) * Math.min(1, satisfaction)), boxY + 29, color);
    }

    private void table(GuiGraphicsExtractor graphics, int x, int y, int mx, int my, int mouseX, int mouseY, StatsSnapshotPayload snapshot) {
        int headerY = y + TABLE_Y - 11;
        this.header(graphics, x + NAME_X, headerY, "gui.universe.stats.item", Sort.NAME, false, mx, my);
        this.header(graphics, x + PRODUCED_RIGHT, headerY, "gui.universe.stats.produced", Sort.PRODUCED, true, mx, my);
        this.header(graphics, x + CONSUMED_RIGHT, headerY, "gui.universe.stats.consumed", Sort.CONSUMED, true, mx, my);
        this.header(graphics, x + NET_RIGHT, headerY, "gui.universe.stats.net", Sort.NET, true, mx, my);
        List<StatsSnapshotPayload.Row> rows = this.rows(snapshot);
        int visible = this.visibleRows();
        this.scroll = Math.max(0, Math.min(this.scroll, rows.size() - visible));
        if (rows.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.stats.empty"), x + WIDTH / 2, y + TABLE_Y + 20, MachineScreen.TEXT);
            int lineY = y + TABLE_Y + 34;
            for (var line : this.font.split(Component.translatable("gui.universe.stats.empty_hint"), WIDTH - 40)) {
                graphics.centeredText(this.font, line, x + WIDTH / 2, lineY, MachineScreen.DIM);
                lineY += 10;
            }
            return;
        }
        float most = 0;
        for (StatsSnapshotPayload.Row row : rows) {
            most = Math.max(most, Math.max(row.produced(), row.consumed()));
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < visible && this.scroll + i < rows.size(); i++) {
            StatsSnapshotPayload.Row row = rows.get(this.scroll + i);
            int rowY = y + TABLE_Y + i * ROW_HEIGHT;
            if (i % 2 == 1) {
                graphics.fill(x + 6, rowY - 1, x + WIDTH - 10, rowY + ROW_HEIGHT - 1, 0x10FFFFFF);
            }
            ItemStack stack = new ItemStack(item(row.item()));
            graphics.fakeItem(stack, x + 8, rowY);
            if (inside(mx, my, 6, rowY - y - 1, WIDTH - 16, ROW_HEIGHT)) {
                graphics.fill(x + 6, rowY - 1, x + WIDTH - 10, rowY + ROW_HEIGHT - 1, 0x20FFFFFF);
                hovered = stack;
            }
            String name = this.font.plainSubstrByWidth(stack.getHoverName().getString(), PRODUCED_RIGHT - 46 - NAME_X);
            graphics.text(this.font, name, x + NAME_X, rowY + 2, MachineScreen.TEXT, false);
            // 名字下面两道细条：产出（绿）、消耗（橙），按表里最大的那个数归一
            int barWidth = PRODUCED_RIGHT - 50 - NAME_X;
            graphics.fill(x + NAME_X, rowY + 12, x + NAME_X + barWidth, rowY + 16, 0xFF1A2A33);
            graphics.fill(x + NAME_X, rowY + 12, x + NAME_X + Math.round(barWidth * row.produced() / most), rowY + 14, PRODUCED_COLOR);
            graphics.fill(x + NAME_X, rowY + 14, x + NAME_X + Math.round(barWidth * row.consumed() / most), rowY + 16, CONSUMED_COLOR);
            this.right(graphics, rate(row.produced()), x + PRODUCED_RIGHT, rowY + 5, row.produced() > 0 ? PRODUCED_COLOR : MachineScreen.DIM);
            this.right(graphics, rate(row.consumed()), x + CONSUMED_RIGHT, rowY + 5, row.consumed() > 0 ? CONSUMED_COLOR : MachineScreen.DIM);
            double net = row.produced() - row.consumed();
            String netText = (net > 0.05 ? "+" : "") + rate(net);
            this.right(graphics, netText, x + NET_RIGHT, rowY + 5, net > 0.05 ? MachineScreen.GOOD : net < -0.05 ? MachineScreen.BAD : MachineScreen.DIM);
        }
        // 滚动条
        if (rows.size() > visible) {
            int trackTop = y + TABLE_Y - 1;
            int trackHeight = visible * ROW_HEIGHT;
            int thumb = Math.max(10, trackHeight * visible / rows.size());
            int thumbY = trackTop + (trackHeight - thumb) * this.scroll / (rows.size() - visible);
            graphics.fill(x + WIDTH - 8, trackTop, x + WIDTH - 5, trackTop + trackHeight, 0xFF1A2A33);
            graphics.fill(x + WIDTH - 8, thumbY, x + WIDTH - 5, thumbY + thumb, 0xFF4FA3C0);
        }
        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
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

    private void right(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        graphics.text(this.font, text, right - this.font.width(text), y, color, false);
    }

    private static boolean inside(int mx, int my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    // ---- 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x() - this.left;
        int my = (int) event.y() - this.top;
        int selectorRight = WIDTH - 8 - 3 * RANGE_WIDTH - 8;
        if (inside(mx, my, 8, 21, 14, 14)) {
            this.cycle(-1);
            return true;
        }
        if (inside(mx, my, selectorRight - 14, 21, 14, 14)) {
            this.cycle(1);
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (inside(mx, my, WIDTH - 8 - (3 - i) * RANGE_WIDTH, 21, RANGE_WIDTH - 2, 14) && this.range != i) {
                this.range = i;
                this.click();
                this.request();
                return true;
            }
        }
        int headerY = TABLE_Y - 12;
        if (my >= headerY && my < headerY + 11) {
            Sort clicked = mx < PRODUCED_RIGHT - 50 ? Sort.NAME : mx < PRODUCED_RIGHT + 4 ? Sort.PRODUCED
                    : mx < CONSUMED_RIGHT + 4 ? Sort.CONSUMED : Sort.NET;
            this.sort = this.sort == clicked ? Sort.VOLUME : clicked;
            this.scroll = 0;
            this.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** 在"全部"和有统计的维度之间轮换。 */
    private void cycle(int step) {
        List<String> options = new ArrayList<>();
        options.add("");
        StatsClient.snapshot().ifPresent(snapshot -> options.addAll(snapshot.dimensions()));
        if (!options.contains(this.dimension)) {
            options.add(this.dimension);
        }
        int index = options.indexOf(this.dimension);
        this.dimension = options.get(Math.floorMod(index + step, options.size()));
        this.scroll = 0;
        this.click();
        this.request();
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        this.scroll -= (int) Math.signum(scrollY) * 2;
        this.scroll = Math.max(0, this.scroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (UniverseKeys.STATS.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
