package io.github.sweetcornna.universe.darkfog;

/**
 * 黑雾的单位和建筑（ADR-0028）。炮塔、干扰塔按这个接口找目标：优先打正在打设施的，聚爆炮只打地面的。
 */
public interface DarkFogEnemy {
    /** 黑雾等级，0–30。 */
    int darkFogLevel();

    /** 属于哪处基地（{@code DarkFogData} 里的编号）；不属于任何基地是 -1。 */
    int baseId();

    /** 正在打玩家的设施。 */
    boolean attackingFacility();

    /** 地面目标：建筑和贴地悬浮的突袭者是，飞着的游骑兵、守卫者不是。 */
    boolean grounded();

    /** 高空目标：从太空下来轰炸星球的枪骑（ADR-0028 §八）。炮塔里只有导弹炮塔、等离子炮塔打得到。 */
    default boolean highAltitude() {
        return false;
    }

    /** 干扰塔减速：{@code ticks} 刻内移动和攻击变慢到 {@code factor} 倍（0–1）。不动的建筑不理。 */
    default void jam(int ticks, double factor) {
    }
}
