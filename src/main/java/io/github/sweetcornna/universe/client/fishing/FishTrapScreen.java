package io.github.sweetcornna.universe.client.fishing;

import java.util.Locale;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.fishing.Habitat;
import io.github.sweetcornna.universe.fishing.FishTrapBlockEntity;
import io.github.sweetcornna.universe.fishing.FishTrapMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** 捕鱼笼（ADR-0034 §一）：左边饵格，右边 3×3 的收获，中间写状态、水域，画下一条的进度。 */
public class FishTrapScreen extends AbstractContainerScreen<FishTrapMenu> {
    public FishTrapScreen(FishTrapMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FishTrapMenu.WIDTH, FishTrapMenu.HEIGHT);
        this.inventoryLabelX = FishTrapMenu.INVENTORY_X;
        this.inventoryLabelY = FishTrapMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, MachineScreen.PANEL);
        graphics.fill(x + 6, y + 14, x + this.imageWidth - 6, y + 15, 0x602E7A92);
        for (Slot slot : this.menu.slots) {
            MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
        }
        // 饵格到收获之间的进度
        int barX = x + FishTrapMenu.BAIT_X + 22;
        int barY = y + FishTrapMenu.BAIT_Y + 6;
        int barWidth = FishTrapMenu.GRID_X - FishTrapMenu.BAIT_X - 28;
        graphics.fill(barX, barY, barX + barWidth, barY + 5, MachineScreen.INSET);
        int color = this.menu.status() == FishTrapBlockEntity.Status.WORKING ? 0xFF6FD8C8 : 0xFF4A5A60;
        graphics.fill(barX, barY, barX + Math.round(barWidth * Math.clamp(this.menu.progress(), 0, 1)), barY + 5, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, MachineScreen.ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineScreen.DIM, false);
        FishTrapBlockEntity.Status status = this.menu.status();
        int color = switch (status) {
            case WORKING -> MachineScreen.GOOD;
            case DRY -> MachineScreen.BAD;
            case FULL -> MachineScreen.WARN;
        };
        int left = 8;
        graphics.text(this.font, Component.translatable("gui.universe.fish_trap.status." + status.name().toLowerCase(Locale.ROOT)), left, 18,
                color, false);
        graphics.text(this.font, Component.translatable("gui.universe.fish_trap.bait"), FishTrapMenu.BAIT_X - 2, FishTrapMenu.BAIT_Y - 9,
                MachineScreen.DIM, false);
        int habitat = this.menu.habitat();
        Component water = habitat >= 0 && habitat < Habitat.values().length
                ? Component.translatable("fishing.universe.habitat." + Habitat.values()[habitat].key())
                : Component.translatable("gui.universe.fish_trap.no_water");
        graphics.text(this.font, Component.translatable("gui.universe.fish_trap.water", water), left, 58, MachineScreen.TEXT, false);
        Component speed = Component.translatable(this.menu.baited() ? "gui.universe.fish_trap.baited" : "gui.universe.fish_trap.unbaited");
        graphics.text(this.font, speed, left, 68, MachineScreen.DIM, false);
    }
}
