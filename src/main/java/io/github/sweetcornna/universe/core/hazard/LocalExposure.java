package io.github.sweetcornna.universe.core.hazard;

import java.util.EnumMap;
import java.util.Map;

import io.github.sweetcornna.universe.core.galaxy.Hazard;

/**
 * 玩家身边的局部危害（ADR-0034 §六）在这一刻加给环境的东西：间歇泉的水汽柱照星球扣防护（加一份强度），
 * 孢子云里毒性防护掉得快几倍（乘一个倍数）。算进 {@link io.github.sweetcornna.universe.core.survival.Environment#severity}。
 *
 * @param extra   每种危害额外加的强度；星球本来没有这种危害也算
 * @param factors 每种危害的强度再乘的倍数
 */
public record LocalExposure(Map<Hazard, Double> extra, Map<Hazard, Double> factors) {
    public static final LocalExposure NONE = new LocalExposure(Map.of(), Map.of());

    public LocalExposure {
        extra = extra.isEmpty() ? Map.of() : Map.copyOf(extra);
        factors = factors.isEmpty() ? Map.of() : Map.copyOf(factors);
    }

    /** 再加一份强度（几处叠在一起时相加）。 */
    public LocalExposure plus(Hazard hazard, double severity) {
        if (severity <= 0) {
            return this;
        }
        Map<Hazard, Double> map = new EnumMap<>(Hazard.class);
        map.putAll(this.extra);
        map.merge(hazard, severity, Double::sum);
        return new LocalExposure(map, this.factors);
    }

    /** 强度乘一个倍数（几处叠在一起时取大的，孢子云叠两团也只是 4 倍）。 */
    public LocalExposure times(Hazard hazard, double factor) {
        if (factor <= 1) {
            return this;
        }
        Map<Hazard, Double> map = new EnumMap<>(Hazard.class);
        map.putAll(this.factors);
        map.merge(hazard, factor, Math::max);
        return new LocalExposure(this.extra, map);
    }

    public boolean isEmpty() {
        return this.extra.isEmpty() && this.factors.isEmpty();
    }

    /** 把星球和别处来的强度 {@code base} 算上局部的。 */
    public double apply(Hazard hazard, double base) {
        return (base + this.extra.getOrDefault(hazard, 0.0)) * this.factors.getOrDefault(hazard, 1.0);
    }

    /** 这种危害局部有额外的强度（星球本来没有也会掉防护）。 */
    public boolean adds(Hazard hazard) {
        return this.extra.containsKey(hazard);
    }
}
