package io.github.sweetcornna.universe.core.settlement;

import java.util.List;

/**
 * 聚落的规则和数（ADR-0034 §二）：一天多长、施工等多久、住房、幸福、生产力、威胁、等级、维护费、海盗来袭的强弱。
 * {@link Settlement} 每天按这些算一遍。
 */
public final class SettlementRules {
    /** 聚落的一天：24000 刻，不管星球自己的昼长。 */
    public static final long DAY = 24000;
    /** 施工、升级每一段交齐材料以后等多久：5 分钟。 */
    public static final long STAGE_WAIT = 6000;
    /** 一个人最多管几个聚落。 */
    public static final int MAX_OWNED = 4;
    /** 每座盖好的建筑每天的维护费。 */
    public static final int UPKEEP = 40;
    /** 原来那几间小屋住得下的人。 */
    public static final int BASE_HOUSING = 4;
    /** 接管时的人口。 */
    public static final int START_POPULATION = 3;
    /** 来袭在聚落一天里的哪个时候开始（入夜）。 */
    public static final long RAID_HOUR = 13000;
    /** 守多久：2 分钟内打下全部算守住。 */
    public static final int RAID_TICKS = 2400;
    /** 总督离聚落多远以内真打。 */
    public static final double RAID_RANGE = 256;
    /** 接管以后头两天不来袭。 */
    public static final int RAID_GRACE_DAYS = 2;
    /** 留几条最近的事。 */
    public static final int HISTORY = 12;
    /** 最近出过的几个决定不再出。 */
    public static final int RECENT_DECISIONS = 8;
    /** 储备的上限（份），按等级 C、B、A、S。 */
    private static final int[] RESERVE_CAP = {60, 100, 150, 200};
    /** 等级分数到 B、A、S 的门槛。 */
    private static final double[] CLASS_SCORE = {8, 18, 30};
    private static final String[] CLASS_LETTERS = {"C", "B", "A", "S"};

    private SettlementRules() {
    }

    /** 接管要交的建设物资。 */
    public static List<Cost> claimCost() {
        return List.of(Cost.of("steel", 30), Cost.of("carbon_nanotube", 8), Cost.of("circuit_board", 8));
    }

    /** 第几天（按服务器的游戏刻）。 */
    public static long day(long gameTime) {
        return Math.floorDiv(gameTime, DAY);
    }

    /** 这一天来袭开始的游戏刻。 */
    public static long raidTime(long day) {
        return day * DAY + RAID_HOUR;
    }

    public static int reserveCap(int settlementClass) {
        return RESERVE_CAP[Math.clamp(settlementClass, 0, RESERVE_CAP.length - 1)];
    }

    /** 等级分数换成等级（0–3：C、B、A、S）。 */
    public static int settlementClass(double score) {
        int grade = 0;
        while (grade < CLASS_SCORE.length && score >= CLASS_SCORE[grade]) {
            grade++;
        }
        return grade;
    }

    public static String classLetter(int settlementClass) {
        return CLASS_LETTERS[Math.clamp(settlementClass, 0, CLASS_LETTERS.length - 1)];
    }

    /** 每天来袭的概率：威胁 40 以下不来，到 100 一定来。 */
    public static double raidChance(double threat) {
        if (threat >= 100) {
            return 1;
        }
        return Math.max(0, threat - 40) / 200;
    }

    /** 来几架战机：3 架起，等级越高、威胁满了越多，最多 6 架。 */
    public static int fighters(int settlementClass, double threat) {
        return Math.clamp(3 + settlementClass + (threat >= 100 ? 1 : 0), 3, 6);
    }

    /** 来袭的强弱，和防御值比。 */
    public static double strength(int fighters, double threat) {
        return 15.0 * fighters + threat / 5;
    }

    /** 守住了给的信用点。 */
    public static int raidReward(int fighters) {
        return 250 + 150 * fighters;
    }

    /** 不在场时按防御值算：防御值乘上 0.75–1.25 的运气，不小于来袭的强弱就算守住。{@code luck} 是 0–1。 */
    public static boolean defended(double defense, double strength, double luck) {
        return defense * (0.75 + 0.5 * luck) >= strength;
    }

    /** 鱼塘一天出几条鱼。 */
    public static int fishPerDay(int grade) {
        return (int) Math.round(2 * BuildingType.factor(grade));
    }

    /** 瞭望塔一天出几份导航数据。 */
    public static int navDataPerDay(int grade) {
        return (int) Math.round(BuildingType.factor(grade));
    }

    /** 幸福一天往基准值靠多少：差距的四分之一，至少 1。 */
    public static double drift(double happiness, double target) {
        double gap = target - happiness;
        if (Math.abs(gap) < 1) {
            return gap;
        }
        double step = gap / 4;
        return Math.abs(step) < 1 ? Math.signum(gap) : step;
    }
}
