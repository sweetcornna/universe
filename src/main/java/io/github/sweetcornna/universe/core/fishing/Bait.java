package io.github.sweetcornna.universe.core.fishing;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * 鱼饵（ADR-0034 §一）。工具的饵格里放一样，每钩住一条用掉一个。能吃的东西都能当饵，做好的菜更好；另有六种专门的饵，
 * 在营养处理器里做。
 *
 * @param id         专门的饵的物品注册名；能吃的东西、菜、没有饵是 null
 * @param waitFactor 等鱼的时间是原来的几倍
 */
public enum Bait {
    NONE(null, "", "", 1.0),
    /** 能吃的东西：等鱼的时间 ×0.8。 */
    FOOD(null, "", "", 0.8),
    /** 做好的菜（营养处理器里出来的能吃的）：×0.6，少见以上的概率 +10%。 */
    DISH(null, "", "", 0.6),
    /** 腥饵：鱼肉和异星肉剁碎发酵，等鱼的时间减半。 */
    STINK("stink_bait", "腥饵", "Stink Bait", 0.5),
    /** 亮片拟饵：少见、稀有的概率翻倍。 */
    SPINNER("spinner_lure", "亮片拟饵", "Spinner Lure", 1.0),
    /** 深水坠饵：大的、巨的概率翻倍，重量偏重。 */
    SINKER("deep_sinker", "深水坠饵", "Deep Sinker", 1.0),
    /** 夜光饵：夜里的鱼白天也会咬（概率减半），夜里翻倍。 */
    GLOW("glow_bait", "夜光饵", "Glow Bait", 1.0),
    /** 静电拟饵：风暴里的鱼不下风暴也会咬（概率三分之一）。 */
    STATIC("static_lure", "静电拟饵", "Static Lure", 1.0),
    /** 星核饵：传说的概率 ×4。 */
    STAR_CORE("star_core_bait", "星核饵", "Star Core Bait", 1.0);

    /** 少见以上的概率：用做好的菜当饵 +10%。 */
    public static final double DISH_BONUS = 1.1;

    private final @Nullable String id;
    private final String nameZh;
    private final String nameEn;
    private final double waitFactor;

    Bait(@Nullable String id, String nameZh, String nameEn, double waitFactor) {
        this.id = id;
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.waitFactor = waitFactor;
    }

    public @Nullable String id() {
        return this.id;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public double waitFactor() {
        return this.waitFactor;
    }

    /** 六种专门的饵之一。 */
    public boolean special() {
        return this.id != null;
    }

    /** 六种专门的饵，按做出来的顺序。 */
    public static List<Bait> specials() {
        return List.of(STINK, SPINNER, SINKER, GLOW, STATIC, STAR_CORE);
    }
}
