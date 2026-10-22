package io.github.sweetcornna.universe.core.darkfog;

import org.jspecify.annotations.Nullable;

/**
 * 信号塔、干扰塔、投掷物的数值（ADR-0028 §五）。功率是戴森球计划的千分之一、按刻算（焦耳/刻），和别的设备一样。
 */
public final class Support {
    // ---- 信号塔

    /** 信号塔当电线杆用：连得远，覆盖得比电力感应塔大。 */
    public static final int SIGNAL_CONNECT = 30;
    public static final int SIGNAL_COVER = 12;
    /** 信号塔一直要的电：戴森球计划的 600 kW。 */
    public static final long SIGNAL_POWER = 30;
    /** 嘲讽、超载的范围。 */
    public static final double TAUNT_RANGE = 48;

    // ---- 干扰塔

    /** 每隔多少刻发一次干扰。 */
    public static final int JAM_INTERVAL = 20;
    /** 一次干扰管多少刻，比间隔长一点，连着发就不断。 */
    public static final int JAM_TICKS = 30;
    /** 范围里有黑雾时要的电：戴森球计划的 800 kW；没有时待机。 */
    public static final long JAMMER_POWER = 40;
    public static final long JAMMER_IDLE = 2;
    /** 一个干扰胶囊管几次干扰（10 秒）。 */
    public static final int CAPSULE_PULSES = 10;
    /** 只用电：16 格内慢到七成。 */
    public static final Jam POWER_ONLY = new Jam(16, 0.7);
    /** 装了干扰胶囊：24 格内慢到四成。 */
    public static final Jam CAPSULE = new Jam(24, 0.4);

    private Support() {
    }

    /**
     * 一次干扰。
     *
     * @param radius 半径（格）
     * @param factor 被干扰的黑雾移动和攻击变慢到几成，0–1
     */
    public record Jam(double radius, double factor) {
        /** 电不够时干扰变弱：减速的那部分按满足率打折，半径不变。 */
        public Jam scaled(double satisfaction) {
            double s = Math.max(0, Math.min(1, satisfaction));
            return new Jam(this.radius, 1 - (1 - this.factor) * s);
        }
    }

    /** 这一次干扰的效果；没电时是空的。 */
    public static @Nullable Jam jam(boolean capsule, double satisfaction) {
        if (satisfaction <= 0) {
            return null;
        }
        return (capsule ? CAPSULE : POWER_ONLY).scaled(satisfaction);
    }

    // ---- 投掷物

    /**
     * 扔出去的单元：落地炸开，半径里的生物挨伤害，爆心满伤害、边上一半，不破坏方块。燃烧单元另外让人着火。
     *
     * @param radius    半径（格）
     * @param damage    爆心的伤害
     * @param fireTicks 着火多少刻；0 是不着火
     */
    public enum Charge {
        COMBUSTIBLE(3, 6, 100),
        EXPLOSIVE(4, 16, 0),
        CRYSTAL(5, 32, 0);

        private final double radius;
        private final float damage;
        private final int fireTicks;

        Charge(double radius, float damage, int fireTicks) {
            this.radius = radius;
            this.damage = damage;
            this.fireTicks = fireTicks;
        }

        public double radius() {
            return this.radius;
        }

        public int fireTicks() {
            return this.fireTicks;
        }

        /** 离爆心 {@code distance} 格挨的伤害：爆心满伤害，到边上一半，再远是 0。 */
        public float damage(double distance) {
            if (distance > this.radius) {
                return 0;
            }
            return (float) (this.damage * (1 - 0.5 * Math.max(0, distance) / this.radius));
        }
    }

    /** 扔出去以后多久不能再扔（刻）。 */
    public static final int THROW_COOLDOWN = 20;
    /** 扔出去的速度（格/刻）。 */
    public static final float THROW_SPEED = 1.2F;
}
