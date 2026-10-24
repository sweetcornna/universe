package io.github.sweetcornna.universe.client.fossil;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.fossil.BonePart;
import io.github.sweetcornna.universe.core.fossil.CollectorTrade;
import io.github.sweetcornna.universe.core.fossil.Skeleton;
import io.github.sweetcornna.universe.fossil.FossilNames;
import io.github.sweetcornna.universe.fossil.SkeletonDisplayMenu;
import io.github.sweetcornna.universe.trade.Credits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 骨架展台的界面（ADR-0034 §三）：左边五格摆成一副头朝左的骨架，格子之间连着脊柱；右边转着看拼出来的立体骨架。
 * 下面写名字（五格齐了才有）、放了几格、收藏家出多少钱，右下角"封装"。
 */
public class SkeletonDisplayScreen extends AbstractContainerScreen<SkeletonDisplayMenu> {
    /** 预览框（相对界面左上角）。 */
    static final int PREVIEW_X0 = 92;
    static final int PREVIEW_Y0 = 18;
    static final int PREVIEW_X1 = 168;
    static final int PREVIEW_Y1 = 72;
    private static final int SPINE = 0xFF6F6553;
    private static final int BONE_TEXT = 0xFFE9DCC0;
    private Button pack;

    public SkeletonDisplayScreen(SkeletonDisplayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SkeletonDisplayMenu.WIDTH, SkeletonDisplayMenu.HEIGHT);
        this.inventoryLabelX = SkeletonDisplayMenu.INVENTORY_X;
        this.inventoryLabelY = SkeletonDisplayMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        this.pack = this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.skeleton_display.pack"),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SkeletonDisplayMenu.PACK))
                .bounds(this.leftPos + 118, this.topPos + 88, 50, 14).build());
        this.pack.setTooltip(Tooltip.create(Component.translatable("gui.universe.skeleton_display.pack_hint")));
        this.updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.pack != null) {
            this.pack.active = !this.menu.skeleton().isEmpty();
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
        // 脊柱：头骨、颈椎、胸廓、尾骨连成一串，四肢挂在胸廓下面
        int[][] at = SkeletonDisplayMenu.SLOTS;
        this.spine(graphics, x, y, at[BonePart.SKULL.ordinal()], at[BonePart.NECK.ordinal()]);
        this.spine(graphics, x, y, at[BonePart.NECK.ordinal()], at[BonePart.RIBCAGE.ordinal()]);
        this.spine(graphics, x, y, at[BonePart.RIBCAGE.ordinal()], at[BonePart.TAIL.ordinal()]);
        this.spine(graphics, x, y, at[BonePart.RIBCAGE.ordinal()], at[BonePart.LIMBS.ordinal()]);
        for (Slot slot : this.menu.slots) {
            MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
        }
        // 预览框
        graphics.fill(x + PREVIEW_X0 - 1, y + PREVIEW_Y0 - 1, x + PREVIEW_X1 + 1, y + PREVIEW_Y1 + 1, 0xFF24404D);
        graphics.fill(x + PREVIEW_X0, y + PREVIEW_Y0, x + PREVIEW_X1, y + PREVIEW_Y1, MachineScreen.INSET);
        SkeletonPreview.draw(graphics, this.menu.skeleton(), x + PREVIEW_X0, y + PREVIEW_Y0, x + PREVIEW_X1, y + PREVIEW_Y1);
    }

    /** 两格中心之间一道两像素宽的折线（先横后竖）。 */
    private void spine(GuiGraphicsExtractor graphics, int x, int y, int[] from, int[] to) {
        int x0 = x + from[0] + 8;
        int y0 = y + from[1] + 8;
        int x1 = x + to[0] + 8;
        int y1 = y + to[1] + 8;
        graphics.fill(Math.min(x0, x1), y0 - 1, Math.max(x0, x1) + 1, y0 + 1, SPINE);
        graphics.fill(x1 - 1, Math.min(y0, y1), x1 + 1, Math.max(y0, y1) + 1, SPINE);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, MachineScreen.ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineScreen.DIM, false);
        Skeleton skeleton = this.menu.skeleton();
        Component name = skeleton.complete() ? FossilNames.skeleton(skeleton)
                : Component.translatable(skeleton.isEmpty() ? "gui.universe.skeleton_display.empty" : "gui.universe.skeleton_display.incomplete");
        graphics.text(this.font, this.font.split(name, this.imageWidth - 16).getFirst(), 8, 77,
                skeleton.complete() ? BONE_TEXT : MachineScreen.DIM, false);
        Component count = Component.translatable("gui.universe.skeleton_display.count", skeleton.count(), BonePart.values().length);
        graphics.text(this.font, count, 8, 91, MachineScreen.TEXT, false);
        if (!skeleton.isEmpty()) {
            // 价钱跟在块数后面
            Component price = Component.translatable("gui.universe.skeleton_display.price", Credits.format(CollectorTrade.exhibitPrice(skeleton)));
            graphics.text(this.font, price, 8 + this.font.width(count) + 6, 91, skeleton.complete() ? MachineScreen.GOOD : MachineScreen.DIM, false);
        }
        if (skeleton.pure()) {
            graphics.text(this.font, Component.translatable("gui.universe.skeleton_display.pure"), PREVIEW_X0 + 2, PREVIEW_Y0 + 2,
                    MachineScreen.WARN, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // 空格子：写这一格收哪个部位
        if (this.hoveredSlot instanceof SkeletonDisplayMenu.BoneSlot slot && !slot.hasItem() && this.menu.getCarried().isEmpty()) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.universe.skeleton_display.slot", FossilNames.part(slot.part())), mouseX, mouseY);
        }
    }
}
