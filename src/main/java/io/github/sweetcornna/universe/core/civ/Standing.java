package io.github.sweetcornna.universe.core.civ;

/**
 * 声望（ADR-0027 §四）：每人对每个种族、每个公会各一个数，−20 到 100，从 0 开始，按分界分成六档。
 * 陌生从 −5 起：一开始答错一两次、打一下还不至于翻脸。
 */
public final class Standing {
    public static final int MIN = -20;
    public static final int MAX = 100;
    public static final int START = 0;

    /** 对话选对、选错。 */
    public static final int RIGHT_ANSWER = 3;
    public static final int WRONG_ANSWER = -1;
    /** 送了合心意的礼物。 */
    public static final int LIKED_GIFT = 2;
    /** 打了这个种族的 NPC。 */
    public static final int ATTACK = -5;
    /** 完成公会任务：公会、恒星系的主导种族（第二块）。 */
    public static final int GUILD_MISSION = 3;
    public static final int MISSION_RACE = 2;

    /** 档：{@code min} 是这一档的下限。种族和公会共用分界，公会的档名不一样（语言文件里分开写）。 */
    public enum Tier {
        HOSTILE(MIN),
        STRANGER(-5),
        ACQUAINTED(10),
        REGULAR(25),
        FRIEND(45),
        KINDRED(70);

        private final int min;

        Tier(int min) {
            this.min = min;
        }

        public int min() {
            return this.min;
        }

        /** 下一档的下限；最高一档没有，返回 {@link #MAX}。 */
        public int next() {
            return this.ordinal() + 1 < values().length ? values()[this.ordinal() + 1].min : MAX;
        }
    }

    private Standing() {
    }

    public static int clamp(int standing) {
        return Math.clamp(standing, MIN, MAX);
    }

    public static Tier tier(int standing) {
        Tier[] tiers = Tier.values();
        for (int i = tiers.length - 1; i > 0; i--) {
            if (standing >= tiers[i].min) {
                return tiers[i];
            }
        }
        return Tier.HOSTILE;
    }

    /** 敌视的 NPC 不对话、不卖东西。 */
    public static boolean canTalk(Tier tier) {
        return tier != Tier.HOSTILE;
    }

    /** "认识"以后能请 NPC 教词。 */
    public static boolean canAskWord(Tier tier) {
        return tier.compareTo(Tier.ACQUAINTED) >= 0;
    }

    /** 在这个种族的空间站买东西的价格倍数。 */
    public static double priceFactor(Tier tier) {
        return switch (tier) {
            case HOSTILE -> 1.10;
            case STRANGER, ACQUAINTED -> 1.0;
            case REGULAR -> 0.95;
            case FRIEND -> 0.90;
            case KINDRED -> 0.85;
        };
    }
}
