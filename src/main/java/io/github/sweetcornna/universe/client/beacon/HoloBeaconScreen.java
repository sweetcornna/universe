package io.github.sweetcornna.universe.client.beacon;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.beacon.HoloBeaconBlockEntity;
import io.github.sweetcornna.universe.core.beacon.BeaconStyle;
import io.github.sweetcornna.universe.core.beacon.BeaconVisibility;
import io.github.sweetcornna.universe.network.HoloBeaconPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 全息信标的界面（ADR-0033 §六）：一行字、图标（点快捷栏里的一个物品）、颜色、能被看到的范围、一页备忘。保存后所有人看到的都变。
 */
public class HoloBeaconScreen extends Screen {
    private static final int WIDTH = 276;
    private static final int ACCENT = 0xFF7FE0F7;
    private static final int TEXT = 0xFFDCE8EE;
    private static final int DIM = 0xFF8BA3AF;
    private static final int FRAME = 0xFF2E7A92;
    private static final int FIELD_X = 58;
    private final BlockPos pos;
    private final MemoEditor memo;
    private String icon;
    private int color;
    private BeaconVisibility visibility;
    private String label;
    private @Nullable EditBox labelBox;
    private int left;
    private int top;

    public HoloBeaconScreen(HoloBeaconBlockEntity beacon) {
        super(Component.translatable("block.universe.holo_beacon"));
        this.pos = beacon.getBlockPos();
        this.icon = beacon.icon();
        this.color = beacon.color();
        this.visibility = beacon.visibility();
        this.label = beacon.label();
        this.memo = new MemoEditor(beacon.memo());
    }

    private int panelHeight() {
        return 112 + this.memo.height() + 30;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight()) / 2);
        this.labelBox = new EditBox(this.font, this.left + FIELD_X, this.top + 20, WIDTH - FIELD_X - 10, 14,
                Component.translatable("gui.universe.holo_beacon.label"));
        this.labelBox.setMaxLength(BeaconStyle.MAX_LABEL);
        this.labelBox.setValue(this.label);
        this.labelBox.setHint(Component.translatable("gui.universe.holo_beacon.label_hint"));
        this.labelBox.setResponder(text -> this.label = text);
        this.addRenderableWidget(this.labelBox);
        this.memo.init(this.font, this::addRenderableWidget, this.left + 10, this.top + 112, WIDTH - 20);
        int buttons = this.top + this.panelHeight() - 26;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.memo.save"), button -> this.save())
                .bounds(this.left + WIDTH - 10 - 64 * 2 - 6, buttons, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.blueprint.save.cancel"), button -> this.onClose())
                .bounds(this.left + WIDTH - 10 - 64, buttons, 64, 20).build());
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new HoloBeaconPayloads.Update(this.pos, this.label, this.icon, this.color, this.visibility,
                this.memo.memo()));
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- 排版：坐标都是屏幕上的

    private int iconY() {
        return this.top + 42;
    }

    /** 快捷栏第 i 格的左边（0–8），第 9 格是"去掉图标"。 */
    private int slotX(int i) {
        return this.left + FIELD_X + 24 + i * 18;
    }

    private int colorY() {
        return this.top + 66;
    }

    private int swatchX(int i) {
        return this.left + FIELD_X + i * 18;
    }

    private int rangeY() {
        return this.top + 84;
    }

    private static boolean inside(double x, double y, int x0, int y0, int w, int h) {
        return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int height = this.panelHeight();
        int rgb = BeaconStyle.color(this.color);
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + height + 1, FRAME);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + height, 0xF2101A21);
        graphics.fill(this.left, this.top, this.left + 2, this.top + height, 0xFF000000 | rgb);
        graphics.text(this.font, this.title, this.left + 10, this.top + 8, ACCENT, false);
        graphics.text(this.font, Component.translatable("gui.universe.holo_beacon.label"), this.left + 10, this.top + 23, DIM, false);
        // 图标：现在的图标，后面是快捷栏，点一格换成那个物品
        graphics.text(this.font, Component.translatable("gui.universe.holo_beacon.icon"), this.left + 10, this.iconY() + 4, DIM, false);
        Item current = BeaconClient.icon(this.icon);
        frame(graphics, this.left + FIELD_X, this.iconY(), 0xFF000000 | rgb);
        if (current != null) {
            graphics.item(new ItemStack(current), this.left + FIELD_X, this.iconY());
        }
        Inventory inventory = this.minecraft.player == null ? null : this.minecraft.player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            int x = this.slotX(i);
            boolean hover = inside(mouseX, mouseY, x - 1, this.iconY() - 1, 18, 18);
            frame(graphics, x, this.iconY(), hover ? ACCENT : 0xFF24404D);
            if (inventory != null && !inventory.getItem(i).isEmpty()) {
                graphics.item(inventory.getItem(i), x, this.iconY());
            }
        }
        int clear = this.slotX(Inventory.getSelectionSize());
        boolean hoverClear = inside(mouseX, mouseY, clear - 1, this.iconY() - 1, 18, 18);
        frame(graphics, clear, this.iconY(), hoverClear ? ACCENT : 0xFF24404D);
        graphics.centeredText(this.font, Component.literal("×"), clear + 8, this.iconY() + 4, hoverClear ? TEXT : DIM);
        // 颜色
        graphics.text(this.font, Component.translatable("gui.universe.holo_beacon.color"), this.left + 10, this.colorY() + 2, DIM, false);
        for (int i = 0; i < BeaconStyle.colors(); i++) {
            int x = this.swatchX(i);
            boolean selected = i == this.color;
            graphics.fill(x - 1, this.colorY() - 1, x + 15, this.colorY() + 11, selected ? 0xFFFFFFFF : 0xFF24404D);
            graphics.fill(x, this.colorY(), x + 14, this.colorY() + 10, 0xFF000000 | BeaconStyle.color(i));
        }
        // 范围
        graphics.text(this.font, Component.translatable("gui.universe.holo_beacon.range"), this.left + 10, this.rangeY() + 2, DIM, false);
        Component range = Component.translatable("gui.universe.holo_beacon.range." + this.visibility.key());
        int rangeWidth = this.font.width(range) + 12;
        boolean hoverRange = inside(mouseX, mouseY, this.left + FIELD_X, this.rangeY() - 1, rangeWidth, 12);
        graphics.fill(this.left + FIELD_X, this.rangeY() - 1, this.left + FIELD_X + rangeWidth, this.rangeY() + 11, FRAME);
        graphics.fill(this.left + FIELD_X + 1, this.rangeY(), this.left + FIELD_X + rangeWidth - 1, this.rangeY() + 10,
                hoverRange ? 0xFF1F4A5A : 0xFF12303B);
        graphics.text(this.font, range, this.left + FIELD_X + 6, this.rangeY() + 1, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.holo_beacon.range_hint." + this.visibility.key()),
                this.left + FIELD_X + rangeWidth + 6, this.rangeY() + 1, DIM, false);
        // 备忘
        graphics.text(this.font, Component.translatable("gui.universe.holo_beacon.memo"), this.left + 10, this.top + 101, ACCENT, false);
        this.memo.render(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private static void frame(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, color);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0A1015);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double x = event.x();
        double y = event.y();
        if (this.memo.click(x, y)) {
            this.click();
            return true;
        }
        Inventory inventory = this.minecraft.player == null ? null : this.minecraft.player.getInventory();
        for (int i = 0; i <= Inventory.getSelectionSize(); i++) {
            if (inside(x, y, this.slotX(i) - 1, this.iconY() - 1, 18, 18)) {
                if (i == Inventory.getSelectionSize()) {
                    this.icon = "";
                } else if (inventory != null && !inventory.getItem(i).isEmpty()) {
                    this.icon = BuiltInRegistries.ITEM.getKey(inventory.getItem(i).getItem()).toString();
                }
                this.click();
                return true;
            }
        }
        for (int i = 0; i < BeaconStyle.colors(); i++) {
            if (inside(x, y, this.swatchX(i) - 1, this.colorY() - 1, 16, 12)) {
                this.color = i;
                this.click();
                return true;
            }
        }
        Component range = Component.translatable("gui.universe.holo_beacon.range." + this.visibility.key());
        if (inside(x, y, this.left + FIELD_X, this.rangeY() - 1, this.font.width(range) + 12, 12)) {
            this.visibility = this.visibility.next();
            this.click();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = this.labelBox != null && this.labelBox.isFocused() || this.memo.typing();
        if (typing && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            this.save();
            return true;
        }
        return super.keyPressed(event);
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
