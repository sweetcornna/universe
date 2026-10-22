package io.github.sweetcornna.universe.client.ship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.fleet.Area;
import io.github.sweetcornna.universe.core.fleet.FreighterLayout;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateTrait;
import io.github.sweetcornna.universe.core.ship.ShipDesign;
import io.github.sweetcornna.universe.core.ship.ShipDesign.Cell;
import io.github.sweetcornna.universe.core.ship.ShipMarket;
import io.github.sweetcornna.universe.core.ship.ShipRating;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.ship.ShipTech;
import io.github.sweetcornna.universe.core.ship.ShipType;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.network.DealerPayloads;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 飞船交易终端（ADR-0024）。"在售"页左边是今天挂着的几艘船，右边是选中那艘的俯视图、评级、属性和自带的技术模块；
 * "护卫舰"页是今天挂着雇的护卫舰（要有货船）；"货船"页是今天挂着卖的货船（不是每天都有）；
 * "卖船"页列出停在机库里的自己的船。船的外形和属性由客户端按种子算，价钱和能不能买由服务端说了算。
 */
public class ShipDealerScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final int LIST_WIDTH = 120;
    private static final int ACCENT = 0xFF6EDCF5;
    private static final int TEXT = 0xFFE6EEF0;
    private static final int DIM = 0xFF9AA8AE;
    private static final int GOOD = 0xFF7BE08A;
    private static final int BAD = 0xFFE07B7B;
    private static final int PREVIEW = 92;

    private enum Tab {
        SHIPS, FRIGATES, FREIGHTER, SELL
    }

    private DealerPayloads.State state;
    private Tab tab = Tab.SHIPS;
    private FreighterLayout.@Nullable Exterior freighter;
    private long freighterSeed;
    private int selected;
    private final Map<Long, ShipDesign> designs = new HashMap<>();
    private int left;
    private int top;

    public ShipDealerScreen(DealerPayloads.State state) {
        super(Component.translatable("gui.universe.dealer.title"));
        this.state = state;
    }

    /** 服务端发来新的内容（买卖以后）。 */
    public void update(DealerPayloads.State state) {
        this.state = state;
        this.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ShipType type(int ordinal) {
        return ShipType.values()[Math.clamp(ordinal, 0, ShipType.values().length - 1)];
    }

    private static Grade grade(int ordinal) {
        return Grade.values()[Math.clamp(ordinal, 0, Grade.values().length - 1)];
    }

    private ShipMarket.Offer offer(DealerPayloads.Offer offer) {
        return new ShipMarket.Offer(offer.slot(), offer.seed(), type(offer.type()), grade(offer.grade()));
    }

    private ShipDesign design(DealerPayloads.Offer offer) {
        return this.designs.computeIfAbsent(offer.seed(), seed -> this.offer(offer).design());
    }

    private DealerPayloads.@Nullable Offer current() {
        List<DealerPayloads.Offer> ships = this.state.ships();
        return ships.isEmpty() ? null : ships.get(Math.clamp(this.selected, 0, ships.size() - 1));
    }

    static Component shipName(int type, int grade) {
        return Component.translatable("gui.universe.dealer.ship", grade(grade).letter(),
                Component.translatable("ship.universe.type." + type(type).key()));
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        int x = this.left + 8;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable("gui.universe.dealer.tab." + t.name().toLowerCase(Locale.ROOT)), b -> this.tab(t))
                    .bounds(x, this.top + 24, 76, 18).build();
            button.active = t != this.tab;
            this.addRenderableWidget(button);
            x += 80;
        }
        switch (this.tab) {
            case SHIPS -> this.initShips();
            case FRIGATES -> this.initFrigates();
            case FREIGHTER -> this.initFreighter();
            case SELL -> this.initSell();
        }
    }

    private void tab(Tab tab) {
        this.tab = tab;
        this.rebuildWidgets();
    }

    private void initShips() {
        List<DealerPayloads.Offer> ships = this.state.ships();
        for (int i = 0; i < ships.size(); i++) {
            DealerPayloads.Offer offer = ships.get(i);
            int index = i;
            Component label = offer.sold() ? Component.translatable("gui.universe.dealer.sold_out") : shipName(offer.type(), offer.grade());
            Button row = Button.builder(label, b -> {
                this.selected = index;
                this.rebuildWidgets();
            }).bounds(this.left + 8, this.top + 50 + i * 24, LIST_WIDTH, 20).build();
            row.active = index != this.selected;
            this.addRenderableWidget(row);
        }
        DealerPayloads.Offer offer = this.current();
        if (offer != null) {
            boolean afford = this.state.credits() >= offer.price();
            Button buy = Button.builder(Component.translatable("gui.universe.dealer.buy", Credits.format(offer.price())),
                    b -> ClientPacketDistributor.sendToServer(new DealerPayloads.Buy(offer.slot())))
                    .bounds(this.left + WIDTH - 128, this.top + HEIGHT - 42, 120, 20).build();
            buy.active = !offer.sold() && afford && this.state.padFree();
            this.addRenderableWidget(buy);
        }
    }

    // ---- 护卫舰

    /** 雇不了的原因（和哪艘无关的那些）。 */
    private @Nullable Component cannotHire() {
        if (this.state.fleet() < 0) {
            return Component.translatable("gui.universe.dealer.need_freighter");
        }
        if (this.state.fleet() >= FreighterRules.MAX_FRIGATES) {
            return Component.translatable("gui.universe.dealer.fleet_full", FreighterRules.MAX_FRIGATES);
        }
        return null;
    }

    private void initFrigates() {
        List<DealerPayloads.FrigateOffer> frigates = this.state.frigates();
        for (int i = 0; i < frigates.size(); i++) {
            DealerPayloads.FrigateOffer offer = frigates.get(i);
            Button hire = Button.builder(Component.translatable("gui.universe.dealer.hire", Credits.format(offer.price())),
                    b -> ClientPacketDistributor.sendToServer(new DealerPayloads.Hire(offer.slot())))
                    .bounds(this.left + WIDTH - 128, this.top + 52 + i * 70, 120, 20).build();
            hire.active = !offer.sold() && this.cannotHire() == null && this.state.credits() >= offer.price();
            this.addRenderableWidget(hire);
        }
    }

    private void drawFrigates(GuiGraphicsExtractor graphics) {
        List<DealerPayloads.FrigateOffer> frigates = this.state.frigates();
        if (frigates.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.none"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        for (int i = 0; i < frigates.size(); i++) {
            DealerPayloads.FrigateOffer offer = frigates.get(i);
            Frigate frigate = offer.frigate().frigate();
            int y = this.top + 50 + i * 70;
            graphics.fill(this.left + 6, y - 2, this.left + WIDTH - 6, y + 64, 0x40203840);
            if (offer.sold()) {
                graphics.text(this.font, Component.translatable("gui.universe.dealer.hired_out"), this.left + 10, y + 2, DIM, false);
                continue;
            }
            graphics.text(this.font, Component.translatable("gui.universe.fleet.frigate", frigate.designation(),
                    Component.translatable("gui.universe.fleet.type." + frigate.type().key()), frigate.rank()), this.left + 10, y + 2, TEXT, false);
            MutableComponent stats = Component.empty();
            for (Area area : Area.values()) {
                if (area.ordinal() > 0) {
                    stats.append(" ");
                }
                stats.append(Component.translatable("gui.universe.fleet.stat." + area.key(), frigate.stat(area)));
            }
            stats.append(" · ").append(Component.translatable("gui.universe.fleet.fuel", frigate.fuelCost()));
            graphics.text(this.font, stats, this.left + 10, y + 14, DIM, false);
            int ty = y + 26;
            if (frigate.traits().isEmpty()) {
                graphics.text(this.font, Component.translatable("gui.universe.dealer.no_traits"), this.left + 10, ty, DIM, false);
            }
            for (FrigateTrait trait : frigate.traits()) {
                String amount = (trait.amount() > 0 ? "+" : "") + trait.amount();
                Component what = trait.area() == null ? Component.translatable("gui.universe.fleet.trait_fuel", amount)
                        : Component.translatable("gui.universe.fleet.trait_stat", amount,
                                Component.translatable("gui.universe.fleet.area." + trait.area().key()));
                graphics.text(this.font, Component.translatable("gui.universe.fleet.trait",
                        Component.translatable("gui.universe.fleet.trait." + trait.key()), what), this.left + 10, ty, trait.good() ? GOOD : BAD, false);
                ty += 10;
            }
        }
        Component why = this.cannotHire();
        if (why != null) {
            graphics.text(this.font, why, this.left + 8, this.top + HEIGHT - 28, BAD, false);
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.fleet", this.state.fleet(), FreighterRules.MAX_FRIGATES),
                    this.left + 8, this.top + HEIGHT - 28, DIM, false);
        }
    }

    // ---- 货船

    private void initFreighter() {
        this.state.freighter().ifPresent(offer -> {
            Button buy = Button.builder(Component.translatable("gui.universe.dealer.buy", Credits.format(offer.price())),
                    b -> ClientPacketDistributor.sendToServer(new DealerPayloads.BuyFreighter(offer.seed())))
                    .bounds(this.left + WIDTH - 128, this.top + HEIGHT - 42, 120, 20).build();
            buy.active = !offer.sold() && this.state.fleet() < 0 && this.state.credits() >= offer.price();
            this.addRenderableWidget(buy);
        });
    }

    private void drawFreighter(GuiGraphicsExtractor graphics) {
        if (this.state.freighter().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.no_freighter"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        DealerPayloads.FreighterOffer offer = this.state.freighter().get();
        if (this.freighter == null || this.freighterSeed != offer.seed()) {
            this.freighter = FreighterLayout.exterior(offer.seed());
            this.freighterSeed = offer.seed();
        }
        FreighterLayout.Exterior exterior = this.freighter;
        Grade grade = grade(offer.grade());
        int y = this.top + 50;
        // 左边一张俯视图，船头朝上
        int size = 120;
        graphics.fill(this.left + 8, y, this.left + 8 + size, y + size, 0xFF10181C);
        graphics.outline(this.left + 8, y, size, size, 0xFF24424C);
        drawFreighterTop(graphics, exterior, this.left + 8, y, size);
        int x = this.left + 8 + size + 10;
        graphics.text(this.font, Component.translatable("gui.universe.dealer.freighter", grade.letter()), x, y, 0xFF000000 | grade.color(), false);
        y += 14;
        y = this.line(graphics, Component.translatable("gui.universe.dealer.freighter_size", exterior.length(), exterior.halfWidth() * 2 + 1,
                exterior.height()), x, y, TEXT);
        y = this.line(graphics, Component.translatable("gui.universe.dealer.freighter_range", String.format(Locale.ROOT, "%.0f", offer.range()),
                FreighterRules.WARPERS), x, y, TEXT);
        y = this.line(graphics, Component.translatable("gui.universe.dealer.freighter_fleet", FreighterRules.MAX_FRIGATES), x, y, TEXT);
        y = this.line(graphics, Component.translatable("gui.universe.dealer.freighter_inside"), x, y, TEXT);
        y += 4;
        this.line(graphics, Component.translatable("gui.universe.dealer.freighter_hint"), x, y, DIM);
        Component why = offer.sold() ? Component.translatable("gui.universe.dealer.sold_out")
                : this.state.fleet() >= 0 ? Component.translatable("gui.universe.dealer.have_freighter")
                : this.state.credits() < offer.price() ? Component.translatable("gui.universe.dealer.poor") : null;
        if (why != null) {
            graphics.text(this.font, why, this.left + WIDTH - 8 - this.font.width(why), this.top + HEIGHT - 54, BAD, false);
        }
    }

    /** 货船的俯视图：每一列画最高的那一格，越高越亮。 */
    private static void drawFreighterTop(GuiGraphicsExtractor graphics, FreighterLayout.Exterior exterior, int x, int y, int size) {
        Map<Long, FreighterLayout.Cell> top = new HashMap<>();
        int[] b = exterior.bounds();
        for (FreighterLayout.Cell cell : exterior.cells()) {
            long key = ((long) cell.x() << 32) ^ (cell.z() & 0xFFFFFFFFL);
            FreighterLayout.Cell old = top.get(key);
            if (old == null || cell.y() > old.y()) {
                top.put(key, cell);
            }
        }
        int w = b[3] - b[0] + 1;
        int d = b[5] - b[2] + 1;
        double scale = (size - 8) / (double) Math.max(w, d);
        int ox = x + (int) Math.round((size - w * scale) / 2);
        int oy = y + (int) Math.round((size - d * scale) / 2);
        for (FreighterLayout.Cell cell : top.values()) {
            double light = 0.6 + 0.4 * (cell.y() - b[1]) / (double) Math.max(1, b[4] - b[1]);
            int color = shade(switch (cell.role()) {
                case PLATE, WALL -> 0xFF5A626C;
                case LIGHT -> 0xFFF4F0D0;
                case GLASS -> 0xFF9FD8F0;
                case ENGINE -> 0xFF6EC8FF;
                case FRAME -> 0xFF50D0E0;
                default -> 0xFFC8D0D8;
            }, light);
            int cx = ox + (int) Math.floor((cell.x() - b[0]) * scale);
            int cz = oy + (int) Math.floor((cell.z() - b[2]) * scale);
            graphics.fill(cx, cz, cx + Math.max(1, (int) Math.ceil(scale)), cz + Math.max(1, (int) Math.ceil(scale)), color);
        }
    }

    private void initSell() {
        List<DealerPayloads.Owned> owned = this.state.owned();
        for (int i = 0; i < owned.size() && i < 6; i++) {
            DealerPayloads.Owned ship = owned.get(i);
            Button sell = Button.builder(Component.translatable("gui.universe.dealer.sell", Credits.format(ship.price())),
                    b -> ClientPacketDistributor.sendToServer(new DealerPayloads.Sell(ship.entity())))
                    .bounds(this.left + WIDTH - 128, this.top + 50 + i * 24, 120, 20).build();
            sell.active = !ship.cargo();
            this.addRenderableWidget(sell);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + HEIGHT + 1, 0xFF2E6E7E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, 0xF0080C0E);
        graphics.text(this.font, this.title, this.left + 8, this.top + 9, ACCENT, false);
        Component credits = Component.translatable("gui.universe.dealer.credits", Credits.format(this.state.credits()));
        graphics.text(this.font, credits, this.left + WIDTH - 8 - this.font.width(credits), this.top + 9, TEXT, false);
        switch (this.tab) {
            case SHIPS -> this.drawShips(graphics, mouseX, mouseY);
            case FRIGATES -> this.drawFrigates(graphics);
            case FREIGHTER -> this.drawFreighter(graphics);
            case SELL -> this.drawSell(graphics);
        }
        this.state.notice().ifPresent(notice -> graphics.text(this.font, notice, this.left + 8, this.top + HEIGHT - 14, DIM, false));
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawShips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        DealerPayloads.Offer offer = this.current();
        if (offer == null) {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.none"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        ShipMarket.Offer market = this.offer(offer);
        ShipDesign design = this.design(offer);
        ShipRating rating = design.rating();
        List<TechModule> modules = market.modules();
        ShipTech tech = new ShipTech(modules, ShipTech.NONE.drive());
        ShipStats stats = design.stats().tuned(rating, tech.bonuses(), tech.drive());
        int x = this.left + LIST_WIDTH + 18;
        int y = this.top + 50;
        // 右上角是俯视图
        int px = this.left + WIDTH - 8 - PREVIEW;
        graphics.fill(px, y, px + PREVIEW, y + PREVIEW, 0xFF10181C);
        graphics.outline(px, y, PREVIEW, PREVIEW, 0xFF24424C);
        drawTopView(graphics, design, px, y, PREVIEW);
        graphics.text(this.font, shipName(offer.type(), offer.grade()), x, y, 0xFF000000 | rating.grade().color(), false);
        y += 12;
        y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.hull", Math.round(stats.hull())), x, y, TEXT);
        if (stats.shield() > 0) {
            y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.shield", Math.round(stats.shield())), x, y, TEXT);
        }
        y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.fuel", Math.round(stats.fuelCapacity())), x, y, TEXT);
        y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.lift",
                String.format(Locale.ROOT, "%.2f", stats.thrustToWeight(1))), x, y, TEXT);
        if (stats.warpDrives() > 0) {
            y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.warp",
                    String.format(Locale.ROOT, "%.1f", stats.warpRange())), x, y, TEXT);
        }
        if (stats.armed()) {
            y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.damage", Math.round(stats.damage() * 100) + "%"), x, y, TEXT);
        }
        y = this.line(graphics, Component.translatable("gui.universe.ship_panel.stat.cargo", design.cargoSlots()), x, y, TEXT);
        y = this.line(graphics, Component.translatable("gui.universe.dealer.slots", ShipRating.slots(rating.grade())), x, y, TEXT);
        // 自带的技术模块：图标一排，指上去看数值
        y += 4;
        if (modules.isEmpty()) {
            this.line(graphics, Component.translatable("gui.universe.dealer.no_tech"), x, y, DIM);
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.tech"), x, y + 4, DIM, false);
            int ix = x + this.font.width(Component.translatable("gui.universe.dealer.tech")) + 4;
            for (TechModule module : modules) {
                ItemStack stack = new ItemStack(UniverseItems.MODULES.get(module.type()).get());
                stack.set(UniverseDataComponents.TECH_MODULE.get(), module);
                graphics.item(stack, ix, y);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= y && mouseY < y + 16) {
                    graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
                }
                ix += 18;
            }
        }
        // 买不了的原因写在按钮上面
        Component why = offer.sold() ? Component.translatable("gui.universe.dealer.sold_out")
                : !this.state.padFree() ? Component.translatable("gui.universe.dealer.pad_busy")
                : this.state.credits() < offer.price() ? Component.translatable("gui.universe.dealer.poor") : null;
        if (why != null) {
            graphics.text(this.font, why, this.left + WIDTH - 8 - this.font.width(why), this.top + HEIGHT - 54, BAD, false);
        }
    }

    private int line(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(this.font, text, x, y, color, false);
        return y + 10;
    }

    private void drawSell(GuiGraphicsExtractor graphics) {
        List<DealerPayloads.Owned> owned = this.state.owned();
        if (owned.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.dealer.no_ships"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        for (int i = 0; i < owned.size() && i < 6; i++) {
            DealerPayloads.Owned ship = owned.get(i);
            int y = this.top + 50 + i * 24;
            graphics.text(this.font, shipName(ship.type(), ship.grade()), this.left + 8, y + 2, TEXT, false);
            Component detail = ship.cargo() ? Component.translatable("gui.universe.dealer.has_cargo")
                    : Component.translatable("gui.universe.dealer.blocks", ship.blocks());
            graphics.text(this.font, detail, this.left + 8, y + 12, ship.cargo() ? BAD : DIM, false);
        }
        graphics.text(this.font, Component.translatable("gui.universe.dealer.sell_hint"), this.left + 8, this.top + HEIGHT - 28, DIM, false);
    }

    /** 俯视图：每一列画最高的那一格，越高越亮；船头朝上。 */
    static void drawTopView(GuiGraphicsExtractor graphics, ShipDesign design, int x, int y, int size) {
        Map<Long, Cell> top = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Cell cell : design.cells()) {
            long key = ((long) cell.x() << 32) ^ (cell.z() & 0xFFFFFFFFL);
            Cell old = top.get(key);
            if (old == null || cell.y() > old.y()) {
                top.put(key, cell);
            }
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());
            minY = Math.min(minY, cell.y());
            maxY = Math.max(maxY, cell.y());
        }
        int w = maxX - minX + 1;
        int d = maxZ - minZ + 1;
        int cellSize = Math.max(1, Math.min(8, (size - 8) / Math.max(w, d)));
        int ox = x + (size - w * cellSize) / 2;
        int oy = y + (size - d * cellSize) / 2;
        List<Cell> cells = new ArrayList<>(top.values());
        for (Cell cell : cells) {
            double light = maxY == minY ? 1 : 0.7 + 0.3 * (cell.y() - minY) / (double) (maxY - minY);
            int color = shade(color(design, cell), light);
            int cx = ox + (cell.x() - minX) * cellSize;
            int cz = oy + (cell.z() - minZ) * cellSize;
            graphics.fill(cx, cz, cx + cellSize, cz + cellSize, color);
        }
    }

    private static int color(ShipDesign design, Cell cell) {
        return switch (cell.role()) {
            case HULL -> 0xFF000000 | DyeColor.byId(design.primary()).getTextureDiffuseColor();
            case TRIM -> 0xFF000000 | DyeColor.byId(design.accent()).getTextureDiffuseColor();
            case ALLOY -> 0xFF8A93A0;
            case GLASS -> 0xFF9FD8F0;
            case LAMP -> 0xFFFFF2B0;
            case HELM -> 0xFFE0E4E8;
            case THRUSTER, THRUSTER_MK2 -> 0xFF3A3F45;
            case FUEL_TANK, FUEL_TANK_MK2 -> 0xFFB86B2E;
            case IMPULSE, IMPULSE_MK2 -> 0xFF4A90C0;
            case WARP, WARP_MK2 -> 0xFF8E5CC8;
            case CANNON -> 0xFFC04040;
            case SHIELD -> 0xFF40C0C0;
            case CARGO -> 0xFF8B5A2B;
        };
    }

    private static int shade(int argb, double light) {
        int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * light);
        int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * light);
        int b = (int) Math.min(255, (argb & 0xFF) * light);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
