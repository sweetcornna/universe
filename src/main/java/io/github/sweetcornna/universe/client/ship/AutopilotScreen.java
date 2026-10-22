package io.github.sweetcornna.universe.client.ship;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.network.ShipPayloads;
import io.github.sweetcornna.universe.network.ShipPayloads.Destination;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 自动驾驶的目的地（ADR-0024）：本系的星球、卫星、空间站，近的在前，一行一个，点了船就自己飞过去。目的地多时分页。
 */
public class AutopilotScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int PAGE = 8;
    private static final int ACCENT = 0xFF6EDCF5;
    private final List<Destination> destinations;
    private int page;
    private int left;
    private int top;
    private int panelHeight;

    public AutopilotScreen(ShipPayloads.Destinations payload) {
        super(Component.translatable("gui.universe.autopilot.title"));
        this.destinations = payload.list();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pages() {
        return Math.max(1, (this.destinations.size() + PAGE - 1) / PAGE);
    }

    @Override
    protected void init() {
        int rows = Math.max(1, Math.min(PAGE, this.destinations.size()));
        this.panelHeight = 40 + rows * 24 + (this.pages() > 1 ? 26 : 4) + 8;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.page = Math.min(this.page, this.pages() - 1);
        int from = this.page * PAGE;
        for (int i = from; i < Math.min(this.destinations.size(), from + PAGE); i++) {
            int index = i;
            this.addRenderableWidget(Button.builder(label(this.destinations.get(i)), b -> this.go(index))
                    .bounds(this.left + 12, this.top + 36 + (i - from) * 24, WIDTH - 24, 20)
                    .build());
        }
        if (this.pages() > 1) {
            int y = this.top + 36 + rows * 24 + 2;
            Button previous = Button.builder(Component.literal("<"), b -> this.turn(-1)).bounds(this.left + 12, y, 20, 20).build();
            Button next = Button.builder(Component.literal(">"), b -> this.turn(1)).bounds(this.left + WIDTH - 32, y, 20, 20).build();
            previous.active = this.page > 0;
            next.active = this.page < this.pages() - 1;
            this.addRenderableWidget(previous);
            this.addRenderableWidget(next);
        }
    }

    private void turn(int delta) {
        this.page = Math.max(0, Math.min(this.pages() - 1, this.page + delta));
        this.rebuildWidgets();
    }

    private void go(int index) {
        ClientPacketDistributor.sendToServer(new ShipPayloads.Navigate(index));
        this.onClose();
    }

    /** "星球 · 名字 · 12.3k 格"。 */
    static Component label(Destination destination) {
        if (destination.kind() == Destination.SUMMON_FREIGHTER) {
            return Component.translatable("gui.universe.autopilot.summon_freighter");
        }
        String kind = switch (destination.kind()) {
            case Destination.MOON -> "moon";
            case Destination.STATION -> "station";
            case Destination.FREIGHTER -> "freighter";
            case Destination.SIGNAL -> "signal";
            case Destination.BLACK_HOLE -> "black_hole";
            default -> "planet";
        };
        Component kindName = Component.translatable("gui.universe.autopilot.kind." + kind);
        if (destination.nameZh().isEmpty() && destination.nameEn().isEmpty()) {
            // 空间站没有自己的名字，只写种类
            return Component.translatable("gui.universe.autopilot.entry_unnamed", kindName, distance(destination.distance()));
        }
        Component name = Component.literal(GalaxyText.pick(destination.nameZh(), destination.nameEn()));
        return Component.translatable("gui.universe.autopilot.entry", kindName, name, distance(destination.distance()));
    }

    public static String distance(int blocks) {
        return blocks >= 10000 ? String.format(Locale.ROOT, "%.1fk", blocks / 1000.0) : String.valueOf(blocks);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + this.panelHeight + 1, 0xFF2E6E7E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + this.panelHeight, 0xF0080C0E);
        graphics.text(this.font, this.title, this.left + 12, this.top + 10, ACCENT, false);
        graphics.text(this.font, Component.translatable("gui.universe.autopilot.hint"), this.left + 12, this.top + 22, 0xFF9AA8AE, false);
        if (this.destinations.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.autopilot.empty"), this.left + 12, this.top + 40, 0xFF9AA8AE, false);
        }
        if (this.pages() > 1) {
            Component pageText = Component.literal((this.page + 1) + " / " + this.pages());
            int rows = Math.min(PAGE, this.destinations.size());
            graphics.text(this.font, pageText, this.left + (WIDTH - this.font.width(pageText)) / 2, this.top + 36 + rows * 24 + 8,
                    0xFF9AA8AE, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
