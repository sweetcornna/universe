package io.github.sweetcornna.universe.client.microbe;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetcornna.universe.client.factory.MachineScreen;
import io.github.sweetcornna.universe.core.microbe.Isolation;
import io.github.sweetcornna.universe.core.microbe.Organism;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.microbe.BioMachineBlockEntity;
import io.github.sweetcornna.universe.microbe.IncubatorBlockEntity;
import io.github.sweetcornna.universe.microbe.MicrobeText;
import io.github.sweetcornna.universe.microbe.Sample;
import io.github.sweetcornna.universe.microbe.SampleItem;
import io.github.sweetcornna.universe.microbe.StrainTubeItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 培养箱的界面（ADR-0036 §五）：上面写在培养什么和进度，中间一条 0–120 °C 的温度条（点哪设到哪），
 * 下面四个按钮微调。放进样本时写出采样处的温度，照着设；放进菌种时写它在这个温度下长不长、算不算驯化。
 */
public class IncubatorScreen extends MachineScreen {
    private static final int LEFT = 30;
    private static final int WIDTH = 148;
    private static final int BAR_Y = 70;
    private static final int BUTTON_Y = 80;
    private static final int BUTTON_WIDTH = 35;
    private static final String[] STEPS = {"-10", "-1", "+1", "+10"};

    public IncubatorScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private int temperature() {
        return this.menu.data(IncubatorBlockEntity.TEMPERATURE);
    }

    @Override
    protected void extractInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int center = LEFT + WIDTH / 2;
        ItemStack input = this.menu.getSlot(BioMachineBlockEntity.INPUT).getItem();
        Sample sample = SampleItem.sample(input);
        Organism organism = StrainTubeItem.organism(input);
        IncubatorBlockEntity.Status status = status(this.menu.data(BioMachineBlockEntity.STATUS));
        Component line = switch (status) {
            case EMPTY -> Component.translatable("gui.universe.incubator.empty");
            case TISSUE -> Component.translatable("gui.universe.incubator.tissue");
            case NO_MEDIUM -> Component.translatable("gui.universe.incubator.no_medium");
            case NO_GROWTH -> Component.translatable("gui.universe.incubator.no_growth");
            case OUTPUT_FULL -> Component.translatable("gui.universe.incubator.output_full");
            case WORKING -> sample != null ? Component.translatable("gui.universe.incubator.isolating", SampleItem.describe(sample))
                    : Component.translatable("gui.universe.incubator.expanding", organism == null ? Component.empty() : StrainTubeItem.strainName(organism));
        };
        int color = status == IncubatorBlockEntity.Status.WORKING ? TEXT : status == IncubatorBlockEntity.Status.EMPTY ? DIM : WARN;
        graphics.centeredText(this.font, line, center, 22, color);
        int total = this.menu.data(BioMachineBlockEntity.TOTAL);
        bar(graphics, LEFT, 34, WIDTH, 4, total <= 0 ? 0 : this.menu.data(BioMachineBlockEntity.PROGRESS) / (float) total, GOOD);

        // 提示：样本写采样处的温度，菌种写在这个温度下扩增算不算驯化；没东西时写上次分离的结果
        Component hint = null;
        int hintColor = DIM;
        if (sample != null && !sample.tissue()) {
            hint = Component.translatable("gui.universe.incubator.sampled_at", MicrobeText.temperature(sample.temperature()));
        } else if (organism != null && Isolation.domesticates(organism, this.temperature())) {
            hint = Component.translatable("gui.universe.incubator.domesticating");
            hintColor = GOOD;
        } else if (input.isEmpty()) {
            int last = this.menu.data(IncubatorBlockEntity.LAST);
            if (last == IncubatorBlockEntity.Last.NOTHING.ordinal()) {
                hint = Component.translatable("gui.universe.incubator.last_nothing");
                hintColor = WARN;
            } else if (last == IncubatorBlockEntity.Last.GREW.ordinal()) {
                hint = Component.translatable("gui.universe.incubator.last_grew");
            }
        }
        if (hint != null) {
            graphics.centeredText(this.font, hint, center, 44, hintColor);
        }

        // 温度条：0–120 °C，刻度每 20 度一道
        graphics.text(this.font, Component.translatable("gui.universe.incubator.temperature", MicrobeText.temperature(this.temperature())),
                LEFT, 58, ACCENT, false);
        int span = (int) (Isolation.MAX_SETTING_K - Isolation.MIN_SETTING_K);
        bar(graphics, LEFT, BAR_Y, WIDTH, 5, (this.temperature() - (float) Isolation.MIN_SETTING_K) / span, 0xFFE07B4A);
        for (int c = 0; c <= span; c += 20) {
            int x = LEFT + Math.round(WIDTH * c / (float) span);
            graphics.fill(x, BAR_Y + 5, x + 1, BAR_Y + 7, 0xFF24404D);
        }
        for (int i = 0; i < STEPS.length; i++) {
            this.textButton(graphics, LEFT + i * (BUTTON_WIDTH + 2), BUTTON_Y, BUTTON_WIDTH, Component.literal(STEPS[i]), TEXT, mouseX, mouseY);
        }
        this.powerLine(graphics, LEFT, 96);
    }

    private static IncubatorBlockEntity.Status status(int index) {
        IncubatorBlockEntity.Status[] values = IncubatorBlockEntity.Status.values();
        return index >= 0 && index < values.length ? values[index] : IncubatorBlockEntity.Status.EMPTY;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double x = event.x() - this.leftPos;
            double y = event.y() - this.topPos;
            for (int i = 0; i < STEPS.length; i++) {
                if (overMode(x, y, LEFT + i * (BUTTON_WIDTH + 2), BUTTON_Y, BUTTON_WIDTH)) {
                    this.clickMode(i);
                    return true;
                }
            }
            if (x >= LEFT && x <= LEFT + WIDTH && y >= BAR_Y - 2 && y <= BAR_Y + 8) {
                int span = (int) (Isolation.MAX_SETTING_K - Isolation.MIN_SETTING_K);
                int offset = (int) Math.round((x - LEFT) / WIDTH * span);
                this.clickMode(IncubatorBlockEntity.SET_BUTTON + Math.max(0, Math.min(span, offset)));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
