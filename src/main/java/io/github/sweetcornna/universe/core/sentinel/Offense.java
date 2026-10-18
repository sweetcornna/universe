package io.github.sweetcornna.universe.core.sentinel;

/**
 * 会提高警戒值的行为（ADR-0014），数值再乘上星球守卫强度的倍数。
 *
 * <p>攻击守卫机是"挑衅"：不管原来的警戒值是多少，直接到一级通缉。
 */
public enum Offense {
    /** 用采矿激光挖普通方块。 */
    LASER_MINING(0.3, false),
    /** 用采矿激光挖矿石。 */
    LASER_MINING_ORE(2.0, false),
    /** 捕杀星球上的动物。 */
    KILL_CREATURE(12, false),
    /** 攻击守卫机。 */
    ATTACK_SENTINEL(5, true),
    /** 击毁守卫机。 */
    DESTROY_SENTINEL(15, true);

    private final double alert;
    private final boolean provokes;

    Offense(double alert, boolean provokes) {
        this.alert = alert;
        this.provokes = provokes;
    }

    public double alert() {
        return this.alert;
    }

    public boolean provokes() {
        return this.provokes;
    }
}
