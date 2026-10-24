package io.github.sweetcornna.universe.client.fishing;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.fishing.Bait;
import io.github.sweetcornna.universe.fishing.BaitMenu;
import io.github.sweetcornna.universe.fishing.Fishing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 钓鱼器的饵格（ADR-0034 §一）：一格放饵，右边写这样饵有什么用。 */
public class BaitScreen extends AbstractContainerScreen<BaitMenu> {
    public BaitScreen(BaitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BaitMenu.WIDTH, BaitMenu.HEIGHT);
        this.inventoryLabelX = BaitMenu.INVENTORY_X;
        this.inventoryLabelY = BaitMenu.INVENTORY_Y - 11;
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
            MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
        }
        graphics.outline(x + BaitMenu.BAIT_X - 2, y + BaitMenu.BAIT_Y - 2, 20, 20, MachineScreen.ACCENT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, MachineScreen.ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineScreen.DIM, false);
        ItemStack bait = this.menu.bait();
        Bait kind = Fishing.baitKind(bait);
        Component effect = switch (kind) {
            case NONE -> Component.translatable("gui.universe.bait.empty");
            case FOOD -> Component.translatable("gui.universe.bait.food");
            case DISH -> Component.translatable("gui.universe.bait.dish");
            default -> Component.translatable("tooltip.universe.bait." + kind.id());
        };
        int left = BaitMenu.BAIT_X + 26;
        TextWrap.draw(graphics, this.font, effect, left, BaitMenu.BAIT_Y - 6, this.imageWidth - left - 8, kind == Bait.NONE ? MachineScreen.DIM
                : MachineScreen.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.bait.rule"), 8, BaitMenu.INVENTORY_Y - 24, MachineScreen.DIM, false);
    }
}
