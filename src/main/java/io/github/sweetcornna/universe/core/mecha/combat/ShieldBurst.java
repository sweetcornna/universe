package io.github.sweetcornna.universe.core.mecha.combat;

/**
 * 护盾爆发（ADR-0033 §二）：按住键蓄力，最多 {@link #MAX_CHARGE} 刻，松开时放出。蓄满用掉当前护盾的一半，没蓄满按比例；
 * 对半径 {@link #RADIUS} 格内的黑雾和怪物造成用掉的护盾 × {@link #DAMAGE_PER_POINT} 的伤害，带击退。太空里半径 ×
 * {@link #SPACE_RADIUS_FACTOR}，伤害跟着加 {@link #SPACE_DAMAGE_BONUS}。放完冷却 {@link #COOLDOWN} 刻。
 */
public final class ShieldBurst {
    public static final int MAX_CHARGE = 40;
    /** 蓄力不到这么多刻就松开：不放。 */
    public static final int MIN_CHARGE = 4;
    public static final double RADIUS = 6;
    public static final double SPACE_RADIUS_FACTOR = 3;
    public static final double SPACE_DAMAGE_BONUS = 0.5;
    public static final double SPEND = 0.5;
    public static final double DAMAGE_PER_POINT = 2;
    public static final int COOLDOWN = 100;
    /** 用掉的护盾不到这么多：不放。 */
    public static final float MIN_SPENT = 1;

    private ShieldBurst() {
    }

    /** 蓄了 {@code ticks} 刻，蓄到几成（0–1）。 */
    public static double charge(int ticks) {
        return Math.clamp(ticks / (double) MAX_CHARGE, 0, 1);
    }

    /** 当前护盾 {@code shield}、蓄到 {@code charge} 成时用掉多少护盾。 */
    public static float spent(float shield, double charge) {
        return (float) (Math.max(0, shield) * SPEND * Math.clamp(charge, 0, 1));
    }

    public static double radius(boolean space) {
        return space ? RADIUS * SPACE_RADIUS_FACTOR : RADIUS;
    }

    /** 用掉 {@code spent} 点护盾打出去的伤害。 */
    public static float damage(float spent, boolean space) {
        return (float) (spent * DAMAGE_PER_POINT * (space ? 1 + SPACE_DAMAGE_BONUS : 1));
    }

    /** 离中心 {@code distance} 格的击退力度：中心最大，边上剩三成。 */
    public static double knockback(double distance, double radius) {
        return 0.4 + 1.0 * (1 - Math.clamp(distance / Math.max(1e-6, radius), 0, 1));
    }

    /** 松开时放不放得出来。 */
    public static boolean fires(int chargeTicks, float spent) {
        return chargeTicks >= MIN_CHARGE && spent >= MIN_SPENT;
    }
}
