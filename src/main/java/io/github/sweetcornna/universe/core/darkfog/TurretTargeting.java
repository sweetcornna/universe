package io.github.sweetcornna.universe.core.darkfog;

import java.util.List;

/**
 * 炮塔挑目标（ADR-0028 §五、§八）：射程内、看得见的黑雾里，先打正在打设施的，再打最近的；聚爆炮只打地面的，
 * 高空的（下来轰炸的枪骑）只有导弹炮塔、等离子炮塔打。
 * 换目标有惯性：现在的目标还能打就接着打，免得两个差不多远的来回切。
 */
public final class TurretTargeting {
    private TurretTargeting() {
    }

    /**
     * 一个候选目标。
     *
     * @param distance          离炮塔多远
     * @param inRange           在射程内（导弹借信号塔的范围也算；高空目标按 {@link TurretKind#reach} 的高空射程）
     * @param visible           看得见（不要视线的炮塔填 true）
     * @param grounded          地面目标
     * @param attackingFacility 正在打设施
     * @param current           就是现在的目标
     * @param highAltitude      高空目标
     */
    public record Candidate(double distance, boolean inRange, boolean visible, boolean grounded, boolean attackingFacility, boolean current,
            boolean highAltitude) {
        /** 不在高空的目标。 */
        public Candidate(double distance, boolean inRange, boolean visible, boolean grounded, boolean attackingFacility, boolean current) {
            this(distance, inRange, visible, grounded, attackingFacility, current, false);
        }
    }

    /** 这种炮塔能不能打这个目标。 */
    public static boolean eligible(TurretKind turret, Candidate candidate) {
        return candidate.inRange() && candidate.visible() && (!turret.groundOnly() || candidate.grounded())
                && (!candidate.highAltitude() || turret.antiAir());
    }

    /**
     * 挑一个目标。
     *
     * @return 下标；一个能打的都没有是 -1
     */
    public static int choose(TurretKind turret, List<Candidate> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            // 现在的目标还在打设施，或者没有别的在打设施，就不换
            if (candidate.current() && eligible(turret, candidate)
                    && (candidate.attackingFacility() || candidates.stream().noneMatch(c -> c.attackingFacility() && eligible(turret, c)))) {
                return i;
            }
        }
        int best = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (!eligible(turret, candidate)) {
                continue;
            }
            if (best < 0 || better(candidate, candidates.get(best))) {
                best = i;
            }
        }
        return best;
    }

    private static boolean better(Candidate a, Candidate b) {
        if (a.attackingFacility() != b.attackingFacility()) {
            return a.attackingFacility();
        }
        return a.distance() < b.distance();
    }
}
