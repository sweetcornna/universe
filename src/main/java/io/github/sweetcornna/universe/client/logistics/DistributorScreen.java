package io.github.sweetcornna.universe.client.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.mecha.Distribution;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.logistics.DistributorBlockEntity;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 物流配送器的界面（ADR-0023）：管的物品（拿着物品点物品框设定，右键清掉）、电池、机器人、对机甲和对别的配送器的模式、下面容器的存量。
 */
public class DistributorScreen extends MachineScreen {
    private static final int ITEM_X = 8;
    private static final int ITEM_Y = 36;
    private static final int BUTTON_Y = 78;
    private static final int BUTTON_WIDTH = 78;
    private static final int PEER_X = 90;

    public DistributorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private Item filter() {
        int id = (int) this.menu.wide(DistributorBlockEntity.FILTER) - 1;
        return id < 0 ? Items.AIR : BuiltInRegistries.ITEM.byId(id);
    }

    private Distribution.Icarus icarus() {
        Distribution.Icarus[] values = Distribution.Icarus.values();
        return values[Math.floorMod(this.menu.data(DistributorBlockEntity.ICARUS), values.length)];
    }

    private Distribution.Peer peer() {
        Distribution.Peer[] values = Distribution.Peer.values();
        return values[Math.floorMod(this.menu.data(DistributorBlockEntity.PEER), values.length)];
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        slotFrame(graphics, x + ITEM_X, y + ITEM_Y);
        Item item = this.filter();
        if (item != Items.AIR) {
            graphics.fakeItem(new ItemStack(item), x + ITEM_X, y + ITEM_Y);
        }
        // 空着的机器人格画一个淡淡的机器人
        Slot bots = this.menu.getSlot(DistributorBlockEntity.BOT_SLOT);
        if (!bots.hasItem()) {
            graphics.fakeItem(new ItemStack(UniverseItems.LOGISTICS_BOT.get()), x + bots.x, y + bots.y);
            graphics.fill(x + bots.x, y + bots.y, x + bots.x + 16, y + bots.y + 16, 0xA00A1015);
        }
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
        Item item = this.filter();
        graphics.text(this.font, item == Items.AIR ? Component.translatable("gui.universe.distributor.no_item")
                : new ItemStack(item).getHoverName(), ITEM_X + 22, ITEM_Y + 1, item == Items.AIR ? DIM : TEXT, false);
        long energy = this.menu.wide(DistributorBlockEntity.ENERGY);
        graphics.text(this.font, Component.translatable("gui.universe.distributor.battery", joules(energy), joules(DistributorBlockEntity.CAPACITY)),
                ITEM_X + 22, ITEM_Y + 11, DIM, false);
        bar(graphics, 8, 56, 132, 3, energy / (float) DistributorBlockEntity.CAPACITY, GOOD);
        graphics.text(this.font, Component.translatable("gui.universe.distributor.bots", this.menu.data(DistributorBlockEntity.IDLE_BOTS),
                this.menu.data(DistributorBlockEntity.BOTS)), 8, 64, TEXT, false);
        this.textButton(graphics, 8, BUTTON_Y, BUTTON_WIDTH, Component.translatable("gui.universe.distributor.icarus."
                + this.icarus().name().toLowerCase(Locale.ROOT)), this.icarus() == Distribution.Icarus.NONE ? DIM : TEXT, mouseX, mouseY);
        this.textButton(graphics, PEER_X, BUTTON_Y, BUTTON_WIDTH, Component.translatable("gui.universe.distributor.peer."
                + this.peer().name().toLowerCase(Locale.ROOT)), this.peer() == Distribution.Peer.NONE ? DIM : TEXT, mouseX, mouseY);
        if (this.menu.data(DistributorBlockEntity.HAS_STORAGE) == 0) {
            graphics.text(this.font, Component.translatable("gui.universe.distributor.no_storage"), 8, 94, BAD, false);
        } else if (item != Items.AIR) {
            // 空位只试放到 SPACE_PROBE 个，到顶了写成 999+
            int space = this.menu.data(DistributorBlockEntity.SPACE);
            graphics.text(this.font, Component.translatable("gui.universe.distributor.storage", this.menu.data(DistributorBlockEntity.STOCK),
                    space >= DistributorBlockEntity.SPACE_PROBE ? space + "+" : String.valueOf(space)), 8, 94, DIM, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        List<Component> lines = new ArrayList<>();
        if (x >= ITEM_X - 1 && x < ITEM_X + 17 && y >= ITEM_Y - 1 && y < ITEM_Y + 17) {
            lines.add(Component.translatable("gui.universe.distributor.item_hint").withStyle(ChatFormatting.GRAY));
        } else if (overMode(x, y, 8, BUTTON_Y, BUTTON_WIDTH)) {
            lines.add(Component.translatable("gui.universe.distributor.icarus_hint." + this.icarus().name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        } else if (overMode(x, y, PEER_X, BUTTON_Y, BUTTON_WIDTH)) {
            lines.add(Component.translatable("gui.universe.distributor.peer_hint." + this.peer().name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        } else if (this.hoveredSlot != null && this.hoveredSlot.index == DistributorBlockEntity.BOT_SLOT && !this.hoveredSlot.hasItem()) {
            lines.add(Component.translatable("gui.universe.distributor.bot_hint", DistributorBlockEntity.MAX_BOTS).withStyle(ChatFormatting.GRAY));
        }
        if (!lines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) Math.floor(event.x() - this.leftPos);
        int y = (int) Math.floor(event.y() - this.topPos);
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (x >= ITEM_X - 1 && x < ITEM_X + 17 && y >= ITEM_Y - 1 && y < ITEM_Y + 17 && (left || right)) {
            this.clickMode(right || this.menu.getCarried().isEmpty() ? DistributorBlockEntity.CLEAR_FILTER : DistributorBlockEntity.SET_FILTER);
            return true;
        }
        if (left && overMode(x, y, 8, BUTTON_Y, BUTTON_WIDTH)) {
            this.clickMode(DistributorBlockEntity.CYCLE_ICARUS);
            return true;
        }
        if (left && overMode(x, y, PEER_X, BUTTON_Y, BUTTON_WIDTH)) {
            this.clickMode(DistributorBlockEntity.CYCLE_PEER);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
