package io.github.sweetcornna.universe.core.survival;

import java.util.Map;

import io.github.sweetcornna.universe.core.tech.Grade;
import io.github.sweetcornna.universe.core.tech.Stat;

/**
 * 多功能工具的类型（ADR-0026 §六）：每种有一项专长，按档次给加成。只能往后加，存档里按名字存。
 *
 * <table>
 * <tr><th>类型</th><th>专长</th><th>哪里来</th></tr>
 * <tr><td>手枪</td><td>采矿速度</td><td>工具柜</td></tr>
 * <tr><td>步枪</td><td>武器伤害</td><td>工具柜</td></tr>
 * <tr><td>实验型</td><td>扫描范围</td><td>工具柜</td></tr>
 * <tr><td>异星型</td><td>伤害和扫描范围，各一半</td><td>工具柜，少见</td></tr>
 * <tr><td>守卫机型</td><td>武器伤害，加成更高</td><td>守卫塔</td></tr>
 * </table>
 */
public enum MultitoolClass {
    PISTOL("pistol", 35),
    RIFLE("rifle", 30),
    EXPERIMENTAL("experimental", 25),
    ALIEN("alien", 10),
    SENTINEL("sentinel", 0);

    /** 专长加成：C、B、A、S。 */
    private static final double[] BONUS = {0, 0.10, 0.20, 0.35};
    /** 守卫机型的专长加成更高。 */
    private static final double[] SENTINEL_BONUS = {0.15, 0.30, 0.45, 0.60};

    private final String key;
    private final double weight;

    MultitoolClass(String key, double weight) {
        this.key = key;
        this.weight = weight;
    }

    /** 语言键和贴图名的后缀。 */
    public String key() {
        return this.key;
    }

    /** 工具柜里挂出来的权重；守卫机型不卖，是 0。 */
    public double weight() {
        return this.weight;
    }

    /** 这个档次的专长加成。 */
    public double bonus(Grade grade) {
        return (this == SENTINEL ? SENTINEL_BONUS : BONUS)[grade.ordinal()];
    }

    /** 专长落在哪几项属性上、各多少；加成是 0 时是空的。 */
    public Map<Stat, Double> specialty(Grade grade) {
        double bonus = this.bonus(grade);
        if (bonus <= 0) {
            return Map.of();
        }
        return switch (this) {
            case PISTOL -> Map.of(Stat.MINING, bonus);
            case RIFLE, SENTINEL -> Map.of(Stat.DAMAGE, bonus);
            case EXPERIMENTAL -> Map.of(Stat.SCAN_RADIUS, bonus);
            case ALIEN -> Map.of(Stat.DAMAGE, bonus / 2, Stat.SCAN_RADIUS, bonus / 2);
        };
    }
}
