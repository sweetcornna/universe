package io.github.sweetcornna.universe.client.beacon;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.beacon.Memo;
import io.github.sweetcornna.universe.network.MemoPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 星球或恒星系的备忘（ADR-0033 §六）：几行字，每行能打勾。所有玩家共用一页，存了别人也看得到。 */
public class MemoScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int ACCENT = 0xFF7FE0F7;
    private static final int DIM = 0xFF8BA3AF;
    private final String key;
    private final Component subject;
    private final @Nullable Screen parent;
    private final MemoEditor editor;
    private int left;
    private int top;

    public MemoScreen(String key, Component subject, Memo memo, @Nullable Screen parent) {
        super(Component.translatable("gui.universe.memo.title"));
        this.key = key;
        this.subject = subject;
        this.parent = parent;
        this.editor = new MemoEditor(memo);
    }

    private int panelHeight() {
        return 40 + this.editor.height() + 30;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = Math.max(4, (this.height - this.panelHeight()) / 2);
        this.editor.init(this.font, this::addRenderableWidget, this.left + 10, this.top + 36, WIDTH - 20);
        int buttons = this.top + this.panelHeight() - 26;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.memo.save"), button -> this.save())
                .bounds(this.left + WIDTH - 10 - 64 * 2 - 6, buttons, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.blueprint.save.cancel"), button -> this.onClose())
                .bounds(this.left + WIDTH - 10 - 64, buttons, 64, 20).build());
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new MemoPayloads.Save(this.key, this.editor.memo()));
        this.onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int height = this.panelHeight();
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + height + 1, 0xFF2E7A92);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + height, 0xF2101A21);
        graphics.text(this.font, this.title, this.left + 10, this.top + 8, ACCENT, false);
        graphics.text(this.font, this.font.plainSubstrByWidth(this.subject.getString(), WIDTH - 20), this.left + 10, this.top + 20, DIM, false);
        this.editor.render(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.editor.click(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
