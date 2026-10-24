package io.github.sweetcornna.universe.client.fossil;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.fossil.Bone;
import io.github.sweetcornna.universe.core.fossil.CollectorTrade;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.fossil.CollectorMenu;
import io.github.sweetcornna.universe.fossil.FossilNames;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 化石收藏家的界面（ADR-0034 §三）。左边收展品：放进展品，看着转动的骨架和出价，按"卖给他"。
 * 右边换骨头：三格放不要的骨头，下面翻想要的类群和部位，箭头后面是换来的那块，按"换"；不能换时写明白为什么。
 */
public class CollectorScreen extends AbstractContainerScreen<CollectorMenu> {
    private static final int PREVIEW_X0 = 28;
    private static final int PREVIEW_Y0 = 22;
    private static final int PREVIEW_X1 = 84;
    private static final int PREVIEW_Y1 = 62;
    private static final int RIGHT = 92;
    private static final int TARGET_X = 154;
    private static final int TARGET_Y = 30;
    private static final int FAMILY_Y = 50;
    private static final int PART_Y = 63;
    private static final int BUTTON_Y = 77;
    private Button sell;
    private Button swap;

    public CollectorScreen(CollectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CollectorMenu.WIDTH, CollectorMenu.HEIGHT);
        this.inventoryLabelX = CollectorMenu.INVENTORY_X;
        this.inventoryLabelY = CollectorMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        this.addRenderableWidget(this.small("<", CollectorMenu.FAMILY_PREV, x + RIGHT, y + FAMILY_Y));
        this.addRenderableWidget(this.small(">", CollectorMenu.FAMILY_NEXT, x + 156, y + FAMILY_Y));
        this.addRenderableWidget(this.small("<", CollectorMenu.PART_PREV, x + RIGHT, y + PART_Y));
        this.addRenderableWidget(this.small(">", CollectorMenu.PART_NEXT, x + 156, y + PART_Y));
        this.sell = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.collector.sell"),
                b -> this.press(CollectorMenu.SELL)).bounds(x + 8, y + BUTTON_Y, 76, 14).build());
        this.swap = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.collector.swap"),
                b -> this.press(CollectorMenu.SWAP)).bounds(x + RIGHT, y + BUTTON_Y, 76, 14).build());
        this.updateButtons();
    }

    private Button small(String label, int id, int x, int y) {
        return Button.builder(Component.literal(label), b -> this.press(id)).bounds(x, y, 12, 12).build();
    }

    private void press(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.sell != null) {
            this.sell.active = !this.menu.exhibit().isEmpty();
            this.swap.active = this.menu.swapCheck() == CollectorTrade.Swap.OK;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, MachineScreen.BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, MachineScreen.PANEL);
        graphics.fill(x + 6, y + 14, x + this.imageWidth - 6, y + 15, 0x602E7A92);
        // 两半之间一道竖线
        graphics.fill(x + RIGHT - 5, y + 18, x + RIGHT - 4, y + 104, 0x402E7A92);
        for (Slot slot : this.menu.slots) {
            MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
        }
        graphics.fill(x + PREVIEW_X0 - 1, y + PREVIEW_Y0 - 1, x + PREVIEW_X1 + 1, y + PREVIEW_Y1 + 1, 0xFF24404D);
        graphics.fill(x + PREVIEW_X0, y + PREVIEW_Y0, x + PREVIEW_X1, y + PREVIEW_Y1, MachineScreen.INSET);
        SkeletonPreview.draw(graphics, this.menu.exhibit(), x + PREVIEW_X0, y + PREVIEW_Y0, x + PREVIEW_X1, y + PREVIEW_Y1);
        // 换来的那块：箭头后面一个亮框
        graphics.fill(x + 149, y + 37, x + 152, y + 39, MachineScreen.DIM);
        graphics.fill(x + TARGET_X - 1, y + TARGET_Y - 1, x + TARGET_X + 17, y + TARGET_Y + 17, MachineScreen.ACCENT);
        graphics.fill(x + TARGET_X, y + TARGET_Y, x + TARGET_X + 16, y + TARGET_Y + 16, MachineScreen.INSET);
        graphics.item(this.wantedStack(), x + TARGET_X, y + TARGET_Y);
    }

    private ItemStack wantedStack() {
        return new ItemStack(UniverseItems.BONES.get(this.menu.wanted()).get());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, MachineScreen.ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineScreen.DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.collector.exhibits"), 8, 18, MachineScreen.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.collector.swaps", CollectorTrade.SWAP_COST), RIGHT, 18, MachineScreen.TEXT,
                false);
        Skeleton exhibit = this.menu.exhibit();
        if (exhibit.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.collector.no_exhibit"), 8, 66, MachineScreen.DIM, false);
        } else {
            long price = CollectorTrade.exhibitPrice(exhibit);
            graphics.text(this.font, Component.translatable("gui.universe.collector.offer", Credits.format(price)), 8, 66, MachineScreen.GOOD, false);
            Component bonus = exhibit.pure() ? Component.translatable("gui.universe.collector.bonus_pure")
                    : exhibit.complete() ? Component.translatable("gui.universe.collector.bonus_complete")
                    : Component.translatable("gui.universe.collector.bonus_none");
            graphics.text(this.font, bonus, 8, 94, exhibit.complete() ? MachineScreen.WARN : MachineScreen.DIM, false);
        }
        Bone wanted = this.menu.wanted();
        graphics.centeredText(this.font, FossilNames.family(wanted.family()).getString(), RIGHT + 38, FAMILY_Y + 2, MachineScreen.TEXT);
        graphics.centeredText(this.font, FossilNames.part(wanted).getString(), RIGHT + 38, PART_Y + 2, MachineScreen.TEXT);
        Component status = switch (this.menu.swapCheck()) {
            case OK -> Component.translatable("gui.universe.collector.ready");
            case NEED_THREE -> Component.translatable("gui.universe.collector.need_three", CollectorTrade.SWAP_COST);
            case TOO_RARE -> Component.translatable("gui.universe.collector.too_rare");
        };
        int color = this.menu.swapCheck() == CollectorTrade.Swap.OK ? MachineScreen.GOOD : MachineScreen.DIM;
        graphics.text(this.font, this.font.split(status, this.imageWidth - RIGHT - 6).getFirst(), RIGHT, 94, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int lx = mouseX - this.leftPos;
        int ly = mouseY - this.topPos;
        if (lx >= TARGET_X && lx < TARGET_X + 16 && ly >= TARGET_Y && ly < TARGET_Y + 16) {
            graphics.setTooltipForNextFrame(this.font, this.wantedStack(), mouseX, mouseY);
        }
    }
}
