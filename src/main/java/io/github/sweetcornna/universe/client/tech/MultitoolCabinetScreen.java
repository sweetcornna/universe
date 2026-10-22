package io.github.sweetcornna.universe.client.tech;

import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import io.github.sweetcornna.universe.core.survival.MultitoolClass;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.core.trade.MultitoolMarket;
import io.github.sweetcornna.universe.network.CabinetPayloads;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 空间站的多功能工具柜（ADR-0026）：今天挂着的三把工具并排，每把一张卡片：工具的样子、档次和类型、格数、专长、自带的模块、
 * 买的按钮。手上拿着多功能工具时上面写折价，按钮上是差价；折价比标价高时按钮写退多少。价钱和能不能买由服务端说了算。
 */
public class MultitoolCabinetScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 206;
    private static final int CARD_WIDTH = 104;
    private static final int CARD_GAP = 8;
    private static final int CARD_Y = 44;
    private static final int CARD_HEIGHT = 140;
    private static final int ACCENT = 0xFFFFAA5A;
    private static final int TEXT = 0xFFE6EEF0;
    private static final int DIM = 0xFF9AA8AE;
    private static final int GOOD = 0xFF7BE08A;
    private static final int BAD = 0xFFE07B7B;

    private CabinetPayloads.State state;
    private int left;
    private int top;

    public MultitoolCabinetScreen(CabinetPayloads.State state) {
        super(Component.translatable("gui.universe.cabinet.title"));
        this.state = state;
    }

    /** 服务端发来新的内容（买完以后）。 */
    public void update(CabinetPayloads.State state) {
        this.state = state;
        this.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static MultitoolSpec spec(CabinetPayloads.Offer offer) {
        MultitoolClass type = MultitoolClass.values()[Math.clamp(offer.type(), 0, MultitoolClass.values().length - 1)];
        Grade grade = Grade.values()[Math.clamp(offer.grade(), 0, Grade.values().length - 1)];
        return new MultitoolSpec(type, grade, 0);
    }

    private static List<TechModule> modules(CabinetPayloads.Offer offer) {
        MultitoolSpec spec = spec(offer);
        return new MultitoolMarket.Offer(offer.slot(), offer.seed(), spec.type(), spec.grade()).modules();
    }

    /** 实际要付多少（扣掉手上旧工具的折价）。 */
    private long due(CabinetPayloads.Offer offer) {
        return MultitoolMarket.due(offer.price(), Math.max(0, this.state.tradeIn()));
    }

    private int cardX(int index) {
        int total = MultitoolMarket.TOOLS_PER_DAY * CARD_WIDTH + (MultitoolMarket.TOOLS_PER_DAY - 1) * CARD_GAP;
        return this.left + (WIDTH - total) / 2 + index * (CARD_WIDTH + CARD_GAP);
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        List<CabinetPayloads.Offer> offers = this.state.offers();
        for (int i = 0; i < offers.size() && i < MultitoolMarket.TOOLS_PER_DAY; i++) {
            CabinetPayloads.Offer offer = offers.get(i);
            long due = this.due(offer);
            Component label = offer.sold() ? Component.translatable("gui.universe.dealer.sold_out")
                    : due < 0 ? Component.translatable("gui.universe.cabinet.buy_refund", Credits.format(-due))
                    : Component.translatable("gui.universe.dealer.buy", Credits.format(due));
            Button buy = Button.builder(label, b -> ClientPacketDistributor.sendToServer(new CabinetPayloads.Buy(offer.slot())))
                    .bounds(this.cardX(i) + 4, this.top + CARD_Y + CARD_HEIGHT - 24, CARD_WIDTH - 8, 20).build();
            buy.active = !offer.sold() && this.state.credits() >= due;
            this.addRenderableWidget(buy);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + HEIGHT + 1, 0xFF7E5A2E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, 0xF0080C0E);
        graphics.text(this.font, this.title, this.left + 8, this.top + 9, ACCENT, false);
        Component credits = Component.translatable("gui.universe.dealer.credits", Credits.format(this.state.credits()));
        graphics.text(this.font, credits, this.left + WIDTH - 8 - this.font.width(credits), this.top + 9, TEXT, false);
        Component trade = this.state.tradeIn() >= 0
                ? Component.translatable("gui.universe.cabinet.trade_in", Credits.format(this.state.tradeIn()))
                : Component.translatable("gui.universe.cabinet.no_trade_in");
        graphics.text(this.font, trade, this.left + 8, this.top + 25, this.state.tradeIn() >= 0 ? GOOD : DIM, false);
        List<CabinetPayloads.Offer> offers = this.state.offers();
        if (offers.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.none"), this.left + 8, this.top + CARD_Y + 4, DIM, false);
        }
        for (int i = 0; i < offers.size() && i < MultitoolMarket.TOOLS_PER_DAY; i++) {
            this.card(graphics, offers.get(i), this.cardX(i), this.top + CARD_Y, mouseX, mouseY);
        }
        this.state.notice().ifPresent(notice -> graphics.text(this.font, notice, this.left + 8, this.top + HEIGHT - 14, DIM, false));
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /** 一把工具的卡片。 */
    private void card(GuiGraphicsExtractor graphics, CabinetPayloads.Offer offer, int x, int y, int mouseX, int mouseY) {
        MultitoolSpec spec = spec(offer);
        int color = 0xFF000000 | spec.grade().color();
        graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0x40203840);
        graphics.fill(x, y, x + CARD_WIDTH, y + 1, color);
        // 工具放大两倍画在卡片上方
        ItemStack tool = MultitoolItem.create(spec);
        int ix = x + CARD_WIDTH / 2 - 16;
        int iy = y + 6;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(ix, iy);
        pose.scale(2);
        graphics.item(tool, 0, 0);
        pose.popMatrix();
        if (mouseX >= ix && mouseX < ix + 32 && mouseY >= iy && mouseY < iy + 32) {
            graphics.setTooltipForNextFrame(this.font, tool, mouseX, mouseY);
        }
        int ty = y + 42;
        Component name = Component.translatable("gui.universe.cabinet.tool", spec.grade().letter(), MultitoolItem.className(spec));
        graphics.text(this.font, name, x + (CARD_WIDTH - this.font.width(name)) / 2, ty, color, false);
        ty += 12;
        ty = this.line(graphics, Component.translatable("gui.universe.cabinet.slots", spec.slots()), x + 6, ty, TEXT);
        Map<Stat, Double> specialty = spec.specialty();
        if (specialty.isEmpty()) {
            ty = this.line(graphics, Component.translatable("gui.universe.cabinet.no_specialty"), x + 6, ty, DIM);
        }
        for (Map.Entry<Stat, Double> entry : specialty.entrySet()) {
            ty = this.line(graphics, TechModuleItem.statLine(entry.getKey(), entry.getValue()), x + 6, ty, GOOD);
        }
        // 自带的模块：图标一排，指上去看数值
        List<TechModule> modules = modules(offer);
        if (!modules.isEmpty()) {
            int mx = x + 6;
            int my = ty + 1;
            for (TechModule module : modules) {
                ItemStack stack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
                stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
                graphics.item(stack, mx, my);
                if (mouseX >= mx && mouseX < mx + 16 && mouseY >= my && mouseY < my + 16) {
                    graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
                }
                mx += 18;
            }
        }
        if (!offer.sold()) {
            Component price = Component.translatable("gui.universe.cabinet.price", Credits.format(offer.price()));
            int py = y + CARD_HEIGHT - 36;
            graphics.text(this.font, price, x + (CARD_WIDTH - this.font.width(price)) / 2, py,
                    this.state.credits() >= this.due(offer) ? TEXT : BAD, false);
        }
    }

    private int line(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(this.font, text, x, y, color, false);
        return y + 10;
    }
}
