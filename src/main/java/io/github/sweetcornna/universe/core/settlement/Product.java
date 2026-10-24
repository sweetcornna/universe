package io.github.sweetcornna.universe.core.settlement;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.civ.Race;

/**
 * 聚落能选的产品（ADR-0034 §二）：按种族和已有的建筑开放。每天按生产力攒"份"进储备，取的时候按份换成物品，卖掉时每份值
 * {@link #unitValue} 信用点。按名字存档，只往后加。
 *
 * @param race     哪个种族的聚落能选（null 是不限种族）
 * @param building 要有哪种建筑才能选（null 是不要）
 * @param item     取出来是什么；空串是直接给信用点
 * @param units    几份换一批
 * @param items    一批几个（信用点是一批多少信用点）
 * @param unitValue 卖掉时一份多少信用点
 */
public enum Product {
    /** 商旅族：直接出信用点。 */
    CREDITS(Race.TRADER, null, "", 1, 100, 80),
    /** 商旅族：贸易品（导航数据换来的星图）。 */
    TRADE_MAPS(Race.TRADER, null, "universe:navigation_data", 2, 1, 40),
    /** 学者族：勘测数据。 */
    SURVEY_DATA(Race.SCHOLAR, null, "universe:survey_data", 1, 1, 35),
    /** 学者族：导航数据。 */
    NAV_DATA(Race.SCHOLAR, null, "universe:navigation_data", 1, 1, 28),
    /** 戍卫族：马格南弹箱。 */
    AMMO(Race.WARDEN, null, "universe:magnum_ammo_box", 1, 1, 30),
    /** 戍卫族：钢材。 */
    STEEL(Race.WARDEN, null, "universe:steel", 1, 2, 24),
    /** 农场：熟肉。 */
    FOOD(null, BuildingType.FARM, "universe:cooked_alien_meat", 1, 3, 20),
    /** 工坊：齿轮。 */
    GEARS(null, BuildingType.WORKSHOP, "universe:gear", 1, 2, 22),
    /** 工坊：电路板。 */
    CIRCUITS(null, BuildingType.WORKSHOP, "universe:circuit_board", 1, 1, 30);

    private final @Nullable Race race;
    private final @Nullable BuildingType building;
    private final String item;
    private final int units;
    private final int items;
    private final int unitValue;

    Product(@Nullable Race race, @Nullable BuildingType building, String item, int units, int items, int unitValue) {
        this.race = race;
        this.building = building;
        this.item = item;
        this.units = units;
        this.items = items;
        this.unitValue = unitValue;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static Product byId(String id, Product fallback) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public @Nullable Race race() {
        return this.race;
    }

    public @Nullable BuildingType building() {
        return this.building;
    }

    /** 取出来的物品 ID；空串是信用点。 */
    public String item() {
        return this.item;
    }

    public boolean credits() {
        return this.item.isEmpty();
    }

    public int units() {
        return this.units;
    }

    public int items() {
        return this.items;
    }

    public int unitValue() {
        return this.unitValue;
    }

    /** 这个种族的聚落默认出什么。 */
    public static Product defaultFor(Race race) {
        return switch (race) {
            case TRADER -> CREDITS;
            case SCHOLAR -> SURVEY_DATA;
            case WARDEN -> STEEL;
        };
    }
}
