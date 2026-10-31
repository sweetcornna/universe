package io.github.sweetcornna.universe.client.terraform;

import java.util.List;

import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.terraform.Terraform;
import io.github.sweetcornna.universe.terraform.ThresholdText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 星球改造的门槛（ADR-0036 §六），一行一项：名字、要到的范围、现在的值，到了的绿色，没到的红色。
 * 大气释放塔的界面用；发现日志的星球页也可以用同一套。
 */
public final class ThresholdRows {
    /** 一行多高。 */
    public static final int ROW_HEIGHT = 11;

    private ThresholdRows() {
    }

    /**
     * 画门槛表。
     *
     * @param width 一行多宽：名字靠左，范围在中间偏右，现在的值靠右
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, List<Terraform.Threshold> thresholds) {
        int rangeX = x + width * 9 / 20;
        for (int i = 0; i < thresholds.size(); i++) {
            Terraform.Threshold threshold = thresholds.get(i);
            int rowY = y + i * ROW_HEIGHT;
            graphics.text(font, Component.translatable(ThresholdText.labelKey(threshold.kind())), x, rowY, MachineScreen.TEXT, false);
            graphics.text(font, Component.literal(ThresholdText.range(threshold)), rangeX, rowY, MachineScreen.DIM, false);
            String value = ThresholdText.value(threshold);
            graphics.text(font, Component.literal(value), x + width - font.width(value), rowY, threshold.met() ? MachineScreen.GOOD : MachineScreen.BAD,
                    false);
        }
    }
}
