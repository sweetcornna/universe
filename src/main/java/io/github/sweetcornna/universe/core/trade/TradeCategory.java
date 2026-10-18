package io.github.sweetcornna.universe.core.trade;

/**
 * 交易品的大类：恒星系的经济类型按大类决定哪些东西收得贵、哪些卖得便宜（ADR-0017）。
 *
 * <p>物品属于哪一类由物品标签 {@code universe:trade/<类>} 决定（数据生成写出），没打标签的按价钱归到原料、零件或高级品。
 */
public enum TradeCategory {
    /** 矿石、原木、沙石这类直接采来的。 */
    RAW,
    /** 锭、玻璃、石墨这类冶炼出来的。 */
    REFINED,
    /** 电路板、齿轮、电机这类零件。 */
    COMPONENT,
    /** 处理器、量子芯片、框架材料这类高级品。 */
    ADVANCED,
    /** 燃料、氢、蓄电器、矩阵这类能量和研究用品。 */
    ENERGY,
    /** 动植物的产物。 */
    BIO,
    /** 飞船部件、技术模块。 */
    TECH;

    /** 没有打标签时：按价钱估一个大类。 */
    public static TradeCategory byValue(double value) {
        if (value < 8) {
            return RAW;
        }
        if (value < 40) {
            return REFINED;
        }
        return value < 400 ? COMPONENT : ADVANCED;
    }

    public String key() {
        return this.name().toLowerCase(java.util.Locale.ROOT);
    }
}
