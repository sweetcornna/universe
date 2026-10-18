package io.github.sweetcornna.universe.logistics;

import io.github.sweetcornna.universe.core.logistics.Station;
import io.github.sweetcornna.universe.core.logistics.StationKind;
import io.github.sweetcornna.universe.core.logistics.StationSlot;
import io.github.sweetcornna.universe.registry.UniverseItems;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 物流站对自动化的物品能力（ADR-0015）：直接读写账本里的物品格，分拣器、漏斗和别的模组的管道都能接。
 * 放进来不超过每格设的上限；拿出去不动载具预订的那部分。星际物流站多一个只进不出的格子收翘曲器。
 */
public final class StationHandler implements ResourceHandler<ItemResource> {
    private final StationBlockEntity owner;
    private final Journal journal = new Journal();

    StationHandler(StationBlockEntity owner) {
        this.owner = owner;
    }

    private int slots() {
        return this.owner.kind().slots();
    }

    private boolean hasWarperSlot() {
        return this.owner.kind() == StationKind.INTERSTELLAR;
    }

    @Override
    public int size() {
        return this.slots() + (this.hasWarperSlot() ? 1 : 0);
    }

    @Override
    public ItemResource getResource(int index) {
        Station station = this.owner.station();
        if (station == null) {
            return ItemResource.EMPTY;
        }
        if (index >= this.slots()) {
            return station.warpers() > 0 ? ItemResource.of(UniverseItems.SPACE_WARPER.get()) : ItemResource.EMPTY;
        }
        StationSlot slot = station.slot(index);
        return slot.isEmpty() ? ItemResource.EMPTY : ItemResource.of(StationBlockEntity.itemOf(slot.item()));
    }

    @Override
    public long getAmountAsLong(int index) {
        Station station = this.owner.station();
        if (station == null) {
            return 0;
        }
        return index >= this.slots() ? station.warpers() : station.slot(index).count();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        Station station = this.owner.station();
        if (station == null) {
            return 0;
        }
        if (index >= this.slots()) {
            return Station.MAX_WARPERS;
        }
        return Math.min(station.slot(index).max(), this.owner.kind().slotCapacity());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Station station = this.owner.station();
        if (station == null || resource.isEmpty() || !resource.isComponentsPatchEmpty()) {
            return false;
        }
        if (index >= this.slots()) {
            return resource.is(UniverseItems.SPACE_WARPER.get());
        }
        StationSlot slot = station.slot(index);
        return !slot.isEmpty() && slot.item().equals(StationBlockEntity.idOf(resource.getItem()));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || !this.isValid(index, resource)) {
            return 0;
        }
        Station station = this.owner.station();
        if (index >= this.slots()) {
            int added = Math.min(amount, Station.MAX_WARPERS - station.warpers());
            if (added > 0) {
                this.journal.updateSnapshots(transaction);
                station.setWarpers(station.warpers() + added);
            }
            return added;
        }
        StationSlot slot = station.slot(index);
        int room = Math.min(slot.max(), this.owner.kind().slotCapacity()) - slot.count();
        int added = Math.min(amount, Math.max(0, room));
        if (added > 0) {
            this.journal.updateSnapshots(transaction);
            station.insert(slot.item(), added);
        }
        return added;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || index >= this.slots() || !this.isValid(index, resource)) {
            return 0;
        }
        Station station = this.owner.station();
        int taken = Math.min(amount, station.slot(index).available());
        if (taken > 0) {
            this.journal.updateSnapshots(transaction);
            station.extract(index, taken);
        }
        return taken;
    }

    /** 传送带直接送来一个（不走事务）。 */
    boolean insertOne(Station station, Item item) {
        if (station.insert(StationBlockEntity.idOf(item), 1) == 1) {
            this.owner.setChanged();
            return true;
        }
        if (this.hasWarperSlot() && item == UniverseItems.SPACE_WARPER.get() && station.warpers() < Station.MAX_WARPERS) {
            station.setWarpers(station.warpers() + 1);
            this.owner.setChanged();
            return true;
        }
        return false;
    }

    /** 事务的快照：各格的数量和翘曲器。 */
    private record Snapshot(int[] counts, int warpers) {
    }

    private final class Journal extends SnapshotJournal<Snapshot> {
        @Override
        protected Snapshot createSnapshot() {
            Station station = StationHandler.this.owner.station();
            if (station == null) {
                return new Snapshot(new int[0], 0);
            }
            int[] counts = new int[station.slots().size()];
            for (int i = 0; i < counts.length; i++) {
                counts[i] = station.slot(i).count();
            }
            return new Snapshot(counts, station.warpers());
        }

        @Override
        protected void revertToSnapshot(Snapshot snapshot) {
            Station station = StationHandler.this.owner.station();
            if (station == null) {
                return;
            }
            for (int i = 0; i < snapshot.counts().length; i++) {
                station.restoreCount(i, snapshot.counts()[i]);
            }
            station.setWarpers(snapshot.warpers());
        }

        @Override
        protected void onRootCommit(Snapshot originalState) {
            StationHandler.this.owner.setChanged();
        }
    }
}
