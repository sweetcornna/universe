package io.github.sweetcornna.universe.core.ship;

import io.github.sweetcornna.universe.core.survival.LaserHeat;

/**
 * 飞船的船体、护盾和武器（ADR-0017）。
 *
 * <p>船体和护盾算在整艘船上：挨打先扣护盾，护盾打光再扣船体，船体扣光就是被击毁。3 秒没挨打后护盾每秒回 10 点。
 * 光子炮每门每秒 3 发、每发 6 点伤害，几门炮轮流开火；按住开火键升温，和多功能工具一样连发 5 秒过热，
 * 过热后要冷却到底才能再开。采矿光束升温慢一些。
 *
 * <p>瞄准：炮口朝驾驶员看的方向，但每门炮左右只能偏离自己的朝向 {@link #AIM_YAW}°，上下最多 {@link #AIM_PITCH}°（方块拼的船
 * 只会转向、不会俯仰，所以炮像炮塔一样能抬高压低）。朝前的炮管船头前方，朝两侧、朝后装的炮守住别的方向。视线
 * {@link #ASSIST_CONE}° 以内有敌人时瞄准它的提前量。向量都是 {x, y, z} 的 double 数组。
 */
public final class ShipCombat {
    /** 挨打后这么多刻不回护盾。 */
    public static final int SHIELD_DELAY = 60;
    /** 护盾每刻回多少。 */
    public static final double SHIELD_REGEN = 10.0 / 20;
    /** 每门光子炮每秒几发。 */
    public static final double CANNON_RATE = 3;
    /** 一发光子弹的伤害。 */
    public static final float CANNON_DAMAGE = 6;
    /** 光子弹相对飞船每刻飞几格、飞多少刻（射程约 150 格）。 */
    public static final double BOLT_SPEED = 5;
    public static final int BOLT_LIFETIME = 30;
    /** 采矿光束的射程（格）和每刻对小行星的伤害。 */
    public static final double BEAM_RANGE = 48;
    public static final float BEAM_DAMAGE = 1.0F;
    /** 炮口左右能偏离炮的朝向多少度、上下能抬高压低多少度。 */
    public static final double AIM_YAW = 60;
    public static final double AIM_PITCH = 60;
    /** 视线这么多度以内的敌人会被自动瞄准。 */
    public static final double ASSIST_CONE = 10;
    /** 自动瞄准的最远距离（格）。 */
    public static final double ASSIST_RANGE = 128;
    /** 光子炮和采矿光束的升温倍数（多功能工具的等离子弹是 0.8，连发 5 秒过热）。 */
    public static final float CANNON_HEAT = 0.8F;
    public static final float BEAM_HEAT = 0.5F;
    /** 击毁后拖回空间站时船体剩几成；货舱里每一组丢几成。 */
    public static final double TOW_HULL = 0.25;
    public static final double CARGO_LOSS = 0.3;
    private static final double EPSILON = 1e-9;

    /** 一次伤害打在哪。 */
    public enum Hit {
        /** 护盾全挡住了。 */
        SHIELD,
        /** 打到了船体。 */
        HULL,
        /** 船体打光了。 */
        DESTROYED
    }

    private double hull;
    private double shield;
    private int sinceHit = SHIELD_DELAY;
    private final LaserHeat heat = new LaserHeat();
    private double charge = 1;

    public ShipCombat(double hull, double shield) {
        this.hull = hull;
        this.shield = shield;
    }

    public double hull() {
        return this.hull;
    }

    public double shield() {
        return this.shield;
    }

    public float heat() {
        return this.heat.heat();
    }

    public boolean overheated() {
        return this.heat.overheated();
    }

    /** 读档、停船重新组装、修理后设定船体和护盾。 */
    public void set(double hull, double shield) {
        this.hull = Math.max(0, hull);
        this.shield = Math.max(0, shield);
    }

    /** 挨一下打。伤害不大于 0 时什么也不做，返回 {@link Hit#SHIELD}。 */
    public Hit damage(double amount) {
        if (amount <= 0 || this.hull <= 0) {
            return this.hull <= 0 ? Hit.DESTROYED : Hit.SHIELD;
        }
        this.sinceHit = 0;
        if (this.shield >= amount) {
            this.shield -= amount;
            return Hit.SHIELD;
        }
        double rest = amount - this.shield;
        this.shield = 0;
        this.hull = Math.max(0, this.hull - rest);
        return this.hull <= EPSILON ? Hit.DESTROYED : Hit.HULL;
    }

    /**
     * 推进一刻：回护盾、武器升温和冷却。
     *
     * @param maxShield 护盾上限
     * @param trigger   驾驶员按着开火键
     * @param cannons   光子炮门数
     * @param beam      在采矿模式（有采矿光束）
     * @return 这一刻光子炮打出几发；采矿模式下光束开着时返回 -1
     */
    public int tick(double maxShield, boolean trigger, int cannons, boolean beam) {
        return this.tick(maxShield, trigger, cannons, beam, 1);
    }

    /**
     * 同上，按射速的倍数（火控校准的技术，ADR-0024）充能。
     *
     * @param rate 射速的倍数
     */
    public int tick(double maxShield, boolean trigger, int cannons, boolean beam, double rate) {
        this.sinceHit++;
        if (this.sinceHit > SHIELD_DELAY && this.shield < maxShield) {
            this.shield = Math.min(maxShield, this.shield + SHIELD_REGEN);
        }
        this.shield = Math.min(this.shield, maxShield);
        if (beam) {
            return this.heat.tick(trigger, BEAM_HEAT) ? -1 : 0;
        }
        boolean firing = this.heat.tick(trigger && cannons > 0, CANNON_HEAT);
        double perTick = CANNON_RATE * cannons * rate / 20;
        if (!firing) {
            // 不开火时照样充能，但最多存 1 发：按下开火键的第一发立即打出，连点也快不过射速
            this.charge = Math.min(1, this.charge + perTick);
            return 0;
        }
        int shots = (int) Math.floor(this.charge + EPSILON);
        this.charge = Math.max(0, this.charge - shots) + perTick;
        return shots;
    }

    // ---- 瞄准

    /** 炮口实际能朝的方向：视线 {@code look} 的偏航限制在炮的朝向 {@code weaponYaw} 左右 {@link #AIM_YAW}° 以内，俯仰限制在 {@link #AIM_PITCH}° 以内。 */
    public static double[] clampToArc(float weaponYaw, double[] look) {
        return clampToArc(weaponYaw, look, AIM_YAW, AIM_PITCH);
    }

    /** 朝向 {@code weaponYaw} 的炮转得过去朝 {@code look} 打。 */
    public static boolean inArc(float weaponYaw, double[] look) {
        return angle(clampToArc(weaponYaw, look), normalize(look)) < 0.5;
    }

    public static double[] clampToArc(float shipYaw, double[] look, double yawLimit, double pitchLimit) {
        double[] l = normalize(look);
        double horizontal = Math.sqrt(l[0] * l[0] + l[2] * l[2]);
        double yaw = horizontal < 1e-9 ? shipYaw : Math.toDegrees(Math.atan2(-l[0], l[2]));
        double pitch = Math.toDegrees(Math.atan2(l[1], horizontal));
        double relative = yaw - shipYaw;
        relative = relative - 360 * Math.floor((relative + 180) / 360);
        relative = Math.max(-yawLimit, Math.min(yawLimit, relative));
        pitch = Math.max(-pitchLimit, Math.min(pitchLimit, pitch));
        double y = Math.toRadians(shipYaw + relative);
        double p = Math.toRadians(pitch);
        return new double[] {-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    /**
     * 提前量：弹速 {@code speed} 打向相对位置 {@code target}、相对速度 {@code velocity} 的目标，返回瞄准方向（单位向量）。
     * 追不上时直接瞄准目标现在的位置。
     */
    public static double[] lead(double[] target, double[] velocity, double speed) {
        double a = dot(velocity, velocity) - speed * speed;
        double b = 2 * dot(target, velocity);
        double c = dot(target, target);
        double t = -1;
        if (Math.abs(a) < 1e-9) {
            if (Math.abs(b) > 1e-9) {
                t = -c / b;
            }
        } else {
            double disc = b * b - 4 * a * c;
            if (disc >= 0) {
                double sq = Math.sqrt(disc);
                double t1 = (-b - sq) / (2 * a);
                double t2 = (-b + sq) / (2 * a);
                double small = Math.min(t1, t2);
                double large = Math.max(t1, t2);
                t = small > 0 ? small : large;
            }
        }
        if (t <= 0) {
            return normalize(target);
        }
        return normalize(new double[] {target[0] + velocity[0] * t, target[1] + velocity[1] * t, target[2] + velocity[2] * t});
    }

    /**
     * 带加速度的提前量：目标还在按 {@code accel} 加速（比如正在转弯的海盗战机），迭代几次求出命中的时刻再瞄准那里。
     * 预判最多看 {@code maxTicks} 刻：转弯的加速度方向一直在变，看得太远反而偏。
     */
    public static double[] lead(double[] target, double[] velocity, double[] accel, double speed, double maxTicks) {
        double t = Math.min(maxTicks, length(target) / speed);
        double[] p = target;
        for (int i = 0; i < 6; i++) {
            double h = 0.5 * t * t;
            p = new double[] {target[0] + velocity[0] * t + accel[0] * h, target[1] + velocity[1] * t + accel[1] * h,
                    target[2] + velocity[2] * t + accel[2] * h};
            t = Math.min(maxTicks, length(p) / speed);
        }
        return normalize(p);
    }

    /** 两个单位向量的夹角（度）。 */
    public static double angle(double[] a, double[] b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot(a, b)))));
    }

    /** 偏航对应的水平单位向量（偏航 0 朝南 +z，和原版实体一致）。 */
    public static double[] forward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new double[] {-Math.sin(rad), 0, Math.cos(rad)};
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double length(double[] v) {
        return Math.sqrt(dot(v, v));
    }

    public static double[] normalize(double[] v) {
        double length = length(v);
        if (length < 1e-12) {
            return new double[] {0, 0, 1};
        }
        return new double[] {v[0] / length, v[1] / length, v[2] / length};
    }
}
