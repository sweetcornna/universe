package io.github.sweetcornna.universe.client;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.text.LineBreaker;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;

/**
 * 本模组界面里的自动换行，代替原版的 {@code font.split} 和 {@code textWithWordWrap}。原版只在空格处断行，中文里夹一个空格
 * 就会提前断开；断行规则见 {@link LineBreaker}。样式（颜色、粗体）按原样保留。
 */
public final class TextWrap {
    private TextWrap() {
    }

    public static List<FormattedCharSequence> split(Font font, FormattedText text, int width) {
        IntArrayList codepoints = new IntArrayList();
        List<Style> styles = new ArrayList<>();
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (position, style, codepoint) -> {
            codepoints.add(codepoint);
            styles.add(style);
            return true;
        });
        int[] text32 = codepoints.toIntArray();
        float[] widths = new float[text32.length];
        StringSplitter splitter = font.getSplitter();
        for (int i = 0; i < text32.length; i++) {
            widths[i] = text32[i] == '\n' ? 0 : splitter.stringWidth(FormattedCharSequence.codepoint(text32[i], styles.get(i)));
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (LineBreaker.Line line : LineBreaker.wrap(text32, widths, Math.max(width, 1))) {
            List<FormattedText> parts = new ArrayList<>();
            int i = line.start();
            while (i < line.end()) {
                Style style = styles.get(i);
                StringBuilder run = new StringBuilder();
                while (i < line.end() && styles.get(i).equals(style)) {
                    run.appendCodePoint(text32[i]);
                    i++;
                }
                parts.add(FormattedText.of(run.toString(), style));
            }
            lines.add(Language.getInstance().getVisualOrder(FormattedText.composite(parts)));
        }
        return lines;
    }

    /** 画换好行的文字，返回最后一行下面的 y。 */
    public static int draw(GuiGraphicsExtractor graphics, Font font, FormattedText text, int x, int y, int width, int color, boolean shadow) {
        for (FormattedCharSequence line : split(font, text, width)) {
            graphics.text(font, line, x, y, color, shadow);
            y += font.lineHeight;
        }
        return y;
    }

    public static int draw(GuiGraphicsExtractor graphics, Font font, FormattedText text, int x, int y, int width, int color) {
        return draw(graphics, font, text, x, y, width, color, true);
    }
}
