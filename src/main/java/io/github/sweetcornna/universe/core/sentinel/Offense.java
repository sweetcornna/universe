package io.github.sweetcornna.universe.core.sentinel;

/**
 * 会提高警戒值的行为（ADR-0014），数值再乘上星球守卫强度的倍数。
 *
 * <p>攻击守卫机是"挑衅"：不管原来的警戒值是多少，直接到一级通缉。摘引力果（ADR-0026）更严重，直接三级。
 */
public enum Offense {
    /** 用采矿激光挖普通方块。 */
    LASER_MINING(0.3, 0),
    /** 用采矿激光挖矿石。 */
    LASER_MINING_ORE(2.0, 0),
    /** 捕杀星球上的动物。 */
    KILL_CREATURE(12, 0),
    /** 攻击守卫机。 */
    ATTACK_SENTINEL(5, 1),
    /** 击毁守卫机。 */
    DESTROY_SENTINEL(15, 1),
    /** 在守卫机眼前收资源植物（ADR-0026）。 */
    HARVEST(0.5, 0),
    /** 摘引力果（ADR-0026）。 */
    GRAVITINO(10, 3);

    private final double alert;
    private final int jumpTo;

    /** @param jumpTo 不管原来的警戒值，直接到几级通缉；0 表示只加警戒值 */
    Offense(double alert, int jumpTo) {
        this.alert = alert;
        this.jumpTo = jumpTo;
    }

    public double alert() {
        return this.alert;
    }

    public boolean provokes() {
        return this.jumpTo > 0;
    }

    /** 直接到几级通缉；0 表示不跳级。 */
    public int jumpTo() {
        return this.jumpTo;
    }
}
