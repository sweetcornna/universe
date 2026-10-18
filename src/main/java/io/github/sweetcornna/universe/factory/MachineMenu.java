package io.github.sweetcornna.universe.factory;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 设备界面的容器菜单。每种设备一个 {@link Type}：设备格子的位置和用途、界面数据的格数；服务端包着方块实体，
 * 客户端用同样大小的空容器，由原版同步内容。打开时附带方块位置，界面可以读客户端的方块实体（比如选定的配方）。
 */
public class MachineMenu extends AbstractContainerMenu {
    private final Type type;
    private final Container container;
    private final ContainerData data;
    private final BlockPos pos;

    public MachineMenu(Type type, int containerId, Inventory inventory, Container container, ContainerData data, BlockPos pos) {
        super(type.menuType(), containerId);
        checkContainerSize(container, type.slots().size());
        checkContainerDataCount(data, type.dataCount());
        this.type = type;
        this.container = container;
        this.data = data;
        this.pos = pos;
        for (int i = 0; i < type.slots().size(); i++) {
            SlotSpec spec = type.slots().get(i);
            this.addSlot(new MachineSlot(container, i, spec));
        }
        // 没有格子的设备（发电机、感应塔）只看读数，不显示背包
        if (type.showInventory()) {
            this.addStandardInventorySlots(inventory, type.inventoryX(), type.inventoryY());
        }
        this.addDataSlots(data);
    }

    /** 客户端：读出方块位置，其余内容等服务端同步。 */
    public MachineMenu(Type type, int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(type, containerId, inventory, new SimpleContainer(type.slots().size()), new SimpleContainerData(type.dataCount()), buf.readBlockPos());
    }

    public Type type() {
        return this.type;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int data(int index) {
        return this.data.get(index);
    }

    /** 两格拼成的大数（{@link #low}、{@link #high}）。 */
    public long wide(int lowIndex) {
        return join(this.data.get(lowIndex), this.data.get(lowIndex + 1));
    }

    /** 界面上的按钮（选配方之类）交给设备处理。 */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return this.container instanceof ButtonTarget target && player instanceof ServerPlayer serverPlayer
                && target.clickButton(serverPlayer, buttonId);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machine = this.type.slots().size();
        int inventoryEnd = machine + 27;
        int end = this.slots.size();
        if (index < machine) {
            if (!this.moveItemStackTo(stack, machine, end, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (!this.moveItemStackTo(stack, 0, machine, false)) {
            // 放不进设备就在背包和快捷栏之间挪
            if (index < inventoryEnd) {
                if (!this.moveItemStackTo(stack, inventoryEnd, end, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, machine, inventoryEnd, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    // ---- 大数拆成两个 16 位

    public static int low(long value) {
        return (int) (value & 0xFFFF);
    }

    public static int high(long value) {
        return (int) ((value >>> 16) & 0xFFFF);
    }

    public static long join(int low, int high) {
        return (low & 0xFFFFL) | ((high & 0xFFFFL) << 16);
    }

    // ---- 布局

    /** 设备格子的用途。 */
    public enum Role {
        /** 原料，玩家和自动化都能放。 */
        INPUT,
        /** 产物，只能拿。 */
        OUTPUT,
        /** 燃料。 */
        FUEL
    }

    /** 一个设备格子：界面上的位置和用途。 */
    public record SlotSpec(int x, int y, Role role) {
    }

    /** 处理界面按钮的设备（服务端）。 */
    public interface ButtonTarget {
        /** @return 按钮有效，界面要同步 */
        boolean clickButton(ServerPlayer player, int button);
    }

    /**
     * 一种设备界面。
     *
     * @param slots      设备格子
     * @param dataCount  界面数据的格数
     * @param width      界面宽度，原版是 176；背包排在中间
     * @param height     界面高度（背包排在最下面）
     */
    public static final class Type {
        private final List<SlotSpec> slots;
        private final int dataCount;
        private final int width;
        private final int height;
        private Supplier<MenuType<MachineMenu>> menuType = () -> {
            throw new IllegalStateException("menu type not registered");
        };

        public Type(int dataCount, int height, SlotSpec... slots) {
            this(dataCount, 176, height, slots);
        }

        public Type(int dataCount, int width, int height, SlotSpec... slots) {
            this.slots = List.of(slots);
            this.dataCount = dataCount;
            this.width = width;
            this.height = height;
        }

        public List<SlotSpec> slots() {
            return this.slots;
        }

        public int dataCount() {
            return this.dataCount;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public boolean showInventory() {
            return !this.slots.isEmpty();
        }

        /** 背包第一格的位置。 */
        public int inventoryX() {
            return (this.width - 176) / 2 + 8;
        }

        public int inventoryY() {
            return this.height - 82;
        }

        public MenuType<MachineMenu> menuType() {
            return this.menuType.get();
        }

        public void bind(Supplier<MenuType<MachineMenu>> menuType) {
            this.menuType = menuType;
        }
    }

    private static final class MachineSlot extends Slot {
        private final Role role;

        MachineSlot(Container container, int slot, SlotSpec spec) {
            super(container, slot, spec.x(), spec.y());
            this.role = spec.role();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.role != Role.OUTPUT && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }
}
