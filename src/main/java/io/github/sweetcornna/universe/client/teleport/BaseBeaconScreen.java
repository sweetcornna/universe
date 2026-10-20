package io.github.sweetcornna.universe.client.teleport;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.core.discovery.DiscoveryLog;
import io.github.sweetcornna.universe.network.OpenBaseBeaconPayload;
import io.github.sweetcornna.universe.network.RenameBasePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** 基地信标的界面（ADR-0017）：给基地改名，留空恢复默认名。 */
public class BaseBeaconScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 92;
    private static final int ACCENT = 0xFF6EDCF5;
    private final OpenBaseBeaconPayload payload;
    private EditBox nameBox;
    private Button saveButton;
    private int left;
    private int top;

    public BaseBeaconScreen(OpenBaseBeaconPayload payload) {
        super(Component.translatable("block.universe.base_beacon"));
        this.payload = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component defaultName() {
        return TeleporterScreen.defaultBaseName(this.payload.body(), "", this.payload.number());
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        String value = this.nameBox == null ? this.payload.name() : this.nameBox.getValue();
        this.nameBox = new EditBox(this.font, this.left + 12, this.top + 40, WIDTH - 24, 16, Component.translatable("gui.universe.discovery.rename"));
        this.nameBox.setMaxLength(DiscoveryLog.MAX_NAME);
        this.nameBox.setValue(value);
        this.nameBox.setHint(this.defaultName());
        this.nameBox.setResponder(text -> this.saveButton.active = DiscoveryLog.cleanName(text).isPresent());
        this.addRenderableWidget(this.nameBox);
        this.saveButton = Button.builder(Component.translatable("gui.universe.discovery.save"), b -> this.save())
                .bounds(this.left + WIDTH - 72, this.top + 64, 60, 20).build();
        this.saveButton.active = DiscoveryLog.cleanName(value).isPresent();
        this.addRenderableWidget(this.saveButton);
        this.setInitialFocus(this.nameBox);
    }

    private void save() {
        ClientPacketDistributor.sendToServer(new RenameBasePayload(this.payload.pos(), this.nameBox.getValue()));
        this.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (this.saveButton.active) {
                this.save();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left - 1, this.top - 1, this.left + WIDTH + 1, this.top + HEIGHT + 1, 0xFF2E6E7E);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, 0xF0080C0E);
        graphics.text(this.font, this.title, this.left + 12, this.top + 10, ACCENT, false);
        graphics.text(this.font, Component.translatable("gui.universe.base.rename_hint"), this.left + 12, this.top + 26, 0xFF9AA8AE, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
}
