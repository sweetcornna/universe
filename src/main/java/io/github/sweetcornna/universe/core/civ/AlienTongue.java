package io.github.sweetcornna.universe.core.civ;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 一个种族的语言（ADR-0027 §二）：每个概念一个外星写法，按派生键 {@code language/<种族>} 用这个种族的音节表拼，
 * 同一个世界里固定不变。NPC 的名字也用这套音节。
 *
 * <p>三套音节读起来各不一样：商旅族是短的开音节（辅音加元音），学者族是辅音连缀，戍卫族是长元音和复合元音。
 */
public final class AlienTongue {
    /** 一个音节的拼写和音译。 */
    private record Syllable(String en, String zh) {
    }

    private static final List<Syllable> TRADER_SYLLABLES = syllables(
            "ka", "卡", "ta", "塔", "mi", "米", "lo", "洛", "pa", "帕", "nu", "努", "sa", "萨", "ri", "瑞",
            "fe", "费", "do", "多", "bi", "比", "ma", "玛", "zo", "佐", "ve", "维", "ku", "库", "ne", "内",
            "lu", "露", "ti", "提", "ga", "加", "po", "珀");
    private static final List<Syllable> SCHOLAR_SYLLABLES = syllables(
            "thra", "瑟拉", "skel", "斯凯尔", "vrin", "弗林", "drex", "德雷克斯", "kyth", "基斯", "stro", "斯特罗",
            "plix", "普利克斯", "zhul", "朱尔", "grem", "格雷姆", "kvor", "克沃尔", "phyl", "菲尔", "brask", "布拉斯克",
            "tsen", "岑", "klor", "克洛尔", "vyrn", "维恩", "shtal", "什塔尔", "dral", "德拉尔", "xyst", "希斯特",
            "krith", "克里斯", "sporn", "斯波恩");
    private static final List<Syllable> WARDEN_SYLLABLES = syllables(
            "aar", "阿尔", "oom", "乌姆", "eel", "伊尔", "vaal", "瓦尔", "dou", "斗", "rau", "劳", "kai", "凯",
            "moor", "穆尔", "sei", "塞", "thoo", "图", "gaal", "加尔", "uun", "乌恩", "hei", "海", "noor", "努尔",
            "lao", "老", "zeel", "泽尔", "bau", "包", "ruu", "鲁", "fai", "法伊", "koo", "库");

    private final Race race;
    private final String[] words = new String[Lexicon.SIZE];

    private AlienTongue(Race race, long seed) {
        this.race = race;
        Rng rng = Rng.of(seed);
        Set<String> used = new HashSet<>();
        for (int concept = 0; concept < Lexicon.SIZE; concept++) {
            String word;
            do {
                word = this.spell(rng, 1, 2).en();
            } while (!used.add(word));
            this.words[concept] = word;
        }
    }

    public static AlienTongue of(long galaxySeed, Race race) {
        return new AlienTongue(race, Seeds.derive(galaxySeed, "language/" + race.id()));
    }

    public Race race() {
        return this.race;
    }

    /** 概念的外星写法（小写拉丁字母，两种语言里都这么显示）。 */
    public String word(int concept) {
        return this.words[concept];
    }

    /** NPC 的名字（双语：中文是音译）。 */
    public NameGenerator.Name name(long seed) {
        NameGenerator.Name name = this.spell(Rng.of(Seeds.derive(seed, "name")), 2, 2);
        String en = name.en();
        return new NameGenerator.Name(Character.toUpperCase(en.charAt(0)) + en.substring(1), name.zh());
    }

    /** 拼一个词：商旅族的音节短，多拼一个。 */
    private NameGenerator.Name spell(Rng rng, int min, int max) {
        List<Syllable> table = switch (this.race) {
            case TRADER -> TRADER_SYLLABLES;
            case SCHOLAR -> SCHOLAR_SYLLABLES;
            case WARDEN -> WARDEN_SYLLABLES;
        };
        int extra = this.race == Race.TRADER ? 1 : 0;
        int count = rng.range(min + extra, max + extra);
        StringBuilder en = new StringBuilder();
        StringBuilder zh = new StringBuilder();
        Syllable previous = null;
        for (int i = 0; i < count; i++) {
            Syllable syllable;
            do {
                syllable = rng.pick(table);
            } while (syllable == previous);
            previous = syllable;
            en.append(syllable.en());
            zh.append(syllable.zh());
        }
        return new NameGenerator.Name(en.toString(), zh.toString());
    }

    private static List<Syllable> syllables(String... pairs) {
        Syllable[] table = new Syllable[pairs.length / 2];
        for (int i = 0; i < table.length; i++) {
            table[i] = new Syllable(pairs[2 * i], pairs[2 * i + 1]);
        }
        return List.of(table);
    }
}
