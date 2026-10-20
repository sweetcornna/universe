package io.github.sweetcornna.universe.client.teleport;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.client.ClientGalaxy;
import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.galaxy.BodyId;
import io.github.sweetcornna.universe.core.teleport.TeleportRules;
import io.github.sweetcornna.universe.network.OpenTeleporterPayload;
import io.github.sweetcornna.universe.network.OpenTeleporterPayload.Destination;
import io.github.sweetcornna.universe.network.TeleportRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 传送器的界面（ADR-0017）：电量，下面一行一个目的地（自己的基地、进过的空间站），点了就传送，结果由服务端发消息。
 * 目的地多时分页。
 */
public class TeleporterScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int PAGE = 8;
    private static final int ACCENT = 0xFF6EDCF5;
    private final OpenTeleporterPayload payload;
    private int page;
    private int left;
    private int top;
    private int panelHeight;

    public TeleporterScreen(OpenTeleporterPayload payload) {
        super(Component.translatable(payload.free() ? "block.universe.station_teleporter" : "block.universe.teleporter"));
        this.payload = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean ready() {
        return this.payload.free() || this.payload.energy() >= TeleportRules.CHARGE
                || this.minecraft.player != null && this.minecraft.player.isCreative();
    }

    private int pages() {
        return Math.max(1, (this.payload.destinations().size() + PAGE - 1) / PAGE);
    }

    @Override
    protected void init() {
        List<Destination> destinations = this.payload.destinations();
        int rows = Math.max(1, Math.min(PAGE, destinations.size()));
        this.panelHeight = 44 + rows * 24 + (this.pages() > 1 ? 26 : 4) + 8;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.page = Math.min(this.page, this.pages() - 1);
        int from = this.page * PAGE;
        for (int i = from; i < Math.min(destinations.size(), from + PAGE); i++) {
            Destination destination = destinations.get(i);
            Button button = Button.builder(label(destination), b -> this.go(destination))
                    .bounds(this.left + 12, this.top + 40 + (i - from) * 24, WIDTH - 24, 20)
                    .tooltip(Tooltip.create(detail(destination)))
                    .build();
            button.active = this.ready() && !destination.here();
            this.addRenderableWidget(button);
        }
        if (this.pages() > 1) {
            int y = this.top + 40 + rows * 24 + 2;
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

    private void go(Destination destination) {
        ClientPacketDistributor.sendToServer(new TeleportRequestPayload(this.payload.pos(), destination.kind(), destination.id()));
        this.onClose();
    }

    /** 按钮上的名字：基地起过名就用它，否则"星球名 · 基地 N"；空间站是"恒星系名空间站"。 */
    static Component label(Destination destination) {
        if (destination.kind() == OpenTeleporterPayload.STATION) {
            return Component.translatable("gui.universe.trade.station", systemName(destination.system()));
        }
        if (!destination.name().isEmpty()) {
            return Component.literal(destination.name());
        }
        return defaultBaseName(destination.body(), destination.dimension(), destination.number());
    }

    static Component defaultBaseName(String body, String dimension, int number) {
        return Component.translatable("gui.universe.base.default_name", placeName(body, dimension), number);
    }

    private static String placeName(String body, String dimension) {
        return BodyId.parse(body).flatMap(id -> ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.body(id))).map(GalaxyText::planet)
                .orElseGet(() -> dimension.contains("/") ? dimension.substring(dimension.indexOf(':') + 1) : dimension);
    }

    private static String systemName(int system) {
        return ClientGalaxy.galaxy().flatMap(galaxy -> galaxy.system(system)).map(GalaxyText::system).orElse("#" + system);
    }

    /** 鼠标移上去看的：在哪个恒星系，或者就是这里。 */
    private static Component detail(Destination destination) {
        if (destination.here()) {
            return Component.translatable("gui.universe.teleporter.here");
        }
        if (destination.system() < 0) {
            return Component.literal(destination.dimension());
        }
        return Component.translatable("gui.universe.teleporter.system", systemName(destination.system()));
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
        Component status;
        int color;
        if (this.payload.free()) {
            status = Component.translatable("gui.universe.teleporter.free");
            color = 0xFFB8D8E0;
        } else if (!this.payload.connected()) {
            status = Component.translatable("gui.universe.machine.unpowered");
            color = 0xFFFF6A5A;
        } else {
            status = Component.translatable("gui.universe.teleporter.energy",
                    String.format(Locale.ROOT, "%.1f", this.payload.energy() / 1000.0), TeleportRules.CHARGE / 1000);
            color = this.payload.energy() >= TeleportRules.CHARGE ? 0xFF8AE08A : 0xFFFFE08A;
        }
        graphics.text(this.font, status, this.left + 12, this.top + 24, color, false);
        if (!this.payload.free()) {
            // 电量条
            int barRight = this.left + WIDTH - 12;
            int filled = (int) ((WIDTH - 24) * Math.min(1.0, this.payload.energy() / (double) TeleportRules.CHARGE));
            graphics.fill(this.left + 12, this.top + 35, barRight, this.top + 37, 0xFF1E2A30);
            graphics.fill(this.left + 12, this.top + 35, this.left + 12 + filled, this.top + 37, color);
        }
        if (this.payload.destinations().isEmpty()) {
            TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.teleporter.empty"), this.left + 12, this.top + 42,
                    WIDTH - 24, 0xFF9AA8AE, false);
        }
        if (this.pages() > 1) {
            Component pageText = Component.literal((this.page + 1) + " / " + this.pages());
            int rows = Math.min(PAGE, this.payload.destinations().size());
            graphics.text(this.font, pageText, this.left + (WIDTH - this.font.width(pageText)) / 2, this.top + 40 + rows * 24 + 8,
                    0xFF9AA8AE, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
