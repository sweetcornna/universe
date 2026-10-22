package io.github.sweetcornna.universe.core.darkfog;

/** 黑雾的等级（ADR-0028 §二）：0–30 级，每级血量和伤害 +4%。 */
public final class DarkFogLevels {
    public static final int MAX_LEVEL = 30;
    /** 每级加的血量和伤害。 */
    public static final double PER_LEVEL = 0.04;

    private DarkFogLevels() {
    }

    /** 这一级的血量、伤害倍率：0 级 1 倍，30 级 2.2 倍。 */
    public static double scale(int level) {
        return 1 + PER_LEVEL * clamp(level);
    }

    public static int clamp(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** 从这一级升到下一级要的经验：400 起，每级多 200。基地挨的每一点伤害算一点经验。 */
    public static double xpToNext(int level) {
        return 400 + 200.0 * clamp(level);
    }
}
