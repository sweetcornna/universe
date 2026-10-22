package io.github.sweetcornna.universe.core.civ;

import java.util.List;

/**
 * 外星语言的词表（ADR-0027 §二）：64 个概念，三个种族一样，意思写在语言文件里（{@code civ.universe.word.<id>}），
 * 外星的写法按种族各拼一套（{@link AlienTongue}）。前 8 个是数学和物理的词，一开始就认识。只往后加，不改顺序：
 * 玩家认识的词按序号存成掩码。
 */
public final class Lexicon {
    public static final List<String> CONCEPTS = List.of(
            // 一开始就认识（阿雷西博信息的思路：数和元素哪个文明都推得出来）
            "one", "two", "three", "hydrogen", "carbon", "oxygen", "star", "light",
            "trade", "price", "gift", "friend", "enemy", "danger", "help", "ship",
            "planet", "home", "water", "food", "metal", "machine", "knowledge", "discovery",
            "ancient", "ruin", "sentinel", "weapon", "strength", "honor", "fear", "peace",
            "war", "travel", "path", "sky", "ground", "life", "death", "time",
            "old", "new", "good", "bad", "yes", "no", "give", "take",
            "want", "see", "speak", "learn", "build", "break", "hunt", "guard",
            "wealth", "credits", "data", "signal", "fuel", "storm", "stone", "creature");
    public static final int SIZE = CONCEPTS.size();
    /** 一开始就认识的词数。 */
    public static final int INNATE = 8;
    public static final long INNATE_MASK = (1L << INNATE) - 1;

    private Lexicon() {
    }

    /** 概念的序号；没有这个词时为 -1。 */
    public static int index(String id) {
        return CONCEPTS.indexOf(id);
    }

    public static String id(int concept) {
        return CONCEPTS.get(concept);
    }
}
