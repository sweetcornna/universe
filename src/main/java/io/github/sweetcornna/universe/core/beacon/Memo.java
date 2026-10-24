package io.github.sweetcornna.universe.core.beacon;

import java.util.ArrayList;
import java.util.List;

/**
 * 一页备忘（ADR-0033 §六）：几行字，每行可以打勾。全息信标、星球、恒星系的备忘都是这个格式，存在世界里，所有玩家共用。
 */
public record Memo(List<Line> lines) {
    public static final int MAX_LINES = 8;
    public static final int MAX_CHARS = 48;
    public static final Memo EMPTY = new Memo(List.of());

    /** 一行：字和勾。 */
    public record Line(String text, boolean done) {
        public Line {
            text = text == null ? "" : text;
        }
    }

    public Memo {
        lines = List.copyOf(lines);
    }

    /**
     * 客户端发来的、或者旧存档里的行整理一遍：去掉控制字符和首尾空白，每行截到 {@link #MAX_CHARS} 个字，空行去掉，
     * 最多 {@link #MAX_LINES} 行。
     */
    public static Memo clean(List<Line> raw) {
        List<Line> lines = new ArrayList<>();
        for (Line line : raw) {
            if (lines.size() == MAX_LINES) {
                break;
            }
            String text = cleanText(line.text(), MAX_CHARS);
            if (!text.isEmpty()) {
                lines.add(new Line(text, line.done()));
            }
        }
        return lines.isEmpty() ? EMPTY : new Memo(lines);
    }

    /** 去掉控制字符（换行、制表符）和格式代码 §、首尾空白，截到 max 个字（按码点算，不切开表情）。 */
    public static String cleanText(String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        raw.codePoints().filter(c -> !Character.isISOControl(c) && c != 0xA7).forEach(builder::appendCodePoint);
        String text = builder.toString().strip();
        if (text.codePointCount(0, text.length()) > max) {
            text = text.substring(0, text.offsetByCodePoints(0, max)).strip();
        }
        return text;
    }

    /** 第 index 行打勾或取消。 */
    public Memo toggle(int index) {
        if (index < 0 || index >= this.lines.size()) {
            return this;
        }
        List<Line> copy = new ArrayList<>(this.lines);
        Line line = copy.get(index);
        copy.set(index, new Line(line.text(), !line.done()));
        return new Memo(copy);
    }

    /** 打了勾的行数。 */
    public int done() {
        return (int) this.lines.stream().filter(Line::done).count();
    }

    public int size() {
        return this.lines.size();
    }

    public boolean isEmpty() {
        return this.lines.isEmpty();
    }
}
