package io.github.sweetcornna.universe.client.factory;

import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 储物仓的界面：30 格加背包，不用电，没有读数。 */
public class StorageDepotScreen extends MachineScreen {
    public StorageDepotScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }
}
