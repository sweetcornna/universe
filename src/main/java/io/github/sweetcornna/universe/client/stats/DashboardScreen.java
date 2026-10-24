package io.github.sweetcornna.universe.client.stats;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.stats.Dashboard;
import io.github.sweetcornna.universe.core.stats.Dashboard.Pin;
import io.github.sweetcornna.universe.network.DashboardEditPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * 编辑仪表盘（ADR-0033 §四）：拖标题栏挪位置，点"–"收成一行，点"×"关掉，"全部靠右"让拖过的回到右边叠着。
 * 游戏不暂停，数照常更新。
 */
public class DashboardScreen extends Screen {
    private final @Nullable Screen parent;
    private int dragging = -1;
    private int grabX;
    private int grabY;
    private int dragX;
    private int dragY;
    /** 刚放下的那张：服务端改好同步回来之前先画在放下的地方。 */
    private int pending = -1;
    private int pendingX;
    private int pendingY;
    private int pendingTicks;

    public DashboardScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.universe.dashboard.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dashboard.reset"),
                button -> DashboardHud.edit(DashboardEditPayload.Action.RESET, -1, Pin.of(Dashboard.Kind.POWER, "", "", 0)))
                .bounds(this.width / 2 - 104, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dashboard.done"), button -> this.onClose())
                .bounds(this.width / 2 + 4, y, 100, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void tick() {
        if (this.pending >= 0 && --this.pendingTicks <= 0) {
            this.pending = -1;
        }
    }

    private int[] positions(List<Pin> pins) {
        int[] at = this.minecraft.player == null ? new int[pins.size() * 2]
                : DashboardHud.layout(pins, this.width, this.height, DashboardHud.top(this.minecraft.player));
        if (this.pending >= 0 && this.pending < pins.size()) {
            at[this.pending * 2] = this.pendingX;
            at[this.pending * 2 + 1] = this.pendingY;
        }
        if (this.dragging >= 0 && this.dragging < pins.size()) {
            at[this.dragging * 2] = this.dragX;
            at[this.dragging * 2 + 1] = this.dragY;
        }
        return at;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(this.font, this.title, this.width / 2, 8, MachineScreen.ACCENT);
        graphics.centeredText(this.font, Component.translatable("gui.universe.dashboard.help"), this.width / 2, this.height - 40, MachineScreen.DIM);
        List<Pin> pins = DashboardHud.pins();
        if (pins.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.universe.dashboard.empty"), this.width / 2, this.height / 2 - 4,
                    MachineScreen.TEXT);
        }
        int[] at = this.positions(pins);
        for (int i = 0; i < pins.size(); i++) {
            int x = at[i * 2];
            int y = at[i * 2 + 1];
            DashboardHud.render(graphics, this.font, pins.get(i), DashboardHud.reading(i), x, y, true, mouseX, mouseY);
            if (i == this.dragging) {
                graphics.outline(x - 1, y - 1, DashboardHud.WIDTH + 2, DashboardHud.height(pins.get(i), DashboardHud.reading(i)) + 2,
                        MachineScreen.ACCENT);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        int mx = (int) event.x();
        int my = (int) event.y();
        List<Pin> pins = DashboardHud.pins();
        int[] at = this.positions(pins);
        for (int i = pins.size() - 1; i >= 0; i--) {
            int x = at[i * 2];
            int y = at[i * 2 + 1];
            if (mx < x || mx >= x + DashboardHud.WIDTH || my < y || my >= y + DashboardHud.height(pins.get(i), DashboardHud.reading(i))) {
                continue;
            }
            if (my < y + DashboardHud.HEADER && mx >= x + DashboardHud.WIDTH - 22 && mx < x + DashboardHud.WIDTH - 12) {
                DashboardHud.edit(DashboardEditPayload.Action.TOGGLE, i, pins.get(i));
                this.click();
            } else if (my < y + DashboardHud.HEADER && mx >= x + DashboardHud.WIDTH - 11) {
                DashboardHud.edit(DashboardEditPayload.Action.REMOVE, i, pins.get(i));
                this.pending = -1;
                this.click();
            } else {
                this.dragging = i;
                this.grabX = mx - x;
                this.grabY = my - y;
                this.dragX = x;
                this.dragY = y;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging >= 0) {
            this.dragX = Math.max(0, Math.min(this.width - DashboardHud.WIDTH, (int) event.x() - this.grabX));
            this.dragY = Math.max(0, Math.min(this.height - DashboardHud.HEADER, (int) event.y() - this.grabY));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging >= 0) {
            List<Pin> pins = DashboardHud.pins();
            if (this.dragging < pins.size()) {
                Pin pin = pins.get(this.dragging);
                float x = (float) this.dragX / this.width;
                float y = (float) this.dragY / this.height;
                DashboardHud.edit(DashboardEditPayload.Action.PLACE, this.dragging,
                        new Pin(pin.kind(), pin.target(), pin.scope(), pin.range(), x, y, pin.collapsed()));
                this.pending = this.dragging;
                this.pendingX = this.dragX;
                this.pendingY = this.dragY;
                this.pendingTicks = 20;
            }
            this.dragging = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (DashboardHud.EDIT_KEY.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
