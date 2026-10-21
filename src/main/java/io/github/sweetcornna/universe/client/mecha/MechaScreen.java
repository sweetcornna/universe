package io.github.sweetcornna.universe.client.mecha;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BAD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.GOOD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.core.research.Upgrades;
import io.github.sweetcornna.universe.factory.research.Research;
import io.github.sweetcornna.universe.mecha.Mecha;
import io.github.sweetcornna.universe.mecha.MechaMenu;
import io.github.sweetcornna.universe.mecha.MechaSettings;
import io.github.sweetcornna.universe.network.MechaPayloads;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.survival.SuitHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 机甲面板（ADR-0023）：左边能量核心、燃烧室和机甲的各项数值，右边 12 个物流栏位和选中那格的上下限，下面背包。
 *
 * <p>物流栏位：拿着物品点一格设成这种物品，右键清掉，空手点一格选中它；上下限的 −/+ 一次 16，按住 Shift 一次 64，
 * 在数字上滚轮一次 1。
 */
public class MechaScreen extends AbstractContainerScreen<MechaMenu> {
    private static final int LEFT_WIDTH = 112;
    private static final int BAR_Y = 31;
    private static final int REFUEL_X = 36;
    private static final int REFUEL_Y = 75;
    private static final int REFUEL_WIDTH = 82;
    private static final int STATS_Y = 90;
    private static final int RIGHT_X = MechaMenu.LOGISTICS_X;
    private static final int EDIT_Y = 72;
    private static final int MIN_Y = 84;
    private static final int MAX_Y = 100;
    private static final int MINUS_X = RIGHT_X + 34;
    private static final int VALUE_X = MINUS_X + 12;
    private static final int VALUE_WIDTH = 34;
    private static final int PLUS_X = VALUE_X + VALUE_WIDTH;
    private static final int SMALL = 11;
    private static final int CANCEL_Y = 130;
    private static final int CANCEL_WIDTH = 108;
    private static final int ENERGY = 0xFF5BE0B0;
    private static final int LOCKED = 0xFF151B20;

    private int selected = -1;

    public MechaScreen(MechaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MechaMenu.WIDTH, MechaMenu.HEIGHT);
        this.inventoryLabelX = MechaMenu.INVENTORY_X;
        this.inventoryLabelY = MechaMenu.INVENTORY_Y - 11;
    }

    private MechaSettings settings() {
        return this.minecraft.player.getData(UniverseAttachments.MECHA_SETTINGS);
    }

    private boolean unlocked() {
        return Research.isUnlocked(this.minecraft.level, MechaMenu.LOGISTICS_TECH);
    }

    private int level(String upgrade) {
        return Research.level(this.minecraft.level, upgrade);
    }

    // ---- 画

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        graphics.fill(x + LEFT_WIDTH + 10, y + 20, x + LEFT_WIDTH + 11, y + MechaMenu.INVENTORY_Y - 14, 0x402E7A92);
        for (Slot slot : this.menu.slots) {
            MachineScreen.slotFrame(graphics, x + slot.x, y + slot.y);
        }
        // 能量条
        long capacity = Math.max(1, this.menu.capacity());
        float share = Mth.clamp(this.menu.energy() / (float) capacity, 0, 1);
        graphics.fill(x + 8, y + BAR_Y, x + 8 + LEFT_WIDTH - 2, y + BAR_Y + 6, 0xFF1A2A30);
        graphics.fill(x + 8, y + BAR_Y, x + 8 + Math.round((LEFT_WIDTH - 2) * share), y + BAR_Y + 6, share < 0.15F ? BAD : ENERGY);
        graphics.outline(x + 7, y + BAR_Y - 1, LEFT_WIDTH, 8, 0xFF2E7A92);
        // 燃烧室的火：剩多少画多高
        int fx = x + MechaMenu.FUEL_X + 20;
        int fy = y + MechaMenu.FUEL_Y;
        graphics.fill(fx, fy, fx + 4, fy + 16, 0xFF1A2A30);
        int flame = Math.round(16 * this.menu.burning());
        if (flame > 0) {
            graphics.fill(fx, fy + 16 - flame, fx + 4, fy + 16, 0xFFFF9A3C);
        }
        // 物流栏位
        MechaSettings settings = this.settings();
        boolean unlocked = this.unlocked();
        for (int i = 0; i < MechaSettings.SLOTS; i++) {
            int sx = x + slotX(i);
            int sy = y + slotY(i);
            if (!unlocked) {
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF1C2A31);
                graphics.fill(sx, sy, sx + 16, sy + 16, LOCKED);
                continue;
            }
            MachineScreen.slotFrame(graphics, sx, sy);
            if (i == this.selected) {
                graphics.outline(sx - 1, sy - 1, 18, 18, ACCENT);
            }
            LogisticsSlot slot = settings.slot(i);
            Item item = item(slot);
            if (item != Items.AIR) {
                graphics.fakeItem(new ItemStack(item), sx, sy);
            }
        }
    }

    private static int slotX(int index) {
        return RIGHT_X + index % MechaMenu.LOGISTICS_COLUMNS * 18;
    }

    private static int slotY(int index) {
        return MechaMenu.LOGISTICS_Y + index / MechaMenu.LOGISTICS_COLUMNS * 18;
    }

    private static Item item(LogisticsSlot slot) {
        if (slot.isEmpty()) {
            return Items.AIR;
        }
        Identifier id = Identifier.tryParse(slot.item());
        return id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int mx = mouseX - this.leftPos;
        int my = mouseY - this.topPos;
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
        boolean chest = Mecha.chest(this.minecraft.player);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.core"), 8, 20, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.energy", Upgrades.energy(this.menu.energy()),
                Upgrades.energy(this.menu.capacity())), 8, 41, TEXT, false);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.flow", Upgrades.watts(this.menu.inflow()),
                Upgrades.watts(this.menu.outflow())), 8, 52, DIM, false);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.chamber", Upgrades.watts(this.menu.fuelPower())), REFUEL_X,
                MechaMenu.FUEL_Y, TEXT, false);
        boolean refuel = this.settings().autoRefuel();
        this.button(graphics, REFUEL_X, REFUEL_Y, REFUEL_WIDTH, Component.translatable(refuel ? "gui.universe.mecha.refuel.on"
                : "gui.universe.mecha.refuel.off"), mx, my, refuel ? GOOD : DIM);
        // 各项数值
        int y = STATS_Y;
        if (!chest) {
            graphics.text(this.font, Component.translatable("gui.universe.mecha.no_chest"), 8, y, BAD, false);
            y += 10;
        }
        int frame = (int) Math.round(Upgrades.frameBonus(this.level(Upgrades.MECHANICAL_FRAME)) * 100);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.speed", frame), 8, y, TEXT, false);
        y += 10;
        double flight = Upgrades.flightSpeed(this.level(Upgrades.DRIVE_ENGINE));
        graphics.text(this.font, flight > 0 ? Component.translatable("gui.universe.mecha.flight", Upgrades.effectArgs(Upgrades.DRIVE_ENGINE,
                this.level(Upgrades.DRIVE_ENGINE)).getFirst()) : Component.translatable("gui.universe.mecha.no_flight"), 8, y, flight > 0 ? TEXT : DIM,
                false);
        y += 10;
        int control = this.level(Upgrades.COMMUNICATION_CONTROL);
        graphics.text(this.font, Component.translatable("gui.universe.mecha.drones", Upgrades.drones(control),
                Upgrades.droneSpeed(this.level(Upgrades.DRONE_ENGINE))), 8, y, TEXT, false);
        y += 10;
        graphics.text(this.font, Component.translatable("gui.universe.mecha.tasks", Upgrades.droneTasks(control)), 8, y, DIM, false);
        y += 10;
        SuitHud hud = this.minecraft.player.getData(UniverseAttachments.SUIT_HUD);
        graphics.text(this.font, hud.build() > 0 ? Component.translatable("gui.universe.mecha.sites", hud.build())
                : Component.translatable("gui.universe.mecha.no_sites"), 8, y, hud.build() > 0 ? ACCENT : DIM, false);
        y += 10;
        if (hud.missing() >= 0) {
            graphics.text(this.font, Component.translatable("gui.universe.mecha.missing", new ItemStack(BuiltInRegistries.ITEM.byId(hud.missing()))
                    .getHoverName()), 8, y, BAD, false);
        }
        // 右边：物流栏位
        graphics.text(this.font, Component.translatable("gui.universe.mecha.logistics"), RIGHT_X, 20, TEXT, false);
        if (!this.unlocked()) {
            this.wrapped(graphics, Component.translatable("gui.universe.mecha.logistics.locked"), RIGHT_X, EDIT_Y, DIM);
        } else {
            this.counts(graphics);
            this.editor(graphics, mx, my);
        }
        this.button(graphics, RIGHT_X, CANCEL_Y, CANCEL_WIDTH, Component.translatable("gui.universe.mecha.cancel"), mx, my,
                hud.build() > 0 ? TEXT : DIM);
    }

    /** 每格右下角写身上有几个：不够下限红色，超过上限黄色。 */
    private void counts(GuiGraphicsExtractor graphics) {
        MechaSettings settings = this.settings();
        for (int i = 0; i < MechaSettings.SLOTS; i++) {
            LogisticsSlot slot = settings.slot(i);
            Item item = item(slot);
            if (item == Items.AIR) {
                continue;
            }
            int count = this.carried(item);
            String text = count > 999 ? "999+" : Integer.toString(count);
            int color = count < slot.min() ? 0xFFFF7A6A : count > slot.max() ? 0xFFFFD45A : 0xFFFFFFFF;
            graphics.pose().pushMatrix();
            graphics.pose().translate(slotX(i) + 17, slotY(i) + 11);
            graphics.pose().scale(0.6F, 0.6F);
            graphics.text(this.font, text, -this.font.width(text), 0, color, true);
            graphics.pose().popMatrix();
        }
    }

    private void editor(GuiGraphicsExtractor graphics, int mx, int my) {
        LogisticsSlot slot = this.selected >= 0 ? this.settings().slot(this.selected) : LogisticsSlot.EMPTY;
        Item item = item(slot);
        if (item == Items.AIR) {
            this.wrapped(graphics, Component.translatable("gui.universe.mecha.logistics.hint"), RIGHT_X, EDIT_Y, DIM);
            return;
        }
        graphics.text(this.font, this.font.plainSubstrByWidth(new ItemStack(item).getHoverName().getString(), CANCEL_WIDTH), RIGHT_X, EDIT_Y,
                TEXT, false);
        this.bound(graphics, MIN_Y, Component.translatable("gui.universe.mecha.min"), slot.min(), mx, my);
        this.bound(graphics, MAX_Y, Component.translatable("gui.universe.mecha.max"), slot.max(), mx, my);
    }

    private void bound(GuiGraphicsExtractor graphics, int y, Component label, int value, int mx, int my) {
        graphics.text(this.font, label, RIGHT_X, y + 3, DIM, false);
        this.button(graphics, MINUS_X, y, SMALL, Component.literal("-"), mx, my, TEXT);
        graphics.fill(VALUE_X, y, VALUE_X + VALUE_WIDTH, y + 14, 0xFF0C1418);
        graphics.centeredText(this.font, Component.literal(Integer.toString(value)), VALUE_X + VALUE_WIDTH / 2, y + 3, TEXT);
        this.button(graphics, PLUS_X, y, SMALL, Component.literal("+"), mx, my, TEXT);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mx, int my, int color) {
        boolean hover = inside(mx, my, x, y, width, 14);
        graphics.fill(x, y, x + width, y + 14, hover ? 0xFF24404D : 0xFF16262E);
        graphics.outline(x, y, width, 14, BORDER);
        graphics.centeredText(this.font, label, x + width / 2 + 1, y + 3, color);
    }

    private void wrapped(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        for (var line : this.font.split(text, CANCEL_WIDTH)) {
            graphics.text(this.font, line, x, y, color, false);
            y += 10;
        }
    }

    private int carried(Item item) {
        int count = 0;
        Inventory inventory = this.minecraft.player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ---- 提示

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = this.tooltip(mouseX - this.leftPos, mouseY - this.topPos);
        if (!lines.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    private List<Component> tooltip(int x, int y) {
        List<Component> lines = new ArrayList<>();
        if (inside(x, y, REFUEL_X, REFUEL_Y, REFUEL_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.mecha.refuel.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, RIGHT_X, CANCEL_Y, CANCEL_WIDTH, 14)) {
            lines.add(Component.translatable("gui.universe.mecha.cancel.hint").withStyle(ChatFormatting.GRAY));
        } else if (inside(x, y, 7, BAR_Y - 1, LEFT_WIDTH, 8)) {
            lines.add(Component.translatable("gui.universe.mecha.core.hint").withStyle(ChatFormatting.GRAY));
        } else if (this.unlocked()) {
            int slot = this.slotAt(x, y);
            if (slot >= 0) {
                LogisticsSlot setting = this.settings().slot(slot);
                Item item = item(setting);
                if (item != Items.AIR) {
                    lines.add(new ItemStack(item).getHoverName());
                    lines.add(Component.translatable("gui.universe.mecha.carried", this.carried(item)).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("gui.universe.mecha.bounds", setting.min(), setting.max()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("gui.universe.mecha.logistics.hint").withStyle(ChatFormatting.DARK_GRAY));
            } else if (this.selected >= 0 && (inside(x, y, MINUS_X, MIN_Y, PLUS_X + SMALL - MINUS_X, 14)
                    || inside(x, y, MINUS_X, MAX_Y, PLUS_X + SMALL - MINUS_X, 14))) {
                lines.add(Component.translatable("gui.universe.mecha.limit_hint").withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    private int slotAt(int x, int y) {
        for (int i = 0; i < MechaSettings.SLOTS; i++) {
            if (inside(x, y, slotX(i) - 1, slotY(i) - 1, 18, 18)) {
                return i;
            }
        }
        return -1;
    }

    // ---- 点击

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (int) Math.floor(event.x() - this.leftPos);
        int y = (int) Math.floor(event.y() - this.topPos);
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (left && inside(x, y, REFUEL_X, REFUEL_Y, REFUEL_WIDTH, 14)) {
            return this.press(MechaMenu.TOGGLE_REFUEL);
        }
        if (left && inside(x, y, RIGHT_X, CANCEL_Y, CANCEL_WIDTH, 14)) {
            return this.press(MechaMenu.CANCEL_BUILD);
        }
        if (this.unlocked()) {
            int slot = this.slotAt(x, y);
            if (slot >= 0) {
                if (right) {
                    if (this.selected == slot) {
                        this.selected = -1;
                    }
                    return this.press(MechaMenu.CLEAR_SLOT + slot);
                }
                if (left) {
                    if (this.menu.getCarried().isEmpty()) {
                        this.selected = slot;
                        this.click();
                        return true;
                    }
                    this.selected = slot;
                    return this.press(MechaMenu.SET_SLOT + slot);
                }
            }
            if (left && this.selected >= 0) {
                int step = event.hasShiftDown() ? 64 : 16;
                if (inside(x, y, MINUS_X, MIN_Y, SMALL, 14)) {
                    return this.adjust(-step, 0);
                }
                if (inside(x, y, PLUS_X, MIN_Y, SMALL, 14)) {
                    return this.adjust(step, 0);
                }
                if (inside(x, y, MINUS_X, MAX_Y, SMALL, 14)) {
                    return this.adjust(0, -step);
                }
                if (inside(x, y, PLUS_X, MAX_Y, SMALL, 14)) {
                    return this.adjust(0, step);
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = (int) Math.floor(mouseX - this.leftPos);
        int y = (int) Math.floor(mouseY - this.topPos);
        if (this.selected >= 0 && this.unlocked() && scrollY != 0) {
            int step = scrollY > 0 ? 1 : -1;
            if (inside(x, y, VALUE_X, MIN_Y, VALUE_WIDTH, 14)) {
                return this.adjust(step, 0);
            }
            if (inside(x, y, VALUE_X, MAX_Y, VALUE_WIDTH, 14)) {
                return this.adjust(0, step);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** 改选中那格的上下限：下限调高时上限跟着，上限调低时下限跟着。 */
    private boolean adjust(int minStep, int maxStep) {
        LogisticsSlot slot = this.settings().slot(this.selected);
        if (slot.isEmpty()) {
            return false;
        }
        int min = Math.clamp(slot.min() + minStep, 0, LogisticsSlot.LIMIT);
        int max = Math.clamp(slot.max() + maxStep, 0, LogisticsSlot.LIMIT);
        if (minStep != 0 && min > max) {
            max = min;
        }
        if (maxStep != 0 && max < min) {
            min = max;
        }
        ClientPacketDistributor.sendToServer(new MechaPayloads.Bounds(this.selected, min, max));
        this.click();
        return true;
    }

    private boolean press(int button) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        this.click();
        return true;
    }

    private void click() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
