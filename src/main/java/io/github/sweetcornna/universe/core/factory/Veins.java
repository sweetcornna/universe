package io.github.sweetcornna.universe.core.factory;

import io.github.sweetcornna.universe.core.procgen.Rng;
import io.github.sweetcornna.universe.core.procgen.Seeds;

/**
 * 矿脉节点的储量和原油涌泉的产量（ADR-0011）。没开采过的节点不存档，储量由世界种子和位置算出来；
 * 开采过的只记开采了多少。储量和涌泉的产量都乘建世界时定的资源倍率（ADR-0028 §七）。
 *
 * <p>派生结果属于存档格式：改动算法，已开采过的节点剩余储量会跟着变。
 */
public final class Veins {
    /** 一个节点的基础储量范围（丰度 1）。戴森球计划的节点动辄上万，这里一个方块按几百算，一片露头几十个方块。 */
    public static final int MIN_RESERVE = 400;
    public static final int MAX_RESERVE = 1200;
    /** 涌泉的初始产量范围（个/秒）。 */
    public static final double MIN_SEEP_RATE = 0.5;
    public static final double MAX_SEEP_RATE = 1.5;
    /** 涌泉每开采这么多，产量降到初始的 1/e。 */
    public static final double SEEP_DECAY = 30_000;
    /** 涌泉产量最低降到初始的这么多。 */
    public static final double SEEP_FLOOR = 0.3;

    private Veins() {
    }

    /** 资源倍率（ADR-0028 §七）的上限：10 倍。 */
    public static final double MAX_MULTIPLIER = 10;
    /** 资源倍率 0 时节点的储量：采不完。 */
    public static final int INFINITE = Integer.MAX_VALUE;

    /**
     * 一个节点的初始储量。
     *
     * @param richness 丰度倍率，星球上是资源的丰度换算来的（0.5–1.5），主世界是 1
     */
    public static int initialReserve(long worldSeed, int x, int y, int z, double richness) {
        return initialReserve(worldSeed, x, y, z, richness, 1);
    }

    /**
     * 一个节点的初始储量，乘上建世界时定的资源倍率。开采过的节点只记开采了多少，倍率变了剩余储量跟着变。
     *
     * @param multiplier 资源倍率，1 是 100%；0 是无限（返回 {@link #INFINITE}）
     */
    public static int initialReserve(long worldSeed, int x, int y, int z, double richness, double multiplier) {
        if (infinite(multiplier)) {
            return INFINITE;
        }
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(worldSeed, "vein_reserve"), pack(x, y, z)));
        double reserve = rng.range(MIN_RESERVE, MAX_RESERVE) * richness * Math.min(multiplier, MAX_MULTIPLIER);
        return Math.max(1, (int) Math.round(reserve));
    }

    /** 资源倍率 0：储量无限，涌泉不衰减。 */
    public static boolean infinite(double multiplier) {
        return multiplier <= 0;
    }

    /** 涌泉的初始产量（个/秒）。 */
    public static double initialSeepRate(long worldSeed, int x, int y, int z) {
        Rng rng = Rng.of(Seeds.derive(Seeds.derive(worldSeed, "seep_rate"), pack(x, y, z)));
        return rng.range(MIN_SEEP_RATE, MAX_SEEP_RATE);
    }

    /** 已经开采了 {@code extracted} 个之后的产量：按指数衰减，不低于初始的 {@link #SEEP_FLOOR}。 */
    public static double seepRate(double initialRate, long extracted) {
        return initialRate * Math.max(SEEP_FLOOR, Math.exp(-extracted / SEEP_DECAY));
    }

    /**
     * 乘上资源倍率的产量：倍率越高出得越多；倍率 0 时不衰减，一直是初始产量。
     *
     * @param multiplier 资源倍率，1 是 100%；0 是无限
     */
    public static double seepRate(double initialRate, long extracted, double multiplier) {
        if (infinite(multiplier)) {
            return initialRate;
        }
        return seepRate(initialRate, extracted) * Math.min(multiplier, MAX_MULTIPLIER);
    }

    /** 方块坐标打包成一个数：x、z 各 26 位，y 12 位。 */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }
}
