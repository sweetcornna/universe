package io.github.sweetcornna.universe.client.facility;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.darkfog.PickupFilter;
import io.github.sweetcornna.universe.darkfog.facility.BattlefieldBaseBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 战场分析基地的界面（ADR-0028 §五）：电力、范围内待修的设施和残影，捡东西的模式，下面 27 格储物。 */
public class BattlefieldBaseScreen extends MachineScreen {
    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 48;
    private static final int BUTTON_WIDTH = 120;

    public BattlefieldBaseScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
        int damaged = this.menu.data(BattlefieldBaseBlockEntity.DAMAGED);
        int ghosts = this.menu.data(BattlefieldBaseBlockEntity.GHOSTS);
        graphics.text(this.font, Component.translatable("gui.universe.battlefield_base.damaged", damaged), 8, 34, damaged > 0 ? WARN : DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.battlefield_base.ghosts", ghosts), 90, 34, ghosts > 0 ? WARN : DIM, false);
        PickupFilter filter = PickupFilter.byIndex(this.menu.data(BattlefieldBaseBlockEntity.FILTER));
        this.textButton(graphics, BUTTON_X, BUTTON_Y, BUTTON_WIDTH, Component.translatable("gui.universe.battlefield_base.pickup." + filter.id()),
                filter == PickupFilter.OFF ? DIM : TEXT, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && overMode(event.x() - this.leftPos, event.y() - this.topPos, BUTTON_X, BUTTON_Y, BUTTON_WIDTH)) {
            this.clickMode(BattlefieldBaseBlockEntity.BUTTON_FILTER);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
