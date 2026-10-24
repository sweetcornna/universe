package io.github.sweetcornna.universe.core.deepspace;

import java.util.Map;

/**
 * 深空打捞物的基准价（ADR-0034 §四），并进 {@code RawValues}。废金属两块回炉成一块铁，比铁矿略贵；合金板、电子元件
 * 比钛合金、电路板便宜一截（拆下来的旧货）；反应堆芯一艘残骸只有一个，拆的时候要冒熔毁的险，和头骨化石一个价。
 */
public final class DeepSpacePrices {
    public static final Map<String, Double> VALUES = Map.of(
            "universe:scrap_metal", 5.0,
            "universe:alloy_plate", 30.0,
            "universe:salvaged_electronics", 45.0,
            "universe:salvaged_reactor_core", 2500.0);

    private DeepSpacePrices() {
    }
}
