package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;

/**
 * 打黑雾的四种炮塔（ADR-0028 §五）。射程按格，射速按每发间隔的刻数，用电按焦耳/刻（数值是戴森球计划的千分之一，
 * 和别的设备一样：界面上的瓦数是焦耳/刻 × 20）。
 *
 * <p>没在交战时只耗待机的电；有目标时按交战的电算，供电不足时按满足率放慢射速。
 */
public enum TurretKind {
    /** 高斯炮塔：子弹，打地面和低空，单体，射得快，远了会打偏。 */
    GAUSS("gauss_turret", 32, 20.0 / 6, 1, 6, true, false, true, 0.95, 0.75),
    /** 激光炮塔：不要弹药，耗电多，伤害低，打得准。 */
    LASER("laser_turret", 24, 5, 6, 120, false, false, true, 1, 1),
    /** 聚爆炮：炮弹走弧线，只打地面，范围伤害。 */
    IMPLOSION("implosion_cannon", 40, 30, 2, 18, true, true, false, 1, 1),
    /** 导弹炮塔：导弹会追，地面和低空都打，范围伤害带击退；能借同一电网里信号塔的范围。 */
    MISSILE("missile_turret", 48, 30, 2, 9, true, false, false, 1, 1);

    /** 激光炮塔每一发的伤害。 */
    public static final float LASER_DAMAGE = 2;

    private final String id;
    private final double range;
    private final double ticksPerShot;
    private final long idlePower;
    private final long activePower;
    private final boolean usesAmmo;
    private final boolean groundOnly;
    private final boolean direct;
    private final double nearAccuracy;
    private final double farAccuracy;

    TurretKind(String id, double range, double ticksPerShot, long idlePower, long activePower, boolean usesAmmo, boolean groundOnly,
            boolean direct, double nearAccuracy, double farAccuracy) {
        this.id = id;
        this.range = range;
        this.ticksPerShot = ticksPerShot;
        this.idlePower = idlePower;
        this.activePower = activePower;
        this.usesAmmo = usesAmmo;
        this.groundOnly = groundOnly;
        this.direct = direct;
        this.nearAccuracy = nearAccuracy;
        this.farAccuracy = farAccuracy;
    }

    /** 方块 ID（不带命名空间）。 */
    public String id() {
        return this.id;
    }

    public double range() {
        return this.range;
    }

    /** 每发间隔多少刻（满电时）。 */
    public double ticksPerShot() {
        return this.ticksPerShot;
    }

    /** 每秒几发（满电时）。 */
    public double shotsPerSecond() {
        return 20 / this.ticksPerShot;
    }

    /** 没有目标时的用电，焦耳/刻。 */
    public long idlePower() {
        return this.idlePower;
    }

    /** 交战时的用电，焦耳/刻。 */
    public long activePower() {
        return this.activePower;
    }

    public boolean usesAmmo() {
        return this.usesAmmo;
    }

    /** 只打地面目标。 */
    public boolean groundOnly() {
        return this.groundOnly;
    }

    /** 直射（子弹、激光）：要有视线，打出去当刻就命中。 */
    public boolean direct() {
        return this.direct;
    }

    /** 这种炮塔用的弹药。 */
    public List<AmmoKind> ammo() {
        return AmmoKind.forTurret(this);
    }

    /** 打 {@code distance} 格外的目标命中的概率：近处和远处之间线性插值。 */
    public double hitChance(double distance) {
        double t = Math.max(0, Math.min(1, distance / this.range));
        return this.nearAccuracy + (this.farAccuracy - this.nearAccuracy) * t;
    }

    /** 每秒的伤害（满电、一直命中）：激光按自己的伤害，别的按弹药。 */
    public double damagePerSecond(AmmoKind ammo) {
        double damage = this == LASER ? LASER_DAMAGE : ammo.damage();
        return damage * this.shotsPerSecond();
    }
}
