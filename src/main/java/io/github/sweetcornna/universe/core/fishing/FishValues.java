package io.github.sweetcornna.universe.core.fishing;

import java.util.HashMap;
import java.util.Map;

/**
 * 钓上来的东西的价钱（ADR-0034 §一），并进 {@code RawValues}。鱼按个头和档次：普通的小鱼 20 信用点，传说的巨鱼 6000。
 * 漂流瓶和补给舱按开出来的东西的平均值定。
 */
public final class FishValues {
    public static final double MESSAGE_BOTTLE = 40;
    public static final double SEALED_CACHE = 150;

    private FishValues() {
    }

    public static Map<String, Double> prices() {
        Map<String, Double> prices = new HashMap<>();
        for (FishSpecies species : FishTable.ALL) {
            prices.put("universe:" + species.itemId(), species.price());
        }
        for (Junk junk : Junk.values()) {
            prices.put("universe:" + junk.id(), junk.price());
        }
        prices.put("universe:message_bottle", MESSAGE_BOTTLE);
        prices.put("universe:sealed_cache", SEALED_CACHE);
        // 配方里用的鱼的标签：按最便宜的一种算（哪种都能放，玩家会放最便宜的）
        prices.put("#universe:fish", cheapest(null));
        for (Rarity rarity : Rarity.values()) {
            prices.put("#universe:fish/" + rarity.key(), cheapest(rarity));
        }
        return prices;
    }

    private static double cheapest(Rarity rarity) {
        return FishTable.ALL.stream().filter(species -> rarity == null || species.rarity() == rarity).mapToDouble(FishSpecies::price).min()
                .orElseThrow();
    }
}
