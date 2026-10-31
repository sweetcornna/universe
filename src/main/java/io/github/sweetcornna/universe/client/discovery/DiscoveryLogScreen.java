package io.github.sweetcornna.universe.client.discovery;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.WARN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.fishing.FishingPage;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.core.sentinel.SentinelLevel;
import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.discovery.DiscoveryKey;
import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.core.discovery.Survey;
import io.github.sweetcornna.universe.core.ecology.CreatureSpecies;
import io.github.sweetcornna.universe.core.ecology.Fauna;
import io.github.sweetcornna.universe.core.ecology.Flora;
import io.github.sweetcornna.universe.core.ecology.PlantSpecies;
import io.github.sweetcornna.universe.core.ecology.TreeSpecies;
import io.github.sweetcornna.universe.core.galaxy.Galaxy;
import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.galaxy.PlanetType;
import io.github.sweetcornna.universe.core.galaxy.Star;
import io.github.sweetcornna.universe.core.galaxy.StarClass;
import io.github.sweetcornna.universe.core.galaxy.StarSystem;
import io.github.sweetcornna.universe.network.RenamePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 发现日志（ADR-0012）：左边是发现过的恒星系，中间是选中恒星系的恒星和天体（没发现的天体只写"未发现"），
 * 右边是详情：参数、发现者、星球上发现了哪些动植物和矿物。发现者可以在下面改名。
 */
public class DiscoveryLogScreen extends Screen {
    private static final int HEADER = 18;
    private static final int FOOTER = 14;
    private static final int PADDING = 6;
    private static final int SYSTEM_WIDTH = 124;
    private static final int BODY_WIDTH = 140;
    private static final int ROW = 20;
    /** 右下角改名那一块的高度。 */
    private static final int RENAME_HEIGHT = 40;
    private static final int BACKGROUND = 0xFF02050A;
    private static final int COLUMN = 0xC00A1218;
    private static final int LINE = 0x604FB4D0;
    private static final int GOLD = 0xFFFFD36B;
    private static final int GREEN = 0xFF7FE08A;

    /** 中间一列的一行：恒星（planet 为 null）或天体。 */
    private record BodyRow(@Nullable Planet planet, boolean moon) {
    }

    private final @Nullable Galaxy galaxy;
    private final Map<Planet, Survey.Census> censuses = new HashMap<>();
    private final Map<Planet, Fauna> faunas = new HashMap<>();
    private final Map<Planet, Flora> floras = new HashMap<>();
    private List<StarSystem> systems = List.of();
    private List<BodyRow> bodies = List.of();
    private int selectedSystem = -1;
    private @Nullable Planet selectedBody;
    private double systemScroll;
    private double bodyScroll;
    private double detailScroll;
    private int detailHeight;
    private int seenVersion = -1;
    private @Nullable EditBox nameBox;
    private @Nullable Button saveButton;
    /** 星球、星系备忘的入口（ADR-0033 §六）：发现过的才能写。 */
    private @Nullable Button memoButton;
    /** 改名框现在对着的东西；切换选中时清空重填。 */
    private @Nullable DiscoveryKey editing;
    /** 标题栏上的三页：发现、文明（ADR-0027）、钓鱼（ADR-0034）。 */
    private static final int TAB_LOG = 0;
    private static final int TAB_CIV = 1;
    private static final int TAB_FISHING = 2;
    private static final int TABS = 3;
    private int tab = TAB_LOG;
    private @Nullable CivPage civPage;
    private @Nullable FishingPage fishingPage;

    public DiscoveryLogScreen() {
        super(Component.translatable("gui.universe.discovery.title"));
        this.galaxy = ClientGalaxy.galaxy().orElse(null);
        OptionalInt current = ClientGalaxy.currentSystem();
        this.refresh();
        if (current.isPresent() && ClientDiscoveries.known(DiscoveryKey.system(current.getAsInt()))) {
            this.selectSystem(current.getAsInt());
        } else if (!this.systems.isEmpty()) {
            this.selectSystem(this.systems.getFirst().index());
        }
    }

    /** 直接翻到钓鱼图鉴那一页。 */
    public static DiscoveryLogScreen fishing() {
        DiscoveryLogScreen screen = new DiscoveryLogScreen();
        screen.tab = TAB_FISHING;
        return screen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int left = this.detailLeft() + PADDING;
        int width = this.width - left - PADDING;
        int y = this.height - FOOTER - RENAME_HEIGHT + 14;
        String value = this.nameBox == null ? "" : this.nameBox.getValue();
        this.nameBox = new EditBox(this.font, left, y, Math.max(40, width - 54), 16, Component.translatable("gui.universe.discovery.rename"));
        this.nameBox.setMaxLength(DiscoveryLog.MAX_NAME);
        this.nameBox.setValue(value);
        this.nameBox.setHint(Component.translatable("gui.universe.discovery.rename_hint"));
        this.addRenderableWidget(this.nameBox);
        this.saveButton = Button.builder(Component.translatable("gui.universe.discovery.save"), button -> this.save())
                .bounds(left + Math.max(40, width - 54) + 4, y - 2, 50, 20).build();
        this.addRenderableWidget(this.saveButton);
        this.memoButton = Button.builder(Component.translatable("gui.universe.memo.open"), button -> this.openMemo())
                .bounds(this.width - PADDING - 4 - 44, HEADER + 4, 44, 14).build();
        this.addRenderableWidget(this.memoButton);
        this.updateRename();
    }

    // ---- 数据

    private void refresh() {
        this.seenVersion = ClientDiscoveries.version();
        if (this.galaxy == null) {
            return;
        }
        List<DiscoveryLog.Entry> found = new ArrayList<>();
        for (DiscoveryLog.Entry entry : ClientDiscoveries.log().entries()) {
            if (entry.key().kind() == DiscoveryKey.Kind.SYSTEM) {
                found.add(entry);
            }
        }
        // 母星系在最上面，其余按发现的先后
        found.sort(Comparator.comparingInt((DiscoveryLog.Entry entry) -> entry.key().system() == GalaxyGenerator.HOME_SYSTEM ? 0 : 1)
                .thenComparingLong(DiscoveryLog.Entry::day)
                .thenComparingInt(entry -> entry.key().system()));
        List<StarSystem> list = new ArrayList<>();
        for (DiscoveryLog.Entry entry : found) {
            this.galaxy.system(entry.key().system()).ifPresent(list::add);
        }
        this.systems = list;
    }

    private void selectSystem(int index) {
        this.selectedSystem = index;
        this.selectedBody = null;
        this.bodyScroll = 0;
        this.detailScroll = 0;
        List<BodyRow> rows = new ArrayList<>();
        rows.add(new BodyRow(null, false));
        StarSystem system = this.system();
        if (system != null) {
            for (Planet planet : system.planets()) {
                rows.add(new BodyRow(planet, false));
                for (Planet moon : planet.moons()) {
                    rows.add(new BodyRow(moon, true));
                }
            }
        }
        this.bodies = rows;
        this.updateRename();
    }

    private void selectBody(@Nullable Planet planet) {
        this.selectedBody = planet;
        this.detailScroll = 0;
        this.updateRename();
    }

    private @Nullable StarSystem system() {
        return this.galaxy == null || this.selectedSystem < 0 ? null : this.galaxy.system(this.selectedSystem).orElse(null);
    }

    /** 右边详情对着的东西：选了天体就是天体，否则是恒星系。 */
    private @Nullable DiscoveryKey selectedKey() {
        if (this.selectedSystem < 0) {
            return null;
        }
        return this.selectedBody == null ? DiscoveryKey.system(this.selectedSystem) : DiscoveryKey.body(this.selectedBody.id());
    }

    private Survey.Census census(Planet planet) {
        return this.censuses.computeIfAbsent(planet, Survey::census);
    }

    private Fauna fauna(Planet planet) {
        return this.faunas.computeIfAbsent(planet, Fauna::of);
    }

    private Flora flora(Planet planet) {
        return this.floras.computeIfAbsent(planet, Flora::of);
    }

    /** 改名框：只有发现者看得到；换了选中的东西就填上它现在的自定义名字。 */
    private void updateRename() {
        if (this.nameBox == null || this.saveButton == null) {
            return;
        }
        DiscoveryKey key = this.selectedKey();
        Optional<DiscoveryLog.Entry> entry = key == null ? Optional.empty() : ClientDiscoveries.entry(key);
        boolean renamable = this.tab == TAB_LOG && entry.isPresent() && this.minecraft.player != null
                && entry.get().renamableBy(this.minecraft.player.getUUID());
        this.nameBox.visible = renamable;
        this.saveButton.visible = renamable;
        if (!Objects.equals(key, this.editing)) {
            this.editing = key;
            this.nameBox.setValue(entry.map(DiscoveryLog.Entry::customName).orElse(""));
            this.nameBox.setFocused(false);
        }
        String current = entry.map(DiscoveryLog.Entry::customName).orElse("");
        Optional<String> clean = DiscoveryLog.cleanName(this.nameBox.getValue());
        this.saveButton.active = renamable && clean.isPresent() && !clean.get().equals(current);
        if (this.memoButton != null) {
            this.memoButton.visible = this.tab == TAB_LOG && entry.isPresent();
        }
    }

    /** 打开选中的星球或星系的备忘；服务端回了内容才真正打开，关掉回到这里。 */
    private void openMemo() {
        StarSystem system = this.system();
        if (system == null) {
            return;
        }
        String key = this.selectedBody == null ? io.github.sweetcornna.universe.core.beacon.MemoKeys.system(system.index())
                : io.github.sweetcornna.universe.core.beacon.MemoKeys.body(this.selectedBody.id());
        String title = this.selectedBody == null ? GalaxyText.system(system) : GalaxyText.planet(this.selectedBody);
        io.github.sweetcornna.universe.client.beacon.BeaconClient.openMemo(key, Component.literal(title), this);
    }

    private void save() {
        DiscoveryKey key = this.selectedKey();
        if (key == null || this.nameBox == null) {
            return;
        }
        ClientPacketDistributor.sendToServer(new RenamePayload(key.id(), this.nameBox.getValue()));
        this.nameBox.setFocused(false);
        this.setFocused(null);
    }

    // ---- 排版

    private int bodyLeft() {
        return SYSTEM_WIDTH;
    }

    private int detailLeft() {
        return SYSTEM_WIDTH + BODY_WIDTH;
    }

    private int listTop() {
        return HEADER + 14;
    }

    private int listBottom() {
        return this.height - FOOTER;
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
    }

    @Override
    public void tick() {
        if (this.seenVersion != ClientDiscoveries.version()) {
            this.refresh();
        }
        this.updateRename();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (this.galaxy == null) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.star_map.unknown"), this.width / 2, this.height / 2, DIM);
            return;
        }
        this.drawHeader(graphics, mouseX, mouseY);
        if (this.tab == TAB_CIV) {
            this.civPage().draw(graphics, HEADER, this.listBottom(), this.width, mouseX, mouseY);
        } else if (this.tab == TAB_FISHING) {
            this.fishingPage().draw(graphics, HEADER, this.listBottom(), this.width, mouseX, mouseY);
        } else {
            this.drawSystems(graphics, mouseX, mouseY);
            this.drawBodies(graphics, mouseX, mouseY);
            this.drawDetail(graphics);
        }
        graphics.fill(0, this.height - FOOTER, this.width, this.height, 0xE0050A0E);
        Component hint = Component.translatable(switch (this.tab) {
            case TAB_CIV -> "gui.universe.civ.footer";
            case TAB_FISHING -> "gui.universe.fishing.footer";
            default -> "gui.universe.discovery.footer";
        });
        graphics.text(this.font, hint, 6, this.height - FOOTER + 3, DIM, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, HEADER, 0xE0050A0E);
        graphics.fill(0, HEADER - 1, this.width, HEADER, LINE);
        graphics.text(this.font, this.title, 6, 5, ACCENT, false);
        for (int tab = 0; tab < TABS; tab++) {
            int[] span = this.tabSpan(tab);
            boolean active = tab == this.tab;
            boolean hover = !active && mouseY < HEADER && mouseX >= span[0] && mouseX < span[1];
            if (active) {
                graphics.fill(span[0], HEADER - 3, span[1], HEADER - 1, ACCENT);
            }
            graphics.text(this.font, this.tabName(tab), span[0] + 4, 5, active ? 0xFFFFFFFF : hover ? TEXT : DIM, false);
        }
        if (this.tab != TAB_LOG) {
            return;
        }
        DiscoveryLog log = ClientDiscoveries.log();
        int species = log.count(DiscoveryKey.Kind.CREATURE) + log.count(DiscoveryKey.Kind.PLANT) + log.count(DiscoveryKey.Kind.TREE);
        Component totals = Component.translatable("gui.universe.discovery.totals", log.count(DiscoveryKey.Kind.SYSTEM),
                log.count(DiscoveryKey.Kind.BODY), species, log.count(DiscoveryKey.Kind.MINERAL));
        graphics.text(this.font, totals, this.width - this.font.width(totals) - 6, 5, DIM, false);
    }

    private Component tabName(int tab) {
        return Component.translatable(switch (tab) {
            case TAB_CIV -> "gui.universe.discovery.tab.civ";
            case TAB_FISHING -> "gui.universe.discovery.tab.fishing";
            default -> "gui.universe.discovery.tab.log";
        });
    }

    /** 标题栏上第几页的左右边界（标题后面依次排开）。 */
    private int[] tabSpan(int tab) {
        int x = 6 + this.font.width(this.title) + 10;
        for (int i = 0; i < tab; i++) {
            x += this.font.width(this.tabName(i)) + 8;
        }
        return new int[] {x, x + this.font.width(this.tabName(tab)) + 8};
    }

    private CivPage civPage() {
        if (this.civPage == null) {
            this.civPage = new CivPage(this.font);
        }
        return this.civPage;
    }

    private FishingPage fishingPage() {
        if (this.fishingPage == null) {
            this.fishingPage = new FishingPage(this.font);
        }
        return this.fishingPage;
    }

    private void columnTitle(GuiGraphicsExtractor graphics, Component title, int left, int right) {
        graphics.fill(left, HEADER, right, this.listTop(), 0xD0081016);
        graphics.text(this.font, title, left + PADDING, HEADER + 3, TEXT, false);
        graphics.fill(right - 1, HEADER, right, this.listBottom(), LINE);
    }

    private void drawSystems(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int right = this.bodyLeft();
        graphics.fill(0, HEADER, right, this.listBottom(), COLUMN);
        this.columnTitle(graphics, Component.translatable("gui.universe.discovery.systems").append(" " + this.systems.size()), 0, right);
        this.systemScroll = this.clampScroll(this.systemScroll, this.systems.size() * ROW);
        graphics.enableScissor(0, this.listTop(), right - 1, this.listBottom());
        int y = this.listTop() - (int) this.systemScroll;
        for (StarSystem system : this.systems) {
            boolean selected = system.index() == this.selectedSystem;
            boolean hover = !selected && mouseX < right && mouseY >= Math.max(y, this.listTop()) && mouseY < y + ROW && mouseY < this.listBottom();
            if (selected || hover) {
                graphics.fill(0, y, right - 1, y + ROW, selected ? 0x502A6A80 : 0x30FFFFFF);
            }
            if (selected) {
                graphics.fill(0, y, 2, y + ROW, ACCENT);
            }
            Star star = system.star();
            this.starDot(graphics, star, PADDING + 3, y + ROW / 2);
            String name = this.font.plainSubstrByWidth(GalaxyText.system(system), right - PADDING - 16);
            graphics.text(this.font, name, PADDING + 10, y + 2, selected ? 0xFFFFFFFF : TEXT, false);
            this.small(graphics, GalaxyText.pick(star.starClass().nameZh(), star.starClass().nameEn()), PADDING + 10, y + 12,
                    ARGB.opaque(star.color()));
            y += ROW;
        }
        graphics.disableScissor();
    }

    private void drawBodies(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = this.bodyLeft();
        int right = this.detailLeft();
        StarSystem system = this.system();
        graphics.fill(left, HEADER, right, this.listBottom(), 0xB0071016);
        int known = 0;
        for (BodyRow row : this.bodies) {
            if (row.planet() != null && ClientDiscoveries.known(DiscoveryKey.body(row.planet().id()))) {
                known++;
            }
        }
        this.columnTitle(graphics, Component.translatable("gui.universe.discovery.bodies").append(" " + known + " / " + (this.bodies.size() - 1)),
                left, right);
        if (system == null) {
            return;
        }
        this.bodyScroll = this.clampScroll(this.bodyScroll, this.bodies.size() * ROW);
        graphics.enableScissor(left, this.listTop(), right - 1, this.listBottom());
        int y = this.listTop() - (int) this.bodyScroll;
        for (BodyRow row : this.bodies) {
            Planet planet = row.planet();
            boolean selected = planet == this.selectedBody;
            boolean hover = !selected && mouseX >= left && mouseX < right && mouseY >= Math.max(y, this.listTop()) && mouseY < y + ROW
                    && mouseY < this.listBottom();
            if (selected || hover) {
                graphics.fill(left, y, right - 1, y + ROW, selected ? 0x502A6A80 : 0x30FFFFFF);
            }
            if (selected) {
                graphics.fill(left, y, left + 2, y + ROW, ACCENT);
            }
            int x = left + PADDING + (row.moon() ? 10 : 0);
            int room = right - x - 14;
            if (planet == null) {
                this.starDot(graphics, system.star(), x + 3, y + ROW / 2);
                graphics.text(this.font, this.font.plainSubstrByWidth(GalaxyText.system(system), room), x + 10, y + 2, TEXT, false);
                this.small(graphics, Component.translatable("discovery.universe.kind.system").getString(), x + 10, y + 12, DIM);
            } else if (ClientDiscoveries.known(DiscoveryKey.body(planet.id()))) {
                graphics.fill(x, y + ROW / 2 - 3, x + 6, y + ROW / 2 + 3, ARGB.opaque(planet.palette().surface()));
                graphics.text(this.font, this.font.plainSubstrByWidth(GalaxyText.planet(planet), room), x + 10, y + 2,
                        row.moon() ? 0xFFC8D0D6 : TEXT, false);
                this.small(graphics, GalaxyText.planetType(planet), x + 10, y + 12,
                        planet.landable() ? DIM : WARN);
                if (this.complete(planet)) {
                    graphics.text(this.font, "✔", right - 12, y + 6, GREEN, false);
                }
            } else {
                graphics.fill(x, y + ROW / 2 - 3, x + 6, y + ROW / 2 + 3, 0xFF30363C);
                graphics.text(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.universe.discovery.undiscovered").getString(), room),
                        x + 10, y + 6, 0xFF5C666E, false);
            }
            y += ROW;
        }
        graphics.disableScissor();
    }

    private boolean complete(Planet planet) {
        return Survey.complete(ClientDiscoveries.log(), DiscoveryKey.body(planet.id()), this.census(planet));
    }

    private void drawDetail(GuiGraphicsExtractor graphics) {
        int left = this.detailLeft();
        int x = left + PADDING + 2;
        int width = this.width - x - PADDING - 4;
        int top = HEADER + 6;
        DiscoveryKey key = this.selectedKey();
        boolean renaming = this.nameBox != null && this.nameBox.visible;
        int bottom = this.listBottom() - (renaming ? RENAME_HEIGHT : 0);
        StarSystem system = this.system();
        if (key == null || system == null) {
            return;
        }
        graphics.enableScissor(left, HEADER, this.width, bottom);
        int y = top - (int) this.detailScroll;
        Optional<DiscoveryLog.Entry> entry = ClientDiscoveries.entry(key);
        if (this.selectedBody == null) {
            y = this.systemDetail(graphics, system, x, y, width);
        } else if (entry.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.discovery.undiscovered"), x, y, DIM, false);
            y += 12;
            y = this.wrap(graphics, Component.translatable("gui.universe.discovery.undiscovered_hint"), x, y, width, 0xFF5C666E);
        } else {
            y = this.bodyDetail(graphics, this.selectedBody, x, y, width);
        }
        if (entry.isPresent()) {
            y += 4;
            graphics.fill(x, y, x + width, y + 1, 0x304FB4D0);
            y += 5;
            DiscoveryLog.Entry found = entry.get();
            if (found.discoverer() == null) {
                y = this.wrap(graphics, Component.translatable("discovery.universe.charted"), x, y, width, DIM);
            } else {
                y = this.wrap(graphics, Component.translatable("gui.universe.discovery.discoverer", found.discovererName())
                        .append(" · ").append(Component.translatable("gui.universe.discovery.day", found.day() + 1)), x, y, width, DIM);
            }
            if (found.customName() != null) {
                Optional<String> original = DiscoveryText.generated(key).map(GalaxyText::name);
                if (original.isPresent()) {
                    y = this.wrap(graphics, Component.literal("« " + original.get() + " »"), x, y, width, 0xFF5C666E);
                }
            }
        }
        graphics.disableScissor();
        this.detailHeight = y + (int) this.detailScroll - top;
        int visible = bottom - top;
        if (this.detailHeight > visible) {
            int barHeight = Math.max(12, visible * visible / this.detailHeight);
            int barTop = top + (int) ((visible - barHeight) * this.detailScroll / (this.detailHeight - visible));
            graphics.fill(this.width - 3, barTop, this.width - 1, barTop + barHeight, 0x804FB4D0);
        }
        if (renaming) {
            int ry = this.listBottom() - RENAME_HEIGHT;
            graphics.fill(left + 1, ry, this.width, this.listBottom(), 0xD0081016);
            graphics.fill(left + 1, ry, this.width, ry + 1, LINE);
            graphics.text(this.font, Component.translatable("gui.universe.discovery.rename"), x, ry + 4, TEXT, false);
        }
    }

    private int systemDetail(GuiGraphicsExtractor graphics, StarSystem system, int x, int y, int width) {
        Star star = system.star();
        y = this.title(graphics, GalaxyText.system(system), x, y);
        graphics.text(this.font, GalaxyText.pick(star.starClass().nameZh(), star.starClass().nameEn()), x, y, ARGB.opaque(star.color()), false);
        y += 12;
        Component stats = star.starClass() == StarClass.BLACK_HOLE
                ? Component.translatable("gui.universe.star_map.mass", GalaxyText.magnitude(star.mass()))
                : Component.translatable("gui.universe.star_map.star_stats", GalaxyText.magnitude(star.luminosity()),
                        Math.round(star.temperature()), GalaxyText.magnitude(star.mass()));
        y = this.wrap(graphics, stats, x, y, width, DIM);
        if (system.index() != GalaxyGenerator.HOME_SYSTEM) {
            y = this.wrap(graphics, Component.translatable("gui.universe.star_map.from_home",
                    GalaxyText.number(this.galaxy.home().distanceTo(system), 1)), x, y, width, DIM);
        }
        y += 6;
        int bodies = this.bodies.size() - 1;
        int known = 0;
        int complete = 0;
        int species = 0;
        for (BodyRow row : this.bodies) {
            if (row.planet() != null && ClientDiscoveries.known(DiscoveryKey.body(row.planet().id()))) {
                known++;
                DiscoveryKey body = DiscoveryKey.body(row.planet().id());
                species += ClientDiscoveries.log().countOn(body, DiscoveryKey.Kind.CREATURE)
                        + ClientDiscoveries.log().countOn(body, DiscoveryKey.Kind.PLANT)
                        + ClientDiscoveries.log().countOn(body, DiscoveryKey.Kind.TREE);
                if (this.complete(row.planet())) {
                    complete++;
                }
            }
        }
        graphics.text(this.font, Component.translatable("gui.universe.discovery.bodies").append(": " + known + " / " + bodies), x, y, TEXT, false);
        y += 11;
        if (known < bodies) {
            y = this.wrap(graphics, Component.translatable("gui.universe.discovery.unknown_bodies", bodies - known), x, y, width, WARN);
        }
        if (species > 0) {
            y = this.wrap(graphics, Component.translatable("gui.universe.discovery.species_total", species), x, y, width, DIM);
        }
        if (complete > 0) {
            y = this.wrap(graphics, Component.translatable("gui.universe.discovery.complete_bodies", complete), x, y, width, GREEN);
        }
        return y;
    }

    private int bodyDetail(GuiGraphicsExtractor graphics, Planet planet, int x, int y, int width) {
        y = this.title(graphics, GalaxyText.planet(planet), x, y);
        graphics.text(this.font, GalaxyText.planetType(planet), x, y, planet.landable() ? TEXT : WARN, false);
        y += 12;
        Component details = planet.type() == PlanetType.GAS_GIANT
                ? Component.translatable("gui.universe.star_map.gas_giant", GalaxyText.number(planet.radius(), 1))
                : Component.translatable("gui.universe.star_map.details", Math.round(planet.temperatureCelsius()),
                        GalaxyText.number(planet.gravity(), 2),
                        GalaxyText.pick(planet.atmosphere().kind().nameZh(), planet.atmosphere().kind().nameEn()));
        y = this.wrap(graphics, details, x, y, width, DIM);
        if (!planet.hazards().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Hazard hazard : planet.hazards()) {
                names.add(GalaxyText.pick(hazard.nameZh(), hazard.nameEn()));
            }
            y = this.wrap(graphics, Component.translatable("gui.universe.star_map.hazards", String.join(GalaxyText.pick("、", ", "), names)),
                    x, y, width, WARN);
        }
        // 大气成分和氧分压（ADR-0036 §二）
        for (Component line : io.github.sweetcornna.universe.chem.PlanetAir.lines(ClientGalaxy.environment(planet), GalaxyText.pick("、", ", "))) {
            y = this.wrap(graphics, line, x, y, width, DIM);
        }
        if (planet.landable()) {
            SentinelLevel sentinels = ClientGalaxy.sentinels(planet);
            y = this.wrap(graphics, Component.translatable("gui.universe.star_map.sentinels", GalaxyText.pick(sentinels.nameZh(), sentinels.nameEn())),
                    x, y, width, sentinels.ordinal() >= SentinelLevel.NORMAL.ordinal() ? WARN : DIM);
        }
        y += 6;
        if (!planet.landable()) {
            return this.wrap(graphics, Component.translatable("gui.universe.discovery.not_landable"), x, y, width, DIM);
        }
        DiscoveryKey body = DiscoveryKey.body(planet.id());
        Survey.Census census = this.census(planet);
        if (planet.id().equals(GalaxyGenerator.HOME_PLANET_ID)) {
            // 母星上是原版的动植物，只记矿物
            return this.minerals(graphics, body, x, y, width);
        }
        if (census.total() == 0) {
            y = this.wrap(graphics, Component.translatable("gui.universe.discovery.lifeless"), x, y, width, DIM);
            return this.minerals(graphics, body, x, y, width);
        }
        if (this.complete(planet)) {
            y = this.wrap(graphics, Component.translatable("gui.universe.discovery.complete"), x, y, width, GREEN);
        }
        y = this.speciesSection(graphics, body, DiscoveryKey.Kind.CREATURE, census, planet, x, y, width);
        y = this.speciesSection(graphics, body, DiscoveryKey.Kind.PLANT, census, planet, x, y, width);
        y = this.speciesSection(graphics, body, DiscoveryKey.Kind.TREE, census, planet, x, y, width);
        return this.minerals(graphics, body, x, y, width);
    }

    /** 一类物种：标题"动物 3 / 5"，下面列出发现了的。 */
    private int speciesSection(GuiGraphicsExtractor graphics, DiscoveryKey body, DiscoveryKey.Kind kind, Survey.Census census, Planet planet,
            int x, int y, int width) {
        int total = census.of(kind);
        if (total == 0) {
            return y;
        }
        DiscoveryLog log = ClientDiscoveries.log();
        int found = log.countOn(body, kind);
        String key = switch (kind) {
            case CREATURE -> "gui.universe.discovery.creatures";
            case PLANT -> "gui.universe.discovery.plants";
            default -> "gui.universe.discovery.trees";
        };
        y += 2;
        graphics.text(this.font, Component.translatable(key, found, total), x, y, found >= total ? GREEN : TEXT, false);
        y += 11;
        // 动物的序号：陆地的从 0 起，水生的从 Fauna.MARINE_INDEX 起（ADR-0026）
        int[] indices = kind == DiscoveryKey.Kind.CREATURE
                ? this.fauna(planet).all().stream().mapToInt(CreatureSpecies::index).toArray()
                : IntStream.range(0, total).toArray();
        for (int index : indices) {
            DiscoveryKey speciesKey = switch (kind) {
                case CREATURE -> DiscoveryKey.creature(planet.id(), index);
                case PLANT -> DiscoveryKey.plant(planet.id(), index);
                default -> DiscoveryKey.tree(planet.id(), index);
            };
            if (!log.contains(speciesKey)) {
                continue;
            }
            String detail = this.speciesDetail(kind, planet, index);
            graphics.fill(x + 2, y + 3, x + 4, y + 5, ACCENT);
            String name = DiscoveryText.name(speciesKey);
            graphics.text(this.font, this.font.plainSubstrByWidth(name, width - 8), x + 8, y, 0xFFE6F2F6, false);
            y += 10;
            if (!detail.isEmpty()) {
                y = this.smallWrap(graphics, Component.literal(detail), x + 8, y, width - 8, DIM);
            }
        }
        if (found < total) {
            y = this.smallWrap(graphics, Component.translatable("gui.universe.discovery.unknown_species", total - found), x + 8, y, width - 8,
                    0xFF5C666E);
        }
        return y;
    }

    private String speciesDetail(DiscoveryKey.Kind kind, Planet planet, int index) {
        switch (kind) {
            case CREATURE -> {
                Optional<CreatureSpecies> found = this.fauna(planet).find(index);
                if (found.isEmpty()) {
                    return "";
                }
                CreatureSpecies creature = found.get();
                return Component.translatable("hud.universe.visor.creature",
                        Component.translatable("discovery.universe.plan." + creature.plan().name().toLowerCase(Locale.ROOT)),
                        Component.translatable("discovery.universe.temperament." + creature.temperament().name().toLowerCase(Locale.ROOT)),
                        GalaxyText.number(creature.size(), 1)).getString();
            }
            case PLANT -> {
                List<PlantSpecies> plants = this.flora(planet).plants();
                return index < plants.size() ? GalaxyText.pick(plants.get(index).form().nameZh(), plants.get(index).form().nameEn()) : "";
            }
            default -> {
                List<TreeSpecies> trees = this.flora(planet).trees();
                return index < trees.size()
                        ? Component.translatable("discovery.universe.tree_shape." + trees.get(index).shape().name().toLowerCase(Locale.ROOT)).getString()
                        : "";
            }
        }
    }

    /** 天体页末尾：发现过的矿物，和测序出来的微生物（ADR-0036 §五）。 */
    private int minerals(GuiGraphicsExtractor graphics, DiscoveryKey body, int x, int y, int width) {
        y = this.childNames(graphics, body, DiscoveryKey.Kind.MINERAL, "gui.universe.discovery.minerals", x, y, width);
        return this.childNames(graphics, body, DiscoveryKey.Kind.MICROBE, "gui.universe.discovery.microbes", x, y, width);
    }

    private int childNames(GuiGraphicsExtractor graphics, DiscoveryKey body, DiscoveryKey.Kind kind, String title, int x, int y, int width) {
        List<String> names = new ArrayList<>();
        for (DiscoveryLog.Entry entry : ClientDiscoveries.log().children(body)) {
            if (entry.key().kind() == kind) {
                names.add(DiscoveryText.name(entry.key()));
            }
        }
        if (names.isEmpty()) {
            return y;
        }
        names.sort(null);
        y += 2;
        graphics.text(this.font, Component.translatable(title, names.size()), x, y, TEXT, false);
        y += 11;
        return this.smallWrap(graphics, Component.literal(String.join(GalaxyText.pick("、", ", "), names)), x + 8, y, width - 8, DIM);
    }

    // ---- 小工具

    private int title(GuiGraphicsExtractor graphics, String text, int x, int y) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(1.25F);
        graphics.text(this.font, text, 0, 0, ACCENT, true);
        pose.popMatrix();
        return y + 14;
    }

    private void starDot(GuiGraphicsExtractor graphics, Star star, int cx, int cy) {
        if (star.starClass() == StarClass.BLACK_HOLE) {
            graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, ARGB.opaque(star.color()));
            graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF000000);
        } else {
            graphics.fill(cx - 2, cy - 3, cx + 3, cy + 4, ARGB.opaque(star.color()));
            graphics.fill(cx - 3, cy - 2, cx + 4, cy + 3, ARGB.opaque(star.color()));
        }
    }

    private void small(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.75F);
        graphics.text(this.font, text, 0, 0, color, false);
        pose.popMatrix();
    }

    private int wrap(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        return TextWrap.draw(graphics, this.font, text, x, y, width, color, false) + 1;
    }

    private int smallWrap(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        float scale = 0.75F;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        int end = TextWrap.draw(graphics, this.font, text, 0, 0, (int) (width / scale), color, false);
        pose.popMatrix();
        return y + (int) Math.ceil(end * scale) + 1;
    }

    private double clampScroll(double scroll, int content) {
        return Math.clamp(scroll, 0, Math.max(0, content - (this.listBottom() - this.listTop())));
    }

    // ---- 输入

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.nameBox != null) {
            this.nameBox.setFocused(false);
        }
        double x = event.x();
        double y = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && y < HEADER) {
            for (int tab = 0; tab < TABS; tab++) {
                int[] span = this.tabSpan(tab);
                if (x >= span[0] && x < span[1] && tab != this.tab) {
                    this.tab = tab;
                    this.updateRename();
                    this.click();
                    return true;
                }
            }
        }
        if (this.tab == TAB_CIV) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && y < this.listBottom() && this.civPage().click(x, y)) {
                this.click();
                return true;
            }
            return false;
        }
        if (this.tab == TAB_FISHING) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && y >= HEADER && y < this.listBottom() && this.fishingPage().click(x, y)) {
                this.click();
                return true;
            }
            return false;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || y < this.listTop() || y >= this.listBottom()) {
            return false;
        }
        if (x < this.bodyLeft()) {
            int row = (int) ((y - this.listTop() + this.systemScroll) / ROW);
            if (row >= 0 && row < this.systems.size()) {
                this.selectSystem(this.systems.get(row).index());
                this.click();
                return true;
            }
        } else if (x < this.detailLeft()) {
            int row = (int) ((y - this.listTop() + this.bodyScroll) / ROW);
            if (row >= 0 && row < this.bodies.size()) {
                this.selectBody(this.bodies.get(row).planet());
                this.click();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.tab == TAB_CIV) {
            this.civPage().scroll(x, scrollY);
        } else if (this.tab == TAB_FISHING) {
            this.fishingPage().scroll(x, scrollY);
        } else if (x < this.bodyLeft()) {
            this.systemScroll = this.clampScroll(this.systemScroll - scrollY * ROW, this.systems.size() * ROW);
        } else if (x < this.detailLeft()) {
            this.bodyScroll = this.clampScroll(this.bodyScroll - scrollY * ROW, this.bodies.size() * ROW);
        } else {
            int bottom = this.listBottom() - (this.nameBox != null && this.nameBox.visible ? RENAME_HEIGHT : 0);
            this.detailScroll = Math.clamp(this.detailScroll - scrollY * 12, 0, Math.max(0, this.detailHeight - (bottom - HEADER - 6)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = this.nameBox != null && this.nameBox.visible && this.nameBox.isFocused();
        if (typing && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            if (this.saveButton != null && this.saveButton.active) {
                this.save();
            }
            return true;
        }
        if (!typing && UniverseKeys.DISCOVERY_LOG.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
