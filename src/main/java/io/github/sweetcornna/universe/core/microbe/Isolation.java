package io.github.sweetcornna.universe.core.microbe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 培养箱（ADR-0036 §五）：分离、扩增、驯化。
 *
 * <ul>
 *   <li>分离：一管样本加培养基，设一个温度。样本里温度范围盖得住这个温度的菌才长，离最适越近越容易长出来；
 *       一个都长不出来就是空的培养皿。平板上长出菌落现实里要一到三天，这里按一天（{@link #ISOLATION_HOURS}）。
 *       长得慢的菌现实里要几周才看得见菌落，这里不分快慢，都按一天。</li>
 *   <li>扩增：一管菌种加培养基过夜（{@link #EXPAND_HOURS}），变成 {@link #EXPAND_COPIES} 管，编辑进去的基因和驯化的加成都跟着。</li>
 *   <li>驯化：照适应性实验室进化，在菌的温度范围边上（离界限不到范围的 {@link #EDGE_BAND}）连续扩增，
 *       每一轮产率加 {@link #DOMESTICATION_STEP}，最多加到 {@link #DOMESTICATION_MAX}。在最适温度附近扩增不算驯化。
 *       比现实快得多：一轮扩增只有十来代，现实的实验室进化几百、几千代才把生长速度提高两三成到一倍
 *       （Lenski 的大肠杆菌长期进化实验，两万代适应度提高约七成）。</li>
 * </ul>
 */
public final class Isolation {
    /** 分离一次要几小时（现实时间）。 */
    public static final double ISOLATION_HOURS = 24;
    /** 分离一次要几刻：2.4 分钟。 */
    public static final int ISOLATION_TICKS = (int) Math.round(BioTime.ticks(ISOLATION_HOURS));
    /** 扩增一次要几小时（过夜培养）。 */
    public static final double EXPAND_HOURS = 16;
    /** 扩增一次要几刻：1.6 分钟。 */
    public static final int EXPAND_TICKS = (int) Math.round(BioTime.ticks(EXPAND_HOURS));
    /** 扩增一管出几管。 */
    public static final int EXPAND_COPIES = 2;
    /** 每一轮驯化加多少产率。 */
    public static final double DOMESTICATION_STEP = 0.05;
    /** 驯化最多加多少产率。 */
    public static final double DOMESTICATION_MAX = 0.5;
    /** 离界限多近算"边上"：最适温度到界限的这么多比例以内。 */
    public static final double EDGE_BAND = 0.25;
    /** 培养箱能设的温度（K），0–120 °C 上下。 */
    public static final double MIN_SETTING_K = 273;
    public static final double MAX_SETTING_K = 393;

    private Isolation() {
    }

    /** 这些菌里在 {@code incubatorK} 下能长的。 */
    public static List<MicrobeStrain> growable(List<MicrobeStrain> candidates, double incubatorK) {
        List<MicrobeStrain> list = new ArrayList<>();
        for (MicrobeStrain strain : candidates) {
            if (strain.grows(incubatorK)) {
                list.add(strain);
            }
        }
        return list;
    }

    /** 分离出来的菌；一个都长不出来为空。 */
    public static Optional<MicrobeStrain> isolate(List<MicrobeStrain> candidates, double incubatorK, Rng rng) {
        List<MicrobeStrain> growable = growable(candidates, incubatorK);
        if (growable.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rng.weighted(growable, strain -> strain.growthFactor(incubatorK)));
    }

    /** 在这个温度下扩增算不算驯化：长得了，而且在范围的边上。 */
    public static boolean domesticates(Organism organism, double incubatorK) {
        Tolerance tolerance = organism.tolerance();
        if (!tolerance.grows(incubatorK)) {
            return false;
        }
        double low = tolerance.optK() - (tolerance.optK() - tolerance.minK()) * (1 - EDGE_BAND);
        double high = tolerance.optK() + (tolerance.maxK() - tolerance.optK()) * (1 - EDGE_BAND);
        return incubatorK <= low || incubatorK >= high;
    }

    /** 扩增一次以后的菌：在边上扩增的多一轮驯化。 */
    public static Organism expand(Organism organism, double incubatorK) {
        if (!domesticates(organism, incubatorK)) {
            return organism;
        }
        return organism.withDomestication(Math.min(DOMESTICATION_MAX, organism.domestication() + DOMESTICATION_STEP));
    }
}
