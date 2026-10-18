package io.github.sweetcornna.universe.core.tech;

/**
 * 技术模块的品级（ADR-0014）：C、B、A、S，越往后数值越高、越难得到。
 */
public enum Grade {
    C(0xD8DEE2),
    B(0x5BD47A),
    A(0x5AA9F0),
    S(0xF2B84B);

    private final int color;

    Grade(int color) {
        this.color = color;
    }

    /** 物品图标和名字的颜色（RGB）。 */
    public int color() {
        return this.color;
    }

    public String letter() {
        return this.name();
    }
}
