package io.github.sweetcornna.universe.core.text;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动换行。原版只在空格处断行，中文句子里只要有一个空格（"Y 键打开……"、"最多 10 层"），整行就会在那个空格提前断开。
 * 这里汉字、假名、全角标点前后都能断，并照中文排版的习惯避头尾：逗号、句号、右括号这类标点不放在行首，左括号、
 * 左引号不放在行尾。一行里找不到能断的地方就硬断，和原版一样。
 */
public final class LineBreaker {
    /** 不能放在行首的字符。 */
    private static final String NO_START = "，。、；：！？）」』】》〉〕｝’”…·%‰°,.;:!?)]}";
    /** 不能放在行尾的字符。 */
    private static final String NO_END = "（「『【《〈〔｛‘“([{";

    private LineBreaker() {
    }

    /** 一行在原文里的范围，按码点数，含头不含尾；行尾的空格不算在里面。 */
    public record Line(int start, int end) {
    }

    /**
     * 按宽度断行。
     *
     * @param text 码点
     * @param widths 每个码点的宽度
     * @param maxWidth 一行最宽多少
     * @return 每一行的范围；换行符另起一行，空段落是一个空行
     */
    public static List<Line> wrap(int[] text, float[] widths, float maxWidth) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (true) {
            int i = start;
            float width = 0;
            int lastBreak = -1;
            while (i < text.length && text[i] != '\n') {
                if (i > start && canBreakBefore(text[i - 1], text[i])) {
                    lastBreak = i;
                }
                if (i > start && width + widths[i] > maxWidth) {
                    break;
                }
                width += widths[i];
                i++;
            }
            if (i >= text.length || text[i] == '\n') {
                lines.add(new Line(start, trimEnd(text, start, i)));
                if (i >= text.length) {
                    return lines;
                }
                start = i + 1;
                continue;
            }
            int at = lastBreak > start ? lastBreak : i;
            lines.add(new Line(start, trimEnd(text, start, at)));
            start = at;
            while (start < text.length && text[start] == ' ') {
                start++;
            }
        }
    }

    /** prev 和 cur 之间能不能断行。 */
    static boolean canBreakBefore(int prev, int cur) {
        if (cur == ' ' || NO_START.indexOf(cur) >= 0 || NO_END.indexOf(prev) >= 0) {
            return false;
        }
        if (prev == ' ') {
            return true;
        }
        if (prev == cur && (cur == '—' || cur == '…')) {
            return false;
        }
        return cjk(prev) || cjk(cur);
    }

    private static boolean cjk(int codepoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codepoint);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL || script == Character.UnicodeScript.BOPOMOFO) {
            return true;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codepoint);
        return block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS || block == Character.UnicodeBlock.VERTICAL_FORMS
                || codepoint == '“' || codepoint == '”' || codepoint == '‘' || codepoint == '’' || codepoint == '…' || codepoint == '—';
    }

    private static int trimEnd(int[] text, int start, int end) {
        while (end > start && text[end - 1] == ' ') {
            end--;
        }
        return end;
    }
}
