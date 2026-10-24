package io.github.sweetcornna.universe.core.mecha.fleet;

import java.util.List;

/**
 * 机甲的编队（ADR-0033 §一）：几个编队、每个几格、开没开，跟着机甲时排在哪，打谁，停着时怎么修。
 *
 * <p>编队按序号排：先是地面编队（{@link #GROUND_MAX} 个，每个 {@link #GROUND_SLOTS} 格），后面是太空舰队
 * （{@link #SPACE_MAX} 个，每个 {@link #SPACE_SLOTS} 格）。向量都是 {x, y, z} 的 double 数组，单位是格。
 */
public final class Squadrons {
    public static final int GROUND_MAX = 3;
    public static final int GROUND_SLOTS = 6;
    public static final int SPACE_MAX = 2;
    public static final int SPACE_SLOTS = 3;
    public static final int COUNT = GROUND_MAX + SPACE_MAX;
    /** 所有编队的格子加起来。 */
    public static final int TOTAL_SLOTS = GROUND_MAX * GROUND_SLOTS + SPACE_MAX * SPACE_SLOTS;
    /** 开第一个地面编队的科技。 */
    public static final String GROUND_TECH = "ground_squadron";
    /** 开第一个太空舰队的科技。 */
    public static final String SPACE_TECH = "space_fleet";

    /** 离机甲多远以外的不追了，回来（格）。 */
    public static final double GROUND_LEASH = 48;
    public static final double SPACE_LEASH = 160;
    /** 找机甲多远以内的黑雾打（格）。 */
    public static final double GROUND_ENGAGE = 24;
    public static final double SPACE_ENGAGE = 96;
    /** 机甲最近这么多刻里伤过的目标优先打。 */
    public static final int RECENT_HIT = 100;
    /** 出动着的编队补员：隔这么多刻放出一架。 */
    public static final int REPLENISH_INTERVAL = 60;
    /** 核心没电这么多刻还没回来的，自动召回。 */
    public static final int STARVE_RECALL = 200;
    /** 离机甲比拴绳远这么多倍（掉队、机甲传送走了）就直接飞到机甲身边。 */
    public static final double TELEPORT_FACTOR = 1.5;

    private Squadrons() {
    }

    /** 这个编队是太空舰队。 */
    public static boolean space(int squadron) {
        return squadron >= GROUND_MAX;
    }

    /** 编队的格数。 */
    public static int slots(int squadron) {
        return space(squadron) ? SPACE_SLOTS : GROUND_SLOTS;
    }

    /** 编队在同类里排第几（地面编队 0–2，太空舰队 0–1）。 */
    public static int ordinal(int squadron) {
        return space(squadron) ? squadron - GROUND_MAX : squadron;
    }

    /** 第 squadron 个编队的第 slot 格在所有格子里排第几。 */
    public static int flatIndex(int squadron, int slot) {
        return space(squadron) ? GROUND_MAX * GROUND_SLOTS + (squadron - GROUND_MAX) * SPACE_SLOTS + slot : squadron * GROUND_SLOTS + slot;
    }

    /** {@link #flatIndex} 反过来：{squadron, slot}。 */
    public static int[] unflatten(int index) {
        int ground = GROUND_MAX * GROUND_SLOTS;
        if (index < ground) {
            return new int[] {index / GROUND_SLOTS, index % GROUND_SLOTS};
        }
        return new int[] {GROUND_MAX + (index - ground) / SPACE_SLOTS, (index - ground) % SPACE_SLOTS};
    }

    /** 开了几个地面编队：研究"地面编队"开第一个，"编队扩充"每级再开一个。 */
    public static int groundOpen(boolean researched, int expansion) {
        return researched ? 1 + Math.clamp(expansion, 0, GROUND_MAX - 1) : 0;
    }

    /** 开了几个太空舰队：研究"太空舰队"开第一个，"舰队扩充"一级再开一个。 */
    public static int spaceOpen(boolean researched, int expansion) {
        return researched ? 1 + Math.clamp(expansion, 0, SPACE_MAX - 1) : 0;
    }

    /** 这个编队开了没有。 */
    public static boolean open(int squadron, int groundOpen, int spaceOpen) {
        return space(squadron) ? squadron - GROUND_MAX < spaceOpen : squadron < groundOpen;
    }

    public static double leash(boolean space) {
        return space ? SPACE_LEASH : GROUND_LEASH;
    }

    public static double engage(boolean space) {
        return space ? SPACE_ENGAGE : GROUND_ENGAGE;
    }

    /**
     * 跟着机甲时排在哪（相对机甲的脚下）。地面编队排在背后一段弧上：第一个编队半径 3 格、2 格高，往外一个编队远 1.5 格、
     * 高半格，六架从左后到右后摆开 160°。太空舰队排在后面：一排三架，第二个舰队在第一个后面、高一点。
     *
     * @param yaw   机甲（或者坐着的飞船）朝的方向（度，原版的约定）
     * @param scale 太空舰队按飞船的大小拉开（没坐船是 1）
     */
    public static double[] formation(int squadron, int slot, float yaw, double scale) {
        double theta = Math.toRadians(yaw);
        if (!space(squadron)) {
            double radius = 3 + 1.5 * squadron;
            double height = 2 + 0.5 * squadron;
            double a = theta + Math.toRadians((slot - (GROUND_SLOTS - 1) / 2.0) * 32);
            // 正后方转 a：朝向 θ 的前方是 (-sin θ, cos θ)，后方转过 a 就是 (sin(θ + a), -cos(θ + a))
            return new double[] {Math.sin(a) * radius, height, -Math.cos(a) * radius};
        }
        int fleet = squadron - GROUND_MAX;
        double back = (10 + 8 * fleet) * scale;
        double side = (slot - (SPACE_SLOTS - 1) / 2.0) * 7 * scale;
        double up = 2 + 3 * fleet;
        double[] forward = {-Math.sin(theta), 0, Math.cos(theta)};
        double[] right = {-Math.cos(theta), 0, -Math.sin(theta)};
        return new double[] {-forward[0] * back + right[0] * side, up, -forward[2] * back + right[2] * side};
    }

    /** 跟上队形的速度：离位置越远飞得越快，十格开外两倍。 */
    public static double catchUp(double speed, double distance) {
        return speed * (1 + Math.clamp((distance - 2) / 8, 0, 1));
    }

    /**
     * 停着的单位这一秒修几点：缺多少修多少，但不超过每秒的修理量，也不超过核心里的电够修的。
     *
     * @param missing   缺的生命
     * @param energy    核心里的电（焦）
     * @param perSecond 每秒最多修几点
     */
    public static int repair(int missing, double energy, int perSecond) {
        if (missing <= 0 || perSecond <= 0) {
            return 0;
        }
        int affordable = (int) Math.floor(energy / FleetUnitKind.REPAIR_JOULES_PER_HP);
        return Math.max(0, Math.min(missing, Math.min(perSecond, affordable)));
    }

    /**
     * 能打的东西。
     *
     * @param toOwner  离机甲多远
     * @param toUnit   离这架单位多远
     * @param building 是黑雾的建筑（基地、巢穴的）
     * @param recent   机甲最近 {@link #RECENT_HIT} 刻伤过它
     */
    public record Candidate(double toOwner, double toUnit, boolean building, boolean recent) {
    }

    /**
     * 挑一个打：机甲 {@code engage} 格以内的；建筑只在开着"攻击黑雾建筑"时打；机甲正在打的优先，其余挑离自己最近的。
     *
     * @return 挑中的序号；没有能打的是 -1
     */
    public static int pick(List<Candidate> candidates, double engage, boolean buildings) {
        int best = -1;
        boolean bestRecent = false;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.toOwner() > engage || c.building() && !buildings) {
                continue;
            }
            boolean better = c.recent() && !bestRecent || c.recent() == bestRecent && c.toUnit() < bestDistance;
            if (better) {
                best = i;
                bestRecent = c.recent();
                bestDistance = c.toUnit();
            }
        }
        return best;
    }

    /**
     * 交战时站在哪：在目标和机甲之间、离目标 {@code stand} 格的地方，再绕着目标错开一个角度（每架不同），免得挤在一起。
     * 太远的地方（离机甲超过拴绳）收回到拴绳以内。
     *
     * @param offset 绕着目标错开的角度（弧度）
     * @param lift   比目标高多少
     */
    public static double[] engagePoint(double[] owner, double[] target, double stand, double offset, double lift, double leash) {
        double dx = owner[0] - target[0];
        double dz = owner[2] - target[2];
        double horizontal = Math.hypot(dx, dz);
        double base = horizontal < 1e-6 ? 0 : Math.atan2(dz, dx);
        double angle = base + offset;
        double[] point = {target[0] + Math.cos(angle) * stand, target[1] + lift, target[2] + Math.sin(angle) * stand};
        double[] fromOwner = {point[0] - owner[0], point[1] - owner[1], point[2] - owner[2]};
        double distance = Math.sqrt(fromOwner[0] * fromOwner[0] + fromOwner[1] * fromOwner[1] + fromOwner[2] * fromOwner[2]);
        double limit = leash * 0.9;
        if (distance > limit) {
            double f = limit / distance;
            return new double[] {owner[0] + fromOwner[0] * f, owner[1] + fromOwner[1] * f, owner[2] + fromOwner[2] * f};
        }
        return point;
    }

    /** 这些单位一起每刻耗多少电（面板上显示）。 */
    public static double idleJoules(List<FleetUnitKind> units) {
        double total = 0;
        for (FleetUnitKind kind : units) {
            total += kind.idleJoules();
        }
        return total;
    }
}
