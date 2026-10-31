package io.github.sweetcornna.universe.terraform;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;
import io.github.sweetcornna.universe.core.survival.Environment;
import io.github.sweetcornna.universe.core.terraform.EffectiveEnvironment;
import io.github.sweetcornna.universe.core.terraform.Terraform;

/** 星球改造适配层里不碰世界的几条规矩（ADR-0036 §六），单独放着好做单元测试。 */
public final class TerraformRules {
    /** 一座有水的生态播种站给星球加多少灌溉：四座浇满（{@link io.github.sweetcornna.universe.core.terraform.Biosphere#carryingCapacity}）。 */
    public static final double IRRIGATION_PER_STATION = 0.25;
    /** 播种站周围绿化的半径（格）：刚播下时这么大。 */
    public static final int GREENING_MIN_RADIUS = 4;
    /** 绿化半径最大多少格。 */
    public static final int GREENING_MAX_RADIUS = 32;
    /** 生物量到这么多时绿化半径到顶。 */
    public static final double GREENING_FULL_BIOMASS = 0.25;
    /** 生物量到这么多以后，地衣底下的土长成苔藓，岩石上的地衣有的换成苔藓。 */
    public static final double MOSS_BIOMASS = 0.01;

    private TerraformRules() {
    }

    /** 这颗星球上有几座播种站在浇水时的灌溉，0–1。 */
    public static double irrigation(int stations) {
        return Math.min(1, Math.max(0, stations) * IRRIGATION_PER_STATION);
    }

    /**
     * 播种站周围绿化的半径：随星球的生物量开平方变大，刚播下 {@value #GREENING_MIN_RADIUS} 格，生物量到
     * {@value #GREENING_FULL_BIOMASS} 时 {@value #GREENING_MAX_RADIUS} 格；没有生物量为 0。
     */
    public static int greeningRadius(double biomass) {
        if (!(biomass > 0)) {
            return 0;
        }
        double t = Math.min(1, Math.sqrt(biomass / GREENING_FULL_BIOMASS));
        return (int) Math.floor(GREENING_MIN_RADIUS + (GREENING_MAX_RADIUS - GREENING_MIN_RADIUS) * t);
    }

    /**
     * 现用环境的危害和强度（{@link Environment#hazardsOf}）。改造到大气柱挡得住宇宙线、但在致密恒星旁边的星球，
     * 辐射还在，只剩一半（{@link EffectiveEnvironment#radiationScale}）；没改造过的星球和以前一样。
     */
    public static Map<Hazard, Double> hazards(EffectiveEnvironment environment) {
        Map<Hazard, Double> hazards = Environment.hazardsOf(environment.planet());
        double scale = environment.radiationScale();
        if (!hazards.containsKey(Hazard.RADIATION) || scale >= 1) {
            return hazards;
        }
        Map<Hazard, Double> scaled = new EnumMap<>(hazards);
        scaled.computeIfPresent(Hazard.RADIATION, (hazard, severity) -> severity * scale);
        return scaled;
    }

    /**
     * 到了阶段时全服提示用哪一句：星球本来有的危害没了就说那种危害没了（"寒冷危害消失了"），本来就没有的说环境变成什么样了。
     */
    public static String stageKey(Planet base, Terraform.Stage stage, boolean compactStar) {
        return switch (stage) {
            case TEMPERATE -> base.hasHazard(Hazard.COLD) ? "cold_gone" : base.hasHazard(Hazard.HEAT) ? "heat_gone" : "temperate";
            case CLEAN_AIR -> base.hasHazard(Hazard.TOXIC) ? "toxic_gone" : "clean_air";
            case SHIELDED -> !base.hasHazard(Hazard.RADIATION) ? "shielded" : compactStar ? "radiation_halved" : "radiation_gone";
            case BREATHABLE -> "breathable";
        };
    }
}
