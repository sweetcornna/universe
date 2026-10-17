package io.github.sweetcornna.universe.core.procgen;

import java.util.List;

/**
 * 程序生成的天体、物种名称，中英文各一份。
 *
 * <p>名字先生成成音节（声母 + 韵母，结尾可能带一个辅音），英文按拼写拼起来，中文按音译表逐个音节换成汉字，
 * 所以两种语言的名字读音大致对应，例如 Kalor ↔ 卡洛尔。
 */
public final class NameGenerator {
    /** 声母，空串表示没有声母。 */
    private static final List<String> ONSETS = List.of(
            "", "b", "d", "f", "g", "h", "k", "l", "m", "n", "p", "r", "s", "t", "v", "z", "th", "sh", "qu", "y");
    private static final String[] VOWELS = {"a", "e", "i", "o", "u"};
    /** 音译表：每行对应 {@link #ONSETS} 里的一个声母，五列依次是 a、e、i、o、u。 */
    private static final String[][] SYLLABLE_ZH = {
            {"阿", "埃", "伊", "奥", "乌"},
            {"巴", "贝", "比", "波", "布"},
            {"达", "德", "迪", "多", "杜"},
            {"法", "费", "菲", "佛", "福"},
            {"加", "格", "吉", "戈", "古"},
            {"哈", "赫", "希", "霍", "胡"},
            {"卡", "凯", "基", "科", "库"},
            {"菈", "莱", "莉", "洛", "露"},
            {"玛", "梅", "米", "莫", "穆"},
            {"纳", "内", "尼", "诺", "努"},
            {"帕", "佩", "皮", "珀", "普"},
            {"拉", "雷", "瑞", "罗", "鲁"},
            {"萨", "塞", "西", "索", "苏"},
            {"塔", "特", "提", "托", "图"},
            {"瓦", "维", "薇", "沃", "武"},
            {"扎", "泽", "齐", "佐", "祖"},
            {"莎", "瑟", "希", "索", "苏"},
            {"夏", "谢", "希", "肖", "舒"},
            {"夸", "奎", "奎", "阔", "库"},
            {"雅", "耶", "伊", "尤", "宇"},
    };
    private static final String[] CODAS = {"r", "s", "n", "l", "k", "x"};
    private static final String[] CODAS_ZH = {"尔", "斯", "恩", "尔", "克", "克斯"};

    private NameGenerator() {
    }

    /** 一个名字的两种语言版本。 */
    public record Name(String en, String zh) {
    }

    /**
     * 生成一个名字。
     *
     * @param minSyllables 最少音节数（不含结尾辅音）
     * @param maxSyllables 最多音节数
     */
    public static Name generate(Rng rng, int minSyllables, int maxSyllables) {
        int syllables = rng.range(minSyllables, maxSyllables);
        StringBuilder en = new StringBuilder();
        StringBuilder zh = new StringBuilder();
        int previousOnset = -1;
        for (int i = 0; i < syllables; i++) {
            int onset;
            do {
                // 第一个音节以后尽量不用无声母的音节，免得出现 "aeo" 这样一串元音
                onset = (i > 0 && rng.chance(0.85)) ? 1 + rng.nextInt(ONSETS.size() - 1) : rng.nextInt(ONSETS.size());
            } while (onset == previousOnset);
            previousOnset = onset;
            int vowel = rng.nextInt(VOWELS.length);
            en.append(ONSETS.get(onset));
            // 词中的 i 偶尔写成 y，拼写更多样，读音不变
            en.append(vowel == 2 && i > 0 && i < syllables - 1 && rng.chance(0.3) ? "y" : VOWELS[vowel]);
            zh.append(SYLLABLE_ZH[onset][vowel]);
        }
        if (rng.chance(0.35)) {
            int coda = rng.nextInt(CODAS.length);
            en.append(CODAS[coda]);
            zh.append(CODAS_ZH[coda]);
        }
        en.setCharAt(0, Character.toUpperCase(en.charAt(0)));
        return new Name(en.toString(), zh.toString());
    }
}
