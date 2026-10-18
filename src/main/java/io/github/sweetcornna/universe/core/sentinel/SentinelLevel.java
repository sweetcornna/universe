package io.github.sweetcornna.universe.core.sentinel;

import java.util.List;

import io.github.sweetcornna.universe.core.galaxy.GalaxyGenerator;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 星球的守卫强度（ADR-0014），由星球种子决定。母星系和没有生命的星球多为无或稀少；奇异、辐射星球的守卫更警觉。
 *
 * @param patrols     每个玩家附近保持几架巡逻的守卫机
 * @param alertFactor 违规行为提高警戒值的倍数
 */
public enum SentinelLevel {
    NONE("无", "None", 0, 0),
    LOW("稀少", "Low", 1, 0.5),
    NORMAL("正常", "Normal", 2, 1.0),
    AGGRESSIVE("警觉", "Aggressive", 3, 1.5);

    private final String nameZh;
    private final String nameEn;
    private final int patrols;
    private final double alertFactor;

    SentinelLevel(String nameZh, String nameEn, int patrols, double alertFactor) {
        this.nameZh = nameZh;
        this.nameEn = nameEn;
        this.patrols = patrols;
        this.alertFactor = alertFactor;
    }

    public String nameZh() {
        return this.nameZh;
    }

    public String nameEn() {
        return this.nameEn;
    }

    public int patrols() {
        return this.patrols;
    }

    public double alertFactor() {
        return this.alertFactor;
    }

    public boolean present() {
        return this != NONE;
    }

    /** 这颗星球的守卫强度。 */
    public static SentinelLevel of(Planet planet) {
        if (!planet.landable()) {
            return NONE;
        }
        Rng rng = Rng.of(Seeds.derive(planet.seed(), "sentinels"));
        double[] weights;
        if (planet.id().system() == GalaxyGenerator.HOME_SYSTEM) {
            weights = new double[] {0.45, 0.55, 0, 0};
        } else if (planet.ecology().lifeless()) {
            weights = new double[] {0.4, 0.4, 0.2, 0};
        } else {
            weights = switch (planet.type()) {
                case EXOTIC, IRRADIATED -> new double[] {0.05, 0.2, 0.4, 0.35};
                case HABITABLE -> new double[] {0.15, 0.35, 0.35, 0.15};
                default -> new double[] {0.1, 0.3, 0.4, 0.2};
            };
        }
        double[] w = weights;
        return rng.weighted(List.of(values()), level -> w[level.ordinal()]);
    }
}
