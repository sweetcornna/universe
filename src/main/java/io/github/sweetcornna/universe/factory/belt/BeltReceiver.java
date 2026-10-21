package io.github.sweetcornna.universe.factory.belt;

/** 传送带尽头可以直接送进去的方块实体（储物仓）。生产设备不实现它，只能通过分拣器存取（玩法设计 §9.3）。 */
public interface BeltReceiver {
    /**
     * 收下传送带尽头的一格货物（服务端）：1–4 个同一种物品，带着增产点数（{@link io.github.sweetcornna.universe.core.factory.Cargo}，
     * 用 {@link BeltNetwork#stacks} 换成物品堆）。要么全收，要么不收。
     *
     * @return 放不下返回 false，货物留在尽头
     */
    boolean acceptFromBelt(int cargo);
}
