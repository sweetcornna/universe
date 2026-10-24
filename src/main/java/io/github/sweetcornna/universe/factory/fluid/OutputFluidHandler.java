package io.github.sweetcornna.universe.factory.fluid;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import io.github.sweetcornna.universe.core.factory.FluidUnits;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 抽水站、原油萃取站对外的流体能力（ADR-0030 §三）：产物格里的流体物品按一个 1000 mB 往外给，只出不进。
 *
 * <p>管道一次抽不满 1000 mB 时先记"欠着"，欠够一个才真的从格子里拿走一个物品。欠的数记在内存里，跟着设备走，
 * 不进存档：区块卸载时最多丢掉不到一个物品的零头。
 */
public final class OutputFluidHandler implements ResourceHandler<FluidResource> {
    /** 每台设备欠着的毫桶。 */
    private static final Map<Object, Owed> OWED = new WeakHashMap<>();

    private final WorldlyContainer container;
    private final int slot;
    private final ResourceHandler<ItemResource> items;
    private final Owed owed;

    public OutputFluidHandler(WorldlyContainer container, int slot) {
        this.container = container;
        this.slot = slot;
        this.items = new WorldlyContainerWrapper(container, null);
        synchronized (OWED) {
            this.owed = OWED.computeIfAbsent(container, key -> new Owed());
        }
    }

    private ItemStack stack() {
        return this.container.getItem(this.slot);
    }

    /** 欠着的数只对现在格子里的这种物品算；格子里换了东西就作废。 */
    private int owedFor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == this.owed.item ? this.owed.millibuckets : 0;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        ItemStack stack = this.stack();
        return stack.isEmpty() || !stack.isComponentsPatchEmpty() ? FluidResource.EMPTY : FluidBridge.toFluid(stack.getItem());
    }

    @Override
    public long getAmountAsLong(int index) {
        ItemStack stack = this.stack();
        return this.getResource(index).isEmpty() ? 0 : FluidUnits.available(stack.getCount(), this.owedFor(stack));
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        ItemStack stack = this.stack();
        int max = stack.isEmpty() ? 64 : stack.getMaxStackSize();
        return FluidUnits.total(max, 0);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return false;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack stack = this.stack();
        if (amount == 0 || stack.isEmpty() || !resource.equals(this.getResource(index))) {
            return 0;
        }
        Item item = stack.getItem();
        FluidUnits.Draw draw = FluidUnits.draw(stack.getCount(), this.owedFor(stack), amount);
        if (draw.taken() <= 0) {
            return 0;
        }
        try (Transaction inner = Transaction.open(transaction)) {
            if (draw.items() > 0 && this.items.extract(this.slot, ItemResource.of(stack), draw.items(), inner) != draw.items()) {
                return 0;
            }
            this.owed.updateSnapshots(inner);
            this.owed.item = draw.owed() > 0 ? item : null;
            this.owed.millibuckets = draw.owed();
            inner.commit();
        }
        return (int) draw.taken();
    }

    @Override
    public String toString() {
        return "OutputFluidHandler[" + this.container + " slot " + this.slot + "]";
    }

    private record OwedState(@Nullable Item item, int millibuckets) {
    }

    /** 欠着的毫桶，带事务的快照。 */
    private static final class Owed extends SnapshotJournal<OwedState> {
        @Nullable Item item;
        int millibuckets;

        @Override
        protected OwedState createSnapshot() {
            return new OwedState(this.item, this.millibuckets);
        }

        @Override
        protected void revertToSnapshot(OwedState snapshot) {
            this.item = snapshot.item();
            this.millibuckets = snapshot.millibuckets();
        }
    }
}
