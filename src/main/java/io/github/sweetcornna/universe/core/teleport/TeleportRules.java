package io.github.sweetcornna.universe.core.teleport;

import java.util.Collection;

/**
 * 基地信标和传送器的规则（ADR-0017）：传送一次要的能量、充电速度、基地的默认编号、离传送器多远能用。
 */
public final class TeleportRules {
    /** 传送一次要充满的能量（焦耳；戴森球计划的单位换算下是 12 MJ）。 */
    public static final long CHARGE = 12_000;
    /** 每刻最多充进多少（60 焦/刻，10 秒充满）。 */
    public static final long CHARGE_RATE = 60;
    /** 离传送器多少格以内才能用（中心到中心）。 */
    public static final double USE_DISTANCE = 6;
    /** 宠物跟着传送的范围（格）。 */
    public static final double PET_RADIUS = 24;

    private TeleportRules() {
    }

    /** 一个主人在一颗星球上的下一个基地编号：没用过的最小正整数。 */
    public static int nextBaseNumber(Collection<Integer> used) {
        int number = 1;
        while (used.contains(number)) {
            number++;
        }
        return number;
    }

    /** 这一刻要充多少电：没满时按充电速度要，最后一刻只要差的那点。 */
    public static long demand(long stored) {
        return stored >= CHARGE ? 0 : Math.min(CHARGE_RATE, CHARGE - stored);
    }

    /** 电网结算后的电量：按满足率充进去，不超过满电。 */
    public static long charge(long stored, double satisfaction) {
        long gained = Math.round(demand(stored) * Math.max(0, Math.min(1, satisfaction)));
        return Math.min(CHARGE, stored + gained);
    }

    public static boolean inReach(double distanceSquared) {
        return distanceSquared <= USE_DISTANCE * USE_DISTANCE;
    }
}
