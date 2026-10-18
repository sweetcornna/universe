package io.github.sweetcornna.universe.client.factory;

import java.util.Locale;

import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 设备界面（ADR-0011）：背景用代码画，深色面板加青色描边，和飞船仪表盘一个风格。子类在 {@link #extractInfo} 里画
 * 各自的读数。
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    public static final int BORDER = 0xFF2E7A92;
    public static final int PANEL = 0xF2101A21;
    public static final int INSET = 0xFF0A1015;
    public static final int ACCENT = 0xFF7FE0F7;
    public static final int TEXT = 0xFFDCE8EE;
    public static final int DIM = 0xFF8BA3AF;
    public static final int GOOD = 0xFF5BD47A;
    public static final int WARN = 0xFFE8C14A;
    public static final int BAD = 0xFFE5583F;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.type().width(), menu.type().height());
        this.inventoryLabelX = menu.type().inventoryX();
        this.inventoryLabelY = menu.type().inventoryY() - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        // 标题下面一道细线
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        for (Slot slot : this.menu.slots) {
            slotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    /** 一个格子的底框（18×18，物品画在里面 16×16）。 */
    public static void slotFrame(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF24404D);
        graphics.fill(x, y, x + 16, y + 16, INSET);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
        if (this.menu.type().showInventory()) {
            graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
        }
        this.extractInfo(graphics, mouseX - this.leftPos, mouseY - this.topPos);
    }

    /** 设备的读数，坐标相对界面左上角。 */
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
    }

    /** 电力一行：没接上电网时红字提示，接上了显示功率和满足率（发电机是负荷）。 */
    protected void powerLine(GuiGraphicsExtractor graphics, int x, int y) {
        int status = this.menu.data(0);
        if (status < 0) {
            graphics.text(this.font, Component.translatable("gui.universe.machine.unpowered"), x, y, BAD, false);
            return;
        }
        if ((this.menu.data(3) & 2) != 0) {
            // 发电机：负荷低只是电网用得少，不算故障
            graphics.text(this.font, Component.translatable("gui.universe.machine.output", watts(this.menu.wide(1)), status / 10), x, y,
                    status > 0 ? GOOD : DIM, false);
            return;
        }
        if ((this.menu.data(3) & 4) == 0) {
            // 用电设备没在干活：不要电，满足率没有意义
            graphics.text(this.font, Component.translatable("gui.universe.machine.standby"), x, y, DIM, false);
            return;
        }
        int color = status >= 999 ? GOOD : status >= 500 ? WARN : BAD;
        graphics.text(this.font, Component.translatable("gui.universe.machine.power", watts(this.menu.wide(1)), status / 10), x, y, color, false);
    }

    /** 横条：底色、填充、描边。 */
    public static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float share, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF24404D);
        graphics.fill(x, y, x + width, y + height, INSET);
        int filled = Math.round(width * Math.max(0, Math.min(1, share)));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + height, color);
            graphics.fill(x, y, x + filled, y + 1, lighten(color));
        }
    }

    /** 每个通道往白色靠一半。 */
    public static int lighten(int color) {
        int r = (((color >> 16) & 0xFF) + 0xFF) >> 1;
        int g = (((color >> 8) & 0xFF) + 0xFF) >> 1;
        int b = ((color & 0xFF) + 0xFF) >> 1;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** 界面数据里的功率（焦耳/刻 × 10）换成瓦特显示。 */
    public static String watts(long tenthJoulesPerTick) {
        double watts = tenthJoulesPerTick * 2.0;
        if (watts < 1000) {
            return String.format(Locale.ROOT, "%.0f W", watts);
        }
        if (watts < 1_000_000) {
            return String.format(Locale.ROOT, "%.2f kW", watts / 1000);
        }
        return String.format(Locale.ROOT, "%.2f MW", watts / 1_000_000);
    }

    /** 焦耳换成合适的单位。 */
    public static String joules(long joules) {
        if (joules < 1000) {
            return joules + " J";
        }
        if (joules < 1_000_000) {
            return String.format(Locale.ROOT, "%.1f kJ", joules / 1000.0);
        }
        return String.format(Locale.ROOT, "%.2f MJ", joules / 1_000_000.0);
    }
}
