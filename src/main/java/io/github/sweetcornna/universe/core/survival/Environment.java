package io.github.sweetcornna.universe.core.survival;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Hazard;
import io.github.sweetcornna.universe.core.galaxy.Planet;

/**
 * 玩家此刻所处的环境（玩法设计 §3.2）：能不能呼吸、有哪些危害、各有多强，以及会减缓、加快消耗的条件。
 *
 * @param breathable 大气能直接呼吸
 * @param hazards    每种危害的强度（1 是标准强度，没有的危害不在表里）
 * @param sheltered  头顶有遮挡（室内、洞穴）
 * @param storm      0 晴、1 雨雪、2 雷暴或沙尘暴
 * @param nearHeat   旁边有火、岩浆、亮着的炉子：寒冷消耗变慢
 * @param nearCold   旁边有冰雪：高温消耗变慢
 * @param supplied   在飞船里：舱内有生命维持，氧气和防护都会补满
 * @param pressure   在深水里（水面以下 40 格更深，ADR-0026）：氧气掉得快一半
 */
public record Environment(boolean breathable, Map<Hazard, Double> hazards, boolean sheltered, int storm,
        boolean nearHeat, boolean nearCold, boolean supplied, boolean pressure) {
    /** 主世界这样的安全环境。 */
    public static final Environment SAFE = new Environment(true, Map.of(), false, 0, false, false, false);

    public Environment {
        hazards = hazards.isEmpty() ? Map.of() : Map.copyOf(hazards);
    }

    public Environment(boolean breathable, Map<Hazard, Double> hazards, boolean sheltered, int storm, boolean nearHeat, boolean nearCold,
            boolean supplied) {
        this(breathable, hazards, sheltered, storm, nearHeat, nearCold, supplied, false);
    }

    public Environment withPressure(boolean pressure) {
        return new Environment(this.breathable, this.hazards, this.sheltered, this.storm, this.nearHeat, this.nearCold, this.supplied,
                pressure);
    }

    public boolean exposedTo(Hazard hazard) {
        return !this.supplied && this.hazards.containsKey(hazard);
    }

    /** 需要生命维持：不能呼吸，又不在飞船里。 */
    public boolean needsOxygen() {
        return !this.breathable && !this.supplied;
    }

    /**
     * 星球上危害的标准强度：寒冷、高温按平均温度离 ±60 °C 有多远加重，最轻 0.6、最重 2；毒性、辐射为 1。
     */
    public static Map<Hazard, Double> hazardsOf(Planet planet) {
        Map<Hazard, Double> result = new EnumMap<>(Hazard.class);
        double celsius = planet.temperatureCelsius();
        for (Hazard hazard : planet.hazards()) {
            double severity = switch (hazard) {
                case COLD -> 1 + (-celsius - 60) / 150;
                case HEAT -> 1 + (celsius - 60) / 150;
                default -> 1;
            };
            result.put(hazard, Math.clamp(severity, 0.6, 2.0));
        }
        return result;
    }
}
