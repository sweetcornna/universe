package io.github.sweetcornna.universe.core.darkfog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 炮塔的弹药（ADR-0028 §五）。一个弹药物品装进炮塔能打 {@link #rounds()} 发；伤害对黑雾的单位和建筑都一样，
 * 不随黑雾等级变（等级只加对方的血）。
 *
 * <p>范围伤害的 {@link #radius()} 以外不伤；{@link #push()} 是爆心处把目标推开的速度（格/刻），负数是往里吸（引力导弹）。
 */
public enum AmmoKind {
    MAGNUM("magnum_ammo_box", TurretKind.GAUSS, 2.5F, 40, 0, 0, 0),
    TITANIUM("titanium_ammo_box", TurretKind.GAUSS, 4, 40, 0, 0, 0),
    SUPERALLOY("superalloy_ammo_box", TurretKind.GAUSS, 6, 40, 0, 0, 0),
    SHELL("shell_set", TurretKind.IMPLOSION, 15, 10, 3, 0.3, 1.1),
    HIGH_EXPLOSIVE_SHELL("high_explosive_shell_set", TurretKind.IMPLOSION, 25, 10, 3.5, 0.4, 1.1),
    CRYSTAL_SHELL("crystal_shell_set", TurretKind.IMPLOSION, 40, 10, 4, 0.4, 1.2),
    MISSILE("missile_set", TurretKind.MISSILE, 20, 8, 3, 0.6, 0.9),
    SUPERSONIC_MISSILE("supersonic_missile_set", TurretKind.MISSILE, 32, 8, 3, 0.7, 1.8),
    GRAVITY_MISSILE("gravity_missile_set", TurretKind.MISSILE, 45, 8, 4.5, -0.5, 1.2);

    private final String id;
    private final TurretKind turret;
    private final float damage;
    private final int rounds;
    private final double radius;
    private final double push;
    private final double speed;

    AmmoKind(String id, TurretKind turret, float damage, int rounds, double radius, double push, double speed) {
        this.id = id;
        this.turret = turret;
        this.damage = damage;
        this.rounds = rounds;
        this.radius = radius;
        this.push = push;
        this.speed = speed;
    }

    /** 物品 ID（不带命名空间）。 */
    public String id() {
        return this.id;
    }

    public TurretKind turret() {
        return this.turret;
    }

    /** 一发的伤害；范围伤害是爆心的伤害，往外线性减到一半。 */
    public float damage() {
        return this.damage;
    }

    /** 一个物品打几发。 */
    public int rounds() {
        return this.rounds;
    }

    /** 范围伤害的半径；0 是单体。 */
    public double radius() {
        return this.radius;
    }

    public double push() {
        return this.push;
    }

    /** 炮弹、导弹飞行的速度（格/刻）；子弹是直接命中，这里不用。 */
    public double speed() {
        return this.speed;
    }

    /** 离爆心 {@code distance} 格的伤害：爆心满伤害，到边上剩一半，边外没有。 */
    public float splashDamage(double distance) {
        if (this.radius <= 0) {
            return distance <= 0 ? this.damage : 0;
        }
        if (distance > this.radius) {
            return 0;
        }
        double t = Math.max(0, distance) / this.radius;
        return (float) (this.damage * (1 - 0.5 * t));
    }

    public static List<AmmoKind> forTurret(TurretKind turret) {
        List<AmmoKind> list = new ArrayList<>();
        for (AmmoKind kind : values()) {
            if (kind.turret == turret) {
                list.add(kind);
            }
        }
        return List.copyOf(list);
    }

    public static Optional<AmmoKind> byId(String id) {
        for (AmmoKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
