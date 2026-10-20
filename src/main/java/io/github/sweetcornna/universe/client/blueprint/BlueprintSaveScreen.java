package io.github.sweetcornna.universe.client.blueprint;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.network.BlueprintPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 保存蓝图（ADR-0018）：起个名字，回车或点保存。 */
public class BlueprintSaveScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 92;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private @Nullable EditBox name;

    public BlueprintSaveScreen(int sizeX, int sizeY, int sizeZ) {
        super(Component.translatable("gui.universe.blueprint.save.title"));
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    @Override
    protected void init() {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        String value = this.name == null ? "" : this.name.getValue();
        this.name = new EditBox(this.font, left + 10, top + 36, WIDTH - 20, 16, Component.translatable("gui.universe.blueprint.save.name"));
        this.name.setMaxLength(BlueprintPayloads.Save.MAX_NAME);
        this.name.setValue(value);
        this.name.setHint(Component.translatable("gui.universe.blueprint.save.name"));
        this.addRenderableWidget(this.name);
        this.setInitialFocus(this.name);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.blueprint.save.save"), button -> this.save())
                .bounds(left + WIDTH - 10 - 64 * 2 - 6, top + HEIGHT - 28, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.universe.blueprint.save.cancel"), button -> this.onClose())
                .bounds(left + WIDTH - 10 - 64, top + HEIGHT - 28, 64, 20).build());
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new BlueprintPayloads.Save(this.name == null ? "" : this.name.getValue()));
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF2A6A8C);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xF00A1620);
        graphics.text(this.font, this.title, left + 10, top + 9, 0xFF7FE3FF, false);
        graphics.text(this.font, Component.translatable("gui.universe.blueprint.save.size", this.sizeX, this.sizeY, this.sizeZ), left + 10, top + 21,
                0xFF8FA3B3, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            this.save();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
