package io.github.sweetcornna.universe.client.stats;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.Universe;
import io.github.sweetcornna.universe.client.HudRegions;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.core.stats.Dashboard.Pin;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.network.DashboardDataPayload;
import io.github.sweetcornna.universe.network.DashboardDataPayload.Entry;
import io.github.sweetcornna.universe.network.DashboardDataPayload.Reading;
import io.github.sweetcornna.universe.network.DashboardEditPayload;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 仪表盘（ADR-0033 §四）：钉住的统计图画在屏幕上。没拖动过的从右上角往下叠（让开信用点、状态效果和任务），拖动过的
 * 放在拖到的地方；压到左边的外骨骼、载具面板时挪到它们右边。数是服务端每 5 秒发来的。改位置、收起、关掉在
 * {@link DashboardScreen} 里（统计面板上的"编辑仪表盘"，或者绑一个键）。
 */
public final class DashboardHud {
    /** 打开仪表盘的编辑界面。默认不绑键，统计面板上也能打开。 */
    public static final KeyMapping EDIT_KEY = new KeyMapping("key.universe.dashboard", InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(), UniverseKeys.CATEGORY);
    static final int WIDTH = 136;
    static final int HEADER = 12;
    static final int MARGIN = 4;
    static final int GAP = 3;
    private static final int CHART = 28;
    private static final int BACKGROUND = 0x90000000;
    private static final int HEADER_COLOR = 0xC0102830;

    private static List<Reading> readings = List.of();

    private DashboardHud() {
    }

    public static void registerEvents(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(DashboardDataPayload.TYPE, DashboardHud::handle));
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerBelow(VanillaGuiLayers.CROSSHAIR, Universe.id("dashboard"), DashboardHud::renderHud));
        gameBus.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            while (EDIT_KEY.consumeClick()) {
                if (minecraft.gui.screen() == null && minecraft.player != null) {
                    minecraft.gui.setScreen(new DashboardScreen(null));
                }
            }
        });
        gameBus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> readings = List.of());
    }

    private static void handle(DashboardDataPayload payload, IPayloadContext context) {
        readings = List.copyOf(payload.readings());
    }

    static List<Pin> pins() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? List.of() : player.getData(UniverseAttachments.DASHBOARD);
    }

    static @Nullable Reading reading(int index) {
        return index < readings.size() ? readings.get(index) : null;
    }

    /** 钉一张图（统计面板、科技树、物流站界面的"钉到仪表盘"）。满了、钉过了在动作栏说一声。 */
    public static void pin(Pin pin) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        List<Pin> pins = pins();
        if (Dashboard.contains(pins, pin)) {
            player.sendOverlayMessage(Component.translatable("gui.universe.dashboard.already"));
            return;
        }
        if (pins.size() >= Dashboard.MAX_PINS) {
            player.sendOverlayMessage(Component.translatable("gui.universe.dashboard.full", Dashboard.MAX_PINS));
            return;
        }
        ClientPacketDistributor.sendToServer(new DashboardEditPayload(DashboardEditPayload.Action.ADD, -1, pin));
        player.sendOverlayMessage(Component.translatable("gui.universe.dashboard.pinned"));
    }

    /** 物流站界面用：把这座站的存货钉到仪表盘。 */
    public static void pinStation(java.util.UUID station) {
        pin(Pin.of(Dashboard.Kind.STATION, station.toString(), "", 0));
    }

    static void edit(DashboardEditPayload.Action action, int index, Pin pin) {
        ClientPacketDistributor.sendToServer(new DashboardEditPayload(action, index, pin));
    }

    // ---- 排版

    /** 没拖动过的图从哪儿开始往下叠：右上角让开信用点和状态效果的图标，有任务时再让开任务。 */
    static int top(LocalPlayer player) {
        int top = player.getActiveEffects().isEmpty() ? 21 : 54;
        if (player.getData(UniverseAttachments.MISSIONS).active().isPresent()) {
            top += 30;
        }
        return top;
    }

    static int[] layout(List<Pin> pins, int screenWidth, int screenHeight, int top) {
        int[] heights = new int[pins.size()];
        for (int i = 0; i < pins.size(); i++) {
            heights[i] = height(pins.get(i), reading(i));
        }
        int[] at = Dashboard.layout(pins, heights, WIDTH, screenWidth, screenHeight, top, MARGIN, GAP);
        for (int i = 0; i < pins.size(); i++) {
            if (pins.get(i).placed()) {
                at[i * 2] = Math.min(HudRegions.avoid(at[i * 2], at[i * 2 + 1], WIDTH, heights[i]), screenWidth - WIDTH);
            }
        }
        return at;
    }

    static int height(Pin pin, @Nullable Reading reading) {
        if (pin.collapsed()) {
            return HEADER;
        }
        return HEADER + switch (pin.kind()) {
            case ITEM, POWER -> 27 + CHART;
            case RESEARCH -> 30 + 10 * (reading == null ? 0 : Math.max(0, Math.min(3, reading.entries().size() - (reading.title().isEmpty() ? 0 : 1))));
            case DYSON -> 33 + CHART;
            case STATION -> 4 + 10 * Math.max(1, reading == null ? 1 : Math.min(8, reading.entries().size()));
        };
    }

    // ---- 画

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) {
            return;
        }
        List<Pin> pins = pins();
        if (pins.isEmpty()) {
            return;
        }
        int[] at = layout(pins, graphics.guiWidth(), graphics.guiHeight(), top(player));
        for (int i = 0; i < pins.size(); i++) {
            render(graphics, minecraft.font, pins.get(i), reading(i), at[i * 2], at[i * 2 + 1], false, -1, -1);
        }
    }

    /** 画一张图。编辑时标题栏右边有收起和关掉两个小按钮。 */
    static void render(GuiGraphicsExtractor graphics, Font font, Pin pin, @Nullable Reading reading, int x, int y, boolean edit, int mouseX,
            int mouseY) {
        int height = height(pin, reading);
        graphics.fill(x, y, x + WIDTH, y + height, BACKGROUND);
        graphics.fill(x, y, x + WIDTH, y + HEADER, HEADER_COLOR);
        graphics.fill(x, y, x + 2, y + height, MachineScreen.ACCENT);
        Component title = title(pin, reading);
        String summary = reading == null ? "…" : summary(pin, reading);
        int buttons = edit ? 2 * 10 + 2 : 0;
        int summaryWidth = font.width(summary);
        graphics.text(font, font.plainSubstrByWidth(title.getString(), WIDTH - 8 - summaryWidth - 4 - buttons), x + 5, y + 2,
                MachineScreen.TEXT, true);
        graphics.text(font, summary, x + WIDTH - 3 - buttons - summaryWidth, y + 2, summaryColor(pin, reading), true);
        if (edit) {
            button(graphics, font, x + WIDTH - 22, y + 1, pin.collapsed() ? "+" : "–", mouseX, mouseY);
            button(graphics, font, x + WIDTH - 11, y + 1, "×", mouseX, mouseY);
        }
        if (pin.collapsed() || reading == null) {
            return;
        }
        int bodyY = y + HEADER + 2;
        switch (pin.kind()) {
            case ITEM -> item(graphics, font, reading, x, bodyY);
            case POWER -> power(graphics, font, reading, x, bodyY);
            case RESEARCH -> research(graphics, font, reading, x, bodyY);
            case DYSON -> dyson(graphics, font, reading, x, bodyY);
            case STATION -> station(graphics, font, reading, x, bodyY);
        }
    }

    private static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10;
        graphics.fill(x, y, x + 10, y + 10, hovered ? MachineScreen.ACCENT : 0xFF2A4A58);
        graphics.fill(x + 1, y + 1, x + 9, y + 9, hovered ? 0xFF1F4A5A : 0xFF0C151B);
        graphics.centeredText(font, Component.literal(label), x + 5, y + 1, MachineScreen.TEXT);
    }

    /** 标题栏左边：图的名字和看的地方。 */
    static Component title(Pin pin, @Nullable Reading reading) {
        return switch (pin.kind()) {
            case ITEM -> pin.scope().isEmpty() ? StatsCharts.itemName(pin.target())
                    : Component.translatable("gui.universe.dashboard.scoped", StatsCharts.itemName(pin.target()), StatsCharts.scopeName(pin.scope()));
            case POWER -> Component.translatable("gui.universe.dashboard.power", StatsCharts.scopeName(pin.scope()));
            case RESEARCH -> Component.translatable("gui.universe.dashboard.research");
            case DYSON -> {
                int system;
                try {
                    system = Integer.parseInt(pin.target());
                } catch (NumberFormatException e) {
                    system = -1;
                }
                yield Component.translatable("gui.universe.dashboard.dyson", StatsCharts.systemName(system));
            }
            case STATION -> reading == null || reading.title().isEmpty() ? Component.translatable("gui.universe.dashboard.station")
                    : Component.literal(reading.title());
        };
    }

    /** 标题栏右边的一个数：收起来时也看得到。 */
    private static String summary(Pin pin, Reading reading) {
        return switch (pin.kind()) {
            case ITEM -> signed(reading.a() - reading.b()) + "/" + Component.translatable("gui.universe.stats.per_minute_short").getString();
            case POWER -> StatsCharts.percent(reading.c());
            case RESEARCH -> reading.title().isEmpty() || reading.b() <= 0 ? "—" : StatsCharts.percent(reading.a() / reading.b());
            case DYSON -> StatsCharts.watts(reading.a());
            case STATION -> Integer.toString(reading.entries().size());
        };
    }

    private static int summaryColor(Pin pin, @Nullable Reading reading) {
        if (reading == null) {
            return MachineScreen.DIM;
        }
        return switch (pin.kind()) {
            case ITEM -> reading.a() - reading.b() > 0.05 ? MachineScreen.GOOD : reading.a() - reading.b() < -0.05 ? MachineScreen.BAD
                    : MachineScreen.DIM;
            case POWER -> reading.c() >= 0.999 ? MachineScreen.GOOD : reading.c() >= 0.5 ? MachineScreen.WARN : MachineScreen.BAD;
            default -> MachineScreen.ACCENT;
        };
    }

    private static String signed(double value) {
        return (value > 0.05 ? "+" : "") + StatsCharts.rate(value);
    }

    private static void item(GuiGraphicsExtractor graphics, Font font, Reading reading, int x, int y) {
        graphics.text(font, Component.translatable("gui.universe.dashboard.produced", StatsCharts.rate(reading.a())), x + 5, y,
                StatsCharts.PRODUCED, true);
        graphics.text(font, Component.translatable("gui.universe.dashboard.consumed", StatsCharts.rate(reading.b())), x + 70, y,
                StatsCharts.CONSUMED, true);
        graphics.text(font, Component.translatable("gui.universe.dashboard.reference", StatsCharts.rate(reading.c())), x + 5, y + 11,
                MachineScreen.DIM, true);
        StatsCharts.lines(graphics, x + 5, y + 23, WIDTH - 9, CHART, reading.lineA(), reading.lineB(), StatsCharts.PRODUCED,
                StatsCharts.CONSUMED);
    }

    private static void power(GuiGraphicsExtractor graphics, Font font, Reading reading, int x, int y) {
        graphics.text(font, Component.translatable("gui.universe.dashboard.generation", StatsCharts.watts(reading.a())), x + 5, y,
                StatsCharts.PRODUCED, true);
        graphics.text(font, Component.translatable("gui.universe.dashboard.demand", StatsCharts.watts(reading.b())), x + 5, y + 11,
                StatsCharts.CONSUMED, true);
        StatsCharts.lines(graphics, x + 5, y + 23, WIDTH - 9, CHART, reading.lineA(), reading.lineB(), StatsCharts.PRODUCED,
                StatsCharts.CONSUMED);
    }

    private static void research(GuiGraphicsExtractor graphics, Font font, Reading reading, int x, int y) {
        List<Entry> entries = reading.entries();
        int next = 0;
        if (reading.title().isEmpty()) {
            graphics.text(font, Component.translatable("gui.universe.dashboard.research_idle"), x + 5, y, MachineScreen.DIM, true);
        } else {
            Entry current = entries.getFirst();
            Component name = current.max() > 0 ? Research.name(current.item(), (int) current.max()) : Research.name(current.item());
            graphics.text(font, font.plainSubstrByWidth(name.getString(), WIDTH - 10), x + 5, y, MachineScreen.ACCENT, true);
            next = 1;
        }
        StatsCharts.bar(graphics, x + 5, y + 11, WIDTH - 9, 3, reading.b() > 0 ? reading.a() / reading.b() : 0, MachineScreen.ACCENT);
        graphics.text(font, Component.translatable("gui.universe.dashboard.queue", (int) reading.c()), x + 5, y + 16, MachineScreen.DIM, true);
        for (int i = next; i < entries.size() && i < next + 3; i++) {
            Entry entry = entries.get(i);
            Component name = entry.count() > 0 ? Research.name(entry.item(), (int) entry.count()) : Research.name(entry.item());
            graphics.text(font, font.plainSubstrByWidth("· " + name.getString(), WIDTH - 10), x + 5, y + 26 + (i - next) * 10, MachineScreen.TEXT,
                    true);
        }
    }

    private static void dyson(GuiGraphicsExtractor graphics, Font font, Reading reading, int x, int y) {
        graphics.text(font, Component.translatable("gui.universe.dashboard.dyson_power", StatsCharts.watts(reading.a())), x + 5, y,
                StatsCharts.PRODUCED, true);
        graphics.text(font, Component.translatable("gui.universe.dashboard.dyson_drawn", StatsCharts.watts(reading.b())), x + 5, y + 11,
                StatsCharts.CONSUMED, true);
        graphics.text(font, Component.translatable("gui.universe.dashboard.dyson_sails", (long) reading.c()), x + 5, y + 22, MachineScreen.DIM,
                true);
        StatsCharts.lines(graphics, x + 5, y + 33, WIDTH - 9, CHART - 4, reading.lineA(), new float[0], StatsCharts.PRODUCED, 0);
    }

    private static void station(GuiGraphicsExtractor graphics, Font font, Reading reading, int x, int y) {
        List<Entry> entries = reading.entries();
        if (entries.isEmpty()) {
            graphics.text(font, Component.translatable("gui.universe.dashboard.station_empty"), x + 5, y, MachineScreen.DIM, true);
            return;
        }
        for (int i = 0; i < Math.min(8, entries.size()); i++) {
            Entry entry = entries.get(i);
            int rowY = y + i * 10;
            String count = StatsCharts.rate(entry.count());
            graphics.text(font, font.plainSubstrByWidth(StatsCharts.itemName(entry.item()).getString(), WIDTH - 50), x + 5, rowY,
                    MachineScreen.TEXT, true);
            graphics.text(font, count, x + WIDTH - 4 - font.width(count), rowY, MachineScreen.ACCENT, true);
            StatsCharts.bar(graphics, x + 5, rowY + 8, WIDTH - 9, 1, entry.max() > 0 ? (double) entry.count() / entry.max() : 0, 0xFF4FA3C0);
        }
    }

    /** 物品图标（统计面板里用）。 */
    static ItemStack stack(String item) {
        return new ItemStack(StatsCharts.item(item));
    }
}
