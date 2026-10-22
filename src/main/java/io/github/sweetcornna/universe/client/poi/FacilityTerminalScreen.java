package io.github.sweetcornna.universe.client.poi;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.poi.FacilityTerminalMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 制造设施终端的界面（ADR-0026）：三个模块格画成发光的底座，上面一行说明，下面是背包。
 */
public class FacilityTerminalScreen extends AbstractContainerScreen<FacilityTerminalMenu> {
    private static final int GLOW = 0x60F2B84B;

    public FacilityTerminalScreen(FacilityTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FacilityTerminalMenu.WIDTH, FacilityTerminalMenu.HEIGHT);
        this.inventoryLabelX = FacilityTerminalMenu.INVENTORY_X;
        this.inventoryLabelY = FacilityTerminalMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        for (int i = 0; i < FacilityTerminalMenu.OFFERS; i++) {
            Slot slot = this.menu.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            // 模块格下面一圈暖色的光，空了就不画
            if (slot.hasItem()) {
                graphics.fill(sx - 4, sy - 4, sx + 20, sy + 20, GLOW);
            }
        }
        for (Slot slot : this.menu.slots) {
            MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
        TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.facility_terminal.hint"), 8, 21, this.imageWidth - 16, DIM, false);
    }
}
