package io.github.sweetcornna.universe.client.ship;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BAD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.GOOD;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;

import java.util.Locale;

import org.joml.Matrix3x2fStack;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.ship.ShipRating;
import io.github.sweetcornna.universe.core.ship.ShipStats;
import io.github.sweetcornna.universe.core.ship.ShipType;
import io.github.sweetcornna.universe.entity.ship.ShipPanelMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 飞船面板（ADR-0024）：左边类型、档次和各项数值（比只按部件算的高出多少），右边驾驶台的技术槽，下面是背包。
 * 数值随技术槽里放的东西实时变。档次给不到的格子画成锁住；里面还有东西时画红框，提示不生效。
 */
public class ShipPanelScreen extends AbstractContainerScreen<ShipPanelMenu> {
    private static final int LOCKED = 0xFF151B20;
    private static final int SHIP_COLOR = 0xFFBE96FF;
    private static final float SCALE = 0.75F;
    private static final int LINE = 10;

    public ShipPanelScreen(ShipPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShipPanelMenu.WIDTH, ShipPanelMenu.HEIGHT);
        this.inventoryLabelX = ShipPanelMenu.INVENTORY_X;
        this.inventoryLabelY = ShipPanelMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        graphics.fill(x + ShipPanelMenu.LEFT_COLUMN, y + 20, x + ShipPanelMenu.LEFT_COLUMN + 1, y + ShipPanelMenu.INVENTORY_Y - 16, 0x402E7A92);
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            if (slot instanceof ShipPanelMenu.TechSlot tech) {
                if (tech.unlocked()) {
                    MachineScreen.slotFrame(graphics, sx, sy);
                    if (!slot.hasItem()) {
                        int faint = (SHIP_COLOR & 0x00FFFFFF) | 0x60000000;
                        graphics.fill(sx, sy, sx + 2, sy + 1, faint);
                        graphics.fill(sx, sy, sx + 1, sy + 2, faint);
                        graphics.fill(sx + 14, sy + 15, sx + 16, sy + 16, faint);
                        graphics.fill(sx + 15, sy + 14, sx + 16, sy + 16, faint);
                    }
                } else if (slot.hasItem()) {
                    // 档次降了：东西还在，但不生效
                    graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, BAD);
                    graphics.fill(sx, sy, sx + 16, sy + 16, LOCKED);
                } else {
                    locked(graphics, sx, sy);
                }
            } else {
                MachineScreen.slotFrame(graphics, sx, sy);
            }
        }
    }

    private static void locked(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF1C2A31);
        graphics.fill(x, y, x + 16, y + 16, LOCKED);
        for (int i = 0; i < 16; i += 4) {
            for (int j = 0; j < 16 - i; j++) {
                graphics.fill(x + i + j, y + 15 - j, x + i + j + 1, y + 16 - j, 0xFF1F282E);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
        ShipRating rating = this.menu.rating();
        ShipType type = rating.type();
        int x = 8;
        int y = 22;
        graphics.text(this.font, Component.translatable("gui.universe.ship_panel.rating",
                Component.translatable("ship.universe.type." + type.key()), rating.grade().letter()), x, y, 0xFF000000 | rating.grade().color(), false);
        y += LINE;
        y = this.small(graphics, Component.translatable("gui.universe.ship_panel.quality", Math.round(rating.quality())), x, y, DIM);
        y = this.small(graphics, specialty(rating), x, y, rating.bonus() > 0 ? GOOD : DIM);
        y += 2;
        ShipStats base = this.menu.baseStats();
        ShipStats stats = this.menu.stats();
        // 整数显示的数值，加了多少也按显示出来的整数算，免得"45 +5.4"
        y = this.stat(graphics, "hull", round(stats.hull()), Math.round(stats.hull()) - Math.round(base.hull()), x, y);
        y = this.stat(graphics, "shield", round(stats.shield()), Math.round(stats.shield()) - Math.round(base.shield()), x, y);
        y = this.stat(graphics, "fuel", round(stats.fuelCapacity()), 0, x, y);
        double twr = stats.thrustToWeight(1);
        y = this.stat(graphics, "lift", String.format(Locale.ROOT, "%.2f", twr), 0, x, y);
        if (stats.impulse() > 0) {
            y = this.stat(graphics, "impulse", round(stats.impulseSpeed() * 20), (stats.impulseSpeed() - base.impulseSpeed()) * 20, x, y);
        }
        if (stats.warpDrives() > 0) {
            y = this.stat(graphics, "warp", String.format(Locale.ROOT, "%.1f", stats.warpRange()), stats.warpRange() - base.warpRange(), x, y);
        }
        if (stats.armed()) {
            y = this.stat(graphics, "damage", Math.round(stats.damage() * 100) + "%", 0, x, y);
            if (stats.fireRate() > 1.005) {
                y = this.stat(graphics, "fire_rate", Math.round(stats.fireRate() * 100) + "%", 0, x, y);
            }
        }
        y = this.stat(graphics, "cargo", String.valueOf(this.menu.cargoSlots()), 0, x, y);
        this.small(graphics, Component.translatable("gui.universe.ship_panel.drive",
                Component.translatable("ship.universe.drive." + stats.drive().name().toLowerCase(Locale.ROOT))), x, y, TEXT);

        int used = 0;
        for (int i = 0; i < rating.slots(); i++) {
            if (this.menu.slots.get(i).hasItem()) {
                used++;
            }
        }
        graphics.text(this.font, Component.translatable("gui.universe.ship_panel.slots", used, rating.slots()),
                ShipPanelMenu.TECH_X, ShipPanelMenu.TECH_Y - 10, SHIP_COLOR, false);
        int hintY = ShipPanelMenu.TECH_Y + 2 * 18 + 6;
        this.small(graphics, Component.translatable(this.menu.flying() ? "gui.universe.ship_panel.hint_flying" : "gui.universe.ship_panel.hint_parked"),
                ShipPanelMenu.TECH_X - 4, hintY, DIM, this.imageWidth - ShipPanelMenu.TECH_X - 2);
    }

    /** 一行数值；技术和专长加出来的部分用绿字写在后面。 */
    private int stat(GuiGraphicsExtractor graphics, String key, String value, double gained, int x, int y) {
        Component line = Component.translatable("gui.universe.ship_panel.stat." + key, value);
        if (gained > 0.05) {
            boolean whole = gained >= 10 || Math.abs(gained - Math.rint(gained)) < 1e-9;
            line = line.copy().append(Component.literal(" +" + (whole ? String.valueOf(Math.round(gained))
                    : String.format(Locale.ROOT, "%.1f", gained))).withColor(GOOD));
        }
        return this.small(graphics, line, x, y, TEXT);
    }

    /** 类型的专长：加在哪一项上、加多少。 */
    private static Component specialty(ShipRating rating) {
        double bonus = rating.bonus();
        if (bonus <= 0) {
            return Component.translatable("gui.universe.ship_panel.specialty_none");
        }
        String percent = rating.type() == ShipType.SHUTTLE
                ? String.valueOf(Math.round(100 * bonus / (1 + bonus)))
                : String.valueOf(Math.round(100 * bonus));
        return Component.translatable("gui.universe.ship_panel.specialty." + rating.type().key(), percent);
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value));
    }

    private int small(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        return this.small(graphics, text, x, y, color, ShipPanelMenu.LEFT_COLUMN - x - 4);
    }

    private int small(GuiGraphicsExtractor graphics, Component text, int x, int y, int color, int width) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(SCALE);
        int end = TextWrap.draw(graphics, this.font, text, 0, 0, (int) (width / SCALE), color, false);
        pose.popMatrix();
        return y + (int) Math.ceil(end * SCALE) + 1;
    }
}
