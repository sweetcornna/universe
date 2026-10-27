package io.github.sweetcornna.universe.core.microbe;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 一种微生物（ADR-0036 §五）。外星菌由星球的种子派生（{@link Microbiome}），母星的是固定的一组真实的菌（{@link EarthMicrobes}）。
 *
 * <p>温度照真实的分类：嗜冷菌 258–300 K 上下，常温菌最适 295–315 K，嗜热菌最适 330–350 K，超嗜热菌最适 360–380 K。
 * 生命的两头照现实的纪录：最低 −15 °C（盐生钝顶浮球菌 Planococcus halocryophilus，Mykytczuk 等 2013），
 * 最高 122 °C（甲烷嗜高热菌 Methanopyrus kandleri 116 株，Takai 等 2008）。
 *
 * <p>温度和生长速度的关系用 Rosso 等（1993）的基数温度模型（CTMI），要求最适温度比最低、最高的中点高
 * （真实的曲线都是这样：往最高温度那一侧掉得陡）。
 *
 * @param key             稳定的键：外星菌是"星球路径#序号"（{@code s3/p1#2}），母星的是 {@code earth:属_种}
 * @param nameZh          中文名（克拉尔嗜热球菌）
 * @param nameEn          拉丁双名（Thermococcus kelarensis）
 * @param metabolism      代谢
 * @param morphology      形态
 * @param genes           天生的基因，含代谢的标志基因
 * @param minK            能长的最低温度（K）
 * @param optK            最适温度（K）
 * @param maxK            能长的最高温度（K）
 * @param doublingHours   最适条件下翻一倍要几小时（现实时间），游戏里按 {@link BioTime} 压缩
 * @param productivity    产率的倍数，1 是一般
 * @param sources         能从哪些地方采到
 */
public record MicrobeStrain(String key, String nameZh, String nameEn, Metabolism metabolism, Morphology morphology, Set<Gene> genes,
        double minK, double optK, double maxK, double doublingHours, double productivity, Set<SampleSource> sources) {
    /** 生物能长的最低温度（K），−15 °C。 */
    public static final double MIN_LIFE_K = 258.15;
    /** 生物能长的最高温度（K），122 °C。 */
    public static final double MAX_LIFE_K = 395.15;

    public MicrobeStrain {
        genes = enumSet(genes, Gene.class);
        sources = enumSet(sources, SampleSource.class);
        if (!(minK < optK && optK < maxK) || optK <= (minK + maxK) / 2) {
            throw new IllegalArgumentException("cardinal temperatures must satisfy min < (min + max) / 2 < opt < max: "
                    + minK + ", " + optK + ", " + maxK);
        }
        if (minK < MIN_LIFE_K - 1e-9 || maxK > MAX_LIFE_K + 1e-9) {
            throw new IllegalArgumentException("outside the limits of life: " + minK + ", " + maxK);
        }
        if (doublingHours <= 0 || productivity <= 0) {
            throw new IllegalArgumentException("doubling time and productivity must be positive");
        }
    }

    static <E extends Enum<E>> Set<E> enumSet(Collection<E> values, Class<E> type) {
        EnumSet<E> set = EnumSet.noneOf(type);
        set.addAll(values);
        return Collections.unmodifiableSet(set);
    }

    public boolean has(Gene gene) {
        return this.genes.contains(gene);
    }

    /** 嗜冷菌：天生有抗冻蛋白。 */
    public boolean psychrophile() {
        return this.has(Gene.ANTIFREEZE_PROTEIN);
    }

    /** 嗜热菌（含超嗜热）：天生有嗜热酶。 */
    public boolean thermophile() {
        return this.has(Gene.THERMOSTABLE_ENZYMES);
    }

    /** 最适条件下翻一倍要几刻（游戏时间）。 */
    public double doublingTicks() {
        return BioTime.ticks(this.doublingHours);
    }

    /** 这个温度下的生长速度是最适时的几成，0–1，见 {@link #cardinal}。 */
    public double growthFactor(double temperatureK) {
        return cardinal(this.minK, this.optK, this.maxK, temperatureK);
    }

    /**
     * Rosso 等（1993）的基数温度模型（CTMI）：
     * γ(T) = (T − Tmax)(T − Tmin)² / {(Topt − Tmin)[(Topt − Tmin)(T − Topt) − (Topt − Tmax)(Topt + Tmin − 2T)]}，
     * 最适温度为 1，最低、最高温度和以外为 0。要求 Topt 高于 (Tmin + Tmax) / 2。
     */
    public static double cardinal(double min, double opt, double max, double temperatureK) {
        if (temperatureK <= min || temperatureK >= max) {
            return 0;
        }
        double t = temperatureK;
        double numerator = (t - max) * (t - min) * (t - min);
        double denominator = (opt - min) * ((opt - min) * (t - opt) - (opt - max) * (opt + min - 2 * t));
        return Math.clamp(numerator / denominator, 0, 1);
    }

    /** 在这个温度下长不长。 */
    public boolean grows(double temperatureK) {
        return this.growthFactor(temperatureK) > 0;
    }

    /** 天生的、带来本事的基因。 */
    public Set<Gene> functions() {
        EnumSet<Gene> set = EnumSet.noneOf(Gene.class);
        for (Gene gene : this.genes) {
            if (gene.role() == Gene.Role.FUNCTION) {
                set.add(gene);
            }
        }
        return set;
    }

    /** 母星的菌。 */
    public boolean earthly() {
        return this.key.startsWith(EarthMicrobes.KEY_PREFIX);
    }
}
