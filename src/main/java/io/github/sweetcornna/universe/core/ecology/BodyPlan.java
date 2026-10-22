package io.github.sweetcornna.universe.core.ecology;

/**
 * 动物的体型模板（玩法设计 §8）。每个物种在模板上再定比例、部件和配色。后四种住在水里（ADR-0026），只往后加。
 */
public enum BodyPlan {
    /** 四足：食草的兽群、独行的猎手。 */
    QUADRUPED("四足兽", "Quadruped", false),
    /** 六足：像昆虫的甲壳动物。 */
    HEXAPOD("六足虫", "Hexapod", false),
    /** 两足：长腿长尾的奔走者。 */
    BIPED("两足兽", "Strider", false),
    /** 多节的爬行者，有的有一排短腿。 */
    SERPENT("节蠕虫", "Crawler", false),
    /** 有翼、一直在飞。 */
    FLYER("飞兽", "Glider", true),
    /** 鱼：成群地游。 */
    FISH("鱼", "Fish", false),
    /** 鳗：长身子，住在深水里，会咬人。 */
    EEL("鳗", "Eel", false),
    /** 水母：一口钟、一圈触手，碰到会蜇人。 */
    JELLY("水母", "Jelly", false),
    /** 乌贼：长套膜、一束触手，少数长成巨型。 */
    SQUID("乌贼", "Squid", false);

    private final String nameZh;
    private final String nameEn;
    private final boolean flying;

    BodyPlan(String nameZh, String nameEn, boolean flying) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.flying = flying;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public boolean flying() {
        return this.flying;
    }

    /** 住在水里。 */
    public boolean aquatic() {
        return this.ordinal() >= FISH.ordinal();
    }
}
