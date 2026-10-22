package io.github.sweetcornna.universe.client.factory;

import java.util.List;

import io.github.sweetcornna.universe.factory.FuelRecipeMachineBlockEntity;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.MachineMenu.Role;
import io.github.sweetcornna.universe.factory.MachineMenu.SlotSpec;
import io.github.sweetcornna.universe.factory.RecipeMachineBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 烧燃料的配方设备的界面（ADR-0026）：和电网的配方设备一样，电力那一行换成炉膛里剩下的火，燃料格空着时轮流画煤和原木的虚影。
 */
public class FuelRecipeMachineScreen extends RecipeMachineScreen {
    private static final int FLAME = 0xFFF0943A;
    /** 煤在深色的格子里看不清，和原木轮流显示。 */
    private static final List<ItemStack> GHOST_FUEL = List.of(new ItemStack(Items.COAL), new ItemStack(Items.OAK_LOG));

    public FuelRecipeMachineScreen(MachineMenu menu, Inventory inventory, Component title, RecipeMachineBlockEntity.Spec spec) {
        super(menu, inventory, title, spec);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        for (int i = 0; i < this.menu.type().slots().size(); i++) {
            SlotSpec slot = this.menu.type().slots().get(i);
            if (slot.role() == Role.FUEL && this.menu.getSlot(i).getItem().isEmpty()) {
                int x = this.leftPos + slot.x();
                int y = this.topPos + slot.y();
                graphics.fakeItem(GHOST_FUEL.get((int) (System.currentTimeMillis() / 1000 % GHOST_FUEL.size())), x, y);
                graphics.nextStratum();
                graphics.fill(x, y, x + 16, y + 16, 0x700A1015);
            }
        }
    }

    @Override
    protected void powerLine(GuiGraphicsExtractor graphics, int x, int y) {
        Component label = Component.translatable("gui.universe.fuel_machine.fuel");
        graphics.text(this.font, label, x, y, TEXT, false);
        float fuel = this.menu.data(FuelRecipeMachineBlockEntity.FUEL) / 1000F;
        bar(graphics, x + this.font.width(label) + 6, y + 1, 72, 5, fuel, fuel > 0.2F ? FLAME : BAD);
    }
}
