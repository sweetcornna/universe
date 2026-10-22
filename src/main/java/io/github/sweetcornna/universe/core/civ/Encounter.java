package io.github.sweetcornna.universe.core.civ;

import java.util.List;

/**
 * 一道对话题目（ADR-0027 §四）：一段外星话（嵌几个词）、一段旁白、三个选项，三个选项分别对上利、知、勇。
 * 文字在语言文件里：{@code civ.universe.encounter.<id>.scene}（旁白）、{@code .option.<value>}（选项），都是占位。
 *
 * @param id      题目 id
 * @param words   外星话里依次出现的概念序号
 * @param options 显示顺序下三个选项各对上什么
 */
public record Encounter(String id, List<Integer> words, List<Value> options) {
    /** 选中的这一项对这个种族算不算对。 */
    public boolean correct(Race race, int option) {
        return this.options.get(option) == race.value();
    }

    /** 这段话里所有词的掩码（选对时从里面猜一个新词）。 */
    public long wordMask() {
        long mask = 0;
        for (int concept : this.words) {
            mask |= 1L << concept;
        }
        return mask;
    }
}
