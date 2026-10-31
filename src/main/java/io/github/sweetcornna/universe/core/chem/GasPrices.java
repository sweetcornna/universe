package io.github.sweetcornna.universe.core.chem;

import java.util.Map;

/**
 * 大气提取器取的气体的基准价（ADR-0036 §三），并进 {@code RawValues}。氢、氧以前就有价钱；氯气、一氧化碳只从电解来
 * （一氧化碳在大气里都不到千分之一，取不了），价钱顺着配方推。
 *
 * <p>大宗的氮和二氧化碳，一台提取器一分钟出三四十个，和水一个价；二氧化硫、甲烷、氨、氩只占千分之几到百分之几，
 * 一分钟只出几个，贵三四倍；氦只在少数奇异星球上有，最贵。
 */
public final class GasPrices {
    public static final Map<String, Double> VALUES = Map.of(
            "universe:nitrogen", 0.5,
            "universe:carbon_dioxide", 0.5,
            "universe:sulfur_dioxide", 1.5,
            "universe:methane", 2.0,
            "universe:ammonia", 2.0,
            "universe:argon", 2.0,
            "universe:helium", 4.0);

    private GasPrices() {
    }
}
