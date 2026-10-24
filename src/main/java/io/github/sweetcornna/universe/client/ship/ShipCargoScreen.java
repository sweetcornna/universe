package io.github.sweetcornna.universe.client.ship;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.entity.ship.ShipCargoMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 飞船货舱的界面（ADR-0030 §六）：船上所有箱子、木桶的格子排成 9 列，超过 6 行就在右边画一根滚动条，
 * 滚轮和拖滚动条都能滚。样子跟设备界面一致（深色面板加青色描边）。
 *
 * <p>滚动是服务端也知道的状态（{@link ShipCargoMenu#clickMenuButton}）：滚完先发给服务端，再本地滚，
 * 两边的格子对着货舱里同一格。
 */
public class ShipCargoScreen extends AbstractContainerScreen<ShipCargoMenu> {
    /** 滚动条和格子之间空几格像素。 */
    private static final int SCROLLBAR_GAP = 4;
    private static final int HANDLE_HEIGHT = 15;

    private boolean dragging;

    public ShipCargoScreen(ShipCargoMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShipCargoMenu.WIDTH,
                ShipCargoMenu.GRID_Y + menu.visibleRows() * 18 + 14 + 54 + 4 + 18 + 7);
        this.inventoryLabelX = ShipCargoMenu.GRID_X;
        this.inventoryLabelY = ShipCargoMenu.GRID_Y + menu.visibleRows() * 18 + 3;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, MachineScreen.PANEL);
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
            }
        }
        if (this.menu.scrollable()) {
            this.scrollbar(graphics);
        }
    }

    /** 右边的滚动条：槽加一个滑块。 */
    private void scrollbar(GuiGraphicsExtractor graphics) {
        int x = this.leftPos + this.scrollbarX();
        int y = this.topPos + ShipCargoMenu.GRID_Y;
        int height = this.menu.visibleRows() * 18;
        graphics.fill(x, y - 1, x + ShipCargoMenu.SCROLLBAR_WIDTH, y + height + 1, 0xFF24404D);
        graphics.fill(x + 1, y, x + ShipCargoMenu.SCROLLBAR_WIDTH - 1, y + height, MachineScreen.INSET);
        int travel = height - HANDLE_HEIGHT;
        int offset = this.menu.maxScroll() == 0 ? 0 : Math.round(travel * this.menu.scroll() / (float) this.menu.maxScroll());
        graphics.fill(x + 1, y + offset, x + ShipCargoMenu.SCROLLBAR_WIDTH - 1, y + offset + HANDLE_HEIGHT, 0xFF2E7A92);
        graphics.fill(x + 1, y + offset, x + ShipCargoMenu.SCROLLBAR_WIDTH - 1, y + offset + 1, MachineScreen.ACCENT);
    }

    private int scrollbarX() {
        return ShipCargoMenu.GRID_X + ShipCargoMenu.COLUMNS * 18 + SCROLLBAR_GAP;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, MachineScreen.ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineScreen.DIM, false);
        if (this.menu.cargoSize() == 0) {
            graphics.text(this.font, Component.translatable("gui.universe.ship_cargo.empty"), ShipCargoMenu.GRID_X,
                    ShipCargoMenu.GRID_Y + 4, MachineScreen.DIM, false);
        } else if (this.menu.scrollable()) {
            // 右上角写第几行到第几行，免得玩家不知道还有多少没露出来
            Component range = Component.translatable("gui.universe.ship_cargo.rows", this.menu.scroll() + 1,
                    this.menu.scroll() + this.menu.visibleRows(), this.menu.rows());
            graphics.text(this.font, range, this.imageWidth - 6 - this.font.width(range), this.titleLabelY, MachineScreen.DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.scrollable() && scrollY != 0) {
            this.scrollTo(this.menu.scroll() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.menu.scrollable() && this.overScrollbar(event.x(), event.y())) {
            this.dragging = true;
            this.dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            this.dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        int x = this.leftPos + this.scrollbarX();
        int y = this.topPos + ShipCargoMenu.GRID_Y;
        int height = this.menu.visibleRows() * 18;
        return mouseX >= x && mouseX < x + ShipCargoMenu.SCROLLBAR_WIDTH && mouseY >= y - 1 && mouseY < y + height + 1;
    }

    /** 滑块跟着鼠标：滑块中心对着鼠标。 */
    private void dragTo(double mouseY) {
        int y = this.topPos + ShipCargoMenu.GRID_Y;
        int travel = this.menu.visibleRows() * 18 - HANDLE_HEIGHT;
        double share = travel <= 0 ? 0 : (mouseY - y - HANDLE_HEIGHT / 2.0) / travel;
        this.scrollTo((int) Math.round(Math.clamp(share, 0, 1) * this.menu.maxScroll()));
    }

    /** 滚到第 row 行：先告诉服务端，再本地滚。 */
    private void scrollTo(int row) {
        int clamped = Math.clamp(row, 0, this.menu.maxScroll());
        if (clamped == this.menu.scroll()) {
            return;
        }
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, clamped);
        }
        this.menu.setScroll(clamped);
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.6F));
        }
    }
}
