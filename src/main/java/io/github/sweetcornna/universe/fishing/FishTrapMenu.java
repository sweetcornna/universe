package io.github.sweetcornna.universe.fishing;

import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 捕鱼笼的界面（ADR-0034 §一）：左边饵格，中间 3×3 的输出，上面写状态和水域、画进度。 */
public class FishTrapMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 168;
    public static final int BAIT_X = 26;
    public static final int BAIT_Y = 36;
    public static final int GRID_X = 98;
    public static final int GRID_Y = 18;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 86;

    private final Container trap;
    private final ContainerData data;

    /** 客户端：格子和数据由菜单同步；打开时带的方块坐标用不到。 */
    public FishTrapMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(FishTrapBlockEntity.SLOTS), new SimpleContainerData(FishTrapBlockEntity.DATA_COUNT));
        buf.readBlockPos();
    }

    FishTrapMenu(int id, Inventory inventory, Container trap, ContainerData data) {
        super(UniverseMenus.FISH_TRAP.get(), id);
        this.trap = trap;
        this.data = data;
        this.addSlot(new Slot(trap, FishTrapBlockEntity.BAIT_SLOT, BAIT_X, BAIT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Fishing.isBait(stack);
            }
        });
        for (int i = 0; i < FishTrapBlockEntity.OUTPUT_SLOTS; i++) {
            this.addSlot(new Slot(trap, 1 + i, GRID_X + i % 3 * 18, GRID_Y + i / 3 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
        this.addDataSlots(data);
    }

    public float progress() {
        return this.data.get(FishTrapBlockEntity.DATA_PROGRESS) / (float) FishTrapBlockEntity.CATCH_TICKS;
    }

    public FishTrapBlockEntity.Status status() {
        int status = this.data.get(FishTrapBlockEntity.DATA_STATUS);
        FishTrapBlockEntity.Status[] values = FishTrapBlockEntity.Status.values();
        return status >= 0 && status < values.length ? values[status] : FishTrapBlockEntity.Status.DRY;
    }

    /** 那里是哪种水域的编号；不在水里是 -1。 */
    public int habitat() {
        return this.data.get(FishTrapBlockEntity.DATA_HABITAT);
    }

    public boolean baited() {
        return !this.trap.getItem(FishTrapBlockEntity.BAIT_SLOT).isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.trap.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int trapEnd = FishTrapBlockEntity.SLOTS;
        if (index < trapEnd) {
            if (!this.moveItemStackTo(stack, trapEnd, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!Fishing.isBait(stack) || !this.moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
