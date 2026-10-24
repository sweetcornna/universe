package io.github.sweetcornna.universe.core.mecha.fleet;

import java.util.Optional;

/**
 * 机甲舰队的单位（ADR-0033 §一）：三种地面的、两种太空的。平时是物品，放在机甲面板的编队格子里，出动时变成实体。
 * 电按焦耳算：待命时每刻从机甲核心扣 {@link #idleJoules()}，每开一发扣 {@link #shotJoules()}。
 *
 * <p>数值对着黑雾：突袭者 30、游骑兵 20、守卫者 60 生命，枪骑 100、座头鲸 450（都按黑雾等级放大）。
 * 满编的原型机编队（6 架，每秒 18 点）两三秒打掉一架突袭者，和一座高斯炮塔差不多。
 */
public enum FleetUnitKind {
    PROTOTYPE("prototype", false, 40, Weapon.LASER, 3, 20, 16, 0.45, 5, 30, 0),
    PRECISION_DRONE("precision_drone", false, 60, Weapon.LASER, 5, 15, 28, 0.6, 8, 60, 0),
    ATTACK_DRONE("attack_drone", false, 150, Weapon.PLASMA, 12, 20, 12, 0.4, 12, 120, 1.5),
    /** 原作的"护卫舰"本模组的货船已经用了（ADR-0024），这里叫炮艇。 */
    CORVETTE("corvette", true, 600, Weapon.TWIN_LASER, 8, 10, 64, 1.8, 50, 200, 0),
    DESTROYER("destroyer", true, 2000, Weapon.PLASMA, 40, 40, 96, 1.2, 150, 800, 3);

    /** 武器：激光是直接命中，等离子弹飞过去炸。 */
    public enum Weapon {
        LASER,
        /** 两根炮管同时打，伤害算一发。 */
        TWIN_LASER,
        PLASMA
    }

    /** 驱逐舰的近防激光：只打枪骑，每 10 刻一发 6 点，射程 48 格，每发 50 J。 */
    public static final float POINT_DEFENSE_DAMAGE = 6;
    public static final int POINT_DEFENSE_INTERVAL = 10;
    public static final double POINT_DEFENSE_RANGE = 48;
    public static final double POINT_DEFENSE_JOULES = 50;
    /** 停在机甲里时修一点生命要的电（焦）。 */
    public static final double REPAIR_JOULES_PER_HP = 50;

    private final String id;
    private final boolean space;
    private final int maxHealth;
    private final Weapon weapon;
    private final float damage;
    private final int interval;
    private final double range;
    private final double speed;
    private final double idleJoules;
    private final double shotJoules;
    private final double splash;

    FleetUnitKind(String id, boolean space, int maxHealth, Weapon weapon, float damage, int interval, double range, double speed,
            double idleJoules, double shotJoules, double splash) {
        this.id = id;
        this.space = space;
        this.maxHealth = maxHealth;
        this.weapon = weapon;
        this.damage = damage;
        this.interval = interval;
        this.range = range;
        this.speed = speed;
        this.idleJoules = idleJoules;
        this.shotJoules = shotJoules;
        this.splash = splash;
    }

    /** 物品 ID（不带命名空间）。 */
    public String id() {
        return this.id;
    }

    /** 太空舰队的单位；不是就是地面编队的。 */
    public boolean space() {
        return this.space;
    }

    public int maxHealth() {
        return this.maxHealth;
    }

    public Weapon weapon() {
        return this.weapon;
    }

    /** 一发的伤害（等离子弹是爆心的伤害）。 */
    public float damage() {
        return this.damage;
    }

    /** 两发之间隔几刻。 */
    public int interval() {
        return this.interval;
    }

    /** 射程（格）。 */
    public double range() {
        return this.range;
    }

    /** 最快飞多快（格/刻）。 */
    public double speed() {
        return this.speed;
    }

    /** 出动着时每刻耗多少电（焦）。 */
    public double idleJoules() {
        return this.idleJoules;
    }

    /** 开一发耗多少电（焦）。 */
    public double shotJoules() {
        return this.shotJoules;
    }

    /** 等离子弹的爆炸半径；激光是 0。 */
    public double splash() {
        return this.splash;
    }

    /** 每秒伤害（不算范围）。 */
    public double dps() {
        return this.damage * 20.0 / this.interval;
    }

    /** 一刻最多改多少速度（格/刻²）：小的灵活，驱逐舰笨重。 */
    public double accel() {
        return switch (this) {
            case PROTOTYPE, PRECISION_DRONE -> 0.12;
            case ATTACK_DRONE -> 0.08;
            case CORVETTE -> 0.14;
            case DESTROYER -> 0.06;
        };
    }

    /** 等离子弹飞多快（格/刻）。 */
    public double projectileSpeed() {
        return this.space ? 2.5 : 1.4;
    }

    /** 停在机甲里时每秒修几点生命：生命的 2%，最少 2 点（满血要 50 秒左右）。 */
    public int repairPerSecond() {
        return Math.max(2, this.maxHealth / 50);
    }

    /** 从坏到满修一遍要多少电（焦）。 */
    public double fullRepairJoules() {
        return this.maxHealth * REPAIR_JOULES_PER_HP;
    }

    public static Optional<FleetUnitKind> byId(String id) {
        for (FleetUnitKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
