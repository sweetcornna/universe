package io.github.sweetcornna.universe.core.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 能交易的一样东西：基准价、大类，空间站会不会一直卖它、要多富的恒星系才卖（ADR-0017）。
 *
 * @param staple    每座空间站都卖（空间翘曲器、燃料、基础的飞船部件）
 * @param minWealth 至少多富的恒星系才卖（高档的飞船部件）
 */
public record Goods(String item, double value, TradeCategory category, boolean staple, Economy.Wealth minWealth) {
    /** 每座空间站卖几样本地特产。 */
    static final int LOCAL_GOODS = 6;

    public static Goods of(String item, double value, TradeCategory category) {
        return new Goods(item, value, category, false, Economy.Wealth.POOR);
    }

    /** 存货上限：便宜的多存，贵的少存，越富存得越多。 */
    public int capacity(Economy.Wealth wealth) {
        double base = 600 / Math.sqrt(Math.max(this.value, 0.5));
        return (int) Math.max(1, Math.min(250, Math.round(base * wealth.stockFactor())));
    }

    /**
     * 一座空间站卖什么：所有够得上财富的常备品，加上从恒星系产的大类里按种子挑的几样（不含飞船部件、模块这些常备的）；
     * 贸易经济什么都不偏，从所有大类里挑。结果按物品 ID 排序，同一个种子、同一份清单总是一样。
     */
    public static List<Market.Listing> stock(long seed, Economy economy, List<Goods> catalog, long now) {
        List<Goods> sorted = new ArrayList<>(catalog);
        sorted.sort(Comparator.comparing(Goods::item));
        List<Goods> chosen = new ArrayList<>();
        List<Goods> local = new ArrayList<>();
        boolean any = economy.type().supplied().isEmpty();
        for (Goods goods : sorted) {
            if (goods.staple()) {
                if (economy.wealth().ordinal() >= goods.minWealth().ordinal()) {
                    chosen.add(goods);
                }
            } else if (any || economy.type().supplies(goods.category())) {
                local.add(goods);
            }
        }
        Rng rng = Rng.of(seed);
        for (int i = 0; i < LOCAL_GOODS && !local.isEmpty(); i++) {
            chosen.add(local.remove(rng.nextInt(local.size())));
        }
        chosen.sort(Comparator.comparing(Goods::item));
        List<Market.Listing> listings = new ArrayList<>();
        for (Goods goods : chosen) {
            int capacity = goods.capacity(economy.wealth());
            listings.add(new Market.Listing(goods.item(), goods.value(), goods.category(), capacity, capacity, now));
        }
        return listings;
    }
}
