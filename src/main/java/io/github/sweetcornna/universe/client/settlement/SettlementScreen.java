package io.github.sweetcornna.universe.client.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.civ.Civ;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.civ.Standing;
import io.github.sweetcornna.universe.core.poi.SettlementLayout;
import io.github.sweetcornna.universe.core.settlement.BuildingType;
import io.github.sweetcornna.universe.core.settlement.Cost;
import io.github.sweetcornna.universe.core.settlement.Decisions;
import io.github.sweetcornna.universe.core.settlement.Plot;
import io.github.sweetcornna.universe.core.settlement.Product;
import io.github.sweetcornna.universe.core.settlement.Settlement;
import io.github.sweetcornna.universe.core.settlement.SettlementRules;
import io.github.sweetcornna.universe.settlement.Materials;
import io.github.sweetcornna.universe.settlement.SettlementData;
import io.github.sweetcornna.universe.settlement.SettlementPayloads;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 聚落的管理界面（ADR-0034 §二），六页：
 * <ul>
 * <li>概况：等级、人口、幸福、生产力、威胁、储备、账上的钱和欠款，最近的事；辞去总督。没有总督的聚落这一页是接管；</li>
 * <li>建筑：八块地基上的建筑、档次和作用；</li>
 * <li>施工：左边是聚落的平面图（北在上），点一块地基；右边选建筑、交材料、开张、升级、修理；</li>
 * <li>决定：今天的一件事和几个选项，每个选项会怎样写在下面；</li>
 * <li>生产：选产品，从储备里取、卖，取走市场的钱和鱼塘、瞭望塔攒的东西；</li>
 * <li>我的聚落：管着的几个聚落的数值放在一起看。</li>
 * </ul>
 * 数值由客户端从服务端发来的聚落还原，能不能做由服务端说了算。别人的聚落只能看概况和建筑；离得太远只能看。
 */
public class SettlementScreen extends Screen {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 240;
    private static final int ACCENT = 0xFF6EDCF5;
    private static final int TEXT = 0xFFE6EEF0;
    private static final int DIM = 0xFF9AA8AE;
    private static final int GOOD = 0xFF7BE08A;
    private static final int WARN = 0xFFE8C060;
    private static final int BAD = 0xFFE07B7B;
    private static final int ROW = 0x40203840;
    private static final int REFRESH = 60;
    /** 平面图：每格几个像素，往外画多少格。 */
    private static final int MAP_SCALE = 2;
    private static final int MAP_RANGE = 32;
    private static final int MAP_SIZE = MAP_SCALE * MAP_RANGE * 2;
    private static final String[] STAGE_KEYS = {"foundation", "frame", "finish"};

    enum Tab {
        OVERVIEW, BUILDINGS, CONSTRUCTION, DECISIONS, PRODUCTION, MINE;

        String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private CompoundTag data;
    private @Nullable Settlement settlement;
    private List<ItemStack> stock = List.of();
    private @Nullable Component notice;
    /** 操作的结果显示到第几刻。 */
    private int noticeUntil;
    private Tab tab;
    private int focus = -1;
    /** 要再点一次才算数的按钮（辞去总督、取消施工）。 */
    private @Nullable String armed;
    private long received;
    private int ticks;
    private int left;
    private int top;
    /** 平面图上地基按钮的位置，画边框用。 */
    private final List<int[]> plotButtons = new ArrayList<>();
    private @Nullable List<int[]> roads;

    public SettlementScreen(CompoundTag data) {
        super(Component.translatable("gui.universe.settlement.title"));
        this.tab = Tab.OVERVIEW;
        this.read(data);
        int wanted = data.getIntOr("tab", 0);
        this.tab = wanted >= 0 && wanted < Tab.values().length ? Tab.values()[wanted] : Tab.OVERVIEW;
        this.focus = data.getIntOr("focus", -1);
        this.fixTab();
    }

    /** 服务端发来新的一份。 */
    public void update(CompoundTag data) {
        String before = this.id();
        this.read(data);
        int wanted = data.getIntOr("tab", -1);
        if (wanted >= 0 && wanted < Tab.values().length) {
            this.tab = Tab.values()[wanted];
        }
        int focus = data.getIntOr("focus", -1);
        if (focus >= 0 || !before.equals(this.id())) {
            this.focus = focus;
        }
        if (!before.equals(this.id())) {
            this.roads = null;
        }
        this.fixTab();
        this.rebuildWidgets();
    }

    private void read(CompoundTag data) {
        this.data = data;
        this.received = this.clientTime();
        this.settlement = data.getCompound("settlement").map(SettlementData::read).orElse(null);
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        List<ItemStack> stacks = new ArrayList<>();
        if (level != null) {
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for (Tag tag : data.getListOrEmpty("stock")) {
                ItemStack.CODEC.parse(ops, tag).result().ifPresent(stacks::add);
            }
            Tag notice = data.get("notice");
            if (notice != null) {
                this.notice = ComponentSerialization.CODEC.parse(ops, notice).result().orElse(null);
                this.noticeUntil = this.ticks + 100;
            }
        }
        this.stock = stacks;
    }

    /** 只能看的页：没有聚落只有"我的聚落"，别人的聚落只有概况和建筑，没有总督的只有接管。 */
    private List<Tab> tabs() {
        Settlement s = this.settlement;
        if (s == null) {
            return List.of(Tab.MINE);
        }
        if (!s.owned()) {
            return List.of(Tab.OVERVIEW, Tab.MINE);
        }
        if (!this.mine()) {
            return List.of(Tab.OVERVIEW, Tab.BUILDINGS, Tab.MINE);
        }
        return List.of(Tab.values());
    }

    private void fixTab() {
        if (!this.tabs().contains(this.tab)) {
            this.tab = this.tabs().getFirst();
        }
    }

    public String id() {
        return this.data.getStringOr("id", "");
    }

    private boolean mine() {
        return this.data.getBooleanOr("mine", false);
    }

    private boolean near() {
        return this.data.getBooleanOr("near", false);
    }

    /** 能管：是自己的、人在聚落里。 */
    private boolean canAct() {
        return this.mine() && this.near();
    }

    private long credits() {
        return this.data.getLongOr("credits", 0);
    }

    private long clientTime() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        return minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    /** 估计现在服务端的游戏刻。 */
    private long now() {
        return this.data.getLongOr("now", 0) + Math.max(0, this.clientTime() - this.received);
    }

    private static String pickName(String both) {
        String[] parts = both.split("\\|", 2);
        return GalaxyText.pick(parts[0], parts.length > 1 ? parts[1] : parts[0]);
    }

    private String place(CompoundTag tag) {
        return GalaxyText.pick(tag.getStringOr("place_zh", ""), tag.getStringOr("place_en", ""));
    }

    private void send(int action, int value) {
        ClientPacketDistributor.sendToServer(new SettlementPayloads.Action(this.id(), action, this.focus, value));
    }

    // ---- 布局

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        this.plotButtons.clear();
        List<Tab> tabs = this.tabs();
        int gap = 4;
        int tabWidth = Math.min(88, (WIDTH - 16 - gap * (tabs.size() - 1)) / tabs.size());
        int x = this.left + 8;
        for (Tab t : tabs) {
            Component label = t == Tab.OVERVIEW && this.settlement != null && !this.settlement.owned()
                    ? Component.translatable("gui.universe.settlement.tab.claim")
                    : Component.translatable("gui.universe.settlement.tab." + t.key());
            Button button = Button.builder(label, b -> this.select(t)).bounds(x, this.top + 22, tabWidth, 18).build();
            button.active = t != this.tab;
            this.addRenderableWidget(button);
            x += tabWidth + gap;
        }
        Settlement s = this.settlement;
        switch (this.tab) {
            case OVERVIEW -> {
                if (s != null && !s.owned()) {
                    this.initClaim();
                } else if (s != null) {
                    this.initOverview(s);
                }
            }
            case BUILDINGS -> this.initBuildings();
            case CONSTRUCTION -> {
                if (s != null) {
                    this.initConstruction(s);
                }
            }
            case DECISIONS -> {
                if (s != null) {
                    this.initDecisions(s);
                }
            }
            case PRODUCTION -> {
                if (s != null) {
                    this.initProduction(s);
                }
            }
            case MINE -> {
            }
        }
    }

    private void select(Tab tab) {
        this.tab = tab;
        this.armed = null;
        this.rebuildWidgets();
    }

    /** 给开发脚本用：切到第几页、选第几块地基（从 0 数）。 */
    public void select(int tab, int plot) {
        if (tab >= 0 && tab < Tab.values().length && this.tabs().contains(Tab.values()[tab])) {
            this.tab = Tab.values()[tab];
        }
        if (plot >= 0 && plot < SettlementLayout.plotCount()) {
            this.focus = plot;
        }
        this.armed = null;
        this.rebuildWidgets();
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        Button button = Button.builder(label, b -> action.run()).bounds(x, y, width, 18).build();
        this.addRenderableWidget(button);
        return button;
    }

    /** 要按两下的按钮：第一下换成"再点一次"。 */
    private Button confirm(String key, Component label, int x, int y, int width, Runnable action) {
        boolean armed = key.equals(this.armed);
        return this.button(armed ? Component.translatable("gui.universe.settlement.confirm") : label, x, y, width, () -> {
            if (key.equals(this.armed)) {
                this.armed = null;
                action.run();
            } else {
                this.armed = key;
                this.rebuildWidgets();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.ticks % REFRESH == 0) {
            ClientPacketDistributor.sendToServer(new SettlementPayloads.Action(this.id(), this.id().isEmpty()
                    ? SettlementPayloads.Action.LIST : SettlementPayloads.Action.REFRESH, this.focus, 0));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 接管

    private void initClaim() {
        Button claim = this.button(Component.translatable("gui.universe.settlement.claim"), this.left + WIDTH - 128, this.top + HEIGHT - 44, 120,
                () -> this.send(SettlementPayloads.Action.CLAIM, 0));
        claim.active = this.claimProblems() == 0 && this.near();
    }

    /** 接管还差几条。 */
    private int claimProblems() {
        Player player = this.minecraft != null ? this.minecraft.player : null;
        int problems = 0;
        if (this.data.getIntOr("tier", 0) < Standing.Tier.ACQUAINTED.ordinal()) {
            problems++;
        }
        if (this.data.getIntOr("owned", 0) >= SettlementRules.MAX_OWNED) {
            problems++;
        }
        if (player == null || !Materials.enough(player, SettlementRules.claimCost())) {
            problems++;
        }
        return problems;
    }

    private void drawClaim(GuiGraphicsExtractor graphics, Settlement s) {
        int x = this.left + 10;
        int y = this.top + 50;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.unclaimed"), x, y, TEXT, false);
        y += 16;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.claim.needs"), x, y, ACCENT, false);
        y += 13;
        Standing.Tier tier = Standing.Tier.values()[Math.clamp(this.data.getIntOr("tier", 0), 0, Standing.Tier.values().length - 1)];
        boolean standing = tier.ordinal() >= Standing.Tier.ACQUAINTED.ordinal();
        this.check(graphics, x, y, standing, Component.translatable("gui.universe.settlement.claim.standing", Civ.raceName(s.race()),
                Civ.tierName(Standing.Tier.ACQUAINTED), Civ.tierName(tier)));
        y += 12;
        int owned = this.data.getIntOr("owned", 0);
        this.check(graphics, x, y, owned < SettlementRules.MAX_OWNED, Component.translatable("gui.universe.settlement.claim.owned", owned,
                SettlementRules.MAX_OWNED));
        y += 12;
        Player player = this.minecraft != null ? this.minecraft.player : null;
        boolean materials = player != null && Materials.enough(player, SettlementRules.claimCost());
        this.check(graphics, x, y, materials, Component.translatable("gui.universe.settlement.claim.materials"));
        y += 14;
        y = this.costs(graphics, x + 10, y, SettlementRules.claimCost(), null);
        y += 8;
        for (FormattedCharSequence line : this.font.split(Component.translatable("gui.universe.settlement.claim.after"), WIDTH - 24)) {
            graphics.text(this.font, line, x, y, DIM, false);
            y += 10;
        }
        if (!this.near()) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.too_far"), x, this.top + HEIGHT - 39, WARN, false);
        }
    }

    private void check(GuiGraphicsExtractor graphics, int x, int y, boolean ok, Component text) {
        graphics.text(this.font, Component.literal(ok ? "✔" : "✘"), x, y, ok ? GOOD : BAD, false);
        graphics.text(this.font, text, x + 12, y, ok ? TEXT : DIM, false);
    }

    /**
     * 一组材料，一样一行：图标、名字、要几个；{@code plot} 不为空时写交了几个。背包里的够不够用颜色标。
     *
     * @return 下一行的 y
     */
    private int costs(GuiGraphicsExtractor graphics, int x, int y, List<Cost> costs, @Nullable Plot plot) {
        Player player = this.minecraft != null ? this.minecraft.player : null;
        for (Cost cost : costs) {
            graphics.fakeItem(Materials.icon(cost), x, y);
            int have = player == null ? 0 : Materials.count(player, cost);
            int need = plot == null ? cost.count() : plot.missing(cost);
            Component line = plot == null
                    ? Component.translatable("gui.universe.settlement.cost", Materials.name(cost), cost.count())
                    : Component.translatable("gui.universe.settlement.cost.delivered", Materials.name(cost), cost.count() - need, cost.count());
            graphics.text(this.font, line, x + 20, y + 4, need == 0 ? GOOD : TEXT, false);
            if (need > 0) {
                boolean creative = player != null && player.hasInfiniteMaterials();
                Component bag = Component.translatable("gui.universe.settlement.cost.have", have);
                graphics.text(this.font, bag, x + 20 + this.font.width(line) + 6, y + 4, creative || have >= need ? GOOD : have > 0 ? WARN : BAD,
                        false);
            }
            y += 18;
        }
        return y;
    }

    // ---- 概况

    private void initOverview(Settlement s) {
        if (!this.canAct()) {
            return;
        }
        int y = this.top + HEIGHT - 44;
        if (s.debt() > 0) {
            Button pay = this.button(Component.translatable("gui.universe.settlement.pay_debt", Credits.format(Math.min(s.debt(), this.credits()))),
                    this.left + 8, y, 150, () -> this.send(SettlementPayloads.Action.PAY_DEBT, 0));
            pay.active = this.credits() > 0;
        }
        this.confirm("resign", Component.translatable("gui.universe.settlement.resign"), this.left + WIDTH - 108, y, 100,
                () -> this.send(SettlementPayloads.Action.RESIGN, 0));
    }

    private void drawOverview(GuiGraphicsExtractor graphics, Settlement s) {
        int x = this.left + 10;
        int y = this.top + 48;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.class", SettlementRules.classLetter(s.settlementClass()),
                Civ.raceName(s.race())), x, y, ACCENT, false);
        y += 13;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.population", s.population(), s.housing()), x, y, TEXT, false);
        y += 13;
        this.bar(graphics, x, y, Component.translatable("gui.universe.settlement.happiness"), s.happiness(), 100,
                s.happiness() < 25 ? BAD : s.happiness() > 70 ? GOOD : WARN);
        y += 13;
        this.bar(graphics, x, y, Component.translatable("gui.universe.settlement.productivity"), s.productivity(), 100, ACCENT);
        y += 13;
        this.bar(graphics, x, y, Component.translatable("gui.universe.settlement.threat"), s.threat(), 100, s.threat() >= 70 ? BAD : WARN);
        y += 13;
        this.bar(graphics, x, y, Component.translatable("gui.universe.settlement.reserve"), s.reserve(), s.reserveCap(), GOOD);
        y += 15;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.daily", Credits.format(s.income()), Credits.format(s.upkeep())), x,
                y, DIM, false);
        y += 11;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.treasury", Credits.format(s.treasury())), x, y, DIM, false);
        y += 11;
        if (s.debt() > 0) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.debt", Credits.format(s.debt())), x, y, BAD, false);
            y += 11;
        }
        graphics.text(this.font, Component.translatable("gui.universe.settlement.defense", SettlementText.number(Math.round(s.defense()))), x, y,
                DIM, false);
        y += 11;
        long now = this.now();
        graphics.text(this.font, Component.translatable("gui.universe.settlement.day", s.day() - s.claimedDay() + 1,
                SettlementText.clock(SettlementRules.DAY - Math.floorMod(now, SettlementRules.DAY))), x, y, DIM, false);
        y += 11;
        int raid = this.data.getIntOr("raid", 0);
        if (raid > 0) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.raid_now", SettlementText.clock(raid)), x, y, BAD, false);
        } else if (s.raidAt() >= 0) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.raid_tonight", SettlementText.clock(s.raidAt() - now)), x, y,
                    BAD, false);
        }
        // 右边：最近的事
        int rx = this.left + 206;
        int ry = this.top + 48;
        if (!this.mine()) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.governor", s.ownerName()), rx, ry, WARN, false);
            ry += 13;
        }
        graphics.text(this.font, Component.translatable("gui.universe.settlement.events"), rx, ry, ACCENT, false);
        ry += 13;
        List<Settlement.Event> events = s.events();
        if (events.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.no_events"), rx, ry, DIM, false);
        }
        for (int i = events.size() - 1; i >= 0 && ry < this.top + HEIGHT - 52; i--) {
            Settlement.Event event = events.get(i);
            String day = Component.translatable("gui.universe.settlement.event_day", event.day() - s.claimedDay() + 1).getString();
            graphics.text(this.font, day, rx, ry, DIM, false);
            int dx = this.font.width(day) + 4;
            graphics.text(this.font, this.font.plainSubstrByWidth(SettlementText.event(event).getString(), WIDTH - 216 - dx), rx + dx, ry, TEXT,
                    false);
            ry += 11;
        }
        if (this.mine() && !this.near()) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.too_far"), this.left + 10, this.top + HEIGHT - 39, WARN, false);
        }
    }

    /** 一条带数的横条。 */
    private void bar(GuiGraphicsExtractor graphics, int x, int y, Component label, double value, double max, int color) {
        graphics.text(this.font, label, x, y, DIM, false);
        int bx = x + 52;
        int width = 90;
        graphics.fill(bx, y + 1, bx + width, y + 8, 0xFF1A2428);
        int filled = (int) Math.round(width * Math.clamp(max <= 0 ? 0 : value / max, 0, 1));
        graphics.fill(bx, y + 1, bx + filled, y + 8, color);
        String text = max == 100 ? Long.toString(Math.round(value)) : (int) Math.floor(value + 1e-9) + "/" + (int) max;
        graphics.text(this.font, text, bx + width + 6, y, TEXT, false);
    }

    // ---- 建筑

    private void initBuildings() {
        Settlement s = this.settlement;
        if (s == null || !this.mine()) {
            return;
        }
        for (int i = 0; i < s.plotCount(); i++) {
            int index = i;
            int y = this.top + 47 + i * 21;
            this.button(Component.translatable(s.plot(i).type() == null ? "gui.universe.settlement.build_here" : "gui.universe.settlement.manage"),
                    this.left + WIDTH - 70, y, 62, () -> {
                        this.focus = index;
                        this.select(Tab.CONSTRUCTION);
                    });
        }
    }

    private void drawBuildings(GuiGraphicsExtractor graphics, Settlement s) {
        for (int i = 0; i < s.plotCount(); i++) {
            Plot plot = s.plot(i);
            int y = this.top + 46 + i * 21;
            graphics.fill(this.left + 6, y, this.left + WIDTH - 6, y + 20, ROW);
            graphics.text(this.font, "#" + (i + 1), this.left + 10, y + 6, DIM, false);
            if (plot.type() == null) {
                graphics.text(this.font, Component.translatable("gui.universe.settlement.empty_plot"), this.left + 30, y + 6, DIM, false);
                continue;
            }
            MutableComponent name = SettlementText.building(plot.type()).copy().append(" " + BuildingType.letter(plot.grade()));
            graphics.text(this.font, name, this.left + 30, y + 6, TEXT, false);
            Component status = this.status(plot);
            graphics.text(this.font, status, this.left + 120, y + 6, this.statusColor(plot), false);
            String effects = SettlementText.effectLine(plot.type(), plot.grade()).getString();
            graphics.text(this.font, this.font.plainSubstrByWidth(effects, WIDTH - 210 - 80), this.left + 200, y + 6, DIM, false);
        }
        graphics.text(this.font, Component.translatable("gui.universe.settlement.buildings_total", s.buildings(), Credits.format(s.upkeep())),
                this.left + 10, this.top + HEIGHT - 14 - 10, DIM, false);
    }

    private Component status(Plot plot) {
        return switch (plot.phase()) {
            case EMPTY -> Component.translatable("gui.universe.settlement.status.empty");
            case DELIVER -> Component.translatable("gui.universe.settlement.status.deliver", plot.stage() + 1, BuildingType.STAGES);
            case WAIT -> Component.translatable("gui.universe.settlement.status.wait", plot.stage() + 1, BuildingType.STAGES);
            case OPENING -> Component.translatable("gui.universe.settlement.status.opening");
            case ACTIVE -> Component.translatable(plot.upgrading() ? "gui.universe.settlement.status.upgrading" : "gui.universe.settlement.status.active");
            case DAMAGED -> Component.translatable("gui.universe.settlement.status.damaged");
        };
    }

    private int statusColor(Plot plot) {
        return switch (plot.phase()) {
            case EMPTY -> DIM;
            case DELIVER, WAIT -> WARN;
            case OPENING -> ACCENT;
            case ACTIVE -> GOOD;
            case DAMAGED -> BAD;
        };
    }

    // ---- 施工

    private int mapX() {
        return this.left + 8;
    }

    private int mapY() {
        return this.top + 48;
    }

    private void initConstruction(Settlement s) {
        // 平面图上的地基：北在上
        int cx = this.mapX() + MAP_SIZE / 2;
        int cz = this.mapY() + MAP_SIZE / 2;
        int size = (SettlementLayout.PLOT_HALF * 2 + 1) * MAP_SCALE;
        for (int i = 0; i < s.plotCount(); i++) {
            int[] w = SettlementLayout.plotWorld(s.landmark(), i, 0, 0);
            int px = cx + (w[0] - s.x()) * MAP_SCALE - size / 2;
            int pz = cz + (w[1] - s.z()) * MAP_SCALE - size / 2;
            int index = i;
            Button button = Button.builder(Component.literal(Integer.toString(i + 1)), b -> {
                this.focus = index;
                this.armed = null;
                this.rebuildWidgets();
            }).bounds(px + 2, pz + 2, size - 4, size - 4).build();
            Plot plot = s.plot(i);
            if (plot.type() != null) {
                button.setTooltip(Tooltip.create(SettlementText.building(plot.type()).copy().append(" " + BuildingType.letter(plot.grade()))
                        .append("\n").append(this.status(plot))));
            }
            this.addRenderableWidget(button);
            this.plotButtons.add(new int[] {px, pz, size, i});
        }
        if (this.focus < 0 || this.focus >= s.plotCount()) {
            return;
        }
        Plot plot = s.plot(this.focus);
        int x = this.panelX();
        int width = WIDTH - (x - this.left) - 8;
        boolean act = this.canAct();
        switch (plot.phase()) {
            case EMPTY -> {
                int column = (width - 4) / 2;
                BuildingType[] types = BuildingType.values();
                for (int k = 0; k < types.length; k++) {
                    BuildingType type = types[k];
                    Button button = this.button(SettlementText.building(type), x + (k % 2) * (column + 4), this.top + 64 + (k / 2) * 21, column,
                            () -> this.send(SettlementPayloads.Action.START, type.ordinal()));
                    button.active = act && s.canStart(type);
                    button.setTooltip(Tooltip.create(this.typeTooltip(s, type)));
                }
            }
            case DELIVER, DAMAGED -> {
                Button deliver = this.button(Component.translatable("gui.universe.settlement.deliver"), x, this.top + HEIGHT - 44, 110,
                        () -> this.send(SettlementPayloads.Action.DELIVER, 0));
                deliver.active = act && this.canDeliver(plot);
                if (plot.phase() == Plot.Phase.DELIVER) {
                    this.confirm("cancel" + this.focus, Component.translatable("gui.universe.settlement.cancel"), x + width - 100, this.top + HEIGHT - 44,
                            100, () -> this.send(SettlementPayloads.Action.CANCEL, 0)).active = act;
                }
            }
            case WAIT -> this.confirm("cancel" + this.focus, Component.translatable("gui.universe.settlement.cancel"), x + width - 100,
                    this.top + HEIGHT - 44, 100, () -> this.send(SettlementPayloads.Action.CANCEL, 0)).active = act;
            case OPENING -> this.button(Component.translatable("gui.universe.settlement.open"), x, this.top + HEIGHT - 44, 110,
                    () -> this.send(SettlementPayloads.Action.OPEN, 0)).active = act;
            case ACTIVE -> {
                if (plot.upgrading()) {
                    if (plot.readyAt() <= 0) {
                        Button deliver = this.button(Component.translatable("gui.universe.settlement.deliver"), x, this.top + HEIGHT - 44, 110,
                                () -> this.send(SettlementPayloads.Action.DELIVER, 0));
                        deliver.active = act && this.canDeliver(plot);
                    }
                    this.confirm("cancel" + this.focus, Component.translatable("gui.universe.settlement.cancel_upgrade"), x + width - 100,
                            this.top + HEIGHT - 44, 100, () -> this.send(SettlementPayloads.Action.CANCEL, 0)).active = act;
                } else if (plot.grade() < BuildingType.GRADES - 1) {
                    this.button(Component.translatable("gui.universe.settlement.upgrade", BuildingType.letter(plot.grade() + 1)), x,
                            this.top + HEIGHT - 44, 110, () -> this.send(SettlementPayloads.Action.UPGRADE, 0)).active = act;
                }
            }
        }
    }

    private int panelX() {
        return this.mapX() + MAP_SIZE + 10;
    }

    private boolean canDeliver(Plot plot) {
        Player player = this.minecraft != null ? this.minecraft.player : null;
        if (player == null) {
            return false;
        }
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        for (Cost cost : plot.costs()) {
            if (plot.missing(cost) > 0 && Materials.count(player, cost) > 0) {
                return true;
            }
        }
        return false;
    }

    /** 选建筑的按钮上指着看：占地、作用、第一段的材料，盖不了的原因。 */
    private Component typeTooltip(Settlement s, BuildingType type) {
        MutableComponent text = SettlementText.building(type).copy();
        text.append("\n").append(Component.translatable("gui.universe.settlement.size", type.halfX() * 2 + 1, type.halfZ() * 2 + 1));
        for (Component line : SettlementText.effects(type, 0)) {
            text.append("\n").append(line);
        }
        text.append("\n").append(Component.translatable("gui.universe.settlement.first_stage"));
        for (Cost cost : type.stageCost(0)) {
            text.append("\n  ").append(Component.translatable("gui.universe.settlement.cost", Materials.name(cost), cost.count()));
        }
        if (!s.canStart(type)) {
            text.append("\n").append(Component.translatable(type.unique() && s.planned(type) ? "gui.universe.settlement.only_one"
                    : "gui.universe.settlement.office_first"));
        }
        return text;
    }

    private void drawMap(GuiGraphicsExtractor graphics, Settlement s) {
        int x0 = this.mapX();
        int y0 = this.mapY();
        graphics.fill(x0, y0, x0 + MAP_SIZE, y0 + MAP_SIZE, 0xFF10181B);
        int cx = x0 + MAP_SIZE / 2;
        int cz = y0 + MAP_SIZE / 2;
        if (this.roads == null) {
            List<int[]> cells = new ArrayList<>();
            for (int[] cell : SettlementLayout.roads(s.landmark())) {
                cells.add(SettlementLayout.world(s.landmark(), cell[0], cell[1]));
            }
            this.roads = cells;
        }
        for (int[] road : this.roads) {
            int dx = road[0] - s.x();
            int dz = road[1] - s.z();
            if (Math.abs(dx) < MAP_RANGE && Math.abs(dz) < MAP_RANGE) {
                int px = cx + dx * MAP_SCALE;
                int pz = cz + dz * MAP_SCALE;
                graphics.fill(px, pz, px + MAP_SCALE, pz + MAP_SCALE, 0xFF34444A);
            }
        }
        // 原来就有的小屋和交易棚
        int hut = SettlementLayout.HUT_HALF * MAP_SCALE;
        for (int[] local : SettlementLayout.huts(s.landmark())) {
            int[] w = SettlementLayout.world(s.landmark(), local[0], local[1]);
            int px = cx + (w[0] - s.x()) * MAP_SCALE;
            int pz = cz + (w[1] - s.z()) * MAP_SCALE;
            graphics.fill(px - hut, pz - hut, px + hut + MAP_SCALE, pz + hut + MAP_SCALE, 0xFF26323A);
        }
        int plaza = SettlementLayout.PLAZA * MAP_SCALE;
        graphics.fill(cx - plaza, cz - plaza, cx + plaza, cz + plaza, 0xFF3E5258);
        graphics.centeredText(this.font, Component.translatable("gui.universe.settlement.plaza"), cx, cz - 4, DIM);
        for (int[] b : this.plotButtons) {
            Plot plot = s.plot(b[3]);
            int color = b[3] == this.focus ? ACCENT : this.statusColor(plot);
            graphics.outline(b[0], b[1], b[2], b[2], color);
            graphics.outline(b[0] + 1, b[1] + 1, b[2] - 2, b[2] - 2, color & 0x80FFFFFF);
        }
        // 自己站在哪
        Player player = this.minecraft != null ? this.minecraft.player : null;
        if (player != null && this.near()) {
            int dx = (int) Math.floor(player.getX()) - s.x();
            int dz = (int) Math.floor(player.getZ()) - s.z();
            if (Math.abs(dx) < MAP_RANGE && Math.abs(dz) < MAP_RANGE) {
                int px = cx + dx * MAP_SCALE;
                int pz = cz + dz * MAP_SCALE;
                graphics.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFFFFFFFF);
            }
        }
        graphics.text(this.font, "N", x0 + 3, y0 + 3, DIM, false);
    }

    private void drawConstruction(GuiGraphicsExtractor graphics, Settlement s) {
        this.drawMap(graphics, s);
        int x = this.panelX();
        int y = this.top + 48;
        int width = WIDTH - (x - this.left) - 8;
        if (this.focus < 0 || this.focus >= s.plotCount()) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("gui.universe.settlement.pick_plot"), width)) {
                graphics.text(this.font, line, x, y, DIM, false);
                y += 10;
            }
            return;
        }
        Plot plot = s.plot(this.focus);
        long now = this.now();
        if (plot.type() == null) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.plot_empty", this.focus + 1), x, y, ACCENT, false);
            if (!s.has(BuildingType.OFFICE) && !s.planned(BuildingType.OFFICE)) {
                graphics.text(this.font, Component.translatable("gui.universe.settlement.office_hint"), x, this.top + 176, WARN, false);
            }
            return;
        }
        MutableComponent header = Component.literal("#" + (this.focus + 1) + " ").append(SettlementText.building(plot.type()))
                .append(" " + BuildingType.letter(plot.grade()));
        graphics.text(this.font, header, x, y, ACCENT, false);
        graphics.text(this.font, this.status(plot), x + width - this.font.width(this.status(plot)), y, this.statusColor(plot), false);
        y += 14;
        switch (plot.phase()) {
            case DELIVER -> {
                graphics.text(this.font, Component.translatable("gui.universe.settlement.stage", plot.stage() + 1, BuildingType.STAGES,
                        Component.translatable("gui.universe.settlement.stage." + STAGE_KEYS[Math.clamp(plot.stage(), 0, 2)])), x, y, TEXT, false);
                y += 14;
                this.costs(graphics, x, y, plot.costs(), plot);
            }
            case DAMAGED -> {
                graphics.text(this.font, Component.translatable("gui.universe.settlement.repair"), x, y, BAD, false);
                y += 14;
                this.costs(graphics, x, y, plot.costs(), plot);
            }
            case WAIT -> {
                graphics.text(this.font, Component.translatable("gui.universe.settlement.stage", plot.stage() + 1, BuildingType.STAGES,
                        Component.translatable("gui.universe.settlement.stage." + STAGE_KEYS[Math.clamp(plot.stage(), 0, 2)])), x, y, TEXT, false);
                y += 14;
                this.progress(graphics, x, y, width, plot, now);
            }
            case OPENING -> {
                for (FormattedCharSequence line : this.font.split(Component.translatable("gui.universe.settlement.ready_to_open"), width)) {
                    graphics.text(this.font, line, x, y, TEXT, false);
                    y += 10;
                }
                y += 4;
                this.effects(graphics, x, y, plot);
            }
            case ACTIVE -> {
                y = this.effects(graphics, x, y, plot);
                y += 6;
                if (plot.upgrading()) {
                    graphics.text(this.font, Component.translatable("gui.universe.settlement.upgrading_to", BuildingType.letter(plot.grade() + 1)), x, y,
                            WARN, false);
                    y += 14;
                    if (plot.readyAt() > 0) {
                        this.progress(graphics, x, y, width, plot, now);
                    } else {
                        this.costs(graphics, x, y, plot.costs(), plot);
                    }
                } else if (plot.grade() < BuildingType.GRADES - 1) {
                    graphics.text(this.font, Component.translatable("gui.universe.settlement.next_grade", BuildingType.letter(plot.grade() + 1),
                            SettlementText.number(BuildingType.factor(plot.grade() + 1))), x, y, DIM, false);
                    y += 13;
                    this.costs(graphics, x, y, BuildingType.upgradeCost(plot.grade() + 1), null);
                } else {
                    graphics.text(this.font, Component.translatable("gui.universe.settlement.max_grade"), x, y, DIM, false);
                }
            }
            case EMPTY -> {
            }
        }
    }

    private int effects(GuiGraphicsExtractor graphics, int x, int y, Plot plot) {
        for (Component line : SettlementText.effects(plot.type(), plot.grade())) {
            graphics.text(this.font, line, x, y, GOOD, false);
            y += 11;
        }
        return y;
    }

    private void progress(GuiGraphicsExtractor graphics, int x, int y, int width, Plot plot, long now) {
        double fraction = plot.progress(now);
        graphics.fill(x, y, x + width, y + 8, 0xFF1A2428);
        graphics.fill(x, y, x + (int) Math.round(width * fraction), y + 8, ACCENT);
        y += 12;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.building_now", SettlementText.clock(plot.readyAt() - now)), x, y,
                TEXT, false);
    }

    // ---- 决定

    private void initDecisions(Settlement s) {
        Optional<Decisions.Decision> pending = s.decision();
        if (pending.isEmpty()) {
            return;
        }
        Decisions.Decision decision = pending.get();
        int y = this.optionsTop(decision);
        Player player = this.minecraft != null ? this.minecraft.player : null;
        long credits = player != null && player.hasInfiniteMaterials() ? Long.MAX_VALUE : this.credits();
        for (int i = 0; i < decision.options().size(); i++) {
            int option = i;
            Decisions.Effect effect = decision.options().get(i);
            Button button = Button.builder(Component.translatable(decision.optionKey(i)), b -> this.send(SettlementPayloads.Action.DECIDE, option))
                    .bounds(this.left + 10, y, WIDTH - 20, 18).build();
            button.active = this.canAct() && s.affordable(effect, credits);
            this.addRenderableWidget(button);
            y += 31;
        }
    }

    /** 选项从哪一行开始：问题有几行。 */
    private int optionsTop(Decisions.Decision decision) {
        int lines = this.font.split(Component.translatable(decision.key()), WIDTH - 20).size();
        return this.top + 48 + 13 + lines * 10 + 8;
    }

    private void drawDecisions(GuiGraphicsExtractor graphics, Settlement s) {
        int x = this.left + 10;
        int y = this.top + 48;
        if (!s.has(BuildingType.OFFICE)) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("gui.universe.settlement.no_office"), WIDTH - 20)) {
                graphics.text(this.font, line, x, y, DIM, false);
                y += 10;
            }
            return;
        }
        Optional<Decisions.Decision> pending = s.decision();
        if (pending.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.decided_today"), x, y, TEXT, false);
            y += 12;
            graphics.text(this.font, Component.translatable("gui.universe.settlement.next_day",
                    SettlementText.clock(SettlementRules.DAY - Math.floorMod(this.now(), SettlementRules.DAY))), x, y, DIM, false);
            return;
        }
        Decisions.Decision decision = pending.get();
        MutableComponent title = SettlementText.kind(decision.kind()).copy().append(" · ")
                .append(Component.translatable(decision.key() + ".title"));
        graphics.text(this.font, title, x, y, ACCENT, false);
        y += 13;
        for (FormattedCharSequence line : this.font.split(Component.translatable(decision.key()), WIDTH - 20)) {
            graphics.text(this.font, line, x, y, TEXT, false);
            y += 10;
        }
        int oy = this.optionsTop(decision);
        for (int i = 0; i < decision.options().size(); i++) {
            graphics.text(this.font, SettlementText.effect(decision.options().get(i)), x + 4, oy + 20, DIM, false);
            oy += 31;
        }
    }

    // ---- 生产

    private void initProduction(Settlement s) {
        boolean act = this.canAct();
        int x = this.left + 10;
        int y = this.top + 60;
        Product current = s.effectiveProduct();
        for (Product product : s.products()) {
            MutableComponent label = product == current ? Component.literal("▶ ").append(SettlementText.product(product))
                    : SettlementText.product(product).copy();
            Button button = this.button(label, x, y, 140, () -> this.send(SettlementPayloads.Action.PRODUCT, product.ordinal()));
            button.active = act && product != current;
            y += 21;
        }
        int rx = this.left + 170;
        int width = WIDTH - 178;
        int batches = (int) Math.min(64, Math.floor(s.reserve() / current.units() + 1e-9));
        Button withdraw = this.button(current.credits()
                ? Component.translatable("gui.universe.settlement.withdraw_credits", Credits.format((long) batches * current.items()))
                : Component.translatable("gui.universe.settlement.withdraw", batches * current.items()), rx, this.top + 92, (width - 4) / 2,
                () -> this.send(SettlementPayloads.Action.WITHDRAW, 0));
        withdraw.active = act && batches > 0;
        long value = (long) Math.floor(s.reserve() + 1e-9) * current.unitValue();
        Button sell = this.button(Component.translatable("gui.universe.settlement.sell", Credits.format(value)), rx + (width - 4) / 2 + 4,
                this.top + 92, (width - 4) / 2, () -> this.send(SettlementPayloads.Action.SELL, 0));
        sell.active = act && value > 0;
        Button treasury = this.button(Component.translatable("gui.universe.settlement.take_treasury"), rx + width - 80, this.top + 116, 80,
                () -> this.send(SettlementPayloads.Action.TREASURY, 0));
        treasury.active = act && s.treasury() > 0;
        Button stock = this.button(Component.translatable("gui.universe.settlement.take_stock"), rx + width - 80, this.top + HEIGHT - 44, 80,
                () -> this.send(SettlementPayloads.Action.STOCK, 0));
        stock.active = act && !this.stock.isEmpty();
    }

    private void drawProduction(GuiGraphicsExtractor graphics, Settlement s, int mouseX, int mouseY) {
        int x = this.left + 10;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.products"), x, this.top + 48, ACCENT, false);
        Product current = s.effectiveProduct();
        int rx = this.left + 170;
        int y = this.top + 48;
        double perDay = s.productivity() / 10;
        Component output = current.credits()
                ? Component.translatable("gui.universe.settlement.output_credits", SettlementText.number(perDay * current.items() / current.units()))
                : Component.translatable("gui.universe.settlement.output", SettlementText.number(perDay * current.items() / current.units()),
                        Component.translatable("item." + current.item().replace(':', '.')));
        graphics.text(this.font, output, rx, y, TEXT, false);
        y += 14;
        this.bar(graphics, rx, y, Component.translatable("gui.universe.settlement.reserve"), s.reserve(), s.reserveCap(), GOOD);
        y += 13;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.unit_value", Credits.format(current.unitValue())), rx, y, DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.settlement.treasury", Credits.format(s.treasury())), rx, this.top + 121, TEXT,
                false);
        // 鱼塘、瞭望塔攒下的
        int sy = this.top + 142;
        graphics.text(this.font, Component.translatable("gui.universe.settlement.stock"), rx, sy, ACCENT, false);
        sy += 12;
        if (this.stock.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.settlement.stock_empty"), rx, sy, DIM, false);
        }
        for (int i = 0; i < this.stock.size(); i++) {
            ItemStack stack = this.stock.get(i);
            int ix = rx + (i % 9) * 18;
            int iy = sy + (i / 9) * 18;
            graphics.fill(ix, iy, ix + 17, iy + 17, ROW);
            graphics.fakeItem(stack, ix, iy);
            graphics.itemDecorations(this.font, stack, ix, iy);
            if (mouseX >= ix && mouseX < ix + 17 && mouseY >= iy && mouseY < iy + 17) {
                graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
            }
        }
    }

    // ---- 我的聚落

    private void drawMine(GuiGraphicsExtractor graphics) {
        int x = this.left + 10;
        int y = this.top + 48;
        List<CompoundTag> rows = new ArrayList<>();
        for (Tag tag : this.data.getListOrEmpty("list")) {
            if (tag instanceof CompoundTag row) {
                rows.add(row);
            }
        }
        graphics.text(this.font, Component.translatable("gui.universe.settlement.mine_count", rows.size(), SettlementRules.MAX_OWNED), x, y, DIM,
                false);
        y += 14;
        if (rows.isEmpty()) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("gui.universe.settlement.mine_none"), WIDTH - 20)) {
                graphics.text(this.font, line, x, y, DIM, false);
                y += 10;
            }
            return;
        }
        for (CompoundTag row : rows) {
            graphics.fill(this.left + 6, y, this.left + WIDTH - 6, y + 37, ROW);
            boolean here = row.getStringOr("id", "").equals(this.id());
            MutableComponent name = Component.literal(pickName(row.getStringOr("name", ""))).append(" · ")
                    .append(Component.literal(this.place(row))).append(" · ")
                    .append(Component.translatable("gui.universe.settlement.class_short", row.getStringOr("class", "C")));
            graphics.text(this.font, name, x, y + 3, here ? ACCENT : TEXT, false);
            Component coords = Component.literal(row.getIntOr("x", 0) + ", " + row.getIntOr("z", 0));
            graphics.text(this.font, coords, this.left + WIDTH - 10 - this.font.width(coords), y + 3, DIM, false);
            Component values = Component.translatable("gui.universe.settlement.row", row.getIntOr("population", 0), row.getIntOr("housing", 0),
                    row.getIntOr("happiness", 0), row.getIntOr("threat", 0), row.getIntOr("reserve", 0), row.getIntOr("cap", 0),
                    row.getIntOr("buildings", 0));
            graphics.text(this.font, values, x, y + 14, DIM, false);
            int fx = x;
            long debt = row.getLongOr("debt", 0);
            if (debt > 0) {
                Component text = Component.translatable("gui.universe.settlement.debt", Credits.format(debt));
                graphics.text(this.font, text, fx, y + 25, BAD, false);
                fx += this.font.width(text) + 10;
            }
            if (row.getBooleanOr("decision", false)) {
                Component text = Component.translatable("gui.universe.settlement.flag.decision");
                graphics.text(this.font, text, fx, y + 25, WARN, false);
                fx += this.font.width(text) + 10;
            }
            if (row.getBooleanOr("raid", false)) {
                graphics.text(this.font, Component.translatable("gui.universe.settlement.flag.raid"), fx, y + 25, BAD, false);
            }
            y += 40;
        }
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + HEIGHT + 1, 0xFF2E6E7E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, 0xF0080C0E);
        Settlement s = this.settlement;
        Component heading = s == null ? this.title : Component.literal(pickName(this.data.getStringOr("name", "")))
                .append(Component.literal(this.place(this.data).isEmpty() ? "" : " · " + this.place(this.data)));
        graphics.text(this.font, heading, this.left + 8, this.top + 8, ACCENT, false);
        Component credits = Component.translatable("gui.universe.dealer.credits", Credits.format(this.credits()));
        graphics.text(this.font, credits, this.left + WIDTH - 8 - this.font.width(credits), this.top + 8, TEXT, false);
        switch (this.tab) {
            case OVERVIEW -> {
                if (s != null && !s.owned()) {
                    this.drawClaim(graphics, s);
                } else if (s != null) {
                    this.drawOverview(graphics, s);
                }
            }
            case BUILDINGS -> {
                if (s != null) {
                    this.drawBuildings(graphics, s);
                }
            }
            case CONSTRUCTION -> {
                if (s != null) {
                    this.drawConstruction(graphics, s);
                }
            }
            case DECISIONS -> {
                if (s != null) {
                    this.drawDecisions(graphics, s);
                }
            }
            case PRODUCTION -> {
                if (s != null) {
                    this.drawProduction(graphics, s, mouseX, mouseY);
                }
            }
            case MINE -> this.drawMine(graphics);
        }
        if (this.notice != null && this.ticks < this.noticeUntil) {
            graphics.text(this.font, this.font.plainSubstrByWidth(this.notice.getString(), WIDTH - 16), this.left + 8, this.top + HEIGHT - 13, WARN,
                    false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
