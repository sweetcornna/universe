package io.github.sweetcornna.universe.core.sentinel;

/**
 * 建世界时定的守卫机强度（ADR-0028 §七）：血量、伤害和每波的架数乘这个倍率；0 时守卫机不出现。
 */
public final class SentinelStrength {
    public static final double MAX = 3;

    private SentinelStrength() {
    }

    public static double clamp(double strength) {
        return Math.max(0, Math.min(MAX, strength));
    }

    /** 守卫机出不出现。 */
    public static boolean present(double strength) {
        return strength > 0;
    }

    /** 一种守卫机的架数：本来没有的还是没有，本来有的至少一架。 */
    public static int count(int count, double strength) {
        if (count <= 0 || !present(strength)) {
            return 0;
        }
        return Math.max(1, (int) Math.round(count * clamp(strength)));
    }

    /** 血量、伤害按倍率调整；倍率 0 时守卫机不出现，这里按 1 算。 */
    public static double scale(double value, double strength) {
        return present(strength) ? value * clamp(strength) : value;
    }
}
