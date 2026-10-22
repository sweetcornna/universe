package io.github.sweetcornna.universe.core.darkfog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 先修哪些（ADR-0028 §四）：范围内掉血最多的先修，一样多时先修近的。战场分析基地和建造无人机都按这个排。
 */
public final class RepairQueue {
    /**
     * 一个待修的设施。
     *
     * @param key        适配层用的编号（方块位置）
     * @param distanceSq 离修理的人（基地、玩家）的距离的平方
     * @param ratio      还剩的耐久比例
     */
    public record Candidate(long key, double distanceSq, float ratio) {
    }

    private static final Comparator<Candidate> ORDER = Comparator.comparingDouble(Candidate::ratio).thenComparingDouble(Candidate::distanceSq)
            .thenComparingLong(Candidate::key);

    private RepairQueue() {
    }

    /** 范围（距离的平方不超过 {@code rangeSq}）内最多 {@code max} 个，按先修的排在前面。 */
    public static List<Candidate> pick(List<Candidate> candidates, double rangeSq, int max) {
        List<Candidate> inRange = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.distanceSq() <= rangeSq) {
                inRange.add(candidate);
            }
        }
        inRange.sort(ORDER);
        return inRange.size() > max ? List.copyOf(inRange.subList(0, Math.max(0, max))) : inRange;
    }
}
