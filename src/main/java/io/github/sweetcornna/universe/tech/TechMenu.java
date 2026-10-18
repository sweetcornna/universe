package io.github.sweetcornna.universe.tech;

import java.util.List;

import io.github.sweetcornna.universe.core.tech.ModuleHost;
import io.github.sweetcornna.universe.core.tech.ModuleType;
import io.github.sweetcornna.universe.core.tech.SuitSlots;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseDataComponents;
import io.github.sweetcornna.universe.registry.UniverseItems;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import io.github.sweetcornna.universe.survival.MultitoolItem;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * 技术配置界面（ADR-0014）：外骨骼的技术槽、主手多功能工具的技术槽、货舱，下面是背包。
 *
 * <p>格子的顺序：外骨骼技术槽 12 个、工具技术槽 6 个、货舱 54 格、背包 27 格、快捷栏 9 格。没解锁的格子、
 * 手上没有工具时的工具格都不生效（{@link Slot#isActive()} 为假，界面画成锁住）。主手那一格快捷栏锁住，
 * 免得把正在配置的工具拿走。
 */
public class TechMenu extends AbstractContainerMenu {
    public static final int SUIT_X = 8;
    public static final int SUIT_Y = 30;
    public static final int SUIT_COLUMNS = 4;
    public static final int TOOL_X = 8;
    public static final int TOOL_Y = 106;
    public static final int TOOL_COLUMNS = 3;
    /** 左栏（技术槽和加成列表）的右边界。 */
    public static final int LEFT_COLUMN = 124;
    public static final int CARGO_X = 132;
    public static final int CARGO_Y = 30;
    public static final int INVENTORY_X = 132;
    public static final int INVENTORY_Y = 158;
    public static final int WIDTH = 302;
    public static final int HEIGHT = 240;

    public static final int SUIT_START = 0;
    public static final int TOOL_START = SUIT_START + SuitSlots.TECH_MAX;
    public static final int CARGO_START = TOOL_START + SuitSlots.TOOL_SLOTS;
    public static final int INVENTORY_START = CARGO_START + SuitSlots.CARGO_MAX;
    public static final int INVENTORY_END = INVENTORY_START + 36;

    private final Player player;
    private final int techSlots;
    private final int cargoSlots;
    /** 主手工具所在的快捷栏格；没有拿着工具时为 -1。 */
    private final int toolSlot;

    /** 客户端：格数从服务端发来，内容由菜单同步。 */
    public TechMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                new SimpleContainer(SuitSlots.TECH_MAX), new SimpleContainer(SuitSlots.TOOL_SLOTS), new SimpleContainer(SuitSlots.CARGO_MAX));
    }

    private TechMenu(int id, Inventory inventory, int techSlots, int cargoSlots, int toolSlot, Container suit, Container tool, Container cargo) {
        super(UniverseMenus.TECH.get(), id);
        this.player = inventory.player;
        this.techSlots = techSlots;
        this.cargoSlots = cargoSlots;
        this.toolSlot = toolSlot;
        for (int i = 0; i < SuitSlots.TECH_MAX; i++) {
            this.addSlot(new TechSlot(suit, i, SUIT_X + i % SUIT_COLUMNS * 18, SUIT_Y + i / SUIT_COLUMNS * 18, ModuleHost.EXOSUIT, techSlots));
        }
        for (int i = 0; i < SuitSlots.TOOL_SLOTS; i++) {
            this.addSlot(new TechSlot(tool, i, TOOL_X + i % TOOL_COLUMNS * 18, TOOL_Y + i / TOOL_COLUMNS * 18, ModuleHost.MULTITOOL,
                    toolSlot >= 0 ? SuitSlots.TOOL_SLOTS : 0));
        }
        for (int i = 0; i < SuitSlots.CARGO_MAX; i++) {
            this.addSlot(new LimitedSlot(cargo, i, CARGO_X + i % 9 * 18, CARGO_Y + i / 9 * 18, cargoSlots));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            int x = INVENTORY_X + column * 18;
            int y = INVENTORY_Y + 58;
            this.addSlot(column == toolSlot ? new LockedSlot(inventory, column, x, y) : new Slot(inventory, column, x, y));
        }
    }

    /** 服务端：直接读写玩家的外骨骼数据和主手工具的组件。 */
    public static TechMenu server(int id, Inventory inventory, ServerPlayer player) {
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        int toolSlot = toolSlot(player);
        Container suit = new ListContainer(data.modules(), data::changed);
        Container cargo = new ListContainer(data.cargo(), () -> {
        });
        Container tool;
        if (toolSlot >= 0) {
            NonNullList<ItemStack> items = NonNullList.withSize(SuitSlots.TOOL_SLOTS, ItemStack.EMPTY);
            ItemStack stack = inventory.getItem(toolSlot);
            stack.getOrDefault(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.EMPTY).copyInto(items);
            tool = new ListContainer(items, () -> inventory.getItem(toolSlot)
                    .set(UniverseDataComponents.TOOL_MODULES.get(), ItemContainerContents.fromItems(items)));
        } else {
            tool = new SimpleContainer(SuitSlots.TOOL_SLOTS);
        }
        return new TechMenu(id, inventory, data.techSlots(), data.cargoSlots(), toolSlot, suit, tool, cargo);
    }

    /** 打开界面时发给客户端的格数。 */
    public static void writeOpenData(ServerPlayer player, RegistryFriendlyByteBuf buf) {
        ExosuitData data = player.getData(UniverseAttachments.EXOSUIT);
        buf.writeVarInt(data.techSlots());
        buf.writeVarInt(data.cargoSlots());
        buf.writeVarInt(toolSlot(player));
    }

    private static int toolSlot(Player player) {
        int selected = player.getInventory().getSelectedSlot();
        return player.getInventory().getItem(selected).getItem() instanceof MultitoolItem ? selected : -1;
    }

    public int techSlots() {
        return this.techSlots;
    }

    public int cargoSlots() {
        return this.cargoSlots;
    }

    public boolean hasTool() {
        return this.toolSlot >= 0;
    }

    /** 外骨骼模块要穿着外骨骼胸甲才生效。 */
    public boolean wearingSuit() {
        return this.player.getItemBySlot(EquipmentSlot.CHEST).is(UniverseItems.EXOSUIT_CHESTPLATE);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && (this.toolSlot < 0 || player.getInventory().getItem(this.toolSlot).getItem() instanceof MultitoolItem);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            // 技术槽、货舱 → 背包
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (stack.getItem() instanceof TechModuleItem module) {
                int start = module.type().host() == ModuleHost.EXOSUIT ? SUIT_START : TOOL_START;
                int end = module.type().host() == ModuleHost.EXOSUIT ? TOOL_START : CARGO_START;
                moved = this.moveItemStackTo(stack, start, end, false);
            }
            if (!moved && !this.moveItemStackTo(stack, CARGO_START, INVENTORY_START, false)) {
                // 货舱满了：背包和快捷栏之间挪
                int hotbar = INVENTORY_START + 27;
                if (index < hotbar ? !this.moveItemStackTo(stack, hotbar, INVENTORY_END, false)
                        : !this.moveItemStackTo(stack, INVENTORY_START, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
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

    /** 已解锁的格子才生效。 */
    static class LimitedSlot extends Slot {
        private final int unlocked;

        LimitedSlot(Container container, int index, int x, int y, int unlocked) {
            super(container, index, x, y);
            this.unlocked = unlocked;
        }

        boolean unlocked() {
            return this.getContainerSlot() < this.unlocked;
        }

        @Override
        public boolean isActive() {
            return this.unlocked();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.unlocked();
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.unlocked() || this.hasItem();
        }
    }

    /** 技术槽：只放这个位置的模块，一格一个，同一种最多三个。 */
    public static class TechSlot extends LimitedSlot {
        private final ModuleHost host;

        TechSlot(Container container, int index, int x, int y, ModuleHost host, int unlocked) {
            super(container, index, x, y, unlocked);
            this.host = host;
        }

        public ModuleHost host() {
            return this.host;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!this.unlocked() || !(stack.getItem() instanceof TechModuleItem module) || module.type().host() != this.host) {
                return false;
            }
            return this.sameType(module.type()) < ModuleType.MAX_STACK;
        }

        /** 其他格里已经装了几个同种模块。 */
        int sameType(ModuleType type) {
            int count = 0;
            for (int i = 0; i < this.container.getContainerSize(); i++) {
                if (i != this.getContainerSlot() && this.container.getItem(i).getItem() instanceof TechModuleItem other && other.type() == type) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void set(ItemStack stack) {
            // 只在服务端随机（服务端的容器是 ListContainer），客户端等同步
            if (this.container instanceof ListContainer && !TechModuleItem.calibrated(stack)) {
                TechModuleItem.calibrate(stack, System.nanoTime() ^ stack.hashCode());
            }
            super.set(stack);
        }
    }

    /** 主手工具那一格：配置期间不能拿走。 */
    static class LockedSlot extends Slot {
        LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** 技术槽里装着的模块（界面统计加成用）。 */
    public List<ItemStack> installed(ModuleHost host) {
        int start = host == ModuleHost.EXOSUIT ? SUIT_START : TOOL_START;
        int count = host == ModuleHost.EXOSUIT ? this.techSlots : this.hasTool() ? SuitSlots.TOOL_SLOTS : 0;
        return this.slots.subList(start, start + count).stream().map(Slot::getItem).filter(stack -> !stack.isEmpty()).toList();
    }
}
