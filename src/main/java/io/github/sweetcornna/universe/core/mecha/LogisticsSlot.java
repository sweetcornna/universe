package io.github.sweetcornna.universe.core.mecha;

/**
 * 机甲的一个物流栏位（ADR-0023）：一种物品和上下限。计数是背包加货舱里这种物品的总数。
 *
 * <p>少于下限时请配送器送到上限，多于上限时请配送器把多的收走。
 *
 * @param item 物品 ID，空字符串是没设
 */
public record LogisticsSlot(String item, int min, int max) {
    public static final int LIMIT = 9999;
    public static final LogisticsSlot EMPTY = new LogisticsSlot("", 0, 0);

    public LogisticsSlot {
        item = item == null ? "" : item;
        min = Math.clamp(min, 0, LIMIT);
        max = Math.clamp(max, 0, LIMIT);
        if (max < min) {
            max = min;
        }
    }

    /** 设成一种物品，默认上下限是一组。 */
    public static LogisticsSlot of(String item, int stack) {
        return new LogisticsSlot(item, stack, stack);
    }

    public boolean isEmpty() {
        return this.item.isEmpty();
    }

    /** 身上有 count 个时要送来几个；不缺时是 0。 */
    public int supply(int count) {
        return this.isEmpty() || count >= this.min ? 0 : this.max - count;
    }

    /** 身上有 count 个时要收走几个；不多时是 0。 */
    public int collect(int count) {
        return this.isEmpty() || count <= this.max ? 0 : count - this.max;
    }

    public LogisticsSlot withBounds(int min, int max) {
        return new LogisticsSlot(this.item, min, max);
    }
}
