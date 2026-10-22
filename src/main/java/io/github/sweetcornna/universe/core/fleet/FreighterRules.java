package io.github.sweetcornna.universe.core.fleet;

import java.util.List;

import io.github.sweetcornna.universe.core.space.SpaceLayout;
import io.github.sweetcornna.universe.core.tech.Grade;

/**
 * 货船的数字（ADR-0024）。
 */
public final class FreighterRules {
    /** 货船跃迁一次用几个空间翘曲器。 */
    public static final int WARPERS = 3;
    /** 召唤货船的冷却（刻）：5 分钟。 */
    public static final int SUMMON_COOLDOWN = 5 * 60 * 20;
    /** 舰队最多几艘护卫舰。 */
    public static final int MAX_FRIGATES = 12;
    /** 修一艘受损的护卫舰要多少信用点。 */
    public static final long REPAIR_COST = 3000;
    /** 货船停在离空间站多远的地方。 */
    public static final double ANCHOR_DISTANCE = 3000;
    /** 同一个系里的几艘货船错开多远。 */
    public static final double ANCHOR_SPACING = 160;
    /** 锚点离天体表面、恒星至少这么远（格）。 */
    public static final double CLEARANCE = 400;
    /** 召唤货船时停在玩家前方多远。 */
    public static final double SUMMON_DISTANCE = 220;
    /** 档次决定跃迁航程（光年）：C、B、A、S。 */
    static final double[] RANGE = {40, 45, 50, 60};

    private FreighterRules() {
    }

    public static double range(Grade grade) {
        return RANGE[grade.ordinal()];
    }

    /**
     * 货船的锚点：从锚定的星球往空间站的方向再往外 {@link #ANCHOR_DISTANCE}，每艘按序号往旁边错开。
     *
     * @param station 空间站 {x, y, z}
     * @param planet  空间站锚定的星球中心 {x, y, z}
     * @param slot    这个系里的第几艘
     */
    public static double[] anchor(double[] station, double[] planet, int slot) {
        double dx = station[0] - planet[0];
        double dz = station[2] - planet[2];
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-6) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        return along(station, dx / d, dz / d, slot);
    }

    /** 同上，再避开天体和恒星：那个方向上挡着东西就每次转 30° 再找，都挡着就用第一个。 */
    public static double[] anchor(double[] station, double[] planet, int slot, List<SpaceLayout.Body> bodies, double starRadius) {
        double base = Math.atan2(station[2] - planet[2], station[0] - planet[0]);
        double[] first = null;
        for (int i = 0; i < 12; i++) {
            double angle = base + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * Math.PI / 6;
            double[] spot = along(station, Math.cos(angle), Math.sin(angle), slot);
            if (first == null) {
                first = spot;
            }
            if (clear(spot, bodies, starRadius)) {
                return spot;
            }
        }
        return first;
    }

    /** 这个点放得下一艘货船：离每个天体的表面和恒星都够远。 */
    public static boolean clear(double[] spot, List<SpaceLayout.Body> bodies, double starRadius) {
        if (Math.sqrt(spot[0] * spot[0] + spot[1] * spot[1] + spot[2] * spot[2]) < starRadius + CLEARANCE) {
            return false;
        }
        for (SpaceLayout.Body body : bodies) {
            if (body.distanceTo(spot[0], spot[1], spot[2]) < body.radius() + CLEARANCE) {
                return false;
            }
        }
        return true;
    }

    private static double[] along(double[] station, double dx, double dz, int slot) {
        // 往旁边错开：垂直于那个方向，左右交替
        int side = slot % 2 == 0 ? 1 : -1;
        double offset = ((slot + 1) / 2) * ANCHOR_SPACING * side;
        return new double[] {station[0] + dx * ANCHOR_DISTANCE - dz * offset, station[1], station[2] + dz * ANCHOR_DISTANCE + dx * offset};
    }
}
