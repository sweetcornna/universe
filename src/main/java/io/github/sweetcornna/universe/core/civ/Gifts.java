package io.github.sweetcornna.universe.core.civ;

/** 礼物（ADR-0027 §四）：适配层把手里的东西归到一类，这里定每个种族喜欢哪几类。 */
public final class Gifts {
    /** 东西的大类。 */
    public enum Kind {
        /** 贸易品、信用芯片这类值钱的东西。 */
        VALUABLES,
        /** 导航数据、勘探数据。 */
        DATA,
        /** 化石、遗物、活珍珠。 */
        RELIC,
        /** 技术模块、电路板这类技术品。 */
        TECH,
        /** 守卫机残骸、伺服器、主脑。 */
        SENTINEL_PART,
        /** 武器模块。 */
        WEAPON,
        /** 吃的。 */
        FOOD,
        OTHER
    }

    private Gifts() {
    }

    /** 商旅族喜欢值钱的和吃的，学者族喜欢数据、遗物和技术品，戍卫族喜欢守卫机零件和武器。 */
    public static boolean liked(Race race, Kind kind) {
        return switch (race) {
            case TRADER -> kind == Kind.VALUABLES || kind == Kind.FOOD;
            case SCHOLAR -> kind == Kind.DATA || kind == Kind.RELIC || kind == Kind.TECH;
            case WARDEN -> kind == Kind.SENTINEL_PART || kind == Kind.WEAPON;
        };
    }
}
