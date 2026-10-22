package io.github.sweetcornna.universe.core.tech;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 一组装好的模块合起来的加成（ADR-0014）。同一种模块最多 {@link ModuleType#MAX_STACK} 个生效，多出来的取数值
 * 最高的几个；不同模块的同一项加成直接相加。
 */
public final class TechBonuses {
    public static final TechBonuses NONE = new TechBonuses(Map.of());
    /** 冷却缩短最多到原来的四成。 */
    static final double MIN_COOLDOWN = 0.4;
    /** 分析时间最多缩短到原来的四分之一。 */
    static final double MIN_ANALYSIS = 0.25;

    private final Map<Stat, Double> totals;

    private TechBonuses(Map<Stat, Double> totals) {
        this.totals = totals;
    }

    public static TechBonuses of(Collection<TechModule> modules) {
        if (modules.isEmpty()) {
            return NONE;
        }
        Map<ModuleType, List<TechModule>> byType = new EnumMap<>(ModuleType.class);
        for (TechModule module : modules) {
            byType.computeIfAbsent(module.type(), type -> new ArrayList<>()).add(module);
        }
        Map<Stat, Double> totals = new EnumMap<>(Stat.class);
        for (Map.Entry<ModuleType, List<TechModule>> entry : byType.entrySet()) {
            List<TechModule> list = entry.getValue();
            list.sort(Comparator.comparing(TechModule::grade).thenComparingDouble(TechModule::quality).reversed());
            for (TechModule module : list.subList(0, Math.min(ModuleType.MAX_STACK, list.size()))) {
                for (Stat stat : module.type().stats()) {
                    totals.merge(stat, module.value(stat), Double::sum);
                }
            }
        }
        return new TechBonuses(totals);
    }

    /** 再加上一些不来自模块的加成（比如多功能工具类型的专长，ADR-0026）。 */
    public TechBonuses with(Map<Stat, Double> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        Map<Stat, Double> totals = new EnumMap<>(Stat.class);
        totals.putAll(this.totals);
        extra.forEach((stat, value) -> totals.merge(stat, value, Double::sum));
        return new TechBonuses(totals);
    }

    public double get(Stat stat) {
        return this.totals.getOrDefault(stat, 0.0);
    }

    public boolean isEmpty() {
        return this.totals.isEmpty();
    }

    /** "降低消耗"类加成换算成的消耗倍数：{@code 1 / (1 + 加成)}。 */
    public double reduction(Stat stat) {
        return 1 / (1 + this.get(stat));
    }

    /** "提高"类加成换算成的倍数：{@code 1 + 加成}。 */
    public double boost(Stat stat) {
        return 1 + this.get(stat);
    }

    /** 扫描冷却的倍数。 */
    public double scanCooldown() {
        return Math.max(MIN_COOLDOWN, 1 - this.get(Stat.SCAN_COOLDOWN));
    }

    /** 分析时间的倍数。 */
    public double analysisTime() {
        return Math.max(MIN_ANALYSIS, 1 - this.get(Stat.ANALYSIS));
    }

    /** 每项加成的合计，按 {@link Stat} 的顺序。 */
    public Map<Stat, Double> totals() {
        return Collections.unmodifiableMap(this.totals);
    }
}
