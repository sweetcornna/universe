package io.github.sweetcornna.universe.core.mission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

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
 *
 * <p>公会使者（ADR-0027 §六）每天另给两个，只出这个公会管的种类，多了两种任务板上没有的：守卫机每架 160，
 * 地标 350 加上离着陆点每格 0.5。报酬再乘玩家在这个公会的档。
 */
public final class MissionBoard {
    public static final int OFFERS = 3;
    public static final long DAY = 24_000;
    /** 使者给的任务从这一格起，和任务板的错开（{@link Mission#id()} 不重）。 */
    public static final int ENVOY_SLOT = 10;
    public static final int ENVOY_OFFERS = 2;
    /** 任务板只出这几种，顺序和原来一样，老世界的任务板不变。 */
    private static final List<MissionKind> BOARD_KINDS = List.of(MissionKind.DELIVERY, MissionKind.BOUNTY, MissionKind.MINING, MissionKind.SURVEY);
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
    static final long SENTINEL_EACH = 160;
    static final long LANDMARK_BASE = 350;
    static final double LANDMARK_PER_BLOCK = 0.5;
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
                for (MissionKind kind : BOARD_KINDS) {
                    if (available(kind, wanted, species, List.of())) {
                        pool.add(kind);
                    }
                }
            }
            MissionKind kind = pool.remove(rng.nextInt(pool.size()));
            missions.add(mission(rng, system, day, slot, kind, economy.wealth(), wanted, species, 1, List.of()));
        }
        return missions;
    }

    /**
     * 公会使者今天给的任务（ADR-0027 §六）：两个，从这个公会管的种类里挑，一样一个；有一样出不了（没货可送、没有动植物、
     * 找不到地标）就两个都是另一样。种类、目标、件数对谁都一样，报酬再乘 {@code rewardFactor}（玩家在这个公会的档）。
     *
     * @param kinds        这个公会管的任务种类
     * @param salt         区分公会的派生键（公会的 id）
     * @param rewardFactor 报酬的倍数
     * @param targets      地标任务能去的地方，顺序固定；只有要出地标任务时才算
     */
    public static List<Mission> envoyOffers(long systemSeed, int system, Economy economy, long day, List<Goods> catalog, int species,
            List<MissionKind> kinds, String salt, double rewardFactor, Supplier<List<LandmarkTarget>> targets) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(systemSeed, "missions/envoy/" + salt), day));
        List<Goods> wanted = wanted(economy, catalog);
        List<LandmarkTarget> places = kinds.contains(MissionKind.LANDMARK) ? targets.get() : List.of();
        List<MissionKind> usable = new ArrayList<>();
        for (MissionKind kind : kinds) {
            if (available(kind, wanted, species, places)) {
                usable.add(kind);
            }
        }
        if (usable.isEmpty()) {
            return List.of();
        }
        int first = rng.nextInt(usable.size());
        List<Mission> missions = new ArrayList<>();
        for (int i = 0; i < ENVOY_OFFERS; i++) {
            MissionKind kind = usable.get((first + i) % usable.size());
            missions.add(mission(rng, system, day, ENVOY_SLOT + i, kind, economy.wealth(), wanted, species, rewardFactor, places));
        }
        return missions;
    }

    /** 这种任务今天出不出得了。 */
    private static boolean available(MissionKind kind, List<Goods> wanted, int species, List<LandmarkTarget> places) {
        return switch (kind) {
            case DELIVERY -> !wanted.isEmpty();
            case SURVEY -> species > 0;
            case LANDMARK -> !places.isEmpty();
            default -> true;
        };
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
            int species, double extra, List<LandmarkTarget> places) {
        double factor = factor(wealth) * extra;
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
            case SURVEY -> {
                count = Math.min(species, rng.range(2, 5));
                reward = count * SURVEY_EACH;
            }
            case SENTINELS -> {
                count = rng.range(3, 8);
                reward = count * SENTINEL_EACH;
            }
            case LANDMARK -> {
                LandmarkTarget target = rng.pick(places);
                item = target.encode();
                count = 1;
                reward = LANDMARK_BASE + target.distance() * LANDMARK_PER_BLOCK;
            }
            default -> throw new IllegalArgumentException("unknown mission kind " + kind);
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
     * 任务进度。清剿、采矿、守卫机看接任务以后统计涨了多少，测绘看分析过几种，地标看到过没有；送货在交付时数背包，这里不算。
     *
     * @param statNow  现在的统计（击落的海盗、打碎的小行星、打掉的守卫机）
     * @param baseline 接任务时的统计
     * @param analyzed 接任务以后分析过的种数；地标任务到过是 1
     */
    public static int progress(Mission mission, long statNow, long baseline, int analyzed) {
        long done = switch (mission.kind()) {
            case BOUNTY, MINING, SENTINELS -> statNow - baseline;
            case SURVEY, LANDMARK -> analyzed;
            case DELIVERY -> 0;
        };
        return (int) Math.max(0, Math.min(mission.count(), done));
    }
}
