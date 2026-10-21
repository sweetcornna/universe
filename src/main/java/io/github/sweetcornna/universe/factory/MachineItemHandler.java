package io.github.sweetcornna.universe.factory;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 设备的物品能力（ADR-0022）：原版包装再加一条，喷过增产剂的物品进原料格时去掉组件、点数记进设备的池子，同一种原料
 * 喷没喷过都叠在一格里。别的格子照原版的规矩。
 */
final class MachineItemHandler extends WorldlyContainerWrapper {
    private final MachineBlockEntity machine;
    private final @Nullable Direction side;

    MachineItemHandler(MachineBlockEntity machine, @Nullable Direction side) {
        super(machine, side);
        this.machine = machine;
        this.side = side;
    }

    private int slot(int index) {
        return this.side == null ? index : this.machine.getSlotsForFace(this.side)[index];
    }

    private static ItemResource plain(ItemResource resource) {
        return resource.without(UniverseDataComponents.PROLIFERATION.get());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return super.isValid(index, plain(resource));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Integer level = resource.getComponents().get(UniverseDataComponents.PROLIFERATION.get());
        int slot = this.slot(index);
        if (level == null || !this.machine.isInput(slot)) {
            return super.insert(index, resource, amount, transaction);
        }
        this.machine.pool(slot);
        this.machine.snapshotPools(transaction);
        int inserted = super.insert(index, plain(resource), amount, transaction);
        if (inserted > 0) {
            this.machine.receiveInput(slot, resource.getItem(), inserted, (long) inserted * level);
        }
        return inserted;
    }
}
