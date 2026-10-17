package io.github.sweetcornna.universe.core.factory;

/**
 * 传送带线路尽头的去处：下一条线路、侧面汇入的线路，或者适配层的储物仓、物流站。
 */
@FunctionalInterface
public interface BeltOutput {
    /**
     * 接收线路尽头的一个物品。
     *
     * @param item     物品编号（由适配层分配）
     * @param overshoot 这一刻物品越过线路尽头多少个单位；接到另一条线路上时从这个位置开始，保证吞吐不打折
     * @return 收下了返回 true；放不下返回 false，物品留在尽头等下一刻
     */
    boolean accept(int item, int overshoot);

    /** 接到另一条线路的 {@code entry} 处（0 是线路起点，中间的位置就是侧面汇入）。 */
    static BeltOutput into(BeltLine target, int entry) {
        return new LineEntry(target, entry);
    }

    /** 接到另一条线路上的去处；{@link BeltSystem} 据此安排结算顺序。 */
    record LineEntry(BeltLine target, int entry) implements BeltOutput {
        @Override
        public boolean accept(int item, int overshoot) {
            int position = this.entry + overshoot;
            if (position >= this.target.length()) {
                position = this.entry;
            }
            return this.target.insert(position, item);
        }
    }
}
