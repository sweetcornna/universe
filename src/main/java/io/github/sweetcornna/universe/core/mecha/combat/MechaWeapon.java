package io.github.sweetcornna.universe.core.mecha.combat;

import io.github.sweetcornna.universe.core.darkfog.AmmoKind;
import io.github.sweetcornna.universe.core.darkfog.TurretKind;

/**
 * 外骨骼胸甲自带的三门武器（ADR-0033 §二）。用背包和货舱里的炮塔弹药：伤害照弹药的数值乘 {@link #DAMAGE_FACTOR}，
 * 一个弹药物品打的发数和炮塔一样；每发从机甲核心扣 {@link #SHOT_JOULES}，没电不开。
 */
public enum MechaWeapon {
    /** 动能炮：子弹，直接命中，每秒 4 发。 */
    KINETIC("kinetic", TurretKind.GAUSS, 24, 5),
    /** 导弹舱：导弹会追，每 2 秒 1 发。 */
    MISSILE("missile", TurretKind.MISSILE, 40, 40),
    /** 等离子炮：每秒 1 发，只在太空里、或者打从太空下来的高空目标时用。 */
    PLASMA("plasma", TurretKind.PLASMA, 48, 20);

    public static final double DAMAGE_FACTOR = 0.6;
    public static final double SHOT_JOULES = 40;
    /** 自动模式下，正在打自己的敌人在这么多刻里算"正在打"。 */
    public static final int ATTACKER_MEMORY = 100;
    /** 瞄准模式下，准星和目标的夹角在这么多度以内就算对着。 */
    public static final double AIM_CONE = 6;

    private final String id;
    private final TurretKind turret;
    private final double range;
    private final int interval;

    MechaWeapon(String id, TurretKind turret, double range, int interval) {
        this.id = id;
        this.turret = turret;
        this.range = range;
        this.interval = interval;
    }

    public String id() {
        return this.id;
    }

    /** 用哪种炮塔的弹药。 */
    public TurretKind turret() {
        return this.turret;
    }

    public double range() {
        return this.range;
    }

    /** 两发之间隔几刻。 */
    public int interval() {
        return this.interval;
    }

    public boolean accepts(AmmoKind ammo) {
        return ammo.turret() == this.turret;
    }

    /** 一发的伤害（范围伤害是爆心的）。 */
    public float damage(AmmoKind ammo) {
        return (float) (ammo.damage() * DAMAGE_FACTOR);
    }

    /**
     * 这时候能不能用：等离子炮只在太空和低空用（机甲飞着，或者打的是从太空下来的高空目标）；另外两门哪里都能用。
     */
    public boolean usable(boolean space, boolean airborne) {
        return this != PLASMA || space || airborne;
    }

    /** 弹药数值对着原作再打折以后的每秒伤害（不算范围）。 */
    public double dps(AmmoKind ammo) {
        return this.damage(ammo) * 20.0 / this.interval;
    }
}
