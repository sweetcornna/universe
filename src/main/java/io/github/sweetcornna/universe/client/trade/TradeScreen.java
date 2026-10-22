package io.github.sweetcornna.universe.client.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.civ.ClientCiv;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.TradeCategory;
import io.github.sweetcornna.universe.network.TradeActionPayload;
import io.github.sweetcornna.universe.network.TradeStatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 交易界面（ADR-0017）：上面是空间站所在的恒星系、经济和财富、玩家的信用点；"卖出"页列出背包里能卖的东西，
 * "购买"页列出空间站的货。点一行交易一件，Shift 一组，Ctrl 全部卖掉；价钱都是服务端算好发来的。
 */
public class TradeScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int ROW_HEIGHT = 20;
    private static final int TABLE_Y = 70;
    private static final int TAB_WIDTH = 72;
    private static final int NAME_X = 28;
    private static final int COUNT_RIGHT = 214;
    private static final int PRICE_RIGHT = 266;
    private static final int TOTAL_RIGHT = 326;
    private static final int CREDIT_COLOR = 0xFFF2C94C;
    private static final int DEMAND_COLOR = MachineScreen.GOOD;
    private static final int SUPPLY_COLOR = 0xFFE89A3C;
    private static final int[] WEALTH_COLORS = {0xFF9AA4AA, MachineScreen.TEXT, CREDIT_COLOR};

    private TradeStatePayload state;
    private boolean buyTab;
    private int scroll;
    private int left;
    private int top;
    private int panelHeight;

    public TradeScreen(TradeStatePayload state, boolean buyTab) {
        super(Component.translatable("gui.universe.trade.title"));
        this.state = state;
        this.buyTab = buyTab;
    }

    void update(TradeStatePayload state) {
        this.state = state;
    }

    @Override
    protected void init() {
        this.panelHeight = Math.min(this.height - 20, 290);
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Economy.Type type() {
        Economy.Type[] types = Economy.Type.values();
        return types[Math.floorMod(this.state.economy(), types.length)];
    }

    private Economy.Wealth wealth() {
        Economy.Wealth[] wealths = Economy.Wealth.values();
        return wealths[Math.floorMod(this.state.wealth(), wealths.length)];
    }

    private static TradeCategory category(TradeStatePayload.Row row) {
        TradeCategory[] categories = TradeCategory.values();
        return categories[Math.floorMod(row.category(), categories.length)];
    }

    private List<TradeStatePayload.Row> rows() {
        return this.buyTab ? this.state.buy() : this.state.sell();
    }

    private int visibleRows() {
        return Math.max(1, (this.panelHeight - TABLE_Y - 34) / ROW_HEIGHT);
    }

    // ---- 数字

    /** 信用点：千分位。 */
    public static String credits(long credits) {
        return String.format(Locale.ROOT, "%,d", credits);
    }

    /** 单价：十以下留一位小数。 */
    static String price(double price) {
        if (price < 9.95) {
            return String.format(Locale.ROOT, "%.1f", price);
        }
        return String.format(Locale.ROOT, "%,d", Math.round(price));
    }

    /** 这一行的大类在这里是缺的（收得贵）还是产的（卖得便宜）。 */
    private int categoryColor(TradeCategory category) {
        if (this.type().demands(category)) {
            return DEMAND_COLOR;
        }
        return this.type().supplies(category) ? SUPPLY_COLOR : MachineScreen.DIM;
    }

    private Component categoryLabel(TradeCategory category) {
        Component name = Component.translatable("gui.universe.trade.category." + category.key());
        if (this.type().demands(category)) {
            return Component.translatable("gui.universe.trade.category.demanded", name);
        }
        if (this.type().supplies(category)) {
            return Component.translatable("gui.universe.trade.category.supplied", name);
        }
        return name;
    }

    // ---- 画

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int x = this.left;
        int y = this.top;
        graphics.fill(x, y, x + WIDTH, y + this.panelHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + this.panelHeight - 1, MachineScreen.PANEL);
        int mx = mouseX - x;
        int my = mouseY - y;
        this.header(graphics, x, y);
        this.tabs(graphics, x, y, mx, my);
        TradeStatePayload.Row hovered = this.table(graphics, x, y, mx, my, mouseX, mouseY);
        this.footer(graphics, x, y, hovered);
    }

    /** 恒星系、信用点、经济和财富、缺什么产什么。 */
    private void header(GuiGraphicsExtractor graphics, int x, int y) {
        Component station = ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(this.state.system()))
                .<Component>map(system -> Component.translatable(this.state.outpost() ? "gui.universe.trade.outpost" : "gui.universe.trade.station",
                        GalaxyText.system(system)))
                .orElse(this.title);
        Component credits = Component.translatable("gui.universe.trade.credits", credits(this.state.credits()));
        int creditsWidth = this.font.width(credits);
        // 标题后面跟着主导种族（ADR-0027）
        Optional<Race> race = ClientCiv.dominant(this.state.system());
        Component raceText = race.<Component>map(r -> Component.translatable("gui.universe.trade.race", Civ.raceName(r))).orElse(Component.empty());
        int raceWidth = this.font.width(raceText);
        String name = this.font.plainSubstrByWidth(station.getString(), WIDTH - 30 - creditsWidth - raceWidth);
        graphics.text(this.font, name, x + 8, y + 6, MachineScreen.ACCENT, false);
        race.ifPresent(r -> graphics.text(this.font, raceText, x + 8 + this.font.width(name), y + 6, ARGB.opaque(r.color()), false));
        graphics.text(this.font, credits, x + WIDTH - 8 - creditsWidth, y + 6, CREDIT_COLOR, false);
        Component economy = Component.translatable("gui.universe.trade.economy",
                Component.translatable("gui.universe.trade.economy." + this.type().key()),
                Component.translatable("gui.universe.trade.wealth." + this.wealth().key()));
        graphics.text(this.font, economy, x + 8, y + 19, WEALTH_COLORS[this.wealth().ordinal()], false);
        // 右边：缺的（绿）、产的（橙）
        int right = x + WIDTH - 8;
        Component supplied = this.categories(false);
        if (supplied != null) {
            right -= this.font.width(supplied);
            graphics.text(this.font, supplied, right, y + 19, SUPPLY_COLOR, false);
            right -= 8;
        }
        Component demanded = this.categories(true);
        if (demanded != null) {
            right -= this.font.width(demanded);
            graphics.text(this.font, demanded, right, y + 19, DEMAND_COLOR, false);
        }
        graphics.fill(x + 6, y + 31, x + WIDTH - 6, y + 32, 0x602E7A92);
    }

    /** "缺：零件、能源"或"产：原料"；没有就是 null。 */
    private Component categories(boolean demands) {
        StringJoiner names = new StringJoiner(Component.translatable("gui.universe.trade.separator").getString());
        for (TradeCategory category : TradeCategory.values()) {
            if (demands ? this.type().demands(category) : this.type().supplies(category)) {
                names.add(Component.translatable("gui.universe.trade.category." + category.key()).getString());
            }
        }
        if (names.length() == 0) {
            return null;
        }
        return Component.translatable(demands ? "gui.universe.trade.demands" : "gui.universe.trade.supplies", names.toString());
    }

    private void tabs(GuiGraphicsExtractor graphics, int x, int y, int mx, int my) {
        for (int i = 0; i < 2; i++) {
            int bx = 8 + i * (TAB_WIDTH + 4);
            boolean selected = this.buyTab == (i == 1);
            boolean hovered = inside(mx, my, bx, 37, TAB_WIDTH, 14);
            int count = i == 0 ? this.state.sell().size() : this.state.buy().size();
            Component label = Component.translatable(i == 0 ? "gui.universe.trade.sell" : "gui.universe.trade.buy", count);
            int border = selected ? MachineScreen.ACCENT : hovered ? 0xFF4FA3C0 : 0xFF2A4A58;
            graphics.fill(x + bx, y + 37, x + bx + TAB_WIDTH, y + 51, border);
            graphics.fill(x + bx + 1, y + 38, x + bx + TAB_WIDTH - 1, y + 50, selected ? 0xFF1C4352 : hovered ? 0xFF16303B : MachineScreen.INSET);
            graphics.centeredText(this.font, label, x + bx + TAB_WIDTH / 2, y + 40, selected ? MachineScreen.TEXT : MachineScreen.DIM);
        }
    }

    private TradeStatePayload.Row table(GuiGraphicsExtractor graphics, int x, int y, int mx, int my, int mouseX, int mouseY) {
        int headerY = y + TABLE_Y - 12;
        graphics.text(this.font, Component.translatable("gui.universe.trade.item"), x + NAME_X, headerY, MachineScreen.DIM, false);
        this.right(graphics, Component.translatable(this.buyTab ? "gui.universe.trade.stock" : "gui.universe.trade.held").getString(),
                x + COUNT_RIGHT, headerY, MachineScreen.DIM);
        this.right(graphics, Component.translatable("gui.universe.trade.unit").getString(), x + PRICE_RIGHT, headerY, MachineScreen.DIM);
        this.right(graphics, Component.translatable(this.buyTab ? "gui.universe.trade.stack_total" : "gui.universe.trade.all_total").getString(),
                x + TOTAL_RIGHT, headerY, MachineScreen.DIM);
        List<TradeStatePayload.Row> rows = this.rows();
        int visible = this.visibleRows();
        this.scroll = Math.max(0, Math.min(this.scroll, rows.size() - visible));
        if (rows.isEmpty()) {
            Component empty = Component.translatable(this.buyTab ? "gui.universe.trade.empty_buy" : "gui.universe.trade.empty_sell");
            int lineY = y + TABLE_Y + 24;
            for (var line : TextWrap.split(this.font, empty, WIDTH - 40)) {
                graphics.centeredText(this.font, line, x + WIDTH / 2, lineY, MachineScreen.DIM);
                lineY += 10;
            }
            return null;
        }
        TradeStatePayload.Row hovered = null;
        for (int i = 0; i < visible && this.scroll + i < rows.size(); i++) {
            TradeStatePayload.Row row = rows.get(this.scroll + i);
            int rowY = y + TABLE_Y + i * ROW_HEIGHT;
            if (i % 2 == 1) {
                graphics.fill(x + 6, rowY - 1, x + WIDTH - 10, rowY + ROW_HEIGHT - 1, 0x10FFFFFF);
            }
            boolean over = inside(mx, my, 6, rowY - y - 1, WIDTH - 16, ROW_HEIGHT);
            if (over) {
                graphics.fill(x + 6, rowY - 1, x + WIDTH - 10, rowY + ROW_HEIGHT - 1, 0x20FFFFFF);
                hovered = row;
            }
            boolean available = this.buyTab ? row.one() >= 0 : row.all() >= 1;
            graphics.fakeItem(row.stack(), x + 8, rowY + 1);
            TradeCategory category = category(row);
            int nameColor = available ? MachineScreen.TEXT : MachineScreen.DIM;
            String name = this.font.plainSubstrByWidth(row.stack().getHoverName().getString(), COUNT_RIGHT - 40 - NAME_X);
            graphics.text(this.font, name, x + NAME_X, rowY + 1, nameColor, false);
            graphics.text(this.font, this.categoryLabel(category), x + NAME_X, rowY + 10, this.categoryColor(category), false);
            String count = this.buyTab ? row.count() + "/" + row.capacity() : "×" + row.count();
            int countColor = this.buyTab && row.count() == 0 ? MachineScreen.BAD : MachineScreen.TEXT;
            this.right(graphics, count, x + COUNT_RIGHT, rowY + 5, countColor);
            // 卖出：这里缺的收得贵（绿）；购买：这里产的卖得便宜（绿）
            int priceColor = this.buyTab ? (this.type().supplies(category) ? DEMAND_COLOR : CREDIT_COLOR)
                    : (this.type().demands(category) ? DEMAND_COLOR : this.type().supplies(category) ? SUPPLY_COLOR : CREDIT_COLOR);
            this.right(graphics, price(row.price()), x + PRICE_RIGHT, rowY + 5, available ? priceColor : MachineScreen.DIM);
            double total = this.buyTab ? row.group() : row.all();
            String totalText = total < 0 ? "—" : credits(Math.round(total));
            this.right(graphics, totalText, x + TOTAL_RIGHT, rowY + 5, available ? MachineScreen.TEXT : MachineScreen.DIM);
            if (over && mx >= 6 && mx < 26) {
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, row.stack()));
                graphics.setTooltipForNextFrame(this.font, lines, row.stack().getTooltipImage(), row.stack(), mouseX, mouseY);
            }
        }
        if (rows.size() > visible) {
            int trackTop = y + TABLE_Y - 1;
            int trackHeight = visible * ROW_HEIGHT;
            int thumb = Math.max(10, trackHeight * visible / rows.size());
            int thumbY = trackTop + (trackHeight - thumb) * this.scroll / (rows.size() - visible);
            graphics.fill(x + WIDTH - 8, trackTop, x + WIDTH - 5, trackTop + trackHeight, 0xFF1A2A33);
            graphics.fill(x + WIDTH - 8, thumbY, x + WIDTH - 5, thumbY + thumb, 0xFF4FA3C0);
        }
        return hovered;
    }

    /** 底部两行：指着的那一行点一下、Shift、Ctrl 各是多少钱；上一次交易的结果。 */
    private void footer(GuiGraphicsExtractor graphics, int x, int y, TradeStatePayload.Row hovered) {
        int lineY = y + this.panelHeight - 27;
        graphics.fill(x + 6, lineY - 4, x + WIDTH - 6, lineY - 3, 0x602E7A92);
        Component hint;
        if (hovered == null) {
            hint = Component.translatable(this.buyTab ? "gui.universe.trade.hint_buy" : "gui.universe.trade.hint_sell");
        } else if (this.buyTab) {
            int group = Math.max(1, Math.min(hovered.count(), hovered.stack().getMaxStackSize()));
            hint = hovered.one() < 0 ? Component.translatable("gui.universe.trade.sold_out")
                    : Component.translatable("gui.universe.trade.quote_buy", credits(Math.round(hovered.one())), group, credits(Math.round(hovered.group())));
        } else {
            int group = Math.min(hovered.count(), hovered.stack().getMaxStackSize());
            hint = Component.translatable("gui.universe.trade.quote_sell", credits(Math.round(hovered.one())), group,
                    credits(Math.round(hovered.group())), hovered.count(), credits(Math.round(hovered.all())));
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(hint.getString(), WIDTH - 16), x + 8, lineY, MachineScreen.DIM, false);
        this.state.notice().ifPresent(notice -> graphics.text(this.font, this.font.plainSubstrByWidth(notice.getString(), WIDTH - 16), x + 8,
                lineY + 12, notice.getStyle().getColor() != null ? notice.getStyle().getColor().getValue() | 0xFF000000 : MachineScreen.TEXT,
                false));
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
        for (int i = 0; i < 2; i++) {
            if (inside(mx, my, 8 + i * (TAB_WIDTH + 4), 37, TAB_WIDTH, 14)) {
                if (this.buyTab != (i == 1)) {
                    this.buyTab = i == 1;
                    this.scroll = 0;
                    TradeClient.rememberTab(this.buyTab);
                    this.click();
                }
                return true;
            }
        }
        List<TradeStatePayload.Row> rows = this.rows();
        int visible = this.visibleRows();
        for (int i = 0; i < visible && this.scroll + i < rows.size(); i++) {
            if (inside(mx, my, 6, TABLE_Y + i * ROW_HEIGHT - 1, WIDTH - 16, ROW_HEIGHT)) {
                TradeStatePayload.Row row = rows.get(this.scroll + i);
                int mode = event.hasControlDown() && !this.buyTab ? TradeActionPayload.ALL
                        : event.hasShiftDown() ? TradeActionPayload.STACK : TradeActionPayload.ONE;
                ClientPacketDistributor.sendToServer(new TradeActionPayload(this.buyTab, row.key(), mode));
                this.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
}
