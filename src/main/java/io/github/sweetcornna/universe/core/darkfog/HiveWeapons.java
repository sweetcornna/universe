package io.github.sweetcornna.universe.core.darkfog;

import java.util.Optional;

/**
 * 太空巢穴两种塔的火力（ADR-0028 §八）。射程按太空的尺度（海盗战机 90 格开火，飞船巡航一刻两格多），伤害按飞船的船体
 * （一两百点）定。等级加伤害（{@link DarkFogLevels#scale}），也让射得快一些：每级快 1%。
 *
 * <ul>
 *   <li>等离子塔：远，慢，一发重；炮弹飞得不快，开着引擎拐个弯就躲开了，炸开有范围。</li>
 *   <li>相位激光塔：近一些，一秒两三发，打得准，单发轻。</li>
 * </ul>
 */
public final class HiveWeapons {
    /** 每级射速快多少。 */
    public static final double RATE_PER_LEVEL = 0.01;

    public enum Weapon {
        PLASMA(180, 50, 10, 2.4, 4.5, 1.5F),
        LASER(96, 8, 1.5F, 5.0, 0, 1.0F);

        private final double range;
        private final int interval;
        private final float damage;
        private final double speed;
        private final double splash;
        private final float inaccuracy;

        Weapon(double range, int interval, float damage, double speed, double splash, float inaccuracy) {
            this.range = range;
            this.interval = interval;
            this.damage = damage;
            this.speed = speed;
            this.splash = splash;
            this.inaccuracy = inaccuracy;
        }

        /** 射程（格），按炮口到目标中心算。 */
        public double range() {
            return this.range;
        }

        /** 弹体的速度（格/刻）。 */
        public double speed() {
            return this.speed;
        }

        /** 炸开的半径；0 是只打中的那一个。 */
        public double splash() {
            return this.splash;
        }

        /** 散布（原版 {@code shoot} 的 uncertainty）。 */
        public float inaccuracy() {
            return this.inaccuracy;
        }

        /** 弹体飞多少刻才消失：飞满射程再多一点。 */
        public int lifetime() {
            return (int) Math.ceil(this.range * 1.25 / this.speed);
        }

        /** 这一级每发间隔多少刻。 */
        public int interval(int level) {
            return Math.max(1, (int) Math.round(this.interval / (1 + RATE_PER_LEVEL * DarkFogLevels.clamp(level))));
        }

        /** 这一级一发的伤害（范围伤害是爆心的）。 */
        public float damage(int level) {
            return (float) (this.damage * DarkFogLevels.scale(level));
        }

        /** 这一级每秒的伤害（一直打中、范围伤害只算一个目标）。 */
        public double damagePerSecond(int level) {
            return this.damage(level) * 20.0 / this.interval(level);
        }
    }

    private HiveWeapons() {
    }

    /** 这种建筑带的武器；不是塔的没有。 */
    public static Optional<Weapon> of(HiveBuildingKind kind) {
        return switch (kind) {
            case PLASMA_TOWER -> Optional.of(Weapon.PLASMA);
            case PHASE_LASER -> Optional.of(Weapon.LASER);
            default -> Optional.empty();
        };
    }

    /**
     * 弹体多少刻后能和匀速飞的目标碰上。
     *
     * @param dx    目标相对炮口的位置
     * @param vx    目标的速度（格/刻）
     * @param speed 弹体的速度（格/刻）
     * @return 最早碰上的时刻；追不上是 {@link Double#NaN}
     */
    public static double interceptTime(double dx, double dy, double dz, double vx, double vy, double vz, double speed) {
        // |d + v t| = s t  →  (v·v - s²) t² + 2 (d·v) t + d·d = 0
        double a = vx * vx + vy * vy + vz * vz - speed * speed;
        double b = 2 * (dx * vx + dy * vy + dz * vz);
        double c = dx * dx + dy * dy + dz * dz;
        if (c < 1e-12) {
            return 0;
        }
        if (Math.abs(a) < 1e-9) {
            double t = -c / b;
            return b < 0 && t > 0 ? t : Double.NaN;
        }
        double disc = b * b - 4 * a * c;
        if (disc < 0) {
            return Double.NaN;
        }
        double root = Math.sqrt(disc);
        double t1 = (-b - root) / (2 * a);
        double t2 = (-b + root) / (2 * a);
        double t = Math.min(t1, t2);
        if (t <= 0) {
            t = Math.max(t1, t2);
        }
        return t > 0 ? t : Double.NaN;
    }

    /**
     * 往哪里打：按目标现在的速度提前量算碰头的地方（相对炮口）；追不上、或者要飞得比 {@code maxTicks} 还久，就直接对着现在的位置打。
     *
     * @param lead 提前量打几成：1 是算满，0 是不提前
     */
    public static double[] aim(double dx, double dy, double dz, double vx, double vy, double vz, double speed, double maxTicks, double lead) {
        double t = interceptTime(dx, dy, dz, vx, vy, vz, speed);
        if (Double.isNaN(t) || t > maxTicks) {
            return new double[] {dx, dy, dz};
        }
        double k = t * Math.max(0, Math.min(1, lead));
        return new double[] {dx + vx * k, dy + vy * k, dz + vz * k};
    }

    /** 离爆心 {@code distance} 格的伤害：爆心满伤害，到边上剩一半，边外没有。 */
    public static float splashDamage(Weapon weapon, int level, double distance) {
        return splashDamage(weapon.damage(level), weapon.splash(), distance);
    }

    /** 同上，按一发的伤害 {@code damage} 和溅射半径 {@code radius} 算。 */
    public static float splashDamage(float damage, double radius, double distance) {
        if (radius <= 0 || distance > radius) {
            return 0;
        }
        double t = Math.max(0, distance) / radius;
        return (float) (damage * (1 - 0.5 * t));
    }
}
