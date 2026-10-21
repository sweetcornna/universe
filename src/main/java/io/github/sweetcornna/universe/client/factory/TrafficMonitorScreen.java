package io.github.sweetcornna.universe.client.factory;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.belt.TrafficMonitorBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 流速监测器（ADR-0022）：最近一分钟的流量、只数哪种物品、报警的下限，下面一排按钮调下限。 */
public class TrafficMonitorScreen extends MachineScreen {
    private static final int BUTTON_Y = 62;
    private static final int BUTTON_WIDTH = 26;
    private static final int BUTTON_GAP = 2;
    private static final int BUTTON_X = 6;

    public TrafficMonitorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** 过滤的物品在客户端的方块实体上（随方块更新同步过来）。 */
    private @Nullable Item filter() {
        if (this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof TrafficMonitorBlockEntity monitor) {
            return monitor.filter();
        }
        return null;
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int count = this.menu.data(TrafficMonitorBlockEntity.COUNT);
        int threshold = this.menu.data(TrafficMonitorBlockEntity.THRESHOLD);
        int flags = this.menu.data(TrafficMonitorBlockEntity.FLAGS);
        graphics.text(this.font, Component.translatable("gui.universe.traffic_monitor.count", count), 8, 22, TEXT, false);
        Item filter = this.filter();
        graphics.text(this.font, filter == null ? Component.translatable("gui.universe.traffic_monitor.all")
                : Component.translatable("gui.universe.traffic_monitor.only", new ItemStack(filter).getHoverName()), 8, 34, DIM, false);
        Component status;
        int color;
        if (threshold == 0) {
            status = Component.translatable("gui.universe.traffic_monitor.no_threshold");
            color = DIM;
        } else if ((flags & 1) != 0) {
            status = Component.translatable("gui.universe.traffic_monitor.alarm", threshold);
            color = BAD;
        } else if ((flags & 2) == 0) {
            status = Component.translatable("gui.universe.traffic_monitor.warming", threshold);
            color = DIM;
        } else {
            status = Component.translatable("gui.universe.traffic_monitor.ok", threshold);
            color = GOOD;
        }
        graphics.text(this.font, status, 8, 46, color, false);
        int[] steps = TrafficMonitorBlockEntity.STEPS;
        for (int i = 0; i < steps.length; i++) {
            this.textButton(graphics, buttonX(i), BUTTON_Y, BUTTON_WIDTH, Component.literal((steps[i] > 0 ? "+" : "") + steps[i]),
                    steps[i] > 0 ? GOOD : WARN, mouseX, mouseY);
        }
    }

    private static int buttonX(int index) {
        return BUTTON_X + index * (BUTTON_WIDTH + BUTTON_GAP);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < TrafficMonitorBlockEntity.STEPS.length; i++) {
                if (overMode(event.x() - this.leftPos, event.y() - this.topPos, buttonX(i), BUTTON_Y, BUTTON_WIDTH)) {
                    this.clickMode(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
