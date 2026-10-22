package io.github.sweetcornna.universe.client.fleet;

import java.util.List;
import java.util.Locale;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.fleet.FreighterRules;
import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.network.FleetPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 货船控制台（ADR-0024）：上面是货船现在在哪、档次、航程和背包里的空间翘曲器，下面是航程内的恒星系，近的在前，
 * 点一个就跃迁过去。没去过的系名字后面标一下。
 */
public class FreighterConsoleScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int PAGE = 7;
    private static final int ACCENT = 0xFF6EDCF5;
    private static final int TEXT = 0xFFE6EEF0;
    private static final int DIM = 0xFF9AA8AE;
    private static final int BAD = 0xFFE07B7B;
    private FleetPayloads.Console state;
    private int page;
    private int left;
    private int top;
    private int panelHeight;

    public FreighterConsoleScreen(FleetPayloads.Console state) {
        super(Component.translatable("gui.universe.freighter_console.title"));
        this.state = state;
    }

    public void update(FleetPayloads.Console state) {
        this.state = state;
        this.rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int pages() {
        return Math.max(1, (this.state.targets().size() + PAGE - 1) / PAGE);
    }

    private boolean canWarp() {
        boolean creative = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasInfiniteMaterials();
        return !this.state.spooling() && (creative || this.state.warpers() >= FreighterRules.WARPERS);
    }

    @Override
    protected void init() {
        List<FleetPayloads.Target> targets = this.state.targets();
        int rows = Math.max(1, Math.min(PAGE, targets.size()));
        this.panelHeight = 64 + rows * 24 + (this.pages() > 1 ? 26 : 4) + 18;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.page = Math.min(this.page, this.pages() - 1);
        int from = this.page * PAGE;
        boolean warp = this.canWarp();
        for (int i = from; i < Math.min(targets.size(), from + PAGE); i++) {
            FleetPayloads.Target target = targets.get(i);
            Button row = Button.builder(label(target), b -> ClientPacketDistributor.sendToServer(new FleetPayloads.Warp(target.system())))
                    .bounds(this.left + 12, this.top + 60 + (i - from) * 24, WIDTH - 24, 20).build();
            row.active = warp;
            this.addRenderableWidget(row);
        }
        if (this.pages() > 1) {
            int y = this.top + 60 + rows * 24 + 2;
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

    private static Component label(FleetPayloads.Target target) {
        Component name = Component.literal(GalaxyText.pick(target.nameZh(), target.nameEn()));
        String distance = String.format(Locale.ROOT, "%.1f", target.distance());
        return target.visited() ? Component.translatable("gui.universe.freighter_console.target", name, distance)
                : Component.translatable("gui.universe.freighter_console.target_new", name, distance);
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
        Grade grade = Grade.values()[Math.clamp(this.state.grade(), 0, Grade.values().length - 1)];
        Component here = Component.translatable("gui.universe.freighter_console.here", Component.literal(GalaxyText.pick(this.state.nameZh(),
                this.state.nameEn())));
        graphics.text(this.font, here, this.left + 12, this.top + 24, TEXT, false);
        Component info = Component.translatable("gui.universe.freighter_console.info", grade.letter(),
                String.format(Locale.ROOT, "%.0f", this.state.range()), this.state.warpers(), FreighterRules.WARPERS);
        graphics.text(this.font, info, this.left + 12, this.top + 36, 0xFF000000 | grade.color(), false);
        Component hint = this.state.spooling() ? Component.translatable("gui.universe.freighter_console.spooling")
                : this.canWarp() ? Component.translatable("gui.universe.freighter_console.hint")
                : Component.translatable("gui.universe.freighter_console.no_warpers", FreighterRules.WARPERS);
        graphics.text(this.font, hint, this.left + 12, this.top + 48, this.canWarp() || this.state.spooling() ? DIM : BAD, false);
        if (this.state.targets().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.freighter_console.empty"), this.left + 12, this.top + 64, DIM, false);
        }
        if (this.pages() > 1) {
            Component pageText = Component.literal((this.page + 1) + " / " + this.pages());
            int rows = Math.min(PAGE, this.state.targets().size());
            graphics.text(this.font, pageText, this.left + (WIDTH - this.font.width(pageText)) / 2, this.top + 60 + rows * 24 + 8, DIM, false);
        }
        this.state.notice().ifPresent(notice -> graphics.text(this.font, notice, this.left + 12, this.top + this.panelHeight - 14, DIM, false));
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
