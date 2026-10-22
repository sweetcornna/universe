package io.github.sweetcornna.universe.client.tech;

import static io.github.sweetcornna.universe.client.factory.MachineScreen.ACCENT;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.BORDER;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.DIM;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.PANEL;
import static io.github.sweetcornna.universe.client.factory.MachineScreen.TEXT;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.joml.Matrix3x2fStack;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.survival.MultitoolSpec;
import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.Stat;
import io.github.sweetcornna.universe.core.tech.SuitSlots;
import io.github.sweetcornna.universe.core.tech.TechBonuses;
import io.github.sweetcornna.universe.core.tech.TechModule;
import io.github.sweetcornna.universe.tech.TechMenu;
import io.github.sweetcornna.universe.tech.TechModuleItem;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 技术配置界面（ADR-0014）：左上外骨骼技术槽，左中多功能工具技术槽，左下当前的加成；右边货舱和背包。
 * 没解锁的格子画成带斜纹的暗格。工具格按工具的档次画 4–8 个（ADR-0026），坏的格子画裂纹，鼠标移上去提示怎么修。
 */
public class TechScreen extends AbstractContainerScreen<TechMenu> {
    private static final int SUIT_COLOR = 0xFF6EDCF5;
    private static final int TOOL_COLOR = 0xFFFFAA5A;
    private static final int LOCKED = 0xFF151B20;
    private static final int BONUS_Y = TechMenu.TOOL_Y + 42;
    private static final int TEXT_WIDTH = TechMenu.LEFT_COLUMN - 12;
    private static final float SCALE = 0.75F;
    /** 一行小字的高度。 */
    private static final int LINE = 8;

    /** 加成列表放不下时的全部条目（鼠标提示用），放得下时为空。 */
    private List<Component> overflow = List.of();

    public TechScreen(TechMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TechMenu.WIDTH, TechMenu.HEIGHT);
        this.inventoryLabelX = TechMenu.INVENTORY_X;
        this.inventoryLabelY = TechMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BORDER);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
        graphics.fill(x + 6, y + 16, x + this.imageWidth - 6, y + 17, 0x602E7A92);
        // 左右两栏之间的竖线
        graphics.fill(x + TechMenu.LEFT_COLUMN, y + 20, x + TechMenu.LEFT_COLUMN + 1, y + this.imageHeight - 6, 0x402E7A92);
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            int sx = x + slot.x;
            int sy = y + slot.y;
            if (slot.isActive()) {
                MachineScreen.slotFrame(graphics, sx, sy);
                if (slot instanceof TechMenu.TechSlot tech && !slot.hasItem()) {
                    // 空的技术槽：四个角点一下颜色
                    int color = tech.host() == ModuleHost.EXOSUIT ? SUIT_COLOR : TOOL_COLOR;
                    int faint = (color & 0x00FFFFFF) | 0x60000000;
                    graphics.fill(sx, sy, sx + 2, sy + 1, faint);
                    graphics.fill(sx, sy, sx + 1, sy + 2, faint);
                    graphics.fill(sx + 14, sy + 15, sx + 16, sy + 16, faint);
                    graphics.fill(sx + 15, sy + 14, sx + 16, sy + 16, faint);
                }
            } else if (i < TechMenu.INVENTORY_START && (i >= TechMenu.CARGO_START || i < TechMenu.TOOL_START)) {
                locked(graphics, sx, sy);
            }
            if (slot instanceof TechMenu.TechSlot tech && tech.broken()) {
                broken(graphics, sx, sy);
            }
        }
    }

    /** 坏了的工具格：暗红的底，一道折线裂纹。 */
    private static void broken(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0xFF2A1416);
        int[][] crack = {{3, 2}, {4, 3}, {5, 4}, {6, 5}, {6, 6}, {7, 7}, {8, 8}, {9, 8}, {10, 9}, {10, 10}, {11, 11}, {12, 12}, {12, 13}};
        for (int[] p : crack) {
            graphics.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, 0xFFB0463C);
        }
        graphics.fill(x + 9, y + 3, x + 10, y + 7, 0xFF6E2A26);
        graphics.fill(x + 3, y + 10, x + 7, y + 11, 0xFF6E2A26);
    }

    /** 没解锁的格子：暗底加斜纹。 */
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
        graphics.text(this.font, Component.translatable("gui.universe.tech.exosuit", this.menu.techSlots(), SuitSlots.TECH_MAX),
                TechMenu.SUIT_X, TechMenu.SUIT_Y - 10, SUIT_COLOR, false);
        graphics.text(this.font, this.toolLabel(), TechMenu.TOOL_X, TechMenu.TOOL_Y - 10, TOOL_COLOR, false);
        graphics.text(this.font, Component.translatable("gui.universe.tech.cargo", this.menu.cargoSlots(), SuitSlots.CARGO_MAX),
                TechMenu.CARGO_X, TechMenu.CARGO_Y - 10, TEXT, false);
        if (!this.menu.hasTool()) {
            this.small(graphics, Component.translatable("gui.universe.tech.no_tool"), TechMenu.TOOL_X, TechMenu.TOOL_Y + 2, TEXT_WIDTH, DIM);
        }
        this.bonuses(graphics);
    }

    /** 工具格上面的标题：拿着工具时写上档次和类型，放不下就只写档次和类型。 */
    private Component toolLabel() {
        if (!this.menu.hasTool()) {
            return Component.translatable("gui.universe.tech.multitool");
        }
        MultitoolSpec spec = this.menu.spec();
        Component full = Component.translatable("gui.universe.tech.multitool_spec", spec.grade().letter(), MultitoolItem.className(spec));
        return this.font.width(full) <= TEXT_WIDTH ? full
                : Component.translatable("gui.universe.tech.multitool_short", spec.grade().letter(), MultitoolItem.className(spec));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.hoveredSlot instanceof TechMenu.TechSlot tech && tech.broken()) {
            graphics.setTooltipForNextFrame(this.font, TextWrap.split(this.font,
                    Component.translatable("gui.universe.tech.broken_slot", MultitoolSpec.REPAIR_SCRAP), 160), mouseX, mouseY);
        }
        // 加成列表放不下时，鼠标移到列表上看全部
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        if (!this.overflow.isEmpty() && x >= 6 && x < TechMenu.LEFT_COLUMN && y >= BONUS_Y && y < this.imageHeight) {
            graphics.setTooltipForNextFrame(this.font, this.overflow, Optional.empty(), mouseX, mouseY);
        }
    }

    /** 左下角：两边的加成合起来列出来；放不下时最后一行写"还有几项"。 */
    private void bonuses(GuiGraphicsExtractor graphics) {
        int x = 8;
        int y = BONUS_Y;
        this.overflow = List.of();
        graphics.text(this.font, Component.translatable("gui.universe.tech.bonuses"), x, y, TEXT, false);
        y += 11;
        List<Component> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        boolean suitOff = false;
        for (ModuleHost host : ModuleHost.values()) {
            // 没穿胸甲时外骨骼的加成照样列出来，但画成灰色，上面提示一句
            boolean inactive = host == ModuleHost.EXOSUIT && !this.menu.wearingSuit();
            for (Map.Entry<Stat, Double> entry : this.bonuses(host).totals().entrySet()) {
                lines.add(TechModuleItem.statLine(entry.getKey(), entry.getValue()));
                colors.add(inactive ? DIM : 0xFFB8E8C4);
                suitOff |= inactive;
            }
        }
        if (lines.isEmpty()) {
            this.small(graphics, Component.translatable("gui.universe.tech.no_bonuses"), x, y, TEXT_WIDTH, DIM);
            return;
        }
        if (suitOff) {
            y = this.small(graphics, Component.translatable("gui.universe.tech.suit_off"), x, y, TEXT_WIDTH, 0xFFFF8A65);
        }
        int bottom = this.imageHeight - 5;
        for (int i = 0; i < lines.size(); i++) {
            // 最后一行留给"还有几项"，除非剩下的正好放得下
            int rest = 0;
            for (int j = i; j < lines.size(); j++) {
                rest += this.height(lines.get(j));
            }
            if (y + rest > bottom && y + this.height(lines.get(i)) + LINE > bottom) {
                this.small(graphics, Component.translatable("gui.universe.tech.more", lines.size() - i), x, y, TEXT_WIDTH, DIM);
                this.overflow = lines;
                return;
            }
            y = this.small(graphics, lines.get(i), x, y, TEXT_WIDTH, colors.get(i));
        }
    }

    private TechBonuses bonuses(ModuleHost host) {
        List<TechModule> modules = new ArrayList<>();
        for (ItemStack stack : this.menu.installed(host)) {
            if (stack.getItem() instanceof TechModuleItem item) {
                modules.add(item.module(stack));
            }
        }
        TechBonuses bonuses = TechBonuses.of(modules);
        // 工具的类型专长也算进去（ADR-0026）
        return host == ModuleHost.MULTITOOL && this.menu.hasTool() ? bonuses.with(this.menu.spec().specialty()) : bonuses;
    }

    /** 一段小字占的高度（和 {@link #small} 一致）。 */
    private int height(Component text) {
        return (int) Math.ceil(TextWrap.split(this.font, text, (int) (TEXT_WIDTH / SCALE)).size() * this.font.lineHeight * SCALE) + 1;
    }

    private int small(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int color) {
        float scale = SCALE;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        int end = TextWrap.draw(graphics, this.font, text, 0, 0, (int) (width / scale), color, false);
        pose.popMatrix();
        return y + (int) Math.ceil(end * scale) + 1;
    }
}
