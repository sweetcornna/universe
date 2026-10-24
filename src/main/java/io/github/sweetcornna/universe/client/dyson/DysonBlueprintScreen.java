package io.github.sweetcornna.universe.client.dyson;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.core.dyson.DysonBlueprint;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 导入戴森球蓝图（ADR-0030 §四）：粘贴一串蓝图，或者从本地蓝图库里挑一条。坏的串、别的版本的串当场说明原因，
 * 不发给服务端。
 */
public class DysonBlueprintScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 190;
    private static final int ROW = 12;

    private final DysonEditorScreen parent;
    private final int layerId;
    private @Nullable EditBox box;
    private List<DysonLibrary.Entry> entries = List.of();
    private int scroll;
    /** 粘贴的串有什么问题；没问题是 null。 */
    private @Nullable Component error;
    /** 认出来的蓝图有多少节点、框架、球壳；没认出来是 null。 */
    private @Nullable Component summary;

    DysonBlueprintScreen(DysonEditorScreen parent, int layerId) {
        super(Component.translatable("gui.universe.dyson.blueprint.title"));
        this.parent = parent;
        this.layerId = layerId;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        String value = this.box == null ? "" : this.box.getValue();
        this.box = new EditBox(this.font, left + 10, top + 26, WIDTH - 20, 16, Component.translatable("gui.universe.dyson.blueprint.paste"));
        this.box.setMaxLength(DysonBlueprint.MAX_TEXT);
        this.box.setHint(Component.translatable("gui.universe.dyson.blueprint.paste"));
        this.box.setResponder(this::check);
        this.box.setValue(value);
        this.addRenderableWidget(this.box);
        this.setInitialFocus(this.box);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.dyson.blueprint.do_import"), button -> this.doImport())
                .bounds(left + WIDTH - 10 - 70 * 2 - 6, top + HEIGHT - 26, 70, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> this.onClose())
                .bounds(left + WIDTH - 10 - 70, top + HEIGHT - 26, 70, 20).build());
        this.entries = DysonLibrary.list();
    }

    /** 每打一个字查一遍，当场说行不行。 */
    private void check(String text) {
        this.error = null;
        this.summary = null;
        if (text.isBlank()) {
            return;
        }
        DysonBlueprint.Parsed parsed = DysonBlueprint.parse(text);
        if (parsed.error() != null) {
            Component reason = Component.translatable(parsed.error().key());
            this.error = parsed.problem() == null ? reason
                    : Component.translatable("gui.universe.dyson.result_detail", reason, Component.translatable(parsed.problem().key()));
            return;
        }
        this.summary = Component.translatable("gui.universe.dyson.blueprint.summary", parsed.design().nodeCount(), parsed.design().frameCount(),
                parsed.design().shellCount());
    }

    private void doImport() {
        String text = this.box == null ? "" : this.box.getValue().strip();
        if (text.isEmpty() || this.error != null) {
            return;
        }
        this.parent.importBlueprint(this.layerId, text);
        this.onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, MachineScreen.BORDER);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, MachineScreen.PANEL);
        graphics.text(this.font, this.title, left + 10, top + 9, MachineScreen.ACCENT, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int y = top + 46;
        if (this.error != null) {
            graphics.text(this.font, this.error, left + 10, y, MachineScreen.WARN, false);
        } else if (this.summary != null) {
            graphics.text(this.font, this.summary, left + 10, y, MachineScreen.GOOD, false);
        } else {
            graphics.text(this.font, Component.translatable("gui.universe.dyson.blueprint.only_empty"), left + 10, y, MachineScreen.DIM, false);
        }
        // 本地蓝图库
        int listTop = top + 60;
        int listBottom = top + HEIGHT - 32;
        graphics.text(this.font, Component.translatable("gui.universe.dyson.blueprint.library"), left + 10, listTop - 11, MachineScreen.TEXT,
                false);
        graphics.fill(left + 10, listTop, left + WIDTH - 10, listBottom, MachineScreen.INSET);
        graphics.enableScissor(left + 10, listTop, left + WIDTH - 10, listBottom);
        int row = listTop + 2 - this.scroll;
        if (this.entries.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.universe.dyson.blueprint.library_empty"), left + 15, row + 2, MachineScreen.DIM,
                    false);
        }
        for (DysonLibrary.Entry entry : this.entries) {
            boolean hovered = mouseX >= left + 10 && mouseX < left + WIDTH - 10 && mouseY >= row && mouseY < row + ROW;
            if (hovered) {
                graphics.fill(left + 11, row, left + WIDTH - 11, row + ROW, 0xFF12303B);
            }
            graphics.text(this.font, entry.name(), left + 15, row + 2, MachineScreen.TEXT, false);
            Component load = Component.translatable("gui.universe.dyson.blueprint.load");
            graphics.text(this.font, load, left + WIDTH - 15 - this.font.width(load), row + 2, MachineScreen.ACCENT, false);
            row += ROW;
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        int listTop = top + 60;
        int listBottom = top + HEIGHT - 32;
        if (event.x() >= left + 10 && event.x() < left + WIDTH - 10 && event.y() >= listTop && event.y() < listBottom) {
            int index = (int) ((event.y() - (listTop + 2 - this.scroll)) / ROW);
            if (index >= 0 && index < this.entries.size() && this.box != null) {
                String text = DysonLibrary.read(this.entries.get(index).path());
                if (text != null) {
                    this.box.setValue(text);
                } else {
                    this.error = Component.translatable("gui.universe.dyson.blueprint.error.corrupt");
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int content = this.entries.size() * ROW;
        int visible = HEIGHT - 92;
        this.scroll = Math.max(0, Math.min(Math.max(0, content - visible), this.scroll - (int) Math.round(scrollY * ROW)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            this.doImport();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
