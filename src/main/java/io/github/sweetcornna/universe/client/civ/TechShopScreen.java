package io.github.sweetcornna.universe.client.civ;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.client.trade.TradeScreen;
import io.github.sweetcornna.universe.core.civ.NpcRole;
import io.github.sweetcornna.universe.core.civ.Race;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.network.TechShopPayloads;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 技术商的货（ADR-0027 §六），和对话界面一个样子：上面是种族、技术商的名字、你的声望档和折扣、信用点；中间三样货，
 * 每行是模块、加成、价钱和"买"；底下是上一次买的结果。价钱都是服务端算好的。
 */
public class TechShopScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int PAD = 12;
    private static final int HEADER = 42;
    private static final int ROW_HEIGHT = 26;
    private static final int BUY_WIDTH = 50;
    private static final int PRICE_RIGHT = WIDTH - PAD - BUY_WIDTH - 8;
    private static final int FOOTER = 40;
    private static final int MAX_DISTANCE = 8;
    private static final int TEXT = 0xFFE4E8EE;
    private static final int DIM = 0xFF8C93A0;
    private static final int CREDIT_COLOR = 0xFFF2C94C;

    private TechShopPayloads.State state;
    private final Race race;
    private int left;
    private int top;
    private int panelHeight;
    private final List<Button> buys = new ArrayList<>();

    public TechShopScreen(TechShopPayloads.State state) {
        super(Component.literal(GalaxyText.pick(state.nameZh(), state.nameEn())));
        this.state = state;
        this.race = Race.values()[Math.floorMod(state.race(), Race.values().length)];
    }

    /** 对着哪个 NPC。 */
    public int entity() {
        return this.state.entity();
    }

    /** 服务端发来了新的货单（买完、档变了）。 */
    public void update(TechShopPayloads.State state) {
        this.state = state;
        this.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int accent() {
        return 0xFF000000 | this.race.color();
    }

    private Standing.Tier tier() {
        return Standing.Tier.values()[Math.floorMod(this.state.tier(), Standing.Tier.values().length)];
    }

    @Override
    protected void init() {
        int rows = Math.max(1, this.state.rows().size());
        this.panelHeight = HEADER + rows * ROW_HEIGHT + 6 + FOOTER;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.buys.clear();
        for (int i = 0; i < this.state.rows().size(); i++) {
            TechShopPayloads.Row row = this.state.rows().get(i);
            int index = i;
            boolean afford = this.state.credits() >= row.price();
            Component label = Component.translatable(row.sold() ? "gui.universe.tech_shop.bought_label" : "gui.universe.tech_shop.buy");
            Button button = Button.builder(label, b -> ClientPacketDistributor.sendToServer(new TechShopPayloads.Buy(this.state.entity(), index, row.price())))
                    .bounds(this.left + WIDTH - PAD - BUY_WIDTH, this.top + HEADER + i * ROW_HEIGHT + 3, BUY_WIDTH, 20).build();
            button.active = !row.sold() && afford;
            if (row.sold()) {
                button.setTooltip(Tooltip.create(Component.translatable("gui.universe.tech_shop.sold_today")));
            } else if (!afford) {
                button.setTooltip(Tooltip.create(Component.translatable("gui.universe.tech_shop.not_enough", TradeScreen.credits(row.price()))));
            }
            this.buys.add(this.addRenderableWidget(button));
        }
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dialogue.leave"), b -> this.onClose())
                .bounds(this.left + WIDTH - PAD - BUY_WIDTH, this.top + this.panelHeight - 24, BUY_WIDTH, 18).build());
    }

    @Override
    public void tick() {
        // NPC 不在了或者走远了就关上
        Entity npc = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.state.entity());
        if (npc == null || this.minecraft.player == null || npc.distanceToSqr(this.minecraft.player) > MAX_DISTANCE * MAX_DISTANCE) {
            this.onClose();
        }
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x = this.left;
        int y = this.top;
        int accent = this.accent();
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + this.panelHeight + 1, 0xFF000000 | (accent >> 1 & 0x7F7F7F));
        graphics.fill(x, y, x + WIDTH, y + this.panelHeight, 0xF0080A0E);
        graphics.fill(x, y, x + 2, y + this.panelHeight, (accent & 0x00FFFFFF) | 0xA0000000);
        // 种族 · 技术商；信用点
        Component heading = Component.translatable("gui.universe.dialogue.heading", Component.translatable("civ.universe.race." + this.race.id()),
                Component.translatable("civ.universe.role." + NpcRole.TECH_MERCHANT.id()));
        graphics.text(this.font, heading, x + PAD, y + 9, (accent & 0x00FFFFFF) | 0xB0000000, false);
        Component credits = Component.translatable("gui.universe.trade.credits", TradeScreen.credits(this.state.credits()));
        graphics.text(this.font, credits, x + WIDTH - PAD - this.font.width(credits), y + 9, CREDIT_COLOR, false);
        // 名字；声望档和折扣
        graphics.text(this.font, this.title, x + PAD, y + 21, accent, false);
        Component discount = this.discount();
        graphics.text(this.font, discount, x + WIDTH - PAD - this.font.width(discount), y + 21, 0xFFB8C0CC, false);
        graphics.fill(x + PAD, y + 33, x + WIDTH - PAD, y + 34, (accent & 0x00FFFFFF) | 0x60000000);

        ItemStack hovered = ItemStack.EMPTY;
        List<TechShopPayloads.Row> rows = this.state.rows();
        if (rows.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.tech_shop.empty"), x + WIDTH / 2, y + HEADER + 8, DIM);
        }
        for (int i = 0; i < rows.size(); i++) {
            TechShopPayloads.Row row = rows.get(i);
            int rowY = y + HEADER + i * ROW_HEIGHT;
            if (i % 2 == 1) {
                graphics.fill(x + PAD - 4, rowY, x + WIDTH - PAD + 4, rowY + ROW_HEIGHT, 0x10FFFFFF);
            }
            graphics.fakeItem(row.stack(), x + PAD, rowY + 5);
            int nameX = x + PAD + 22;
            // 价钱最多占 52 像素
            int room = x + PRICE_RIGHT - 52 - nameX;
            Component name = row.stack().getHoverName();
            graphics.text(this.font, this.font.plainSubstrByWidth(name.getString(), room), nameX, rowY + 4,
                    row.sold() ? DIM : (name.getStyle().getColor() != null ? 0xFF000000 | name.getStyle().getColor().getValue() : TEXT), false);
            graphics.text(this.font, this.font.plainSubstrByWidth(this.stats(row.stack()).getString(), room), nameX, rowY + 15, DIM, false);
            String price = row.sold() ? "—" : TradeScreen.credits(row.price());
            int priceColor = row.sold() ? DIM : this.state.credits() >= row.price() ? CREDIT_COLOR : 0xFFE05A4E;
            graphics.text(this.font, price, x + PRICE_RIGHT - this.font.width(price), rowY + 9, priceColor, false);
            if (mouseX >= x + PAD - 2 && mouseX < x + PRICE_RIGHT - 52 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                hovered = row.stack();
            }
        }
        // 底下：上一次买的结果，或者提示
        int footY = y + this.panelHeight - FOOTER + 4;
        graphics.fill(x + PAD, footY - 4, x + WIDTH - PAD, footY - 3, (accent & 0x00FFFFFF) | 0x40000000);
        Component line = this.state.notice().orElse(Component.translatable("gui.universe.tech_shop.hint"));
        int color = line.getStyle().getColor() != null ? 0xFF000000 | line.getStyle().getColor().getValue() : DIM;
        graphics.text(this.font, this.font.plainSubstrByWidth(line.getString(), WIDTH - 2 * PAD), x + PAD, footY + 2, color, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, Screen.getTooltipFromItem(this.minecraft, hovered), hovered.getTooltipImage(), hovered,
                    mouseX, mouseY);
        }
    }

    /** "熟客 · 便宜 5%"：档和折扣。 */
    private Component discount() {
        Standing.Tier tier = this.tier();
        Component name = Component.translatable("civ.universe.standing." + tier.name().toLowerCase(Locale.ROOT));
        long percent = Math.round((1 - Standing.priceFactor(tier)) * 100);
        if (percent > 0) {
            return Component.translatable("gui.universe.tech_shop.discount", name, percent);
        }
        if (percent < 0) {
            return Component.translatable("gui.universe.tech_shop.markup", name, -percent);
        }
        return Component.translatable("gui.universe.tech_shop.full_price", name);
    }

    /** 加成，比如"+23% 采矿速度  +6 护盾"。 */
    private Component stats(ItemStack stack) {
        TechModule module = stack.get(UniverseDataComponents.TECH_MODULE.get());
        MutableComponent line = Component.empty();
        if (module == null) {
            return line;
        }
        boolean first = true;
        for (Stat stat : module.type().stats()) {
            if (!first) {
                line.append("  ");
            }
            line.append(TechModuleItem.statLine(stat, module.value(stat)));
            first = false;
        }
        return line;
    }
}
