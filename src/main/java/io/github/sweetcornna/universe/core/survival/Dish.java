package io.github.sweetcornna.universe.core.survival;

/**
 * 营养处理器做的东西（ADR-0026）：半成品和菜。半成品不能吃（{@link #nutrition} 为 0），菜吃了按原版回饥饿和饱和度，
 * 有的另有效果。物品按这里的顺序注册，只往后加。
 *
 * @param id 物品注册名
 * @param nutrition 回几点饥饿；0 表示不能吃
 * @param saturation 饱和度系数（原版的写法：回的饱和度 = 饥饿 × 系数 × 2）
 * @param effect 额外效果
 * @param amount 效果的数值：氧气、防护补几点，保暖持续几秒
 */
public enum Dish {
    // 半成品
    FLOUR("flour", "面粉", "Flour", 0, 0, Effect.NONE, 0),
    DOUGH("dough", "面团", "Dough", 0, 0, Effect.NONE, 0),
    BUTTER("butter", "黄油", "Butter", 0, 0, Effect.NONE, 0),
    PASTRY("pastry", "酥皮", "Pastry", 0, 0, Effect.NONE, 0),
    CAKE_BASE("cake_base", "蛋糕坯", "Cake Base", 0, 0, Effect.NONE, 0),
    // 一样原料做的
    STAR_JAM("star_jam", "星莓果酱", "Star Jam", 3, 0.3F, Effect.NONE, 0),
    SOLAR_SAUCE("solar_sauce", "日焰辣酱", "Solar Hot Sauce", 2, 0.2F, Effect.WARMTH, 60),
    MOULD_PASTE("mould_paste", "发酵霉酱", "Fermented Mould Paste", 2, 0.4F, Effect.NONE, 0),
    CACTUS_JUICE("cactus_juice", "仙人掌汁", "Cactus Juice", 2, 0.2F, Effect.OXYGEN, 20),
    FROST_ICE_CREAM("frost_ice_cream", "霜晶冰淇淋", "Frost Ice Cream", 4, 0.3F, Effect.NONE, 0),
    ROOT_SOUP("root_soup", "根茎汤", "Root Soup", 6, 0.6F, Effect.WARMTH, 120),
    ROASTED_MARROW("roasted_marrow", "烤髓球", "Roasted Marrow", 5, 0.6F, Effect.NONE, 0),
    GRAVITINO_JELLY("gravitino_jelly", "引力果冻", "Gravitino Jelly", 4, 0.5F, Effect.NONE, 0),
    CUSTARD("custard", "卡士达", "Custard", 5, 0.6F, Effect.HAZARD, 25),
    FLUFF_CANDY("fluff_candy", "绒毛糖", "Fluff Candy", 2, 0.1F, Effect.NONE, 0),
    // 组合菜
    STAR_PIE("star_pie", "星莓馅饼", "Star Pie", 8, 0.6F, Effect.NONE, 0),
    MEAT_PIE("meat_pie", "肉馅饼", "Meat Pie", 9, 0.8F, Effect.NONE, 0),
    SPICY_ROAST("spicy_roast", "辣烤肉", "Spicy Roast", 10, 0.8F, Effect.WARMTH, 180),
    BRAISED_MEAT("braised_meat", "酱烧肉", "Braised Meat", 10, 0.9F, Effect.NONE, 0),
    ALIEN_CAKE("alien_cake", "外星蛋糕", "Alien Cake", 12, 0.8F, Effect.HAZARD, 25),
    STAR_CUSTARD("star_custard", "星莓卡士达", "Star Custard", 8, 0.7F, Effect.HAZARD, 25),
    EGG_TART("egg_tart", "蛋挞", "Egg Tart", 8, 0.7F, Effect.HAZARD, 25),
    FROST_TART("frost_tart", "霜晶挞", "Frost Tart", 8, 0.6F, Effect.NONE, 0),
    SOLAR_PIE("solar_pie", "日焰派", "Solar Pie", 12, 0.9F, Effect.WARMTH, 240),
    CACTUS_JELLY("cactus_jelly", "仙人掌冻", "Cactus Jelly", 4, 0.4F, Effect.OXYGEN, 20),
    GRAVITINO_PASTRY("gravitino_pastry", "引力果酥", "Gravitino Pastry", 10, 0.8F, Effect.NONE, 0),
    MARROW_SOUP("marrow_soup", "髓球浓汤", "Marrow Soup", 10, 0.8F, Effect.NONE, 0),
    FISH_PIE("fish_pie", "鱼派", "Fish Pie", 10, 0.8F, Effect.NONE, 0),
    STAR_CAKE("star_cake", "星莓蛋糕", "Star Cake", 14, 0.9F, Effect.HAZARD, 25);

    /** 吃了以后的额外效果。 */
    public enum Effect {
        NONE,
        /** 补氧气。 */
        OXYGEN,
        /** 补每一项危害防护。 */
        HAZARD,
        /** 保暖：一段时间里寒冷防护掉得慢一半。 */
        WARMTH
    }

    private final String id;
    private final String nameZh;
    private final String nameEn;
    private final int nutrition;
    private final float saturation;
    private final Effect effect;
    private final int amount;

    Dish(String id, String nameZh, String nameEn, int nutrition, float saturation, Effect effect, int amount) {
        this.id = id;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.nutrition = nutrition;
        this.saturation = saturation;
        this.effect = effect;
        this.amount = amount;
    }

    public String id() {
        return this.id;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public int nutrition() {
        return this.nutrition;
    }

    public float saturation() {
        return this.saturation;
    }

    public Effect effect() {
        return this.effect;
    }

    public int amount() {
        return this.amount;
    }

    /** 能直接吃。 */
    public boolean edible() {
        return this.nutrition > 0;
    }
}
