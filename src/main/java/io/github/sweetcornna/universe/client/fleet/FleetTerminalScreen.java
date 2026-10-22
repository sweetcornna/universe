package io.github.sweetcornna.universe.client.fleet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.fleet.Area;
import io.github.sweetcornna.universe.core.fleet.Expedition;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.fleet.Frigate;
import io.github.sweetcornna.universe.core.fleet.FrigateTrait;
import io.github.sweetcornna.universe.core.fleet.HiveSortie;
import io.github.sweetcornna.universe.network.FleetPayloads;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 舰队终端（ADR-0024），四页：
 * <ul>
 * <li>护卫舰：每艘的种类、等级、四项属性、燃料、特性（指上去看），受损的能修，停着的能解散（点两下）；</li>
 * <li>远征任务：今天的三个任务，选一个，再点选要派的护卫舰（最多 5 艘），下面算好每段的成功率和要付的发射燃料；</li>
 * <li>在外面：每支远征的进度和已经揭开的那几段，回来了就领；</li>
 * <li>清剿（ADR-0028 §八）：去过的恒星系里的黑雾巢穴，挑一座、挑护卫舰派去打，只看战斗属性。</li>
 * </ul>
 * 属性和成功率由客户端按服务端发来的护卫舰还原以后算，能不能做由服务端说了算。界面开着时每 5 秒要一份新的。
 */
public class FleetTerminalScreen extends Screen {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 236;
    private static final int ACCENT = 0xFF6EDCF5;
    private static final int TEXT = 0xFFE6EEF0;
    private static final int DIM = 0xFF9AA8AE;
    private static final int GOOD = 0xFF7BE08A;
    private static final int WARN = 0xFFE8C060;
    private static final int BAD = 0xFFE07B7B;
    private static final int PAGE = 5;
    private static final int REFRESH = 100;
    /** 平均每段成功的机会低于这个时，出发要按两次。 */
    private static final double RISKY = 0.4;

    private enum Tab {
        FRIGATES, OFFERS, VOYAGES, HIVES
    }

    private FleetPayloads.Terminal state;
    private Tab tab = Tab.FRIGATES;
    private int page;
    private int offer = -1;
    /** 清剿页选中的巢穴所在的恒星系，没选时 -1。 */
    private int hive = -1;
    private int voyage;
    private final Set<Integer> team = new LinkedHashSet<>();
    private int confirmDismiss = -1;
    /** 成功率低时按过一次出发：记下当时的任务和队伍，变了就要重新确认。 */
    private @Nullable List<Integer> confirmLaunch;
    /** 收到这份内容时客户端的游戏刻，用来往后推算现在的游戏刻。 */
    private long received;
    private int ticks;
    private int left;
    private int top;

    public FleetTerminalScreen(FleetPayloads.Terminal state) {
        super(Component.translatable("gui.universe.fleet.title"));
        this.state = state;
    }

    public void update(FleetPayloads.Terminal state) {
        this.state = state;
        this.received = this.clientTime();
        // 派出去的、受损的、沉了的从挑选里去掉
        this.team.removeIf(id -> {
            FleetPayloads.FrigateInfo info = this.info(id);
            return info == null || info.busy() || info.damaged();
        });
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        this.received = this.received == 0 ? this.clientTime() : this.received;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        int x = this.left + 8;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable("gui.universe.fleet.tab." + t.name().toLowerCase(java.util.Locale.ROOT)),
                    b -> this.select(t)).bounds(x, this.top + 22, 88, 18).build();
            button.active = t != this.tab;
            this.addRenderableWidget(button);
            x += 92;
        }
        switch (this.tab) {
            case FRIGATES -> this.initFrigates();
            case OFFERS -> this.initOffers();
            case VOYAGES -> this.initVoyages();
            case HIVES -> this.initHives();
        }
    }

    private void select(Tab tab) {
        this.tab = tab;
        this.page = 0;
        this.confirmDismiss = -1;
        this.rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.ticks % REFRESH == 0) {
            send(FleetPayloads.Action.REFRESH, 0, List.of());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private long clientTime() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0;
    }

    /** 估计现在服务端的游戏刻。 */
    private long now() {
        return this.state.now() + Math.max(0, this.clientTime() - this.received);
    }

    private static void send(int action, int value, List<Integer> team) {
        ClientPacketDistributor.sendToServer(new FleetPayloads.Action(action, value, team));
    }

    private FleetPayloads.@Nullable FrigateInfo info(int id) {
        for (FleetPayloads.FrigateInfo info : this.state.frigates()) {
            if (info.id() == id) {
                return info;
            }
        }
        return null;
    }

    // ---- 护卫舰

    private int pages(int count) {
        return Math.max(1, (count + PAGE - 1) / PAGE);
    }

    private void initFrigates() {
        List<FleetPayloads.FrigateInfo> frigates = this.state.frigates();
        this.page = Math.min(this.page, this.pages(frigates.size()) - 1);
        int from = this.page * PAGE;
        for (int i = from; i < Math.min(frigates.size(), from + PAGE); i++) {
            FleetPayloads.FrigateInfo info = frigates.get(i);
            int y = this.top + 48 + (i - from) * 28;
            if (info.damaged() && !info.busy()) {
                Button repair = Button.builder(Component.translatable("gui.universe.fleet.repair", Credits.format(FreighterRules.REPAIR_COST)),
                        b -> send(FleetPayloads.Action.REPAIR, info.id(), List.of())).bounds(this.left + WIDTH - 172, y + 2, 92, 18).build();
                repair.active = this.state.credits() >= FreighterRules.REPAIR_COST;
                this.addRenderableWidget(repair);
            }
            if (!info.busy()) {
                boolean confirm = this.confirmDismiss == info.id();
                this.addRenderableWidget(Button.builder(Component.translatable(confirm ? "gui.universe.fleet.dismiss_confirm"
                        : "gui.universe.fleet.dismiss"), b -> {
                            if (this.confirmDismiss == info.id()) {
                                this.confirmDismiss = -1;
                                send(FleetPayloads.Action.DISMISS, info.id(), List.of());
                            } else {
                                this.confirmDismiss = info.id();
                                this.rebuildWidgets();
                            }
                        }).bounds(this.left + WIDTH - 76, y + 2, 68, 18).build());
            }
        }
        this.pager(frigates.size());
    }

    private void pager(int count) {
        if (this.pages(count) <= 1) {
            return;
        }
        int y = this.top + HEIGHT - 44;
        Button previous = Button.builder(Component.literal("<"), b -> this.turn(-1, count)).bounds(this.left + 8, y, 20, 18).build();
        Button next = Button.builder(Component.literal(">"), b -> this.turn(1, count)).bounds(this.left + 32, y, 20, 18).build();
        previous.active = this.page > 0;
        next.active = this.page < this.pages(count) - 1;
        this.addRenderableWidget(previous);
        this.addRenderableWidget(next);
    }

    private void turn(int delta, int count) {
        this.page = Math.max(0, Math.min(this.pages(count) - 1, this.page + delta));
        this.rebuildWidgets();
    }

    private static Component typeName(Frigate frigate) {
        return Component.translatable("gui.universe.fleet.type." + frigate.type().key());
    }

    private static Component areaName(Area area) {
        return Component.translatable("gui.universe.fleet.area." + area.key());
    }

    /** "战 19 探 3 工 0 贸 2"。 */
    private static Component stats(Frigate frigate) {
        MutableComponent line = Component.empty();
        for (Area area : Area.values()) {
            if (area.ordinal() > 0) {
                line.append(" ");
            }
            line.append(Component.translatable("gui.universe.fleet.stat." + area.key(), frigate.stat(area)));
        }
        return line;
    }

    private void drawFrigates(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<FleetPayloads.FrigateInfo> frigates = this.state.frigates();
        if (frigates.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.no_frigates"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        // 四个页签占满了一行，艘数放在标题后面
        graphics.text(this.font, Component.translatable("gui.universe.fleet.count", frigates.size(), FreighterRules.MAX_FRIGATES),
                this.left + 16 + this.font.width(this.title), this.top + 9, DIM, false);
        int from = this.page * PAGE;
        for (int i = from; i < Math.min(frigates.size(), from + PAGE); i++) {
            FleetPayloads.FrigateInfo info = frigates.get(i);
            Frigate frigate = info.frigate();
            int y = this.top + 48 + (i - from) * 28;
            graphics.fill(this.left + 6, y, this.left + WIDTH - 6, y + 24, 0x40203840);
            MutableComponent title = Component.translatable("gui.universe.fleet.frigate", frigate.designation(), typeName(frigate), frigate.rank());
            int color = TEXT;
            if (info.busy()) {
                title.append(" ").append(Component.translatable("gui.universe.fleet.busy"));
                color = DIM;
            } else if (info.damaged()) {
                title.append(" ").append(Component.translatable("gui.universe.fleet.damaged"));
                color = BAD;
            }
            graphics.text(this.font, title, this.left + 10, y + 3, color, false);
            MutableComponent detail = stats(frigate).copy().append(" · ")
                    .append(Component.translatable("gui.universe.fleet.fuel", frigate.fuelCost()));
            if (!frigate.traits().isEmpty()) {
                detail.append(" · ").append(Component.translatable("gui.universe.fleet.traits", frigate.traits().size()));
            }
            graphics.text(this.font, detail, this.left + 10, y + 14, DIM, false);
            if (mouseX >= this.left + 6 && mouseX < this.left + WIDTH - 180 && mouseY >= y && mouseY < y + 24 && !frigate.traits().isEmpty()) {
                List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                for (FrigateTrait trait : frigate.traits()) {
                    lines.add(traitLine(trait).getVisualOrderText());
                }
                graphics.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            }
        }
    }

    private static Component traitLine(FrigateTrait trait) {
        String amount = (trait.amount() > 0 ? "+" : "") + trait.amount();
        Component what = trait.area() == null ? Component.translatable("gui.universe.fleet.trait_fuel", amount)
                : Component.translatable("gui.universe.fleet.trait_stat", amount, areaName(trait.area()));
        return Component.translatable("gui.universe.fleet.trait", Component.translatable("gui.universe.fleet.trait." + trait.key()), what)
                .withColor(trait.good() ? GOOD : BAD);
    }

    // ---- 远征任务

    private void initOffers() {
        List<FleetPayloads.OfferInfo> offers = this.state.offers();
        for (int i = 0; i < offers.size(); i++) {
            FleetPayloads.OfferInfo info = offers.get(i);
            int index = i;
            Component label = info.taken() ? Component.translatable("gui.universe.fleet.offer_taken") : offerName(info.offer());
            Button row = Button.builder(label, b -> {
                this.offer = index;
                this.rebuildWidgets();
            }).bounds(this.left + 8, this.top + 48 + i * 24, 140, 20).build();
            row.active = !info.taken() && index != this.offer;
            this.addRenderableWidget(row);
        }
        if (this.offer < 0 || this.offer >= offers.size() || offers.get(this.offer).taken()) {
            return;
        }
        this.teamPicker();
        int fuel = Expedition.fuelItems(this.picked());
        boolean creative = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials();
        // 胜算低时按两次：第一次只换成确认的字样
        List<Integer> key = new ArrayList<>(this.team);
        key.add(this.offer);
        boolean risky = Expedition.expected(offers.get(this.offer).offer(), this.picked()) < RISKY;
        boolean confirm = risky && key.equals(this.confirmLaunch);
        Button launch = Button.builder(Component.translatable(confirm ? "gui.universe.fleet.launch_confirm" : "gui.universe.fleet.launch"), b -> {
            if (risky && !key.equals(this.confirmLaunch)) {
                this.confirmLaunch = key;
                this.rebuildWidgets();
                return;
            }
            this.confirmLaunch = null;
            send(FleetPayloads.Action.LAUNCH, this.offer, List.copyOf(this.team));
        }).bounds(this.left + WIDTH - 108, this.top + HEIGHT - 42, 100, 20).build();
        launch.active = !this.team.isEmpty() && (creative || this.state.fuel() >= fuel) && this.state.voyages().size() < Expedition.MAX_ACTIVE;
        this.addRenderableWidget(launch);
    }

    /** 右边点选要派的护卫舰，三列，最多 {@link Expedition#MAX_TEAM} 艘。 */
    private void teamPicker() {
        List<FleetPayloads.FrigateInfo> ready = this.ready();
        for (int i = 0; i < ready.size(); i++) {
            FleetPayloads.FrigateInfo info = ready.get(i);
            boolean picked = this.team.contains(info.id());
            Button pick = Button.builder(Component.translatable(picked ? "gui.universe.fleet.picked" : "gui.universe.fleet.pick",
                    info.designation(), Component.translatable("gui.universe.fleet.type_short." + info.frigate().type().key())), b -> {
                        if (!this.team.remove(info.id()) && this.team.size() < Expedition.MAX_TEAM) {
                            this.team.add(info.id());
                        }
                        this.rebuildWidgets();
                    }).bounds(this.left + 156 + (i % 3) * 74, this.top + 58 + (i / 3) * 20, 72, 18).build();
            pick.active = picked || this.team.size() < Expedition.MAX_TEAM;
            this.addRenderableWidget(pick);
        }
    }

    /** 能派出去的：停着、没受损。 */
    private List<FleetPayloads.FrigateInfo> ready() {
        List<FleetPayloads.FrigateInfo> ready = new ArrayList<>();
        for (FleetPayloads.FrigateInfo info : this.state.frigates()) {
            if (!info.busy() && !info.damaged() && ready.size() < 12) {
                ready.add(info);
            }
        }
        return ready;
    }

    private List<Frigate> picked() {
        List<Frigate> picked = new ArrayList<>();
        for (int id : this.team) {
            FleetPayloads.FrigateInfo info = this.info(id);
            if (info != null) {
                picked.add(info.frigate());
            }
        }
        return picked;
    }

    private static Component offerName(Expedition.Offer offer) {
        return Component.translatable("gui.universe.fleet.offer", Component.translatable("gui.universe.fleet.kind." + offer.kind().key()),
                "★".repeat(offer.difficulty()), offer.minutes());
    }

    private void drawOffers(GuiGraphicsExtractor graphics) {
        List<FleetPayloads.OfferInfo> offers = this.state.offers();
        if (offers.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.no_offers"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        if (this.offer < 0 || this.offer >= offers.size() || offers.get(this.offer).taken()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.pick_offer"), this.left + 156, this.top + 52, DIM, false);
            return;
        }
        Expedition.Offer offer = offers.get(this.offer).offer();
        graphics.text(this.font, Component.translatable("gui.universe.fleet.team", this.team.size(), Expedition.MAX_TEAM), this.left + 156,
                this.top + 46, ACCENT, false);
        if (this.ready().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.none_ready"), this.left + 156, this.top + 60, DIM, false);
        }
        List<Frigate> team = this.picked();
        int y = this.top + HEIGHT - 96;
        graphics.text(this.font, Component.translatable("gui.universe.fleet.segments", 2 + offer.difficulty(), Expedition.required(offer.difficulty())),
                this.left + 8, y, TEXT, false);
        y += 12;
        // 每一项的属性合计和一段的成功率；任务本身那一项标亮
        int x = this.left + 8;
        for (Area area : Area.values()) {
            int strength = Expedition.strength(team, area);
            double chance = Expedition.chance(strength, offer.difficulty());
            int color = chance >= 0.8 ? GOOD : chance >= 0.4 ? WARN : BAD;
            Component text = Component.translatable("gui.universe.fleet.chance", areaName(area), strength, Math.round(chance * 100));
            if (area == offer.kind().area()) {
                text = Component.literal("▶ ").append(text);
            }
            graphics.text(this.font, text, x, y, team.isEmpty() ? DIM : color, false);
            x += 92;
        }
        y += 12;
        if (!team.isEmpty()) {
            double expected = Expedition.expected(offer, team);
            int color = expected >= 0.8 ? GOOD : expected >= RISKY ? WARN : BAD;
            graphics.text(this.font, Component.translatable(expected < RISKY ? "gui.universe.fleet.expected_low" : "gui.universe.fleet.expected",
                    Math.round(expected * 100)), this.left + 8, y, color, false);
        }
        y += 12;
        int fuel = Expedition.fuelItems(team);
        boolean enough = this.state.fuel() >= fuel;
        graphics.text(this.font, Component.translatable("gui.universe.fleet.fuel_cost", fuel, this.state.fuel()), this.left + 8, y,
                enough || team.isEmpty() ? DIM : BAD, false);
        y += 12;
        if (this.state.voyages().size() >= Expedition.MAX_ACTIVE) {
            graphics.text(this.font, Component.translatable("message.universe.fleet.problem.too_many_voyages"), this.left + 8, y, BAD, false);
        }
    }

    // ---- 在外面

    private void initVoyages() {
        List<FleetPayloads.VoyageInfo> voyages = this.state.voyages();
        this.voyage = Math.min(this.voyage, Math.max(0, voyages.size() - 1));
        long now = this.now();
        for (int i = 0; i < voyages.size(); i++) {
            FleetPayloads.VoyageInfo info = voyages.get(i);
            int index = i;
            Button row = Button.builder(Component.translatable(now >= info.end() ? "gui.universe.fleet.voyage_back" : "gui.universe.fleet.voyage",
                    kindName(info), "★".repeat(info.difficulty())),
                    b -> {
                        this.voyage = index;
                        this.rebuildWidgets();
                    }).bounds(this.left + 8, this.top + 48 + i * 24, 120, 20).build();
            row.active = index != this.voyage;
            this.addRenderableWidget(row);
        }
        if (!voyages.isEmpty()) {
            FleetPayloads.VoyageInfo info = voyages.get(this.voyage);
            int index = this.voyage;
            Button claim = Button.builder(Component.translatable("gui.universe.fleet.claim"),
                    b -> send(FleetPayloads.Action.CLAIM, index, List.of())).bounds(this.left + WIDTH - 108, this.top + HEIGHT - 42, 100, 20).build();
            claim.active = now >= info.end();
            this.addRenderableWidget(claim);
        }
    }

    private void drawVoyages(GuiGraphicsExtractor graphics) {
        List<FleetPayloads.VoyageInfo> voyages = this.state.voyages();
        if (voyages.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.no_voyages"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        FleetPayloads.VoyageInfo info = voyages.get(Math.min(this.voyage, voyages.size() - 1));
        long now = this.now();
        int x = this.left + 140;
        int y = this.top + 48;
        List<String> names = new ArrayList<>();
        for (int id : info.team()) {
            FleetPayloads.FrigateInfo frigate = this.info(id);
            names.add(frigate == null ? "?" : frigate.designation());
        }
        FleetPayloads.@Nullable HiveInfo target = info.hive() >= 0 ? this.hiveInfo(info.hive()) : null;
        if (target != null) {
            graphics.text(this.font, hiveLabel(target), x, y, ACCENT, false);
            y += 12;
        }
        graphics.text(this.font, Component.translatable("gui.universe.fleet.voyage_team", String.join("、", names)), x, y, TEXT, false);
        y += 14;
        // 进度条
        double progress = Math.clamp((now - info.start()) / (double) Math.max(1, info.end() - info.start()), 0, 1);
        int bar = WIDTH - 148;
        graphics.fill(x, y, x + bar, y + 6, 0xFF1C2A31);
        graphics.fill(x, y, x + (int) Math.round(bar * progress), y + 6, now >= info.end() ? GOOD : ACCENT);
        y += 10;
        long left = Math.max(0, info.end() - now);
        Component when = now >= info.end() ? Component.translatable("gui.universe.fleet.back")
                : Component.translatable("gui.universe.fleet.remaining", left / 1200, (left / 20) % 60);
        graphics.text(this.font, when, x, y, now >= info.end() ? GOOD : DIM, false);
        y += 14;
        for (int i = 0; i < info.total(); i++) {
            if (i >= info.events().size()) {
                graphics.text(this.font, Component.translatable("gui.universe.fleet.event_pending", i + 1), x, y, DIM, false);
            } else {
                graphics.text(this.font, this.eventLine(i, info.events().get(i)), x, y, info.events().get(i).success() ? GOOD : BAD, false);
            }
            y += 11;
        }
    }

    private static Component kindName(FleetPayloads.VoyageInfo info) {
        Expedition.Kind[] kinds = Expedition.Kind.values();
        return Component.translatable("gui.universe.fleet.kind." + (info.hive() >= 0 ? "hive" : kinds[Math.clamp(info.kind(), 0, kinds.length - 1)].key()));
    }

    // ---- 清剿

    private FleetPayloads.@Nullable HiveInfo hiveInfo(int system) {
        for (FleetPayloads.HiveInfo info : this.state.hives()) {
            if (info.system() == system) {
                return info;
            }
        }
        return null;
    }

    /** "天苑四 12级 ★★"。 */
    private static Component hiveLabel(FleetPayloads.HiveInfo info) {
        return Component.translatable("gui.universe.fleet.hive", GalaxyText.pick(info.nameZh(), info.nameEn()), info.level(),
                "★".repeat(info.difficulty()));
    }

    private void initHives() {
        List<FleetPayloads.HiveInfo> hives = this.state.hives();
        for (int i = 0; i < hives.size(); i++) {
            FleetPayloads.HiveInfo info = hives.get(i);
            Button row = Button.builder(hiveLabel(info), b -> {
                this.hive = info.system();
                this.rebuildWidgets();
            }).bounds(this.left + 8, this.top + 48 + i * 22, 140, 20).build();
            row.active = info.system() != this.hive;
            this.addRenderableWidget(row);
        }
        FleetPayloads.HiveInfo target = this.hive >= 0 ? this.hiveInfo(this.hive) : null;
        if (target == null) {
            return;
        }
        this.teamPicker();
        int fuel = Expedition.fuelItems(this.picked());
        boolean creative = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials();
        // 胜算低时按两次，和远征任务一样；确认的记号用负的恒星系编号，免得和远征任务的序号混
        List<Integer> key = new ArrayList<>(this.team);
        key.add(-1 - target.system());
        boolean risky = HiveSortie.chance(this.picked(), target.difficulty()) < RISKY;
        boolean confirm = risky && key.equals(this.confirmLaunch);
        Button launch = Button.builder(Component.translatable(confirm ? "gui.universe.fleet.launch_confirm" : "gui.universe.fleet.launch"), b -> {
            if (risky && !key.equals(this.confirmLaunch)) {
                this.confirmLaunch = key;
                this.rebuildWidgets();
                return;
            }
            this.confirmLaunch = null;
            send(FleetPayloads.Action.SORTIE, target.system(), List.copyOf(this.team));
        }).bounds(this.left + WIDTH - 108, this.top + HEIGHT - 42, 100, 20).build();
        launch.active = !this.team.isEmpty() && (creative || this.state.fuel() >= fuel) && this.state.voyages().size() < Expedition.MAX_ACTIVE;
        this.addRenderableWidget(launch);
    }

    private void drawHives(GuiGraphicsExtractor graphics) {
        if (this.state.hives().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.no_hives"), this.left + 8, this.top + 52, DIM, false);
            return;
        }
        FleetPayloads.HiveInfo target = this.hive >= 0 ? this.hiveInfo(this.hive) : null;
        if (target == null) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.pick_hive"), this.left + 156, this.top + 52, DIM, false);
            return;
        }
        graphics.text(this.font, Component.translatable("gui.universe.fleet.team", this.team.size(), Expedition.MAX_TEAM), this.left + 156,
                this.top + 46, ACCENT, false);
        if (this.ready().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.fleet.none_ready"), this.left + 156, this.top + 60, DIM, false);
        }
        List<Frigate> team = this.picked();
        int d = target.difficulty();
        int y = this.top + HEIGHT - 96;
        graphics.text(this.font, Component.translatable("gui.universe.fleet.offer", Component.translatable("gui.universe.fleet.kind.hive"),
                "★".repeat(d), target.minutes()).copy().append(" · ").append(Component.translatable("gui.universe.fleet.segments", 2 + d,
                        Expedition.required(d))), this.left + 8, y, TEXT, false);
        y += 12;
        int strength = Expedition.strength(team, Area.COMBAT);
        double chance = HiveSortie.chance(team, d);
        int color = team.isEmpty() ? DIM : chance >= 0.8 ? GOOD : chance >= RISKY ? WARN : BAD;
        graphics.text(this.font, Component.translatable("gui.universe.fleet.chance", areaName(Area.COMBAT), strength, Math.round(chance * 100)),
                this.left + 8, y, color, false);
        y += 12;
        graphics.text(this.font, Component.translatable("gui.universe.fleet.hive_note"), this.left + 8, y, DIM, false);
        y += 12;
        int fuel = Expedition.fuelItems(team);
        boolean enough = this.state.fuel() >= fuel;
        graphics.text(this.font, Component.translatable("gui.universe.fleet.fuel_cost", fuel, this.state.fuel()), this.left + 8, y,
                enough || team.isEmpty() ? DIM : BAD, false);
        y += 12;
        if (this.state.voyages().size() >= Expedition.MAX_ACTIVE) {
            graphics.text(this.font, Component.translatable("message.universe.fleet.problem.too_many_voyages"), this.left + 8, y, BAD, false);
        }
    }

    /** "第 2 段：战斗，成功" / "第 3 段：探索，失败，KX-417 受损"。 */
    private Component eventLine(int index, FleetPayloads.EventInfo event) {
        Component area = areaName(Area.values()[Math.clamp(event.area(), 0, Area.values().length - 1)]);
        if (event.success()) {
            return Component.translatable("gui.universe.fleet.event_won", index + 1, area);
        }
        Expedition.Outcome outcome = Expedition.Outcome.values()[Math.clamp(event.outcome(), 0, Expedition.Outcome.values().length - 1)];
        if (outcome == Expedition.Outcome.NONE) {
            return Component.translatable("gui.universe.fleet.event_lost", index + 1, area);
        }
        FleetPayloads.FrigateInfo frigate = this.info(event.frigate());
        String name = frigate == null ? "#" + event.frigate() : frigate.designation();
        return Component.translatable("gui.universe.fleet.event_" + (outcome == Expedition.Outcome.LOST ? "sunk" : "hurt"), index + 1, area, name);
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
        graphics.text(this.font, this.title, this.left + 8, this.top + 9, ACCENT, false);
        Component credits = Component.translatable("gui.universe.dealer.credits", Credits.format(this.state.credits()));
        graphics.text(this.font, credits, this.left + WIDTH - 8 - this.font.width(credits), this.top + 9, TEXT, false);
        switch (this.tab) {
            case FRIGATES -> this.drawFrigates(graphics, mouseX, mouseY);
            case OFFERS -> this.drawOffers(graphics);
            case VOYAGES -> this.drawVoyages(graphics);
            case HIVES -> this.drawHives(graphics);
        }
        this.state.notice().ifPresent(notice -> graphics.text(this.font, notice, this.left + 8, this.top + HEIGHT - 14, DIM, false));
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
