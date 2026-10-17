package io.github.sweetcornna.universe.core.ecology;

/**
 * 动物的体型模板（玩法设计 §8）。每个物种在模板上再定比例、部件和配色。
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
    FLYER("飞兽", "Glider", true);

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
}
