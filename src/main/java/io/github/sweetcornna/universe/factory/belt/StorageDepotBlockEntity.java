package io.github.sweetcornna.universe.factory.belt;

import java.util.ArrayList;
import java.util.List;

import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseBlockEntities;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * 储物仓 Mk.I（ADR-0011）：30 格的箱子，传送带可以直接送进来（{@link BeltReceiver}），分拣器和漏斗从物品能力存取。
 * 放下或拆掉时告诉传送带网络，正对着它的线路重新看去处。
 *
 * <p>可以竖着叠（ADR-0022）：传送带送进来的东西这一层放不下就往上一层放；物品能力把这一层和上面叠着的几层连成一个，
 * 分拣器从最下面一层就能存取整叠。
 */
public class StorageDepotBlockEntity extends BaseContainerBlockEntity implements BeltReceiver {
    public static final int SLOTS = 30;
    /** 最多连几层。 */
    public static final int MAX_STACK = 16;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public StorageDepotBlockEntity(BlockPos pos, BlockState state) {
        super(UniverseBlockEntities.STORAGE_DEPOT.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            BeltNetwork.of(this.level).outputChanged(this.worldPosition);
            this.invalidateBelow();
        }
    }

    /** 叠上或拆掉一层时，下面几层的物品能力变了。 */
    private void invalidateBelow() {
        BlockPos.MutableBlockPos pos = this.worldPosition.mutable();
        for (int i = 1; i < MAX_STACK; i++) {
            this.level.invalidateCapabilities(pos.move(Direction.DOWN).immutable());
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            this.invalidateBelow();
        }
    }

    /** 从这一层往上叠着的储物仓，这一层在最前面。 */
    public List<StorageDepotBlockEntity> column() {
        List<StorageDepotBlockEntity> column = new ArrayList<>();
        column.add(this);
        if (this.level != null) {
            BlockPos.MutableBlockPos pos = this.worldPosition.mutable();
            for (int i = 1; i < MAX_STACK && this.level.getBlockEntity(pos.move(Direction.UP)) instanceof StorageDepotBlockEntity above; i++) {
                column.add(above);
            }
        }
        return column;
    }

    /** 物品能力：这一层和上面叠着的几层连成一个，先放下面的。 */
    public ResourceHandler<ItemResource> handler() {
        List<StorageDepotBlockEntity> column = this.column();
        if (column.size() == 1) {
            return VanillaContainerWrapper.of(this);
        }
        List<ResourceHandler<ItemResource>> handlers = new ArrayList<>(column.size());
        for (StorageDepotBlockEntity depot : column) {
            handlers.add(VanillaContainerWrapper.of(depot));
        }
        return new CombinedResourceHandler<>(handlers);
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            BeltNetwork network = BeltNetwork.existing(this.level);
            if (network != null) {
                network.outputChanged(this.worldPosition);
            }
        }
        super.setRemoved();
    }

    @Override
    public boolean acceptFromBelt(int cargo) {
        List<ItemStack> stacks = BeltNetwork.stacks(cargo);
        for (StorageDepotBlockEntity depot : this.column()) {
            if (fits(depot.items, stacks)) {
                for (ItemStack stack : stacks) {
                    insert(depot.items, stack.copy());
                }
                depot.setChanged();
                return true;
            }
        }
        return false;
    }

    /** 这几堆能不能全放进这几格。 */
    static boolean fits(List<ItemStack> slots, List<ItemStack> stacks) {
        if (stacks.size() == 1) {
            return room(slots, stacks.getFirst()) >= stacks.getFirst().getCount();
        }
        List<ItemStack> copy = new ArrayList<>(slots.size());
        for (ItemStack slot : slots) {
            copy.add(slot.copy());
        }
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            insert(copy, rest);
            if (!rest.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 这几格还放得下几个 {@code stack}（先叠到同样的堆上，再用空格）。 */
    static int room(List<ItemStack> slots, ItemStack stack) {
        int room = 0;
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
        }
        return room;
    }

    /** 放进去，先叠到同样的堆上；先用 {@link #room} 看放不放得下。 */
    static void insert(List<ItemStack> slots, ItemStack stack) {
        for (ItemStack slot : slots) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
            if (slots.get(i).isEmpty()) {
                slots.set(i, stack.split(stack.getMaxStackSize()));
            }
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected Component getDefaultName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        MachineMenu.Type type = UniverseMenus.STORAGE_DEPOT;
        return new MachineMenu(type, containerId, inventory, this, new SimpleContainerData(type.dataCount()), this.worldPosition);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
    }
}
