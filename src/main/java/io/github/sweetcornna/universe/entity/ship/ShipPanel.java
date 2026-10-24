package io.github.sweetcornna.universe.entity.ship;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.block.ship.HelmBlockEntity;
import io.github.sweetcornna.universe.core.ship.ShipAssembler;
import io.github.sweetcornna.universe.registry.UniverseBlocks;
import io.github.sweetcornna.universe.tech.ListContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 打开飞船面板（ADR-0024）。驾驶时按机甲面板键打开的是这艘船的；停着的船潜行右键驾驶台打开，评级按和驾驶台相连的方块算。
 *
 * <p>停着的船直接改驾驶台的方块实体。飞着的船驾驶台存在实体里：每次改动都写回一份新的方块数据、重算性能，
 * 再把客户端该知道的那部分发给看得见这艘船的玩家。
 */
public final class ShipPanel {
    /** 停着的船离驾驶台多远还能开着面板。 */
    private static final double REACH = 8;

    private ShipPanel() {
    }

    /** 驾驶员打开自己正开着的船的面板。 */
    public static void openFlying(ServerPlayer player, ShipEntity ship) {
        ShipStructure structure = ship.structure();
        if (structure == null || !structure.helm().state().is(UniverseBlocks.SHIP_HELM.get())) {
            return;
        }
        RegistryAccess registries = player.level().registryAccess();
        NonNullList<ItemStack> items = NonNullList.withSize(HelmBlockEntity.SLOTS, ItemStack.EMPTY);
        HelmBlockEntity helm = helm(structure, registries);
        List<ItemStack> loaded = helm.items();
        for (int i = 0; i < items.size(); i++) {
            items.set(i, loaded.get(i).copy());
        }
        int supercharged = helm.superchargedSlots();
        ListContainer tech = new ListContainer(items, () -> writeBack(ship, items));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShipPanelMenu(id, inventory, structure.counts(), structure.cargoSlots(),
                true, supercharged, tech, viewer -> !ship.isRemoved() && viewer.getVehicle() == ship),
                Component.translatable("gui.universe.ship_panel.title")), buf -> ShipPanelMenu.writeOpenData(structure, true, supercharged, buf));
    }

    /**
     * 坐在船上按货舱键：船上所有箱子、木桶拼成一个界面（ADR-0030 §六）。飞着、停着都能开，坐着就行，不用是驾驶员；
     * 船上一个货柜都没有也开得起来，界面里写一行"船上没有货柜"。
     */
    public static void openCargo(ServerPlayer player, ShipEntity ship) {
        if (ship.isRemoved() || ship.structure() == null) {
            return;
        }
        ShipCargoContainer cargo = ShipCargoContainer.of(ship, player.level().registryAccess());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShipCargoMenu(id, inventory, cargo,
                viewer -> !ship.isRemoved() && viewer.getVehicle() == ship), Component.translatable("gui.universe.ship_cargo.title")),
                buf -> ShipCargoMenu.writeOpenData(cargo, buf));
    }

    /** 潜行右键停着的船的驾驶台。 */
    public static void openParked(ServerLevel level, BlockPos helmPos, ServerPlayer player) {
        if (!(level.getBlockEntity(helmPos) instanceof HelmBlockEntity helm)) {
            return;
        }
        ShipStructure structure = preview(level, helmPos);
        if (structure == null) {
            player.sendOverlayMessage(Component.translatable("message.universe.ship.too_large", ShipAssembler.MAX_BLOCKS));
            return;
        }
        ListContainer tech = new ListContainer(helm.items(), helm::setChanged);
        int supercharged = helm.superchargedSlots();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShipPanelMenu(id, inventory, structure.counts(), structure.cargoSlots(),
                false, supercharged, tech, viewer -> !helm.isRemoved() && viewer.distanceToSqr(Vec3.atCenterOf(helmPos)) < REACH * REACH),
                Component.translatable("gui.universe.ship_panel.title")), buf -> ShipPanelMenu.writeOpenData(structure, false, supercharged, buf));
    }

    /** 和驾驶台相连、组装时会装上船的方块（不带方块实体的数据，只用来评级）；太大时为 {@code null}。 */
    static ShipStructure preview(ServerLevel level, BlockPos helmPos) {
        ShipAssembler.Result result = ShipAssembler.assemble(
                (x, y, z) -> ShipParts.canAssemble(level, new BlockPos(x, y, z)),
                helmPos.getX(), helmPos.getY(), helmPos.getZ(), ShipAssembler.MAX_BLOCKS);
        if (result.tooLarge() || result.positions().isEmpty()) {
            return null;
        }
        List<ShipStructure.ShipBlock> blocks = new ArrayList<>(result.positions().size());
        for (long packed : result.positions()) {
            BlockPos pos = BlockPos.of(packed);
            blocks.add(new ShipStructure.ShipBlock(pos.getX() - helmPos.getX(), pos.getY() - helmPos.getY(), pos.getZ() - helmPos.getZ(),
                    level.getBlockState(pos), null));
        }
        return new ShipStructure(blocks);
    }

    /** 船上驾驶台的方块实体（旧存档里没有就新建一个空的）。 */
    /** 船上驾驶台的方块实体（从组装时存下的数据读出来，改了不会写回船上）。 */
    static HelmBlockEntity helm(ShipStructure structure, RegistryAccess registries) {
        ShipStructure.ShipBlock block = structure.helm();
        BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
        if (block.blockEntity() != null
                && BlockEntity.loadStatic(pos, block.state(), block.blockEntity(), registries) instanceof HelmBlockEntity loaded) {
            return loaded;
        }
        return new HelmBlockEntity(pos, block.state());
    }

    /** 飞着的船改了技术：写回驾驶台的数据。 */
    private static void writeBack(ShipEntity ship, List<ItemStack> items) {
        ShipStructure structure = ship.structure();
        if (ship.isRemoved() || structure == null) {
            return;
        }
        RegistryAccess registries = ship.level().registryAccess();
        HelmBlockEntity helm = helm(structure, registries);
        for (int i = 0; i < items.size(); i++) {
            helm.items().set(i, items.get(i).copy());
        }
        ship.techChanged(structure.withHelmData(helm.saveWithFullMetadata(registries)), helm.getUpdateTag(registries));
    }
}
