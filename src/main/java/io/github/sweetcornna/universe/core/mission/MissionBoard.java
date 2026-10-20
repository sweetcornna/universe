package io.github.sweetcornna.universe.core.mission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;
import io.github.sweetcornna.universe.core.trade.Economy;
import io.github.sweetcornna.universe.core.trade.Goods;
import io.github.sweetcornna.universe.core.trade.TradeCategory;

/**
 * 空间站的任务板（ADR-0017）：每座空间站每天三个任务，由恒星系的种子和游戏日派生。一次只能接一个，回发任务的空间站交付。
 *
 * <p>报酬：送货按货物基准价的 1.6 倍（比卖给空间站多赚一半上下）；清剿每架 220、采矿每颗 70、测绘每种 150。
 * 富裕的恒星系多给两成五，贫穷的少给一成五。四分之一的任务附带 1–3 份导航数据，一成多附带一个技术模块。
 */
public final class MissionBoard {
    public static final int OFFERS = 3;
    public static final long DAY = 24_000;
    /** 送货：货物的总基准价在这个范围里，件数 2–64。 */
    static final int DELIVERY_MIN_VALUE = 300;
    static final int DELIVERY_MAX_VALUE = 900;
    static final int DELIVERY_MAX_COUNT = 64;
    static final double DELIVERY_PREMIUM = 1.6;
    /** 送货只挑这个价钱范围里的东西：太便宜的是泥土石头，太贵的一件就够了。 */
    static final double WANTED_MIN = 4;
    static final double WANTED_MAX = 250;
    static final long BOUNTY_EACH = 220;
    static final long MINING_EACH = 70;
    static final long SURVEY_EACH = 150;
    static final double NAVIGATION_CHANCE = 0.25;
    static final double MODULE_CHANCE = 0.12;

    private MissionBoard() {
    }

    /** 游戏刻是第几天。 */
    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, DAY);
    }

    /**
     * 这一天的任务。三格的种类各不相同；没有能送的货时不出送货，恒星系里没有动植物时不出测绘。
     *
     * @param systemSeed 恒星系的种子
     * @param catalog    能交易的东西（送货从里面挑空间站缺的）
     * @param species    恒星系里能分析的动植物种数
     */
    public static List<Mission> offers(long systemSeed, int system, Economy economy, long day, List<Goods> catalog, int species) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(systemSeed, "missions"), day));
        List<Goods> wanted = wanted(economy, catalog);
        List<MissionKind> pool = new ArrayList<>();
        List<Mission> missions = new ArrayList<>();
        for (int slot = 0; slot < OFFERS; slot++) {
            if (pool.isEmpty()) {
                for (MissionKind kind : MissionKind.values()) {
                    if ((kind != MissionKind.DELIVERY || !wanted.isEmpty()) && (kind != MissionKind.SURVEY || species > 0)) {
                        pool.add(kind);
                    }
                }
            }
            MissionKind kind = pool.remove(rng.nextInt(pool.size()));
            missions.add(mission(rng, system, day, slot, kind, economy.wealth(), wanted, species));
        }
        return missions;
    }

    /** 空间站缺的东西：经济类型缺的大类里、价钱合适的（贸易经济什么都要一点），飞船部件和模块不算。按物品 ID 排序。 */
    static List<Goods> wanted(Economy economy, List<Goods> catalog) {
        boolean any = economy.type() == Economy.Type.TRADING;
        List<Goods> wanted = new ArrayList<>();
        for (Goods goods : catalog) {
            if (goods.staple() || goods.category() == TradeCategory.TECH || goods.value() < WANTED_MIN || goods.value() > WANTED_MAX) {
                continue;
            }
            if (any || economy.type().demands(goods.category())) {
                wanted.add(goods);
            }
        }
        wanted.sort(Comparator.comparing(Goods::item));
        return wanted;
    }

    private static Mission mission(Rng rng, int system, long day, int slot, MissionKind kind, Economy.Wealth wealth, List<Goods> wanted,
            int species) {
        double factor = factor(wealth);
        String item = "";
        int count;
        double reward;
        switch (kind) {
            case DELIVERY -> {
                Goods goods = rng.pick(wanted);
                item = goods.item();
                double total = rng.range(DELIVERY_MIN_VALUE, DELIVERY_MAX_VALUE);
                count = (int) Math.max(2, Math.min(DELIVERY_MAX_COUNT, Math.round(total / goods.value())));
                reward = goods.value() * count * DELIVERY_PREMIUM;
            }
            case BOUNTY -> {
                count = rng.range(2, 5);
                reward = count * BOUNTY_EACH;
            }
            case MINING -> {
                count = rng.range(4, 10);
                reward = count * MINING_EACH;
            }
            default -> {
                count = Math.min(species, rng.range(2, 5));
                reward = count * SURVEY_EACH;
            }
        }
        double roll = rng.nextDouble();
        Mission.Bonus bonus = roll < NAVIGATION_CHANCE ? Mission.Bonus.NAVIGATION_DATA
                : roll < NAVIGATION_CHANCE + MODULE_CHANCE ? Mission.Bonus.MODULE : Mission.Bonus.NONE;
        int bonusCount = switch (bonus) {
            case NAVIGATION_DATA -> rng.range(1, 3);
            case MODULE -> 1;
            case NONE -> 0;
        };
        return new Mission(system, day, slot, kind, item, count, roundTen(reward * factor), bonus, bonusCount);
    }

    /** 报酬的倍数：贫穷 0.85、中等 1、富裕 1.25。 */
    static double factor(Economy.Wealth wealth) {
        return switch (wealth) {
            case POOR -> 0.85;
            case AVERAGE -> 1.0;
            case WEALTHY -> 1.25;
        };
    }

    private static long roundTen(double value) {
        return Math.max(10, Math.round(value / 10) * 10);
    }

    /**
     * 任务进度。清剿和采矿看接任务以后统计涨了多少，测绘看分析过几种；送货在交付时数背包，这里不算。
     *
     * @param statNow  现在的统计（击落的海盗、打碎的小行星）
     * @param baseline 接任务时的统计
     * @param analyzed 接任务以后分析过的种数
     */
    public static int progress(Mission mission, long statNow, long baseline, int analyzed) {
        long done = switch (mission.kind()) {
            case BOUNTY, MINING -> statNow - baseline;
            case SURVEY -> analyzed;
            case DELIVERY -> 0;
        };
        return (int) Math.max(0, Math.min(mission.count(), done));
    }
}
