package io.github.sweetcornna.universe.core.space;

import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.galaxy.StarSystem;

/**
 * 跃迁（玩法设计 §5.1、ADR-0012）：射程、能不能在这里跃迁、到了目标恒星系停在哪里。
 *
 * <p>飞船上每装一台跃迁引擎射程多 5 光年、每台 Mk.II 多 10 光年（ADR-0017），底数 5，最多算 4 台（先算 Mk.II）；
 * 每跳一次消耗一个空间翘曲器。离恒星或天体太近时
 * 引力井太深，不能跃迁。到达时从出发地的方向进入目标恒星系，停在朝着来路的那颗行星外面不远，船头对着它。
 */
public final class Warp {
    /** 充能：这么多刻之后离开当前恒星系。 */
    public static final int SPOOL_TICKS = 70;
    /** 到达后的减速、画面恢复。 */
    public static final int ARRIVAL_TICKS = 50;
    /** 最多算几台跃迁引擎。 */
    public static final int MAX_DRIVES = 4;
    /** 离恒星中心至少这么远才能跃迁（格）。 */
    public static final double STAR_CLEARANCE = 14400;
    /** 离天体的大气球外沿至少这么远才能跃迁（格），再加上大气球半径的 {@link #BODY_CLEARANCE_SHARE}。 */
    public static final double BODY_CLEARANCE = 600;
    static final double BODY_CLEARANCE_SHARE = 0.25;
    /** 到达点离那颗行星的大气球外沿多远（格），再加上大气球半径的 {@link #APPROACH_SHARE} 倍：星球看上去二三十度大，脉冲引擎几秒到。 */
    public static final double APPROACH = 3000;
    static final double APPROACH_SHARE = 2.5;
    /** 没有行星时停在离恒星多远（格）。 */
    private static final double EMPTY_ARRIVAL = 48000;

    private Warp() {
    }

    /** 能不能在这里跃迁。 */
    public enum Status {
        OK,
        /** 在恒星的引力井里。 */
        NEAR_STAR,
        /** 离某个行星或卫星太近。 */
        NEAR_BODY
    }

    /** 跃迁射程（光年）；没有跃迁引擎时为 0。 */
    public static double range(int drives) {
        return range(drives, 0);
    }

    /** 装了 {@code drives} 台普通跃迁引擎和 {@code advanced} 台 Mk.II 时的射程（光年）。 */
    public static double range(int drives, int advanced) {
        if (drives + advanced <= 0) {
            return 0;
        }
        int a = Math.min(Math.max(0, advanced), MAX_DRIVES);
        int b = Math.min(Math.max(0, drives), MAX_DRIVES - a);
        return 5 + 10 * a + 5 * b;
    }

    public static boolean inRange(StarSystem from, StarSystem to, int drives) {
        return inReach(from, to, range(drives));
    }

    /** 射程 {@code range} 光年够不够从 {@code from} 跳到 {@code to}。 */
    public static boolean inReach(StarSystem from, StarSystem to, double range) {
        return from.index() != to.index() && from.distanceTo(to) <= range + 1e-9;
    }

    /** 太空维度里的这个位置能不能开始跃迁。 */
    public static Status check(List<SpaceLayout.Body> bodies, double x, double y, double z) {
        if (Math.sqrt(x * x + y * y + z * z) < STAR_CLEARANCE) {
            return Status.NEAR_STAR;
        }
        for (SpaceLayout.Body body : bodies) {
            if (body.distanceTo(x, y, z) < clearance(body)) {
                return Status.NEAR_BODY;
            }
        }
        return Status.OK;
    }

    /** 离这个天体中心至少多远才能跃迁。 */
    public static double clearance(SpaceLayout.Body body) {
        return body.radius() * (1 + BODY_CLEARANCE_SHARE) + BODY_CLEARANCE;
    }

    /** 挡住跃迁的那个天体（{@link Status#NEAR_BODY} 时）。 */
    public static Optional<SpaceLayout.Body> blocker(List<SpaceLayout.Body> bodies, double x, double y, double z) {
        for (SpaceLayout.Body body : bodies) {
            if (body.distanceTo(x, y, z) < clearance(body)) {
                return Optional.of(body);
            }
        }
        return Optional.empty();
    }

    /**
     * 到达点：在来路那一侧挑一颗行星（能着陆的优先），停在它外面 {@link #APPROACH} 格的地方。
     *
     * @param bodies 目标恒星系在太空里的布局
     * @return {x, y, z, 偏航}；偏航按 Minecraft 的习惯，0 朝 +z，船头对着那颗行星（没有行星时对着恒星）
     */
    public static double[] arrival(StarSystem from, StarSystem to, List<SpaceLayout.Body> bodies) {
        // 来的方向：星团里从目标指向出发地，投到黄道面上；正好在正上方或正下方时随便取一个
        double dx = from.x() - to.x();
        double dz = from.z() - to.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-6) {
            dx = 0;
            dz = 1;
        } else {
            dx /= length;
            dz /= length;
        }
        SpaceLayout.Body target = approachTarget(bodies, dx, dz);
        double cx = target == null ? 0 : target.x();
        double cz = target == null ? 0 : target.z();
        double distance = target == null ? EMPTY_ARRIVAL : target.radius() * (1 + APPROACH_SHARE) + APPROACH;
        double x = cx + dx * distance;
        double z = cz + dz * distance;
        // 离别的天体（卫星）或者恒星太近就再往外挪
        for (int attempt = 0; attempt < 64 && check(bodies, x, 0, z) != Status.OK; attempt++) {
            distance += 1500;
            x = cx + dx * distance;
            z = cz + dz * distance;
        }
        double yaw = Math.toDegrees(Math.atan2(dx, -dz));
        return new double[] {x, 0, z, yaw};
    }

    /** 来路那一侧的行星：越正对来路越好，能着陆的优先；没有行星时为空。 */
    static SpaceLayout.Body approachTarget(List<SpaceLayout.Body> bodies, double dx, double dz) {
        SpaceLayout.Body best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (SpaceLayout.Body body : bodies) {
            double length = Math.sqrt(body.x() * body.x() + body.z() * body.z());
            if (body.planet().moon() || length < 1e-6) {
                continue;
            }
            double score = (body.x() * dx + body.z() * dz) / length + (body.planet().landable() ? 0.6 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = body;
            }
        }
        return best;
    }
}
