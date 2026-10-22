package io.github.sweetcornna.universe.core.darkfog;

/**
 * 行星护盾发生器的数值和几何（ADR-0028 §八）。能量按焦耳，功率按焦耳/刻，和别的设备一样是戴森球计划的千分之一
 * （界面上的瓦数是焦耳/刻 × 20）。
 *
 * <ul>
 *   <li>原作（DSP 维基）：工作功率 24 MW、待机 12 MW；存到 1.44 GJ 展开低空护盾，只护住发生器附近，存到 2.88 GJ 升到满高度。
 *       满了以后多出来的电照样存着，加厚护盾。论坛实测单台存到约 8.6 GJ 后转为待机，这里把它当上限。</li>
 *   <li>这里：充能 1200 J/t（24 kW）；展开以后维持穹顶要 600 J/t（12 kW），从充进来的电里扣，没电时从存的能量里扣；
 *       存满 8.64 MJ 后只要维持的电。1.44 MJ 展开，半径从 {@value #MIN_RADIUS} 格起，到 2.88 MJ 时罩满 {@value #RADIUS} 格。</li>
 *   <li>穹顶是压扁的半椭球：小的时候是半球，大了以后最高 {@value #MAX_HEIGHT} 格（原作的护盾也是贴着地表的一层）。</li>
 *   <li>挡伤害：每点伤害扣 {@value #JOULES_PER_DAMAGE} 焦耳。刚展开的护盾挡得住约 1400 点，存满的约 8600 点。
 *       能量扣光了穹顶收起，要重新充到 1.44 MJ 才再展开。</li>
 * </ul>
 */
public final class ShieldMath {
    /** 存满时穹顶的半径（格）。 */
    public static final double RADIUS = 160;
    /** 刚展开时的半径：只护住发生器附近。 */
    public static final double MIN_RADIUS = 32;
    /**
     * 穹顶最高多高（格）。大了以后压扁成一层低低的罩子，像原作贴着星球表面的那层护盾；停在罩子上面扔炸弹的枪骑
     * 也就还在等离子炮塔的高空射程里。
     */
    public static final double MAX_HEIGHT = 64;
    /** 充能时的用电（焦耳/刻）。 */
    public static final long CHARGE_POWER = 1200;
    /** 展开以后维持穹顶的用电（焦耳/刻）。 */
    public static final long UPKEEP_POWER = 600;
    /** 存到这么多展开。 */
    public static final double RAISE_ENERGY = 1_440_000;
    /** 存到这么多罩满 {@link #RADIUS}。 */
    public static final double FULL_ENERGY = 2_880_000;
    /** 最多存这么多。 */
    public static final double CAPACITY = 8_640_000;
    /** 挡一点伤害扣多少焦耳。 */
    public static final double JOULES_PER_DAMAGE = 1000;
    /** 一颗星球上最多几台在工作，多的不充能、不展开。 */
    public static final int MAX_PER_PLANET = 80;

    private ShieldMath() {
    }

    /** 一台发生器的能量和穹顶展开没有。 */
    public record State(double energy, boolean raised) {
        public static final State EMPTY = new State(0, false);
    }

    /** 这一刻要多少电：没存满按充能算，存满了只要维持的电。 */
    public static long demand(State state) {
        if (state.energy() < CAPACITY) {
            return CHARGE_POWER;
        }
        return state.raised() ? UPKEEP_POWER : 0;
    }

    /** 一刻的结算：{@code supplied} 是电网实际给的焦耳。展开着的先扣维持的电，存到展开的线就展开，扣光了收起。 */
    public static State tick(State state, double supplied) {
        double energy = state.energy() + Math.max(0, supplied) - (state.raised() ? UPKEEP_POWER : 0);
        energy = Math.max(0, Math.min(CAPACITY, energy));
        boolean raised = state.raised();
        if (!raised && energy >= RAISE_ENERGY) {
            raised = true;
        } else if (raised && energy <= 0) {
            raised = false;
        }
        return new State(energy, raised);
    }

    /** 展开着的穹顶有多大：能量在展开线以下（挨了打）时缩到最小，满线以上罩满。 */
    public static double radius(double energy) {
        double t = (energy - RAISE_ENERGY) / (FULL_ENERGY - RAISE_ENERGY);
        return MIN_RADIUS + (RADIUS - MIN_RADIUS) * Math.max(0, Math.min(1, t));
    }

    /** 挡下一次伤害以后：剩下的能量、没挡住的伤害。 */
    public record Absorbed(double energy, float leftover) {
    }

    /** 用 {@code energy} 焦耳去挡 {@code damage} 点伤害。 */
    public static Absorbed absorb(double energy, float damage) {
        if (damage <= 0) {
            return new Absorbed(energy, 0);
        }
        double cost = damage * JOULES_PER_DAMAGE;
        if (energy >= cost) {
            return new Absorbed(energy - cost, 0);
        }
        return new Absorbed(0, (float) Math.max(0, damage - Math.max(0, energy) / JOULES_PER_DAMAGE));
    }

    /** 水平半径 {@code radius} 的穹顶有多高：小的是半球，大了压扁，最高 {@link #MAX_HEIGHT} 格。 */
    public static double height(double radius) {
        return Math.min(radius, MAX_HEIGHT);
    }

    /** 这一点（相对穹顶中心）在不在水平半径 {@code radius}、高 {@code height} 的穹顶（椭球）里。 */
    public static boolean inside(double dx, double dy, double dz, double radius, double height) {
        return (dx * dx + dz * dz) / (radius * radius) + dy * dy / (height * height) <= 1;
    }

    /** 穹顶外壳在水平偏开 ({@code dx}, {@code dz}) 的地方有多高（相对中心）；那一列不在穹顶底下是 NaN。 */
    public static double surface(double dx, double dz, double radius, double height) {
        double f = 1 - (dx * dx + dz * dz) / (radius * radius);
        return f < 0 ? Double.NaN : height * Math.sqrt(f);
    }

    /**
     * 从 {@code from} 飞到 {@code to} 的线段从外面碰到穹顶外壳的地方，按线段上的比例算（0 是起点，1 是终点）。
     * 起点已经在穹顶里、碰不到、或者要过了终点才碰到时是 -1。坐标都相对中心。椭球把竖直方向拉成和水平一样，就按球算。
     */
    public static double entry(double fx, double fy, double fz, double tx, double ty, double tz, double radius, double height) {
        double k = radius / height;
        return sphereEntry(fx, fy * k, fz, tx, ty * k, tz, radius);
    }

    private static double sphereEntry(double fx, double fy, double fz, double tx, double ty, double tz, double radius) {
        double c = fx * fx + fy * fy + fz * fz - radius * radius;
        if (c <= 0) {
            return -1;
        }
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        double a = dx * dx + dy * dy + dz * dz;
        if (a < 1e-12) {
            return -1;
        }
        double b = 2 * (fx * dx + fy * dy + fz * dz);
        double disc = b * b - 4 * a * c;
        if (disc < 0) {
            return -1;
        }
        double t = (-b - Math.sqrt(disc)) / (2 * a);
        return t >= 0 && t <= 1 ? t : -1;
    }

    /** 能量占"罩满"那条线的比例，界面和穹顶的颜色用（存满时是 3）。 */
    public static double charge(double energy) {
        return Math.max(0, energy) / FULL_ENERGY;
    }
}
