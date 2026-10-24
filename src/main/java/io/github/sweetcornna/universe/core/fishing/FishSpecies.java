package io.github.sweetcornna.universe.core.fishing;

import java.util.Locale;

/**
 * 一种鱼（ADR-0034 §一）。物品是 {@code universe:fish_<id>}，图鉴按 {@link #index} 排。
 *
 * @param index  在 {@link FishTable#ALL} 里的位置（同步给客户端用；表只能往后加）
 * @param id     注册名的后半截
 * @param time   只在白天、只在夜里，还是不限
 * @param weather 只在风暴里，还是不限
 * @param depth  只在浅水、只在深水，还是不限
 */
public record FishSpecies(int index, String id, String nameZh, String nameEn, Habitat habitat, Rarity rarity, SizeClass size, Time time,
        Weather weather, Depth depth) {
    /** 什么时候咬钩。 */
    public enum Time {
        ANY,
        DAY,
        NIGHT;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 什么天气咬钩。 */
    public enum Weather {
        ANY,
        STORM;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 水多深才咬钩：浅水是浮标往下不超过 {@value #SHALLOW_MAX} 格，深水是至少 {@value #DEEP_MIN} 格。 */
    public enum Depth {
        ANY,
        SHALLOW,
        DEEP;

        public static final int SHALLOW_MAX = 4;
        public static final int DEEP_MIN = 8;

        public boolean matches(int depth) {
            return switch (this) {
                case ANY -> true;
                case SHALLOW -> depth <= SHALLOW_MAX;
                case DEEP -> depth >= DEEP_MIN;
            };
        }

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** 物品的注册名。 */
    public String itemId() {
        return "fish_" + this.id;
    }

    /** 加成后的力气（0–1）：个头定大头，档次再加一点。 */
    public double strength() {
        return Math.min(1, this.size.strength() + this.rarity.fight());
    }

    /** 张力一直在绿区时收上来要几刻。 */
    public int reelTicks() {
        return (int) Math.round(this.size.reelTicks() * this.rarity.reelFactor());
    }

    /** 卖多少信用点：按个头和档次。 */
    public double price() {
        return this.size.price() * this.rarity.price();
    }

    /** 有没有时间、天气、水深的条件（图鉴里写出来）。 */
    public boolean conditional() {
        return this.time != Time.ANY || this.weather != Weather.ANY || this.depth != Depth.ANY;
    }
}
