package io.github.sweetcornna.universe.core.chem;

/**
 * 一样物品是什么做的（ADR-0036 §一）：矿物、化工品、气体的名字和化学式，物品说明和分析目镜里显示。
 *
 * @param item    物品的注册名，带命名空间（{@code minecraft:raw_copper}、{@code universe:hydrogen}）
 * @param nameZh  矿物或物质的名字（"钛铁矿"），和物品名一样时界面上可以只写化学式
 * @param nameEn  英文名
 * @param formula 化学式，下标用 Unicode 的下标数字；混合物写主要成分，用" + "连起来；现实里没有的东西写"?"
 * @param kind    纯物质、混合物，还是现实里没有的
 */
public record Substance(String item, String nameZh, String nameEn, String formula, Kind kind) {
    /** 现实里没有的东西写的化学式，和教育版物质还原器的做法一样。 */
    public static final String UNKNOWN_FORMULA = "?";

    public enum Kind {
        /** 一种物质，有确定的化学式（合金、矿物的理想式也算）。 */
        PURE,
        /** 几种东西混在一起，化学式写的是主要成分。 */
        MIXTURE,
        /** 现实里没有：戴森球计划、无人深空里的虚构物质。 */
        UNKNOWN
    }

    public boolean known() {
        return this.kind != Kind.UNKNOWN;
    }
}
