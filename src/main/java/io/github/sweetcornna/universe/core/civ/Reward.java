package io.github.sweetcornna.universe.core.civ;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 对话选对给的奖励（ADR-0027 §四）：商旅族给信用点、贸易品，学者族给导航数据、勘探数据、偶尔一个技术模块，
 * 戍卫族给守卫机残骸、电磁线圈、偶尔一个武器模块。适配层把种类换成物品。
 */
public record Reward(Kind kind, int amount) {
    public enum Kind {
        CREDITS,
        TRADE_GOODS,
        NAVIGATION_DATA,
        SURVEY_DATA,
        TECH_MODULE,
        SENTINEL_SCRAP,
        MAGNETIC_COIL,
        WEAPON_MODULE
    }

    /** 技术模块、武器模块的机会。 */
    static final double MODULE_CHANCE = 0.10;

    /** 按种族和种子（NPC 加日期）抽一份。 */
    public static Reward forAnswer(Race race, long seed) {
        Rng rng = Rng.of(seed);
        return switch (race) {
            // 信用点 200 到 600，取整到 10
            case TRADER -> rng.chance(0.6) ? new Reward(Kind.CREDITS, 10 * rng.range(20, 60)) : new Reward(Kind.TRADE_GOODS, rng.range(1, 2));
            case SCHOLAR -> {
                if (rng.chance(MODULE_CHANCE)) {
                    yield new Reward(Kind.TECH_MODULE, 1);
                }
                yield rng.chance(0.5) ? new Reward(Kind.NAVIGATION_DATA, rng.range(1, 3)) : new Reward(Kind.SURVEY_DATA, rng.range(1, 2));
            }
            case WARDEN -> {
                if (rng.chance(MODULE_CHANCE)) {
                    yield new Reward(Kind.WEAPON_MODULE, 1);
                }
                yield rng.chance(0.5) ? new Reward(Kind.SENTINEL_SCRAP, rng.range(2, 4)) : new Reward(Kind.MAGNETIC_COIL, rng.range(1, 2));
            }
        };
    }
}
