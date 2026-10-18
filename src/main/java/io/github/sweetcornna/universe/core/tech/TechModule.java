package io.github.sweetcornna.universe.core.tech;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 一个技术模块（ADR-0014）：种类、品级，以及它每项加成的具体数值（和 {@link ModuleType#stats()} 一一对应）。
 */
public record TechModule(ModuleType type, Grade grade, List<Double> values) {
    public TechModule {
        values = List.copyOf(values);
        if (values.size() != type.stats().size()) {
            throw new IllegalArgumentException(type + " needs " + type.stats().size() + " values, got " + values.size());
        }
    }

    /** 在品级的范围里随机出数值；比例保留三位小数，护盾保留一位。 */
    public static TechModule roll(ModuleType type, Grade grade, Rng rng) {
        List<Double> values = new ArrayList<>();
        for (Stat stat : type.stats()) {
            double value = rng.range(stat.min(grade), stat.max(grade));
            values.add(round(value, stat.absolute() ? 10 : 1000));
        }
        return new TechModule(type, grade, values);
    }

    /** 品级范围的中间值，给没有随机过的物品（创造模式、旧存档）用。 */
    public static TechModule typical(ModuleType type, Grade grade) {
        List<Double> values = new ArrayList<>();
        for (Stat stat : type.stats()) {
            values.add(round((stat.min(grade) + stat.max(grade)) / 2, stat.absolute() ? 10 : 1000));
        }
        return new TechModule(type, grade, values);
    }

    public double value(Stat stat) {
        int index = this.type.stats().indexOf(stat);
        return index < 0 ? 0 : this.values.get(index);
    }

    /**
     * 这个数值在本品级范围里的位置（0 最差、1 最好），多项取平均。界面上用来提示"这个模块出得好不好"。
     */
    public double quality() {
        double total = 0;
        for (int i = 0; i < this.values.size(); i++) {
            Stat stat = this.type.stats().get(i);
            double span = stat.max(this.grade) - stat.min(this.grade);
            total += span <= 0 ? 1 : Math.clamp((this.values.get(i) - stat.min(this.grade)) / span, 0, 1);
        }
        return total / this.values.size();
    }

    private static double round(double value, double scale) {
        return Math.round(value * scale) / scale;
    }
}
