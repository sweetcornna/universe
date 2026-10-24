package io.github.sweetcornna.universe.core.fishing;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 咬钩的是什么（ADR-0034 §一）：钓鱼器钓上来的鱼 87%、杂物 10%、漂流瓶和密封补给舱 2.5%、技术模块 0.5%；鱼按档次的权重挑，
 * 再按时间、天气、水深和鱼饵加减。没有一种鱼对得上条件时钓上来的是杂物。
 */
public final class CatchRoll {
    /** 放生以后少见以上的概率 +15%。 */
    public static final double RELEASE_BONUS = 1.15;
    /** 水越深越偏重：到这么深偏得最多。 */
    private static final int HEAVY_DEPTH = 24;

    /** 现在的情形：白天还是夜里、有没有风暴、浮标往下几格水。 */
    public record Situation(boolean day, boolean storm, int depth) {
    }

    /**
     * 各样东西出现的概率，和鱼的档次上限。
     *
     * @param specialBoost 少见以上的鱼的权重再乘几倍（鱼塘的档次）
     */
    public record Odds(double junk, double bottle, double cache, double module, Rarity maxRarity, double specialBoost) {
        /** 钓鱼器。 */
        public static final Odds ROD = new Odds(0.10, 0.015, 0.01, 0.005, Rarity.LEGENDARY, 1);
        /** 捕鱼笼：只出普通和少见，杂物 15%。 */
        public static final Odds TRAP = new Odds(0.15, 0, 0, 0, Rarity.UNCOMMON, 1);

        /** 聚落的鱼塘（ADR-0034 §二），档次 0–3（C 到 S）：档次越高杂物越少、好鱼越多，A 档起出稀有，S 档出传说。 */
        public static Odds pond(int grade) {
            int g = Math.clamp(grade, 0, 3);
            Rarity max = g >= 3 ? Rarity.LEGENDARY : g >= 2 ? Rarity.RARE : Rarity.UNCOMMON;
            return new Odds(0.12 - 0.03 * g, 0, 0, 0, max, 1 + 0.5 * g);
        }
    }

    private CatchRoll() {
    }

    /**
     * 这种鱼现在咬钩的权重；条件对不上（或者档次超了上限）是 0。
     *
     * @param released 刚放生过（接下来 3 次甩竿）
     */
    public static double weight(FishSpecies species, Situation situation, Bait bait, boolean released, Odds odds) {
        Rarity rarity = species.rarity();
        if (rarity.ordinal() > odds.maxRarity().ordinal() || !species.depth().matches(situation.depth())) {
            return 0;
        }
        double w = rarity.weight();
        switch (species.time()) {
            case DAY -> {
                if (!situation.day()) {
                    return 0;
                }
            }
            case NIGHT -> {
                if (situation.day()) {
                    if (bait != Bait.GLOW) {
                        return 0;
                    }
                    w *= 0.5;
                } else if (bait == Bait.GLOW) {
                    w *= 2;
                }
            }
            case ANY -> {
            }
        }
        if (species.weather() == FishSpecies.Weather.STORM && !situation.storm()) {
            if (bait != Bait.STATIC) {
                return 0;
            }
            w /= 3;
        }
        if (rarity.special()) {
            w *= odds.specialBoost();
            if (bait == Bait.DISH) {
                w *= Bait.DISH_BONUS;
            }
            if (released) {
                w *= RELEASE_BONUS;
            }
        }
        if (bait == Bait.SPINNER && (rarity == Rarity.UNCOMMON || rarity == Rarity.RARE)) {
            w *= 2;
        }
        if (bait == Bait.SINKER && species.size().big()) {
            w *= 2;
        }
        if (bait == Bait.STAR_CORE && rarity == Rarity.LEGENDARY) {
            w *= 4;
        }
        return w;
    }

    /** 池子里现在会咬钩的鱼。 */
    public static List<FishSpecies> biting(List<FishSpecies> pool, Situation situation, Bait bait, Odds odds) {
        List<FishSpecies> list = new ArrayList<>();
        for (FishSpecies species : pool) {
            if (weight(species, situation, bait, false, odds) > 0) {
                list.add(species);
            }
        }
        return list;
    }

    /** 挑一样：先按概率分是鱼、杂物还是别的，是鱼再按权重挑一种、随一个重量。 */
    public static Catch roll(List<FishSpecies> pool, Situation situation, Bait bait, boolean released, Odds odds, Rng rng) {
        double r = rng.nextDouble();
        if (r < odds.module()) {
            return Catch.of(Catch.Kind.MODULE);
        }
        r -= odds.module();
        if (r < odds.bottle()) {
            return Catch.of(Catch.Kind.BOTTLE);
        }
        r -= odds.bottle();
        if (r < odds.cache()) {
            return Catch.of(Catch.Kind.CACHE);
        }
        r -= odds.cache();
        if (r < odds.junk()) {
            return Catch.junk(junk(rng));
        }
        return fish(pool, situation, bait, released, odds, rng);
    }

    /** 只挑鱼；没有对得上的就是杂物。 */
    public static Catch fish(List<FishSpecies> pool, Situation situation, Bait bait, boolean released, Odds odds, Rng rng) {
        double total = 0;
        for (FishSpecies species : pool) {
            total += weight(species, situation, bait, released, odds);
        }
        if (total <= 0) {
            return Catch.junk(junk(rng));
        }
        double pick = rng.nextDouble() * total;
        FishSpecies chosen = null;
        for (FishSpecies species : pool) {
            double w = weight(species, situation, bait, released, odds);
            if (w <= 0) {
                continue;
            }
            chosen = species;
            pick -= w;
            if (pick < 0) {
                break;
            }
        }
        return Catch.fish(chosen, kilograms(chosen, situation.depth(), bait, rng));
    }

    /**
     * 一条鱼多重：在个头的范围里按对数均匀偏轻的那头随机，水越深、用了深水坠饵越偏重。小的保留两位小数，大的一位。
     */
    public static double kilograms(FishSpecies species, int depth, Bait bait, Rng rng) {
        double exponent = 2.0 - Math.min(Math.max(depth, 0), HEAVY_DEPTH) / (double) HEAVY_DEPTH;
        if (bait == Bait.SINKER) {
            exponent *= 0.6;
        }
        double t = Math.pow(rng.nextDouble(), exponent);
        SizeClass size = species.size();
        double kg = size.min() * Math.pow(size.max() / size.min(), t);
        return kg >= 10 ? Math.round(kg * 10) / 10.0 : Math.round(kg * 100) / 100.0;
    }

    private static Junk junk(Rng rng) {
        double r = rng.nextDouble();
        return r < 0.4 ? Junk.TANGLED_WEED : r < 0.75 ? Junk.RUSTED_PARTS : Junk.BROKEN_CANISTER;
    }
}
