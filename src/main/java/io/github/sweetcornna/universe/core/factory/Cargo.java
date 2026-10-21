package io.github.sweetcornna.universe.core.factory;

/**
 * 传送带上的一格货物（ADR-0022）：一个 {@code int} 里装物品编号、叠了几个、增产点数的总和。
 *
 * <ul>
 * <li>低 20 位：物品编号（适配层的注册表编号）
 * <li>再 2 位：叠的个数减 1（1–4 个）
 * <li>再 5 位：增产点数的总和（0–16，每个物品最多 {@link Proliferation#MAX_LEVEL} 点）
 * </ul>
 *
 * <p>叠 1 个、0 点的货物就是物品编号本身，所以只认物品编号的旧代码和旧存档照常能用。最高位不用，货物永远不是负数，
 * 不会和 {@link BeltLine#EMPTY} 混淆。
 */
public final class Cargo {
    /** 一格最多叠几个。 */
    public static final int MAX_STACK = 4;
    /** 物品编号的上限（不含）。 */
    public static final int MAX_ITEM = 1 << 20;

    private static final int ITEM_MASK = MAX_ITEM - 1;
    private static final int COUNT_SHIFT = 20;
    private static final int POINTS_SHIFT = 22;
    private static final int POINTS_MASK = 0x1F;

    private Cargo() {
    }

    /**
     * @param item   物品编号（0 到 {@link #MAX_ITEM} - 1）
     * @param count  叠几个，1–{@link #MAX_STACK}
     * @param points 增产点数的总和，超出 {@code count × 4} 的截掉
     */
    public static int of(int item, int count, int points) {
        if (item < 0 || item >= MAX_ITEM) {
            throw new IllegalArgumentException("item id out of range: " + item);
        }
        int n = Math.max(1, Math.min(MAX_STACK, count));
        int p = Math.max(0, Math.min(points, n * Proliferation.MAX_LEVEL));
        return item | (n - 1) << COUNT_SHIFT | p << POINTS_SHIFT;
    }

    /** {@code count} 个、每个 {@code level} 点。 */
    public static int uniform(int item, int count, int level) {
        int n = Math.max(1, Math.min(MAX_STACK, count));
        return of(item, n, n * Math.max(0, Math.min(Proliferation.MAX_LEVEL, level)));
    }

    public static int item(int cargo) {
        return cargo & ITEM_MASK;
    }

    public static int count(int cargo) {
        return (cargo >>> COUNT_SHIFT & 0x3) + 1;
    }

    public static int points(int cargo) {
        return cargo >>> POINTS_SHIFT & POINTS_MASK;
    }

    /** 换成另一个物品编号，个数和点数不变（存档里物品按名字存，读档时编号要重新查）。 */
    public static int withItem(int cargo, int item) {
        if (item < 0 || item >= MAX_ITEM) {
            throw new IllegalArgumentException("item id out of range: " + item);
        }
        return cargo & ~ITEM_MASK | item;
    }

    /** 每个物品平均几点（向下取整）。 */
    public static int level(int cargo) {
        return points(cargo) / count(cargo);
    }

    /** 两格能不能叠成一格：同一种物品，加起来不超过 {@code limit} 个。 */
    public static boolean canMerge(int a, int b, int limit) {
        return item(a) == item(b) && count(a) + count(b) <= Math.min(limit, MAX_STACK);
    }

    /** 叠成一格；先用 {@link #canMerge} 看能不能叠。 */
    public static int merge(int a, int b) {
        return of(item(a), count(a) + count(b), points(a) + points(b));
    }

    /**
     * 从一格里分出 {@code n} 个，点数按比例分（零头留在剩下的那份里）。
     *
     * @return {分出来的, 剩下的}；全拿走时剩下的是 {@link BeltLine#EMPTY}
     */
    public static int[] split(int cargo, int n) {
        int count = count(cargo);
        if (n >= count) {
            return new int[] {cargo, BeltLine.EMPTY};
        }
        int points = points(cargo);
        int taken = points * n / count;
        return new int[] {of(item(cargo), n, taken), of(item(cargo), count - n, points - taken)};
    }

    /** 喷涂：每个物品补到 {@code tier} 的点数。返回喷过的货物；{@code used} 里加上用掉的喷涂次数（补了几个物品）。 */
    public static int spray(int cargo, Proliferation.Tier tier, int[] used) {
        return spray(cargo, tier, MAX_STACK, used);
    }

    /**
     * 喷涂，最多喷 {@code limit} 个（喷涂机里剩的次数不够时）：喷到的补到 {@code tier} 的点数，没喷到的按原来的平均点数算。
     * 已经喷过这一档或更高档的不再喷，也不用次数。
     */
    public static int spray(int cargo, Proliferation.Tier tier, int limit, int[] used) {
        int count = count(cargo);
        if (level(cargo) >= tier.points() || limit <= 0) {
            return cargo;
        }
        int n = Math.min(count, limit);
        used[0] += n;
        int rest = points(cargo) * (count - n) / count;
        return of(item(cargo), count, rest + n * tier.points());
    }
}
