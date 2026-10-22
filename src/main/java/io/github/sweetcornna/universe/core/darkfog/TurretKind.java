package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;

/**
 * 打黑雾的炮塔（ADR-0028 §五、§八）。射程按格，射速按每发间隔的刻数，用电按焦耳/刻（数值是戴森球计划的千分之一，
 * 和别的设备一样：界面上的瓦数是焦耳/刻 × 20）。
 *
 * <p>没在交战时只耗待机的电；有目标时按交战的电算，供电不足时按满足率放慢射速。
 *
 * <p>高空目标（从太空下来轰炸的枪骑）只有导弹炮塔和等离子炮塔打得到，等离子炮塔对它们射程更远。
 */
public enum TurretKind {
    /** 高斯炮塔：子弹，打地面和低空，单体，射得快，远了会打偏。 */
    GAUSS("gauss_turret", 32, 20.0 / 6, 1, 6, true, false, true, 0.95, 0.75, 0),
    /** 激光炮塔：不要弹药，耗电多，伤害低，打得准。 */
    LASER("laser_turret", 24, 5, 6, 120, false, false, true, 1, 1, 0),
    /** 聚爆炮：炮弹走弧线，只打地面，范围伤害。 */
    IMPLOSION("implosion_cannon", 40, 30, 2, 18, true, true, false, 1, 1, 0),
    /** 导弹炮塔：导弹会追，地面、低空、高空都打，范围伤害带击退；能借同一电网里信号塔的范围。 */
    MISSILE("missile_turret", 48, 30, 2, 9, true, false, false, 1, 1, 48),
    /** 等离子炮塔：等离子胶囊、反物质胶囊，打出飞得快的等离子团，命中有范围伤害；地面、低空、高空都打，打高空射程更远。 */
    PLASMA("plasma_turret", 56, 20, 4, 84, true, false, false, 1, 1, 96),
    /** 近程等离子炮塔：只用电，射程短、一发伤害高，打地面和低空；待机也很耗电。 */
    SR_PLASMA("sr_plasma_turret", 16, 100.0 / 3, 105, 210, false, false, false, 1, 1, 0);

    /** 激光炮塔每一发的伤害。 */
    public static final float LASER_DAMAGE = 2;
    /** 近程等离子炮塔每一发的伤害（爆心）。 */
    public static final float SR_PLASMA_DAMAGE = 36;
    /** 近程等离子炮塔的爆炸半径。 */
    public static final double SR_PLASMA_RADIUS = 1.5;
    /** 等离子团飞行的速度（格/刻）。 */
    public static final double PLASMA_SPEED = 2.5;

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
    private final double highAltitudeRange;

    TurretKind(String id, double range, double ticksPerShot, long idlePower, long activePower, boolean usesAmmo, boolean groundOnly,
            boolean direct, double nearAccuracy, double farAccuracy, double highAltitudeRange) {
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
        this.highAltitudeRange = highAltitudeRange;
    }

    /** 方块 ID（不带命名空间）。 */
    public String id() {
        return this.id;
    }

    public double range() {
        return this.range;
    }

    /** 打得到高空目标。 */
    public boolean antiAir() {
        return this.highAltitudeRange > 0;
    }

    /** 打高空目标的射程；打不到是 0。 */
    public double highAltitudeRange() {
        return this.highAltitudeRange;
    }

    /** 对这种目标的射程：高空目标按 {@link #highAltitudeRange}，别的按 {@link #range}。 */
    public double reach(boolean highAltitude) {
        return highAltitude ? this.highAltitudeRange : this.range;
    }

    /** 两种射程里远的那个（找目标时搜这么大一圈）。 */
    public double maxReach() {
        return Math.max(this.range, this.highAltitudeRange);
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

    /** 每秒的伤害（满电、一直命中）：激光、近程等离子按自己的伤害，别的按弹药。 */
    public double damagePerSecond(AmmoKind ammo) {
        double damage = switch (this) {
            case LASER -> LASER_DAMAGE;
            case SR_PLASMA -> SR_PLASMA_DAMAGE;
            default -> ammo.damage();
        };
        return damage * this.shotsPerSecond();
    }
}
