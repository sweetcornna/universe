package io.github.sweetcornna.universe.client.chem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetcornna.universe.chem.AtmosphericExtractorBlockEntity;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.chem.Gas;
import io.github.sweetcornna.universe.factory.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 大气提取器（ADR-0036 §三）：左边写在取什么、气压和产量，下面一排按钮列出这里能取的气体（化学式写在按钮上，
 * 名字太长放不下），右边一个产物格。
 */
public class AtmosphericExtractorScreen extends MachineScreen {
    private static final int BUTTON_Y = 64;
    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_STEP_X = 62;
    private static final int BUTTON_STEP_Y = 14;
    private static final int COLUMNS = 3;

    public AtmosphericExtractorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** 能取的气体，按序号排。 */
    private List<Gas> collectable() {
        int bits = this.menu.data(AtmosphericExtractorBlockEntity.COLLECTABLE);
        List<Gas> gases = new ArrayList<>();
        for (Gas gas : Gas.values()) {
            if ((bits & 1 << gas.ordinal()) != 0) {
                gases.add(gas);
            }
        }
        return gases;
    }

    private Gas selected() {
        int index = this.menu.data(AtmosphericExtractorBlockEntity.SELECTED) - 1;
        return index >= 0 && index < Gas.values().length ? Gas.values()[index] : null;
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.powerLine(graphics, 8, 22);
        Component label = Component.translatable("gui.universe.machine.output_slot");
        graphics.text(this.font, label, 180 - this.font.width(label) / 2, 28, DIM, false);
        int status = this.menu.data(AtmosphericExtractorBlockEntity.STATUS);
        Gas selected = this.selected();
        if (status == AtmosphericExtractorBlockEntity.NO_ATMOSPHERE || selected == null) {
            graphics.text(this.font, Component.translatable("gui.universe.atmospheric_extractor.no_atmosphere"), 8, 36, BAD, false);
            return;
        }
        Component state = switch (status) {
            case AtmosphericExtractorBlockEntity.NO_SKY -> Component.translatable("gui.universe.atmospheric_extractor.no_sky");
            case AtmosphericExtractorBlockEntity.FULL -> Component.translatable("gui.universe.machine.output_full");
            default -> Component.translatable("gui.universe.atmospheric_extractor.collecting",
                    Component.translatable("item.universe." + selected.item()));
        };
        graphics.text(this.font, state, 8, 36, status == AtmosphericExtractorBlockEntity.OK ? TEXT : BAD, false);
        double pressure = this.menu.wide(AtmosphericExtractorBlockEntity.PRESSURE) / 1000.0;
        double rate = this.menu.data(AtmosphericExtractorBlockEntity.RATE) / 10.0;
        graphics.text(this.font, Component.translatable("gui.universe.atmospheric_extractor.rate",
                String.format(Locale.ROOT, pressure >= 1 ? "%.2f" : "%.3f", pressure), String.format(Locale.ROOT, "%.1f", rate)),
                8, 48, DIM, false);
        List<Gas> gases = this.collectable();
        for (int i = 0; i < gases.size(); i++) {
            Gas gas = gases.get(i);
            int x = 8 + i % COLUMNS * BUTTON_STEP_X;
            int y = BUTTON_Y + i / COLUMNS * BUTTON_STEP_Y;
            this.textButton(graphics, x, y, BUTTON_WIDTH, Component.literal(gas.formula()), gas == selected ? GOOD : TEXT, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double x = event.x() - this.leftPos;
            double y = event.y() - this.topPos;
            List<Gas> gases = this.collectable();
            for (int i = 0; i < gases.size(); i++) {
                if (overMode(x, y, 8 + i % COLUMNS * BUTTON_STEP_X, BUTTON_Y + i / COLUMNS * BUTTON_STEP_Y, BUTTON_WIDTH)) {
                    this.clickMode(gases.get(i).ordinal());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
