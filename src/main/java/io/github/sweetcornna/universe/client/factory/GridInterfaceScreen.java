package io.github.sweetcornna.universe.client.factory;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.core.power.GridInterface;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.power.GridInterfaceBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 电网接口的界面（ADR-0030 §三）：电网这边的功率，外面这边每刻走了多少、接着几台设备，缓冲，模式按钮。
 */
public final class GridInterfaceScreen extends MachineScreen {
    private static final int BUTTON_Y = 88;
    private static final int BUTTON_WIDTH = 160;

    public GridInterfaceScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean output = this.menu.data(GridInterfaceBlockEntity.MODE) != 0;
        this.powerLine(graphics, 8, 22);
        int neighbors = this.menu.data(GridInterfaceBlockEntity.NEIGHBORS);
        long external = this.menu.wide(GridInterfaceBlockEntity.EXTERNAL);
        Component flow = Component.translatable(output ? "gui.universe.grid_interface.sent" : "gui.universe.grid_interface.received",
                watts(external));
        graphics.text(this.font, flow, 8, 36, external > 0 ? GOOD : DIM, false);
        // 输出模式要有设备来接；输入模式靠别的模组推进来，旁边没设备也可能是管线接着
        Component devices = neighbors > 0 ? Component.translatable("gui.universe.grid_interface.devices", neighbors)
                : Component.translatable("gui.universe.grid_interface.no_devices");
        graphics.text(this.font, devices, 8, 48, neighbors > 0 ? TEXT : output ? WARN : DIM, false);
        long buffer = this.menu.wide(GridInterfaceBlockEntity.BUFFER);
        graphics.text(this.font, Component.translatable("gui.universe.grid_interface.buffer", joules(buffer), joules(GridInterface.BUFFER)),
                8, 62, TEXT, false);
        bar(graphics, 8, 74, BUTTON_WIDTH, 6, buffer / (float) GridInterface.BUFFER, output ? WARN : GOOD);
        this.textButton(graphics, 8, BUTTON_Y, BUTTON_WIDTH, Component.translatable(output
                ? "gui.universe.grid_interface.output" : "gui.universe.grid_interface.input"), output ? WARN : GOOD, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && overMode(event.x() - this.leftPos, event.y() - this.topPos, 8, BUTTON_Y, BUTTON_WIDTH)) {
            this.clickMode(GridInterfaceBlockEntity.MODE_BUTTON);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
