package io.github.sweetcornna.universe.client.beacon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.sweetcornna.universe.core.beacon.Memo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 一页备忘的编辑区（ADR-0033 §六）：{@link Memo#MAX_LINES} 行，每行左边一个勾选框、右边一个输入框。信标界面和星球备忘的界面共用。
 */
final class MemoEditor {
    static final int ROW = 15;
    private static final int BOX = 10;
    private static final int TEXT = 0xFFDCE8EE;
    private static final int DONE = 0xFF6F8794;
    private static final int FRAME = 0xFF2E7A92;
    private static final int CHECK = 0xFF6EE07A;

    private final String[] texts = new String[Memo.MAX_LINES];
    private final boolean[] done = new boolean[Memo.MAX_LINES];
    private final List<EditBox> boxes = new ArrayList<>();
    private int x;
    private int y;

    MemoEditor(Memo memo) {
        for (int i = 0; i < Memo.MAX_LINES; i++) {
            Memo.Line line = i < memo.size() ? memo.lines().get(i) : null;
            this.texts[i] = line == null ? "" : line.text();
            this.done[i] = line != null && line.done();
        }
    }

    /** 界面 init 时调：建输入框（界面大小变了会重建，字留着）。 */
    void init(Font font, Consumer<EditBox> add, int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.boxes.clear();
        for (int i = 0; i < Memo.MAX_LINES; i++) {
            int index = i;
            EditBox box = new EditBox(font, x + BOX + 4, y + i * ROW, width - BOX - 4, ROW - 2, Component.translatable("gui.universe.memo.line"));
            box.setMaxLength(Memo.MAX_CHARS);
            box.setValue(this.texts[i]);
            box.setTextColor(this.done[i] ? DONE : TEXT);
            if (i == 0) {
                box.setHint(Component.translatable("gui.universe.memo.hint"));
            }
            box.setResponder(text -> this.texts[index] = text);
            this.boxes.add(box);
            add.accept(box);
        }
    }

    int height() {
        return Memo.MAX_LINES * ROW;
    }

    /** 画勾选框（输入框由界面自己画）。 */
    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < Memo.MAX_LINES; i++) {
            int bx = this.x;
            int by = this.y + i * ROW + 1;
            boolean hover = this.overBox(i, mouseX, mouseY);
            graphics.fill(bx, by, bx + BOX, by + BOX, hover ? 0xFF4FB4D0 : FRAME);
            graphics.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, 0xFF0A1015);
            if (this.done[i]) {
                // 一个小对勾
                graphics.fill(bx + 2, by + 5, bx + 4, by + 7, CHECK);
                graphics.fill(bx + 3, by + 6, bx + 5, by + 8, CHECK);
                graphics.fill(bx + 5, by + 4, bx + 7, by + 6, CHECK);
                graphics.fill(bx + 6, by + 2, bx + 8, by + 4, CHECK);
            }
        }
    }

    private boolean overBox(int row, double mouseX, double mouseY) {
        int by = this.y + row * ROW + 1;
        return mouseX >= this.x && mouseX < this.x + BOX && mouseY >= by && mouseY < by + BOX;
    }

    /** 点了勾选框就打勾或取消。 */
    boolean click(double mouseX, double mouseY) {
        for (int i = 0; i < Memo.MAX_LINES; i++) {
            if (this.overBox(i, mouseX, mouseY)) {
                this.done[i] = !this.done[i];
                if (i < this.boxes.size()) {
                    this.boxes.get(i).setTextColor(this.done[i] ? DONE : TEXT);
                }
                return true;
            }
        }
        return false;
    }

    boolean typing() {
        return this.boxes.stream().anyMatch(EditBox::isFocused);
    }

    Memo memo() {
        List<Memo.Line> lines = new ArrayList<>();
        for (int i = 0; i < Memo.MAX_LINES; i++) {
            lines.add(new Memo.Line(this.texts[i], this.done[i]));
        }
        return Memo.clean(lines);
    }
}
