package io.github.sweetcornna.universe.core.survival;

/**
 * 玩家身上穿了哪几件外骨骼。头盔加胸甲才算密封（有生命维持）；危害防护按件数算，缺件消耗更快。
 */
public record Suit(boolean helmet, boolean chest, boolean legs, boolean boots) {
    public static final Suit NONE = new Suit(false, false, false, false);
    public static final Suit FULL = new Suit(true, true, true, true);

    public boolean sealed() {
        return this.helmet && this.chest;
    }

    public int pieces() {
        return (this.helmet ? 1 : 0) + (this.chest ? 1 : 0) + (this.legs ? 1 : 0) + (this.boots ? 1 : 0);
    }

    /** 防护的消耗倍数：整套 1，三件 2，两件 3.5，一件 6，不穿 10（半分钟）。 */
    public double hazardFactor() {
        return switch (this.pieces()) {
            case 4 -> 1;
            case 3 -> 2;
            case 2 -> 3.5;
            case 1 -> 6;
            default -> 10;
        };
    }
}
