package io.github.sweetcornna.universe.client.poi;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.galaxy.GalaxyText;
import io.github.sweetcornna.universe.core.poi.OutpostLog;
import io.github.sweetcornna.universe.poi.PoiInteractions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 兴趣点的文字界面（ADR-0014）：数据终端的日志、石碑上的先驱者线索。文字一行行显示出来，点一下或按空格直接显示全部，
 * Esc、回车关闭。终端是青色的屏幕字，石碑是紫色的符文光。
 */
public class PoiTextScreen extends Screen {
    private static final int TICKS_PER_LINE = 2;
    private static final int MAX_WIDTH = 340;

    private final Component heading;
    private final List<Component> paragraphs;
    private final Component footer;
    private final int accent;
    private final int text;
    private final int border;
    private List<FormattedCharSequence> lines = List.of();
    private int panelWidth;
    private int ticks;

    private PoiTextScreen(Component title, Component heading, List<Component> paragraphs, Component footer, int accent, int text, int border) {
        super(title);
        this.heading = heading;
        this.paragraphs = paragraphs;
        this.footer = footer;
        this.accent = accent;
        this.text = text;
        this.border = border;
    }

    /** 数据终端：按种子拼一份据点日志，人名按客户端的语言写。 */
    public static PoiTextScreen terminal(long seed, boolean rewarded) {
        OutpostLog log = OutpostLog.of(seed);
        String author = GalaxyText.name(log.author());
        String crewmate = GalaxyText.name(log.crewmate());
        List<Component> paragraphs = new ArrayList<>();
        paragraphs.add(Component.translatable("poi.universe.log.opening." + (log.opening() + 1), log.code(), log.day(), author));
        paragraphs.add(Component.translatable("poi.universe.log.situation." + (log.situation() + 1), crewmate));
        paragraphs.add(Component.translatable("poi.universe.log.event." + (log.event() + 1), crewmate));
        paragraphs.add(Component.translatable("poi.universe.log.ending." + (log.ending() + 1)));
        paragraphs.add(Component.translatable("gui.universe.terminal.signature", author));
        Component footer = rewarded ? Component.translatable("gui.universe.poi.reward", Component.translatable("item.universe.navigation_data"), 1)
                : Component.translatable("gui.universe.terminal.read_before");
        return new PoiTextScreen(Component.translatable("gui.universe.terminal.title", log.code()),
                Component.translatable("gui.universe.terminal.heading", log.code()), paragraphs, footer, 0xFF6EF2D8, 0xFFBDEFE4, 0xFF2E8C7A);
    }

    /** 石碑：第几段先驱者线索。 */
    public static PoiTextScreen monolith(int lore, boolean rewarded) {
        int index = Math.floorMod(lore, PoiInteractions.LORE_COUNT) + 1;
        List<Component> paragraphs = List.of(Component.translatable("poi.universe.lore." + index + ".text"));
        Component footer = rewarded ? Component.translatable("gui.universe.poi.reward", Component.translatable("item.universe.survey_data"), 3)
                : Component.translatable("gui.universe.monolith.touched_before");
        return new PoiTextScreen(Component.translatable("poi.universe.lore." + index + ".title"),
                Component.translatable("gui.universe.monolith.heading", index, PoiInteractions.LORE_COUNT), paragraphs, footer,
                0xFFC79BFF, 0xFFE6DAFF, 0xFF6A4A9C);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(MAX_WIDTH, this.width - 40);
        int wrap = this.panelWidth - 28;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (int i = 0; i < this.paragraphs.size(); i++) {
            if (i > 0) {
                wrapped.add(FormattedCharSequence.EMPTY);
            }
            wrapped.addAll(this.font.split(this.paragraphs.get(i), wrap));
        }
        this.lines = wrapped;
    }

    @Override
    public void tick() {
        this.ticks++;
    }

    private int shown() {
        return Math.min(this.lines.size(), this.ticks / TICKS_PER_LINE + 1);
    }

    private boolean done() {
        return this.shown() >= this.lines.size();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int lineHeight = this.font.lineHeight + 2;
        int height = 46 + this.lines.size() * lineHeight + 22;
        height = Math.min(height, this.height - 20);
        int x = (this.width - this.panelWidth) / 2;
        int y = (this.height - height) / 2;
        graphics.fill(x - 1, y - 1, x + this.panelWidth + 1, y + height + 1, this.border);
        graphics.fill(x, y, x + this.panelWidth, y + height, 0xF0060B10);
        // 屏幕上的扫描线
        for (int sy = y + 2; sy < y + height - 1; sy += 3) {
            graphics.fill(x + 1, sy, x + this.panelWidth - 1, sy + 1, 0x0CFFFFFF);
        }
        graphics.text(this.font, this.heading, x + 14, y + 10, (this.accent & 0x00FFFFFF) | 0xB0000000, false);
        graphics.text(this.font, this.title, x + 14, y + 22, this.accent, false);
        graphics.fill(x + 14, y + 34, x + this.panelWidth - 14, y + 35, (this.accent & 0x00FFFFFF) | 0x60000000);
        int ty = y + 42;
        int shown = this.shown();
        for (int i = 0; i < shown && ty < y + height - 24; i++) {
            graphics.text(this.font, this.lines.get(i), x + 14, ty, this.text, false);
            ty += lineHeight;
        }
        if (this.done()) {
            graphics.text(this.font, this.footer, x + 14, y + height - 16, (this.accent & 0x00FFFFFF) | 0xD0000000, false);
            Component close = Component.translatable("gui.universe.poi.close");
            graphics.text(this.font, close, x + this.panelWidth - 14 - this.font.width(close), y + height - 16, 0x80FFFFFF, false);
        } else if (this.ticks / 6 % 2 == 0) {
            // 光标
            graphics.fill(x + 14, ty + 1, x + 20, ty + this.font.lineHeight - 1, this.accent);
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.done()) {
            this.ticks = this.lines.size() * TICKS_PER_LINE;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (!this.done()) {
                this.ticks = this.lines.size() * TICKS_PER_LINE;
            } else {
                this.onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }
}
