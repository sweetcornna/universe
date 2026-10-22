package io.github.sweetcornna.universe.client.factory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.sweetcornna.universe.client.TextWrap;
import io.github.sweetcornna.universe.client.UniverseKeys;
import io.github.sweetcornna.universe.core.research.ResearchState;
import io.github.sweetcornna.universe.core.research.Tech;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.factory.research.MatrixLabBlockEntity;
import io.github.sweetcornna.universe.factory.research.Research;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 矩阵研究站的界面（ADR-0011）：左边是全服当前在研究的科技和进度，右边每种矩阵一格（七种，四个一排，第七格是黑雾矩阵，
 * ADR-0028 §六；空着时画出该放的矩阵），
 * 下面一个按钮打开科技树。左下角的按钮切换增产剂的"额外产出"和"加速"（ADR-0022）。
 */
public class MatrixLabScreen extends MachineScreen {
    private static final int TEXT_WIDTH = 100;
    private static final int BUTTON_X = 112;
    private static final int BUTTON_Y = 68;
    private static final int BUTTON_WIDTH = 56;
    private static final int BUTTON_HEIGHT = 14;
    /** 增产模式按钮（ADR-0022），在研究状态下面。 */
    private static final int MODE_Y = 80;

    public MatrixLabScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static ItemStack matrix(int slot) {
        return new ItemStack(Research.item(MatrixLabBlockEntity.matrix(slot)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        // 当前研究要的矩阵格描一圈；空着的格子画出该放的矩阵
        List<String> required = ClientResearch.state().currentMatrices(Research.TREE);
        List<Slot> ghosts = new ArrayList<>();
        for (int i = 0; i < this.menu.type().slots().size(); i++) {
            Slot slot = this.menu.getSlot(i);
            if (required.contains(MatrixLabBlockEntity.matrix(i))) {
                graphics.outline(x + slot.x - 2, y + slot.y - 2, 20, 20, ACCENT);
            }
            if (!slot.hasItem()) {
                graphics.fakeItem(matrix(i), x + slot.x, y + slot.y);
                ghosts.add(slot);
            }
        }
        // 打开科技树的按钮
        boolean hovered = this.overButton(mouseX - x, mouseY - y);
        graphics.fill(x + BUTTON_X, y + BUTTON_Y, x + BUTTON_X + BUTTON_WIDTH, y + BUTTON_Y + BUTTON_HEIGHT, BORDER);
        graphics.fill(x + BUTTON_X + 1, y + BUTTON_Y + 1, x + BUTTON_X + BUTTON_WIDTH - 1, y + BUTTON_Y + BUTTON_HEIGHT - 1,
                hovered ? 0xFF1F4A5A : 0xFF12303B);
        graphics.nextStratum();
        for (Slot slot : ghosts) {
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0x900A1015);
        }
    }

    private boolean overButton(double mouseX, double mouseY) {
        return mouseX >= BUTTON_X && mouseX < BUTTON_X + BUTTON_WIDTH && mouseY >= BUTTON_Y && mouseY < BUTTON_Y + BUTTON_HEIGHT;
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 20);
        ResearchState state = ClientResearch.state();
        String current = state.current();
        Tech tech = current == null ? null : Research.TREE.get(current).orElse(null);
        if (tech == null) {
            TextWrap.draw(graphics, this.font, Component.translatable("gui.universe.matrix_lab.idle",
                    UniverseKeys.TECH_TREE.getTranslatedKeyMessage()), 8, 34, TEXT_WIDTH, DIM, false);
        } else {
            List<FormattedCharSequence> name = TextWrap.split(this.font, Research.name(tech.id()), TEXT_WIDTH);
            if (!name.isEmpty()) {
                graphics.text(this.font, name.getFirst(), 8, 32, ACCENT, false);
            }
            bar(graphics, 8, 43, TEXT_WIDTH, 4, (float) state.progress(tech.id()) / tech.points(), ACCENT);
            graphics.text(this.font, Component.translatable("gui.universe.tech_tree.points", state.progress(tech.id()), tech.points()),
                    8, 50, DIM, false);
            bar(graphics, 8, 61, TEXT_WIDTH, 2, this.menu.data(MatrixLabBlockEntity.PROGRESS) / 1000F, GOOD);
            int status = this.menu.data(MatrixLabBlockEntity.STATUS);
            String key = switch (status) {
                case MatrixLabBlockEntity.RUNNING -> "running";
                case MatrixLabBlockEntity.NO_MATRIX -> "no_matrix";
                case MatrixLabBlockEntity.NO_POWER -> "no_power";
                default -> "waiting";
            };
            int color = switch (status) {
                case MatrixLabBlockEntity.RUNNING -> GOOD;
                case MatrixLabBlockEntity.NO_MATRIX -> WARN;
                case MatrixLabBlockEntity.NO_POWER -> BAD;
                default -> DIM;
            };
            graphics.text(this.font, Component.translatable("gui.universe.matrix_lab." + key), 8, 68, color, false);
        }
        graphics.centeredText(this.font, Component.translatable("gui.universe.matrix_lab.tech_tree"), BUTTON_X + BUTTON_WIDTH / 2,
                BUTTON_Y + 3, TEXT);
        this.modeButton(graphics, 8, MODE_Y, TEXT_WIDTH, this.menu.data(MatrixLabBlockEntity.PROLIFERATION), true, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // 空着的矩阵格也提示该放什么
        Slot slot = this.hoveredSlot;
        if (slot != null && !slot.hasItem() && this.menu.getCarried().isEmpty() && slot.index < this.menu.type().slots().size()) {
            graphics.setTooltipForNextFrame(this.font, matrix(slot.index), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.overButton(event.x() - this.leftPos, event.y() - this.topPos)) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.onClose();
            this.minecraft.gui.setScreen(new TechTreeScreen());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overMode(event.x() - this.leftPos, event.y() - this.topPos, 8, MODE_Y, TEXT_WIDTH)) {
            this.clickMode(MatrixLabBlockEntity.MODE_BUTTON);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
