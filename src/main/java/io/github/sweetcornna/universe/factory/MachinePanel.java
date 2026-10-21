package io.github.sweetcornna.universe.factory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 没有物品格的方块（流速监测器）打开设备界面时用的容器：只把界面上的按钮转给方块实体，走远了界面自动关掉。
 */
public final class MachinePanel extends SimpleContainer implements MachineMenu.ButtonTarget {
    private final BlockEntity owner;
    private final MachineMenu.ButtonTarget target;

    public <T extends BlockEntity & MachineMenu.ButtonTarget> MachinePanel(T owner) {
        super(0);
        this.owner = owner;
        this.target = owner;
    }

    @Override
    public boolean clickButton(ServerPlayer player, int button) {
        return this.target.clickButton(player, button);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this.owner, player);
    }
}
