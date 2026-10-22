package io.github.sweetcornna.universe.core.poi;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.core.procgen.Rng;

/**
 * 雷击石（ADR-0026）：雷打在沙、土、石头上，把落点熔成一根往下伸的玻璃管。现实里的雷击石就是这么来的，
 * 管子顺着电流往地下钻，偶尔分出一个岔。
 *
 * <p>不是每次落雷都有：{@link #CONVERT_CHANCE}。下雨的星球按原版的规矩打雷；火山灰的风暴不下雨，原版不打，
 * 由适配层按 {@link #VOLCANIC_STRIKE_CHANCE} 在玩家附近补（现实里火山灰云里也打雷）。
 */
public final class Fulgurite {
    /** 一次落雷熔出雷击石的概率。 */
    public static final double CONVERT_CHANCE = 0.6;
    /** 管子每往下多伸一格的概率。 */
    static final double DEEPER_CHANCE = 0.45;
    /** 最多往下伸几格（不算落点）。 */
    public static final int MAX_DEPTH = 3;
    /** 分岔的概率：落点或下面一格旁边多一块。 */
    static final double BRANCH_CHANCE = 0.25;
    /** 火山灰风暴里，每个玩家每秒附近落一道雷的概率（平均 25 秒一道）。 */
    public static final double VOLCANIC_STRIKE_CHANCE = 0.04;
    /** 火山灰风暴的雷落在离玩家多远（格）。 */
    public static final int VOLCANIC_MIN_DISTANCE = 16;
    public static final int VOLCANIC_MAX_DISTANCE = 80;

    private Fulgurite() {
    }

    /** 熔成雷击石的格子，相对落点（被打中的那块方块），第一格总是落点本身。每格是 {dx, dy, dz}。 */
    public static List<int[]> tube(Rng rng) {
        List<int[]> cells = new ArrayList<>();
        cells.add(new int[] {0, 0, 0});
        int depth = 0;
        while (depth < MAX_DEPTH && rng.chance(DEEPER_CHANCE)) {
            depth++;
            cells.add(new int[] {0, -depth, 0});
        }
        if (rng.chance(BRANCH_CHANCE)) {
            int side = rng.nextInt(4);
            int dy = depth > 0 && rng.chance(0.5) ? -1 : 0;
            cells.add(new int[] {side == 0 ? 1 : side == 1 ? -1 : 0, dy, side == 2 ? 1 : side == 3 ? -1 : 0});
        }
        return cells;
    }
}
