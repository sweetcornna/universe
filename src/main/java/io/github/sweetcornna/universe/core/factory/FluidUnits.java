package io.github.sweetcornna.universe.core.factory;

/**
 * 流体物品和毫桶的换算（ADR-0030 §三）：本模组的流体是物品（ADR-0011），一个流体物品对外算 1000 mB。别的模组的管道
 * 常常一次只走几十、几百毫桶，不满一个物品的零头要单独记着。
 *
 * <p>两种记法：储液罐收进来不满一个的记"多出来的毫桶"（{@code whole × 1000 + extra}）；设备产物格被抽走不满一个的记
 * "欠着的毫桶"（{@code whole × 1000 − owed}），够一个时才真的拿走一个物品。
 */
public final class FluidUnits {
    /** 一个流体物品算多少毫桶。 */
    public static final int BUCKET = 1000;

    private FluidUnits() {
    }

    /** {@code whole} 个物品加 {@code extra} 毫桶一共多少毫桶。 */
    public static long total(long whole, long extra) {
        return whole * BUCKET + extra;
    }

    /** 毫桶里有几个整的物品。 */
    public static long whole(long millibuckets) {
        return Math.floorDiv(millibuckets, BUCKET);
    }

    /** 毫桶里不满一个物品的零头。 */
    public static int extra(long millibuckets) {
        return (int) Math.floorMod(millibuckets, BUCKET);
    }

    /**
     * 从格子里抽 {@code amount} 毫桶：格子里有 {@code count} 个物品，已经欠着 {@code owed} 毫桶（0–999）。
     *
     * @return 要拿走的物品数和抽完以后欠着的毫桶；能抽的不到 {@code amount} 时按能抽的算
     */
    public static Draw draw(int count, int owed, long amount) {
        long available = Math.max(0, total(count, -owed));
        long taken = Math.max(0, Math.min(amount, available));
        long owedAfter = owed + taken;
        int items = (int) whole(owedAfter);
        return new Draw(taken, items, extra(owedAfter));
    }

    /** 能抽多少毫桶：{@code count} 个物品减掉欠着的。 */
    public static long available(int count, int owed) {
        return Math.max(0, total(count, -owed));
    }

    /**
     * 一次抽取的结果。
     *
     * @param taken 抽走的毫桶
     * @param items 要从格子里拿走的物品数
     * @param owed  抽完以后欠着的毫桶（0–999）
     */
    public record Draw(long taken, int items, int owed) {
    }
}
