package io.github.sweetcornna.universe.core.darkfog;

/**
 * 建世界时定的黑雾选项（ADR-0028 §七），由游戏规则读出来。倍率都是小数，1 就是 100%。
 *
 * @param enabled        关了不生成基地，已有的停摆
 * @param aggressiveness 0–5：被动、迟钝、普通、敏锐、好斗、狂暴
 * @param initialLevel   新基地的等级，0–30
 * @param occupation     每颗星球的基地数和母星系保底，0–3
 * @param growth         攒物质、盖建筑、补单位的速度，0.25–4
 * @param powerThreat    用电涨威胁的速度，0–4
 * @param combatThreat   挨打涨威胁的速度，0–4
 */
public record DarkFogSettings(boolean enabled, int aggressiveness, int initialLevel, double occupation, double growth, double powerThreat,
        double combatThreat) {
    public static final int PASSIVE = 0;
    public static final int NORMAL = 2;
    public static final int MAX_AGGRESSIVENESS = 5;
    public static final DarkFogSettings DEFAULT = new DarkFogSettings(true, NORMAL, 0, 1, 1, 1, 1);

    public DarkFogSettings {
        aggressiveness = Math.max(PASSIVE, Math.min(MAX_AGGRESSIVENESS, aggressiveness));
        initialLevel = DarkFogLevels.clamp(initialLevel);
        occupation = Math.max(0, Math.min(3, occupation));
        growth = Math.max(0.25, Math.min(4, growth));
        powerThreat = Math.max(0, Math.min(4, powerThreat));
        combatThreat = Math.max(0, Math.min(4, combatThreat));
    }

    /** 被动：只守家，不进攻。 */
    public boolean passive() {
        return this.aggressiveness == PASSIVE;
    }
}
