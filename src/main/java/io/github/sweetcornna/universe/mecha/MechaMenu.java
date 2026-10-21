package io.github.sweetcornna.universe.mecha;

import io.github.sweetcornna.universe.blueprint.Construction;
import io.github.sweetcornna.universe.core.mecha.LogisticsSlot;
import io.github.sweetcornna.universe.factory.MachineMenu;
import io.github.sweetcornna.universe.registry.UniverseAttachments;
import io.github.sweetcornna.universe.registry.UniverseMenus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 机甲面板（ADR-0023）：燃料格和背包是真的格子；能量核心的读数走 {@link ContainerData}；物流栏位是设置，
 * 存在 {@link MechaSettings} 里，按钮改（{@link #clickMenuButton}），上下限用 {@code MechaPayloads.Bounds} 改。
 */
public class MechaMenu extends AbstractContainerMenu {
    /** 物流栏位要研究的科技。 */
    public static final String LOGISTICS_TECH = "distribution_logistics";
    public static final int WIDTH = 248;
    public static final int HEIGHT = 242;
    public static final int FUEL_X = 8;
    public static final int FUEL_Y = 64;
    public static final int LOGISTICS_X = 130;
    public static final int LOGISTICS_Y = 30;
    public static final int LOGISTICS_COLUMNS = 6;
    public static final int INVENTORY_X = 43;
    public static final int INVENTORY_Y = 160;

    /** 按钮：0–11 用手上的物品设物流栏位，20–31 清掉，40 切换自动补燃料，41 清空工地。 */
    public static final int SET_SLOT = 0;
    public static final int CLEAR_SLOT = 20;
    public static final int TOGGLE_REFUEL = 40;
    public static final int CANCEL_BUILD = 41;

    public static final int ENERGY = 0;
    public static final int CAPACITY = 2;
    public static final int BURNING = 4;
    public static final int FUEL_POWER = 5;
    public static final int INFLOW = 6;
    public static final int OUTFLOW = 7;
    public static final int DATA_COUNT = 8;

    private static final int FUEL_SLOT = 0;
    private static final int INVENTORY_START = 1;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final Player player;
    private final ContainerData data;

    /** 客户端。 */
    public MechaMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(1), new SimpleContainerData(DATA_COUNT));
    }

    private MechaMenu(int id, Inventory inventory, Container fuel, ContainerData data) {
        super(UniverseMenus.MECHA.get(), id);
        this.player = inventory.player;
        this.data = data;
        this.addSlot(new Slot(fuel, 0, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.has(DataComponents.COOKING_FUEL);
            }
        });
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

    /** 服务端：燃料格直接读写玩家的机甲数据。 */
    public static MechaMenu server(int id, Inventory inventory, ServerPlayer player) {
        MechaData mecha = player.getData(UniverseAttachments.MECHA);
        Container fuel = new FuelContainer(mecha);
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                long capacity = Mecha.capacity(player.level());
                return switch (index) {
                    case ENERGY -> MachineMenu.low((long) mecha.energy().energy());
                    case ENERGY + 1 -> MachineMenu.high((long) mecha.energy().energy());
                    case CAPACITY -> MachineMenu.low(capacity);
                    case CAPACITY + 1 -> MachineMenu.high(capacity);
                    case BURNING -> mecha.energy().burningTotal() <= 0 ? 0
                            : (int) Math.round(1000 * mecha.energy().burning() / mecha.energy().burningTotal());
                    case FUEL_POWER -> (int) Mecha.fuelPower(player.level());
                    case INFLOW -> (int) Math.round(mecha.inflow());
                    case OUTFLOW -> (int) Math.round(mecha.outflow());
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        return new MechaMenu(id, inventory, fuel, data);
    }

    public long energy() {
        return MachineMenu.join(this.data.get(ENERGY), this.data.get(ENERGY + 1));
    }

    public long capacity() {
        return MachineMenu.join(this.data.get(CAPACITY), this.data.get(CAPACITY + 1));
    }

    /** 正在烧的燃料还剩多少（0–1）。 */
    public float burning() {
        return this.data.get(BURNING) / 1000F;
    }

    public int fuelPower() {
        return this.data.get(FUEL_POWER);
    }

    public int inflow() {
        return this.data.get(INFLOW);
    }

    public int outflow() {
        return this.data.get(OUTFLOW);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer server)) {
            return false;
        }
        MechaSettings settings = server.getData(UniverseAttachments.MECHA_SETTINGS);
        if (id >= SET_SLOT && id < SET_SLOT + MechaSettings.SLOTS) {
            if (!Mecha.logisticsUnlocked(server.level())) {
                return false;
            }
            ItemStack carried = this.getCarried();
            LogisticsSlot slot = carried.isEmpty() ? LogisticsSlot.EMPTY : LogisticsSlot.of(Mecha.id(carried), carried.getMaxStackSize());
            server.setData(UniverseAttachments.MECHA_SETTINGS, settings.withSlot(id - SET_SLOT, slot));
            return true;
        }
        if (id >= CLEAR_SLOT && id < CLEAR_SLOT + MechaSettings.SLOTS) {
            server.setData(UniverseAttachments.MECHA_SETTINGS, settings.withSlot(id - CLEAR_SLOT, LogisticsSlot.EMPTY));
            return true;
        }
        if (id == TOGGLE_REFUEL) {
            server.setData(UniverseAttachments.MECHA_SETTINGS, settings.withAutoRefuel(!settings.autoRefuel()));
            return true;
        }
        if (id == CANCEL_BUILD) {
            Construction.cancel(server);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == FUEL_SLOT) {
            if (!this.moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(stack.has(DataComponents.COOKING_FUEL) && this.moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false))) {
            int hotbar = INVENTORY_START + 27;
            if (index < hotbar ? !this.moveItemStackTo(stack, hotbar, INVENTORY_END, false)
                    : !this.moveItemStackTo(stack, INVENTORY_START, hotbar, false)) {
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

    /** 燃料格：直接是 {@link MechaData#fuel()}。 */
    private static final class FuelContainer implements Container {
        private final MechaData data;

        FuelContainer(MechaData data) {
            this.data = data;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return this.data.fuel().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.data.fuel();
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack taken = this.data.fuel().split(count);
            if (this.data.fuel().isEmpty()) {
                this.data.setFuel(ItemStack.EMPTY);
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = this.data.fuel();
            this.data.setFuel(ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.data.setFuel(stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            this.data.setFuel(ItemStack.EMPTY);
        }
    }
}
