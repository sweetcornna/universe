package io.github.sweetcornna.universe.core.factory;

/**
 * 增产剂（ADR-0022），照戴森球计划的规则：
 *
 * <ul>
 * <li>喷涂机把增产剂喷到传送带上经过的物品上，每个物品记下"增产点数"：Mk.I 喷一次 1 点，Mk.II 2 点，Mk.III 4 点。
 *     一个增产剂能喷 12、24、60 个物品。
 * <li>设备收原料时把点数攒起来，做一次配方时按每种原料拿走的那些物品的平均点数算等级，几种原料取最低的。
 *     等级 0–4，Mk.III 喷满就是 4。
 * <li>设备选"额外产出"时，每做一次多攒一点额外进度，攒满一次多出一份产物；选"加速"时做得更快。两种都更费电。
 * </ul>
 */
public final class Proliferation {
    /** 等级的上限：Mk.III 喷满。 */
    public static final int MAX_LEVEL = 4;

    // 按等级 0–4 查的表（千分比），和原作一样：Mk.I、Mk.II、Mk.III 分别对应等级 1、2、4
    private static final int[] EXTRA = {0, 125, 200, 225, 250};
    private static final int[] SPEED = {0, 250, 500, 750, 1000};
    private static final int[] POWER = {0, 300, 700, 1100, 1500};

    private Proliferation() {
    }

    /** 三档增产剂。 */
    public enum Tier {
        MK1(1, 12),
        MK2(2, 24),
        MK3(4, 60);

        private final int points;
        private final int sprays;

        Tier(int points, int sprays) {
            this.points = points;
            this.sprays = sprays;
        }

        /** 喷一次给物品加的点数。 */
        public int points() {
            return this.points;
        }

        /** 一个增产剂能喷多少个物品。 */
        public int sprays() {
            return this.sprays;
        }
    }

    /** 设备怎么用增产点数。 */
    public enum Mode {
        /** 原料不变，多出产物。 */
        EXTRA_PRODUCTS,
        /** 做得更快。 */
        SPEEDUP;

        public Mode next() {
            return this == EXTRA_PRODUCTS ? SPEEDUP : EXTRA_PRODUCTS;
        }
    }

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /** 额外产出：每做一次多攒的进度（1 是多出一整份）。 */
    public static double extraProducts(int level) {
        return EXTRA[clampLevel(level)] / 1000.0;
    }

    /** 加速：速度多出的比例（1 是快一倍）。 */
    public static double speedup(int level) {
        return SPEED[clampLevel(level)] / 1000.0;
    }

    /** 耗电多出的比例。 */
    public static double extraPower(int level) {
        return POWER[clampLevel(level)] / 1000.0;
    }

    /** 这一档模式在这个等级下，设备的速度倍数。 */
    public static double speedFactor(Mode mode, int level) {
        return mode == Mode.SPEEDUP ? 1 + speedup(level) : 1;
    }

    /** 这一档模式在这个等级下，设备的功率倍数。 */
    public static double powerFactor(int level) {
        return 1 + extraPower(level);
    }

    /**
     * 喷涂：原来 {@code level} 点的物品用 {@code tier} 喷过以后的点数。只补到这一档的点数，不叠加；已经喷过更高档的不变，也不消耗喷涂次数。
     */
    public static int sprayed(int level, Tier tier) {
        return Math.max(clampLevel(level), tier.points());
    }

    /**
     * 设备里一种原料攒下的物品和点数。物品进来时点数跟着加，做配方时按比例拿走，拿走的那些物品的平均点数就是这次的等级。
     */
    public static final class Pool {
        private long count;
        private long points;

        public long count() {
            return this.count;
        }

        public long points() {
            return this.points;
        }

        /** 放进 {@code n} 个、每个 {@code level} 点的物品。 */
        public void add(long n, int level) {
            if (n <= 0) {
                return;
            }
            this.count += n;
            this.points += n * clampLevel(level);
        }

        /** 放进 {@code n} 个物品，点数一共 {@code points}（传送带上的一格货物，点数不一定分得匀）。 */
        public void addTotal(long n, long points) {
            if (n <= 0) {
                return;
            }
            this.count += n;
            this.points += Math.max(0, Math.min(points, n * MAX_LEVEL));
        }

        /**
         * 格子里的物品数被别处改了（玩家拿走一些、漏斗放进没喷过的）：少了按比例扣点数，多了的算 0 点。
         */
        public void reconcile(long actual) {
            long n = Math.max(0, actual);
            if (n < this.count) {
                this.remove(this.count - n);
            } else {
                this.count = n;
            }
        }

        /** 读档时直接设。 */
        public void set(long count, long points) {
            this.count = Math.max(0, count);
            this.points = Math.max(0, Math.min(points, this.count * MAX_LEVEL));
        }

        /**
         * 拿走 {@code n} 个，返回它们的平均点数（向下取整）。点数按比例拿：剩下的物品平均点数不变（取整的零头留在池里）。
         * 不够 {@code n} 个时什么也不拿，返回 -1。
         */
        public int take(long n) {
            if (n <= 0) {
                return MAX_LEVEL;
            }
            if (n > this.count) {
                return -1;
            }
            long taken = n == this.count ? this.points : this.points * n / this.count;
            this.count -= n;
            this.points -= taken;
            return (int) Math.min(MAX_LEVEL, taken / n);
        }

        /** 拿出去（比如玩家从设备里取原料）的 {@code n} 个物品平均几点，和 {@link #take} 一样按比例扣。 */
        public int remove(long n) {
            return n > this.count ? this.take(this.count) : this.take(n);
        }
    }

    /**
     * 额外产出的进度：每做一次按等级攒一点，攒满 1 就多出一份。返回这一次要多出几份。
     */
    public static final class ExtraProgress {
        private int milli;

        public int milli() {
            return this.milli;
        }

        public void set(int milli) {
            this.milli = Math.max(0, milli);
        }

        public int craft(int level) {
            this.milli += EXTRA[clampLevel(level)];
            int extra = this.milli / 1000;
            this.milli %= 1000;
            return extra;
        }
    }

    /** 几种原料各自的等级里取最低的；没有原料（比如采矿）算 0。 */
    public static int recipeLevel(int... levels) {
        if (levels.length == 0) {
            return 0;
        }
        int min = MAX_LEVEL;
        for (int level : levels) {
            min = Math.min(min, clampLevel(level));
        }
        return min;
    }
}
