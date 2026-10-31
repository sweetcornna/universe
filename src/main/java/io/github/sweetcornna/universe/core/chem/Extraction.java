package io.github.sweetcornna.universe.core.chem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 大气提取器（ADR-0036 §三）：现实里先压缩再低温分馏。界面里选一种气体，按气压和这种气体的分数出货。
 *
 * <p>每分钟出 {@code 40 × √气压 × √分数} 个（气压单位是标准大气压）。现实的产量和分数成正比，
 * 这里开平方让千分之几的氩、氦也取得到，一台机器不至于几个小时才出一个。
 */
public final class Extraction {
    /** 分数到这里才能选（千分之一）。 */
    public static final double MIN_FRACTION = 0.001;
    /** 1 个标准大气压、纯的一种气体时每分钟出几个。 */
    public static final double BASE_PER_MINUTE = 40;
    /** 气压按这么多算封顶：再浓也不更快，压缩机吸不进更多。 */
    public static final double MAX_PRESSURE = 10;
    /** 1 个标准大气压以上的用电（J/刻，戴森球计划的 480 kW）。 */
    public static final double BASE_POWER = 24;
    /** 气压每低 e 倍，用电多这么多倍的底数（等温压缩功和 ln(1/气压) 成正比）。 */
    public static final double POWER_PER_E_FOLD = 0.4;
    /** 用电最多是底数的几倍。 */
    public static final double MAX_POWER_FACTOR = 3;

    private Extraction() {
    }

    /** 这份大气里能选的气体，按分数从大到小。 */
    public static List<Gas> collectable(AtmosphereMix mix) {
        List<Gas> gases = new ArrayList<>();
        for (Map.Entry<Gas, Double> entry : mix.sorted()) {
            if (entry.getValue() >= MIN_FRACTION) {
                gases.add(entry.getKey());
            }
        }
        return gases;
    }

    /** 选这种气体时每分钟出几个；取不到为 0。 */
    public static double perMinute(AtmosphereMix mix, Gas gas, double pressure) {
        double fraction = mix.fraction(gas);
        if (fraction < MIN_FRACTION || pressure <= 0) {
            return 0;
        }
        return BASE_PER_MINUTE * Math.sqrt(Math.min(pressure, MAX_PRESSURE)) * Math.sqrt(fraction);
    }

    /** 出一个要几刻；取不到为 {@link Double#POSITIVE_INFINITY}。 */
    public static double ticksPerItem(AtmosphereMix mix, Gas gas, double pressure) {
        double perMinute = perMinute(mix, gas, pressure);
        return perMinute <= 0 ? Double.POSITIVE_INFINITY : 1200 / perMinute;
    }

    /** 用电（J/刻）：1 个大气压以上是底数，越稀越费电，最多 3 倍。 */
    public static double power(double pressure) {
        if (pressure >= 1) {
            return BASE_POWER;
        }
        double factor = pressure <= 0 ? MAX_POWER_FACTOR : 1 + POWER_PER_E_FOLD * Math.log(1 / pressure);
        return BASE_POWER * Math.min(MAX_POWER_FACTOR, factor);
    }
}
