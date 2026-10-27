package io.github.sweetcornna.universe.core.chem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 一份大气的成分：各种气体的摩尔分数，加起来是 1（ADR-0036 §二）。没有大气时为空。
 */
public final class AtmosphereMix {
    public static final AtmosphereMix EMPTY = new AtmosphereMix(new EnumMap<>(Gas.class));

    private final Map<Gas, Double> fractions;

    private AtmosphereMix(EnumMap<Gas, Double> fractions) {
        this.fractions = Collections.unmodifiableMap(fractions);
    }

    /** 按给的量（摩尔数或任意比例）归一化；非正的量去掉。 */
    public static AtmosphereMix of(Map<Gas, Double> amounts) {
        double total = 0;
        for (double amount : amounts.values()) {
            if (amount > 0) {
                total += amount;
            }
        }
        EnumMap<Gas, Double> fractions = new EnumMap<>(Gas.class);
        if (total <= 0) {
            return EMPTY;
        }
        for (Map.Entry<Gas, Double> entry : amounts.entrySet()) {
            if (entry.getValue() > 0) {
                fractions.put(entry.getKey(), entry.getValue() / total);
            }
        }
        return new AtmosphereMix(fractions);
    }

    public boolean isEmpty() {
        return this.fractions.isEmpty();
    }

    /** 这种气体的摩尔分数，0–1。 */
    public double fraction(Gas gas) {
        return this.fractions.getOrDefault(gas, 0.0);
    }

    /** 分压，单位和 {@code pressure} 一样。 */
    public double partial(Gas gas, double pressure) {
        return this.fraction(gas) * pressure;
    }

    /** 有毒气体（{@link Gas#toxic()}）加起来的分数。 */
    public double toxicFraction() {
        double sum = 0;
        for (Map.Entry<Gas, Double> entry : this.fractions.entrySet()) {
            if (entry.getKey().toxic()) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    /**
     * 有毒气体的接触指数：每种有毒气体的分压折成一个大气压下的 ppm，除以它的限值，加起来（NIOSH 和 ACGIH 对混合物的算法：
     * Σ Cᵢ/Lᵢ ≤ 1 才算安全）。大于 1 就是有毒。按分压算，所以往大气里掺别的气体冲淡不了，只有把毒气去掉才行。
     *
     * @param pressure 总气压（标准大气压）
     */
    public double exposureIndex(double pressure) {
        double sum = 0;
        for (Map.Entry<Gas, Double> entry : this.fractions.entrySet()) {
            Gas gas = entry.getKey();
            if (gas.toxic() && gas.exposureLimit() > 0) {
                sum += entry.getValue() * pressure * 1e6 / gas.exposureLimit();
            }
        }
        return sum;
    }

    /** 这种气体的分压折成一个大气压下的浓度（ppm）。 */
    public double ppmEquivalent(Gas gas, double pressure) {
        return this.fraction(gas) * pressure * 1e6;
    }

    /** 所有成分，按分数从大到小。 */
    public List<Map.Entry<Gas, Double>> sorted() {
        List<Map.Entry<Gas, Double>> list = new ArrayList<>(this.fractions.entrySet());
        list.sort(Map.Entry.<Gas, Double>comparingByValue().reversed());
        return list;
    }

    /** 分数最大的几种。 */
    public List<Map.Entry<Gas, Double>> top(int count) {
        List<Map.Entry<Gas, Double>> sorted = this.sorted();
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    /** 只读的表。 */
    public Map<Gas, Double> fractions() {
        return this.fractions;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AtmosphereMix mix && mix.fractions.equals(this.fractions);
    }

    @Override
    public int hashCode() {
        return this.fractions.hashCode();
    }

    @Override
    public String toString() {
        return "AtmosphereMix" + this.fractions;
    }
}
