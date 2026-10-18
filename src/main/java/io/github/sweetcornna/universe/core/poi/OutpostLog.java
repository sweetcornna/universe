package io.github.sweetcornna.universe.core.poi;

import io.github.sweetcornna.universe.core.procgen.NameGenerator;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 废弃据点数据终端里的日志（ADR-0014）：从语言文件里的几组片段按种子各挑一段，配上据点编号、天数和两个人名，
 * 每个据点读起来不一样。服务端和客户端用同一个种子算出同一份，客户端按自己的语言显示（人名也有中英两种写法）。
 *
 * @param code     据点编号，比如 K-417
 * @param day      记录写在驻扎后的第几天
 * @param author   写日志的人
 * @param crewmate 日志里提到的同伴
 */
public record OutpostLog(String code, int day, NameGenerator.Name author, NameGenerator.Name crewmate,
        int opening, int situation, int event, int ending) {
    public static final int OPENINGS = 4;
    public static final int SITUATIONS = 8;
    public static final int EVENTS = 8;
    public static final int ENDINGS = 6;
    private static final String LETTERS = "ABCDEFGHJKLMNPRSTVXZ";

    public static OutpostLog of(long seed) {
        Rng rng = Rng.of(Seeds.derive(seed, "outpost_log"));
        String code = LETTERS.charAt(rng.nextInt(LETTERS.length())) + "-" + rng.range(100, 999);
        int day = rng.range(12, 640);
        NameGenerator.Name author = NameGenerator.generate(rng, 2, 3);
        NameGenerator.Name crewmate = NameGenerator.generate(rng, 2, 3);
        return new OutpostLog(code, day, author, crewmate, rng.nextInt(OPENINGS), rng.nextInt(SITUATIONS),
                rng.nextInt(EVENTS), rng.nextInt(ENDINGS));
    }
}
