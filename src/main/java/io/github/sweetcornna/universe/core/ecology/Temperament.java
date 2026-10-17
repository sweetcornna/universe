package io.github.sweetcornna.universe.core.ecology;

/** 动物的性情：决定见到玩家时怎么反应（玩法设计 §8）。 */
public enum Temperament {
    /** 温顺：自顾自吃草，挨打才逃。 */
    DOCILE("温顺"),
    /** 易受惊：玩家靠近就跑。 */
    SKITTISH("易受惊"),
    /** 有领地意识：不主动攻击，挨打就还手。 */
    TERRITORIAL("有领地意识"),
    /** 捕食者：主动猎杀玩家和温顺的动物。 */
    PREDATOR("捕食者");

    private final String nameZh;

    Temperament(String nameZh) {
        this.nameZh = nameZh;
    }

    public String nameZh() {
        return this.nameZh;
    }

    /** 会攻击（主动或者被激怒后）。 */
    public boolean fights() {
        return this == TERRITORIAL || this == PREDATOR;
    }
}
