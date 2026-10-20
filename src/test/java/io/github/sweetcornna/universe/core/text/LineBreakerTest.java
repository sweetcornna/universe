package io.github.sweetcornna.universe.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class LineBreakerTest {
    /** 每个字宽 1，汉字宽 2（和游戏里差不多是这个比例）。 */
    private static List<String> wrap(String text, float maxWidth) {
        int[] codepoints = text.codePoints().toArray();
        float[] widths = new float[codepoints.length];
        for (int i = 0; i < codepoints.length; i++) {
            widths[i] = codepoints[i] > 0x2E80 ? 2 : 1;
        }
        return LineBreaker.wrap(codepoints, widths, maxWidth).stream()
                .map(line -> new String(codepoints, line.start(), line.end() - line.start()))
                .toList();
    }

    @Test
    void englishBreaksAtSpaces() {
        assertEquals(List.of("the quick", "brown fox"), wrap("the quick brown fox", 10));
    }

    @Test
    void spaceInChineseDoesNotEndTheLineEarly() {
        // 原版会排成 "Y" / "键打开戴森球编辑器"
        assertEquals(List.of("Y 键打开戴森", "球编辑器"), wrap("Y 键打开戴森球编辑器", 12));
        assertEquals(List.of("戴森壳：最多 10", "层，每层选网格"), wrap("戴森壳：最多 10 层，每层选网格", 16));
    }

    @Test
    void punctuationNeverStartsALine() {
        // 一行放五个字，句号不能放行首，带着前一个字一起换行
        assertEquals(List.of("一二三四", "五。"), wrap("一二三四五。", 10));
        // 左括号不能放行尾
        assertEquals(List.of("一二三", "（四）"), wrap("一二三（四）", 8));
    }

    @Test
    void newlinesAndBlankLinesAreKept() {
        assertEquals(List.of("ab", "", "cd"), wrap("ab\n\ncd", 10));
        assertEquals(List.of(""), wrap("", 10));
    }

    @Test
    void spacesAtTheBreakAreDropped() {
        assertEquals(List.of("aaa", "bbb"), wrap("aaa   bbb", 4));
        assertEquals(List.of("  indented"), wrap("  indented", 20));
    }

    @Test
    void longWordIsCutWhenThereIsNoOtherWay() {
        assertEquals(List.of("abcd", "efgh", "ij"), wrap("abcdefghij", 4));
    }

    @Test
    void numbersAndUnitsStayTogether() {
        assertEquals(List.of("纬度：±30°"), wrap("纬度：±30°", 11));
        assertEquals(List.of("纬度：", "±30°"), wrap("纬度：±30°", 9));
    }
}
