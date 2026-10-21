package io.github.sweetcornna.universe.compat.jade;

import io.github.sweetcornna.universe.factory.MachineBlockEntity;
import io.github.sweetcornna.universe.factory.belt.BeltPassBlockEntity;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件（ADR-0019）。Jade 装着时按注解加载，没装时不会碰到这个包。所有设备共用一对提供者：服务端发数据，
 * 客户端按设备种类排版（加工进度和缺什么、采矿机的产量和储量、射线接收站的效率、物流站每格的物品、用电和发电）。
 */
@WailaPlugin
public final class UniverseJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MachineServerData.INSTANCE, MachineBlockEntity.class);
        registration.registerBlockDataProvider(MachineServerData.INSTANCE, BeltPassBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MachineTooltip.INSTANCE, Block.class);
    }
}
