package io.github.sweetcornna.universe.core.procgen;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * 确定性的伪随机数发生器（SplitMix64）。
 *
 * <p>不用 {@link java.util.Random} 或 {@link java.util.SplittableRandom}：前者质量差，后者的算法
 * JDK 规范没有承诺不变。程序生成的结果属于存档格式，所以自己实现，保证跨 JDK 版本、跨平台一致。
 *
 * <p>习惯用法：每个"方面"（地形、配色、天气……）从对象种子派生一条独立的流，
 * {@code Rng.of(Seeds.derive(planetSeed, "terrain"))}。这样以后给某个方面多抽几个数，别的方面不受影响。
 */
public final class Rng {
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    public static Rng of(long seed) {
        return new Rng(seed);
    }

    public long nextLong() {
        this.state += GOLDEN_GAMMA;
        return Seeds.mix(this.state);
    }

    /** [0, bound) 内均匀分布的整数。 */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        // Lemire 的乘法取高位，再拒绝掉会造成偏差的那一小段
        long m = (this.nextLong() >>> 32) * bound;
        long low = m & 0xFFFFFFFFL;
        if (low < bound) {
            long threshold = (0x1_0000_0000L - bound) % bound;
            while (low < threshold) {
                m = (this.nextLong() >>> 32) * bound;
                low = m & 0xFFFFFFFFL;
            }
        }
        return (int) (m >>> 32);
    }

    /** [min, max] 内均匀分布的整数，两端都包含。 */
    public int range(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max < min: " + min + ", " + max);
        }
        return min + this.nextInt(max - min + 1);
    }

    /** [0, 1) 内均匀分布。 */
    public double nextDouble() {
        return (this.nextLong() >>> 11) * 0x1.0p-53;
    }

    /** [min, max) 内均匀分布。 */
    public double range(double min, double max) {
        return min + (max - min) * this.nextDouble();
    }

    /** 对数均匀分布，适合跨几个数量级的量（光度、距离）。 */
    public double logRange(double min, double max) {
        return Math.exp(this.range(Math.log(min), Math.log(max)));
    }

    public boolean chance(double probability) {
        return this.nextDouble() < probability;
    }

    /** 标准正态分布（Box-Muller）。 */
    public double gaussian() {
        double u = 1.0 - this.nextDouble(); // (0, 1]，避免 log(0)
        double v = this.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
    }

    public <T> T pick(List<T> options) {
        return options.get(this.nextInt(options.size()));
    }

    public <T> T pick(T[] options) {
        return options[this.nextInt(options.length)];
    }

    /** 按权重挑选；权重为 0 的选项不会被选中。 */
    public <T> T weighted(List<T> options, ToDoubleFunction<T> weight) {
        double total = 0;
        for (T option : options) {
            total += Math.max(0, weight.applyAsDouble(option));
        }
        if (total <= 0) {
            throw new IllegalArgumentException("all weights are zero");
        }
        double target = this.nextDouble() * total;
        T last = null;
        for (T option : options) {
            double w = Math.max(0, weight.applyAsDouble(option));
            if (w <= 0) {
                continue;
            }
            last = option;
            target -= w;
            if (target < 0) {
                return option;
            }
        }
        return last;
    }

    /** 派生一条新的独立流。会消耗本流的一个数。 */
    public Rng fork(String key) {
        return new Rng(Seeds.derive(this.nextLong(), key));
    }
}
