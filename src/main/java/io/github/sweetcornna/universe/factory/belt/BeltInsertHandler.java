package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 传送带对外的物品能力：只能往里放，一次一个，放在这格的中间（采矿机、抽水站的产物推上传送带，漏斗也能往上放）。
 * 放进去的物品先记在事务里，根事务提交时才真的放上线路，事务回滚就什么都没发生。
 */
public final class BeltInsertHandler implements ResourceHandler<ItemResource> {
    private final BeltBlockEntity belt;
    private final Journal journal = new Journal();

    BeltInsertHandler(BeltBlockEntity belt) {
        this.belt = belt;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return 1;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && resource.isComponentsPatchEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Level level = this.belt.getLevel();
        if (amount <= 0 || level == null || !this.isValid(index, resource) || !this.journal.pending.isEmpty()
                || !BeltNetwork.of(level).canInsertAt(this.belt.getBlockPos(), BeltNetwork.HALF)) {
            return 0;
        }
        this.journal.updateSnapshots(transaction);
        this.journal.pending.add(BeltNetwork.id(resource.getItem()));
        return 1;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    /** 事务里记着要放上去的物品；快照是当时排了几个。 */
    private final class Journal extends SnapshotJournal<Integer> {
        final List<Integer> pending = new ArrayList<>(1);

        @Override
        protected Integer createSnapshot() {
            return this.pending.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            while (this.pending.size() > snapshot) {
                this.pending.removeLast();
            }
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            Level level = BeltInsertHandler.this.belt.getLevel();
            if (level != null) {
                for (int item : this.pending) {
                    BeltNetwork.of(level).insertAt(BeltInsertHandler.this.belt.getBlockPos(), BeltNetwork.HALF, item);
                }
            }
            this.pending.clear();
        }
    }
}
