package io.github.sweetcornna.universe.factory.belt;

import net.minecraft.world.item.Item;

/** 传送带尽头可以直接送进去的方块实体（储物仓）。生产设备不实现它，只能通过分拣器存取（玩法设计 §9.3）。 */
public interface BeltReceiver {
    /**
     * 收下传送带尽头的一个物品（服务端）。
     *
     * @return 放不下返回 false，物品留在尽头
     */
    boolean acceptFromBelt(Item item);
}
